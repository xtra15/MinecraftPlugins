package dev.ah.core.decision;

import dev.ah.core.claim.ClaimRow;
import dev.ah.core.claim.ClaimStore;
import dev.ah.core.listing.Listing;
import dev.ah.core.listing.ListingStore;
import dev.ah.core.offer.Offer;
import dev.ah.core.offer.OfferStore;
import dev.ah.core.saleslog.SalesLogRow;
import dev.ah.core.saleslog.SalesLogStore;
import java.util.Optional;
import java.util.UUID;

public class OfferDecisionService {
    private final ListingStore listings;
    private final OfferStore offers;
    private final ClaimStore claims;
    private final SalesLogStore sales;

    public OfferDecisionService(ListingStore listings, OfferStore offers, ClaimStore claims, SalesLogStore sales) {
        this.listings = listings;
        this.offers = offers;
        this.claims = claims;
        this.sales = sales;
    }

    public enum Result { SUCCESS, NOT_FOUND, NOT_OWNER, NOT_PENDING, ERROR }

    public Result accept(UUID actor, long offerId) {
        return decide(actor, offerId, true);
    }

    public Result reject(UUID actor, long offerId) {
        return decide(actor, offerId, false);
    }

    private Result decide(UUID actor, long offerId, boolean accept) {
        Optional<Offer> maybeOffer = offers.byId(offerId);
        if (maybeOffer.isEmpty()) return Result.NOT_FOUND;
        Offer offer = maybeOffer.get();
        Optional<Listing> maybeListing = listings.byId(offer.listingId());
        if (maybeListing.isEmpty()) return Result.NOT_FOUND;
        Listing listing = maybeListing.get();
        if (!listing.owner().equals(actor)) return Result.NOT_OWNER;
        if (!offer.isPending()) return Result.NOT_PENDING;
        if (accept && !listing.isActive()) return Result.NOT_PENDING;

        try {
            long now = System.currentTimeMillis();
            if (accept) {
                // seller receives the offered items
                claims.add(new ClaimRow(0, listing.owner(), offer.itemsData(), "OFFER_SOLD", offer.id(), now, null));
                // buyer receives the auctioned items
                claims.add(new ClaimRow(0, offer.offerer(), listing.itemData(), "LISTING_SOLD", listing.id(), now, null));
                sales.add(new SalesLogRow(0, listing.id(), listing.owner(), offer.offerer(),
                        listing.itemData(), "ACCEPTED", now));
                offers.updateStatusIfPending(offer.id(), "ACCEPTED", now);
                listings.updateStatus(listing.id(), "SOLD");
                // auto-reject any other pending offers, returning their items
                for (Offer other : offers.byListing(listing.id())) {
                    if (other.id() != offer.id() && other.isPending()) {
                        claims.add(new ClaimRow(0, other.offerer(), other.itemsData(), "OFFER_REJECTED", other.id(), now, null));
                        offers.updateStatusIfPending(other.id(), "REJECTED", now);
                    }
                }
            } else {
                claims.add(new ClaimRow(0, offer.offerer(), offer.itemsData(), "OFFER_REJECTED", offer.id(), now, null));
                offers.updateStatusIfPending(offer.id(), "REJECTED", now);
            }
            return Result.SUCCESS;
        } catch (RuntimeException e) {
            return Result.ERROR;
        }
    }
}