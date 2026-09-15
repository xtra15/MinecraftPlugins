package dev.rollthingy.core.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpinReelTest {

    @Test
    void winnerLandsInCentreSlot() {
        for (int size = 9; size <= 60; size += 3) {
            for (int passes = 1; passes <= 3; passes++) {
                int target = SpinReel.stopOffset(size, passes);
                int centreCell = (target + SpinReel.CENTRE_CELL) % size;
                assertEquals(SpinReel.winnerIndex(size), centreCell,
                        "winner must centre for size=" + size + " passes=" + passes);
            }
        }
    }

    @Test
    void steppingNeverOvershootsAndLandsExactlyOnTarget() {
        for (int size : new int[]{12, 29, 50}) {
            for (int passes = 1; passes <= 3; passes++) {
                int target = SpinReel.stopOffset(size, passes);
                int position = 0;
                int guard = 0;
                while (position < target && guard++ < 10_000) {
                    int remaining = target - position;
                    int advance = Math.min(SpinReel.advanceFor(remaining), remaining);
                    position += advance;
                    assertTrue(position <= target, "overshoot at size=" + size + " passes=" + passes);
                }
                assertEquals(target, position);
            }
        }
    }

    @Test
    void delaysDecelerateNearTheTarget() {
        assertTrue(SpinReel.delayFor(40) <= SpinReel.delayFor(10));
        assertTrue(SpinReel.delayFor(10) < SpinReel.delayFor(2));
    }

    @Test
    void winnerSlidesInFromTheRightBeforeCentring() {
        // The frame before the stop, the winner must already be visible to the right of centre.
        int size = 29;
        int target = SpinReel.stopOffset(size, 2);
        int previous = target - 1;
        int previousCell = (previous + SpinReel.CENTRE_CELL + 1) % size;
        assertEquals(SpinReel.winnerIndex(size), previousCell);
    }
}