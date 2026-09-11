package dev.ah.auctionhouse.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import java.util.List;
import java.util.Locale;

final class IconUtil {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private IconUtil() {}

    static ItemStack clean(ItemStack item) {
        ItemStack icon = item.clone();
        icon.editMeta(meta -> {
            Component custom = meta.displayName();
            meta.displayName(custom != null ? custom : MM.deserialize("<white>" + pretty(item.getType())));
            meta.lore(List.of());
        });
        return icon;
    }

    static String pretty(Material material) {
        String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
            }
        }
        return sb.toString().trim();
    }
}