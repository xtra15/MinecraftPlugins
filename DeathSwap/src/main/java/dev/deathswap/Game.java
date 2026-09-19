package dev.deathswap;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scoreboard.Scoreboard;

import java.util.*;
import java.util.UUID;

public class Game {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    public static final Material KEY_MATERIAL = Material.GOLD_NUGGET;
    private static final String KEY_NAME = "<gold><bold>Death Swap Key";
    private static final String KEY_LORE = "<gray>Right-click to start the round.";
    private final DeathSwap plugin;
    private final ArenaConfig arena;
    private final DeathTeam red, blue;
    private final Scoreboard board;
    private final Set<UUID> alive = new HashSet<>();
    private final Set<UUID> out = new HashSet<>();
    private GameState state = GameState.WAITING;
    private int grabSeconds;
    private int trapSeconds;
    private int reswapSeconds;
    private int trapTicks = -1;
    private int reswapTicks = -1;
    private final Map<UUID, Integer> preps = new HashMap<>();
    private final Set<UUID> prepped = new HashSet<>();

    private static final Random RANDOM = new Random();

    public Game(DeathSwap plugin) {
        this.plugin = plugin;
        this.arena = new ArenaConfig();
        this.grabSeconds = arena.getGrabSeconds();
        this.trapSeconds = arena.getTrapSeconds();
        this.reswapSeconds = arena.getReswapSeconds();
        this.board = Bukkit.getScoreboardManager().getMainScoreboard();
        this.red = new DeathTeam("deathswap_red", "RED", Material.RED_DYE);
        this.blue = new DeathTeam("deathswap_blue", "BLUE", Material.BLUE_DYE);
        registerScoreboardTeam("deathswap_red", net.kyori.adventure.text.format.NamedTextColor.RED);
        registerScoreboardTeam("deathswap_blue", net.kyori.adventure.text.format.NamedTextColor.BLUE);
    }

