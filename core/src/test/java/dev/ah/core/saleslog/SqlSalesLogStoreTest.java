package dev.ah.core.saleslog;

import dev.ah.core.db.SqliteDatabase;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SqlSalesLogStoreTest {

    @Test
    void addAndQueryBySellerBuyerRecent() {
        SqliteDatabase db = SqliteDatabase.inMemory();
        db.init();
        SqlSalesLogStore s = new SqlSalesLogStore(db);
        UUID seller = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();
        s.add(new SalesLogRow(0, 1, seller, buyer, "SWORD", "ACCEPTED", 100L));
        s.add(new SalesLogRow(0, 2, seller, buyer, "PICK", "ACCEPTED", 200L));
        s.add(new SalesLogRow(0, 3, seller, UUID.randomUUID(), "AXE", "ACCEPTED", 300L));
        s.add(new SalesLogRow(0, 4, UUID.randomUUID(), buyer, "BOOTS", "ACCEPTED", 400L));

        assertEquals(4, s.recent(10).size());
        assertEquals(2, s.recent(2).size());
        assertEquals("BOOTS", s.recent(2).get(0).itemData());

        List<SalesLogRow> bySeller = s.bySeller(seller, 10);
        assertEquals(3, bySeller.size());

        List<SalesLogRow> byBuyer = s.byBuyer(buyer, 10);
        assertEquals(3, byBuyer.size());
        db.close();
    }

    @Test
    void recentPagedAndCounted() {
        SqliteDatabase db = SqliteDatabase.inMemory();
        db.init();
        SqlSalesLogStore s = new SqlSalesLogStore(db);
        UUID seller = UUID.randomUUID();
        s.add(new SalesLogRow(0, 1, seller, UUID.randomUUID(), "SWORD", "ACCEPTED", 100L));
        s.add(new SalesLogRow(0, 2, seller, UUID.randomUUID(), "PICK", "ACCEPTED", 200L));
        s.add(new SalesLogRow(0, 3, seller, UUID.randomUUID(), "AXE", "ACCEPTED", 300L));
        s.add(new SalesLogRow(0, 4, seller, UUID.randomUUID(), "BOOTS", "ACCEPTED", 400L));

        assertEquals(4, s.count());
        assertEquals("BOOTS", s.recent(2, 0).get(0).itemData());
        assertEquals("AXE", s.recent(2, 0).get(1).itemData());
        assertEquals("PICK", s.recent(2, 2).get(0).itemData());
        assertEquals("SWORD", s.recent(2, 2).get(1).itemData());
        db.close();
    }
}