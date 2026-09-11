package dev.ah.auctionhouse.gui;

import dev.ah.auctionhouse.AhServices;
import dev.ah.auctionhouse.listener.ChatSearchListener;
import dev.ah.core.gui.ChestGui;
import dev.ah.core.listing.Listing;
import dev.ah.core.msg.SoundRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AhMainGui {
    private final AhServices services;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Map<UUID, String> SEARCH = new ConcurrentHashMap<>();
    private static final Map<UUID, String> SORT = new ConcurrentHashMap<>();
    private String sort = "newest";
    private String search = "";

    public AhMainGui(AhServices services) {
        this.services = services;
    }

    public void open(Player player) {
        open(player, 0);
    }

    public void openSearch(Player player, String term) {
        String t = term == null ? "" : term.toLowerCase(Locale.ROOT);
        SEARCH.put(player.getUniqueId(), t);
        this.search = t;
        open(player, 0);
    }

    public void openSorted(Player player, String sort, String search, int page) {
        SORT.put(player.getUniqueId(), sort);
        if (search != null) SEARCH.put(player.getUniqueId(), search.toLowerCase(Locale.ROOT));
        this.sort = sort;
        this.search = search == null ? null : search.toLowerCase(Locale.ROOT);
        open(player, page);
    }

    private void open(Player player, int page) {
        search = SEARCH.getOrDefault(player.getUniqueId(), "");
        sort = SORT.getOrDefault(player.getUniqueId(), "newest");
        boolean searching = search != null && !search.isBlank();
        String title = searching
                ? services.messages().get("gui.main.search-title", Map.of("term", search))
                : services.messages().get("gui.main.title");
        String needle = (search == null || search.isBlank()) ? null : search;
        boolean oldest = "oldest".equals(sort);
        int pageSize = services.pageSize();
        long total = services.listings().countActive(needle);
        int pages = Math.max(1, (int) Math.ceilDiv(total, pageSize));
        int pageIndex = Math.max(0, Math.min(page, pages - 1));
        List<Listing> pageRows = services.listings().activePage(pageSize, pageIndex * pageSize, needle, oldest);

        ChestGui gui = new ChestGui(6, title);
        gui.fillRect(45, 53, services.fillerItem());

        ItemStack stats = new ItemStack(Material.BOOK, 1);
        long myListings = services.listings().countActiveBy(player.getUniqueId());
        long myOffers = services.offers().countPendingByOfferer(player.getUniqueId());
        long unclaimed = services.claims().countUnclaimed(player.getUniqueId());
        stats.editMeta(meta -> {
            meta.displayName(MM.deserialize(services.messages().get("gui.main.stats-title")));
            meta.lore(List.of(
                    MM.deserialize(services.messages().get("gui.main.stats-listings", Map.of("count", String.valueOf(myListings)))),
                    MM.deserialize(services.messages().get("gui.main.stats-offers", Map.of("count", String.valueOf(myOffers)))),
                    MM.deserialize(services.messages().get("gui.main.stats-claims", Map.of("count", String.valueOf(unclaimed))))));
        });
        gui.set(0, stats);

        int slot = 9;
        Map<Long, Long> pendingOffers = services.offers()
                .countPendingByListings(pageRows.stream().map(Listing::id).toList());
        for (Listing listing : pageRows) {
            if (slot > 44) break;
            ItemCache.CachedIcon cached = ItemCache.icon("l" + listing.id(), listing.itemData());
            if (cached == null) { slot++; continue; }
            ItemStack icon = cached.cloneItem();
            long offers = pendingOffers.getOrDefault(listing.id(), 0L);
            String price = services.isEconomyEnabled() && listing.price() != null
                    ? String.valueOf(listing.price()) : "—";
            String remaining = IconUtil.humanize(listing.expiresAt() - System.currentTimeMillis());
            List<Component> lore = List.of(
                    MM.deserialize(services.messages().get("listings.lore.price", Map.of("price", price))),
                    MM.deserialize(services.messages().get("listings.lore.offers", Map.of("count", String.valueOf(offers)))),
                    MM.deserialize(services.messages().get("listings.lore.remaining",
                            Map.of("time", remaining, "minutes", remaining, "days", remaining))));
            icon.editMeta(meta -> meta.lore(lore));
            long listingId = listing.id();
            gui.on(slot, clk -> { clk.player().closeInventory(); new OfferGui(services, listingId).open(clk.player()); });
            gui.set(slot, icon);
            slot++;
        }
        if (total == 0) {
            gui.set(22, GuiItems.button(services, Material.PAPER, "gui.main.empty"));
        }

        gui.on(53, () -> open(player, pageIndex + 1)).set(53, GuiItems.button(services, Material.ARROW,
                "gui.buttons.next", "gui.buttons.next-lore", Map.of()));
        gui.on(45, () -> open(player, pageIndex - 1)).set(45, GuiItems.button(services, Material.ARROW,
                "gui.buttons.prev", "gui.buttons.prev-lore", Map.of()));
        if (searching) {
            gui.on(47, clk -> {
                SEARCH.remove(player.getUniqueId());
                open(player, pageIndex);
            }).set(47, GuiItems.button(services, Material.BARRIER, "gui.buttons.clear-search"));
        } else {
            gui.on(47, clk -> {
                clk.player().closeInventory();
                ChatSearchListener.prompt(clk.player(), services.messages().get("gui.buttons.search-prompt"));
            }).set(47, GuiItems.button(services, Material.OAK_SIGN,
                    "gui.buttons.search", "gui.buttons.search-lore", Map.of()));
        }
        String sortState = sort.equals("oldest") ? "<red>oldest" : "<green>newest";
        ItemStack sortBtn = new ItemStack(Material.COMPASS, 1);
        sortBtn.editMeta(meta -> {
            meta.displayName(MM.deserialize(services.messages().get("gui.buttons.sort")));
            meta.lore(List.of(MM.deserialize(services.messages().get("gui.buttons.sort-state", Map.of("state", sortState)))));
        });
        gui.on(48, () -> { toggleSort(player); open(player, pageIndex); }).set(48, sortBtn);
        gui.on(49, () -> new SellGui(services).open(player)).set(49, GuiItems.button(services, Material.EMERALD_BLOCK,
                "gui.buttons.sell", "gui.buttons.sell-lore", Map.of()));
        gui.on(50, () -> new ClaimGui(services).open(player, 0)).set(50, GuiItems.button(services, Material.CHEST,
                "gui.buttons.claim", "gui.buttons.claim-lore", Map.of()));
        gui.on(51, () -> new AdminGui(services).openMyListings(player)).set(51, GuiItems.button(services, Material.WRITABLE_BOOK,
                "gui.buttons.my", "gui.buttons.my-lore", Map.of()));
        services.sounds().play(player, SoundRegistry.Event.OPEN);
        gui.open(player);
    }

    private void toggleSort(Player player) {
        sort = sort.equals("newest") ? "oldest" : "newest";
        SORT.put(player.getUniqueId(), sort);
    }
}