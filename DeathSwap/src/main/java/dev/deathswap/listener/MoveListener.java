package dev.deathswap.listener;

import dev.deathswap.DeathSwap;
import dev.deathswap.Game;
import dev.deathswap.GameState;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class MoveListener implements Listener {
    private final DeathSwap plugin;

    public MoveListener(DeathSwap plugin) { this.plugin = plugin; }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        Game game = plugin.getGame();
        if (game.getState() != GameState.BUILD) return;
        Location to = event.getTo();
        if (!game.arena().inBuildArea(to)) {
            Player p = event.getPlayer();
            Location back = game.red().getMembers().contains(p.getUniqueId())
                    ? game.arena().getRedCenter() : game.arena().getBlueCenter();
            p.teleport(back, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
        }
    }
}
