package dev.ah.core.notification;

import java.util.List;
import java.util.UUID;

public interface NotificationStore {
    long add(Notification n);
    List<Notification> unread(UUID player);
    void markRead(long id, long readAt);
}