package dev.rollthingy.core.misc;

public final class Pager {
    private Pager() {}

    public static long pageCount(long total, int pageSize) {
        if (pageSize <= 0) throw new IllegalArgumentException("pageSize must be > 0");
        if (total <= 0) return 1;
        return (total + pageSize - 1) / pageSize;
    }

    public static int safePage(long page, long pageCount) {
        if (page < 0) return 0;
        if (page >= pageCount) return (int) pageCount - 1;
        return (int) page;
    }
}