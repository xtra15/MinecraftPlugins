package dev.rollthingy.core.store;

import dev.rollthingy.core.db.SqliteDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClaimStoreTest {
    private SqliteDatabase db;
    private SqlClaimStore store;

    @BeforeEach
    void setUp() {
        db = SqliteDatabase.inMemory();
        db.init();
        store = new SqlClaimStore(db);
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void addThenCountUnclaimedThenMarkClaimed() {
        UUID owner = UUID.randomUUID();
        long id = store.add("DATA", owner, "box1", 100L);
        assertTrue(id > 0);
        assertEquals(1, store.countUnclaimed(owner));
        List<ClaimRow> page = store.unclaimedPage(owner, 10, 0);
        assertEquals(1, page.size());
        assertEquals("DATA", page.get(0).itemsData());
        assertEquals("box1", page.get(0).source());
        assertNull(page.get(0).claimedAt());
        assertTrue(store.markClaimed(id, 200L));
        assertEquals(0, store.countUnclaimed(owner));
    }

    @Test
    void pageIsNewestFirst() {
        UUID owner = UUID.randomUUID();
        store.add("old", owner, "box", 100L);
        store.add("new", owner, "box", 200L);
        List<ClaimRow> page = store.unclaimedPage(owner, 1, 0);
        assertEquals(1, page.size());
        assertEquals("new", page.get(0).itemsData());
    }
}