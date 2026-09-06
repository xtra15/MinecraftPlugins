package dev.ah.core.notification;

import java.util.UUID;

public record Notification(long id, UUID player, String messageKey, long createdAt, Long readAt) {}