package dev.rollthingy.core.store;

import java.util.UUID;

public record ClaimRow(long id, UUID owner, String itemsData, String source, long createdAt, Long claimedAt) {}