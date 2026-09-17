package dev.deathswap;

import org.bukkit.Material;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class DeathTeam {
    private final String name;
    private final String displayName;
    private final Material color;
    private final Set<UUID> members = new HashSet<>();

    public DeathTeam(String name, String displayName, Material color) {
        this.name = name;
        this.displayName = displayName;
        this.color = color;
    }

    public String getName() { return name; }
    public String getDisplayName() { return displayName; }
    public Material getColor() { return color; }
    public Set<UUID> getMembers() { return members; }
    public int size() { return members.size(); }
    public void add(UUID u) { members.add(u); }
    public boolean remove(UUID u) { return members.remove(u); }
    public boolean contains(UUID u) { return members.contains(u); }
    public void clear() { members.clear(); }
}
