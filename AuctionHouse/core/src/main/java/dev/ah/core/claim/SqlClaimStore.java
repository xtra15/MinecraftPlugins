package dev.ah.core.claim;

import dev.ah.core.db.SqliteDatabase;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SqlClaimStore implements ClaimStore {
    private final SqliteDatabase db;

    public SqlClaimStore(SqliteDatabase db) {
        this.db = db;
    }

    @Override
    public long add(ClaimRow row) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO claims (owner_uuid, items_data, source_type, source_id, created_at, claimed_at)
                    VALUES (?, ?, ?, ?, ?, ?)""", new String[]{"id"})) {
                ps.setString(1, row.owner().toString());
                ps.setString(2, row.itemsData());
                ps.setString(3, row.sourceType());
                ps.setLong(4, row.sourceId());
                ps.setLong(5, row.createdAt());
                if (row.claimedAt() == null) ps.setNull(6, java.sql.Types.INTEGER); else ps.setLong(6, row.claimedAt());
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
    public Optional<ClaimRow> byId(long id) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM claims WHERE id = ?")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.empty();
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public List<ClaimRow> unclaimedFor(UUID owner) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT * FROM claims WHERE owner_uuid = ? AND claimed_at IS NULL ORDER BY created_at DESC, id DESC")) {
                ps.setString(1, owner.toString());
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
    public List<ClaimRow> unclaimedPage(UUID owner, int limit, int offset) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT * FROM claims WHERE owner_uuid = ? AND claimed_at IS NULL
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
                    "SELECT COUNT(*) FROM claims WHERE owner_uuid = ? AND claimed_at IS NULL")) {
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
    public long countUnclaimedTotal() {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM claims WHERE claimed_at IS NULL")) {
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
    public boolean markClaimed(long id, long claimedAt) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE claims SET claimed_at = ? WHERE id = ? AND claimed_at IS NULL")) {
                ps.setLong(1, claimedAt);
                ps.setLong(2, id);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private ClaimRow map(ResultSet rs) throws SQLException {
        long claimed = rs.getLong("claimed_at");
        return new ClaimRow(
                rs.getLong("id"),
                UUID.fromString(rs.getString("owner_uuid")),
                rs.getString("items_data"),
                rs.getString("source_type"),
                rs.getLong("source_id"),
                rs.getLong("created_at"),
                rs.wasNull() ? null : claimed);
    }
}