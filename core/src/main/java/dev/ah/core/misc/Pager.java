package dev.ah.core.misc;

import java.util.ArrayList;
import java.util.List;

public final class Pager<T> {
    private final List<T> items;
    private final int perPage;

    public Pager(List<T> items, int perPage) {
        if (perPage < 1) throw new IllegalArgumentException("perPage must be >= 1");
        this.items = new ArrayList<>(items);
        this.perPage = perPage;
    }

    public int pages() {
        return Math.max(1, (items.size() + perPage - 1) / perPage);
    }

    public List<T> page(int pageIndex) {
        int start = pageIndex * perPage;
        if (start < 0 || start >= items.size()) return List.of();
        return items.subList(start, Math.min(start + perPage, items.size()));
    }

    public int pageSize() {
        return perPage;
    }
}