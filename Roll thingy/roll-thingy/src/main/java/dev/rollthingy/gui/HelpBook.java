package dev.rollthingy.gui;

import dev.rollthingy.RollServices;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.List;

/** A signed, readable help book pulled entirely from lang.yml. */
public class HelpBook {
    private final RollServices services;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public HelpBook(RollServices services) {
        this.services = services;
    }

    public void open(Player player) {
        player.openBook(book());
    }

    public ItemStack book() {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK, 1);
        book.editMeta(BookMeta.class, meta -> {
            meta.setTitle(services.messages().get("help.title"));
            meta.setAuthor(services.messages().get("help.author"));
            meta.setGeneration(BookMeta.Generation.ORIGINAL);
            meta.pages(List.of(
                    MM.deserialize(services.messages().get("help.page-how")),
                    MM.deserialize(services.messages().get("help.page-payment")),
                    MM.deserialize(services.messages().get("help.page-claims")),
                    MM.deserialize(services.messages().get("help.page-rarity")),
                    MM.deserialize(services.messages().get("help.page-cooldown"))));
        });
        return book;
    }

    public ItemStack icon() {
        ItemStack icon = new ItemStack(Material.WRITABLE_BOOK, 1);
        icon.editMeta(meta -> {
            meta.displayName(MM.deserialize(services.messages().get("gui.help")));
            meta.lore(List.of(MM.deserialize(services.messages().get("help.icon-lore"))));
        });
        return icon;
    }
}