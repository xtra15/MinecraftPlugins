package dev.rollthingy;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class RollConfig {
    private final JavaPlugin plugin;
    private final FileConfiguration cfg;

    public RollConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.saveResource("config.yml", false);
        plugin.saveResource("lang.yml", false);
        plugin.saveResource("sounds.yml", false);
        this.cfg = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "config.yml"));
    }

    public int pageSize() {
        return cfg.getInt("page-size", 36);
    }

    public File boxesDir() {
        return new File(plugin.getDataFolder(), "boxes");
    }

    public Material guiFillerMaterial() {
        try {
            return Material.valueOf(cfg.getString("fill-item", "GRAY_STAINED_GLASS_PANE"));
        } catch (IllegalArgumentException | NullPointerException e) {
            return Material.GRAY_STAINED_GLASS_PANE;
        }
    }

    public ItemStack fillerItem() {
        return new ItemStack(guiFillerMaterial(), 1);
    }
}