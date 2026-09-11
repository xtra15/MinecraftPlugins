package dev.ah.auctionhouse.gui;

import dev.ah.auctionhouse.AhServices;
import dev.ah.core.gui.ChestGui;
import dev.ah.core.listing.Listing;
import dev.ah.core.misc.ItemBundleCodec;
import dev.ah.core.msg.SoundRegistry;
import dev.ah.core.saleslog.SalesLogRow;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
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
        ChestGui gui = new ChestGui(6, services.messages().get("admin.title"));
        gui.fillRect(45, 53, new ItemStack(services.guiFillerMaterial(), 1));
        gui.on(0, () -> new AhMainGui(services).open(admin))
                .set(0, GuiItems.button(services, Material.SPECTRAL_ARROW, "gui.buttons.back"));

        List<UUID> players = services.listings().activeOwners(1000);
        int slot = 9;
        for (UUID uuid : players) {
            if (slot > 44) break;
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            String name = op.getName() == null ? uuid.toString().substring(0, 8) : op.getName();
            ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
            head.editMeta(meta -> meta.displayName(MM.deserialize("<gold>" + name)));
            gui.on(slot, clk -> openUser(admin, uuid, 0)).set(slot, head);
            slot++;
        }

        gui.on(49, () -> openSalesLog(admin)).set(49, GuiItems.button(services, Material.BOOK, "admin.sales.button"));
        services.sounds().play(admin, SoundRegistry.Event.OPEN);
        gui.open(admin);
    }

    void openUser(Player admin, UUID uuid, int page) {
        int perPage = services.pageSize();
        long total = services.listings().countBy(uuid);
        int totalPages = Math.max(1, (int) ((total + perPage - 1) / perPage));
        final int pageIndex;
        if (page < 0) pageIndex = 0;
        else if (page >= totalPages) pageIndex = totalPages - 1;
        else pageIndex = page;

        ChestGui gui = new ChestGui(6, services.messages().get("admin.user.title",
                Map.of("page", String.valueOf(pageIndex + 1), "pages", String.valueOf(totalPages))));
        gui.fillRect(45, 53, new ItemStack(services.guiFillerMaterial(), 1));
        gui.on(0, () -> open(admin)).set(0, GuiItems.button(services, Material.SPECTRAL_ARROW, "gui.buttons.back"));
        List<Listing> ownerListings = services.listings().byOwner(uuid, perPage, pageIndex * perPage);
        java.util.Map<Long, Long> pendingOffers = services.offers()
                .countPendingByListings(ownerListings.stream().map(Listing::id).toList());
        int slot = 9;
        for (Listing l : ownerListings) {
            if (slot > 44) break;
            List<ItemStack> items = ItemBundleCodec.decode(l.itemData());
            if (items.isEmpty()) { slot++; continue; }
            ItemStack icon = IconUtil.clean(items.get(0));
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

    /** Reused by /ah my — shows the player's own listings. */
    public void openMyListings(Player player) {
        openUser(player, player.getUniqueId(), 0);
    }

    private void openSalesLog(Player admin) {
        ChestGui gui = new ChestGui(6, services.messages().get("admin.sales.title"));
        gui.fillRect(45, 53, new ItemStack(services.guiFillerMaterial(), 1));
        gui.on(0, () -> open(admin)).set(0, GuiItems.button(services, Material.SPECTRAL_ARROW, "gui.buttons.back"));
        int slot = 9;
        for (SalesLogRow row : services.sales().recent(36)) {
            if (slot > 44) break;
            List<ItemStack> items = ItemBundleCodec.decode(row.itemData());
            if (items.isEmpty()) { slot++; continue; }
            ItemStack icon = IconUtil.clean(items.get(0));
            String seller = Bukkit.getOfflinePlayer(row.seller()).getName();
            String buyer = Bukkit.getOfflinePlayer(row.buyer()).getName();
            icon.editMeta(m -> m.lore(List.of(MM.deserialize(services.messages().get("admin.sales.row", Map.of(
                    "seller", seller == null ? "?" : seller,
                    "buyer", buyer == null ? "?" : buyer,
                    "outcome", row.outcome()))))));
            gui.set(slot, icon);
            slot++;
        }
        gui.open(admin);
    }
}