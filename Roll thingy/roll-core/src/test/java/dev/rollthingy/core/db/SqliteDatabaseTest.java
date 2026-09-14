package dev.rollthingy.core.db;

import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SqliteDatabaseTest {
    @Test
    void transactCommitsAndRollsBack() throws Exception {
        SqliteDatabase db = SqliteDatabase.inMemory();
        db.init();
        AtomicInteger i = new AtomicInteger();
        db.transact(c -> {
            try (var st = c.createStatement()) {
                st.executeUpdate("INSERT INTO cooldowns (player_uuid, box_id, last_spin_at) VALUES ('a','b',1)");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        });
        assertThrows(RuntimeException.class, () -> db.transact(c -> {
            try (var st = c.createStatement()) {
                st.executeUpdate("INSERT INTO cooldowns (player_uuid, box_id, last_spin_at) VALUES ('c','d',2)");
                throw new RuntimeException("boom");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }));
        db.transact(c -> {
            try (var st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM cooldowns")) {
                rs.next();
                i.set(rs.getInt(1));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        });
        assertEquals(1, i.get());
        db.close();
    }
}