package dev.rollthingy.gui;

import dev.rollthingy.RollService;
import dev.rollthingy.RollServices;
import dev.rollthingy.core.box.Box;
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

/** Display-only summary of a multi-spin: every prize (or Zonk) that was just awarded. */
public class MultiResultGui {
    private final RollServices services;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int SHOWN = 14;

    public MultiResultGui(RollServices services) {
        this.services = services;
    }

    public void open(Player player, Box box, List<RollService.SpinResult> results, List<Boolean> stored) {
        int count = results.size();
        ChestGui gui = new ChestGui(3, services.messages().get("spin.multi-result-title",
                Map.of("count", String.valueOf(count))));
        gui.fill(services.config().fillerItem());

        int shown = Math.min(count, SHOWN);
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        for (int i = 0; i < shown; i++) {
            RollService.SpinResult result = results.get(i);
            boolean zonk = result.outcome().tierIndex() == -1;
            ItemStack icon = zonk
                    ? new ItemStack(Material.GRAY_DYE, 1)
                    : result.winnerItem().clone();
            List<Component> lore = new ArrayList<>();
            lore.add(MM.deserialize(zonk
                    ? services.messages().get("spin.zonk")
                    : services.messages().get("spin.win",
                            Map.of("item", displayName(result.winnerItem())))));
            if (stored.get(i)) lore.add(MM.deserialize(services.messages().get("claim.stored")));
            icon.editMeta(meta -> meta.lore(lore));
            gui.set(slots[i], icon);
        }
        if (count > shown) {
            gui.set(26, button(Material.PAPER, services.messages().get("detail.payment-more",
                    Map.of("count", String.valueOf(count - shown)))));
        }

        gui.on(0, clk -> { clk.player().closeInventory(); new BoxDetailGui(services).open(clk.player(), box); })
                .set(0, button(Material.SPECTRAL_ARROW, "<gray>Back"));

        boolean anyRare = results.stream().anyMatch(r -> r.outcome().tierIndex() != -1 && r.chancePct() < 0.5);
        boolean anyWin = results.stream().anyMatch(r -> r.outcome().tierIndex() != -1);
        services.sounds().play(player, anyRare ? SoundRegistry.Event.WIN_RARE
                : anyWin ? SoundRegistry.Event.WIN : SoundRegistry.Event.ZONK);
        gui.open(player);
    }

    private String displayName(ItemStack item) {
        if (item == null || item.getType().isAir()) return "?";
        if (item.getItemMeta() != null && item.getItemMeta().displayName() != null) {
            return MiniMessage.miniMessage().serialize(item.getItemMeta().displayName());
        }
        return item.getType().name().toLowerCase().replace('_', ' ');
    }

    private ItemStack button(Material material, String text) {
        ItemStack item = new ItemStack(material, 1);
        item.editMeta(meta -> meta.displayName(MM.deserialize(text)));
        return item;
    }
}
