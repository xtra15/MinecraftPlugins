package dev.rollthingy;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import dev.rollthingy.listener.MenuListener;

public final class RollThingyPlugin extends JavaPlugin {
    private RollServices services;

    @Override
    public void onEnable() {
        services = new RollServices(this);
        Bukkit.getPluginManager().registerEvents(new MenuListener(services.gui()), this);
        getLogger().info("RollThingy enabled.");
    }

    @Override
    public void onDisable() {
        if (services != null) {
            services.close();
            services = null;
        }
        getLogger().info("RollThingy disabled.");
    }

    public RollServices services() {
        return services;
    }
}