package dev.ah.auctionhouse.notify;

import dev.ah.auctionhouse.AhServices;
import dev.ah.core.msg.SoundRegistry;
import dev.ah.core.notification.Notification;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.UUID;

public class NotificationService {
    private final AhServices services;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public NotificationService(AhServices services) {
        this.services = services;
    }

    public void notifyOfferDecision(UUID offerer, boolean accepted) {
        String key = accepted ? "offers.accepted-by-seller" : "offers.rejected-by-seller";
        Player online = Bukkit.getPlayer(offerer);
        if (online != null) {
            online.sendMessage(MM.deserialize(services.messages().get(key)));
            services.sounds().play(online, accepted ? SoundRegistry.Event.OFFER_ACCEPTED : SoundRegistry.Event.OFFER_REJECTED);
        } else {
            services.notifications().add(new Notification(0, offerer, key, System.currentTimeMillis(), null));
        }
    }

    public void flush(Player player) {
        for (Notification n : services.notifications().unread(player.getUniqueId())) {
            player.sendMessage(MM.deserialize(services.messages().get(n.messageKey())));
            services.notifications().markRead(n.id(), System.currentTimeMillis());
        }
    }
}