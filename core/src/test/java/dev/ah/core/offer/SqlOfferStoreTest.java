package dev.ah.core.offer;

import dev.ah.core.db.SqliteDatabase;
import dev.ah.core.listing.Listing;
import dev.ah.core.listing.SqlListingStore;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
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

    @Test
    void countPendingByListingsBatchesAcrossIds() {
        SqliteDatabase db = SqliteDatabase.inMemory();
        db.init();
        SqlOfferStore o = new SqlOfferStore(db);
        UUID why = UUID.randomUUID();
        o.create(new Offer(0, 10, why, "a", "PENDING", 1, null));
        o.create(new Offer(0, 10, why, "b", "PENDING", 2, null));
        o.create(new Offer(0, 11, why, "c", "PENDING", 3, null));
        o.create(new Offer(0, 12, why, "d", "ACCEPTED", 4, null));

        var counts = o.countPendingByListings(List.of(10L, 11L, 12L));
        assertEquals(2, counts.get(10L));
        assertEquals(1, counts.get(11L));
        assertNull(counts.get(12L));
        assertTrue(o.countPendingByListings(List.of()).isEmpty());
        db.close();
    }

    @Test
    void countPendingByOffererCountsOnlyPending() {
        SqliteDatabase db = SqliteDatabase.inMemory();
        db.init();
        SqlOfferStore o = new SqlOfferStore(db);
        UUID bidder = UUID.randomUUID();
        long p1 = o.create(new Offer(0, 1, bidder, "a", "PENDING", 1, null));
        o.create(new Offer(0, 2, bidder, "b", "PENDING", 2, null));
        o.create(new Offer(0, 3, UUID.randomUUID(), "c", "PENDING", 3, null));
        assertEquals(2, o.countPendingByOfferer(bidder));

        o.updateStatusIfPending(p1, "REJECTED", 9L);
        assertEquals(1, o.countPendingByOfferer(bidder));
        db.close();
    }

    @Test
    void countPendingCountsOnlyPending() {
        SqliteDatabase db = SqliteDatabase.inMemory();
        db.init();
        SqlOfferStore o = new SqlOfferStore(db);
        o.create(new Offer(0, 1, UUID.randomUUID(), "a", "PENDING", 1, null));
        o.create(new Offer(0, 2, UUID.randomUUID(), "b", "PENDING", 2, null));
        o.create(new Offer(0, 3, UUID.randomUUID(), "c", "ACCEPTED", 3, null));
        assertEquals(2, o.countPending());
        db.close();
    }

    @Test
    void countPendingGroupedBySellersBatchesAcrossIds() {
        SqliteDatabase db = SqliteDatabase.inMemory();
        db.init();
        SqlListingStore l = new SqlListingStore(db);
        SqlOfferStore o = new SqlOfferStore(db);
        UUID sellerA = UUID.randomUUID();
        UUID sellerB = UUID.randomUUID();
        UUID sellerC = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();
        long aId = l.create(new Listing(0, sellerA, "A", null, 0, 1, 100, "ACTIVE"));
        long bId = l.create(new Listing(0, sellerB, "B", null, 0, 2, 100, "ACTIVE"));
        l.create(new Listing(0, sellerC, "C", null, 0, 3, 100, "ACTIVE"));
        o.create(new Offer(0, aId, buyer, "o1", "PENDING", 1, null));
        o.create(new Offer(0, aId, buyer, "o2", "PENDING", 2, null));
        o.create(new Offer(0, bId, buyer, "o3", "PENDING", 3, null));

        var counts = o.countPendingGroupedBySellers(List.of(sellerA, sellerB, sellerC));
        assertEquals(2, counts.get(sellerA));
        assertEquals(1, counts.get(sellerB));
        assertNull(counts.get(sellerC));
        assertTrue(o.countPendingGroupedBySellers(List.of()).isEmpty());
        db.close();
    }
}