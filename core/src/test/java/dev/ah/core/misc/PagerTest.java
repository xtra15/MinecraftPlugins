package dev.ah.core.misc;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PagerTest {

    @Test
    void pagesAcrossChunks() {
        Pager<Integer> p = new Pager<>(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), 4);
        assertEquals(3, p.pages());
        assertEquals(List.of(1, 2, 3, 4), p.page(0));
        assertEquals(List.of(5, 6, 7, 8), p.page(1));
        assertEquals(List.of(9, 10), p.page(2));
    }

    @Test
    void emptyInputYieldsOneEmptyPage() {
        Pager<String> p = new Pager<>(List.of(), 9);
        assertEquals(1, p.pages());
        assertTrue(p.page(0).isEmpty());
        assertTrue(p.page(5).isEmpty());
    }

    @Test
    void outOfRangeReturnsEmpty() {
        Pager<String> p = new Pager<>(List.of("a"), 9);
        assertTrue(p.page(2).isEmpty());
    }

    @Test
    void rejectsInvalidPageSize() {
        assertThrows(IllegalArgumentException.class, () -> new Pager<>(List.of("a"), 0));
    }
}