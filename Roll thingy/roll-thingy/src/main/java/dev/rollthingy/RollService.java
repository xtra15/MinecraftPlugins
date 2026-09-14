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
        List<ItemStack> decoded = ItemBundleCodec.decode(item.data());
        return decoded.isEmpty() ? null : decoded.get(0);
    }

    public void award(Player player, Box box, SpinResult result) {
        if (result.outcome().tierIndex() == -1) return;
        ItemStack prize = result.winnerItem();
        if (prize == null) return;
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(prize);
        if (!leftover.isEmpty()) {
            claims.add(ItemBundleCodec.encode(List.of(leftover.get(0))), player.getUniqueId(), box.id(), System.currentTimeMillis());
        }
    }

    double scoreDeposit(Box box, List<ItemStack> deposit) {
        Map<String, Integer> strictCounts = new HashMap<>();
        Map<String, Integer> looseCounts = new HashMap<>();
        Map<String, Integer> wrongCounts = new HashMap<>();
        for (PaymentRequirement req : box.payment()) {
            if (req.strict()) continue;
            for (ItemStack item : deposit) {
                List<Map<String, Object>> maps = safeDecode(req.data());
                if (maps.isEmpty()) continue;
                String material = String.valueOf(maps.get(0).get("type"));
                if (item != null && item.getType().name().equals(material)) {
                    looseCounts.merge(req.data(), 1, Integer::sum);
                }
            }
        }
        for (ItemStack item : deposit) {
            if (item == null) continue;
            boolean matched = false;
            for (PaymentRequirement req : box.payment()) {
                if (req.strict()) {
                    List<Map<String, Object>> maps = safeDecode(req.data());
                    if (!maps.isEmpty() && sameItem(item, maps.get(0))) {
                        strictCounts.merge(req.data(), 1, Integer::sum);
                        matched = true;
                        break;
                    }
                } else {
                    List<Map<String, Object>> maps = safeDecode(req.data());
                    if (!maps.isEmpty() && item.getType().name().equals(String.valueOf(maps.get(0).get("type")))) {
                        matched = true;
                        break;
                    }
                }
            }
            if (!matched) {
                wrongCounts.merge(item.getType().name(), 1, Integer::sum);
            }
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

    private static boolean sameItem(ItemStack item, Map<String, Object> spec) {
        String type = String.valueOf(spec.get("type"));
        if (!item.getType().name().equals(type)) return false;
        ItemStack template = ItemStack.deserialize(spec);
        return item.isSimilar(template);
    }

    static double requiredAmount(Box box) {
        double total = 0;
        for (PaymentRequirement req : box.payment()) total += req.amount();
        return total;
    }
}