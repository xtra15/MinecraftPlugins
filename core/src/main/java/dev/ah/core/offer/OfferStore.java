package dev.ah.core.offer;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface OfferStore {
    long create(Offer offer);
    Optional<Offer> byId(long id);
    List<Offer> byListing(long listingId);
    long countPendingByListing(long listingId);
    Map<Long, Long> countPendingByListings(Collection<Long> listingIds);
    long countPendingByOfferer(UUID offererUuid);
    long countPendingForSeller(UUID sellerUuid);
    Map<UUID, Long> countPendingGroupedBySellers(Collection<UUID> sellers);
    long countPending();
    void updateStatusIfPending(long id, String newStatus, long decidedAt);
}