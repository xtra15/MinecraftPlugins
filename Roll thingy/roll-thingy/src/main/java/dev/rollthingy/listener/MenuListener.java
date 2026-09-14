package dev.rollthingy.listener;

import dev.rollthingy.core.gui.GuiManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class MenuListener implements Listener {
    private final GuiManager gui;

    public MenuListener(GuiManager gui) {
        this.gui = gui;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        gui.onClick(e);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        gui.onDrag(e);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        gui.onClose(e);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        gui.onPlayerQuit(e.getPlayer());
    }
}