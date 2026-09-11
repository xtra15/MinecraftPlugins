package dev.ah.core.db;

import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import static org.junit.jupiter.api.Assertions.*;

class SqliteDatabaseTest {

    @Test
    void missingTablesAreCreated() throws SQLException {
        SqliteDatabase db = SqliteDatabase.inMemory();
        db.init();
        try (Connection c = db.conn();
             ResultSet rs = c.getMetaData().getTables(null, null, "%", null)) {
            java.util.Set<String> tables = new java.util.HashSet<>();
            while (rs.next()) tables.add(rs.getString("TABLE_NAME").toLowerCase());
            for (String expect : new String[]{"listings", "offers", "claims", "sales_log", "balances", "notifications"}) {
                assertTrue(tables.contains(expect), "missing table " + expect);
            }
        }
        db.close();
    }

    @Test
    void transactionCommits() {
        SqliteDatabase db = SqliteDatabase.inMemory();
        db.init();
        Long id = db.transact(c -> {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("INSERT INTO notifications (player_uuid, message_key, created_at) VALUES ('u', 'x', 1)");
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return 1L;
        });
        assertEquals(1L, id);
        db.close();
    }

    @Test
    void transactionRollsBackOnFailure() throws SQLException {
        SqliteDatabase db = SqliteDatabase.inMemory();
        db.init();
        try (Statement st = db.conn().createStatement()) {
            st.executeUpdate("INSERT INTO notifications (player_uuid, message_key, created_at) VALUES ('u', 'x', 1)");
        }
        assertThrows(RuntimeException.class, () -> db.transact(c -> {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("INSERT INTO notifications (player_uuid, message_key, created_at) VALUES ('u2', 'y', 1)");
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            throw new SQLException("boom");
        }));
        try (Statement st = db.conn().createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM notifications")) {
            rs.next();
            assertEquals(1, rs.getInt(1));
        }
        db.close();
    }

    @Test
    void nestedTransactionRollsBackOnInnerRuntimeFailure() throws SQLException {
        SqliteDatabase db = SqliteDatabase.inMemory();
        db.init();
        assertThrows(RuntimeException.class, () -> db.transact(c -> {
            db.transact(c2 -> {
                try (Statement st = c2.createStatement()) {
                    st.executeUpdate("INSERT INTO notifications (player_uuid, message_key, created_at) VALUES ('u', 'x', 1)");
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
                throw new RuntimeException("store failure");
            });
            return null;
        }));
        try (Statement st = db.conn().createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM notifications")) {
            rs.next();
            assertEquals(0, rs.getInt(1), "inner runtime failure must roll back the whole outer transaction");
        }
        db.close();
    }

    @Test
    void nestedTransactionCommitsOnceAtOuterBoundary() throws SQLException {
        SqliteDatabase db = SqliteDatabase.inMemory();
        db.init();
        db.transact(c -> {
            db.transact(c2 -> {
                try (Statement st = c2.createStatement()) {
                    st.executeUpdate("INSERT INTO notifications (player_uuid, message_key, created_at) VALUES ('a', 'x', 1)");
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
                return null;
            });
            try (Statement st = c.createStatement()) {
                st.executeUpdate("INSERT INTO notifications (player_uuid, message_key, created_at) VALUES ('b', 'x', 1)");
            }
            return null;
        });
        try (Statement st = db.conn().createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM notifications")) {
            rs.next();
            assertEquals(2, rs.getInt(1));
        }
        db.close();
    }
}