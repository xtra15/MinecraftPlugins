package dev.ah.core.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import java.util.concurrent.ConcurrentHashMap;

public class GuiManager {
    private static final ConcurrentHashMap<Player, DepositGui> OPEN_DEPOSITS = new ConcurrentHashMap<>();

    public void registerOpen(Player player, DepositGui gui) {
        OPEN_DEPOSITS.put(player, gui);
    }

    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player p) {
            DepositGui deposit = OPEN_DEPOSITS.remove(p);
            if (deposit != null && !deposit.isConfirmed()) {
                deposit.returnItems(p);
            }
        }
        ChestGui.close(event.getInventory());
    }

    public void onClick(InventoryClickEvent event) {
        Inventory inv = event.getClickedInventory();
        if (inv == null) return;
        ChestGui gui = ChestGui.of(inv);
        if (gui == null) {
            Inventory top = event.getView().getTopInventory();
            ChestGui topGui = top == null ? null : ChestGui.of(top);
            if (event.isShiftClick() && topGui != null && !(topGui instanceof DepositGui)) {
                event.setCancelled(true);
            }
            return;
        }
        int slot = event.getSlot();
        if (gui instanceof DepositGui deposit && deposit.isDepositSlot(slot)) {
            return;
        }
        event.setCancelled(true);
        ChestGui.Slot slotDef = gui.slots().get(slot);
        if (slotDef != null && slotDef.action() != null && event.getWhoClicked() instanceof Player p) {
            slotDef.action().accept(new ChestGui.Click(p, slot));
        }
    }

    public void onDrag(InventoryDragEvent event) {
        ChestGui gui = ChestGui.of(event.getView().getTopInventory());
        if (gui == null) return;
        if (!(gui instanceof DepositGui deposit)) {
            event.setCancelled(true);
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        for (int raw : event.getRawSlots()) {
            if (raw < topSize && !deposit.isDepositSlot(raw)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    public void onPlayerQuit(Player player) {
        DepositGui gui = OPEN_DEPOSITS.remove(player);
        if (gui != null) gui.cancelAndReturn(player);
    }

    public void returnAll() {
        for (java.util.Map.Entry<Player, DepositGui> e : OPEN_DEPOSITS.entrySet()) {
            DepositGui deposit = e.getValue();
            if (deposit != null && !deposit.isConfirmed()) {
                deposit.returnItems(e.getKey());
                e.getKey().closeInventory();
            }
        }
        OPEN_DEPOSITS.clear();
    }
}