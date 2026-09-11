package dev.ah.auctionhouse.gui;

import dev.ah.auctionhouse.AhServices;
import dev.ah.core.gui.ChestGui;
import dev.ah.core.listing.Listing;
import dev.ah.core.msg.SoundRegistry;
import dev.ah.core.saleslog.SalesLogRow;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AdminGui {
    private final AhServices services;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public AdminGui(AhServices services) {
        this.services = services;
    }

    public void open(Player admin) {
        open(admin, 0);
    }

    private void open(Player admin, int page) {
        int perPage = services.pageSize();
        long total = services.db().transact(c -> services.listings().countActiveOwners());
        int pages = Math.max(1, (int) Math.ceilDiv(total, perPage));
        final int pageIndex;
        if (page < 0) pageIndex = 0;
        else if (page >= pages) pageIndex = pages - 1;
        else pageIndex = page;

        ChestGui gui = new ChestGui(6, services.messages().get("admin.title",
                Map.of("page", String.valueOf(pageIndex + 1), "pages", String.valueOf(pages))));
        gui.fill(services.fillerItem());
        gui.on(0, () -> new AhMainGui(services).open(admin))
                .set(0, GuiItems.button(services, Material.SPECTRAL_ARROW, "gui.buttons.back"));

        ItemStack stats = new ItemStack(Material.BOOK, 1);
        long activeListings = services.db().transact(c -> services.listings().countActive());
        long pendingOffers = services.db().transact(c -> services.offers().countPending());
        long sales = services.db().transact(c -> services.sales().count());
        long unclaimed = services.db().transact(c -> services.claims().countUnclaimedTotal());
        stats.editMeta(meta -> {
            meta.displayName(MM.deserialize(services.messages().get("admin.stats-title")));
            meta.lore(List.of(
                    MM.deserialize(services.messages().get("admin.stats-listings", Map.of("count", String.valueOf(activeListings)))),
                    MM.deserialize(services.messages().get("admin.stats-offers", Map.of("count", String.valueOf(pendingOffers)))),
                    MM.deserialize(services.messages().get("admin.stats-sales", Map.of("count", String.valueOf(sales)))),
                    MM.deserialize(services.messages().get("admin.stats-claims", Map.of("count", String.valueOf(unclaimed))))));
        });
        gui.set(4, stats);

        List<UUID> owners = services.db().transact(c -> services.listings().activeOwners(perPage, pageIndex * perPage));
        Map<UUID, Long> listingCounts = services.db().transact(c -> services.listings().countActiveGroupedByOwner(owners));
        Map<UUID, Long> offerCounts = services.db().transact(c -> services.offers().countPendingGroupedBySellers(owners));

        int slot = 9;
        for (UUID uuid : owners) {
            if (slot > 44) break;
            String rawName = Bukkit.getOfflinePlayer(uuid).getName();
            String displayName = rawName != null ? rawName : uuid.toString().substring(0, 8);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
            long listings = listingCounts.getOrDefault(uuid, 0L);
            long offers = offerCounts.getOrDefault(uuid, 0L);
            head.editMeta(meta -> {
                meta.displayName(MM.deserialize("<gold>" + displayName));
                meta.lore(List.of(MM.deserialize(services.messages().get("admin.head-lore",
                        Map.of("listings", String.valueOf(listings), "offers", String.valueOf(offers))))));
            });
            gui.on(slot, clk -> openUser(admin, uuid, 0)).set(slot, head);
            slot++;
        }
        if (owners.isEmpty()) {
            gui.set(22, GuiItems.button(services, Material.PAPER, "admin.empty"));
        }

        gui.on(53, () -> open(admin, pageIndex + 1)).set(53, GuiItems.button(services, Material.ARROW,
                "gui.buttons.next", "gui.buttons.next-lore", Map.of()));
        gui.on(45, () -> open(admin, pageIndex - 1)).set(45, GuiItems.button(services, Material.ARROW,
                "gui.buttons.prev", "gui.buttons.prev-lore", Map.of()));
        gui.on(49, () -> openSalesLog(admin, 0)).set(49, GuiItems.button(services, Material.BOOK, "admin.sales.button"));
        services.sounds().play(admin, SoundRegistry.Event.OPEN);
        gui.open(admin);
    }

    void openUser(Player admin, UUID uuid, int page) {
        int perPage = services.pageSize();
        long total = services.db().transact(c -> services.listings().countBy(uuid));
        int totalPages = Math.max(1, (int) ((total + perPage - 1) / perPage));
        final int pageIndex;
        if (page < 0) pageIndex = 0;
        else if (page >= totalPages) pageIndex = totalPages - 1;
        else pageIndex = page;

        ChestGui gui = new ChestGui(6, services.messages().get("admin.user.title",
                Map.of("page", String.valueOf(pageIndex + 1), "pages", String.valueOf(totalPages))));
        gui.fill(services.fillerItem());
        gui.on(0, () -> open(admin)).set(0, GuiItems.button(services, Material.SPECTRAL_ARROW, "gui.buttons.back"));

        List<Listing> ownerListings = services.db().transact(c -> services.listings().byOwner(uuid, perPage, pageIndex * perPage));
        Map<Long, Long> pendingOffers = services.db().transact(c -> services.offers().countPendingByListings(
                ownerListings.stream().map(Listing::id).toList()));
        int slot = 9;
        for (Listing l : ownerListings) {
            if (slot > 44) break;
            ItemCache.CachedIcon cached = ItemCache.icon("l" + l.id(), l.itemData());
            if (cached == null) { slot++; continue; }
            ItemStack icon = cached.cloneItem();
            long offers = pendingOffers.getOrDefault(l.id(), 0L);
            icon.editMeta(m -> m.lore(List.of(MM.deserialize(services.messages().get("admin.user.listing", Map.of(
                    "id", String.valueOf(l.id()),
                    "status", l.status(),
                    "offers", String.valueOf(offers)))))));
            long listingId = l.id();
            gui.on(slot, clk -> new OfferBoardGui(services, listingId, admin).open());
            gui.set(slot, icon);
            slot++;
        }
        gui.on(53, () -> openUser(admin, uuid, pageIndex + 1)).set(53, GuiItems.button(services, Material.ARROW,
                "gui.buttons.next", "gui.buttons.next-lore", Map.of()));
        gui.on(45, () -> openUser(admin, uuid, pageIndex - 1)).set(45, GuiItems.button(services, Material.ARROW,
                "gui.buttons.prev", "gui.buttons.prev-lore", Map.of()));
        gui.open(admin);
    }

    public void openMyListings(Player player) {
        openUser(player, player.getUniqueId(), 0);
    }

    private void openSalesLog(Player admin, int page) {
        int perPage = services.pageSize();
        long total = services.db().transact(c -> services.sales().count());
        int pages = Math.max(1, (int) Math.ceilDiv(total, perPage));
        final int pageIndex;
        if (page < 0) pageIndex = 0;
        else if (page >= pages) pageIndex = pages - 1;
        else pageIndex = page;

        ChestGui gui = new ChestGui(6, services.messages().get("admin.sales.title",
                Map.of("page", String.valueOf(pageIndex + 1), "pages", String.valueOf(pages))));
        gui.fill(services.fillerItem());
        gui.on(0, () -> open(admin)).set(0, GuiItems.button(services, Material.SPECTRAL_ARROW, "gui.buttons.back"));
        int slot = 9;
        for (SalesLogRow row : services.db().transact(c -> services.sales().recent(perPage, pageIndex * perPage))) {
            if (slot > 44) break;
            ItemCache.CachedIcon cached = ItemCache.icon("s" + row.id(), row.itemData());
            if (cached == null) { slot++; continue; }
            ItemStack icon = cached.cloneItem();
            String seller = Bukkit.getOfflinePlayer(row.seller()).getName();
            String buyer = Bukkit.getOfflinePlayer(row.buyer()).getName();
            icon.editMeta(m -> m.lore(List.of(MM.deserialize(services.messages().get("admin.sales.row", Map.of(
                    "seller", seller == null ? "?" : seller,
                    "buyer", buyer == null ? "?" : buyer,
                    "outcome", row.outcome()))))));
            gui.set(slot, icon);
            slot++;
        }
        if (total == 0) {
            gui.set(22, GuiItems.button(services, Material.PAPER, "admin.sales.empty"));
        }
        gui.on(53, () -> openSalesLog(admin, pageIndex + 1)).set(53, GuiItems.button(services, Material.ARROW,
                "gui.buttons.next", "gui.buttons.next-lore", Map.of()));
        gui.on(45, () -> openSalesLog(admin, pageIndex - 1)).set(45, GuiItems.button(services, Material.ARROW,
                "gui.buttons.prev", "gui.buttons.prev-lore", Map.of()));
        gui.open(admin);
    }
}