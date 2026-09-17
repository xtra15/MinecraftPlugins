package dev.rollthingy.core.box;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PenaltyMathTest {
    @Test
    void shortfallClamps() {
        assertEquals(0.5, PenaltyMath.shortfall(8, 16));
        assertEquals(0.0, PenaltyMath.shortfall(16, 16));
        assertEquals(0.0, PenaltyMath.shortfall(99, 16));
        assertEquals(1.0, PenaltyMath.shortfall(0, 16));
        assertEquals(0.0, PenaltyMath.shortfall(5, 0));
    }

    @Test
    void contributedScoreCombinesModes() {
        List<PaymentRequirement> reqs = List.of(
                new PaymentRequirement("DIAMOND_DATA", 2, true),
                new PaymentRequirement("STONE_DATA", 4, false));
        double score = PenaltyMath.contributedScore(reqs,
                Map.of("DIAMOND_DATA", 2),
                Map.of("STONE_DATA", 2),
                0.25,
                Map.of("DIRT", 4));
        // 2 (diamonds) + 2 (stone counts fully within loose req) + 4*0.25 (dirt = wrong) = 5
        assertEquals(5.0, score, 1e-9);
    }

    @Test
    void luckPercentScalesAgainstRequiredAndClamps() {
        assertEquals(100, PenaltyMath.luckPercent(2, 2));
        assertEquals(50, PenaltyMath.luckPercent(1, 2));
        assertEquals(0, PenaltyMath.luckPercent(0, 2));
        assertEquals(100, PenaltyMath.luckPercent(2, 0));
        assertEquals(100, PenaltyMath.luckPercent(9, 2));
        assertEquals(33, PenaltyMath.luckPercent(1, 3));
    }
}