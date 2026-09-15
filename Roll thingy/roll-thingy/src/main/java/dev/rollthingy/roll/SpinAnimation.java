package dev.rollthingy.roll;

import dev.rollthingy.RollServices;
import dev.rollthingy.core.box.Box;
import dev.rollthingy.core.gui.ChestGui;
import dev.rollthingy.core.gui.SpinReel;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Horizontal marquee spin screen.
 *
 * <p>A 3-row ChestGui (so every click/drag is cancelled by {@link dev.rollthingy.core.gui.GuiManager})
 * renders the reel on the middle row (slots 9-17) with gold centre markers above (4) and below (22).
 * The reel is a prebuilt ring of {@link SpinReel#stopOffset ring} icons built once per spin; every
 * frame just re-places the same ItemStack instances, so there is no per-tick cloning or decoding.
 */
public class SpinAnimation {
    private final RollServices services;
    private final Player player;
    private final List<ItemStack> reel;
    private final Runnable onDone;
    private final int ringSize;
    private final int target;
    private final Inventory inv;
    private int position;

    public SpinAnimation(RollServices services, Player player, List<ItemStack> reel, Runnable onDone) {
        this.services = services;
        this.player = player;
        this.reel = reel;
        this.onDone = onDone;
        this.ringSize = reel.size();
        this.target = SpinReel.stopOffset(ringSize, 2);

        ChestGui gui = new ChestGui(3, services.messages().get("spin.title",
                java.util.Map.of("name", "<rolling>")));
        gui.fill(services.config().fillerItem());
        gui.open(player);
        this.inv = gui.inventory();
        inv.setItem(4, marker("▼"));
        inv.setItem(22, marker("▲"));
    }

    public void run() {
        draw();
        schedule();
    }

    /** Builds the 29-icon reel ring once per spin; the winner is always the last element. */
    public static List<ItemStack> buildReel(RollServices services, Box box, ItemStack winner) {
        List<ItemStack> pool = new ArrayList<>();
        for (var tier : box.tiers()) {
            for (var tierItem : tier.items()) {
                ItemStack icon = services.cache().item(tierItem.data());
                if (icon.getType() != Material.AIR) pool.add(icon);
            }
        }
        if (pool.isEmpty()) pool.add(new ItemStack(Material.STONE, 1));
        Random rng = new Random();
        int len = 29;
        List<ItemStack> reel = new ArrayList<>(len);
        for (int i = 0; i < len - 1; i++) {
            reel.add(pool.get(rng.nextInt(pool.size())).clone());
        }
        reel.add(winner.clone());
        return reel;
    }

    private void schedule() {
        Bukkit.getScheduler().runTaskLater(services.plugin(), this::step, SpinReel.delayFor(target - position));
    }

    private void step() {
        if (!player.isOnline() || inv == null) {
            onDone.run();
            return;
        }
        if (player.getOpenInventory().getTopInventory() != inv) {
            onDone.run();
            return;
        }
        int remaining = target - position;
        if (remaining <= 0) {
            draw();
            Bukkit.getScheduler().runTask(services.plugin(), () -> {
                player.closeInventory();
                onDone.run();
            });
            return;
        }
        position += Math.min(SpinReel.advanceFor(remaining), remaining);
        draw();
        Bukkit.getScheduler().runTaskLater(services.plugin(), this::step,
                SpinReel.delayFor(target - position));
    }

    private void draw() {
        for (int cell = 0; cell < SpinReel.VISIBLE; cell++) {
            int index = (position + cell) % ringSize;
            inv.setItem(9 + cell, reel.get(index));
        }
    }

    private ItemStack marker(String glyph) {
        ItemStack marker = new ItemStack(Material.GOLD_INGOT, 1);
        marker.editMeta(meta -> meta.displayName(MiniMessage.miniMessage().deserialize(
                "<gold>" + glyph)));
        return marker;
    }
}