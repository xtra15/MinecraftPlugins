package dev.rollthingy.gui;

import dev.rollthingy.RollServices;
import dev.rollthingy.core.box.Box;
import dev.rollthingy.core.box.PaymentRequirement;
import dev.rollthingy.core.gui.ChestGui;
import dev.rollthingy.core.msg.SoundRegistry;
import net.kyori.adventure.text.Component;
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
        List<Component> lore = new ArrayList<>();
        List<PaymentRequirement> payment = box.payment();
        int total = payment.stream().mapToInt(PaymentRequirement::amount).sum();
        lore.add(MM.deserialize(services.messages().get("detail.payment-summary",
                Map.of("count", String.valueOf(total)))));
        if (!box.tiers().isEmpty()) {
            lore.add(MM.deserialize("<gray>" + box.tiers().size() + " rarity tiers"));
        }
        icon.editMeta(meta -> meta.lore(lore));
        gui.set(4, icon);

        drawPayment(gui, box);

        // View prizes button
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

        // Help button
        HelpBook helpBook = new HelpBook(services);
        gui.on(50, clk -> helpBook.open(clk.player())).set(50, helpBook.icon());

        // Back
        gui.on(0, clk -> { clk.player().closeInventory(); new MainGui(services).open(clk.player(), 0); })
                .set(0, button(Material.ARROW, "<gray>Back"));

        services.sounds().play(player, SoundRegistry.Event.OPEN);
        gui.open(player);
    }

    /** Payment panel: header at 10, requirement icons at 11-17 (up to 7 plus a "more" note). */
    private void drawPayment(ChestGui gui, Box box) {
        ItemStack header = button(Material.GREEN_STAINED_GLASS_PANE, services.messages().get("detail.payment-title"),
                List.of(MM.deserialize(services.messages().get("detail.payment-hint"))));
        gui.set(10, header);

        List<PaymentRequirement> payment = box.payment();
        if (payment.isEmpty()) {
            gui.set(11, button(Material.PAPER, services.messages().get("detail.payment-none")));
            return;
        }
        int slot = 11;
        for (int i = 0; i < payment.size() && slot <= 17; i++, slot++) {
            PaymentRequirement req = payment.get(i);
            ItemStack icon = services.cache().item(req.data());
            String label = req.strict()
                    ? services.messages().get("spin.required-exact", Map.of("count", String.valueOf(req.amount())))
                    : services.messages().get("spin.required-type", Map.of("count", String.valueOf(req.amount())));
            icon.editMeta(meta -> meta.lore(List.of(MM.deserialize(label))));
            gui.set(slot, icon);
        }
        if (payment.size() > 7) {
            gui.set(17, button(Material.PAPER, services.messages().get("detail.payment-more",
                    Map.of("count", String.valueOf(payment.size() - 7)))));
        }
    }

    private ItemStack button(Material material, String text) {
        ItemStack item = new ItemStack(material, 1);
        item.editMeta(meta -> meta.displayName(MM.deserialize(text)));
        return item;
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