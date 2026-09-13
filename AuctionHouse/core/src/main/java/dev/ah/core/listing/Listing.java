package dev.ah.core.listing;

import java.util.UUID;

public record Listing(long id, UUID owner, String itemData, Double price, long durationMs,
                      long createdAt, long expiresAt, String status, String searchText) {
    public Listing(long id, UUID owner, String itemData, Double price, long durationMs,
                   long createdAt, long expiresAt, String status) {
        this(id, owner, itemData, price, durationMs, createdAt, expiresAt, status, null);
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
}