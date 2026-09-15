package dev.rollthingy.gui;

import dev.rollthingy.RollService;
import dev.rollthingy.RollServices;
import dev.rollthingy.core.box.Box;
import dev.rollthingy.core.box.PaymentRequirement;
import dev.rollthingy.core.gui.DepositGui;
import dev.rollthingy.core.msg.SoundRegistry;
import dev.rollthingy.roll.SpinAnimation;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class SpinGui {
    private final RollServices services;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public SpinGui(RollServices services) {
        this.services = services;
    }

    /**
     * Geyser note: the spin screen intentionally uses a plain click-to-spin flow with the payment
     * strip accepting shift-clicks or drag; Bedrock players have no right-click so every action is
     * a simple click. Item names are hover/lore text (Bedrock safe).
     */
    public void open(Player player, Box box) {
        StemGui gui = new StemGui(services, box);
        services.gui().registerOpen(player, gui);
        services.sounds().play(player, SoundRegistry.Event.OPEN);
        gui.open(player);
    }

    /** DepositGui-based spin screen: slots 9-17 accept payment, spin at 49. */
    private static final class StemGui extends DepositGui {
        private final RollServices services;
        private final Box box;
        private boolean started;

        StemGui(RollServices services, Box box) {
            super(6, services.messages().get("spin.title", Map.of("name", box.name())), 9, 17);
            this.services = services;
            this.box = box;
            draw();
        }

        private void draw() {
            fill(services.config().fillerItem());
            fillRect(9, 17, null);
            on(0, clk -> {
                cancelAndReturn(clk.player());
                new BoxDetailGui(services).open(clk.player(), box);
            }).set(0, button(Material.SPECTRAL_ARROW, "<gray>Back"));
            on(49, clk -> start(clk.player())).set(49, button(Material.EMERALD, services.messages().get("spin.button")));

            set(8, bracket());
            set(18, bracket());
            set(4, boxIcon());
            drawRequirements();
        }

        private ItemStack boxIcon() {
            ItemStack icon = services.cache().icon(box);
            icon.editMeta(meta -> meta.lore(List.of(
                    MM.deserialize(services.messages().get("drop.hint-spin")),
                    MM.deserialize("<gray>Payment: " + summary()))));
            return icon;
        }

        private void drawRequirements() {
            List<PaymentRequirement> payment = box.payment();
            if (payment.isEmpty()) {
                ItemStack free = button(Material.PAPER, services.messages().get("spin.required-free"));
                set(22, free);
                return;
            }
            int slot = 19;
            for (int i = 0; i < payment.size() && slot <= 26; i++) {
                PaymentRequirement req = payment.get(i);
                ItemStack icon = services.cache().item(req.data());
                String label = req.strict()
                        ? services.messages().get("spin.required-exact", Map.of("count", String.valueOf(req.amount())))
                        : services.messages().get("spin.required-type", Map.of("count", String.valueOf(req.amount())));
                icon.editMeta(meta -> meta.lore(List.of(MM.deserialize(label))));
                set(slot, icon);
                slot++;
            }
            if (payment.size() > 8) {
                ItemStack more = button(Material.PAPER, "<gray>+" + (payment.size() - 8) + " more");
                set(26, more);
            }
        }

        private String summary() {
            List<PaymentRequirement> payment = box.payment();
            if (payment.isEmpty()) return "free";
            int total = payment.stream().mapToInt(PaymentRequirement::amount).sum();
            return total + " item" + (total == 1 ? "" : "s");
        }

        private ItemStack bracket() {
            ItemStack bracket = new ItemStack(Material.LIME_STAINED_GLASS_PANE, 1);
            bracket.editMeta(meta -> meta.displayName(MM.deserialize(services.messages().get("drop.zone-bracket"))));
            return bracket;
        }

        private void start(Player player) {
            if (started) return;
            long cooldown = services.roll().cooldownMillis(player, box);
            if (cooldown > 0) {
                player.sendMessage(MM.deserialize(services.messages().get("spin.on-cooldown",
                        Map.of("seconds", String.valueOf(Math.max(1, cooldown / 1000))))));
                services.sounds().play(player, SoundRegistry.Event.ERROR);
                return;
            }
            List<ItemStack> deposit = collect();
            if (deposit.isEmpty()) {
                player.sendMessage(MM.deserialize(services.messages().get("spin.no-payment")));
                services.sounds().play(player, SoundRegistry.Event.ERROR);
                return;
            }
            started = true;
            markConfirmed();
            clearDeposit();

            RollService.SpinResult result = services.roll().spin(player, box, deposit);
            if (result.winnerItem() == null) {
                // Zonk: consume payment, no inventory animation needed.
                player.closeInventory();
                finish(player, result);
                return;
            }
            services.sounds().play(player, SoundRegistry.Event.SPIN);
            List<ItemStack> strip = buildStrip(services, box, result.winnerItem());
            player.closeInventory();
            new SpinAnimation(services, player, strip, () -> finish(player, result)).run();
        }

        private void finish(Player player, RollService.SpinResult result) {
            if (result.outcome().tierIndex() == -1) {
                player.sendMessage(MM.deserialize(services.messages().get("spin.zonk")));
                services.sounds().play(player, SoundRegistry.Event.ZONK);
                return;
            }
            boolean rare = result.chancePct() < 0.5;
            String itemName = itemDisplayName(result.winnerItem());
            player.sendMessage(MM.deserialize(services.messages().get(rare ? "spin.win-rare" : "spin.win",
                    Map.of("item", itemName))));
            services.sounds().play(player, rare ? SoundRegistry.Event.WIN_RARE : SoundRegistry.Event.WIN);
            services.roll().award(player, box, result);
        }

        private String itemDisplayName(ItemStack item) {
            if (item == null || item.getType() == Material.AIR) return "?";
            if (item.getItemMeta() != null && item.getItemMeta().displayName() != null) {
                return MiniMessage.miniMessage().serialize(item.getItemMeta().displayName());
            }
            return item.getType().name().toLowerCase().replace('_', ' ');
        }

        private void clearDeposit() {
            for (int i = 9; i <= 17; i++) inventory().setItem(i, null);
        }

        private ItemStack button(Material material, String text) {
            ItemStack item = new ItemStack(material, 1);
            item.editMeta(meta -> meta.displayName(MM.deserialize(text)));
            return item;
        }
    }

    static List<ItemStack> buildStrip(RollServices services, Box box, ItemStack winner) {
        List<ItemStack> pool = new ArrayList<>();
        for (var tier : box.tiers()) {
            for (var tierItem : tier.items()) {
                ItemStack icon = services.cache().item(tierItem.data());
                if (icon.getType() != Material.AIR) pool.add(icon);
            }
        }
        if (pool.isEmpty()) pool.add(new ItemStack(Material.STONE, 1));
        Random rng = new Random();
        List<ItemStack> strip = new ArrayList<>();
        int len = 11;
        for (int i = 0; i < len; i++) strip.add(pool.get(rng.nextInt(pool.size())).clone());
        if (winner != null) strip.set(len / 2, winner.clone());
        return strip;
    }
}