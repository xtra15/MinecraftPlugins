package dev.ah.core.saleslog;

import java.util.List;
import java.util.UUID;

public interface SalesLogStore {
    long add(SalesLogRow row);
    List<SalesLogRow> recent(int limit);
    List<SalesLogRow> bySeller(UUID seller, int limit);
    List<SalesLogRow> byBuyer(UUID buyer, int limit);
}