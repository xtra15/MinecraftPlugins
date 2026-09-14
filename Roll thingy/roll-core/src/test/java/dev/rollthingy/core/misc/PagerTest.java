package dev.rollthingy.core.misc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PagerTest {
    @Test
    void pageCountRoundsUp() {
        assertEquals(1, Pager.pageCount(0, 36));
        assertEquals(1, Pager.pageCount(1, 36));
        assertEquals(2, Pager.pageCount(37, 36));
        assertEquals(2, Pager.pageCount(72, 36));
    }

    @Test
    void rejectsInvalidPageSize() {
        assertThrows(IllegalArgumentException.class, () -> Pager.pageCount(10, 0));
    }

    @Test
    void safePageClamps() {
        assertEquals(0, Pager.safePage(-5, 3));
        assertEquals(2, Pager.safePage(5, 3));
        assertEquals(1, Pager.safePage(1, 3));
    }
}