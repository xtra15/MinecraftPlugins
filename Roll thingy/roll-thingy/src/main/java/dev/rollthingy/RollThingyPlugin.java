package dev.rollthingy;

import org.bukkit.plugin.java.JavaPlugin;

public final class RollThingyPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("RollThingy enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("RollThingy disabled.");
    }
}