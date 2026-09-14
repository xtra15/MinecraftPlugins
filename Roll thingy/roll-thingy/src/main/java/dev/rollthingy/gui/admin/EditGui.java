package dev.rollthingy.gui.admin;

import dev.rollthingy.RollServices;
import dev.rollthingy.core.box.Box;
import dev.rollthingy.core.box.PaymentRequirement;
import dev.rollthingy.core.box.Penalty;
import dev.rollthingy.core.box.Zonk;
import dev.rollthingy.core.gui.DepositGui;
import dev.rollthingy.core.misc.ItemBundleCodec;
import dev.rollthingy.core.msg.SoundRegistry;
import dev.rollthingy.listener.ChatPrompt;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EditGui {
    private final RollServices services;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public EditGui(RollServices services) {
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
        private boolean strict = true;

        StemGui(RollServices services, Box box) {
            super(6, "<gold>Edit " + box.name(), 9, 17);
            this.services = services;
            this.box = box;
            draw();
        }

        private void draw() {
            fill(services.config().fillerItem());
            fillRect(9, 17, null);

            on(0, clk -> {
                cancelAndReturn(clk.player());
                new AdminGui(services).open(clk.player(), 0);
            }).set(0, button(Material.SPECTRAL_ARROW, "<gray>Back"));

            ItemStack icon = services.cache().icon(box);
            icon.editMeta(meta -> meta.lore(List.of(MM.deserialize("<gray>" + box.id()))));
            set(4, icon);

            on(45, clk -> savePayments(clk.player()))
                    .set(45, button(Material.EMERALD, "<green>Save payment items"));
            on(46, clk -> { strict = !strict; updateToggleLabel(); })
                    .set(46, button(Material.ARROW, strictLabel()));
            on(47, clk -> new RarityGui(services).open(clk.player(), box, Integer.MAX_VALUE))
                    .set(47, button(Material.DIAMOND, services.messages().get("admin.rarities")));
            on(48, clk -> rename(clk.player()))
                    .set(48, button(Material.NAME_TAG, services.messages().get("admin.rename")));
            on(49, clk -> new IconPickerGui(services).open(clk.player(), box))
                    .set(49, button(Material.ITEM_FRAME, services.messages().get("admin.icon")));
            on(50, clk -> penalty(clk.player()))
                    .set(50, button(Material.BLAZE_POWDER, services.messages().get("admin.penalty")));
            on(51, clk -> cooldown(clk.player()))
                    .set(51, button(Material.CLOCK, services.messages().get("admin.cooldown")));
            on(52, clk -> zonk(clk.player()))
                    .set(52, button(Material.BARRIER, services.messages().get("admin.zonk")));
            on(53, clk -> delete(clk.player()))
                    .set(53, button(Material.LAVA_BUCKET, services.messages().get("admin.delete")));
        }

        private String strictLabel() {
            return strict
                    ? services.messages().get("admin.toggle-strict", Map.of("strict", "true"))
                    : services.messages().get("admin.toggle-loose", Map.of("strict", "false"));
        }

        private void updateToggleLabel() {
            if (inventory() == null) return;
            ItemStack toggle = inventory().getItem(46);
            if (toggle == null) return;
            toggle.editMeta(meta -> meta.displayName(MM.deserialize(strictLabel())));
            inventory().setItem(46, toggle);
        }

        private void savePayments(Player admin) {
            List<ItemStack> items = collect();
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (ItemStack item : items) {
                String data = ItemBundleCodec.encode(List.of(item.clone()));
                counts.merge(data, 1, Integer::sum);
            }
            if (counts.isEmpty()) {
                admin.sendMessage(MM.deserialize(services.messages().get("spin.no-payment")));
                services.sounds().play(admin, SoundRegistry.Event.ERROR);
                return;
            }
            List<PaymentRequirement> payment = new ArrayList<>();
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                payment.add(new PaymentRequirement(e.getKey(), e.getValue(), strict));
            }
            box = mutate(b -> new Box(b.id(), b.name(), b.icon(), payment, b.penalty(), b.cooldownSeconds(), b.zonk(), b.tiers()));
            clearDeposit();
            admin.sendMessage(MM.deserialize(services.messages().get("admin.saved")));
            services.sounds().play(admin, SoundRegistry.Event.CONFIRM);
            admin.closeInventory();
            new EditGui(services).open(admin, box);
        }

        private void rename(Player admin) {
            admin.sendMessage(MM.deserialize(services.messages().get("admin.name-prompt")));
            ChatPrompt.prompt(admin, name -> {
                String clean = name.trim();
                if (clean.isEmpty()) return;
                box = mutate(b -> new Box(b.id(), clean, b.icon(), b.payment(), b.penalty(), b.cooldownSeconds(), b.zonk(), b.tiers()));
                admin.sendMessage(MM.deserialize(services.messages().get("admin.saved")));
                admin.closeInventory();
                new EditGui(services).open(admin, box);
            });
        }

        private void penalty(Player admin) {
            admin.sendMessage(MM.deserialize("<yellow>loose-value rare-cut zonk-feed"));
            ChatPrompt.prompt(admin, input -> {
                String[] parts = input.trim().split("\\s+");
                if (parts.length != 3) {
                    admin.sendMessage(MM.deserialize(services.messages().get("errors.unknown")));
                    return;
                }
                try {
                    double loose = Double.parseDouble(parts[0]);
                    double rare = Double.parseDouble(parts[1]);
                    double feed = Double.parseDouble(parts[2]);
                    box = mutate(b -> new Box(b.id(), b.name(), b.icon(), b.payment(), new Penalty(loose, rare, feed), b.cooldownSeconds(), b.zonk(), b.tiers()));
                    admin.sendMessage(MM.deserialize(services.messages().get("admin.saved")));
                    admin.closeInventory();
                    new EditGui(services).open(admin, box);
                } catch (NumberFormatException e) {
                    admin.sendMessage(MM.deserialize(services.messages().get("errors.unknown")));
                }
            });
        }

        private void cooldown(Player admin) {
            admin.sendMessage(MM.deserialize(services.messages().get("admin.cooldown")));
            ChatPrompt.prompt(admin, input -> {
                try {
                    long secs = Long.parseLong(input.trim());
                    box = mutate(b -> new Box(b.id(), b.name(), b.icon(), b.payment(), b.penalty(), secs, b.zonk(), b.tiers()));
                    admin.sendMessage(MM.deserialize(services.messages().get("admin.saved")));
                    admin.closeInventory();
                    new EditGui(services).open(admin, box);
                } catch (NumberFormatException e) {
                    admin.sendMessage(MM.deserialize(services.messages().get("errors.unknown")));
                }
            });
        }

        private void zonk(Player admin) {
            Zonk current = box.zonk() != null ? box.zonk() : new Zonk(false, 5.0);
            Zonk next = new Zonk(!current.enabled(), current.baseChance());
            box = mutate(b -> new Box(b.id(), b.name(), b.icon(), b.payment(), b.penalty(), b.cooldownSeconds(), next, b.tiers()));
            admin.sendMessage(MM.deserialize(services.messages().get("admin.saved")));
            admin.closeInventory();
            new EditGui(services).open(admin, box);
        }

        private void delete(Player admin) {
            new dev.rollthingy.gui.admin.ConfirmGui(services).open(admin, services.messages().get("admin.delete"), () -> {
                services.cache().remove(box.id());
                if (admin.isOnline()) {
                    admin.sendMessage(MM.deserialize(services.messages().get("admin.deleted")));
                    admin.closeInventory();
                    new AdminGui(services).open(admin, 0);
                }
            });
        }

        private void clearDeposit() {
            for (int i = 9; i <= 17; i++) inventory().setItem(i, null);
        }

        private Box mutate(java.util.function.Function<Box, Box> fn) {
            Box updated = fn.apply(box);
            services.cache().addOrUpdate(updated);
            return updated;
        }

        private ItemStack button(Material material, String text) {
            ItemStack item = new ItemStack(material, 1);
            item.editMeta(meta -> meta.displayName(MM.deserialize(text)));
            return item;
        }
    }
}