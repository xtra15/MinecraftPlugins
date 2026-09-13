package dev.ah.core.db;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

public final class SqliteDatabase implements AutoCloseable {
    private final Connection connection;
    private final ThreadLocal<Boolean> inTx = ThreadLocal.withInitial(() -> false);
    private boolean closed;

    public SqliteDatabase(File file) {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        } catch (ClassNotFoundException | SQLException e) {
            throw new IllegalStateException("failed to open database", e);
        }
    }

    public static SqliteDatabase inMemory() {
        try {
            Class.forName("org.sqlite.JDBC");
            Connection c = DriverManager.getConnection(
                    "jdbc:sqlite:file:" + UUID.randomUUID() + "?mode=memory&cache=shared");
            return new SqliteDatabase(c);
        } catch (ClassNotFoundException | SQLException e) {
            throw new IllegalStateException("failed to open in-memory database", e);
        }
    }

    private SqliteDatabase(Connection c) {
        this.connection = c;
    }

    public Connection conn() {
        return connection;
    }

    public void init() {
        runPragmas();
        transactAll(DDL);
        ensureColumn("listings", "search_text");
    }

    private void runPragmas() {
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA synchronous=NORMAL");
            st.execute("PRAGMA busy_timeout=5000");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void ensureColumn(String table, String column) {
        transact(c -> {
            boolean exists = false;
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
                while (rs.next()) {
                    if (column.equals(rs.getString(2))) {
                        exists = true;
                        break;
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            if (!exists) {
                try (Statement st = c.createStatement()) {
                    st.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " TEXT");
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
            return null;
        });
    }

    public <T> T transact(Transaction<T> body) {
        synchronized (this) {
            boolean already = Boolean.TRUE.equals(inTx.get());
            if (!already) {
                try {
                    connection.setAutoCommit(false);
                    inTx.set(true);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
            try {
                T result = body.run(connection);
                if (!already) {
                    connection.commit();
                }
                return result;
            } catch (SQLException e) {
                if (!already) {
                    try { connection.rollback(); } catch (SQLException ignored) {}
                }
                throw new RuntimeException("database transaction failed", e);
            } catch (RuntimeException e) {
                if (!already) {
                    try { connection.rollback(); } catch (SQLException ignored) {}
                }
                throw e;
            } finally {
                if (!already) {
                    inTx.remove();
                    try { connection.setAutoCommit(true); } catch (SQLException ignored) {}
                }
            }
        }
    }

    private void transactAll(String sql) {
        transact(c -> {
            try (Statement st = c.createStatement()) {
                for (String statement : sql.split(";")) {
                    if (!statement.isBlank()) st.execute(statement);
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    public void wipe() {
        transact(c -> {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("DELETE FROM notifications");
                st.executeUpdate("DELETE FROM balances");
                st.executeUpdate("DELETE FROM sales_log");
                st.executeUpdate("DELETE FROM claims");
                st.executeUpdate("DELETE FROM offers");
                st.executeUpdate("DELETE FROM listings");
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        try { connection.close(); } catch (SQLException ignored) {}
    }

    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS listings (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              owner_uuid TEXT NOT NULL,
              item_data TEXT NOT NULL,
              price REAL,
              duration_ms INTEGER NOT NULL,
              created_at INTEGER NOT NULL,
              expires_at INTEGER NOT NULL,
              status TEXT NOT NULL,
              search_text TEXT
            );
            CREATE INDEX IF NOT EXISTS idx_listings_status ON listings(status);
            CREATE INDEX IF NOT EXISTS idx_listings_owner ON listings(owner_uuid);
            CREATE TABLE IF NOT EXISTS offers (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              listing_id INTEGER NOT NULL,
              offerer_uuid TEXT NOT NULL,
              items_data TEXT NOT NULL,
              status TEXT NOT NULL,
              created_at INTEGER NOT NULL,
              decided_at INTEGER
            );
            CREATE INDEX IF NOT EXISTS idx_offers_listing ON offers(listing_id);
            CREATE INDEX IF NOT EXISTS idx_offers_offerer ON offers(offerer_uuid);
            CREATE TABLE IF NOT EXISTS claims (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              owner_uuid TEXT NOT NULL,
              items_data TEXT NOT NULL,
              source_type TEXT NOT NULL,
              source_id INTEGER NOT NULL,
              created_at INTEGER NOT NULL,
              claimed_at INTEGER
            );
            CREATE INDEX IF NOT EXISTS idx_claims_owner ON claims(owner_uuid);
            CREATE TABLE IF NOT EXISTS sales_log (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              listing_id INTEGER NOT NULL,
              seller_uuid TEXT NOT NULL,
              buyer_uuid TEXT NOT NULL,
              item_data TEXT NOT NULL,
              outcome TEXT NOT NULL,
              accepted_at INTEGER NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_sales_log_seller ON sales_log(seller_uuid);
            CREATE INDEX IF NOT EXISTS idx_sales_log_buyer ON sales_log(buyer_uuid);
            CREATE TABLE IF NOT EXISTS balances (
              uuid TEXT PRIMARY KEY,
              balance REAL NOT NULL
            );
            CREATE TABLE IF NOT EXISTS notifications (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              player_uuid TEXT NOT NULL,
              message_key TEXT NOT NULL,
              created_at INTEGER NOT NULL,
              read_at INTEGER
            );
            CREATE INDEX IF NOT EXISTS idx_notifications_player ON notifications(player_uuid);
            """;

    @FunctionalInterface
    public interface Transaction<T> {
        T run(Connection c) throws SQLException;
    }
}