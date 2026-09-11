package dev.ah.core.sweep;

import dev.ah.core.claim.ClaimRow;
import dev.ah.core.claim.ClaimStore;
import dev.ah.core.db.SqliteDatabase;
import dev.ah.core.listing.Listing;
import dev.ah.core.listing.ListingStore;
import dev.ah.core.offer.Offer;
import dev.ah.core.offer.OfferStore;

public class ExpirySweep {
    private final SqliteDatabase db;
    private final ListingStore listings;
    private final OfferStore offers;
    private final ClaimStore claims;

    public ExpirySweep(SqliteDatabase db, ListingStore listings, OfferStore offers, ClaimStore claims) {
        this.db = db;
        this.listings = listings;
        this.offers = offers;
        this.claims = claims;
    }

    public int sweep(long nowMs) {
        return db.transact(c -> {
            int count = 0;
            for (Listing listing : listings.expiredActiveBefore(nowMs)) {
                listings.updateStatus(listing.id(), "EXPIRED");
                claims.add(new ClaimRow(0, listing.owner(), listing.itemData(), "EXPIRED", listing.id(), nowMs, null));
                for (Offer offer : offers.byListing(listing.id())) {
                    if (offer.isPending()) {
                        claims.add(new ClaimRow(0, offer.offerer(), offer.itemsData(), "OFFER_REJECTED", offer.id(), nowMs, null));
                        offers.updateStatusIfPending(offer.id(), "REJECTED", nowMs);
                    }
                }
                count++;
            }
            return count;
        });
    }
}