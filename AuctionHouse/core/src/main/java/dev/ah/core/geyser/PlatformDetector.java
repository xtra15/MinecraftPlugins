package dev.ah.core.geyser;

import org.bukkit.entity.Player;
import java.lang.reflect.Method;
import java.util.UUID;

public class PlatformDetector {
    private final Method floodgatePlayerMethod;

    public PlatformDetector() {
        Method method = null;
        try {
            Class<?> api = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            method = api.getMethod("isFloodgatePlayer", UUID.class);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            // Floodgate not installed — Java-only fallback
        }
        this.floodgatePlayerMethod = method;
    }

    public boolean isBedrock(Player player) {
        if (floodgatePlayerMethod == null) return false;
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            return (boolean) floodgatePlayerMethod.invoke(api, player.getUniqueId());
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}