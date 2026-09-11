package dev.ah.auctionhouse.gui;

import dev.ah.auctionhouse.AhActions;
import dev.ah.auctionhouse.AhServices;
import dev.ah.core.gui.DepositGui;
import dev.ah.core.msg.SoundRegistry;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.List;

public class SellGui {
    private final AhServices services;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public SellGui(AhServices services) {
        this.services = services;
    }

    public void open(Player player) {
        DepositGui gui = new DepositGui(5, services.messages().get("sell.title"), 9, 17);
        gui.fill(services.fillerItem());
        gui.fillRect(9, 17, null);
        gui.on(0, clk -> { gui.cancelAndReturn(player); new AhMainGui(services).open(player); })
                .set(0, GuiItems.button(services, Material.SPECTRAL_ARROW, "gui.buttons.back"));
        gui.set(4, GuiItems.button(services, Material.NAME_TAG, "sell.preview"));
        gui.on(39, () -> onConfirm(player, gui)).set(39, GuiItems.button(services, Material.LIME_DYE, "gui.buttons.confirm"));
        gui.on(41, () -> gui.cancelAndReturn(player)).set(41, GuiItems.button(services, Material.BARRIER, "gui.buttons.cancel"));
        services.gui().registerOpen(player, gui);
        services.sounds().play(player, SoundRegistry.Event.OPEN);
        gui.open(player);
    }

    private void onConfirm(Player player, DepositGui gui) {
        if (gui.isConfirmed()) return;
        List<ItemStack> items = gui.collect();
        long duration = services.defaultDurationMs();
        AhActions.CreateResult r = new AhActions(services).createListing(player, items, duration);
        if (r == AhActions.CreateResult.SUCCESS) {
            player.sendMessage(MM.deserialize(services.messages().get("listings.created")));
            services.sounds().play(player, SoundRegistry.Event.SALE);
            gui.markConfirmed();
            player.closeInventory();
        } else {
            player.sendMessage(MM.deserialize(services.messages().get(
                    r == AhActions.CreateResult.LIMIT_REACHED ? "checks.max-listings" : "checks.invalid-items")));
            services.sounds().play(player, SoundRegistry.Event.ERROR);
            gui.cancelAndReturn(player);
        }
    }
}