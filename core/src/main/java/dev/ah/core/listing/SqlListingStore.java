package dev.ah.core.listing;

import dev.ah.core.db.SqliteDatabase;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public class SqlListingStore implements ListingStore {
    private final SqliteDatabase db;

    public SqlListingStore(SqliteDatabase db) {
        this.db = db;
    }

    @Override
    public long create(Listing listing) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO listings (owner_uuid, item_data, price, duration_ms, created_at, expires_at, status, search_text)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)""", new String[]{"id"})) {
                ps.setString(1, listing.owner().toString());
                ps.setString(2, listing.itemData());
                if (listing.price() == null) ps.setNull(3, java.sql.Types.DOUBLE); else ps.setDouble(3, listing.price());
                ps.setLong(4, listing.durationMs());
                ps.setLong(5, listing.createdAt());
                ps.setLong(6, listing.expiresAt());
                ps.setString(7, listing.status());
                ps.setString(8, listing.searchText());
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
    public Optional<Listing> byId(long id) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM listings WHERE id = ?")) {
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
    public List<Listing> activePage(int limit, int offset, String search, boolean oldestFirst) {
        String order = oldestFirst ? "created_at ASC, id ASC" : "created_at DESC, id DESC";
        StringBuilder sql = new StringBuilder("SELECT * FROM listings WHERE status = 'ACTIVE'");
        if (search != null && !search.isBlank()) sql.append(" AND search_text LIKE ?");
        sql.append(" ORDER BY ").append(order).append(" LIMIT ? OFFSET ?");
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
                int i = 1;
                if (search != null && !search.isBlank()) ps.setString(i++, "%" + search.toLowerCase(Locale.ROOT) + "%");
                ps.setInt(i++, limit);
                ps.setInt(i, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    List<Listing> out = new ArrayList<>();
                    while (rs.next()) out.add(map(rs));
                    return out;
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public List<Listing> byOwner(UUID owner, int limit, int offset) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT * FROM listings WHERE owner_uuid = ? ORDER BY created_at DESC LIMIT ? OFFSET ?")) {
                ps.setString(1, owner.toString());
                ps.setInt(2, limit);
                ps.setInt(3, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    List<Listing> out = new ArrayList<>();
                    while (rs.next()) out.add(map(rs));
                    return out;
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public List<Listing> expiredActiveBefore(long nowMs) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT * FROM listings WHERE status = 'ACTIVE' AND expires_at <= ? ORDER BY expires_at ASC")) {
                ps.setLong(1, nowMs);
                try (ResultSet rs = ps.executeQuery()) {
                    List<Listing> out = new ArrayList<>();
                    while (rs.next()) out.add(map(rs));
                    return out;
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public void updateStatus(long id, String status) {
        db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE listings SET status = ? WHERE id = ?")) {
                ps.setString(1, status);
                ps.setLong(2, id);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    @Override
    public long countActive() {
        return countActive(null);
    }

    @Override
    public long countActive(String search) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM listings WHERE status = 'ACTIVE'");
        if (search != null && !search.isBlank()) sql.append(" AND search_text LIKE ?");
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
                if (search != null && !search.isBlank()) {
                    ps.setString(1, "%" + search.toLowerCase(Locale.ROOT) + "%");
                }
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
    public long countActiveBy(UUID owner) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM listings WHERE owner_uuid = ? AND status = 'ACTIVE'")) {
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
    public List<UUID> activeOwners(int limit) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT owner_uuid FROM (
                      SELECT owner_uuid, MAX(created_at) AS latest FROM listings
                      WHERE status = 'ACTIVE' GROUP BY owner_uuid
                    ) ORDER BY latest DESC LIMIT ?""")) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    List<UUID> out = new ArrayList<>();
                    while (rs.next()) out.add(UUID.fromString(rs.getString(1)));
                    return out;
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private Listing map(ResultSet rs) throws SQLException {
        return new Listing(
                rs.getLong("id"),
                UUID.fromString(rs.getString("owner_uuid")),
                rs.getString("item_data"),
                rs.getObject("price") == null ? null : rs.getDouble("price"),
                rs.getLong("duration_ms"),
                rs.getLong("created_at"),
                rs.getLong("expires_at"),
                rs.getString("status"),
                rs.getString("search_text"));
    }
}