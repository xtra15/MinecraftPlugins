package dev.deathswap;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class DeathSwapCommand implements CommandExecutor {
    private final DeathSwap plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Random RANDOM = new Random();

    public DeathSwapCommand(DeathSwap plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(MM.deserialize("<red>Players only."));
            return true;
        }
        if (!p.hasPermission("deathswap.admin")) {
            p.sendMessage(MM.deserialize("<red>No permission."));
            return true;
        }
        if (args.length == 0) {
            p.sendMessage(MM.deserialize("<gold>/sswap key</gold> start the build phase"));
            p.sendMessage(MM.deserialize("<gold>/sswap team <player> <role></gold> assign to red|blue"));
            p.sendMessage(MM.deserialize("<gold>/sswap reset</gold> reset the arena"));
            p.sendMessage(MM.deserialize("<gold>/sswap status</gold> show teams"));
            return true;
        }
        return switch (args[0].toLowerCase()) {
            case "key" -> key(p);
            case "team" -> team(p, args);
            case "reset" -> reset(p);
            case "status" -> status(p);
            default -> {
                p.sendMessage(MM.deserialize("<red>Unknown subcommand."));
                yield true;
            }
        };
    }

    private boolean key(Player p) {
        Game game = plugin.getGame();
        if (game.getState() != GameState.WAITING && game.getState() != GameState.ENDED) {
            p.sendMessage(MM.deserialize("<red>A round is already running."));
            return true;
        }
        if (game.red().size() < 1 || game.blue().size() < 1) {
            p.sendMessage(MM.deserialize("<red>Assign at least one player to each team with /sswap team.</red>"));
            return true;
        }
        game.startBuild(p);
        p.sendMessage(MM.deserialize("<green>Build phase started! 5 minutes.</green>"));
        return true;
    }

    private boolean team(Player p, String[] args) {
        if (args.length < 3) { p.sendMessage(MM.deserialize("<red>Usage: /sswap team <player> <role></red>")); return true; }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { p.sendMessage(MM.deserialize("<red>Player not found.</red>")); return true; }
        String role = args[2].toLowerCase();
        if (!role.equals("red") && !role.equals("blue")) { p.sendMessage(MM.deserialize("<red>Role must be red or blue.</red>")); return true; }
        Game game = plugin.getGame();
        game.assignTeam(target, role);
        p.sendMessage(MM.deserialize("<green>Assigned <white>" + target.getName() + " <green>to " + role + ".</green>"));
        return true;
    }

    private boolean reset(Player p) {
        Game game = plugin.getGame();
        game.reset();
        p.sendMessage(MM.deserialize("<green>Arena reset.</green>"));
        return true;
    }

    private boolean status(Player p) {
        Game game = plugin.getGame();
        String msg = "<gold>Teams:</gold> <red>Red(" + game.red().size() + ")</red> <blue>Blue(" + game.blue().size() + ")</blue> <gray>State: " + game.getState() + "</gray>";
        p.sendMessage(MM.deserialize(msg));
        return true;
    }
}
