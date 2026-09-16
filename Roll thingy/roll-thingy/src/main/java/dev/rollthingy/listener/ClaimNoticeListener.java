package dev.rollthingy.listener;

import dev.rollthingy.RollServices;
import dev.rollthingy.RollThingyPlugin;
import dev.rollthingy.core.msg.SoundRegistry;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Map;

public class ClaimNoticeListener implements Listener {
    private final RollThingyPlugin plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public ClaimNoticeListener(RollThingyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        RollServices services = plugin.services();
        long count = services.claims().countUnclaimed(player.getUniqueId());
        if (count <= 0) return;
        player.sendMessage(MM.deserialize(services.messages().get("claim.join-notice",
                Map.of("count", String.valueOf(count)))));
        services.sounds().play(player, SoundRegistry.Event.OPEN);
    }
}