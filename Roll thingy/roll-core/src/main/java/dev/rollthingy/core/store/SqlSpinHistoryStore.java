package dev.rollthingy.core.store;

import dev.rollthingy.core.db.SqliteDatabase;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SqlSpinHistoryStore implements SpinHistoryStore {
    private final SqliteDatabase db;

    public SqlSpinHistoryStore(SqliteDatabase db) {
        this.db = db;
    }

    @Override
    public void addAll(List<SpinHistoryRow> rows) {
        if (rows.isEmpty()) return;
        db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO spin_history (player_uuid, player_name, box_id, box_name, created_at," +
                            " deposit_data, luck, shortfall, result_data, stored) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                for (SpinHistoryRow r : rows) {
                    ps.setString(1, r.playerUuid().toString());
                    ps.setString(2, r.playerName());
                    ps.setString(3, r.boxId());
                    ps.setString(4, r.boxName());
                    ps.setLong(5, r.createdAt());
                    ps.setString(6, r.depositData());
                    ps.setInt(7, r.luck());
                    ps.setDouble(8, r.shortfall());
                    ps.setString(9, r.resultData());
                    ps.setInt(10, r.stored() ? 1 : 0);
                    ps.addBatch();
                }
                ps.executeBatch();
                return null;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public List<SpinHistoryRow> page(int limit, int offset) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT * FROM spin_history ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?")) {
                ps.setInt(1, limit);
                ps.setInt(2, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    List<SpinHistoryRow> out = new ArrayList<>();
                    while (rs.next()) out.add(map(rs));
                    return out;
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public List<SpinHistoryRow> pageFor(UUID player, int limit, int offset) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT * FROM spin_history WHERE player_uuid = ? ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?")) {
                ps.setString(1, player.toString());
                ps.setInt(2, limit);
                ps.setInt(3, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    List<SpinHistoryRow> out = new ArrayList<>();
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
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM spin_history");
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public long countFor(UUID player) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM spin_history WHERE player_uuid = ?")) {
                ps.setString(1, player.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getLong(1);
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private SpinHistoryRow map(ResultSet rs) throws SQLException {
        return new SpinHistoryRow(
                rs.getLong("id"),
                UUID.fromString(rs.getString("player_uuid")),
                rs.getString("player_name"),
                rs.getString("box_id"),
                rs.getString("box_name"),
                rs.getLong("created_at"),
                rs.getString("deposit_data"),
                rs.getInt("luck"),
                rs.getDouble("shortfall"),
                rs.getString("result_data"),
                rs.getInt("stored") != 0);
    }
}
