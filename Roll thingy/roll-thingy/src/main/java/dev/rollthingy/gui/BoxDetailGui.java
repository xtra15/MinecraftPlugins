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

        // Box name centerpiece, sits above the (centered) payment panel
        ItemStack icon = services.cache().icon(box);
        List<Component> iconLore = new ArrayList<>();
        List<PaymentRequirement> payment = box.payment();
        int total = payment.stream().mapToInt(PaymentRequirement::amount).sum();
        iconLore.add(MM.deserialize(services.messages().get("detail.payment-title")));
        iconLore.add(MM.deserialize(services.messages().get("detail.payment-summary",
                Map.of("count", String.valueOf(total)))));
        iconLore.add(MM.deserialize(services.messages().get("detail.payment-hint")));
        if (!box.tiers().isEmpty()) {
            iconLore.add(MM.deserialize("<gray>" + box.tiers().size() + " rarity tiers"));
        }
        icon.editMeta(meta -> {
            meta.displayName(MM.deserialize("<gold>" + box.name()));
            meta.lore(iconLore);
        });
        gui.set(13, icon);

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

    /** Payment icons centred on row 3 (slots 18-26) so a single item sits mid-screen. */
    private void drawPayment(ChestGui gui, Box box) {
        List<PaymentRequirement> payment = box.payment();
        if (payment.isEmpty()) {
            gui.set(22, button(Material.PAPER, services.messages().get("detail.payment-none")));
            return;
        }
        int shown = Math.min(payment.size(), 7);
        int start = 18 + ((9 - shown) / 2);
        for (int i = 0; i < shown; i++) {
            PaymentRequirement req = payment.get(i);
            ItemStack reqIcon = services.cache().item(req.data());
            String label = req.strict()
                    ? services.messages().get("spin.required-exact", Map.of("count", String.valueOf(req.amount())))
                    : services.messages().get("spin.required-type", Map.of("count", String.valueOf(req.amount())));
            reqIcon.editMeta(meta -> meta.lore(List.of(MM.deserialize(label))));
            gui.set(start + i, reqIcon);
        }
        if (payment.size() > shown) {
            gui.set(start + shown, button(Material.PAPER, services.messages().get("detail.payment-more",
                    Map.of("count", String.valueOf(payment.size() - shown)))));
        }
        drawLuckPreview(gui, box, payment);
    }

    /** Shows the luck % the player gets if they pay the full requirement above the payment row. */
    private void drawLuckPreview(ChestGui gui, Box box, List<PaymentRequirement> payment) {
        if (payment.isEmpty()) return;
        List<ItemStack> required = new ArrayList<>();
        for (PaymentRequirement req : payment) {
            ItemStack item = services.cache().item(req.data()).clone();
            item.setAmount(req.amount());
            required.add(item);
        }
        int luck = services.roll().luckPercent(box, required);
        ItemStack badge = new ItemStack(Material.GOLD_NUGGET, 1);
        badge.editMeta(meta -> {
            meta.displayName(MM.deserialize(services.messages().get("detail.luck-preview-title")));
            meta.lore(List.of(MM.deserialize(services.messages().get("detail.luck-preview",
                    Map.of("luck", String.valueOf(luck))))));
        });
        gui.set(31, badge);
    }

    private ItemStack button(Material material, String text) {
        ItemStack item = new ItemStack(material, 1);
        item.editMeta(meta -> meta.displayName(MM.deserialize(text)));
        return item;
    }
}