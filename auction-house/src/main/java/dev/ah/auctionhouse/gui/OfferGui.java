package dev.ah.auctionhouse.gui;

import dev.ah.auctionhouse.AhActions;
import dev.ah.auctionhouse.AhServices;
import dev.ah.core.gui.DepositGui;
import dev.ah.core.listing.Listing;
import dev.ah.core.misc.ItemBundleCodec;
import dev.ah.core.msg.SoundRegistry;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.List;

public class OfferGui {
    private final AhServices services;
    private final long listingId;
    private final Listing listing;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public OfferGui(AhServices services, long listingId) {
        this.services = services;
        this.listingId = listingId;
        this.listing = services.listings().byId(listingId).orElse(null);
    }

    public void open(Player player) {
        if (listing == null || !listing.isActive()) {
            player.sendMessage(MM.deserialize(services.messages().get("checks.listing-expired")));
            return;
        }
        DepositGui gui = new DepositGui(5, services.messages().get("offer.title"), 9, 17);
        gui.fill(services.fillerItem());
        gui.fillRect(9, 17, null);
        gui.on(0, clk -> { gui.cancelAndReturn(player); new AhMainGui(services).open(player); })
                .set(0, GuiItems.button(services, Material.SPECTRAL_ARROW, "gui.buttons.back"));
        List<ItemStack> preview = ItemBundleCodec.decode(listing.itemData());
        if (!preview.isEmpty()) gui.set(4, IconUtil.clean(preview.get(0)));
        gui.on(39, () -> onConfirm(player, gui)).set(39, GuiItems.button(services, Material.LIME_DYE, "gui.buttons.confirm"));
        gui.on(41, () -> gui.cancelAndReturn(player)).set(41, GuiItems.button(services, Material.BARRIER, "gui.buttons.cancel"));
        services.gui().registerOpen(player, gui);
        services.sounds().play(player, SoundRegistry.Event.OPEN);
        gui.open(player);
    }

    private void onConfirm(Player player, DepositGui gui) {
        if (gui.isConfirmed()) return;
        List<ItemStack> items = gui.collect();
        AhActions.OfferResult r = new AhActions(services).makeOffer(player, listingId, items);
        if (r == AhActions.OfferResult.SUCCESS) {
            player.sendMessage(MM.deserialize(services.messages().get("offers.made")));
            services.sounds().play(player, SoundRegistry.Event.CLICK);
            gui.markConfirmed();
            player.closeInventory();
        } else {
            String key = switch (r) {
                case NOT_ACTIVE -> "checks.listing-expired";
                case SELF_OFFER -> "checks.cannot-offer-self";
                case TOO_MANY_ITEMS -> "checks.max-offer-items";
                case TOO_MANY_OFFERS -> "checks.max-offers";
                case NO_ITEMS -> "checks.invalid-items";
                default -> "errors.unknown";
            };
            player.sendMessage(MM.deserialize(services.messages().get(key)));
            services.sounds().play(player, SoundRegistry.Event.ERROR);
            gui.cancelAndReturn(player);
        }
    }
}