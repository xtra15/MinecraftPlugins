package dev.rollthingy.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ChatPrompt implements Listener {
    private static final Map<UUID, Consumer<String>> PENDING = new ConcurrentHashMap<>();
    private static Plugin plugin;

    public ChatPrompt(Plugin plugin) {
        ChatPrompt.plugin = plugin;
    }

    public static void prompt(Player player, Consumer<String> onInput) {
        PENDING.put(player.getUniqueId(), onInput);
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Consumer<String> handler = PENDING.remove(player.getUniqueId());
        if (handler == null) return;
        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message());
        Bukkit.getScheduler().runTask(plugin, () -> handler.accept(input));
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