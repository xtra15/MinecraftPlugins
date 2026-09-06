package dev.ah.core.offer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OfferStore {
    long create(Offer offer);
    Optional<Offer> byId(long id);
    List<Offer> byListing(long listingId);
    long countPendingByListing(long listingId);
    long countPendingForSeller(UUID sellerUuid);
    void updateStatusIfPending(long id, String newStatus, long decidedAt);
}