package dev.rollthingy.gui;

import dev.rollthingy.RollServices;
import dev.rollthingy.core.gui.ChestGui;
import dev.rollthingy.core.misc.ItemBundleCodec;
import dev.rollthingy.core.msg.SoundRegistry;
import dev.rollthingy.core.store.SpinHistoryRow;
import dev.rollthingy.gui.admin.AdminGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Spin log viewer: every spin's items, luck and prize. Filter null = everyone (admin). */
public class HistoryGui {
    private final RollServices services;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int PER_PAGE = 28;
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    public HistoryGui(RollServices services) {
        this.services = services;
    }

    public void open(Player viewer, int page, UUID filter) {
        long total = filter == null ? services.history().count() : services.history().countFor(filter);
        long pages = Math.max(1, (total + PER_PAGE - 1) / PER_PAGE);
        int pageIndex = Math.max(0, Math.min(page, (int) pages - 1));
        List<SpinHistoryRow> rows = filter == null
                ? services.history().page(PER_PAGE, pageIndex * PER_PAGE)
                : services.history().pageFor(filter, PER_PAGE, pageIndex * PER_PAGE);

        String title = filter == null
                ? services.messages().get("history.title",
                        Map.of("page", String.valueOf(pageIndex + 1), "pages", String.valueOf(pages)))
                : services.messages().get("history.title-player",
                        Map.of("page", String.valueOf(pageIndex + 1), "pages", String.valueOf(pages)));
        ChestGui gui = new ChestGui(6, title);
        gui.fill(services.config().fillerItem());
        gui.fillRect(9, 44, null);

        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
        for (int i = 0; i < rows.size() && i < slots.length; i++) {
            SpinHistoryRow row = rows.get(i);
            ItemStack icon = resultIcon(row);
            List<Component> lore = new ArrayList<>();
            lore.add(MM.deserialize(services.messages().get("history.row-player",
                    Map.of("name", row.playerName()))));
            lore.add(MM.deserialize(services.messages().get("history.row-box",
                    Map.of("box", row.boxName()))));
            lore.add(MM.deserialize(services.messages().get("history.row-date",
                    Map.of("date", DATE.format(Instant.ofEpochMilli(row.createdAt()))))));
            lore.add(MM.deserialize(services.messages().get("history.row-luck",
                    Map.of("luck", String.valueOf(row.luck())))));
            if (row.stored()) lore.add(MM.deserialize(services.messages().get("history.row-stored")));
            icon.editMeta(meta -> meta.lore(lore));
            int slot = slots[i];
            gui.on(slot, clk -> openDetail(clk.player(), pageIndex, filter, row)).set(slot, icon);
        }
        if (rows.isEmpty()) {
            gui.set(22, button(Material.PAPER, services.messages().get("history.empty")));
        }

        gui.on(45, clk -> {
            services.sounds().play(clk.player(), SoundRegistry.Event.CLICK);
            open(clk.player(), pageIndex - 1, filter);
        }).set(45, button(Material.ARROW, "<gray><<"));
        gui.on(53, clk -> {
            services.sounds().play(clk.player(), SoundRegistry.Event.CLICK);
            open(clk.player(), pageIndex + 1, filter);
        }).set(53, button(Material.ARROW, ">>"));
        gui.on(0, clk -> {
            if (filter == null) {
                clk.player().closeInventory();
                new AdminGui(services).open(clk.player(), 0);
            } else {
                clk.player().closeInventory();
            }
        }).set(0, button(Material.SPECTRAL_ARROW, "<gray>Back"));

        services.sounds().play(viewer, SoundRegistry.Event.OPEN);
        gui.open(viewer);
    }

    private void openDetail(Player viewer, int page, UUID filter, SpinHistoryRow row) {
        ChestGui gui = new ChestGui(4, services.messages().get("history.detail-title"));
        gui.fill(services.config().fillerItem());
        gui.fillRect(9, 26, null);

        List<ItemStack> deposit = safeDecode(row.depositData());
        int shown = Math.min(deposit.size(), 5);
        int start = 11 + ((5 - shown) / 2);
        for (int i = 0; i < shown; i++) gui.set(start + i, deposit.get(i));
        if (deposit.size() > shown) {
            gui.set(start + shown, button(Material.PAPER, services.messages().get("detail.payment-more",
                    Map.of("count", String.valueOf(deposit.size() - shown)))));
        }

        ItemStack prize = resultIcon(row);
        List<Component> lore = new ArrayList<>();
        lore.add(MM.deserialize(services.messages().get("history.row-player", Map.of("name", row.playerName()))));
        lore.add(MM.deserialize(services.messages().get("history.row-box", Map.of("box", row.boxName()))));
        lore.add(MM.deserialize(services.messages().get("history.row-date",
                Map.of("date", DATE.format(Instant.ofEpochMilli(row.createdAt()))))));
        lore.add(MM.deserialize(services.messages().get("history.row-luck",
                Map.of("luck", String.valueOf(row.luck())))));
        if (row.stored()) lore.add(MM.deserialize(services.messages().get("history.row-stored")));
        prize.editMeta(meta -> meta.lore(lore));
        gui.set(22, prize);

        gui.on(0, clk -> open(clk.player(), page, filter))
                .set(0, button(Material.SPECTRAL_ARROW, "<gray>Back"));
        services.sounds().play(viewer, SoundRegistry.Event.CLICK);
        gui.open(viewer);
    }

    private ItemStack resultIcon(SpinHistoryRow row) {
        if ("ZONK".equals(row.resultData())) return new ItemStack(Material.GRAY_DYE, 1);
        List<ItemStack> decoded = safeDecode(row.resultData());
        if (decoded.isEmpty()) return new ItemStack(Material.BARRIER, 1);
        return decoded.get(0).clone();
    }

    private List<ItemStack> safeDecode(String data) {
        try {
            return ItemBundleCodec.decode(data);
        } catch (IllegalArgumentException | NullPointerException e) {
            return List.of();
        }
    }

    private ItemStack button(Material material, String text) {
        ItemStack item = new ItemStack(material, 1);
        item.editMeta(meta -> meta.displayName(MM.deserialize(text)));
        return item;
    }
}
