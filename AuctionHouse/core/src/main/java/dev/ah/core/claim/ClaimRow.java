package dev.ah.core.claim;

import java.util.UUID;

public record ClaimRow(long id, UUID owner, String itemsData, String sourceType,
                       long sourceId, long createdAt, Long claimedAt) {}