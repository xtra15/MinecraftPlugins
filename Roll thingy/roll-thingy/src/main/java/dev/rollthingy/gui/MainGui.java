package dev.rollthingy.gui;

import dev.rollthingy.RollServices;
import dev.rollthingy.core.box.Box;
import dev.rollthingy.core.gui.ChestGui;
import dev.rollthingy.core.msg.SoundRegistry;
import dev.rollthingy.core.misc.Pager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

public class MainGui {
    private final RollServices services;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public MainGui(RollServices services) {
        this.services = services;
    }

    public void open(Player player, int page) {
        List<Box> all = services.cache().boxes();
        int pageSize = services.config().pageSize();
        long pages = Pager.pageCount(all.size(), pageSize);
        int pageIndex = Pager.safePage(page, pages);
        int from = pageIndex * pageSize;
        int to = Math.min(all.size(), from + pageSize);
        List<Box> pageBoxes = all.subList(from, to);

        ChestGui gui = new ChestGui(6, services.messages().get("main.title",
                Map.of("page", String.valueOf(pageIndex + 1), "pages", String.valueOf(pages))));
        gui.fill(services.config().fillerItem());
        gui.fillRect(9, 44, null);

        int slot = 9;
        for (Box box : pageBoxes) {
            if (slot > 44) break;
            ItemStack icon = services.cache().icon(box);
            icon.editMeta(meta -> meta.displayName(MM.deserialize("<gold>" + box.name())));
            gui.on(slot, clk -> new BoxDetailGui(services).open(clk.player(), box));
            gui.set(slot, icon);
            slot++;
        }

        gui.on(45, clk -> {
            services.sounds().play(clk.player(), SoundRegistry.Event.CLICK);
            open(clk.player(), pageIndex - 1);
        }).set(45, arrow("gui.page-prev"));
        gui.on(53, clk -> {
            services.sounds().play(clk.player(), SoundRegistry.Event.CLICK);
            open(clk.player(), pageIndex + 1);
        }).set(53, arrow("gui.page-next"));
        services.sounds().play(player, SoundRegistry.Event.OPEN);
        gui.open(player);
    }

    private ItemStack arrow(String key) {
        ItemStack item = new ItemStack(Material.ARROW, 1);
        item.editMeta(meta -> {
            meta.displayName(MM.deserialize(services.messages().get(key)));
        });
        return item;
    }
}