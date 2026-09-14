package dev.rollthingy.core.store;

import dev.rollthingy.core.db.SqliteDatabase;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SqlClaimStore implements ClaimStore {
    private final SqliteDatabase db;

    public SqlClaimStore(SqliteDatabase db) {
        this.db = db;
    }

    @Override
    public long add(String itemsData, UUID owner, String source, long createdAt) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO claims (player_uuid, items_data, source, created_at, claimed_at) VALUES (?, ?, ?, ?, ?)",
                    new String[]{"id"})) {
                ps.setString(1, owner.toString());
                ps.setString(2, itemsData);
                ps.setString(3, source);
                ps.setLong(4, createdAt);
                ps.setNull(5, java.sql.Types.INTEGER);
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
    public List<ClaimRow> unclaimedPage(UUID owner, int limit, int offset) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT * FROM claims WHERE player_uuid = ? AND claimed_at IS NULL
                    ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?""")) {
                ps.setString(1, owner.toString());
                ps.setInt(2, limit);
                ps.setInt(3, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    List<ClaimRow> out = new ArrayList<>();
                    while (rs.next()) out.add(map(rs));
                    return out;
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public long countUnclaimed(UUID owner) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM claims WHERE player_uuid = ? AND claimed_at IS NULL")) {
                ps.setString(1, owner.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getLong(1);
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public boolean markClaimed(long id, long at) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE claims SET claimed_at = ? WHERE id = ? AND claimed_at IS NULL")) {
                ps.setLong(1, at);
                ps.setLong(2, id);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private ClaimRow map(ResultSet rs) throws SQLException {
        long claimed = rs.getLong("claimed_at");
        boolean claimedNull = rs.wasNull();
        return new ClaimRow(
                rs.getLong("id"),
                UUID.fromString(rs.getString("player_uuid")),
                rs.getString("items_data"),
                rs.getString("source"),
                rs.getLong("created_at"),
                claimedNull ? null : claimed);
    }
}