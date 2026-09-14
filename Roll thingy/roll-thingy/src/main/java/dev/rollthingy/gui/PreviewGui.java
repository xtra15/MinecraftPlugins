package dev.rollthingy.gui;

import dev.rollthingy.RollServices;
import dev.rollthingy.core.box.Box;
import dev.rollthingy.core.box.OddsEngine;
import dev.rollthingy.core.gui.ChestGui;
import dev.rollthingy.core.misc.ItemBundleCodec;
import dev.rollthingy.core.msg.SoundRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PreviewGui {
    private final RollServices services;

    public PreviewGui(RollServices services) {
        this.services = services;
    }

    /** Sorted rarest (smallest chance) → easiest (largest chance). */
    public void open(Player player, Box box, int page) {
        OddsEngine.OddsModel model = services.cache().modelOf(box.id());
        List<Entry> entries = buildEntries(box, model);
        entries.sort(Comparator.comparingDouble(Entry::chance));

        int pageSize = 36;
        long pages = Math.max(1, (entries.size() + pageSize - 1) / pageSize);
        int pageIndex = Math.max(0, Math.min(page, (int) pages - 1));
        int from = pageIndex * pageSize;
        int to = Math.min(entries.size(), from + pageSize);

        String title = services.messages().get("preview.title")
                .replace("<page>", String.valueOf(pageIndex + 1))
                .replace("<pages>", String.valueOf(pages));
        ChestGui gui = new ChestGui(6, title);
        gui.fill(services.config().fillerItem());
        gui.fillRect(9, 44, null);

        if (entries.isEmpty()) {
            ItemStack empty = new ItemStack(Material.PAPER, 1);
            empty.editMeta(meta -> meta.displayName(Component.text(services.messages().get("preview.empty"))));
            gui.set(22, empty);
        }

        int slot = 9;
        for (int i = from; i < to && slot <= 44; i++) {
            Entry e = entries.get(i);
            gui.set(slot, e.icon);
            slot++;
        }

        gui.on(0, clk -> new BoxDetailGui(services).open(clk.player(), box))
                .set(0, button("Back"));
        gui.on(45, clk -> open(clk.player(), box, pageIndex - 1)).set(45, button("<gray><<"));
        gui.on(53, clk -> open(clk.player(), box, pageIndex + 1)).set(53, button(">>"));
        services.sounds().play(player, SoundRegistry.Event.OPEN);
        gui.open(player);
    }

    private record Entry(double chance, ItemStack icon) {}

    private List<Entry> buildEntries(Box box, OddsEngine.OddsModel model) {
        List<Entry> out = new ArrayList<>();
        for (int t = 0; t < box.tiers().size(); t++) {
            OddsEngine.TierChances tier = model.tiers().get(t);
            var boxTier = box.tiers().get(t);
            for (int i = 0; i < boxTier.items().size(); i++) {
                double chance = model.chanceOf(tier, i);
                ItemStack icon;
                try {
                    List<ItemStack> decoded = ItemBundleCodec.decode(boxTier.items().get(i).data());
                    icon = decoded.isEmpty() ? new ItemStack(Material.BARRIER) : decoded.get(0).clone();
                } catch (IllegalArgumentException e) {
                    icon = new ItemStack(Material.BARRIER);
                }
                String rarity = boxTier.name();
                String chanceStr = formatChance(chance);
                icon.editMeta(meta -> {
                    meta.displayName(Component.text(rarity).color(rarityColor(chance)));
                    meta.lore(List.of(Component.text(chanceStr + "%")));
                });
                out.add(new Entry(chance, icon));
            }
        }
        return out;
    }

    private static String formatChance(double chance) {
        if (chance >= 0.01) {
            String s = String.format(java.util.Locale.ROOT, "%.2f", chance);
            return s.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return String.format(java.util.Locale.ROOT, "%.6f", chance).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static TextColor rarityColor(double chance) {
        if (chance < 0.00001) return TextColor.color(0xFF5555);   // ultra rare
        if (chance < 0.001) return TextColor.color(0xAA00AA);     // rare
        if (chance < 0.05) return TextColor.color(0x5555FF);      // uncommon
        if (chance < 0.5) return TextColor.color(0x55AA55);       // common
        return TextColor.color(0xFFFFFF);                          // very common
    }

    private ItemStack button(String text) {
        ItemStack item = new ItemStack(Material.ARROW, 1);
        item.editMeta(meta -> meta.displayName(Component.text(text)));
        return item;
    }
}