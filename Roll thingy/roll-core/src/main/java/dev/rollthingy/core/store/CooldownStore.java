package dev.rollthingy.core.store;

import java.util.UUID;

public interface CooldownStore {
    /** 0 = allowed (and recorded); else milliseconds remaining. */
    long attempt(UUID player, String boxId, long now, long cooldownSeconds);

    /** Last recorded spin time, or -1 if never spun. */
    long lastSpinAt(UUID player, String boxId);
}