package dev.rollthingy;

import dev.rollthingy.command.RollCommand;
import dev.rollthingy.listener.ChatPrompt;
import dev.rollthingy.listener.ClaimNoticeListener;
import dev.rollthingy.listener.MenuListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class RollThingyPlugin extends JavaPlugin {
    private RollServices services;

    @Override
    public void onEnable() {
        services = new RollServices(this);
        Bukkit.getPluginManager().registerEvents(new MenuListener(services.gui()), this);
        Bukkit.getPluginManager().registerEvents(new ChatPrompt(this), this);
        Bukkit.getPluginManager().registerEvents(new ClaimNoticeListener(this), this);
        RollCommand command = new RollCommand(this);
        org.bukkit.command.PluginCommand roll = getCommand("roll");
        if (roll != null) {
            roll.setExecutor(command);
            roll.setTabCompleter(command);
        }
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