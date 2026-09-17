package dev.rollthingy.gui;

import dev.rollthingy.RollService;
import dev.rollthingy.RollServices;
import dev.rollthingy.core.box.Box;
import dev.rollthingy.core.box.PaymentRequirement;
import dev.rollthingy.core.gui.ChestGui;
import dev.rollthingy.core.gui.DepositGui;
import dev.rollthingy.core.msg.SoundRegistry;
import dev.rollthingy.roll.SpinAnimation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
            set(40, luckBadge());
        }

        @Override
        protected void onChanged(Player player) {
            if (started || inventory() == null) return;
            if (player.getOpenInventory().getTopInventory() != inventory()) return;
            set(40, luckBadge());
        }

        /** Live luck gauge: updates as the player drops payment in, so they know their odds. */
        private ItemStack luckBadge() {
            List<ItemStack> deposit = collect();
            int luck = services.roll().luckPercent(box, deposit);
            boolean full = !deposit.isEmpty() && luck >= 100;
            Material mat = deposit.isEmpty() ? Material.GRAY_DYE
                    : full ? Material.EMERALD
                    : luck >= 50 ? Material.NETHER_STAR
                    : Material.RED_DYE;
            String title = deposit.isEmpty()
                    ? services.messages().get("spin.luck-empty")
                    : full ? services.messages().get("spin.luck-full")
                    : services.messages().get("spin.luck", Map.of("luck", String.valueOf(luck)));
            List<Component> lore = new ArrayList<>();
            lore.add(MM.deserialize(services.messages().get("spin.luck-hint")));
            if (!deposit.isEmpty() && !full) {
                lore.add(MM.deserialize(services.messages().get("spin.luck-need",
                        Map.of("count", String.valueOf(requiredTotal())))));
            }
            ItemStack badge = new ItemStack(mat, 1);
            badge.editMeta(meta -> {
                meta.displayName(MM.deserialize(title));
                meta.lore(lore);
            });
            return badge;
        }

        private int requiredTotal() {
            return box.payment().stream().mapToInt(PaymentRequirement::amount).sum();
        }

        private ItemStack boxIcon() {
            ItemStack icon = services.cache().icon(box);
            icon.editMeta(meta -> {
                meta.displayName(MM.deserialize("<gold>" + box.name()));
                meta.lore(List.of(
                        MM.deserialize(services.messages().get("drop.hint-spin")),
                        MM.deserialize("<gray>Payment: " + summary())));
            });
            return icon;
        }

        private void drawRequirements() {
            List<PaymentRequirement> payment = box.payment();
            if (payment.isEmpty()) {
                set(22, button(Material.PAPER, services.messages().get("spin.required-free")));
                return;
            }
            int shown = Math.min(payment.size(), 8);
            int start = 19 + ((8 - shown) / 2);
            for (int i = 0; i < shown; i++) {
                PaymentRequirement req = payment.get(i);
                ItemStack icon = services.cache().item(req.data());
                String label = req.strict()
                        ? services.messages().get("spin.required-exact", Map.of("count", String.valueOf(req.amount())))
                        : services.messages().get("spin.required-type", Map.of("count", String.valueOf(req.amount())));
                icon.editMeta(meta -> meta.lore(List.of(MM.deserialize(label))));
                set(start + i, icon);
            }
            if (payment.size() > shown) {
                set(26, button(Material.PAPER, "<gray>+" + (payment.size() - shown) + " more"));
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
            services.sounds().play(player, SoundRegistry.Event.SPIN);
            List<ItemStack> reel = SpinAnimation.buildReel(services, box, result.winnerItem());
            player.closeInventory();
            new SpinAnimation(services, player, reel, () -> showResult(player, box, result)).run();
        }

        private void showResult(Player player, Box box, RollService.SpinResult result) {
            boolean zonk = result.outcome().tierIndex() == -1;
            boolean rare = !zonk && result.chancePct() < 0.5;
            String text = zonk
                    ? services.messages().get("spin.zonk")
                    : services.messages().get(rare ? "spin.win-rare" : "spin.win",
                            Map.of("item", itemDisplayName(result.winnerItem())));

            boolean stored = !zonk && services.roll().award(player, box, result);

            ChestGui gui = new ChestGui(3, services.messages().get("spin.result"));
            gui.fill(services.config().fillerItem());
            ItemStack prize = zonk ? new ItemStack(Material.GRAY_DYE, 1) : result.winnerItem().clone();
            List<Component> lore = stored
                    ? List.of(MM.deserialize(text), MM.deserialize(services.messages().get("claim.stored")))
                    : List.of(MM.deserialize(text));
            prize.editMeta(meta -> meta.lore(lore));
            gui.set(13, prize);

            long cooldown = services.roll().cooldownMillis(player, box);
            if (cooldown <= 0) {
                gui.on(11, clk -> new SpinGui(services).open(clk.player(), box))
                        .set(11, button(Material.EMERALD, services.messages().get("spin.again")));
            } else {
                gui.set(11, button(Material.GRAY_DYE, services.messages().get("spin.cooldown-button",
                        Map.of("seconds", String.valueOf(Math.max(1, cooldown / 1000))))));
            }
            gui.on(15, clk -> new BoxDetailGui(services).open(clk.player(), box))
                    .set(15, button(Material.SPECTRAL_ARROW, "<gray>Back"));

            HelpBook helpBook = new HelpBook(services);
            gui.on(23, clk -> helpBook.open(clk.player())).set(23, helpBook.icon());

            player.sendMessage(MM.deserialize(text));
            if (stored) player.sendMessage(MM.deserialize(services.messages().get("claim.stored")));
            services.sounds().play(player,
                    zonk ? SoundRegistry.Event.ZONK : (rare ? SoundRegistry.Event.WIN_RARE : SoundRegistry.Event.WIN));
            gui.open(player);
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
}