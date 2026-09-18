package dev.rollthingy.command;

import dev.rollthingy.RollServices;
import dev.rollthingy.RollThingyPlugin;
import dev.rollthingy.core.box.Box;
import dev.rollthingy.gui.BoxDetailGui;
import dev.rollthingy.gui.ClaimGui;
import dev.rollthingy.gui.HistoryGui;
import dev.rollthingy.gui.MainGui;
import dev.rollthingy.gui.admin.AdminGui;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RollCommand implements CommandExecutor, TabCompleter {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final List<String> SUBCOMMANDS = List.of("admin", "claim", "history", "reload");
    private final RollThingyPlugin plugin;

    public RollCommand(RollThingyPlugin plugin) {
        this.plugin = plugin;
    }

    private RollServices services() {
        return plugin.services();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length == 0) {
            if (require(player, "roll.use")) new MainGui(services()).open(player, 0);
            return true;
        }
        String head = args[0].toLowerCase(Locale.ROOT);
        switch (head) {
            case "admin" -> {
                if (require(player, "roll.admin")) new AdminGui(services()).open(player, 0);
            }
            case "claim" -> {
                if (require(player, "roll.use")) new ClaimGui(services()).open(player, 0);
            }
            case "history" -> {
                if (require(player, "roll.use"))
                    new HistoryGui(services()).open(player, 0, player.getUniqueId());
            }
            case "reload" -> {
                if (require(player, "roll.admin")) {
                    services().cache().reloadAll();
                    player.sendMessage(MM.deserialize(services().messages().get("admin.saved")));
                }
            }
            default -> {
                if (require(player, "roll.use")) {
                    Box box = services().cache().byId(head);
                    if (box == null) {
                        player.sendMessage(MM.deserialize(services().messages().get("errors.unknown")));
                        return true;
                    }
                    new BoxDetailGui(services()).open(player, box);
                }
            }
        }
        return true;
    }

    private boolean require(Player player, String perm) {
        if (player.hasPermission(perm)) return true;
        player.sendMessage(MM.deserialize(services().messages().get("errors.no-permission")));
        return false;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(sub);
            }
            for (Box box : services().cache().boxes()) {
                if (box.id().startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(box.id());
            }
            return out;
        }
        return List.of();
    }
}