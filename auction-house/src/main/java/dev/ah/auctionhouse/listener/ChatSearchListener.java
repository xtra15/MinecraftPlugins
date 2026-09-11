package dev.ah.auctionhouse.listener;

import dev.ah.auctionhouse.AuctionHousePlugin;
import dev.ah.auctionhouse.gui.AhMainGui;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChatSearchListener implements Listener {
    private static final Set<UUID> PENDING = ConcurrentHashMap.newKeySet();
    private final AuctionHousePlugin plugin;

    public ChatSearchListener(AuctionHousePlugin plugin) {
        this.plugin = plugin;
    }

    public static void prompt(Player player, String message) {
        PENDING.add(player.getUniqueId());
        player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(message));
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!PENDING.remove(player.getUniqueId())) return;
        event.setCancelled(true);
        String term = PlainTextComponentSerializer.plainText().serialize(event.message());
        Bukkit.getScheduler().runTask(plugin, () -> new AhMainGui(plugin.services()).openSearch(player, term));
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        PENDING.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        PENDING.remove(event.getPlayer().getUniqueId());
    }
}