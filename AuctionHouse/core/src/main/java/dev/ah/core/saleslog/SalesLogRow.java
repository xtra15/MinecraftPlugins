package dev.ah.core.saleslog;

import java.util.UUID;

public record SalesLogRow(long id, long listingId, UUID seller, UUID buyer,
                          String itemData, String outcome, long acceptedAt) {}