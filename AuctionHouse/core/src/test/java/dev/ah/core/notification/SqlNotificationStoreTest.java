package dev.ah.core.notification;

import dev.ah.core.db.SqliteDatabase;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SqlNotificationStoreTest {

    @Test
    void addUnreadAndMarkRead() {
        SqliteDatabase db = SqliteDatabase.inMemory();
        db.init();
        SqlNotificationStore s = new SqlNotificationStore(db);
        UUID p = UUID.randomUUID();
        long n1 = s.add(new Notification(0, p, "offer.accepted", 1L, null));
        s.add(new Notification(0, p, "offer.rejected", 2L, null));
        assertEquals(2, s.unread(p).size());
        s.markRead(n1, 9L);
        assertEquals(1, s.unread(p).size());
        db.close();
    }
}