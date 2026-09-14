package dev.rollthingy.core.store;

import java.util.UUID;

public interface CooldownStore {
    /** 0 = allowed (and recorded); else milliseconds remaining. */
    long attempt(UUID player, String boxId, long now, long cooldownSeconds);
}