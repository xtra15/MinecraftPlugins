package dev.ah.core.listing;

import java.util.UUID;

public record Listing(long id, UUID owner, String itemData, Double price, long durationMs,
                      long createdAt, long expiresAt, String status) {
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
}