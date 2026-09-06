package dev.ah.core.sweep;

import dev.ah.core.claim.ClaimRow;
import dev.ah.core.claim.ClaimStore;
import dev.ah.core.listing.Listing;
import dev.ah.core.listing.ListingStore;

public class ExpirySweep {
    private final ListingStore listings;
    private final ClaimStore claims;

    public ExpirySweep(ListingStore listings, ClaimStore claims) {
        this.listings = listings;
        this.claims = claims;
    }

    public int sweep(long nowMs) {
        int count = 0;
        for (Listing listing : listings.expiredActiveBefore(nowMs)) {
            listings.updateStatus(listing.id(), "EXPIRED");
            claims.add(new ClaimRow(0, listing.owner(), listing.itemData(), "EXPIRED", listing.id(), nowMs, null));
            count++;
        }
        return count;
    }
}