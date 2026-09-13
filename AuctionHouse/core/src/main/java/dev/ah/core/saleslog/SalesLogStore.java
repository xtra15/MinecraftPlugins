package dev.ah.core.saleslog;

import java.util.List;
import java.util.UUID;

public interface SalesLogStore {
    long add(SalesLogRow row);
    default List<SalesLogRow> recent(int limit) {
        return recent(limit, 0);
    }
    List<SalesLogRow> recent(int limit, int offset);
    long count();
    List<SalesLogRow> bySeller(UUID seller, int limit);
    List<SalesLogRow> byBuyer(UUID buyer, int limit);
}