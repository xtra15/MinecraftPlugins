package dev.ah.core.offer;

import dev.ah.core.db.SqliteDatabase;
import dev.ah.core.listing.Listing;
import dev.ah.core.listing.SqlListingStore;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SqlOfferStoreTest {

    @Test
    void createFetchListAndCounts() {
        SqliteDatabase db = SqliteDatabase.inMemory();
        db.init();
        SqlListingStore l = new SqlListingStore(db);
        SqlOfferStore o = new SqlOfferStore(db);
        UUID seller = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();
        long listingId = l.create(new Listing(0, seller, "AAA", null, 0, 1, 100, "ACTIVE"));
        long offerId = o.create(new Offer(0, listingId, buyer, "BBB", "PENDING", 5L, null));

        var offer = o.byId(offerId).orElseThrow();
        assertEquals(listingId, offer.listingId());
        assertEquals(buyer, offer.offerer());
        assertTrue(offer.isPending());

        assertEquals(1, o.countPendingByListing(listingId));
        assertEquals(1, o.countPendingForSeller(seller));

        o.updateStatusIfPending(offerId, "ACCEPTED", 99L);
        assertEquals("ACCEPTED", o.byId(offerId).get().status());
        assertEquals(99L, o.byId(offerId).get().decidedAt());
        assertEquals(0, o.countPendingByListing(listingId));
        assertEquals(0, o.countPendingForSeller(seller));

        o.updateStatusIfPending(offerId, "REJECTED", 100L); // no-op, already accepted
        assertEquals("ACCEPTED", o.byId(offerId).get().status());
        db.close();
    }

    @Test
    void listsOffersForListing() {
        SqliteDatabase db = SqliteDatabase.inMemory();
        db.init();
        SqlOfferStore o = new SqlOfferStore(db);
        long listingId = 7;
        o.create(new Offer(0, listingId, UUID.randomUUID(), "a", "PENDING", 1, null));
        o.create(new Offer(0, listingId, UUID.randomUUID(), "b", "PENDING", 2, null));
        o.create(new Offer(0, 8, UUID.randomUUID(), "c", "PENDING", 3, null));
        List<Offer> offers = o.byListing(listingId);
        assertEquals(2, offers.size());
        db.close();
    }

    @Test
    void unacceptedOfferIsStable() {
        SqliteDatabase db = SqliteDatabase.inMemory();
        db.init();
        SqlOfferStore o = new SqlOfferStore(db);
        long offerId = o.create(new Offer(0, 1, UUID.randomUUID(), "a", "PENDING", 1, null));
        o.updateStatusIfPending(offerId, "REJECTED", 2L);
        assertFalse(o.byId(offerId).get().isPending());
        db.close();
    }
}