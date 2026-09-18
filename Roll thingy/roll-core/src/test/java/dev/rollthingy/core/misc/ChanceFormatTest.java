package dev.rollthingy.core.misc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChanceFormatTest {
    @Test
    void zeroShowsAsZeroPercent() {
        assertEquals("0%", ChanceFormat.format(0));
    }

    @Test
    void normalPercentagesUseFixedDecimalsAndTrim() {
        assertEquals("50%", ChanceFormat.format(50));
        assertEquals("1.5%", ChanceFormat.format(1.5));
        assertEquals("0.5%", ChanceFormat.format(0.5));
        assertEquals("0.0001%", ChanceFormat.format(0.0001));
        assertEquals("99.9999%", ChanceFormat.format(99.9999));
    }

    @Test
    void tinyPositiveChanceNeverLooksLikeZero() {
        for (double d : new double[]{0.0000999, 0.00005, 1e-7, 5e-10, 1.23e-12}) {
            String out = ChanceFormat.format(d);
            assertFalse(out.contains("0%"), out);
            assertTrue(out.startsWith("1 in "), out);
        }
    }

    @Test
    void tinyChanceShowsGroupedOneInValue() {
        assertEquals("1 in 1,001,001", ChanceFormat.format(0.0000999));
        assertEquals("1 in 200,000,000,000", ChanceFormat.format(5e-10));
    }

    @Test
    void oneInXAlwaysRendersOneInFormat() {
        assertEquals("0%", ChanceFormat.oneInX(0));
        assertEquals("1 in 2", ChanceFormat.oneInX(50));
        assertEquals("1 in 67", ChanceFormat.oneInX(1.5));
        assertEquals("1 in 200", ChanceFormat.oneInX(0.5));
        assertEquals("1 in 1,001,001", ChanceFormat.oneInX(0.0000999));
    }
}