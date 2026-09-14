package dev.rollthingy.roll;

import dev.rollthingy.RollServices;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class SpinAnimation {
    private final RollServices services;
    private final Player player;
    private final List<ItemStack> strip;
    private final Runnable onDone;
    private final int winnerIndex;
    private final int positionTarget;
    private int position;

    public SpinAnimation(RollServices services, Player player, List<ItemStack> strip, Runnable onDone) {
        this.services = services;
        this.player = player;
        this.strip = strip;
        this.onDone = onDone;
        this.winnerIndex = strip.size() / 2;
        // A target position that is congruent to winnerIndex modulo strip.size().
        this.positionTarget = strip.size() * 3 + winnerIndex;
        this.position = 0;
    }

    public void run() {
        Inventory inv = Bukkit.createInventory(null, 27, MiniMessage.miniMessage().deserialize(
                services.messages().get("spin.title", java.util.Map.of("name", "<rolling>"))));
        player.openInventory(inv);
        // Pointer marker directly below the centre slot of the strip.
        ItemStack cursor = new ItemStack(Material.GOLD_INGOT, 1);
        cursor.editMeta(meta -> meta.displayName(MiniMessage.miniMessage().deserialize("<gold>▼")));
        inv.setItem(9 + winnerIndex + 9, cursor);
        draw(inv);
        schedule();
    }

    private void schedule() {
        Bukkit.getScheduler().runTaskLater(services.plugin(), this::step, 3L);
    }

    private void step() {
        if (!player.isOnline()) {
            onDone.run();
            return;
        }
        Inventory inv = player.getOpenInventory().getTopInventory();
        if (inv == null || inv.getSize() != 27) {
            onDone.run();
            return;
        }
        int remaining = positionTarget - position;
        if (remaining <= 0) {
            position = positionTarget;
            draw(inv);
            Bukkit.getScheduler().runTask(services.plugin(), () -> {
                player.closeInventory();
                onDone.run();
            });
            return;
        }
        int advance = Math.max(1, (int) Math.ceil(remaining / 6.0));
        position += advance;
        draw(inv);
        long delay = Math.min(8L, 3L + (positionTarget - position) / 2L);
        Bukkit.getScheduler().runTaskLater(services.plugin(), this::step, delay);
    }

    private void draw(Inventory inv) {
        int base = (position - winnerIndex) % strip.size();
        if (base < 0) base += strip.size();
        for (int i = 0; i < strip.size(); i++) {
            inv.setItem(9 + i, strip.get((base + i) % strip.size()));
        }
    }
}