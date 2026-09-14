package dev.rollthingy.gui.admin;

import dev.rollthingy.RollServices;
import dev.rollthingy.core.gui.ChestGui;
import dev.rollthingy.core.msg.SoundRegistry;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ConfirmGui {
    private final RollServices services;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public ConfirmGui(RollServices services) {
        this.services = services;
    }

    public void open(Player admin, String title, Runnable onConfirm) {
        ChestGui gui = new ChestGui(3, title);
        gui.fill(services.config().fillerItem());
        gui.on(11, clk -> onConfirm.run()).set(11, button(Material.GREEN_WOOL, services.messages().get("admin.confirm")));
        gui.on(15, clk -> clk.player().closeInventory()).set(15, button(Material.RED_WOOL, services.messages().get("admin.cancel")));
        services.sounds().play(admin, SoundRegistry.Event.OPEN);
        gui.open(admin);
    }

    private ItemStack button(Material material, String text) {
        ItemStack item = new ItemStack(material, 1);
        item.editMeta(meta -> meta.displayName(MM.deserialize(text)));
        return item;
    }
}