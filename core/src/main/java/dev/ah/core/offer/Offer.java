package dev.ah.core.offer;

import java.util.UUID;

public record Offer(long id, long listingId, UUID offerer, String itemsData,
                    String status, long createdAt, Long decidedAt) {
    public boolean isPending() {
        return "PENDING".equals(status);
    }
}