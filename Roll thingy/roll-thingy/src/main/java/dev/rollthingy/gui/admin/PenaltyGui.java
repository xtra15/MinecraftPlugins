package dev.rollthingy.gui.admin;

import dev.rollthingy.RollServices;
import dev.rollthingy.core.box.Box;
import dev.rollthingy.core.box.Penalty;
import dev.rollthingy.core.gui.ChestGui;
import dev.rollthingy.core.msg.SoundRegistry;
import dev.rollthingy.listener.ChatPrompt;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * Human-friendly editor for the three payment-luck numbers. Each setting is its own labelled row
 * with a plain-English description, so "loose value / rare cut / zonk feed" no longer needs guessing.
 */
public class PenaltyGui {
    private final RollServices services;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public PenaltyGui(RollServices services) {
        this.services = services;
    }

    public void open(Player admin, Box box) {
        Penalty p = box.penalty();
        ChestGui gui = new ChestGui(6, services.messages().get("admin.penalty-title"));
        gui.fill(services.config().fillerItem());
        gui.fillRect(9, 44, null);

        gui.on(10, clk -> services.sounds().play(admin, SoundRegistry.Event.CLICK))
                .set(10, setting(Material.STICK,
                        services.messages().get("admin.penalty-wrong"),
                        services.messages().get("admin.penalty-wrong-lore"),
                        services.messages().get("admin.penalty-wrong-current")));
        gui.on(13, clk -> edit(admin, box, Setting.RARE))
                .set(13, setting(Material.RED_DYE,
                        services.messages().get("admin.penalty-rare"),
                        services.messages().get("admin.penalty-rare-lore"),
                        services.messages().get("admin.penalty-rare-current",
                                Map.of("rare", fmt(p.rareCut())))));
        gui.on(16, clk -> edit(admin, box, Setting.FEED))
                .set(16, setting(Material.BLAZE_POWDER,
                        services.messages().get("admin.penalty-feed"),
                        services.messages().get("admin.penalty-feed-lore"),
                        services.messages().get("admin.penalty-feed-current",
                                Map.of("feed", fmt(p.zonkFeed())))));

        ItemStack info = new ItemStack(Material.BOOK, 1);
        info.editMeta(meta -> {
            meta.displayName(MM.deserialize(services.messages().get("admin.penalty-info-title")));
            meta.lore(List.of(MM.deserialize(services.messages().get("admin.penalty-info"))));
        });
        gui.set(22, info);

        gui.on(0, clk -> new EditGui(services).open(clk.player(), box))
                .set(0, button(Material.SPECTRAL_ARROW, "<gray>Back"));

        services.sounds().play(admin, SoundRegistry.Event.OPEN);
        gui.open(admin);
    }

    private enum Setting { RARE, FEED }

    private void edit(Player admin, Box box, Setting setting) {
        Penalty p = box.penalty();
        services.sounds().play(admin, SoundRegistry.Event.CLICK);
        switch (setting) {
            case RARE -> {
                admin.sendMessage(MM.deserialize(services.messages().get("admin.penalty-rare-prompt",
                        Map.of("rare", fmt(p.rareCut())))));
                ChatPrompt.prompt(admin, input -> {
                    Double v = parse(input);
                    if (v == null) { badInput(admin, "50"); return; }
                    save(admin, box, new Penalty(p.looseValue(), clamp(v, 0, 100), p.zonkFeed()));
                });
            }
            case FEED -> {
                admin.sendMessage(MM.deserialize(services.messages().get("admin.penalty-feed-prompt",
                        Map.of("feed", fmt(p.zonkFeed())))));
                ChatPrompt.prompt(admin, input -> {
                    Double v = parse(input);
                    if (v == null) { badInput(admin, "20"); return; }
                    save(admin, box, new Penalty(p.looseValue(), p.rareCut(), clamp(v, 0, 100)));
                });
            }
        }
    }

    private void save(Player admin, Box box, Penalty penalty) {
        Box updated = new Box(box.id(), box.name(), box.icon(), box.payment(), penalty,
                box.cooldownSeconds(), box.zonk(), box.tiers());
        services.cache().addOrUpdate(updated);
        admin.sendMessage(MM.deserialize(services.messages().get("admin.saved")));
        admin.closeInventory();
        open(admin, updated);
    }

    private void badInput(Player admin, String example) {
        admin.sendMessage(MM.deserialize(services.messages().get("errors.bad-input",
                Map.of("example", example))));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Double parse(String input) {
        try {
            return Double.parseDouble(input.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String fmt(double value) {
        if (value == Math.floor(value)) return String.valueOf((long) value);
        return String.valueOf(value);
    }

    private ItemStack setting(Material material, String name, String description, String current) {
        ItemStack item = new ItemStack(material, 1);
        item.editMeta(meta -> {
            meta.displayName(MM.deserialize(name));
            meta.lore(List.of(MM.deserialize(description), MM.deserialize(current)));
        });
        return item;
    }

    private ItemStack button(Material material, String text) {
        ItemStack item = new ItemStack(material, 1);
        item.editMeta(meta -> meta.displayName(MM.deserialize(text)));
        return item;
    }
}