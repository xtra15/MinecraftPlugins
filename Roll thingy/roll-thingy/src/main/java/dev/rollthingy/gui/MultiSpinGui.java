package dev.rollthingy.gui;

import dev.rollthingy.RollService;
import dev.rollthingy.RollServices;
import dev.rollthingy.core.box.Box;
import dev.rollthingy.core.misc.ItemBundleCodec;
import dev.rollthingy.core.gui.ChestGui;
import dev.rollthingy.core.msg.SoundRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * "Open N boxes?" popup. Holds the collected deposit clones in memory; closing
 * the window (X) without choosing returns everything, so items can never vanish.
 */
public class MultiSpinGui {
    private final RollServices services;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public MultiSpinGui(RollServices services) {
        this.services = services;
    }

    /**
     * @param deposit  full collected deposit (returned whole on cancel/X)
     * @param split    pre-carved spin chunks + leftover (leftover returned on confirm)
     * @param spins    number of boxes to open on confirm
     * @param needMore 0 for exact multiples; otherwise how many more units a further box needs
     */
    public void open(Player player, Box box, List<ItemStack> deposit, RollService.Split split,
                     int spins, int needMore, Runnable onConfirm) {
        AtomicBoolean done = new AtomicBoolean(false);
        boolean partial = needMore > 0;
        String title = partial
                ? services.messages().get("spin.multi-partial-title", Map.of("count", String.valueOf(spins)))
                : services.messages().get("spin.multi-title", Map.of("count", String.valueOf(spins)));
        ChestGui gui = new ChestGui(3, title);
        gui.fill(services.config().fillerItem());

        ItemStack info = services.cache().icon(box).clone();
        List<Component> lore = new ArrayList<>();
        if (partial) {
            lore.add(MM.deserialize(services.messages().get("spin.multi-need",
                    Map.of("need", String.valueOf(needMore), "next", String.valueOf(spins + 1)))));
        }
        for (int i = 0; i < split.spins().size(); i++) {
            int luck = services.roll().luckPercent(box, split.spins().get(i));
            lore.add(MM.deserialize(services.messages().get("spin.multi-luck",
                    Map.of("i", String.valueOf(i + 1), "luck", String.valueOf(luck)))));
        }
        lore.add(MM.deserialize(services.messages().get("spin.multi-note")));
        info.editMeta(meta -> {
            meta.displayName(MM.deserialize(title));
            meta.lore(lore);
        });
        gui.set(13, info);

        gui.on(11, clk -> {
            if (!done.compareAndSet(false, true)) return;
            services.sounds().play(player, SoundRegistry.Event.CONFIRM);
            onConfirm.run();
        }).set(11, button(Material.GREEN_WOOL, services.messages().get("spin.multi-confirm",
                Map.of("count", String.valueOf(spins)))));

        gui.on(15, clk -> {
            if (!done.compareAndSet(false, true)) return;
            services.sounds().play(player, SoundRegistry.Event.CANCEL);
            returnItems(services, player, deposit);
            player.closeInventory();
        }).set(15, button(Material.RED_WOOL, services.messages().get("spin.multi-cancel")));

        gui.onClose(() -> {
            if (!done.compareAndSet(false, true)) return;
            returnItems(services, player, deposit);
        });

        services.sounds().play(player, SoundRegistry.Event.OPEN);
        gui.open(player);
    }

    /** Gives items back; overflow goes to claims so nothing is ever lost. */
    public static void returnItems(RollServices services, Player player, List<ItemStack> items) {
        List<ItemStack> clean = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir() && item.getAmount() > 0) clean.add(item.clone());
        }
        if (clean.isEmpty()) return;
        Map<Integer, ItemStack> leftover =
                player.getInventory().addItem(clean.toArray(new ItemStack[0]));
        if (!leftover.isEmpty()) {
            services.claims().add(ItemBundleCodec.encode(List.copyOf(leftover.values())),
                    player.getUniqueId(), "RETURNED", System.currentTimeMillis());
            player.sendMessage(MM.deserialize(services.messages().get("claim.stored")));
        }
    }

    private ItemStack button(Material material, String text) {
        ItemStack item = new ItemStack(material, 1);
        item.editMeta(meta -> meta.displayName(MM.deserialize(text)));
        return item;
    }
}
