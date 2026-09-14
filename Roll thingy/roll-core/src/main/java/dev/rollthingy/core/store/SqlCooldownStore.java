package dev.rollthingy.core.store;

import dev.rollthingy.core.db.SqliteDatabase;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

public class SqlCooldownStore implements CooldownStore {
    private final SqliteDatabase db;

    public SqlCooldownStore(SqliteDatabase db) {
        this.db = db;
    }

    @Override
    public long attempt(UUID player, String boxId, long now, long cooldownSeconds) {
        return db.transact(c -> {
            long last;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT last_spin_at FROM cooldowns WHERE player_uuid = ? AND box_id = ?")) {
                ps.setString(1, player.toString());
                ps.setString(2, boxId);
                try (ResultSet rs = ps.executeQuery()) {
                    last = rs.next() ? rs.getLong(1) : -1;
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            long cooldownMs = cooldownSeconds * 1000L;
            if (last >= 0 && now - last < cooldownMs) return cooldownMs - (now - last);
            upsert(player, boxId, now);
            return 0L;
        });
    }

    private void upsert(UUID player, String boxId, long now) {
        db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO cooldowns (player_uuid, box_id, last_spin_at) VALUES (?, ?, ?)
                    ON CONFLICT(player_uuid, box_id) DO UPDATE SET last_spin_at = excluded.last_spin_at""")) {
                ps.setString(1, player.toString());
                ps.setString(2, boxId);
                ps.setLong(3, now);
                ps.executeUpdate();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }
}