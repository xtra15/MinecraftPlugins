package dev.ah.core.economy;

import dev.ah.core.db.SqliteDatabase;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SqlBalanceStoreTest {

    @Test
    void setBalanceTryTakeAndGive() {
        SqliteDatabase db = SqliteDatabase.inMemory();
        db.init();
        SqlBalanceStore s = new SqlBalanceStore(db);
        UUID p = UUID.randomUUID();
        assertTrue(s.balance(p).isEmpty());
        s.set(p, 1000.0);
        assertEquals(1000.0, s.balance(p).getAsDouble(), 0.001);
        assertTrue(s.tryTake(p, 400.0));
        assertEquals(600.0, s.balance(p).getAsDouble(), 0.001);
        assertFalse(s.tryTake(p, 601.0));
        assertEquals(600.0, s.balance(p).getAsDouble(), 0.001);
        db.close();
    }
}