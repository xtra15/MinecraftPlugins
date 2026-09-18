package dev.rollthingy.core.store;

import dev.rollthingy.core.db.SqliteDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SpinHistoryStoreTest {
    private SqliteDatabase db;
    private SqlSpinHistoryStore store;

    @BeforeEach
    void setUp() {
        db = SqliteDatabase.inMemory();
        db.init();
        store = new SqlSpinHistoryStore(db);
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    private SpinHistoryRow row(UUID player, String name, long at) {
        return new SpinHistoryRow(0, player, name, "box1", "Box One", at,
                "DEPOSIT", 100, 0.0, "PRIZE", false);
    }

    @Test
    void batchInsertThenCounts() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        store.addAll(List.of(row(alice, "Alice", 100L), row(alice, "Alice", 200L), row(bob, "Bob", 300L)));
        assertEquals(3, store.count());
        assertEquals(2, store.countFor(alice));
        assertEquals(1, store.countFor(bob));
    }

    @Test
    void pageIsNewestFirstAndFilterable() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        store.addAll(List.of(row(alice, "Alice", 100L), row(bob, "Bob", 300L), row(alice, "Alice", 200L)));
        List<SpinHistoryRow> all = store.page(10, 0);
        assertEquals(3, all.size());
        assertEquals(300L, all.get(0).createdAt());
        assertEquals("Bob", all.get(0).playerName());
        List<SpinHistoryRow> mine = store.pageFor(alice, 10, 0);
        assertEquals(2, mine.size());
        assertEquals(200L, mine.get(0).createdAt());
        assertEquals(100, mine.get(0).luck());
        assertEquals("PRIZE", mine.get(0).resultData());
        List<SpinHistoryRow> second = store.pageFor(alice, 1, 1);
        assertEquals(1, second.size());
        assertEquals(100L, second.get(0).createdAt());
    }

    @Test
    void emptyBatchIsNoop() {
        store.addAll(List.of());
        assertEquals(0, store.count());
    }
}
