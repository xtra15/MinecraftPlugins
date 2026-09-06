package dev.ah.core.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.List;

public class DepositGui extends ChestGui {
    private final int minSlot;
    private final int maxSlot;
    private boolean confirmed;

    public DepositGui(int rows, String title, int minSlot, int maxSlot) {
        super(rows, title);
        this.minSlot = minSlot;
        this.maxSlot = maxSlot;
        this.confirmed = false;
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

    public boolean cancelAndReturn(Player player) {
        if (inventory() == null) return false;
        for (int i = minSlot; i <= maxSlot; i++) {
            ItemStack item = inventory().getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                player.getInventory().addItem(item);
            }
        }
        player.closeInventory();
        return true;
    }

    public void markConfirmed() {
        this.confirmed = true;
    }

    public boolean isConfirmed() {
        return confirmed;
    }
}