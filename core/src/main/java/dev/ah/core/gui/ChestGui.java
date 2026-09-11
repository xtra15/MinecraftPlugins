package dev.ah.core.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ChestGui {
    public record Click(Player player, int slot) {}
    public record Slot(ItemStack item, Consumer<Click> action) {}

    private static final Map<Inventory, ChestGui> OPEN = new ConcurrentHashMap<>();
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final int rows;
    private final Map<Integer, Slot> slots = new LinkedHashMap<>();
    private Inventory inventory;
    private Component title;

    public ChestGui(int rows, String title) {
        this.rows = rows;
        this.title = MM.deserialize(title);
    }

    public ChestGui title(String title) {
        this.title = MM.deserialize(title);
        return this;
    }

    public ChestGui set(int slot, ItemStack item) {
        requireSlot(slot);
        Slot existing = slots.get(slot);
        slots.put(slot, new Slot(item, existing != null ? existing.action() : clk -> {}));
        return this;
    }

    public ChestGui on(int slot, Runnable action) {
        return on(slot, clk -> action.run());
    }

    public ChestGui on(int slot, Consumer<Click> action) {
        requireSlot(slot);
        Slot existing = slots.get(slot);
        slots.put(slot, new Slot(existing != null ? existing.item() : null, action));
        return this;
    }

    public void fill(ItemStack filler) {
        for (int i = 0; i < rows * 9; i++) {
            slots.putIfAbsent(i, new Slot(filler, clk -> {}));
        }
    }

    public void fillRect(int fromSlot, int toSlot, ItemStack filler) {
        requireSlot(fromSlot);
        requireSlot(toSlot);
        for (int i = fromSlot; i <= toSlot; i++) {
            if (filler == null) {
                slots.remove(i);
            } else {
                slots.putIfAbsent(i, new Slot(filler, clk -> {}));
            }
        }
    }

    private void requireSlot(int slot) {
        if (slot < 0 || slot >= rows * 9) {
            throw new IllegalArgumentException("slot " + slot + " out of range for a " + rows + "-row gui");
        }
    }

    public void open(Player player) {
        if (inventory == null) {
            inventory = Bukkit.createInventory(null, rows * 9, title);
            for (Map.Entry<Integer, Slot> e : slots.entrySet()) {
                inventory.setItem(e.getKey(), e.getValue().item());
            }
        }
        OPEN.put(inventory, this);
        player.openInventory(inventory);
    }

    public Inventory inventory() {
        return inventory;
    }

    public String title() {
        return title.toString();
    }

    public Map<Integer, Slot> slots() {
        return Collections.unmodifiableMap(slots);
    }

    public static ChestGui of(Inventory inventory) {
        return OPEN.get(inventory);
    }

    public static void close(Inventory inventory) {
        if (inventory != null) OPEN.remove(inventory);
    }
}