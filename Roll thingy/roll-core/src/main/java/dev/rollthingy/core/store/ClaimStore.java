package dev.rollthingy.core.store;

import java.util.List;
import java.util.UUID;

public interface ClaimStore {
    long add(String itemsData, UUID owner, String source, long createdAt);

    List<ClaimRow> unclaimedPage(UUID owner, int limit, int offset);

    long countUnclaimed(UUID owner);

    boolean markClaimed(long id, long at);
}