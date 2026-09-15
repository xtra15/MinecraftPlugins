package dev.rollthingy.gui.admin;

import dev.rollthingy.RollServices;
import dev.rollthingy.core.box.Box;
import dev.rollthingy.core.box.BoxItem;
import dev.rollthingy.core.box.RarityTier;
import dev.rollthingy.core.gui.ChestGui;
import dev.rollthingy.core.gui.DepositGui;
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

    /** Item-drop editor for a single tier. Slots 9-17 hold candidate items; 40 confirms them. */
    public void openDrop(Player admin, Box box, int tierIndex) {
        StemDrop gui = new StemDrop(services, box, tierIndex);
        services.gui().registerOpen(admin, gui);
        services.sounds().play(admin, SoundRegistry.Event.OPEN);
        gui.open(admin);
    }

    private static final class StemDrop extends DepositGui {
        private final RollServices services;
        private final Box box;
        private final int tierIndex;

        StemDrop(RollServices services, Box box, int tierIndex) {
            super(6, services.messages().get("admin.rarity.add"), 9, 17);
            this.services = services;
            this.box = box;
            this.tierIndex = tierIndex;
            draw();
        }

        private void draw() {
            fill(services.config().fillerItem());
            fillRect(9, 17, null);
            fillRect(45, 53, new ItemStack(Material.GRAY_STAINED_GLASS_PANE, 1));
            set(8, bracket());
            set(18, bracket());
            set(4, howTo());
            on(0, clk -> new RarityGui(services).open(clk.player(), box, Integer.MAX_VALUE))
                    .set(0, button(Material.SPECTRAL_ARROW, "<gray>Back"));
            on(53, clk -> collectDrop(clk.player())).set(53, button(Material.EMERALD, services.messages().get("admin.confirm")));
            drawCurrent();
        }

        private void drawCurrent() {
            if (tierIndex >= box.tiers().size()) return;
            RarityTier tier = box.tiers().get(tierIndex);
            int slot = 19;
            for (int i = 0; i < tier.items().size() && slot <= 26; i++) {
                BoxItem item = tier.items().get(i);
                ItemStack icon = services.cache().item(item.data());
                icon.editMeta(meta -> meta.lore(List.of(MM.deserialize("weight <white>" + item.weight()))));
                set(slot, icon);
                slot++;
            }
        }

        private ItemStack howTo() {
            ItemStack how = new ItemStack(Material.BOOK, 1);
            how.editMeta(meta -> {
                meta.displayName(MM.deserialize(services.messages().get("drop.banner")));
                meta.lore(List.of(
                        MM.deserialize(services.messages().get("drop.step-price")),
                        MM.deserialize(services.messages().get("drop.step-confirm"))));
            });
            return how;
        }

        private ItemStack bracket() {
            ItemStack bracket = new ItemStack(Material.LIME_STAINED_GLASS_PANE, 1);
            bracket.editMeta(meta -> meta.displayName(MM.deserialize(services.messages().get("drop.zone-bracket"))));
            return bracket;
        }

        private void collectDrop(Player admin) {
            if (tierIndex >= box.tiers().size()) return;
            RarityTier tier = box.tiers().get(tierIndex);
            List<BoxItem> items = new ArrayList<>(tier.items());
            boolean added = false;
            for (int i = 9; i <= 17; i++) {
                ItemStack item = inventory().getItem(i);
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
            markConfirmed();
            clearDrop();
            admin.sendMessage(MM.deserialize(services.messages().get("admin.saved")));
            new RarityGui(services).open(admin, updated, Integer.MAX_VALUE);
        }

        private void clearDrop() {
            if (inventory() == null) return;
            for (int i = 9; i <= 17; i++) inventory().setItem(i, null);
        }

        private ItemStack button(Material material, String text) {
            ItemStack item = new ItemStack(material, 1);
            item.editMeta(meta -> meta.displayName(MM.deserialize(text)));
            return item;
        }
    }

    public void open(Player admin, Box box, int tierIndex) {
        if (tierIndex >= box.tiers().size()) return;
        RarityTier tier = box.tiers().get(tierIndex);
        ChestGui gui = new ChestGui(6, services.messages().get("admin.rarity.weight"));
        gui.fill(services.config().fillerItem());
        gui.fillRect(9, 44, null);
        gui.on(0, clk -> new RarityGui(services).open(clk.player(), box, Integer.MAX_VALUE))
                .set(0, button(Material.SPECTRAL_ARROW, "<gray>Back"));
        gui.set(4, howTo());

        int slot = 9;
        for (int i = 0; i < tier.items().size() && slot <= 44; i++) {
            BoxItem item = tier.items().get(i);
            ItemStack icon = services.cache().item(item.data());
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

    private ItemStack howTo() {
        ItemStack how = new ItemStack(Material.BOOK, 1);
        how.editMeta(meta -> {
            meta.displayName(MM.deserialize(services.messages().get("drop.banner")));
            meta.lore(List.of(
                    MM.deserialize(services.messages().get("drop.step-price")),
                    MM.deserialize(services.messages().get("drop.step-confirm"))));
        });
        return how;
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
        RarityTier tier = box.tiers().get(tierIndex);
        admin.sendMessage(MM.deserialize(services.messages().get("admin.weight-prompt",
                Map.of("weight", fmt(tier.weight())))));
        ChatPrompt.prompt(admin, input -> {
            try {
                double w = Double.parseDouble(input.trim());
                RarityTier current = box.tiers().get(tierIndex);
                List<RarityTier> tiers = new ArrayList<>(box.tiers());
                tiers.set(tierIndex, new RarityTier(current.name(), w, current.items()));
                Box updated = new Box(box.id(), box.name(), box.icon(), box.payment(), box.penalty(),
                        box.cooldownSeconds(), box.zonk(), tiers);
                services.cache().addOrUpdate(updated);
                admin.sendMessage(MM.deserialize(services.messages().get("admin.saved")));
                open(admin, updated, tierIndex);
            } catch (NumberFormatException e) {
                admin.sendMessage(MM.deserialize(services.messages().get("errors.bad-input",
                        Map.of("example", "2.5"))));
            }
        });
    }

    private void setItemWeight(Player admin, Box box, int tierIndex, int itemIndex) {
        BoxItem old = box.tiers().get(tierIndex).items().get(itemIndex);
        admin.sendMessage(MM.deserialize(services.messages().get("admin.item-weight-prompt",
                Map.of("weight", fmt(old.weight())))));
        ChatPrompt.prompt(admin, input -> {
            try {
                double w = Double.parseDouble(input.trim());
                RarityTier tier = box.tiers().get(tierIndex);
                List<BoxItem> items = new ArrayList<>(tier.items());
                BoxItem current = items.get(itemIndex);
                items.set(itemIndex, new BoxItem(current.data(), w));
                List<RarityTier> tiers = new ArrayList<>(box.tiers());
                tiers.set(tierIndex, new RarityTier(tier.name(), tier.weight(), items));
                Box updated = new Box(box.id(), box.name(), box.icon(), box.payment(), box.penalty(),
                        box.cooldownSeconds(), box.zonk(), tiers);
                services.cache().addOrUpdate(updated);
                admin.sendMessage(MM.deserialize(services.messages().get("admin.saved")));
                open(admin, updated, tierIndex);
            } catch (NumberFormatException e) {
                admin.sendMessage(MM.deserialize(services.messages().get("errors.bad-input",
                        Map.of("example", "2.5"))));
            }
        });
    }

    private static String fmt(double value) {
        if (value == Math.floor(value)) return String.valueOf((long) value);
        return String.valueOf(value);
    }

    private ItemStack button(Material material, String text) {
        ItemStack item = new ItemStack(material, 1);
        item.editMeta(meta -> meta.displayName(MM.deserialize(text)));
        return item;
    }
}