package dev.ah.core.msg;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

public class MessageRepository {
    private final YamlConfiguration lang;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public MessageRepository(File langFile, InputStream bundled) {
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(bundled, StandardCharsets.UTF_8));
        this.lang = YamlConfiguration.loadConfiguration(langFile);
        this.lang.setDefaults(defaults);
    }

    public String get(String key) {
        String value = lang.getString(key);
        return value == null ? "<red>Missing message: " + key : value;
    }

    public String get(String key, Map<String, String> placeholders) {
        String value = get(key);
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            value = value.replace("{" + e.getKey() + "}", e.getValue());
            value = value.replace("<" + e.getKey() + ">", e.getValue());
        }
        return value;
    }

    public Set<String> keys() {
        return lang.getKeys(false);
    }
}