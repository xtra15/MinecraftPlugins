package dev.ah.auctionhouse.listener;

import dev.ah.auctionhouse.AhServices;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class JoinNotifier implements Listener {
    private final AhServices services;

    public JoinNotifier(AhServices services) {
        this.services = services;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
    }
}