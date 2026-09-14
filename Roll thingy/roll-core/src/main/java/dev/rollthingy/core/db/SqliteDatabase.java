package dev.rollthingy.core.db;

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
                if (!already) connection.commit();
                return result;
            } catch (SQLException e) {
                if (!already) {
                    try {
                        connection.rollback();
                    } catch (SQLException ignored) {}
                }
                throw new RuntimeException("database transaction failed", e);
            } catch (RuntimeException e) {
                if (!already) {
                    try {
                        connection.rollback();
                    } catch (SQLException ignored) {}
                }
                throw e;
            } finally {
                if (!already) {
                    inTx.remove();
                    try {
                        connection.setAutoCommit(true);
                    } catch (SQLException ignored) {}
                }
            }
        }
    }

    public void wipe() {
        transact(c -> {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("DELETE FROM cooldowns");
                st.executeUpdate("DELETE FROM claims");
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
        try {
            connection.close();
        } catch (SQLException ignored) {}
    }

    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS cooldowns (
              player_uuid TEXT NOT NULL,
              box_id TEXT NOT NULL,
              last_spin_at INTEGER NOT NULL,
              PRIMARY KEY (player_uuid, box_id)
            );
            CREATE TABLE IF NOT EXISTS claims (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              player_uuid TEXT NOT NULL,
              items_data TEXT NOT NULL,
              source TEXT NOT NULL,
              created_at INTEGER NOT NULL,
              claimed_at INTEGER
            );
            CREATE INDEX IF NOT EXISTS idx_claims_owner ON claims(player_uuid);
            """;

    @FunctionalInterface
    public interface Transaction<T> {
        T run(Connection c) throws SQLException;
    }
}