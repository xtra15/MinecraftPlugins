package dev.ah.core.claim;

import dev.ah.core.db.SqliteDatabase;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SqlClaimStoreTest {

    @Test
    void addAndListUnclaimedForOwner() {
        SqliteDatabase db = SqliteDatabase.inMemory();
        db.init();
        SqlClaimStore s = new SqlClaimStore(db);
        UUID bob = UUID.randomUUID();
        s.add(new ClaimRow(0, bob, "ITEM1", "OFFER_REJECTED", 1, 10L, null));
        s.add(new ClaimRow(0, bob, "ITEM2", "LISTING_SOLD", 2, 11L, null));
        s.add(new ClaimRow(0, UUID.randomUUID(), "ITEM3", "OFFER_REJECTED", 3, 12L, null));

        assertEquals(2, s.countUnclaimed(bob));
        List<ClaimRow> rows = s.unclaimedFor(bob);
        assertEquals(2, rows.size());

        long id = rows.get(0).id();
        s.markClaimed(id, 99L);
        assertEquals(1, s.countUnclaimed(bob));
        assertEquals(99L, s.byId(id).get().claimedAt());
        db.close();
    }
}