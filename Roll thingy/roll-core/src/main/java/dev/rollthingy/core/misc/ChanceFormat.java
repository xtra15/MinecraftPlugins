package dev.rollthingy.core.misc;

import java.util.Locale;

/** Formats a percentage (0-100). Tiny chances never show as a literal "0%". */
public final class ChanceFormat {
    private ChanceFormat() {}

    public static String format(double percent) {
        if (percent <= 0) return "0%";
        if (percent >= 0.0001) {
            return trim(String.format(Locale.ROOT, "%.4f", percent)) + "%";
        }
        long in = Math.round(1.0 / percent);
        return "1 in " + String.format(Locale.US, "%,d", Math.max(1, in));
    }

    private static String trim(String s) {
        s = s.replaceAll("0+$", "");
        s = s.replaceAll("\\.$", "");
        return s.isEmpty() ? "0" : s;
    }
}