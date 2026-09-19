package dev.deathswap.listener;

import dev.deathswap.DeathSwap;
import dev.deathswap.Game;
import dev.deathswap.GameState;
import dev.deathswap.DeathTeam;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class JoinLeaveListener implements Listener {
    private final DeathSwap plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public JoinLeaveListener(DeathSwap plugin) { this.plugin = plugin; }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        Game game = plugin.getGame();
        DeathTeam smaller = game.red().size() <= game.blue().size() ? game.red() : game.blue();
        game.assignTeam(p, smaller == game.red() ? "red" : "blue");
        p.setGameMode(GameMode.SURVIVAL);
        game.giveKey(p);
        GameState st = game.getState();
        if (st == GameState.SWAPPING || st == GameState.TRAP || st == GameState.FIGHTING) {
            p.teleport(game.arena().getLobby(), org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
        }
        event.setJoinMessage(p.getName() + " joined.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        Game game = plugin.getGame();
        if (game.red().contains(p.getUniqueId())) game.red().remove(p.getUniqueId());
        else if (game.blue().contains(p.getUniqueId())) game.blue().remove(p.getUniqueId());
        org.bukkit.scoreboard.Team sbTeam = game.getTeam(p);
        if (sbTeam != null) sbTeam.removeEntry(p.getName());
        game.onDeath(p.getUniqueId());
        event.setQuitMessage(p.getName() + " left.");
    }
}
