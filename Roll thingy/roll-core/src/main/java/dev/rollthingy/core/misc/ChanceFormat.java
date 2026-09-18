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
        return "1 in " + compact(Math.max(1, Math.round(100.0 / percent)));
    }

    /** Always renders as "1 in N" with compact suffixes (e.g. 50% -> "1 in 2", tiny -> "1 in 1.5b"). */
    public static String oneInX(double percent) {
        if (percent <= 0) return "0%";
        return "1 in " + compact(Math.max(1, Math.round(100.0 / percent)));
    }

    /** Compact number: 1.5k / 2m / 1.001001m / 10t. Exact integer math, never rounds. */
    static String compact(long n) {
        if (n >= 1_000_000_000_000L) return parts(n, 1_000_000_000_000L) + "t";
        if (n >= 1_000_000_000L) return parts(n, 1_000_000_000L) + "b";
        if (n >= 1_000_000L) return parts(n, 1_000_000L) + "m";
        if (n >= 1_000L) return parts(n, 1_000L) + "k";
        return Long.toString(n);
    }

    private static String parts(long n, long unit) {
        long whole = n / unit;
        long rem = n % unit;
        if (rem == 0) return Long.toString(whole);
        int digits = String.valueOf(unit).length() - 1;
        String r = String.format(Locale.ROOT, "%0" + digits + "d", rem).replaceAll("0+$", "");
        return whole + "." + r;
    }

    private static String trim(String s) {
        s = s.replaceAll("0+$", "");
        s = s.replaceAll("\\.$", "");
        return s.isEmpty() ? "0" : s;
    }
}