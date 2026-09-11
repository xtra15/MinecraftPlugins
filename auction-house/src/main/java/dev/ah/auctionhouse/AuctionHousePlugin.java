package dev.ah.auctionhouse;

import dev.ah.auctionhouse.command.AhCommand;
import dev.ah.auctionhouse.listener.ChatSearchListener;
import dev.ah.auctionhouse.listener.JoinNotifier;
import dev.ah.auctionhouse.listener.MenuListener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class AuctionHousePlugin extends JavaPlugin {
    private AhServices services;
    private BukkitTask sweepTask;

    @Override
    public void onEnable() {
        services = new AhServices(this);
        AhCommand command = new AhCommand(this);
        getCommand("ah").setExecutor(command);
        getCommand("ah").setTabCompleter(command);
        getServer().getPluginManager().registerEvents(new MenuListener(services.gui()), this);
        getServer().getPluginManager().registerEvents(new JoinNotifier(this), this);
        getServer().getPluginManager().registerEvents(new ChatSearchListener(this), this);
        startSweep();
        getLogger().info("AuctionHouse enabled. Economy=" + (services.isEconomyEnabled() ? "ON" : "OFF (item trading)"));
    }

    @Override
    public void onDisable() {
        stopSweep();
        if (services != null) {
            services.gui().returnAll();
            services.close();
        }
    }

    private void startSweep() {
        long interval = services.sweepIntervalMs();
        sweepTask = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            int expired = services.sweep().sweep(System.currentTimeMillis());
            if (expired > 0) getLogger().info(expired + " listings expired");
        }, interval, interval);
    }

    private void stopSweep() {
        if (sweepTask != null) {
            sweepTask.cancel();
            sweepTask = null;
        }
    }

    public AhServices services() {
        return services;
    }

    public void reloadServices() {
        stopSweep();
        if (services != null) {
            services.gui().returnAll();
            services.close();
        }
        services = new AhServices(this);
        startSweep();
        getLogger().info("AuctionHouse reloaded");
    }
}