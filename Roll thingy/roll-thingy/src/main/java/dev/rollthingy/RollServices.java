package dev.rollthingy;

import dev.rollthingy.box.BoxModelCache;
import dev.rollthingy.core.box.BoxRegistry;
import dev.rollthingy.core.box.YamlBoxCodec;
import dev.rollthingy.core.db.SqliteDatabase;
import dev.rollthingy.core.gui.GuiManager;
import dev.rollthingy.core.msg.MessageRepository;
import dev.rollthingy.core.msg.SoundRegistry;
import dev.rollthingy.core.store.ClaimStore;
import dev.rollthingy.core.store.CooldownStore;
import dev.rollthingy.core.store.SqlClaimStore;
import dev.rollthingy.core.store.SqlCooldownStore;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class RollServices {
    private final JavaPlugin plugin;
    private final SqliteDatabase db;
    private final CooldownStore cooldowns;
    private final ClaimStore claims;
    private final BoxRegistry registry;
    private final BoxModelCache cache;
    private final RollService roll;
    private final RollConfig config;
    private final MessageRepository messages;
    private final SoundRegistry sounds;
    private final GuiManager gui;

    public RollServices(JavaPlugin plugin) {
        this.plugin = plugin;
        this.config = new RollConfig(plugin);
        this.db = new SqliteDatabase(new File(plugin.getDataFolder(), "data.db"));
        db.init();
        this.cooldowns = new SqlCooldownStore(db);
        this.claims = new SqlClaimStore(db);
        this.registry = new BoxRegistry(config.boxesDir(), new YamlBoxCodec());
        this.cache = new BoxModelCache(registry);
        cache.reloadAll();
        this.roll = new RollService(cache, cooldowns, claims);
        this.messages = new MessageRepository(new File(plugin.getDataFolder(), "lang.yml"), plugin.getResource("lang.yml"));
        this.sounds = new SoundRegistry(new File(plugin.getDataFolder(), "sounds.yml"));
        this.gui = new GuiManager(plugin);
    }

    public void close() {
        db.close();
    }

    public BoxModelCache cache() { return cache; }
    public BoxRegistry registry() { return registry; }
    public RollService roll() { return roll; }
    public RollConfig config() { return config; }
    public MessageRepository messages() { return messages; }
    public SoundRegistry sounds() { return sounds; }
    public GuiManager gui() { return gui; }
    public JavaPlugin plugin() { return plugin; }
    public CooldownStore cooldowns() { return cooldowns; }
    public ClaimStore claims() { return claims; }
}