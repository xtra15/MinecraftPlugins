package dev.ah.core.msg;

import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import java.io.File;
import java.util.EnumMap;
import java.util.Map;

public class SoundRegistry {
    public enum Event { OPEN, CLICK, OFFER_RECEIVED, OFFER_ACCEPTED, OFFER_REJECTED, SALE, EXPIRY, ERROR }

    private final Map<Event, Entry> entries = new EnumMap<>(Event.class);

    public record Entry(boolean enabled, String sound, float pitch, float volume) {}

    public SoundRegistry(File soundsFile) {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(soundsFile);
        for (Event event : Event.values()) {
            String path = event.name().toLowerCase();
            boolean enabled = cfg.getBoolean(path + ".enabled", true);
            String sound = cfg.getString(path + ".sound", event == Event.ERROR
                    ? Sound.ENTITY_VILLAGER_NO.name() : Sound.UI_BUTTON_CLICK.name());
            float pitch = (float) cfg.getDouble(path + ".pitch", 1.0);
            float volume = (float) cfg.getDouble(path + ".volume", 1.0);
            entries.put(event, new Entry(enabled, sound, pitch, volume));
        }
    }

    public void play(Player player, Event event) {
        Entry e = entries.get(event);
        if (e == null || !e.enabled()) return;
        try {
            Sound sound = Sound.valueOf(e.sound());
            player.playSound(player.getLocation(), sound, e.volume(), e.pitch());
        } catch (IllegalArgumentException ignored) {
            // bad sound name in config — ignore, no crash
        }
    }
}