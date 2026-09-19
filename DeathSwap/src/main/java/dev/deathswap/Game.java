package dev.deathswap;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scoreboard.Scoreboard;

import java.util.*;
import java.util.UUID;

public class Game {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final DeathSwap plugin;
    private final ArenaConfig arena;
    private final DeathTeam red, blue;
    private final Scoreboard board;
    private final Set<UUID> alive = new HashSet<>();
    private final Set<UUID> out = new HashSet<>();
    private GameState state = GameState.WAITING;
    private BossBar bossBar;
    private int buildSeconds;
    private int buildTick = -1;

    private static final Random RANDOM = new Random();

    public Game(DeathSwap plugin) {
        this.plugin = plugin;
        this.arena = new ArenaConfig();
        this.buildSeconds = arena.getBuildSeconds();
        this.board = Bukkit.getScoreboardManager().getMainScoreboard();
        this.red = new DeathTeam("deathswap_red", "RED", Material.RED_DYE);
        this.blue = new DeathTeam("deathswap_blue", "BLUE", Material.BLUE_DYE);
        registerScoreboardTeam("deathswap_red", "RED", Material.RED_DYE);
        registerScoreboardTeam("deathswap_blue", "BLUE", Material.BLUE_DYE);
    }

    private org.bukkit.scoreboard.Team registerScoreboardTeam(String name, String prefix, Material color) {
        org.bukkit.scoreboard.Team t = board.getTeam(name);
        if (t == null) t = board.registerNewTeam(name);
        t.prefix(MM.deserialize("<" + color.name().toLowerCase() + ">" + prefix + " "));
        return t;
    }

    public GameState getState() { return state; }
    public ArenaConfig arena() { return arena; }
    public DeathTeam red() { return red; }
    public DeathTeam blue() { return blue; }

    void startBuild(Player admin) {
        state = GameState.BUILD;
        alive.clear(); out.clear();
        for (UUID u : red.getMembers()) alive.add(u);
        for (UUID u : blue.getMembers()) alive.add(u);
        buildTick = buildSeconds * 20;
        bossBar = Bukkit.createBossBar("Build Phase " + buildSeconds + "s", BarColor.GREEN, BarStyle.SOLID);
        for (UUID u : alive) {
            Player p = Bukkit.getPlayer(u);
            if (p == null) continue;
            p.setGameMode(GameMode.CREATIVE);
            p.setInvulnerable(false);
            bossBar.addPlayer(p);
        }
    }

    private void teleportToPlot(Player p) {}

    void tickBuild() {
        if (buildTick <= 0) { startSwap(); return; }
        buildTick--;
        int s = (buildTick + 19) / 20;
        bossBar.setTitle("Build Phase " + s + "s");
        bossBar.setProgress(buildSeconds > 0 ? (double) buildTick / (buildSeconds * 20) : 0);
    }

    void startSwap() {
        if (bossBar != null) bossBar.removeAll();
        state = GameState.SWAPPING;
        List<Player> redMembers = online(red);
        List<Player> blueMembers = online(blue);
        if (redMembers.isEmpty() || blueMembers.isEmpty()) {
            Bukkit.getConsoleSender().sendMessage(MM.deserialize("<red>Cannot swap: a team has no online players.</red>"));
            endFight();
            return;
        }
        Bukkit.broadcast(MM.deserialize("<gold>SWAP! Every player swaps with a random enemy.</gold>"));
        Map<Player, Location> redLocs = snap(redMembers);
        Map<Player, Location> blueLocs = snap(blueMembers);
        for (Player p : redMembers) {
            Player target = random(blueMembers);
            p.teleport(blueLocs.get(target), PlayerTeleportEvent.TeleportCause.PLUGIN);
            p.setInvulnerable(true);
            Bukkit.getScheduler().runTaskLater(plugin, () -> p.setInvulnerable(false), 20L * 2);
        }
        for (Player p : blueMembers) {
            Player target = random(redMembers);
            p.teleport(redLocs.get(target), PlayerTeleportEvent.TeleportCause.PLUGIN);
            p.setInvulnerable(true);
            Bukkit.getScheduler().runTaskLater(plugin, () -> p.setInvulnerable(false), 20L * 2);
        }
        Bukkit.getScheduler().runTaskLater(plugin, this::startFight, 20L * 3);
    }

    private Map<Player, Location> snap(List<Player> ps) {
        Map<Player, Location> m = new HashMap<>();
        for (Player p : ps) m.put(p, p.getLocation().clone());
        return m;
    }

    private <T> T random(List<T> list) { return list.get(RANDOM.nextInt(list.size())); }

    void endFight() { reset(); }

    void startFight() {
        state = GameState.FIGHTING;
        for (UUID u : alive) {
            Player p = Bukkit.getPlayer(u);
            if (p == null) continue;
            p.setGameMode(GameMode.SURVIVAL);
            p.setInvulnerable(false);
        }
        Bukkit.broadcast(MM.deserialize("<gold>Fight! Last player standing wins.</gold>"));
    }

    public void onDeath(UUID dead) {
        if (state != GameState.BUILD && state != GameState.SWAPPING && state != GameState.FIGHTING) return;
        alive.remove(dead);
        out.add(dead);
        Player p = Bukkit.getPlayer(dead);
        if (p != null) p.setGameMode(GameMode.SPECTATOR);
        if (alive.size() <= 1) announceWinner();
    }

    private void announceWinner() {
        state = GameState.ENDED;
        if (alive.isEmpty()) {
            Bukkit.broadcast(MM.deserialize("<gold>Draw — everyone is out. Nobody is teleported.</gold>"));
        } else {
            UUID winner = alive.iterator().next();
            Player wp = Bukkit.getPlayer(winner);
            String name = wp != null ? wp.getName() : winner.toString();
            Bukkit.broadcast(MM.deserialize("<green>Winner: <white>" + name + "</white>!</green>"));
        }
        Bukkit.getScheduler().runTaskLater(plugin, this::reset, 20L * 20);
    }

    public void assignTeam(Player p, String role) {
        UUID id = p.getUniqueId();
        boolean redRole = role.equalsIgnoreCase("red");
        DeathTeam to = redRole ? red : blue;
        DeathTeam from = redRole ? blue : red;
        from.remove(id);
        to.add(id);
        org.bukkit.scoreboard.Team fromSb = board.getTeam(from.getName());
        org.bukkit.scoreboard.Team toSb = board.getTeam(to.getName());
        if (fromSb != null) fromSb.removeEntry(p.getName());
        if (toSb != null) toSb.addEntry(p.getName());
        p.setScoreboard(board);
        p.setGameMode(GameMode.SURVIVAL);
    }

    public DeathTeam deathTeamOf(Player p) {
        return red.contains(p.getUniqueId()) ? red : blue;
    }

    public org.bukkit.scoreboard.Team getTeam(Player p) {
        return board.getPlayerTeam(p);
    }

    void reset() {
        state = GameState.WAITING;
        if (bossBar != null) { bossBar.removeAll(); bossBar = null; }
        buildTick = -1;
        red.clear(); blue.clear();
        alive.clear(); out.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setGameMode(GameMode.SURVIVAL);
            p.setInvulnerable(false);
        }
    }

    List<Player> online(DeathTeam t) {
        List<Player> ps = new ArrayList<>();
        for (UUID u : t.getMembers()) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) ps.add(p);
        }
        return ps;
    }
}
