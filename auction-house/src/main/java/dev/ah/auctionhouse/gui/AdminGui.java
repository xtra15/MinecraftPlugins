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
        gui.fill(new ItemStack(Material.valueOf(services.getGuiFiller()), 1));
        gui.fillRect(9, 44, null);

        List<UUID> players = services.listings().activeOwners(1000);
        int slot = 9;
        for (UUID uuid : players) {
            if (slot > 44) break;
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            String name = op.getName() == null ? uuid.toString().substring(0, 8) : op.getName();
            ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
            head.editMeta(meta -> meta.displayName(MM.deserialize("<gold>" + name)));
            gui.on(slot, clk -> openUser(admin, uuid)).set(slot, head);
            slot++;
        }

        gui.on(49, () -> openSalesLog(admin)).set(49, new ItemStack(Material.BOOK, 1));
        services.sounds().play(admin, SoundRegistry.Event.OPEN);
        gui.open(admin);
    }

    void openUser(Player admin, UUID uuid) {
        ChestGui gui = new ChestGui(6, services.messages().get("admin.user.title"));
        gui.fill(new ItemStack(Material.valueOf(services.getGuiFiller()), 1));
        gui.fillRect(9, 44, null);
        int slot = 9;
        for (Listing l : services.listings().byOwner(uuid, 10_000, 0)) {
            if (slot > 44) break;
            List<ItemStack> items = ItemBundleCodec.decode(l.itemData());
            if (items.isEmpty()) { slot++; continue; }
            ItemStack icon = items.get(0).clone();
            long offers = services.offers().countPendingByListing(l.id());
            icon.editMeta(m -> m.lore(List.of(MM.deserialize(services.messages().get("admin.user.listing", Map.of(
                    "id", String.valueOf(l.id()),
                    "status", l.status(),
                    "offers", String.valueOf(offers)))))));
            long listingId = l.id();
            gui.on(slot, clk -> new OfferBoardGui(services, listingId, admin).open());
            gui.set(slot, icon);
            slot++;
        }
        gui.open(admin);
    }

    /** Reused by /ah my — shows the player's own listings. */
    public void openMyListings(Player player) {
        openUser(player, player.getUniqueId());
    }

    private void openSalesLog(Player admin) {
        ChestGui gui = new ChestGui(6, services.messages().get("admin.sales.title"));
        gui.fill(new ItemStack(Material.valueOf(services.getGuiFiller()), 1));
        gui.fillRect(9, 44, null);
        int slot = 9;
        for (SalesLogRow row : services.sales().recent(36)) {
            if (slot > 44) break;
            List<ItemStack> items = ItemBundleCodec.decode(row.itemData());
            if (items.isEmpty()) { slot++; continue; }
            ItemStack icon = items.get(0).clone();
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