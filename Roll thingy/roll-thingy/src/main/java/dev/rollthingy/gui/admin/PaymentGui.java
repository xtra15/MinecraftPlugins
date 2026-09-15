package dev.rollthingy.gui.admin;

import dev.rollthingy.RollServices;
import dev.rollthingy.core.box.Box;
import dev.rollthingy.core.box.PaymentRequirement;
import dev.rollthingy.core.gui.DepositGui;
import dev.rollthingy.core.misc.ItemBundleCodec;
import dev.rollthingy.core.msg.SoundRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin payment editor: drop the required items in the middle row, toggle exact/type-only, save.
 * Row 2 (18-26) previews the current requirements so the admin sees what is already set.
 */
public class PaymentGui {
    private final RollServices services;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public PaymentGui(RollServices services) {
        this.services = services;
    }

    public void open(Player admin, Box box) {
        StemGui gui = new StemGui(services, box);
        services.gui().registerOpen(admin, gui);
        services.sounds().play(admin, SoundRegistry.Event.OPEN);
        gui.open(admin);
    }

    private static final class StemGui extends DepositGui {
        private final RollServices services;
        private Box box;
        private boolean strict;

        StemGui(RollServices services, Box box) {
            super(6, services.messages().get("admin.payment") + " — " + box.name(), 9, 17);
            this.services = services;
            this.box = box;
            this.strict = box.payment().stream().anyMatch(PaymentRequirement::strict);
            draw();
        }

        private void draw() {
            fill(services.config().fillerItem());
            fillRect(9, 17, null);
            set(8, bracket());
            set(18, bracket());
            set(4, howTo());

            on(0, clk -> {
                cancelAndReturn(clk.player());
                new EditGui(services).open(clk.player(), box);
            }).set(0, button(Material.SPECTRAL_ARROW, "<gray>Back"));

            on(45, clk -> toggleStrict()).set(45, toggleButton());
            on(53, clk -> save(clk.player())).set(53, button(Material.EMERALD,
                    services.messages().get("admin.payment-save")));

            drawCurrent();
        }

        private void drawCurrent() {
            ItemStack label = new ItemStack(Material.LECTERN, 1);
            label.editMeta(meta -> meta.displayName(MM.deserialize(services.messages().get("drop.current-label"))));
            set(19, label);
            List<PaymentRequirement> payment = box.payment();
            if (payment.isEmpty()) {
                ItemStack none = button(Material.PAPER, services.messages().get("admin.payment-none"));
                set(20, none);
                return;
            }
            int slot = 20;
            for (PaymentRequirement req : payment) {
                if (slot > 25) break;
                ItemStack icon = services.cache().item(req.data());
                String mode = req.strict() ? "exact" : "any type";
                String material = services.cache().materialOf(req.data());
                icon.editMeta(meta -> meta.lore(List.of(
                        MM.deserialize("<gray>" + req.amount() + "x <dark_gray>(" + mode + ")"),
                        MM.deserialize("<dark_gray>" + material))));
                set(slot, icon);
                slot++;
            }
            if (payment.size() > 6) {
                set(26, button(Material.PAPER, "<gray>+" + (payment.size() - 6) + " more"));
            }
        }

        private ItemStack howTo() {
            ItemStack how = new ItemStack(Material.BOOK, 1);
            how.editMeta(meta -> {
                meta.displayName(MM.deserialize(services.messages().get("drop.banner")));
                meta.lore(List.of(
                        MM.deserialize(services.messages().get("drop.step-price")),
                        MM.deserialize(services.messages().get("drop.step-mode")),
                        MM.deserialize(services.messages().get("drop.step-confirm"))));
            });
            return how;
        }

        private void toggleStrict() {
            strict = !strict;
            if (inventory() == null) return;
            inventory().setItem(45, toggleButton());
        }

        private ItemStack toggleButton() {
            ItemStack toggle = new ItemStack(Material.ARROW, 1);
            toggle.editMeta(meta -> {
                meta.displayName(MM.deserialize(strictLabel()));
                meta.lore(List.of(MM.deserialize(services.messages().get("admin.toggle-hint"))));
            });
            return toggle;
        }

        private void save(Player admin) {
            List<ItemStack> items = collect();
            Map<String, Integer> counts = new LinkedHashMap<>();
            Map<String, String> specs = new LinkedHashMap<>();
            for (ItemStack item : items) {
                String data = ItemBundleCodec.encode(List.of(item.clone()));
                String key = strict ? data : item.getType().name();
                counts.merge(key, 1, Integer::sum);
                specs.putIfAbsent(key, data);
            }
            if (counts.isEmpty()) {
                admin.sendMessage(MM.deserialize(services.messages().get("spin.no-payment")));
                services.sounds().play(admin, SoundRegistry.Event.ERROR);
                return;
            }
            List<PaymentRequirement> payment = new ArrayList<>();
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                payment.add(new PaymentRequirement(specs.get(e.getKey()), e.getValue(), strict));
            }
            Box updated = new Box(box.id(), box.name(), box.icon(), payment, box.penalty(),
                    box.cooldownSeconds(), box.zonk(), box.tiers());
            services.cache().addOrUpdate(updated);
            markConfirmed();
            clearDeposit();
            admin.sendMessage(MM.deserialize(services.messages().get("admin.saved")));
            services.sounds().play(admin, SoundRegistry.Event.CONFIRM);
            admin.closeInventory();
            new EditGui(services).open(admin, updated);
        }

        private void clearDeposit() {
            if (inventory() == null) return;
            for (int i = 9; i <= 17; i++) inventory().setItem(i, null);
        }

        private ItemStack bracket() {
            ItemStack bracket = new ItemStack(Material.LIME_STAINED_GLASS_PANE, 1);
            bracket.editMeta(meta -> meta.displayName(MM.deserialize(services.messages().get("drop.zone-bracket"))));
            return bracket;
        }

        private String strictLabel() {
            return services.messages().get(strict ? "admin.toggle-strict" : "admin.toggle-loose");
        }

        private ItemStack button(Material material, String text) {
            ItemStack item = new ItemStack(material, 1);
            item.editMeta(meta -> meta.displayName(MM.deserialize(text)));
            return item;
        }
    }
}