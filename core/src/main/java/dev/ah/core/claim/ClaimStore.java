package dev.ah.core.claim;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClaimStore {
    long add(ClaimRow row);
    Optional<ClaimRow> byId(long id);
    List<ClaimRow> unclaimedFor(UUID owner);
    long countUnclaimed(UUID owner);
    void markClaimed(long id, long claimedAt);
}