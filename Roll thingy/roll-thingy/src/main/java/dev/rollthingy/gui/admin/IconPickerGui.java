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

    /** DepositGui-based picker: top row (slots 0-8) accepts the icon item; confirm at 22. */
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

            ItemStack current = services.cache().icon(box);
            current.editMeta(meta -> meta.lore(List.of(MM.deserialize(services.messages().get("admin.icon-prompt")))));
            set(13, current);

            on(16, clk -> {
                cancelAndReturn(clk.player());
                new EditGui(services).open(clk.player(), box);
            }).set(16, button(Material.BARRIER, services.messages().get("admin.cancel")));
            on(22, clk -> apply(clk.player())).set(22, button(Material.EMERALD, services.messages().get("admin.confirm")));
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