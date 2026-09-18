package dev.rollthingy;

import dev.rollthingy.box.BoxModelCache;
import dev.rollthingy.core.box.Box;
import dev.rollthingy.core.box.BoxItem;
import dev.rollthingy.core.box.OddsEngine;
import dev.rollthingy.core.box.OddsEngine.OddsModel;
import dev.rollthingy.core.box.PaymentRequirement;
import dev.rollthingy.core.box.PenaltyMath;
import dev.rollthingy.core.box.RarityTier;
import dev.rollthingy.core.misc.ItemBundleCodec;
import dev.rollthingy.core.store.ClaimStore;
import dev.rollthingy.core.store.CooldownStore;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class RollService {
    public record SpinResult(OddsEngine.RollOutcome outcome, Box winnerBox, ItemStack winnerItem, double chancePct, double shortfall) {}

    private final BoxModelCache cache;
    private final CooldownStore cooldowns;
    private final ClaimStore claims;

    public RollService(BoxModelCache cache, CooldownStore cooldowns, ClaimStore claims) {
        this.cache = cache;
        this.cooldowns = cooldowns;
        this.claims = claims;
    }

    public long cooldownMillis(Player player, Box box) {
        long last = cooldowns.lastSpinAt(player.getUniqueId(), box.id());
        if (last < 0) return 0;
        long remaining = box.cooldownSeconds() * 1000L - (System.currentTimeMillis() - last);
        return Math.max(0, remaining);
    }

    public boolean isOnCooldown(Player player, Box box) {
        return cooldownMillis(player, box) > 0;
    }

    public SpinResult spin(Player player, Box box, List<ItemStack> deposit) {
        OddsModel model = cache.modelOf(box.id());
        double score = scoreDeposit(box, deposit);
        double required = requiredAmount(box);
        double shortfall = PenaltyMath.shortfall(score, required);
        OddsModel adjusted = model.withAdjustedWeights(shortfall, box.penalty());
        OddsEngine.RollOutcome outcome = adjusted.roll(new Random());
        double chance;
        if (outcome.tierIndex() == -1) {
            chance = adjusted.zonkWeight() / (adjusted.totalItemWeight() + adjusted.zonkWeight()) * 100.0;
        } else {
            chance = adjusted.chanceOf(adjusted.tiers().get(outcome.tierIndex()), outcome.itemIndex());
        }
        long remaining = cooldowns.attempt(player.getUniqueId(), box.id(), System.currentTimeMillis(), box.cooldownSeconds());
        ItemStack winner = winner(box, outcome, adjusted);
        return new SpinResult(outcome, box, winner, chance, shortfall);
    }

    private ItemStack winner(Box box, OddsEngine.RollOutcome outcome, OddsModel adjusted) {
        if (outcome.tierIndex() == -1) return null;
        OddsEngine.TierChances tier = adjusted.tiers().get(outcome.tierIndex());
        // The Box preserves the original data strings; index into the box tiers.
        RarityTier boxTier = box.tiers().get(outcome.tierIndex());
        BoxItem item = boxTier.items().get(outcome.itemIndex());
        return cache.itemOrNull(item.data());
    }

    /** @return true when the prize could not fit and was stored in the player's claims. */
    public boolean award(Player player, Box box, SpinResult result) {
        if (result.outcome().tierIndex() == -1) return false;
        ItemStack prize = result.winnerItem();
        if (prize == null) return false;
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(prize);
        if (leftover.isEmpty()) return false;
        claims.add(ItemBundleCodec.encode(List.copyOf(leftover.values())), player.getUniqueId(), box.id(), System.currentTimeMillis());
        return true;
    }

    double scoreDeposit(Box box, List<ItemStack> deposit) {
        Map<String, Integer> strictCounts = new HashMap<>();
        Map<String, Integer> looseCounts = new HashMap<>();
        Map<String, Integer> wrongCounts = new HashMap<>();
        List<PaymentRequirement> reqs = box.payment();
        // Decode each requirement's spec once (Java serialization is expensive).
        Map<String, List<Map<String, Object>>> specs = new HashMap<>();
        for (PaymentRequirement req : reqs) {
            specs.put(req.data(), safeDecode(req.data()));
        }
        // Pre-deserialize strict templates once so the match loop never rebuilds them.
        Map<String, ItemStack> strictTemplates = new HashMap<>();
        for (PaymentRequirement req : reqs) {
            if (req.strict()) {
                List<Map<String, Object>> maps = specs.get(req.data());
                if (!maps.isEmpty()) strictTemplates.put(req.data(), ItemStack.deserialize(maps.get(0)));
            }
        }
        Map<String, String> looseMaterials = new HashMap<>();
        for (PaymentRequirement req : reqs) {
            if (req.strict()) continue;
            List<Map<String, Object>> maps = specs.get(req.data());
            if (!maps.isEmpty()) looseMaterials.put(req.data(), String.valueOf(maps.get(0).get("type")));
        }
        // Loose contributions: an item can satisfy every loose requirement of its material.
        for (PaymentRequirement req : reqs) {
            if (req.strict()) continue;
            String material = looseMaterials.get(req.data());
            if (material == null) continue;
            for (ItemStack item : deposit) {
                if (item != null && item.getType().name().equals(material)) {
                    looseCounts.merge(req.data(), 1, Integer::sum);
                }
            }
        }
        for (ItemStack item : deposit) {
            if (item == null) continue;
            boolean matched = false;
            for (PaymentRequirement req : reqs) {
                if (req.strict()) {
                    ItemStack template = strictTemplates.get(req.data());
                    if (template != null && item.isSimilar(template)) {
                        strictCounts.merge(req.data(), 1, Integer::sum);
                        matched = true;
                        break;
                    }
                } else {
                    String material = looseMaterials.get(req.data());
                    if (material != null && item.getType().name().equals(material)) {
                        matched = true;
                        break;
                    }
                }
            }
            if (!matched) wrongCounts.merge(item.getType().name(), 1, Integer::sum);
        }
        return PenaltyMath.contributedScore(box.payment(), strictCounts, looseCounts, box.penalty().looseValue(), wrongCounts);
    }

    private static List<Map<String, Object>> safeDecode(String data) {
        try {
            return ItemBundleCodec.decodeMaps(data);
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }

    public static double requiredAmount(Box box) {
        double total = 0;
        for (PaymentRequirement req : box.payment()) total += req.amount();
        return total;
    }

    /** Total item units in a deposit (sums stack amounts, skips air). */
    public static int totalUnits(List<ItemStack> deposit) {
        int total = 0;
        for (ItemStack item : deposit) {
            if (item != null && !item.getType().isAir()) total += item.getAmount();
        }
        return total;
    }

    /** How many full spins fit in this deposit; min 1 when something was paid (free boxes: 1). */
    public int spinCountFor(Box box, List<ItemStack> deposit) {
        double required = requiredAmount(box);
        if (required <= 0) return 1;
        int units = totalUnits(deposit);
        if (units <= 0) return 0;
        return Math.max(1, (int) (units / required));
    }

    public record Split(List<List<ItemStack>> spins, List<ItemStack> leftover) {}

    /** Carves {@code spins} chunks of {@code unitsPerSpin} units out of the deposit (cloned); rest is leftover. */
    public Split splitDeposit(List<ItemStack> deposit, int spins, int unitsPerSpin) {
        List<ItemStack> rest = new ArrayList<>();
        for (ItemStack item : deposit) {
            if (item != null && !item.getType().isAir() && item.getAmount() > 0) rest.add(item.clone());
        }
        List<List<ItemStack>> out = new ArrayList<>();
        if (spins <= 0 || unitsPerSpin <= 0) return new Split(out, rest);
        for (int i = 0; i < spins; i++) {
            List<ItemStack> chunk = new ArrayList<>();
            int need = unitsPerSpin;
            while (need > 0 && !rest.isEmpty()) {
                ItemStack head = rest.get(0);
                int take = Math.min(need, head.getAmount());
                ItemStack part = head.clone();
                part.setAmount(take);
                chunk.add(part);
                if (take >= head.getAmount()) rest.remove(0);
                else head.setAmount(head.getAmount() - take);
                need -= take;
            }
            out.add(chunk);
        }
        return new Split(out, rest);
    }

    /** Live luck (0-100) the current deposit would get; 100 when the required items are paid. */
    public int luckPercent(Box box, List<ItemStack> deposit) {
        return PenaltyMath.luckPercent(scoreDeposit(box, deposit), requiredAmount(box));
    }
}