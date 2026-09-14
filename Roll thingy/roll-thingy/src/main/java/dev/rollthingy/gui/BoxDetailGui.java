package dev.rollthingy.gui;

import dev.rollthingy.RollServices;
import dev.rollthingy.core.box.Box;
import dev.rollthingy.core.box.PaymentRequirement;
import dev.rollthingy.core.gui.ChestGui;
import dev.rollthingy.core.msg.SoundRegistry;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BoxDetailGui {
    private final RollServices services;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public BoxDetailGui(RollServices services) {
        this.services = services;
    }

    public void open(Player player, Box box) {
        long cooldown = services.roll().cooldownMillis(player, box);
        boolean onCooldown = cooldown > 0;

        ChestGui gui = new ChestGui(6, services.messages().get("detail.title", Map.of("name", box.name())));
        gui.fill(services.config().fillerItem());
        gui.fillRect(9, 44, null);

        ItemStack icon = services.cache().icon(box);
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        for (PaymentRequirement req : box.payment()) {
            String material = services.cache().materialOf(req.data());
            String amount = String.valueOf(req.amount());
            String mode = req.strict() ? "<gold>exact" : "<gold>type-only";
            lore.add(MM.deserialize(services.messages().get("detail.payment")
                    + " " + material + " x" + amount + " <gray>(" + mode + ")"));
        }
        if (!box.tiers().isEmpty()) {
            lore.add(MM.deserialize("<gray>" + box.tiers().size() + " rarity tiers"));
        }
        icon.editMeta(meta -> meta.lore(lore));
        gui.set(4, icon);

        // Check items button
        gui.on(47, clk -> new PreviewGui(services).open(clk.player(), box, 0)).set(47,
                button(Material.BOOK, services.messages().get("detail.check-items")));

        // Spin button (disabled-looking when on cooldown)
        if (onCooldown) {
            ItemStack disabled = new ItemStack(Material.BARRIER, 1);
            long seconds = Math.max(1, cooldown / 1000);
            disabled.editMeta(meta -> {
                meta.displayName(MM.deserialize(services.messages().get("spin.on-cooldown",
                        Map.of("seconds", String.valueOf(seconds)))));
            });
            gui.set(49, disabled);
        } else {
            gui.on(49, clk -> new SpinGui(services).open(clk.player(), box)).set(49,
                    button(Material.EMERALD, services.messages().get("detail.open")));
        }

        // Back
        gui.on(0, clk -> { clk.player().closeInventory(); new MainGui(services).open(clk.player(), 0); })
                .set(0, button(Material.ARROW, "<gray>Back"));

        services.sounds().play(player, SoundRegistry.Event.OPEN);
        gui.open(player);
    }

    private ItemStack button(Material material, String text) {
        ItemStack item = new ItemStack(material, 1);
        item.editMeta(meta -> meta.displayName(MM.deserialize(text)));
        return item;
    }
}