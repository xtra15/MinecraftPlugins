package dev.rollthingy.gui.admin;

import dev.rollthingy.RollServices;
import dev.rollthingy.core.box.Box;
import dev.rollthingy.core.box.RarityTier;
import dev.rollthingy.core.gui.ChestGui;
import dev.rollthingy.core.msg.SoundRegistry;
import dev.rollthingy.listener.ChatPrompt;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RarityGui {
    private final RollServices services;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public RarityGui(RollServices services) {
        this.services = services;
    }

    public void open(Player admin, Box box, int page) {
        List<RarityTier> tiers = box.tiers();
        int pageSize = 36;
        long pages = Math.max(1, (tiers.size() + pageSize - 1) / pageSize);
        int pageIndex = Math.max(0, Math.min(page, (int) pages - 1));
        int from = pageIndex * pageSize;
        int to = Math.min(tiers.size(), from + pageSize);

        ChestGui gui = new ChestGui(6, services.messages().get("admin.rarities"));
        gui.fill(services.config().fillerItem());
        gui.fillRect(9, 44, null);

        int slot = 9;
        for (int i = from; i < to && slot <= 44; i++) {
            RarityTier tier = tiers.get(i);
            ItemStack icon = new ItemStack(Material.NAME_TAG, 1);
            final int index = i;
            icon.editMeta(meta -> {
                meta.displayName(MM.deserialize("<gold>" + tier.name()));
                meta.lore(List.of(MM.deserialize(services.messages().get("admin.rarity.weight")
                        + " <white>" + tier.weight())));
            });
            gui.on(slot, clk -> new TierEditGui(services).open(clk.player(), box, index));
            gui.set(slot, icon);
            slot++;
        }

        gui.on(45, clk -> {
            services.sounds().play(clk.player(), SoundRegistry.Event.CLICK);
            open(clk.player(), box, pageIndex - 1);
        }).set(45, button(Material.ARROW, "<gray><<"));
        gui.on(53, clk -> {
            services.sounds().play(clk.player(), SoundRegistry.Event.CLICK);
            open(clk.player(), box, pageIndex + 1);
        }).set(53, button(Material.ARROW, ">>"));
        gui.on(49, clk -> addRarity(admin, box)).set(49, button(Material.EMERALD, services.messages().get("admin.rarity.add")));
        gui.on(0, clk -> new EditGui(services).open(clk.player(), box)).set(0, button(Material.SPECTRAL_ARROW, "<gray>Back"));
        services.sounds().play(admin, SoundRegistry.Event.OPEN);
        gui.open(admin);
    }

    private void addRarity(Player admin, Box box) {
        admin.sendMessage(MM.deserialize(services.messages().get("admin.name-prompt", Map.of("name", ""))));
        services.sounds().play(admin, SoundRegistry.Event.CLICK);
        ChatPrompt.prompt(admin, name -> {
            String clean = name.trim();
            if (clean.isEmpty()) return;
            List<RarityTier> tiers = new ArrayList<>(box.tiers());
            tiers.add(new RarityTier(clean, 1.0, List.of()));
            Box updated = new Box(box.id(), box.name(), box.icon(), box.payment(), box.penalty(),
                    box.cooldownSeconds(), box.zonk(), tiers);
            services.cache().addOrUpdate(updated);
            admin.sendMessage(MM.deserialize(services.messages().get("admin.saved")));
            String example = "2.5";
            admin.sendMessage(MM.deserialize(services.messages().get("admin.weight-prompt",
                    Map.of("weight", "1.0"))));
            ChatPrompt.prompt(admin, weight -> {
                try {
                    double w = Double.parseDouble(weight.trim());
                    List<RarityTier> after = new ArrayList<>(updated.tiers());
                    after.set(after.size() - 1, new RarityTier(clean, w, List.of()));
                    Box finalBox = new Box(box.id(), box.name(), box.icon(), box.payment(), box.penalty(),
                            box.cooldownSeconds(), box.zonk(), after);
                    services.cache().addOrUpdate(finalBox);
                    admin.sendMessage(MM.deserialize(services.messages().get("admin.saved")));
                    open(admin, finalBox, Integer.MAX_VALUE);
                } catch (NumberFormatException e) {
                    admin.sendMessage(MM.deserialize(services.messages().get("errors.bad-input",
                            Map.of("example", "2.5"))));
                }
            });
        });
    }

    private ItemStack button(Material material, String text) {
        ItemStack item = new ItemStack(material, 1);
        item.editMeta(meta -> meta.displayName(MM.deserialize(text)));
        return item;
    }
}