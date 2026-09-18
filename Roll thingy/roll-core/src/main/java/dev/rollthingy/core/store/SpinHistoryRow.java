package dev.rollthingy.core.store;

import java.util.UUID;

public record SpinHistoryRow(long id, UUID playerUuid, String playerName, String boxId, String boxName,
                             long createdAt, String depositData, int luck, double shortfall,
                             String resultData, boolean stored) {}
