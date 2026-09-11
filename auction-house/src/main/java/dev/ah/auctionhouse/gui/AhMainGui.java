package dev.ah.auctionhouse.gui;

import dev.ah.auctionhouse.AhServices;
import dev.ah.core.gui.ChestGui;
import dev.ah.core.listing.Listing;
import dev.ah.core.misc.ItemBundleCodec;
import dev.ah.core.msg.SoundRegistry;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AhMainGui {
    private final AhServices services;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private String sort = "newest";
    private String search = "";

    public AhMainGui(AhServices services) {
        this.services = services;
    }

    public void open(Player player) {
        open(player, 0);
    }

    public void openSearch(Player player, String term) {
        this.search = term == null ? "" : term.toLowerCase(Locale.ROOT);
        open(player, 0);
    }

    public void openSorted(Player player, String sort, String search, int page) {
        this.sort = sort;
        this.search = search == null ? null : search.toLowerCase(Locale.ROOT);
        open(player, page);
    }

    private void open(Player player, int page) {
        String needle = (search == null || search.isBlank()) ? null : search;
        boolean oldest = "oldest".equals(sort);
        int pageSize = services.pageSize();
        long total = services.listings().countActive(needle);
        int pages = Math.max(1, (int) Math.ceilDiv(total, pageSize));
        int pageIndex = Math.max(0, Math.min(page, pages - 1));
        List<Listing> pageRows = services.listings().activePage(pageSize, pageIndex * pageSize, needle, oldest);

        ChestGui gui = new ChestGui(6, services.messages().get("gui.main.title"));
        gui.fill(new ItemStack(services.guiFillerMaterial(), 1));
        gui.fillRect(9, 44, null);

        int slot = 9;
        java.util.Map<Long, Long> pendingOffers = services.offers()
                .countPendingByListings(pageRows.stream().map(Listing::id).toList());
        for (Listing listing : pageRows) {
            if (slot > 44) break;
            List<ItemStack> items = ItemBundleCodec.decode(listing.itemData());
            if (items.isEmpty()) { slot++; continue; }
            ItemStack icon = items.get(0).clone();
            ItemMeta meta = icon.getItemMeta();
            long offers = pendingOffers.getOrDefault(listing.id(), 0L);
            String price = services.isEconomyEnabled() && listing.price() != null
                    ? String.valueOf(listing.price()) : "—";
            meta.lore(List.of(
                    MM.deserialize(services.messages().get("listings.lore.price", Map.of("price", price))),
                    MM.deserialize(services.messages().get("listings.lore.offers", Map.of("count", String.valueOf(offers)))),
                    MM.deserialize(services.messages().get("listings.lore.remaining",
                            Map.of("minutes", String.valueOf((listing.expiresAt() - System.currentTimeMillis()) / 60000))))));
            icon.setItemMeta(meta);
            long listingId = listing.id();
            gui.on(slot, clk -> { clk.player().closeInventory(); new OfferGui(services, listingId).open(clk.player()); });
            gui.set(slot, icon);
            slot++;
        }

        gui.on(53, () -> open(player, pageIndex + 1)).set(53, GuiItems.button(services, Material.ARROW,
                "gui.buttons.next", "gui.buttons.next-lore", Map.of()));
        gui.on(45, () -> open(player, pageIndex - 1)).set(45, GuiItems.button(services, Material.ARROW,
                "gui.buttons.prev", "gui.buttons.prev-lore", Map.of()));
        gui.on(49, this::toggleSort).set(49, GuiItems.button(services, Material.COMPASS,
                "gui.buttons.sort", "gui.buttons.sort-lore", Map.of()));
        gui.on(47, clk -> {
            clk.player().closeInventory();
            clk.player().performCommand("ah search ");
        }).set(47, GuiItems.button(services, Material.OAK_SIGN,
                "gui.buttons.search", "gui.buttons.search-lore", Map.of()));
        gui.on(50, () -> new ClaimGui(services).open(player, 0)).set(50, GuiItems.button(services, Material.CHEST,
                "gui.buttons.claim", "gui.buttons.claim-lore", Map.of()));
        services.sounds().play(player, SoundRegistry.Event.OPEN);
        gui.open(player);
    }

    private void toggleSort() {
        sort = sort.equals("newest") ? "oldest" : "newest";
    }
}