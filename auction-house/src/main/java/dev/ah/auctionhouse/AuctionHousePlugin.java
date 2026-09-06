package dev.ah.auctionhouse;

import dev.ah.auctionhouse.command.AhCommand;
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
        AhCommand command = new AhCommand(services);
        getCommand("ah").setExecutor(command);
        getCommand("ah").setTabCompleter(command);
        getServer().getPluginManager().registerEvents(new MenuListener(services.gui()), this);
        getServer().getPluginManager().registerEvents(new JoinNotifier(services), this);

        long interval = services.sweepIntervalMs();
        sweepTask = getServer().getScheduler().runTaskTimer(this, () -> {
            int expired = services.sweep().sweep(System.currentTimeMillis());
            if (expired > 0) getLogger().info(expired + " listings expired");
        }, interval, interval);

        getLogger().info("AuctionHouse enabled. Economy=" + (services.isEconomyEnabled() ? "ON" : "OFF (item trading)"));
    }

    @Override
    public void onDisable() {
        if (sweepTask != null) sweepTask.cancel();
        if (services != null) services.close();
    }

    public AhServices services() {
        return services;
    }
}