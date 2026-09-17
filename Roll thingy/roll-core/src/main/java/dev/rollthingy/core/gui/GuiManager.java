package dev.rollthingy.core.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GuiManager {
    private static final ConcurrentHashMap<Player, DepositGui> OPEN_DEPOSITS = new ConcurrentHashMap<>();
    private final Plugin plugin;
    private final Map<Player, BukkitTask> pendingChanges = new HashMap<>();

    public GuiManager(Plugin plugin) {
        this.plugin = plugin;
    }

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
            if (topGui instanceof DepositGui deposit && event.getWhoClicked() instanceof Player p) {
                if (deposit.isDepositSlot(event.getSlot()) || event.isShiftClick()) {
                    scheduleChange(deposit, p);
                }
            }
            return;
        }
        int slot = event.getSlot();
        if (gui instanceof DepositGui deposit && deposit.isDepositSlot(slot)) {
            if (event.getWhoClicked() instanceof Player p) scheduleChange(deposit, p);
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
        if (event.getWhoClicked() instanceof Player p) scheduleChange(deposit, p);
    }

    /** After the click/drag settles, let the deposit screen re-render (e.g. a live luck gauge). */
    private void scheduleChange(DepositGui deposit, Player player) {
        BukkitTask old = pendingChanges.remove(player);
        if (old != null) old.cancel();
        pendingChanges.put(player, Bukkit.getScheduler().runTask(plugin, () -> {
            pendingChanges.remove(player);
            deposit.onChanged(player);
        }));
    }

    public void onPlayerQuit(Player player) {
        BukkitTask pending = pendingChanges.remove(player);
        if (pending != null) pending.cancel();
        DepositGui gui = OPEN_DEPOSITS.remove(player);
        if (gui != null) gui.cancelAndReturn(player);
    }

    public void returnAll() {
        for (BukkitTask task : pendingChanges.values()) task.cancel();
        pendingChanges.clear();
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