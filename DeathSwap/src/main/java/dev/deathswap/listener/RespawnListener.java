package dev.deathswap.listener;

import dev.deathswap.DeathSwap;
import dev.deathswap.Game;
import dev.deathswap.GameState;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

public class RespawnListener implements Listener {
    private final DeathSwap plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public RespawnListener(DeathSwap plugin) { this.plugin = plugin; }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Game game = plugin.getGame();
        GameState st = game.getState();
        if (st == GameState.SWAPPING || st == GameState.TRAP || st == GameState.FIGHTING) {
            event.setRespawnLocation(game.arena().getLobby());
        }
    }
}
