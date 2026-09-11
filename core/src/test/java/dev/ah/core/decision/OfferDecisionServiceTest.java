package dev.ah.core.decision;

import dev.ah.core.claim.ClaimRow;
import dev.ah.core.claim.SqlClaimStore;
import dev.ah.core.db.SqliteDatabase;
import dev.ah.core.listing.Listing;
import dev.ah.core.listing.SqlListingStore;
import dev.ah.core.offer.Offer;
import dev.ah.core.offer.SqlOfferStore;
import dev.ah.core.saleslog.SqlSalesLogStore;
import org.junit.jupiter.api.Test;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class OfferDecisionServiceTest {

    private record Fixture(SqliteDatabase db, SqlListingStore listings, SqlOfferStore offers,
                           SqlClaimStore claims, SqlSalesLogStore sales, OfferDecisionService svc) {}

    private Fixture fixture() {
        SqliteDatabase db = SqliteDatabase.inMemory();
        db.init();
        var listings = new SqlListingStore(db);
        var offers = new SqlOfferStore(db);
        var claims = new SqlClaimStore(db);
        var sales = new SqlSalesLogStore(db);
        return new Fixture(db, listings, offers, claims, sales,
                new OfferDecisionService(listings, offers, claims, sales));
    }

    @Test
    void acceptMovesItemsToClaimsAndClosesListing() {
        Fixture f = fixture();
        UUID seller = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();
        long listing = f.listings.create(new Listing(0, seller, "AUCTIONED", null, 0, 1, 999999, "ACTIVE"));
        long offer = f.offers.create(new Offer(0, listing, buyer, "PAYMENT", "PENDING", 2, null));

        assertEquals(OfferDecisionService.Result.SUCCESS, f.svc.accept(seller, offer));

        assertEquals("SOLD", f.listings.byId(listing).get().status());
        assertFalse(f.offers.byId(offer).get().isPending());

        List<ClaimRow> sellerClaims = f.claims.unclaimedFor(seller);
        List<ClaimRow> buyerClaims = f.claims.unclaimedFor(buyer);
        assertEquals(1, sellerClaims.size());
        assertEquals("PAYMENT", sellerClaims.get(0).itemsData());
        assertEquals("OFFER_SOLD", sellerClaims.get(0).sourceType());
        assertEquals(1, buyerClaims.size());
        assertEquals("AUCTIONED", buyerClaims.get(0).itemsData());
        assertEquals("LISTING_SOLD", buyerClaims.get(0).sourceType());

        assertEquals(1, f.sales.recent(10).size());
        assertEquals(seller, f.sales.recent(10).get(0).seller());
        assertEquals(buyer, f.sales.recent(10).get(0).buyer());
        f.db.close();
    }

    @Test
    void acceptAutoRejectsOtherPendingOffers() {
        Fixture f = fixture();
        UUID seller = UUID.randomUUID();
        UUID loser = UUID.randomUUID();
        UUID winner = UUID.randomUUID();
        long listing = f.listings.create(new Listing(0, seller, "AUCTIONED", null, 0, 1, 999999, "ACTIVE"));
        long losingOffer = f.offers.create(new Offer(0, listing, loser, "LOSS", "PENDING", 2, null));
        long winningOffer = f.offers.create(new Offer(0, listing, winner, "WIN", "PENDING", 3, null));

        assertEquals(OfferDecisionService.Result.SUCCESS, f.svc.accept(seller, winningOffer));

        assertFalse(f.offers.byId(losingOffer).get().isPending());
        assertEquals("REJECTED", f.offers.byId(losingOffer).get().status());
        assertEquals("LOSS", f.claims.unclaimedFor(loser).get(0).itemsData());
        assertEquals("OFFER_REJECTED", f.claims.unclaimedFor(loser).get(0).sourceType());
        f.db.close();
    }

    @Test
    void rejectReturnsItemsToOffererAndKeepsListingActive() {
        Fixture f = fixture();
        UUID seller = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();
        long listing = f.listings.create(new Listing(0, seller, "AUCTIONED", null, 0, 1, 999999, "ACTIVE"));
        long offer = f.offers.create(new Offer(0, listing, buyer, "PAYMENT", "PENDING", 2, null));

        assertEquals(OfferDecisionService.Result.SUCCESS, f.svc.reject(seller, offer));

        assertEquals("ACTIVE", f.listings.byId(listing).get().status());
        assertEquals("REJECTED", f.offers.byId(offer).get().status());
        assertEquals("PAYMENT", f.claims.unclaimedFor(buyer).get(0).itemsData());
        assertTrue(f.sales.recent(10).isEmpty());
        f.db.close();
    }

    @Test
    void nonOwnerCannotDecide() {
        Fixture f = fixture();
        UUID seller = UUID.randomUUID();
        UUID intruder = UUID.randomUUID();
        long listing = f.listings.create(new Listing(0, seller, "AUCTIONED", null, 0, 1, 999999, "ACTIVE"));
        long offer = f.offers.create(new Offer(0, listing, UUID.randomUUID(), "PAYMENT", "PENDING", 2, null));

        assertEquals(OfferDecisionService.Result.NOT_OWNER, f.svc.accept(intruder, offer));
        assertEquals(OfferDecisionService.Result.NOT_OWNER, f.svc.reject(intruder, offer));
        assertEquals("PENDING", f.offers.byId(offer).get().status());
        f.db.close();
    }

    @Test
    void doubleDecisionRejected() {
        Fixture f = fixture();
        UUID seller = UUID.randomUUID();
        long listing = f.listings.create(new Listing(0, seller, "AUCTIONED", null, 0, 1, 999999, "ACTIVE"));
        long offer = f.offers.create(new Offer(0, listing, UUID.randomUUID(), "PAYMENT", "PENDING", 2, null));

        assertEquals(OfferDecisionService.Result.SUCCESS, f.svc.accept(seller, offer));
        assertEquals(OfferDecisionService.Result.NOT_PENDING, f.svc.accept(seller, offer));
        f.db.close();
    }

    @Test
    void unknownOfferYieldsNotFound() {
        Fixture f = fixture();
        assertEquals(OfferDecisionService.Result.NOT_FOUND, f.svc.accept(UUID.randomUUID(), 12345L));
        f.db.close();
    }

    @Test
    void acceptOnNonActiveListingIsRejected() {
        Fixture f = fixture();
        UUID seller = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();
        long listing = f.listings.create(new Listing(0, seller, "AUCTIONED", null, 0, 1, 100, "ACTIVE"));
        long offer = f.offers.create(new Offer(0, listing, buyer, "PAYMENT", "PENDING", 2, null));
        f.listings.updateStatus(listing, "EXPIRED");

        assertEquals(OfferDecisionService.Result.NOT_PENDING, f.svc.accept(seller, offer));

        assertEquals("EXPIRED", f.listings.byId(listing).get().status());
        assertTrue(f.offers.byId(offer).get().isPending());
        assertTrue(f.claims.unclaimedFor(seller).isEmpty());
        assertTrue(f.claims.unclaimedFor(buyer).isEmpty());
        f.db.close();
    }

    @Test
    void rejectStillAllowedOnExpiredListingToReturnOfferItems() {
        Fixture f = fixture();
        UUID seller = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();
        long listing = f.listings.create(new Listing(0, seller, "AUCTIONED", null, 0, 1, 100, "ACTIVE"));
        long offer = f.offers.create(new Offer(0, listing, buyer, "PAYMENT", "PENDING", 2, null));
        f.listings.updateStatus(listing, "EXPIRED");

        assertEquals(OfferDecisionService.Result.SUCCESS, f.svc.reject(seller, offer));

        assertEquals("REJECTED", f.offers.byId(offer).get().status());
        assertEquals("PAYMENT", f.claims.unclaimedFor(buyer).get(0).itemsData());
        f.db.close();
    }

    @Test
    void nestedTransactionsCommitTogether() {
        Fixture f = fixture();
        UUID bob = UUID.randomUUID();
        f.db.transact(c -> {
            f.claims.add(new ClaimRow(0, bob, "A", "T", 1, 1L, null));
            f.claims.add(new ClaimRow(0, bob, "B", "T", 1, 2L, null)); // inner add joins outer tx
            return null;
        });
        assertEquals(2, f.claims.countUnclaimed(bob)); // outer commit committed both

        assertThrows(RuntimeException.class, () -> f.db.transact(c -> {
            f.claims.add(new ClaimRow(0, bob, "C", "T", 1, 3L, null));
            throw new SQLException("boom");
        }));
        assertEquals(2, f.claims.countUnclaimed(bob)); // outer rollback undid inner writes
        f.db.close();
    }
}