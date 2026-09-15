package dev.ah.core.claim;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClaimStore {
    long add(ClaimRow row);
    Optional<ClaimRow> byId(long id);
    List<ClaimRow> unclaimedFor(UUID owner);
    List<ClaimRow> unclaimedPage(UUID owner, int limit, int offset);
    long countUnclaimed(UUID owner);
    long countUnclaimedTotal();
    /**
     * Marks a claim as collected. Returns true only if the claim was still unclaimed and is now
     * claimed; false when it had already been collected (idempotent, dupe-safe).
     */
    boolean markClaimed(long id, long claimedAt);
}