package dev.deathswap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

public class ArenaConfig {
    private final double redX1, redZ1, redX2, redZ2, redY1, redY2;
    private final double blueX1, blueZ1, blueX2, blueZ2, blueY1, blueY2;
    private final Location lobby;
    private final int buildSeconds;

    public ArenaConfig() {
        ConfigurationSection a = DeathSwap.getInstance().getConfig().getConfigurationSection("arena");
        ConfigurationSection red = a.getConfigurationSection("red");
        ConfigurationSection blue = a.getConfigurationSection("blue");
        redX1 = red.getDouble("x1"); redZ1 = red.getDouble("z1");
        redX2 = red.getDouble("x2"); redZ2 = red.getDouble("z2");
        redY1 = red.getDouble("y1"); redY2 = red.getDouble("y2");
        blueX1 = blue.getDouble("x1"); blueZ1 = blue.getDouble("z1");
        blueX2 = blue.getDouble("x2"); blueZ2 = blue.getDouble("z2");
        blueY1 = blue.getDouble("y1"); blueY2 = blue.getDouble("y2");
        ConfigurationSection l = a.getConfigurationSection("lobby");
        World w = Bukkit.getWorld("world");
        lobby = new Location(w, l.getDouble("x"), l.getDouble("y"), l.getDouble("z"));
        buildSeconds = a.getInt("build-seconds", 5);
    }

    public Location getRedCenter() { return new Location(lobby.getWorld(), (redX1+redX2)/2, (redY1+redY2)/2, (redZ1+redZ2)/2); }
    public Location getBlueCenter() { return new Location(lobby.getWorld(), (blueX1+blueX2)/2, (blueY1+blueY2)/2, (blueZ1+blueZ2)/2); }
    public Location getLobby() { return lobby; }
    public int getBuildSeconds() { return buildSeconds; }

    public boolean inRed(Location loc) { return inBox(loc, redX1, redZ1, redX2, redZ2); }
    public boolean inBlue(Location loc) { return inBox(loc, blueX1, blueZ1, blueX2, blueZ2); }
    public boolean inArena(Location loc) { return inRed(loc) || inBlue(loc); }
    public boolean inBuildArea(Location loc) { return inArena(loc); }

    private static boolean inBox(Location loc, double x1, double z1, double x2, double z2) {
        double minX = Math.min(x1,x2), maxX = Math.max(x1,x2);
        double minZ = Math.min(z1,z2), maxZ = Math.max(z1,z2);
        return loc.getX() >= minX && loc.getX() <= maxX && loc.getZ() >= minZ && loc.getZ() <= maxZ;
    }
}
