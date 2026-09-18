package dev.rollthingy.core.store;

import java.util.List;
import java.util.UUID;

public interface SpinHistoryStore {
    /** Single-transaction batch insert; keeps multi-spin logging to one commit. */
    void addAll(List<SpinHistoryRow> rows);

    List<SpinHistoryRow> page(int limit, int offset);

    List<SpinHistoryRow> pageFor(UUID player, int limit, int offset);

    long count();

    long countFor(UUID player);
}
