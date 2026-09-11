package dev.ah.core.saleslog;

import dev.ah.core.db.SqliteDatabase;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SqlSalesLogStore implements SalesLogStore {
    private final SqliteDatabase db;

    public SqlSalesLogStore(SqliteDatabase db) {
        this.db = db;
    }

    @Override
    public long add(SalesLogRow row) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO sales_log (listing_id, seller_uuid, buyer_uuid, item_data, outcome, accepted_at)
                    VALUES (?, ?, ?, ?, ?, ?)""", new String[]{"id"})) {
                ps.setLong(1, row.listingId());
                ps.setString(2, row.seller().toString());
                ps.setString(3, row.buyer().toString());
                ps.setString(4, row.itemData());
                ps.setString(5, row.outcome());
                ps.setLong(6, row.acceptedAt());
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

    private List<SalesLogRow> query(String sql, UUID uuid, int limit) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    List<SalesLogRow> out = new ArrayList<>();
                    while (rs.next()) out.add(map(rs));
                    return out;
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public List<SalesLogRow> recent(int limit, int offset) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT * FROM sales_log ORDER BY accepted_at DESC, id DESC LIMIT ? OFFSET ?")) {
                ps.setInt(1, limit);
                ps.setInt(2, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    List<SalesLogRow> out = new ArrayList<>();
                    while (rs.next()) out.add(map(rs));
                    return out;
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public long count() {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM sales_log")) {
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
    public List<SalesLogRow> bySeller(UUID seller, int limit) {
        return query("SELECT * FROM sales_log WHERE seller_uuid = ? ORDER BY accepted_at DESC, id DESC LIMIT ?", seller, limit);
    }

    @Override
    public List<SalesLogRow> byBuyer(UUID buyer, int limit) {
        return query("SELECT * FROM sales_log WHERE buyer_uuid = ? ORDER BY accepted_at DESC, id DESC LIMIT ?", buyer, limit);
    }

    private SalesLogRow map(ResultSet rs) throws SQLException {
        return new SalesLogRow(
                rs.getLong("id"),
                rs.getLong("listing_id"),
                UUID.fromString(rs.getString("seller_uuid")),
                UUID.fromString(rs.getString("buyer_uuid")),
                rs.getString("item_data"),
                rs.getString("outcome"),
                rs.getLong("accepted_at"));
    }
}