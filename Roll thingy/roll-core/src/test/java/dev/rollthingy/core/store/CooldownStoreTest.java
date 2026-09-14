package dev.rollthingy.core.store;

import dev.rollthingy.core.db.SqliteDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CooldownStoreTest {
    private SqliteDatabase db;
    private SqlCooldownStore store;

    @BeforeEach
    void setUp() {
        db = SqliteDatabase.inMemory();
        db.init();
        store = new SqlCooldownStore(db);
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void insertThenImmediateAttemptReturnsRemaining() {
        UUID player = UUID.randomUUID();
        long now = 1_000_000L;
        long remaining = store.attempt(player, "box", now, 10L);
        assertEquals(0L, remaining);
        remaining = store.attempt(player, "box", now + 5_000L, 10L);
        assertEquals(5_000L, remaining);
    }

    @Test
    void afterCooldownPassesAllowsAgain() {
        UUID player = UUID.randomUUID();
        long now = 1_000_000L;
        store.attempt(player, "box", now, 10L);
        long remaining = store.attempt(player, "box", now + 10_000L, 10L);
        assertEquals(0L, remaining);
    }
}