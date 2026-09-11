package dev.ah.core.sweep;

import dev.ah.core.claim.ClaimRow;
import dev.ah.core.claim.ClaimStore;
import dev.ah.core.db.SqliteDatabase;
import dev.ah.core.listing.Listing;
import dev.ah.core.listing.ListingStore;

public class ExpirySweep {
    private final SqliteDatabase db;
    private final ListingStore listings;
    private final ClaimStore claims;

    public ExpirySweep(SqliteDatabase db, ListingStore listings, ClaimStore claims) {
        this.db = db;
        this.listings = listings;
        this.claims = claims;
    }

    public int sweep(long nowMs) {
        return db.transact(c -> {
            int count = 0;
            for (Listing listing : listings.expiredActiveBefore(nowMs)) {
                listings.updateStatus(listing.id(), "EXPIRED");
                claims.add(new ClaimRow(0, listing.owner(), listing.itemData(), "EXPIRED", listing.id(), nowMs, null));
                count++;
            }
            return count;
        });
    }
}