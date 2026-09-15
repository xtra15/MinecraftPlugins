package dev.rollthingy.core.gui;

/**
 * Pure math for the spin reel animation (no Bukkit dependencies).
 *
 * <p>The reel is a circular strip of items rendered into a 9-slot window. The window's centre
 * cell shows items.get((offset + CENTRE_CELL) % size). The winning item is stored as the LAST
 * element of the ring, so choosing a stop offset congruent to size-1 puts the winner dead centre,
 * and keeping the offset below that value at every step makes the winner slide in from the right.
 */
public final class SpinReel {
    public static final int VISIBLE = 9;
    public static final int CENTRE_CELL = 4;

    private SpinReel() {}

    /** Index of the winning item inside the ring (always the final element). */
    public static int winnerIndex(int ringSize) {
        return ringSize - 1;
    }

    /**
     * Stop offset that centres the winner after {@code passes} full rotations of the ring.
     * Backs off 5 so (offset + CENTRE_CELL) mod size lands on the final element.
     */
    public static int stopOffset(int ringSize, int passes) {
        return passes * ringSize - 5;
    }

    /** Items moved per tick; speeds up early, slows down as the target approaches. */
    public static int advanceFor(int remaining) {
        if (remaining > 32) return 4;
        if (remaining > 16) return 2;
        return 1;
    }

    /** Ticks between frames; grows as the reel decelerates near the target. */
    public static long delayFor(int remaining) {
        if (remaining > 32) return 1L;
        if (remaining > 16) return 2L;
        if (remaining > 6) return 3L;
        return 5L;
    }
}