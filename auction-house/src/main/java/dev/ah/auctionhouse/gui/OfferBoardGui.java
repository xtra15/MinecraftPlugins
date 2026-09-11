package dev.ah.auctionhouse.gui;

import dev.ah.auctionhouse.AhServices;
import dev.ah.core.decision.OfferDecisionService;
import dev.ah.core.gui.ChestGui;
import dev.ah.core.listing.Listing;
import dev.ah.core.misc.ItemBundleCodec;
import dev.ah.core.msg.SoundRegistry;
import dev.ah.core.offer.Offer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.List;
import java.util.Map;

public class OfferBoardGui {
    private final AhServices services;
    private final long listingId;
    private final Player viewer;
    private int offerIndex;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public OfferBoardGui(AhServices services, long listingId, Player viewer) {
        this.services = services;
        this.listingId = listingId;
        this.viewer = viewer;
    }

    public void open() {
        Listing listing = services.listings().byId(listingId).orElse(null);
        if (listing == null) return;
        List<Offer> pending = services.offers().byListing(listingId).stream()
                .filter(Offer::isPending)
                .toList();
        if (offerIndex >= pending.size()) offerIndex = Math.max(0, pending.size() - 1);

        ChestGui gui = new ChestGui(6, services.messages().get("offer-board.title"));
        gui.fill(services.fillerItem());
        gui.on(0, this::back).set(0, GuiItems.button(services, Material.SPECTRAL_ARROW, "gui.buttons.back"));

        List<ItemStack> auctioned = ItemBundleCodec.decode(listing.itemData());
        for (int i = 0; i < Math.min(auctioned.size(), 5); i++) {
            gui.set(9 + i, IconUtil.clean(auctioned.get(i)));
        }

        if (pending.isEmpty()) {
            gui.set(22, GuiItems.button(services, Material.PAPER, "offer-board.empty"));
            services.sounds().play(viewer, SoundRegistry.Event.OPEN);
            gui.open(viewer);
            return;
        }

        Offer offer = pending.get(offerIndex);
        List<ItemStack> offered = ItemBundleCodec.decode(offer.itemsData());

        String name = Bukkit.getOfflinePlayer(offer.offerer()).getName();
        String display = name == null ? offer.offerer().toString().substring(0, 8) : name;
        ItemStack by = new ItemStack(Material.NAME_TAG, 1);
        by.editMeta(meta -> {
            meta.displayName(MM.deserialize(services.messages().get("offer-board.by",
                    Map.of("name", display,
                            "index", String.valueOf(offerIndex + 1),
                            "total", String.valueOf(pending.size())))));
            meta.lore(List.of(MM.deserialize(services.messages().get("offer-board.by-lore",
                    Map.of("count", String.valueOf(offered.size()))))));
        });
        gui.set(4, by);

        for (int i = 0; i < Math.min(offered.size(), 12); i++) {
            gui.set(18 + i, IconUtil.clean(offered.get(i)));
        }

        boolean manage = viewer.getUniqueId().equals(listing.owner()) || viewer.hasPermission("ah.admin");
        if (manage) {
            long offerId = offer.id();
            gui.on(48, () -> openItems(offer)).set(48, GuiItems.button(services, Material.DIAMOND, "offer-board.inspect"));
            gui.on(50, () -> confirmAccept(offer)).set(50, GuiItems.button(services, Material.LIME_DYE, "offer-board.accept"));
            gui.on(52, () -> decide(offerId, false)).set(52, GuiItems.button(services, Material.RED_DYE, "offer-board.reject"));
        }
        if (pending.size() > 1) {
            gui.on(45, () -> { offerIndex = Math.max(0, offerIndex - 1); open(); }).set(45, GuiItems.button(services,
                    Material.ARROW, "gui.buttons.prev", "gui.buttons.prev-lore", Map.of()));
            gui.on(53, () -> { offerIndex = Math.min(pending.size() - 1, offerIndex + 1); open(); }).set(53, GuiItems.button(services,
                    Material.ARROW, "gui.buttons.next", "gui.buttons.next-lore", Map.of()));
        }
        services.sounds().play(viewer, SoundRegistry.Event.OPEN);
        gui.open(viewer);
    }

    private void confirmAccept(Offer offer) {
        ChestGui confirm = new ChestGui(3, services.messages().get("offer-board.confirm-title"));
        confirm.fill(services.fillerItem());
        confirm.on(0, this::open).set(0, GuiItems.button(services, Material.SPECTRAL_ARROW, "gui.buttons.back"));
        confirm.on(11, () -> decide(offer.id(), true)).set(11, GuiItems.button(services, Material.LIME_DYE, "gui.buttons.confirm"));
        confirm.on(15, this::open).set(15, GuiItems.button(services, Material.BARRIER, "gui.buttons.cancel"));
        confirm.open(viewer);
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
            String key = r == OfferDecisionService.Result.NOT_PENDING ? "offer-board.expired" : "errors.unknown";
            viewer.sendMessage(MM.deserialize(services.messages().get(key)));
            services.sounds().play(viewer, SoundRegistry.Event.ERROR);
        }
        open();
    }

private void back() {
        Listing listing = services.listings().byId(listingId).orElse(null);
        if (listing != null && viewer.getUniqueId().equals(listing.owner())) {
            new AdminGui(services).openMyListings(viewer);
        } else {
            new AdminGui(services).open(viewer);
        }
    }

    private void openItems(Offer offer) {
        ChestGui viewerGui = new ChestGui(6, services.messages().get("offer-board.items-viewer"));
        viewerGui.fill(services.fillerItem());
        viewerGui.on(53, this::open).set(53, GuiItems.button(services, Material.SPECTRAL_ARROW, "gui.buttons.back"));
        List<ItemStack> items = ItemBundleCodec.decode(offer.itemsData());
        for (int i = 0; i < Math.min(items.size(), 45); i++) {
            viewerGui.set(i, IconUtil.clean(items.get(i)));
        }
        viewerGui.open(viewer);
    }
}