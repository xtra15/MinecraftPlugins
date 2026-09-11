package dev.ah.core.listing;

import dev.ah.core.db.SqliteDatabase;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SqlListingStoreTest {

    private SqlListingStore store(SqliteDatabase db) {
        db.init();
        return new SqlListingStore(db);
    }

    @Test
    void createAndFetchById() {
        SqliteDatabase db = SqliteDatabase.inMemory();
        SqlListingStore s = store(db);
        UUID owner = UUID.randomUUID();
        long id = s.create(new Listing(0, owner, "AAA", null, 86_400_000L, 1000L, 1000L + 86_400_000L, "ACTIVE"));
        Optional<Listing> found = s.byId(id);
        assertTrue(found.isPresent());
        assertEquals(owner, found.get().owner());
        assertEquals("AAA", found.get().itemData());
        assertEquals(1000L, found.get().createdAt());
        db.close();
    }

    @Test
    void activePageIsNewestFirstAndPaginates() {
        SqliteDatabase db = SqliteDatabase.inMemory();
        SqlListingStore s = store(db);
        for (int i = 1; i <= 5; i++) {
            s.create(new Listing(0, UUID.randomUUID(), "D" + i, null, 0, i, i + 1000, "ACTIVE"));
        }
        List<Listing> page1 = s.activePage(3, 0, null, false);
        List<Listing> page2 = s.activePage(3, 3, null, false);
        assertEquals("D5", page1.get(0).itemData());
        assertEquals(3, page1.size());
        assertEquals(2, page2.size());
        assertEquals("D2", page2.get(0).itemData());
        db.close();
    }

    @Test
    void activePageOldestFirst() {
        SqliteDatabase db = SqliteDatabase.inMemory();
        SqlListingStore s = store(db);
        for (int i = 1; i <= 3; i++) {
            s.create(new Listing(0, UUID.randomUUID(), "O" + i, null, 0, i, i + 1000, "ACTIVE"));
        }
        assertEquals("O1", s.activePage(5, 0, null, true).get(0).itemData());
        db.close();
    }

    @Test
    void activePageMatchesSearchTextInsensitively() {
        SqliteDatabase db = SqliteDatabase.inMemory();
        SqlListingStore s = store(db);
        s.create(new Listing(0, UUID.randomUUID(), "A", null, 0, 3, 1000, "ACTIVE", "diamond sword"));
        s.create(new Listing(0, UUID.randomUUID(), "B", null, 0, 2, 1000, "ACTIVE", "iron pickaxe"));
        s.create(new Listing(0, UUID.randomUUID(), "C", null, 0, 1, 1000, "CANCELLED", "diamond sword"));

        List<Listing> matches = s.activePage(10, 0, "Diamond", false);
        assertEquals(1, matches.size());
        assertEquals("A", matches.get(0).itemData());
        assertEquals(1, s.countActive("diamond"));
        assertEquals(2, s.countActive(null));
        db.close();
    }

    @Test
    void searchWildcardsAreLiterals() {
        SqliteDatabase db = SqliteDatabase.inMemory();
        SqlListingStore s = store(db);
        s.create(new Listing(0, UUID.randomUUID(), "A", null, 0, 3, 1000, "ACTIVE", "iron ingot"));
        s.create(new Listing(0, UUID.randomUUID(), "B", null, 0, 2, 1000, "ACTIVE", "diamond"));

        assertEquals(0, s.countActive("%"));
        assertEquals(0, s.countActive("_"));
        assertEquals(0, s.countActive("iron_ingot"));
        assertEquals(1, s.countActive("iron ingot"));
        assertEquals(1, s.activePage(10, 0, "iron ingot", false).size());
        db.close();
    }

    @Test
    void countByCountsAllStatuses() {
        SqliteDatabase db = SqliteDatabase.inMemory();
        SqlListingStore s = store(db);
        UUID bob = UUID.randomUUID();
        s.create(new Listing(0, bob, "ACT", null, 0, 3, 1000, "ACTIVE"));
        s.create(new Listing(0, bob, "SOL", null, 0, 2, 1000, "SOLD"));
        s.create(new Listing(0, UUID.randomUUID(), "OTH", null, 0, 1, 1000, "ACTIVE"));
        assertEquals(2, s.countBy(bob));
        db.close();
    }

    @Test
    void activeOwnersReturnsDistinctOwnersOfActiveListings() {
        SqliteDatabase db = SqliteDatabase.inMemory();
        SqlListingStore s = store(db);
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        s.create(new Listing(0, alice, "1", null, 0, 1, 100, "ACTIVE"));
        s.create(new Listing(0, alice, "2", null, 0, 2, 100, "ACTIVE"));
        s.create(new Listing(0, bob, "3", null, 0, 3, 100, "SOLD"));
        List<UUID> owners = s.activeOwners(10, 0);
        assertEquals(1, owners.size());
        assertEquals(alice, owners.get(0));
        db.close();
    }

    @Test
    void activeOwnersPagedAndCounted() {
        SqliteDatabase db = SqliteDatabase.inMemory();
        SqlListingStore s = store(db);
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        s.create(new Listing(0, alice, "1", null, 0, 1, 100, "ACTIVE"));
        s.create(new Listing(0, alice, "2", null, 0, 2, 100, "ACTIVE"));
        s.create(new Listing(0, bob, "3", null, 0, 3, 100, "ACTIVE"));
        s.create(new Listing(0, UUID.randomUUID(), "4", null, 0, 3, 100, "SOLD"));
        assertEquals(2, s.countActiveOwners());
        assertEquals(bob, s.activeOwners(1, 0).get(0));
        assertEquals(alice, s.activeOwners(1, 1).get(0));
        db.close();
    }

    @Test
    void countActiveGroupedByOwnerBatchesAcrossIds() {
        SqliteDatabase db = SqliteDatabase.inMemory();
        SqlListingStore s = store(db);
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID carol = UUID.randomUUID();
        s.create(new Listing(0, alice, "1", null, 0, 1, 100, "ACTIVE"));
        s.create(new Listing(0, alice, "2", null, 0, 2, 100, "ACTIVE"));
        s.create(new Listing(0, bob, "3", null, 0, 3, 100, "ACTIVE"));
        s.create(new Listing(0, carol, "4", null, 0, 4, 100, "SOLD"));
        var counts = s.countActiveGroupedByOwner(List.of(alice, bob, carol));
        assertEquals(2, counts.get(alice));
        assertEquals(1, counts.get(bob));
        assertNull(counts.get(carol));
        assertTrue(s.countActiveGroupedByOwner(List.of()).isEmpty());
        db.close();
    }

    @Test
    void expiredActiveBeforeReturnsOnlyExpiredActive() {
        SqliteDatabase db = SqliteDatabase.inMemory();
        SqlListingStore s = store(db);
        s.create(new Listing(0, UUID.randomUUID(), "old", null, 0, 1, 100, "ACTIVE"));
        s.create(new Listing(0, UUID.randomUUID(), "new", null, 0, 1, 500, "ACTIVE"));
        s.create(new Listing(0, UUID.randomUUID(), "sold", null, 0, 1, 100, "SOLD"));
        List<Listing> expired = s.expiredActiveBefore(200L);
        assertEquals(1, expired.size());
        assertEquals("old", expired.get(0).itemData());
        db.close();
    }

    @Test
    void updateStatusAndCounts() {
        SqliteDatabase db = SqliteDatabase.inMemory();
        SqlListingStore s = store(db);
        UUID bob = UUID.randomUUID();
        long id = s.create(new Listing(0, bob, "AAA", null, 0, 1, 100, "ACTIVE"));
        s.create(new Listing(0, UUID.randomUUID(), "BBB", null, 0, 1, 100, "ACTIVE"));
        assertEquals(2, s.countActive());
        assertEquals(1, s.countActiveBy(bob));
        s.updateStatus(id, "CANCELLED");
        assertEquals(1, s.countActive());
        assertFalse(s.byId(id).get().isActive());
        db.close();
    }
}