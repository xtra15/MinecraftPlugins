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
        DepositGui gui = new DepositGui(4, services.messages().get("sell.title"), 9, 17);
        gui.fill(new ItemStack(Material.valueOf(services.getGuiFiller()), 1));
        gui.fillRect(9, 17, null);
        gui.set(0, new ItemStack(Material.NAME_TAG, 1));
        gui.on(39, () -> onConfirm(player, gui)).set(39, confirmItem("CONFIRM"));
        gui.on(41, () -> gui.cancelAndReturn(player)).set(41, cancelItem("CANCEL"));
        services.gui().registerOpen(player, gui);
        services.sounds().play(player, SoundRegistry.Event.OPEN);
        gui.open(player);
    }

    private void onConfirm(Player player, DepositGui gui) {
        List<ItemStack> items = gui.collect();
        long duration = services.defaultDurationMs();
        AhActions.CreateResult r = new AhActions(services).createListing(player, items, duration);
        if (r == AhActions.CreateResult.SUCCESS) {
            player.sendMessage(MM.deserialize(services.messages().get("listings.created")));
            services.sounds().play(player, SoundRegistry.Event.SALE);
            player.closeInventory();
        } else {
            player.sendMessage(MM.deserialize(services.messages().get(
                    r == AhActions.CreateResult.LIMIT_REACHED ? "checks.max-listings" : "checks.invalid-items")));
            services.sounds().play(player, SoundRegistry.Event.ERROR);
            gui.cancelAndReturn(player);
        }
    }

    private ItemStack confirmItem(String label) {
        ItemStack i = new ItemStack(Material.LIME_DYE, 1);
        i.editMeta(m -> m.displayName(MM.deserialize("<green>" + label)));
        return i;
    }

    private ItemStack cancelItem(String label) {
        ItemStack i = new ItemStack(Material.BARRIER, 1);
        i.editMeta(m -> m.displayName(MM.deserialize("<red>" + label)));
        return i;
    }
}