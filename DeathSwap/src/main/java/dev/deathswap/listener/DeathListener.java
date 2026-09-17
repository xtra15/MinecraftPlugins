package dev.deathswap.listener;

import dev.deathswap.DeathSwap;
import dev.deathswap.Game;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class DeathListener implements Listener {
    private final DeathSwap plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public DeathListener(DeathSwap plugin) { this.plugin = plugin; }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        Game game = plugin.getGame();
        game.onDeath(p.getUniqueId());
        event.setKeepInventory(true);
        event.setDeathMessage(p.getName() + " was eliminated!");
    }
}
