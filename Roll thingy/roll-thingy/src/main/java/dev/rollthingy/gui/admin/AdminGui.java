package dev.rollthingy.gui.admin;

import dev.rollthingy.RollServices;
import dev.rollthingy.core.box.Box;
import dev.rollthingy.core.box.BoxItem;
import dev.rollthingy.core.box.IconSpec;
import dev.rollthingy.core.box.PaymentRequirement;
import dev.rollthingy.core.box.Penalty;
import dev.rollthingy.core.box.Zonk;
import dev.rollthingy.core.gui.ChestGui;
import dev.rollthingy.core.msg.SoundRegistry;
import dev.rollthingy.gui.MainGui;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

public class AdminGui {
    private final RollServices services;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public AdminGui(RollServices services) {
        this.services = services;
    }

    public void open(Player admin, int page) {
        List<Box> all = services.cache().boxes();
        int pageSize = services.config().pageSize();
        long pages = Math.max(1, (all.size() + pageSize - 1) / pageSize);
        int pageIndex = Math.max(0, Math.min(page, (int) pages - 1));
        int from = pageIndex * pageSize;
        int to = Math.min(all.size(), from + pageSize);

        ChestGui gui = new ChestGui(6, services.messages().get("admin.title",
                Map.of("page", String.valueOf(pageIndex + 1), "pages", String.valueOf(pages))));
        gui.fill(services.config().fillerItem());
        gui.fillRect(9, 44, null);

        int slot = 9;
        for (int i = from; i < to && slot <= 44; i++) {
            Box box = all.get(i);
            ItemStack icon = services.cache().icon(box);
            icon.editMeta(meta -> {
                meta.displayName(MM.deserialize("<gold>" + box.name()));
                meta.lore(List.of(MM.deserialize("<gray>" + box.id())));

            });
            gui.on(slot, clk -> new EditGui(services).open(clk.player(), box));
            gui.set(slot, icon);
            slot++;
        }

        gui.on(45, clk -> {
            services.sounds().play(clk.player(), SoundRegistry.Event.CLICK);
            open(clk.player(), pageIndex - 1);
        }).set(45, button(Material.ARROW, "<gray><<"));
        gui.on(53, clk -> {
            services.sounds().play(clk.player(), SoundRegistry.Event.CLICK);
            open(clk.player(), pageIndex + 1);
        }).set(53, button(Material.ARROW, ">>"));
        gui.on(49, clk -> create(clk.player())).set(49, button(Material.EMERALD_BLOCK,
                services.messages().get("admin.create")));
        gui.on(0, clk -> { clk.player().closeInventory(); new MainGui(services).open(clk.player(), 0); })
                .set(0, button(Material.SPECTRAL_ARROW, "<gray>Back"));
        services.sounds().play(admin, SoundRegistry.Event.OPEN);
        gui.open(admin);
    }

    private void create(Player admin) {
        admin.sendMessage(MM.deserialize(services.messages().get("admin.name-prompt", Map.of("name", ""))));
        services.sounds().play(admin, SoundRegistry.Event.CLICK);
        dev.rollthingy.listener.ChatPrompt.prompt(admin, name -> {
            String clean = name.trim();
            if (clean.isEmpty()) {
                admin.sendMessage(MM.deserialize(services.messages().get("errors.bad-input",
                        Map.of("example", "my-box"))));
                return;
            }
            String id = clean.toLowerCase().replaceAll("[^a-z0-9-]", "-");
            Box box = new Box(id, clean, new IconSpec("CHEST", ""), List.of(),
                    new Penalty(0.5, 50, 20), 5L, new Zonk(true, 5.0), List.of());
            services.cache().addOrUpdate(box);
            admin.sendMessage(MM.deserialize(services.messages().get("admin.saved")));
            new IconPickerGui(services).open(admin, box);
        });
    }

    private ItemStack button(Material material, String text) {
        ItemStack item = new ItemStack(material, 1);
        item.editMeta(meta -> meta.displayName(MM.deserialize(text)));
        return item;
    }
}