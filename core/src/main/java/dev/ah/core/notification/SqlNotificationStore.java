package dev.ah.core.notification;

import dev.ah.core.db.SqliteDatabase;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SqlNotificationStore implements NotificationStore {
    private final SqliteDatabase db;

    public SqlNotificationStore(SqliteDatabase db) {
        this.db = db;
    }

    @Override
    public long add(Notification n) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO notifications (player_uuid, message_key, created_at, read_at)
                    VALUES (?, ?, ?, ?)""", new String[]{"id"})) {
                ps.setString(1, n.player().toString());
                ps.setString(2, n.messageKey());
                ps.setLong(3, n.createdAt());
                if (n.readAt() == null) ps.setNull(4, java.sql.Types.INTEGER); else ps.setLong(4, n.readAt());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    return keys.getLong(1);
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public List<Notification> unread(UUID player) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT * FROM notifications WHERE player_uuid = ? AND read_at IS NULL ORDER BY created_at ASC, id ASC")) {
                ps.setString(1, player.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    List<Notification> out = new ArrayList<>();
                    while (rs.next()) {
                        long read = rs.getLong("read_at");
                        out.add(new Notification(
                                rs.getLong("id"),
                                UUID.fromString(rs.getString("player_uuid")),
                                rs.getString("message_key"),
                                rs.getLong("created_at"),
                                rs.wasNull() ? null : read));
                    }
                    return out;
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public void markRead(long id, long readAt) {
        db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE notifications SET read_at = ? WHERE id = ?")) {
                ps.setLong(1, readAt);
                ps.setLong(2, id);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }
}