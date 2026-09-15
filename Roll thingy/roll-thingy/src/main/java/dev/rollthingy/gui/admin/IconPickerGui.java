package dev.rollthingy.gui.admin;

import dev.rollthingy.RollServices;
import dev.rollthingy.core.box.Box;
import dev.rollthingy.core.box.IconSpec;
import dev.rollthingy.core.gui.DepositGui;
import dev.rollthingy.core.misc.ItemBundleCodec;
import dev.rollthingy.core.msg.SoundRegistry;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

public class IconPickerGui {
    private final RollServices services;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public IconPickerGui(RollServices services) {
        this.services = services;
    }

    public void open(Player admin, Box box) {
        StemGui gui = new StemGui(services, box);
        services.gui().registerOpen(admin, gui);
        services.sounds().play(admin, SoundRegistry.Event.OPEN);
        gui.open(admin);
    }

    /**
     * DepositGui-based picker: top row (slots 0-8) accepts the icon item; current icon preview at
     * 13; confirm at 26, cancel at 18.
     */
    private static final class StemGui extends DepositGui {
        private final RollServices services;
        private final Box box;

        StemGui(RollServices services, Box box) {
            super(3, services.messages().get("admin.icon"), 0, 8);
            this.services = services;
            this.box = box;
            draw();
        }

        private void draw() {
            fill(services.config().fillerItem());
            fillRect(0, 8, null);

            set(9, bracket());
            set(17, bracket());
            set(10, howTo());

            ItemStack current = services.cache().icon(box);
            String material = iconMaterial(box);
            current.editMeta(meta -> {
                meta.displayName(MM.deserialize(services.messages().get("admin.icon-current",
                        Map.of("material", material))));
                meta.lore(List.of(
                        MM.deserialize(services.messages().get("admin.icon-prompt")),
                        MM.deserialize("<gray>Drop a replacement in the <green>green row")));
            });
            set(13, current);

            on(18, clk -> {
                cancelAndReturn(clk.player());
                new EditGui(services).open(clk.player(), box);
            }).set(18, button(Material.BARRIER, services.messages().get("admin.cancel")));
            on(26, clk -> apply(clk.player())).set(26, button(Material.EMERALD, services.messages().get("admin.confirm")));
        }

        private static String iconMaterial(Box box) {
            if (box.icon() != null && box.icon().material() != null && !box.icon().material().isBlank()) {
                return box.icon().material().toLowerCase().replace('_', ' ');
            }
            return "chest";
        }

        private ItemStack howTo() {
            ItemStack how = new ItemStack(Material.BOOK, 1);
            how.editMeta(meta -> {
                meta.displayName(MM.deserialize(services.messages().get("drop.banner")));
                meta.lore(List.of(
                        MM.deserialize(services.messages().get("drop.step-price")),
                        MM.deserialize(services.messages().get("drop.step-confirm"))));
            });
            return how;
        }

        private ItemStack bracket() {
            ItemStack bracket = new ItemStack(Material.LIME_STAINED_GLASS_PANE, 1);
            bracket.editMeta(meta -> meta.displayName(MM.deserialize(services.messages().get("drop.zone-bracket"))));
            return bracket;
        }

        private void apply(Player admin) {
            List<ItemStack> strip = collect();
            if (strip.isEmpty()) {
                admin.sendMessage(MM.deserialize(services.messages().get("admin.icon-prompt")));
                services.sounds().play(admin, SoundRegistry.Event.ERROR);
                return;
            }
            ItemStack item = strip.get(0);
            String data = ItemBundleCodec.encode(List.of(item));
            Box updated = new Box(box.id(), box.name(), new IconSpec(item.getType().name(), data), box.payment(),
                    box.penalty(), box.cooldownSeconds(), box.zonk(), box.tiers());
            services.cache().addOrUpdate(updated);
            markConfirmed();
            clearDeposit();
            for (int i = 1; i < strip.size(); i++) {
                admin.getInventory().addItem(strip.get(i));
            }
            admin.sendMessage(MM.deserialize(services.messages().get("admin.saved")));
            services.sounds().play(admin, SoundRegistry.Event.CONFIRM);
            admin.closeInventory();
            new EditGui(services).open(admin, updated);
        }

        private void clearDeposit() {
            if (inventory() == null) return;
            for (int i = 0; i <= 8; i++) inventory().setItem(i, null);
        }

        private ItemStack button(Material material, String text) {
            ItemStack item = new ItemStack(material, 1);
            item.editMeta(meta -> meta.displayName(MM.deserialize(text)));
            return item;
        }
    }
}