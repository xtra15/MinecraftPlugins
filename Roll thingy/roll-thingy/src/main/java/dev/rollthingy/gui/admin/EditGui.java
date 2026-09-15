package dev.rollthingy.gui.admin;

import dev.rollthingy.RollServices;
import dev.rollthingy.core.box.Box;
import dev.rollthingy.core.box.PaymentRequirement;
import dev.rollthingy.core.box.Penalty;
import dev.rollthingy.core.box.Zonk;
import dev.rollthingy.core.gui.ChestGui;
import dev.rollthingy.core.msg.SoundRegistry;
import dev.rollthingy.listener.ChatPrompt;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EditGui {
    private final RollServices services;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public EditGui(RollServices services) {
        this.services = services;
    }

    public void open(Player admin, Box box) {
        ChestGui gui = new ChestGui(6, "<gold>Edit " + box.name());
        gui.fill(services.config().fillerItem());
        gui.fillRect(9, 44, null);

        gui.on(0, clk -> new AdminGui(services).open(clk.player(), 0))
                .set(0, button(Material.SPECTRAL_ARROW, "<gray>Back"));

        ItemStack icon = services.cache().icon(box);
        icon.editMeta(meta -> meta.lore(List.of(MM.deserialize("<gray>" + box.id()))));
        gui.set(4, icon);

        gui.on(45, clk -> new PaymentGui(services).open(clk.player(), box))
                .set(45, button(Material.SHULKER_BOX, services.messages().get("admin.payment"),
                        paymentLore(box)));
        gui.on(46, clk -> new RarityGui(services).open(clk.player(), box, Integer.MAX_VALUE))
                .set(46, button(Material.DIAMOND, services.messages().get("admin.rarities"),
                        List.of(MM.deserialize("<gray>" + box.tiers().size() + " tiers"))));
        gui.on(47, clk -> rename(admin, box))
                .set(47, button(Material.NAME_TAG, services.messages().get("admin.rename"),
                        List.of(MM.deserialize("<gray>Current: <white>" + box.name()))));
        gui.on(48, clk -> new IconPickerGui(services).open(clk.player(), box))
                .set(48, button(Material.ITEM_FRAME, services.messages().get("admin.icon"),
                        List.of(MM.deserialize("<gray>Current: <white>" + iconMaterial(box)))));
        gui.on(49, clk -> penalty(admin, box))
                .set(49, button(Material.BLAZE_POWDER, services.messages().get("admin.penalty"),
                        penaltyLore(box)));
        gui.on(50, clk -> cooldown(admin, box))
                .set(50, button(Material.CLOCK, services.messages().get("admin.cooldown"),
                        List.of(MM.deserialize("<gray>" + box.cooldownSeconds() + " seconds"))));
        gui.on(51, clk -> toggleZonk(clk.player(), box))
                .set(51, button(Material.BARRIER, services.messages().get("admin.zonk"),
                        zonkLore(box)));
        gui.on(53, clk -> delete(clk.player(), box))
                .set(53, button(Material.LAVA_BUCKET, services.messages().get("admin.delete")));

        services.sounds().play(admin, SoundRegistry.Event.OPEN);
        gui.open(admin);
    }

    private static String iconMaterial(Box box) {
        if (box.icon() != null && box.icon().material() != null && !box.icon().material().isBlank()) {
            return box.icon().material().toLowerCase().replace('_', ' ');
        }
        return "chest";
    }

    private List<Component> paymentLore(Box box) {
        List<Component> lore = new ArrayList<>();
        lore.add(MM.deserialize(services.messages().get("admin.payment-hint")));
        if (box.payment().isEmpty()) {
            lore.add(MM.deserialize(services.messages().get("admin.payment-none")));
            return lore;
        }
        for (PaymentRequirement req : box.payment()) {
            String material = services.cache().materialOf(req.data());
            String mode = req.strict() ? "exact" : "any type";
            lore.add(MM.deserialize("<gray>" + req.amount() + "x <white>" + material + " <dark_gray>(" + mode + ")"));
        }
        return lore;
    }

    private List<Component> penaltyLore(Box box) {
        Penalty p = box.penalty();
        return List.of(MM.deserialize(services.messages().get("admin.penalty-current",
                Map.of("loose", fmt(p.looseValue()), "rare", fmt(p.rareCut()), "feed", fmt(p.zonkFeed())))));
    }

    private List<Component> zonkLore(Box box) {
        boolean on = box.zonk() != null && box.zonk().enabled();
        double base = box.zonk() != null ? box.zonk().baseChance() : 0.0;
        String status = on ? "<green>enabled" : "<red>disabled";
        return List.of(MM.deserialize(status + "<gray> · base " + fmt(base) + "%"));
    }

    private static String fmt(double value) {
        if (value == Math.floor(value)) return String.valueOf((long) value);
        return String.valueOf(value);
    }

    private void toggleZonk(Player admin, Box box) {
        Zonk current = box.zonk() != null ? box.zonk() : new Zonk(false, 5.0);
        Zonk next = new Zonk(!current.enabled(), current.baseChance());
        Box updated = new Box(box.id(), box.name(), box.icon(), box.payment(), box.penalty(),
                box.cooldownSeconds(), next, box.tiers());
        services.cache().addOrUpdate(updated);
        admin.sendMessage(MM.deserialize(services.messages().get("admin.saved")));
        admin.closeInventory();
        new EditGui(services).open(admin, updated);
    }

    private void rename(Player admin, Box box) {
        admin.sendMessage(MM.deserialize(services.messages().get("admin.rename-prompt", Map.of("name", box.name()))));
        services.sounds().play(admin, SoundRegistry.Event.CLICK);
        ChatPrompt.prompt(admin, name -> {
            String clean = name.trim();
            if (clean.isEmpty()) {
                admin.sendMessage(MM.deserialize(services.messages().get("errors.bad-input",
                        Map.of("example", "\"my-box\""))));
                return;
            }
            Box updated = new Box(box.id(), clean, box.icon(), box.payment(), box.penalty(),
                    box.cooldownSeconds(), box.zonk(), box.tiers());
            services.cache().addOrUpdate(updated);
            admin.sendMessage(MM.deserialize(services.messages().get("admin.saved")));
            admin.closeInventory();
            new EditGui(services).open(admin, updated);
        });
    }

    private void penalty(Player admin, Box box) {
        Penalty p = box.penalty();
        admin.sendMessage(MM.deserialize(services.messages().get("admin.penalty-prompt",
                Map.of("loose", fmt(p.looseValue()), "rare", fmt(p.rareCut()), "feed", fmt(p.zonkFeed())))));
        services.sounds().play(admin, SoundRegistry.Event.CLICK);
        ChatPrompt.prompt(admin, input -> {
            String[] parts = input.trim().split("\\s+");
            if (parts.length != 3) {
                admin.sendMessage(MM.deserialize(services.messages().get("errors.bad-input",
                        Map.of("example", "0.7 30 15"))));
                return;
            }
            try {
                double loose = Double.parseDouble(parts[0]);
                double rare = Double.parseDouble(parts[1]);
                double feed = Double.parseDouble(parts[2]);
                Box updated = new Box(box.id(), box.name(), box.icon(), box.payment(),
                        new Penalty(loose, rare, feed), box.cooldownSeconds(), box.zonk(), box.tiers());
                services.cache().addOrUpdate(updated);
                admin.sendMessage(MM.deserialize(services.messages().get("admin.saved")));
                admin.closeInventory();
                new EditGui(services).open(admin, updated);
            } catch (NumberFormatException e) {
                admin.sendMessage(MM.deserialize(services.messages().get("errors.bad-input",
                        Map.of("example", "0.7 30 15"))));
            }
        });
    }

    private void cooldown(Player admin, Box box) {
        admin.sendMessage(MM.deserialize(services.messages().get("admin.cooldown-prompt",
                Map.of("seconds", String.valueOf(box.cooldownSeconds())))));
        services.sounds().play(admin, SoundRegistry.Event.CLICK);
        ChatPrompt.prompt(admin, input -> {
            try {
                long secs = Long.parseLong(input.trim());
                Box updated = new Box(box.id(), box.name(), box.icon(), box.payment(), box.penalty(),
                        secs, box.zonk(), box.tiers());
                services.cache().addOrUpdate(updated);
                admin.sendMessage(MM.deserialize(services.messages().get("admin.saved")));
                admin.closeInventory();
                new EditGui(services).open(admin, updated);
            } catch (NumberFormatException e) {
                admin.sendMessage(MM.deserialize(services.messages().get("errors.bad-input",
                        Map.of("example", "30"))));
            }
        });
    }

    private void delete(Player admin, Box box) {
        new dev.rollthingy.gui.admin.ConfirmGui(services).open(admin, services.messages().get("admin.delete"), () -> {
            services.cache().remove(box.id());
            if (admin.isOnline()) {
                admin.sendMessage(MM.deserialize(services.messages().get("admin.deleted")));
                admin.closeInventory();
                new AdminGui(services).open(admin, 0);
            }
        });
    }

    private ItemStack button(Material material, String text) {
        return button(material, text, List.of());
    }

    private ItemStack button(Material material, String text, List<Component> lore) {
        ItemStack item = new ItemStack(material, 1);
        item.editMeta(meta -> {
            meta.displayName(MM.deserialize(text));
            meta.lore(lore);
        });
        return item;
    }
}