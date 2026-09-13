package dev.ah.core.offer;

import dev.ah.core.db.SqliteDatabase;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class SqlOfferStore implements OfferStore {
    private final SqliteDatabase db;

    public SqlOfferStore(SqliteDatabase db) {
        this.db = db;
    }

    @Override
    public long create(Offer offer) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO offers (listing_id, offerer_uuid, items_data, status, created_at, decided_at)
                    VALUES (?, ?, ?, ?, ?, ?)""", new String[]{"id"})) {
                ps.setLong(1, offer.listingId());
                ps.setString(2, offer.offerer().toString());
                ps.setString(3, offer.itemsData());
                ps.setString(4, offer.status());
                ps.setLong(5, offer.createdAt());
                if (offer.decidedAt() == null) ps.setNull(6, java.sql.Types.INTEGER); else ps.setLong(6, offer.decidedAt());
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
    public Optional<Offer> byId(long id) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM offers WHERE id = ?")) {
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
    public List<Offer> byListing(long listingId) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT * FROM offers WHERE listing_id = ? ORDER BY created_at DESC, id DESC")) {
                ps.setLong(1, listingId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<Offer> out = new ArrayList<>();
                    while (rs.next()) out.add(map(rs));
                    return out;
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public long countPendingByListing(long listingId) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM offers WHERE listing_id = ? AND status = 'PENDING'")) {
                ps.setLong(1, listingId);
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
    public Map<Long, Long> countPendingByListings(Collection<Long> listingIds) {
        if (listingIds == null || listingIds.isEmpty()) return Map.of();
        StringBuilder sql = new StringBuilder(
                "SELECT listing_id, COUNT(*) FROM offers WHERE status = 'PENDING' AND listing_id IN (");
        for (int i = 0; i < listingIds.size(); i++) sql.append(i == 0 ? "?" : ",?");
        sql.append(") GROUP BY listing_id");
        return db.transact(c -> {
            Map<Long, Long> out = new java.util.HashMap<>();
            try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
                int i = 1;
                for (Long id : listingIds) ps.setLong(i++, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.put(rs.getLong(1), rs.getLong(2));
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return out;
        });
    }

    @Override
    public long countPendingByOfferer(UUID offererUuid) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM offers WHERE offerer_uuid = ? AND status = 'PENDING'")) {
                ps.setString(1, offererUuid.toString());
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
    public long countPendingForSeller(UUID sellerUuid) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT COUNT(*) FROM offers
                    WHERE status = 'PENDING'
                      AND listing_id IN (SELECT id FROM listings WHERE owner_uuid = ?)""")) {
                ps.setString(1, sellerUuid.toString());
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
    public Map<UUID, Long> countPendingGroupedBySellers(Collection<UUID> sellers) {
        if (sellers == null || sellers.isEmpty()) return Map.of();
        StringBuilder sql = new StringBuilder("""
                SELECT l.owner_uuid, COUNT(*) FROM offers o
                JOIN listings l ON o.listing_id = l.id
                WHERE o.status = 'PENDING' AND l.owner_uuid IN (""");
        boolean first = true;
        for (UUID ignored : sellers) {
            if (!first) sql.append(",");
            sql.append("?");
            first = false;
        }
        sql.append(") GROUP BY l.owner_uuid");
        return db.transact(c -> {
            Map<UUID, Long> out = new HashMap<>();
            try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
                int i = 1;
                for (UUID uuid : sellers) ps.setString(i++, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.put(UUID.fromString(rs.getString(1)), rs.getLong(2));
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return out;
        });
    }

    @Override
    public long countPending() {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM offers WHERE status = 'PENDING'")) {
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
    public void updateStatusIfPending(long id, String newStatus, long decidedAt) {
        db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE offers SET status = ?, decided_at = ? WHERE id = ? AND status = 'PENDING'")) {
                ps.setString(1, newStatus);
                ps.setLong(2, decidedAt);
                ps.setLong(3, id);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    private Offer map(ResultSet rs) throws SQLException {
        long decided = rs.getLong("decided_at");
        return new Offer(
                rs.getLong("id"),
                rs.getLong("listing_id"),
                UUID.fromString(rs.getString("offerer_uuid")),
                rs.getString("items_data"),
                rs.getString("status"),
                rs.getLong("created_at"),
                rs.wasNull() ? null : decided);
    }
}