package dev.ah.auctionhouse.gui;

import dev.ah.auctionhouse.AhServices;
import dev.ah.core.decision.OfferDecisionService;
import dev.ah.core.gui.ChestGui;
import dev.ah.core.listing.Listing;
import dev.ah.core.misc.ItemBundleCodec;
import dev.ah.core.msg.SoundRegistry;
import dev.ah.core.offer.Offer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.List;

public class OfferBoardGui {
    private final AhServices services;
    private final long listingId;
    private final Player viewer;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public OfferBoardGui(AhServices services, long listingId, Player viewer) {
        this.services = services;
        this.listingId = listingId;
        this.viewer = viewer;
    }

    public void open() {
        Listing listing = services.listings().byId(listingId).orElse(null);
        if (listing == null) return;
        ChestGui gui = new ChestGui(6, services.messages().get("offer-board.title"));
        gui.fill(new ItemStack(Material.valueOf(services.getGuiFiller()), 1));

        List<ItemStack> auctioned = ItemBundleCodec.decode(listing.itemData());
        for (int i = 0; i < Math.min(auctioned.size(), 5); i++) {
            gui.set(18 + i, auctioned.get(i).clone());
        }

        List<Offer> pending = services.offers().byListing(listingId).stream()
                .filter(Offer::isPending)
                .toList();
        if (!pending.isEmpty()) {
            Offer offer = pending.get(0);
            List<ItemStack> offered = ItemBundleCodec.decode(offer.itemsData());
            for (int i = 0; i < Math.min(offered.size(), 12); i++) {
                gui.set(24 + i, offered.get(i).clone());
            }
            long offerId = offer.id();
            if (viewer.getUniqueId().equals(listing.owner()) || viewer.hasPermission("ah.admin")) {
                gui.on(36, () -> decide(offerId, true)).set(36, new ItemStack(Material.LIME_DYE, 1));
                gui.on(38, () -> decide(offerId, false)).set(38, new ItemStack(Material.RED_DYE, 1));
                gui.on(40, () -> openItems(offer)).set(40, new ItemStack(Material.DIAMOND, 1));
            }
        }
        services.sounds().play(viewer, SoundRegistry.Event.OPEN);
        gui.open(viewer);
    }

    private void decide(long offerId, boolean accept) {
        Offer offer = services.offers().byId(offerId).orElse(null);
        if (offer == null) return;
        OfferDecisionService.Result r = accept
                ? services.decisions().accept(viewer.getUniqueId(), offerId)
                : services.decisions().reject(viewer.getUniqueId(), offerId);
        if (r == OfferDecisionService.Result.SUCCESS) {
            viewer.sendMessage(MM.deserialize(services.messages().get(accept ? "offers.accepted-seller" : "offers.rejected-seller")));
            services.sounds().play(viewer, SoundRegistry.Event.OFFER_ACCEPTED);
            new dev.ah.auctionhouse.notify.NotificationService(services).notifyOfferDecision(offer.offerer(), accept);
        } else {
            services.sounds().play(viewer, SoundRegistry.Event.ERROR);
        }
        open();
    }

    private void openItems(Offer offer) {
        ChestGui viewerGui = new ChestGui(6, services.messages().get("offer-board.items-viewer"));
        viewerGui.fill(new ItemStack(Material.valueOf(services.getGuiFiller()), 1));
        List<ItemStack> items = ItemBundleCodec.decode(offer.itemsData());
        for (int i = 0; i < Math.min(items.size(), 45); i++) {
            viewerGui.set(i, items.get(i).clone());
        }
        viewerGui.open(viewer);
    }
}