package dev.ah.core.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import java.util.concurrent.ConcurrentHashMap;

public class GuiManager {
    private static final ConcurrentHashMap<Player, DepositGui> OPEN_DEPOSITS = new ConcurrentHashMap<>();

    public void registerOpen(Player player, DepositGui gui) {
        OPEN_DEPOSITS.put(player, gui);
    }

    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player p) {
            OPEN_DEPOSITS.remove(p);
        }
        ChestGui.close(event.getInventory());
    }

    public void onClick(InventoryClickEvent event) {
        Inventory inv = event.getClickedInventory();
        if (inv == null) return;
        ChestGui gui = ChestGui.of(inv);
        if (gui == null) return;
        event.setCancelled(true);
        int slot = event.getSlot();
        ChestGui.Slot slotDef = gui.slots().get(slot);
        if (slotDef != null && slotDef.action() != null && event.getWhoClicked() instanceof Player p) {
            slotDef.action().accept(new ChestGui.Click(p, slot));
        }
    }

    public void onPlayerQuit(Player player) {
        DepositGui gui = OPEN_DEPOSITS.remove(player);
        if (gui != null) gui.cancelAndReturn(player);
    }
}