package dev.rollthingy.gui;

import dev.rollthingy.RollServices;
import dev.rollthingy.core.gui.ChestGui;
import dev.rollthingy.core.misc.ItemBundleCodec;
import dev.rollthingy.core.msg.SoundRegistry;
import dev.rollthingy.core.store.ClaimRow;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class ClaimGui {
    private final RollServices services;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public ClaimGui(RollServices services) {
        this.services = services;
    }

    public void open(Player player, int page) {
        int perPage = services.config().pageSize();
        long total = services.claims().countUnclaimed(player.getUniqueId());
        int totalPages = Math.max(1, (int) ((total + perPage - 1) / perPage));
        int pageIndex = Math.max(0, Math.min(page, totalPages - 1));
        List<ClaimRow> rows = services.claims().unclaimedPage(player.getUniqueId(), perPage, pageIndex * perPage);

        ChestGui gui = new ChestGui(6, services.messages().get("claim.title",
                Map.of("page", String.valueOf(pageIndex + 1), "pages", String.valueOf(totalPages))));
        gui.fill(services.config().fillerItem());
        gui.fillRect(9, 44, null);

        if (total == 0) {
            ItemStack empty = new ItemStack(Material.PAPER, 1);
            empty.editMeta(meta -> meta.displayName(MM.deserialize(services.messages().get("claim.empty"))));
            gui.set(22, empty);
        }

        int slot = 9;
        for (ClaimRow row : rows) {
            if (slot > 44) break;
            List<ItemStack> items = ItemBundleCodec.decode(row.itemsData());
            if (items.isEmpty()) { slot++; continue; }
            ItemStack icon = items.get(0).clone();
            int count = items.size();
            icon.editMeta(meta -> {
                meta.displayName(MM.deserialize(services.messages().get("claim.row-title", Map.of(
                        "count", String.valueOf(count),
                        "date", new SimpleDateFormat("MMM d HH:mm").format(new Date(row.createdAt()))))));
                List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                if (count > 1) {
                    lore.add(MM.deserialize(services.messages().get("claim.more",
                            Map.of("extra", String.valueOf(count - 1)))));
                }
                meta.lore(lore);
            });
            long rowId = row.id();
            gui.on(slot, clk -> withdraw(clk.player(), rowId));
            gui.set(slot, icon);
            slot++;
        }

        gui.on(0, clk -> { clk.player().closeInventory(); new MainGui(services).open(clk.player(), 0); })
                .set(0, button(Material.SPECTRAL_ARROW, "<gray>Back"));
        gui.on(45, clk -> {
            services.sounds().play(clk.player(), SoundRegistry.Event.CLICK);
            open(clk.player(), pageIndex - 1);
        }).set(45, button(Material.ARROW, "<gray><<"));
        gui.on(53, clk -> {
            services.sounds().play(clk.player(), SoundRegistry.Event.CLICK);
            open(clk.player(), pageIndex + 1);
        }).set(53, button(Material.ARROW, ">>"));
        gui.on(49, clk -> clk.player().closeInventory()).set(49, button(Material.BARRIER, "<red>Close"));
        gui.on(50, clk -> collectAll(clk.player())).set(50, button(Material.HOPPER,
                services.messages().get("claim.collect")));
        services.sounds().play(player, SoundRegistry.Event.OPEN);
        gui.open(player);
    }

    private void collectAll(Player player) {
        int perPage = services.config().pageSize();
        long total = services.claims().countUnclaimed(player.getUniqueId());
        if (total == 0) return;
        List<ClaimRow> rows = new ArrayList<>();
        for (int offset = 0; offset < total; offset += perPage) {
            rows.addAll(services.claims().unclaimedPage(player.getUniqueId(), perPage, offset));
        }
        List<ItemStack> all = new ArrayList<>();
        for (ClaimRow row : rows) all.addAll(ItemBundleCodec.decode(row.itemsData()));
        int freeSlots = 0;
        for (ItemStack s : player.getInventory().getStorageContents()) {
            if (s == null || s.getType().isAir()) freeSlots++;
        }
        if (freeSlots < all.size()) {
            player.sendMessage(MM.deserialize(services.messages().get("claim.no-space")));
            services.sounds().play(player, SoundRegistry.Event.ERROR);
            return;
        }
        player.getInventory().addItem(all.toArray(new ItemStack[0]));
        long now = System.currentTimeMillis();
        for (ClaimRow row : rows) services.claims().markClaimed(row.id(), now);
        player.sendMessage(MM.deserialize(services.messages().get("claim.withdrawn")));
        services.sounds().play(player, SoundRegistry.Event.CONFIRM);
        open(player, 0);
    }

    private void withdraw(Player player, long rowId) {
        List<ClaimRow> matches = allRows(player);
        for (ClaimRow row : matches) {
            if (row.id() != rowId) continue;
            List<ItemStack> items = ItemBundleCodec.decode(row.itemsData());
            int freeSlots = 0;
            for (ItemStack s : player.getInventory().getStorageContents()) {
                if (s == null || s.getType().isAir()) freeSlots++;
            }
            if (freeSlots < items.size()) {
                player.sendMessage(MM.deserialize(services.messages().get("claim.no-space")));
                services.sounds().play(player, SoundRegistry.Event.ERROR);
                return;
            }
            player.getInventory().addItem(items.toArray(new ItemStack[0]));
            services.claims().markClaimed(rowId, System.currentTimeMillis());
            player.sendMessage(MM.deserialize(services.messages().get("claim.withdrawn")));
            services.sounds().play(player, SoundRegistry.Event.CONFIRM);
            open(player, 0);
            return;
        }
    }

    private List<ClaimRow> allRows(Player player) {
        List<ClaimRow> out = new ArrayList<>();
        int perPage = services.config().pageSize();
        long total = services.claims().countUnclaimed(player.getUniqueId());
        for (int offset = 0; offset < total; offset += perPage) {
            out.addAll(services.claims().unclaimedPage(player.getUniqueId(), perPage, offset));
        }
        return out;
    }

    private ItemStack button(Material material, String text) {
        ItemStack item = new ItemStack(material, 1);
        item.editMeta(meta -> meta.displayName(MM.deserialize(text)));
        return item;
    }
}