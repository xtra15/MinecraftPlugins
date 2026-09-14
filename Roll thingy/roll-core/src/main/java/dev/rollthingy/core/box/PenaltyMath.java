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

    public static double contributedScore(List<PaymentRequirement> requirements,
                                          Map<String, Integer> strictCounts,
                                          Map<String, Integer> looseCounts,
                                          double looseValue,
                                          Map<String, Integer> wrongCounts) {
        double score = 0;
        for (PaymentRequirement req : requirements) {
            if (req.strict()) {
                score += strictCounts.getOrDefault(req.data(), 0);
            } else {
                score += looseCounts.getOrDefault(req.data(), 0);
            }
        }
        double wrong = wrongCounts.values().stream().mapToInt(Integer::intValue).sum();
        score += wrong * looseValue;
        return score;
    }
}