    private void registerScoreboardTeam(String name, net.kyori.adventure.text.format.NamedTextColor color) {
        org.bukkit.scoreboard.Team t = board.getTeam(name);
        if (t != null) t.unregister();
        t = board.registerNewTeam(name);
        t.color(color);
        t.setAllowFriendlyFire(false);
        t.setOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY, org.bukkit.scoreboard.Team.OptionStatus.ALWAYS);
    }

    public GameState getState() { return state; }
    public ArenaConfig arena() { return arena; }
    public DeathTeam red() { return red; }
    public DeathTeam blue() { return blue; }
    public int getGrabSeconds() { return grabSeconds; }

    public void setGrabSeconds(int seconds) {
        grabSeconds = Math.max(1, Math.min(600, seconds));
        plugin.getConfig().set("arena.grab-seconds", grabSeconds);
        plugin.saveConfig();
    }

    public int getTrapSeconds() { return trapSeconds; }

    public void setTrapSeconds(int seconds) {
        trapSeconds = Math.max(10, Math.min(3600, seconds));
        plugin.getConfig().set("arena.trap-seconds", trapSeconds);
        plugin.saveConfig();
    }

    public int getReswapSeconds() { return reswapSeconds; }

    public void setReswapSeconds(int seconds) {
        reswapSeconds = Math.max(10, Math.min(3600, seconds));
        plugin.getConfig().set("arena.reswap-seconds", reswapSeconds);
        plugin.saveConfig();
    }

    public boolean tryStart(Player p) {
        if (state != GameState.WAITING && state != GameState.ENDED) {
            p.sendMessage(MM.deserialize("<red>The round is already running.</red>"));
            return true;
        }
        if (red.size() < 1 || blue.size() < 1) {
            p.sendMessage(MM.deserialize("<red>Need at least one player on each team before preparing.</red>"));
            return true;
        }
        UUID id = p.getUniqueId();
        if (preps.containsKey(id) || prepped.contains(id)) {
            p.sendMessage(MM.deserialize("<gold>You already used your prep.</gold>"));
            return true;
        }
        startPrep(p);
        return true;
    }

    public boolean forceSwap(Player p) {
        if (state != GameState.WAITING && state != GameState.ENDED) {
            p.sendMessage(MM.deserialize("<red>The round is already running.</red>"));
            return true;
        }
        if (red.size() < 1 || blue.size() < 1) {
            p.sendMessage(MM.deserialize("<red>Need at least one player on each team before swapping.</red>"));
            return true;
        }
        for (UUID id : new ArrayList<>(preps.keySet())) {
            Player pr = Bukkit.getPlayer(id);
            if (pr != null) { pr.setGameMode(GameMode.SURVIVAL); pr.setLevel(0); pr.setExp(0); }
        }
        preps.clear();
        takeKeys();
        for (Player pr : participants()) prepped.add(pr.getUniqueId());
        startSwap();
        return true;
    }

    private void startPrep(Player p) {
        UUID id = p.getUniqueId();
        preps.put(id, grabSeconds);
        p.setGameMode(GameMode.CREATIVE);
        p.setLevel(grabSeconds);
        p.setExp(1.0f);
        p.sendActionBar(MM.deserialize("<gold><bold>GRAB YOUR STUFF - <white>" + grabSeconds + "<gold>s</bold></gold>"));
        p.sendMessage(MM.deserialize("<aqua>Creative for <white>" + grabSeconds + "s<aqua> - grab items, then back to survival.</aqua>"));
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            int left = preps.getOrDefault(id, 0) - 1;
            if (left <= 0) {
                task.cancel();
                finishPrep(p);
                return;
            }
            preps.put(id, left);
            p.setLevel(left);
            p.setExp(1.0f);
            p.sendActionBar(MM.deserialize("<gold><bold>GRAB YOUR STUFF - <white>" + left + "<gold>s</bold></gold>"));
        }, 20L, 20L);
    }

    private void finishPrep(Player p) {
        UUID id = p.getUniqueId();
        preps.remove(id);
        prepped.add(id);
        p.setGameMode(GameMode.SURVIVAL);
        p.setLevel(0);
        p.setExp(0);
        p.sendActionBar(MM.deserialize("<green>Back to survival!</green>"));
        for (ItemStack it : p.getInventory().getContents()) {
            if (isKeyItem(it)) it.setAmount(0);
        }
        maybeStartSwap();
    }

    private void maybeStartSwap() {
        if (state != GameState.WAITING && state != GameState.ENDED) return;
        List<Player> ps = participants();
        if (ps.isEmpty()) return;
        for (Player pr : ps) {
            if (!prepped.contains(pr.getUniqueId())) return;
        }
        startTrapPhase();
    }

    void startTrapPhase() {
        state = GameState.TRAP;
        alive.clear(); out.clear();
        for (Player pr : participants()) alive.add(pr.getUniqueId());
        trapTicks = trapSeconds * 20;
        Bukkit.broadcast(MM.deserialize("<gold>Everyone's ready! <aqua>" + trapSeconds + "s<gold> to prepare your traps, then SWAP!</gold>"));
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (state != GameState.TRAP) { task.cancel(); return; }
            if (trapTicks <= 0) { task.cancel(); startSwap(); return; }
            trapTicks--;
            int s = (trapTicks + 19) / 20;
            if (s > 0 && s <= 10) {
                net.kyori.adventure.text.Component msg = MM.deserialize("<gold><bold>SWAP in <white>" + s + "<gold>s!</bold></gold>");
                for (UUID u : alive) {
                    Player pr = Bukkit.getPlayer(u);
                    if (pr != null) pr.sendActionBar(msg);
                }
            }
        }, 1L, 1L);
    }

    private List<Player> participants() {
        List<Player> ps = new ArrayList<>();
        for (UUID u : red.getMembers()) {
            Player pl = Bukkit.getPlayer(u);
            if (pl != null) ps.add(pl);
        }
        for (UUID u : blue.getMembers()) {
            Player pl = Bukkit.getPlayer(u);
            if (pl != null) ps.add(pl);
        }
        return ps;
    }

    public void giveKey(Player p) {
        if (state != GameState.WAITING && state != GameState.ENDED) return;
        p.getInventory().clear();
        ItemStack key = new ItemStack(KEY_MATERIAL, 1);
        key.editMeta(meta -> {
            meta.displayName(MM.deserialize(KEY_NAME));
            meta.lore(List.of(MM.deserialize(KEY_LORE)));
        });
        p.getInventory().addItem(key); // (first empty slot; inventory full = no key, no round)
    }

    public void giveKeysToAll() {
        for (Player p : Bukkit.getOnlinePlayers()) giveKey(p);
    }

    public boolean isKeyItem(ItemStack it) {
        if (it == null || it.getType() != KEY_MATERIAL) return false;
        ItemMeta meta = it.getItemMeta();
        return meta != null && meta.displayName() != null && meta.displayName().equals(MM.deserialize(KEY_NAME));
    }

    public void takeKeys() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            for (ItemStack it : p.getInventory().getContents()) {
                if (isKeyItem(it)) it.setAmount(0);
            }
        }
    }

    private void teleportToPlot(Player p) {}

    void startSwap() {
        state = GameState.SWAPPING;
        for (UUID id : new ArrayList<>(preps.keySet())) {
            Player pr = Bukkit.getPlayer(id);
            if (pr != null) { pr.setGameMode(GameMode.SURVIVAL); pr.setLevel(0); pr.setExp(0); }
            prepped.add(id);
        }
        preps.clear();
        if (alive.isEmpty()) {
            alive.clear(); out.clear();
            for (Player pr : participants()) alive.add(pr.getUniqueId());
        }
        List<Player> redMembers = onlineAlive(red);
        List<Player> blueMembers = onlineAlive(blue);
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
        reswapTicks = reswapSeconds * 20;
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (state != GameState.FIGHTING) { task.cancel(); return; }
            if (alive.size() <= 1) { task.cancel(); return; }
            if (reswapTicks <= 0) {
                List<Player> r = onlineAlive(red);
                List<Player> b = onlineAlive(blue);
                if (r.isEmpty() || b.isEmpty()) {
                    reswapTicks = reswapSeconds * 20; // one side wiped, wait for the fight to resolve
                    return;
                }
                task.cancel();
                Bukkit.broadcast(MM.deserialize("<gold>Nobody died — swapping again!</gold>"));
                startSwap();
                return;
            }
            reswapTicks--;
            int s = (reswapTicks + 19) / 20;
            if (s > 0 && s <= 10) {
                net.kyori.adventure.text.Component msg = MM.deserialize("<gold><bold>SWAP in <white>" + s + "<gold>s!</bold></gold>");
                for (UUID u : alive) {
                    Player pr = Bukkit.getPlayer(u);
                    if (pr != null) pr.sendActionBar(msg);
                }
            }
        }, 1L, 1L);
    }

    public void onDeath(UUID dead) {
        if (state != GameState.SWAPPING && state != GameState.FIGHTING) return;
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
        preps.clear(); prepped.clear();
        trapTicks = -1;
        reswapTicks = -1;
        alive.clear(); out.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setGameMode(GameMode.SURVIVAL);
            p.setInvulnerable(false);
            p.setLevel(0);
            p.setExp(0);
        }
        giveKeysToAll(); // resets team-agnostic; teams are preserved
    }

    public void clearTeams() {
        red.clear(); blue.clear();
        giveKeysToAll();
    }

    List<Player> onlineAlive(DeathTeam t) {
        List<Player> ps = new ArrayList<>();
        for (UUID u : t.getMembers()) {
            if (!alive.contains(u)) continue;
            Player p = Bukkit.getPlayer(u);
            if (p != null) ps.add(p);
        }
        return ps;
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
