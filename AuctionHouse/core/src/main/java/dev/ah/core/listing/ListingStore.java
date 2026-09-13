package dev.ah.core.listing;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface ListingStore {
    long create(Listing listing);
    Optional<Listing> byId(long id);
    List<Listing> activePage(int limit, int offset, String search, boolean oldestFirst);
    List<Listing> byOwner(UUID owner, int limit, int offset);
    List<Listing> expiredActiveBefore(long nowMs);
    void updateStatus(long id, String status);
    long countActive();
    long countActive(String search);
    long countActiveBy(UUID owner);
    long countBy(UUID owner);
    Map<UUID, Long> countActiveGroupedByOwner(Collection<UUID> owners);
    List<UUID> activeOwners(int limit, int offset);
    long countActiveOwners();
}