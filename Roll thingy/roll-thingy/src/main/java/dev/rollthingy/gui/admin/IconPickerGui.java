package dev.rollthingy.gui.admin;

import dev.rollthingy.RollServices;
import dev.rollthingy.core.box.Box;
import dev.rollthingy.core.box.IconSpec;
import dev.rollthingy.core.gui.ChestGui;
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

    public IconPickerGui(RollServices services, Box box) {
        this.services = services;
        this.box = box;
    }

    private final Box box;

    public void open(Player admin) {
        ChestGui gui = new ChestGui(3, services.messages().get("admin.icon"));
        gui.fill(services.config().fillerItem());
        gui.fillRect(0, 8, null);
        gui.on(16, clk -> new EditGui(services).open(clk.player(), box))
                .set(16, button(Material.BARRIER, services.messages().get("admin.cancel")));
        // User places exactly one item in the top row; clicking it applies it as the icon.
        gui.on(22, clk -> apply(clk.player())).set(22, button(Material.EMERALD, services.messages().get("admin.confirm")));
        services.sounds().play(admin, SoundRegistry.Event.OPEN);
        gui.open(admin);
    }

    private void apply(Player admin) {
        for (int i = 0; i < 9; i++) {
            ItemStack item = admin.getOpenInventory().getTopInventory().getItem(i);
            if (item == null || item.getType().isAir()) continue;
            String data = ItemBundleCodec.encode(List.of(item.clone()));
            Box updated = new Box(box.id(), box.name(), new IconSpec(item.getType().name(), data), box.payment(),
                    box.penalty(), box.cooldownSeconds(), box.zonk(), box.tiers());
            services.cache().addOrUpdate(updated);
            admin.sendMessage(MM.deserialize(services.messages().get("admin.saved")));
            new EditGui(services).open(admin, updated);
            return;
        }
        admin.sendMessage(MM.deserialize(services.messages().get("admin.icon-prompt")));
        services.sounds().play(admin, SoundRegistry.Event.ERROR);
    }

    private ItemStack button(Material material, String text) {
        ItemStack item = new ItemStack(material, 1);
        item.editMeta(meta -> meta.displayName(MM.deserialize(text)));
        return item;
    }
}