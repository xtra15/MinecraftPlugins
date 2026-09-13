package dev.ah.auctionhouse.gui;

import dev.ah.auctionhouse.AhServices;
import dev.ah.core.claim.ClaimRow;
import dev.ah.core.gui.ChestGui;
import dev.ah.core.misc.ItemBundleCodec;
import dev.ah.core.msg.SoundRegistry;
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
    private final AhServices services;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public ClaimGui(AhServices services) {
        this.services = services;
    }

    public void open(Player player, int page) {
        int perPage = services.pageSize();
        long total = services.claims().countUnclaimed(player.getUniqueId());
        int totalPages = Math.max(1, (int) ((total + perPage - 1) / perPage));

        final int pageIndex;
        if (page < 0) pageIndex = 0;
        else if (page >= totalPages) pageIndex = totalPages - 1;
        else pageIndex = page;

        List<ClaimRow> rows = services.claims().unclaimedPage(player.getUniqueId(), perPage, pageIndex * perPage);

        ChestGui gui = new ChestGui(6, services.messages().get("claims.title",
                Map.of("page", String.valueOf(pageIndex + 1), "pages", String.valueOf(totalPages))));
        gui.fillRect(45, 53, services.fillerItem());
        gui.on(0, () -> new AhMainGui(services).open(player))
                .set(0, GuiItems.button(services, Material.SPECTRAL_ARROW, "gui.buttons.back"));

        if (total == 0) {
            gui.set(22, GuiItems.button(services, Material.PAPER, "claims.empty"));
        }

        int slot = 9;
        for (ClaimRow row : rows) {
            if (slot > 44) break;
            ItemCache.CachedIcon cached = ItemCache.icon("c" + row.id(), row.itemsData());
            if (cached == null) { slot++; continue; }
            ItemStack icon = cached.cloneItem();
            int count = cached.count();
            icon.editMeta(meta -> {
                meta.displayName(MM.deserialize(services.messages().get("claims.row", Map.of(
                        "count", String.valueOf(count),
                        "date", new SimpleDateFormat("MMM d HH:mm").format(new Date(row.createdAt()))))));
                if (count > 1) {
                    meta.lore(List.of(MM.deserialize(services.messages().get("claims.more",
                            Map.of("extra", String.valueOf(count - 1))))));
                } else {
                    meta.lore(List.of());
                }
            });
            final long rowId = row.id();
            gui.on(slot, clk -> withdraw(player, rowId));
            gui.set(slot, icon);
            slot++;
        }

        gui.on(53, () -> open(player, pageIndex + 1)).set(53, GuiItems.button(services, Material.ARROW,
                "gui.buttons.next", "gui.buttons.next-lore", Map.of()));
        gui.on(45, () -> open(player, pageIndex - 1)).set(45, GuiItems.button(services, Material.ARROW,
                "gui.buttons.prev", "gui.buttons.prev-lore", Map.of()));
        gui.on(49, () -> player.closeInventory()).set(49, GuiItems.button(services, Material.BARRIER, "gui.buttons.close"));
        gui.on(50, () -> collectAll(player)).set(50, GuiItems.button(services, Material.HOPPER,
                "gui.buttons.claim-all", "gui.buttons.claim-all-lore", Map.of()));
        services.sounds().play(player, SoundRegistry.Event.OPEN);
        gui.open(player);
    }

    private void collectAll(Player player) {
        List<ClaimRow> rows = services.claims().unclaimedFor(player.getUniqueId());
        List<ItemStack> all = new ArrayList<>();
        for (ClaimRow row : rows) {
            all.addAll(ItemBundleCodec.decode(row.itemsData()));
        }
        if (all.isEmpty()) {
            player.sendMessage(MM.deserialize(services.messages().get("claims.empty")));
            services.sounds().play(player, SoundRegistry.Event.ERROR);
            return;
        }
        int freeSlots = 0;
        for (ItemStack s : player.getInventory().getStorageContents()) {
            if (s == null || s.getType().isAir()) freeSlots++;
        }
        if (freeSlots < all.size()) {
            player.sendMessage(MM.deserialize(services.messages().get("claims.no-space")));
            services.sounds().play(player, SoundRegistry.Event.ERROR);
            return;
        }
        player.getInventory().addItem(all.toArray(new ItemStack[0]));
        long now = System.currentTimeMillis();
        for (ClaimRow row : rows) {
            services.claims().markClaimed(row.id(), now);
        }
        player.sendMessage(MM.deserialize(services.messages().get("claims.withdrawn")));
        services.sounds().play(player, SoundRegistry.Event.CLICK);
        open(player, 0);
    }

    private void withdraw(Player player, long rowId) {
        var row = services.claims().byId(rowId).orElse(null);
        if (row == null) return;
        List<ItemStack> items = ItemBundleCodec.decode(row.itemsData());
        int freeSlots = 0;
        for (ItemStack s : player.getInventory().getStorageContents()) {
            if (s == null || s.getType().isAir()) freeSlots++;
        }
        if (freeSlots < items.size()) {
            player.sendMessage(MM.deserialize(services.messages().get("claims.no-space")));
            services.sounds().play(player, SoundRegistry.Event.ERROR);
            return;
        }
        for (ItemStack item : items) {
            player.getInventory().addItem(item);
        }
        services.claims().markClaimed(rowId, System.currentTimeMillis());
        player.sendMessage(MM.deserialize(services.messages().get("claims.withdrawn")));
        services.sounds().play(player, SoundRegistry.Event.CLICK);
        open(player, 0);
    }
}