package dev.deathswap;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.plugin.java.JavaPlugin;
import dev.deathswap.listener.DeathListener;
import dev.deathswap.listener.JoinLeaveListener;
import dev.deathswap.listener.RespawnListener;

public class DeathSwap extends JavaPlugin {
    private static DeathSwap instance;
    private Game game;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        game = new Game(this);
        getCommand("sswap").setExecutor(new DeathSwapCommand(this));
        Bukkit.getPluginManager().registerEvents(new DeathListener(this), this);
        Bukkit.getPluginManager().registerEvents(new RespawnListener(this), this);
        Bukkit.getPluginManager().registerEvents(new JoinLeaveListener(this), this);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (game.getState() == GameState.BUILD) game.tickBuild();
        }, 1L, 1L);
    }

    @Override
    public void onDisable() {
        game.reset();
    }

    public static DeathSwap getInstance() { return instance; }
    public Game getGame() { return game; }
}
