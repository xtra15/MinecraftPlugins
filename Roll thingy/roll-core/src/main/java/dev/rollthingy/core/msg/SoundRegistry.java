package dev.rollthingy.core.msg;

import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;

public class SoundRegistry {
    public enum Event { OPEN, CLICK, CONFIRM, CANCEL, SPIN, WIN, WIN_RARE, ZONK, ERROR }

    private final Map<Event, Entry> entries = new EnumMap<>(Event.class);

    public record Entry(boolean enabled, Sound sound, float pitch, float volume) {}

    public SoundRegistry(File soundsFile) {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(soundsFile);
        for (Event event : Event.values()) {
            String path = event.name().toLowerCase();
            boolean enabled = cfg.getBoolean(path + ".enabled", true);
            String soundName = cfg.getString(path + ".sound", event == Event.ERROR
                    ? "ENTITY_VILLAGER_NO" : "UI_BUTTON_CLICK");
            float pitch = (float) cfg.getDouble(path + ".pitch", 1.0);
            float volume = (float) cfg.getDouble(path + ".volume", 1.0);
            Sound sound;
            try {
                sound = Sound.valueOf(soundName);
            } catch (IllegalArgumentException e) {
                sound = event == Event.ERROR ? Sound.ENTITY_VILLAGER_NO : Sound.UI_BUTTON_CLICK;
            }
            entries.put(event, new Entry(enabled, sound, pitch, volume));
        }
    }

    public void play(Player player, Event event) {
        Entry e = entries.get(event);
        if (e == null || !e.enabled()) return;
        player.playSound(player.getLocation(), e.sound(), e.volume(), e.pitch());
    }
}