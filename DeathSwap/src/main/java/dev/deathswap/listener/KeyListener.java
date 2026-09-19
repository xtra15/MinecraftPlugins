package dev.deathswap.listener;

import dev.deathswap.DeathSwap;
import dev.deathswap.Game;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class KeyListener implements Listener {
    private final DeathSwap plugin;

    public KeyListener(DeathSwap plugin) { this.plugin = plugin; }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getItem() == null) return;
        if (event.getItem().getType() != Game.KEY_MATERIAL) return;
        Game game = plugin.getGame();
        if (!game.isKeyItem(event.getItem())) return;
        event.setCancelled(true);
        game.tryStart(event.getPlayer());
    }
}