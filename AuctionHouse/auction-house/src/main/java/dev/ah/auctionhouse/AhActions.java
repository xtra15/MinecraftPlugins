package dev.ah.auctionhouse;

import dev.ah.core.listing.Listing;
import dev.ah.core.misc.ItemBundleCodec;
import dev.ah.core.offer.Offer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.List;
import java.util.Optional;

public class AhActions {
    private final AhServices services;

    public AhActions(AhServices services) {
        this.services = services;
    }

    public enum CreateResult { SUCCESS, LIMIT_REACHED, NO_ITEMS, INVALID }
    public enum OfferResult { SUCCESS, NOT_ACTIVE, SELF_OFFER, TOO_MANY_ITEMS, NO_ITEMS, TOO_MANY_OFFERS }

    public CreateResult createListing(Player seller, List<ItemStack> items, long durationMs) {
        List<ItemStack> clean = items == null ? List.of() : items.stream()
                .filter(i -> i != null && !i.getType().isAir()).toList();
        if (clean.isEmpty()) return CreateResult.NO_ITEMS;
        if (services.listings().countActiveBy(seller.getUniqueId()) >= services.maxListingsPerPlayer()) {
            return CreateResult.LIMIT_REACHED;
        }
        long now = System.currentTimeMillis();
        long expires = now + services.allowedDurationsMs().stream()
                .filter(d -> d <= durationMs).max(Long::compareTo).orElse(services.defaultDurationMs());
        services.listings().create(new Listing(0, seller.getUniqueId(), ItemBundleCodec.encode(clean), null,
                expires - now, now, expires, "ACTIVE", buildSearchText(clean)));
        return CreateResult.SUCCESS;
    }

    private static String buildSearchText(List<ItemStack> items) {
        StringBuilder sb = new StringBuilder(512);
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) continue;
            String display = (item.hasItemMeta() && item.getItemMeta().getDisplayName() != null)
                    ? item.getItemMeta().getDisplayName() : "";
            if (!display.isBlank()) {
                sb.append(display.trim()).append(' ');
            } else {
                sb.append(item.getType().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ')).append(' ');
            }
        }
        String text = sb.toString().toLowerCase(java.util.Locale.ROOT).trim();
        return text.length() > 400 ? text.substring(0, 400) : text;
    }

    public OfferResult makeOffer(Player buyer, long listingId, List<ItemStack> items) {
        List<ItemStack> clean = items == null ? List.of() : items.stream()
                .filter(i -> i != null && !i.getType().isAir()).toList();
        if (clean.isEmpty()) return OfferResult.NO_ITEMS;
        if (clean.size() > services.maxItemsPerOffer()) return OfferResult.TOO_MANY_ITEMS;
        Optional<Listing> listing = services.listings().byId(listingId);
        if (listing.isEmpty() || !listing.get().isActive()) return OfferResult.NOT_ACTIVE;
        if (listing.get().owner().equals(buyer.getUniqueId())) return OfferResult.SELF_OFFER;
        if (services.offers().countPendingByOfferer(buyer.getUniqueId()) >= services.maxOffersPerPlayer()) {
            return OfferResult.TOO_MANY_OFFERS;
        }
        long now = System.currentTimeMillis();
        services.offers().create(new Offer(0, listingId, buyer.getUniqueId(),
                ItemBundleCodec.encode(clean), "PENDING", now, null));
        return OfferResult.SUCCESS;
    }
}