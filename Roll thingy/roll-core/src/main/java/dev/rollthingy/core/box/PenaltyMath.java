package dev.rollthingy.core.box;

import java.util.List;
import java.util.Map;

public final class PenaltyMath {
    private PenaltyMath() {}

    public static double shortfall(double contributed, double required) {
        if (required <= 0) return 0;
        if (contributed <= 0) return 1;
        double ratio = contributed / required;
        return Math.max(0, Math.min(1, 1 - ratio));
    }

    /** Matched stacks count 1 each; unlisted junk always counts 0. */
    public static double contributedScore(List<PaymentRequirement> requirements,
                                          Map<String, Integer> strictCounts,
                                          Map<String, Integer> looseCounts) {
        double score = 0;
        for (PaymentRequirement req : requirements) {
            if (req.strict()) {
                score += strictCounts.getOrDefault(req.data(), 0);
            } else {
                score += looseCounts.getOrDefault(req.data(), 0);
            }
        }
        return score;
    }

    /** How much luck (0-100) a deposit gives relative to the required payment amount. */
    public static int luckPercent(double contributed, double required) {
        if (required <= 0) return 100;
        if (contributed <= 0) return 0;
        double pct = 100.0 * contributed / required;
        return (int) Math.min(100, Math.round(pct));
    }
}