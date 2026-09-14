package dev.rollthingy.gui.admin;

import dev.rollthingy.RollServices;
import dev.rollthingy.core.box.Box;
import dev.rollthingy.core.box.BoxItem;
import dev.rollthingy.core.box.RarityTier;
import dev.rollthingy.core.gui.ChestGui;
import dev.rollthingy.core.misc.ItemBundleCodec;
import dev.rollthingy.core.msg.SoundRegistry;
import dev.rollthingy.listener.ChatPrompt;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TierEditGui {
    private final RollServices services;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public TierEditGui(RollServices services) {
        this.services = services;
    }

    /** Item-drop editor for a single tier. Slots 9-17 hold candidate items; 44 confirms them. */
    public void openDrop(Player admin, Box box, int tierIndex) {
        ChestGui gui = new ChestGui(6, services.messages().get("admin.rarity.add"));
        gui.fill(services.config().fillerItem());
        gui.fillRect(9, 17, null);
        gui.fillRect(45, 53, new ItemStack(Material.GRAY_STAINED_GLASS_PANE, 1));
        gui.on(0, clk -> new RarityGui(services).open(clk.player(), box, Integer.MAX_VALUE))
                .set(0, button(Material.SPECTRAL_ARROW, "<gray>Back"));
        gui.on(40, clk -> collectDrop(clk.player(), box, tierIndex))
                .set(40, button(Material.EMERALD, services.messages().get("admin.confirm")));
        services.sounds().play(admin, SoundRegistry.Event.OPEN);
        gui.open(admin);
    }

    private void collectDrop(Player admin, Box box, int tierIndex) {
        if (tierIndex >= box.tiers().size()) return;
        RarityTier tier = box.tiers().get(tierIndex);
        List<BoxItem> items = new ArrayList<>(tier.items());
        boolean added = false;
        for (int i = 9; i <= 17; i++) {
            ItemStack item = admin.getOpenInventory().getTopInventory().getItem(i);
            if (item == null || item.getType().isAir()) continue;
            String data = ItemBundleCodec.encode(List.of(item.clone()));
            items.add(new BoxItem(data, 1.0));
            added = true;
        }
        if (!added) {
            admin.sendMessage(MM.deserialize(services.messages().get("errors.unknown")));
            services.sounds().play(admin, SoundRegistry.Event.ERROR);
            return;
        }
        List<RarityTier> tiers = new ArrayList<>(box.tiers());
        tiers.set(tierIndex, new RarityTier(tier.name(), tier.weight(), items));
        Box updated = new Box(box.id(), box.name(), box.icon(), box.payment(), box.penalty(),
                box.cooldownSeconds(), box.zonk(), tiers);
        services.cache().addOrUpdate(updated);
        admin.sendMessage(MM.deserialize(services.messages().get("admin.saved")));
        new RarityGui(services).open(admin, updated, Integer.MAX_VALUE);
    }

    public void open(Player admin, Box box, int tierIndex) {
        if (tierIndex >= box.tiers().size()) return;
        RarityTier tier = box.tiers().get(tierIndex);
        ChestGui gui = new ChestGui(6, services.messages().get("admin.rarity.weight"));
        gui.fill(services.config().fillerItem());
        gui.fillRect(9, 44, null);
        gui.on(0, clk -> new RarityGui(services).open(clk.player(), box, Integer.MAX_VALUE))
                .set(0, button(Material.SPECTRAL_ARROW, "<gray>Back"));

        int slot = 9;
        for (int i = 0; i < tier.items().size() && slot <= 44; i++) {
            BoxItem item = tier.items().get(i);
            ItemStack icon;
            try {
                List<ItemStack> decoded = ItemBundleCodec.decode(item.data());
                icon = decoded.isEmpty() ? new ItemStack(Material.BARRIER) : decoded.get(0).clone();
            } catch (IllegalArgumentException e) {
                icon = new ItemStack(Material.BARRIER);
            }
            final int itemIndex = i;
            icon.editMeta(meta -> meta.lore(List.of(MM.deserialize("weight <white>" + item.weight()))));
            gui.on(slot, clk -> setItemWeight(admin, box, tierIndex, itemIndex));
            gui.set(slot, icon);
            slot++;
        }

        gui.on(45, clk -> setTierWeight(admin, box, tierIndex)).set(45, button(Material.NAME_TAG,
                services.messages().get("admin.rarity.weight")));
        gui.on(47, clk -> openDrop(clk.player(), box, tierIndex)).set(47, button(Material.HOPPER,
                services.messages().get("admin.rarity.add")));
        gui.on(49, clk -> deleteItem(admin, box, tierIndex)).set(49, button(Material.BARRIER,
                services.messages().get("admin.delete")));
        services.sounds().play(admin, SoundRegistry.Event.OPEN);
        gui.open(admin);
    }

    private void deleteItem(Player admin, Box box, int tierIndex) {
        new ConfirmGui(services).open(admin, services.messages().get("admin.delete"), () -> {
            RarityTier tier = box.tiers().get(tierIndex);
            List<RarityTier> tiers = new ArrayList<>(box.tiers());
            tiers.remove(tierIndex);
            Box updated = new Box(box.id(), box.name(), box.icon(), box.payment(), box.penalty(),
                    box.cooldownSeconds(), box.zonk(), tiers);
            services.cache().addOrUpdate(updated);
            admin.sendMessage(MM.deserialize(services.messages().get("admin.deleted")));
            open(admin, updated, 0);
        });
    }

    private void setTierWeight(Player admin, Box box, int tierIndex) {
        admin.sendMessage(MM.deserialize(services.messages().get("admin.rarity.weight")));
        ChatPrompt.prompt(admin, input -> {
            try {
                double w = Double.parseDouble(input.trim());
                RarityTier tier = box.tiers().get(tierIndex);
                List<RarityTier> tiers = new ArrayList<>(box.tiers());
                tiers.set(tierIndex, new RarityTier(tier.name(), w, tier.items()));
                Box updated = new Box(box.id(), box.name(), box.icon(), box.payment(), box.penalty(),
                        box.cooldownSeconds(), box.zonk(), tiers);
                services.cache().addOrUpdate(updated);
                admin.sendMessage(MM.deserialize(services.messages().get("admin.saved")));
                open(admin, updated, tierIndex);
            } catch (NumberFormatException e) {
                admin.sendMessage(MM.deserialize(services.messages().get("errors.unknown")));
            }
        });
    }

    private void setItemWeight(Player admin, Box box, int tierIndex, int itemIndex) {
        admin.sendMessage(MM.deserialize(services.messages().get("admin.rarity.item-weight")));
        ChatPrompt.prompt(admin, input -> {
            try {
                double w = Double.parseDouble(input.trim());
                RarityTier tier = box.tiers().get(tierIndex);
                List<BoxItem> items = new ArrayList<>(tier.items());
                BoxItem old = items.get(itemIndex);
                items.set(itemIndex, new BoxItem(old.data(), w));
                List<RarityTier> tiers = new ArrayList<>(box.tiers());
                tiers.set(tierIndex, new RarityTier(tier.name(), tier.weight(), items));
                Box updated = new Box(box.id(), box.name(), box.icon(), box.payment(), box.penalty(),
                        box.cooldownSeconds(), box.zonk(), tiers);
                services.cache().addOrUpdate(updated);
                admin.sendMessage(MM.deserialize(services.messages().get("admin.saved")));
                open(admin, updated, tierIndex);
            } catch (NumberFormatException e) {
                admin.sendMessage(MM.deserialize(services.messages().get("errors.unknown")));
            }
        });
    }

    private ItemStack button(Material material, String text) {
        ItemStack item = new ItemStack(material, 1);
        item.editMeta(meta -> meta.displayName(MM.deserialize(text)));
        return item;
    }
}