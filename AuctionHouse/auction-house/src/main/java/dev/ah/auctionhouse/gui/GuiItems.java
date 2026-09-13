package dev.ah.auctionhouse.gui;

import dev.ah.auctionhouse.AhServices;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import java.util.List;
import java.util.Map;

final class GuiItems {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private GuiItems() {}

    static ItemStack button(AhServices services, Material material, String nameKey) {
        ItemStack icon = new ItemStack(material, 1);
        icon.editMeta(meta -> meta.displayName(MM.deserialize(services.messages().get(nameKey))));
        return icon;
    }

    static ItemStack button(AhServices services, Material material, String nameKey,
                            String loreKey, Map<String, String> placeholders) {
        ItemStack icon = button(services, material, nameKey);
        icon.editMeta(meta -> meta.lore(List.of(MM.deserialize(services.messages().get(loreKey, placeholders)))));
        return icon;
    }
}