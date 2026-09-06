package dev.ah.core.economy;

import dev.ah.core.db.SqliteDatabase;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.OptionalDouble;
import java.util.UUID;

public class SqlBalanceStore {
    private final SqliteDatabase db;

    public SqlBalanceStore(SqliteDatabase db) {
        this.db = db;
    }

    public OptionalDouble balance(UUID player) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT balance FROM balances WHERE uuid = ?")) {
                ps.setString(1, player.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? OptionalDouble.of(rs.getDouble("balance")) : OptionalDouble.empty();
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void set(UUID player, double amount) {
        db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO balances (uuid, balance) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET balance = excluded.balance")) {
                ps.setString(1, player.toString());
                ps.setDouble(2, amount);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    public boolean tryTake(UUID player, double amount) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE balances SET balance = balance - ? WHERE uuid = ? AND balance >= ?")) {
                ps.setDouble(1, amount);
                ps.setString(2, player.toString());
                ps.setDouble(3, amount);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }
}