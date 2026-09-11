package dev.ah.core.sweep;

import dev.ah.core.claim.ClaimRow;
import dev.ah.core.claim.SqlClaimStore;
import dev.ah.core.db.SqliteDatabase;
import dev.ah.core.listing.Listing;
import dev.ah.core.listing.SqlListingStore;
import dev.ah.core.offer.Offer;
import dev.ah.core.offer.SqlOfferStore;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ExpirySweepTest {

    @Test
    void expiresOverdueListingsAndClaimsThem() {
        SqliteDatabase db = SqliteDatabase.inMemory();
        db.init();
        var listings = new SqlListingStore(db);
        var offers = new SqlOfferStore(db);
        var claims = new SqlClaimStore(db);
        ExpirySweep sweep = new ExpirySweep(db, listings, offers, claims);
        UUID owner = UUID.randomUUID();
        long l1 = listings.create(new Listing(0, owner, "EXPIRED_ITEM", null, 0, 1, 100, "ACTIVE"));
        listings.create(new Listing(0, owner, "FUTURE_ITEM", null, 0, 1, 9999, "ACTIVE"));

        assertEquals(1, sweep.sweep(500L));

        assertEquals("EXPIRED", listings.byId(l1).get().status());
        assertEquals(1, claims.countUnclaimed(owner));
        assertEquals("EXPIRED_ITEM", claims.unclaimedFor(owner).get(0).itemsData());
        assertEquals("EXPIRED", claims.unclaimedFor(owner).get(0).sourceType());
        db.close();
    }

    @Test
    void pendingOffersOnExpiredListingAreRejectedAndReturned() {
        SqliteDatabase db = SqliteDatabase.inMemory();
        db.init();
        var listings = new SqlListingStore(db);
        var offers = new SqlOfferStore(db);
        var claims = new SqlClaimStore(db);
        ExpirySweep sweep = new ExpirySweep(db, listings, offers, claims);
        UUID seller = UUID.randomUUID();
        UUID bidder = UUID.randomUUID();
        long listing = listings.create(new Listing(0, seller, "EXP_ITEM", null, 0, 1, 100, "ACTIVE"));
        long offerId = offers.create(new Offer(0, listing, bidder, "BID_ITEM", "PENDING", 1, null));

        assertEquals(1, sweep.sweep(500L));

        assertEquals("REJECTED", offers.byId(offerId).get().status());
        assertEquals(1, claims.countUnclaimed(bidder));
        assertEquals("BID_ITEM", claims.unclaimedFor(bidder).get(0).itemsData());
        assertEquals("OFFER_REJECTED", claims.unclaimedFor(bidder).get(0).sourceType());
        db.close();
    }

    @Test
    void noOpWhenNothingExpired() {
        SqliteDatabase db = SqliteDatabase.inMemory();
        db.init();
        var listings = new SqlListingStore(db);
        var offers = new SqlOfferStore(db);
        var claims = new SqlClaimStore(db);
        listings.create(new Listing(0, UUID.randomUUID(), "FUTURE", null, 0, 1, 9999, "ACTIVE"));
        assertEquals(0, new ExpirySweep(db, listings, offers, claims).sweep(1L));
        db.close();
    }
}