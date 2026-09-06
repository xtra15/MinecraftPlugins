package dev.ah.auctionhouse.command;

import dev.ah.auctionhouse.AhServices;
import dev.ah.auctionhouse.gui.AdminGui;
import dev.ah.auctionhouse.gui.AhMainGui;
import dev.ah.auctionhouse.gui.ClaimGui;
import dev.ah.auctionhouse.gui.SellGui;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class AhCommand implements CommandExecutor, TabCompleter {
    private final AhServices services;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final List<String> SUBCOMMANDS = List.of("sell", "search", "my", "claim", "admin", "reload", "help");

    public AhCommand(AhServices services) {
        this.services = services;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length == 0) {
            if (require(player, "ah.use")) new AhMainGui(services).open(player);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "sell" -> {
                if (require(player, "ah.use")) new SellGui(services).open(player);
            }
            case "search" -> {
                if (require(player, "ah.use")) {
                    String term = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "";
                    new AhMainGui(services).openSearch(player, term);
                }
            }
            case "my" -> {
                if (require(player, "ah.use")) new AdminGui(services).openMyListings(player);
            }
            case "claim" -> {
                if (require(player, "ah.use")) new ClaimGui(services).open(player, 0);
            }
            case "admin" -> {
                if (require(player, "ah.admin")) new AdminGui(services).open(player);
            }
            case "reload" -> {
                if (require(player, "ah.admin")) services.sounds().play(player, dev.ah.core.msg.SoundRegistry.Event.CLICK);
            }
            default -> player.sendMessage(MM.deserialize(services.messages().get("commands.help")));
        }
        return true;
    }

    private boolean require(Player player, String perm) {
        if (player.hasPermission(perm)) return true;
        player.sendMessage(MM.deserialize(services.messages().get("admin.no-permission")));
        return false;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}