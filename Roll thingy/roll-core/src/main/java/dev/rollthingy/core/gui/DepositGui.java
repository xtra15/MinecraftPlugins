package dev.rollthingy.core.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.List;

public class DepositGui extends ChestGui {
    private final int minSlot;
    private final int maxSlot;
    private boolean confirmed;
    private boolean returned;

    public DepositGui(int rows, String title, int minSlot, int maxSlot) {
        super(rows, title);
        this.minSlot = minSlot;
        this.maxSlot = maxSlot;
        this.confirmed = false;
        this.returned = false;
    }

    public List<ItemStack> collect() {
        List<ItemStack> out = new ArrayList<>();
        if (inventory() == null) return out;
        for (int i = minSlot; i <= maxSlot; i++) {
            ItemStack item = inventory().getItem(i);
            if (item != null && item.getType() != Material.AIR) out.add(item.clone());
        }
        return out;
    }

    public void returnItems(Player player) {
        if (returned || inventory() == null) return;
        for (int i = minSlot; i <= maxSlot; i++) {
            ItemStack item = inventory().getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                player.getInventory().addItem(item);
            }
        }
        returned = true;
    }

    public boolean cancelAndReturn(Player player) {
        returnItems(player);
        player.closeInventory();
        return true;
    }

    public boolean isDepositSlot(int slot) {
        return slot >= minSlot && slot <= maxSlot;
    }

    public void markConfirmed() {
        this.confirmed = true;
    }

    public boolean isConfirmed() {
        return confirmed;
    }
}