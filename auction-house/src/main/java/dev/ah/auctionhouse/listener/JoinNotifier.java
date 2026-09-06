package dev.ah.auctionhouse.listener;

import dev.ah.auctionhouse.AhServices;
import dev.ah.auctionhouse.notify.NotificationService;
import dev.ah.core.msg.SoundRegistry;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import java.util.Map;

public class JoinNotifier implements Listener {
    private final AhServices services;
    private final NotificationService notifications;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public JoinNotifier(AhServices services) {
        this.services = services;
        this.notifications = new NotificationService(services);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var p = event.getPlayer();
        notifications.flush(p);

        long unclaimed = services.claims().countUnclaimed(p.getUniqueId());
        long pending = services.offers().countPendingForSeller(p.getUniqueId());
        if (unclaimed > 0) {
            p.sendMessage(MM.deserialize(services.messages().get("claims.unclaimed", Map.of("count", String.valueOf(unclaimed)))));
        }
        if (pending > 0) {
            p.sendMessage(MM.deserialize(services.messages().get("offers.received")));
            services.sounds().play(p, SoundRegistry.Event.OFFER_RECEIVED);
        }
    }
}