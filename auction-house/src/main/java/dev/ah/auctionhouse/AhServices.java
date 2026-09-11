package dev.ah.auctionhouse;

import dev.ah.core.claim.ClaimStore;
import dev.ah.core.claim.SqlClaimStore;
import dev.ah.core.db.SqliteDatabase;
import dev.ah.core.decision.OfferDecisionService;
import dev.ah.core.geyser.PlatformDetector;
import dev.ah.core.gui.GuiManager;
import dev.ah.core.listing.ListingStore;
import dev.ah.core.listing.SqlListingStore;
import dev.ah.core.msg.MessageRepository;
import dev.ah.core.msg.SoundRegistry;
import dev.ah.core.notification.NotificationStore;
import dev.ah.core.notification.SqlNotificationStore;
import dev.ah.core.offer.OfferStore;
import dev.ah.core.offer.SqlOfferStore;
import dev.ah.core.saleslog.SalesLogStore;
import dev.ah.core.saleslog.SqlSalesLogStore;
import dev.ah.core.sweep.ExpirySweep;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.util.List;

public class AhServices {
    private final ListingStore listings;
    private final OfferStore offers;
    private final ClaimStore claims;
    private final SalesLogStore sales;
    private final NotificationStore notifications;
    private final OfferDecisionService decisions;
    private final ExpirySweep sweep;
    private final MessageRepository messages;
    private final SoundRegistry sounds;
    private final GuiManager gui;
    private final PlatformDetector platform;
    private final SqliteDatabase db;
    private final boolean economyEnabled;
    private final long defaultDurationMs;
    private final List<Long> allowedDurationsMs;
    private final int maxListingsPerPlayer;
    private final int maxItemsPerOffer;
    private final int maxOffersPerPlayer;
    private final long sweepIntervalMs;
    private final int pageSize;
    private final String guiFiller;

    public AhServices(JavaPlugin plugin) {
        plugin.saveResource("config.yml", false);
        plugin.saveResource("lang.yml", false);
        plugin.saveResource("gui.yml", false);
        plugin.saveResource("sounds.yml", false);
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "config.yml"));

        this.db = new SqliteDatabase(new File(plugin.getDataFolder(), "data.db"));
        db.init();
        this.listings = new SqlListingStore(db);
        this.offers = new SqlOfferStore(db);
        this.claims = new SqlClaimStore(db);
        this.sales = new SqlSalesLogStore(db);
        this.notifications = new SqlNotificationStore(db);
        this.decisions = new OfferDecisionService(listings, offers, claims, sales);
        this.sweep = new ExpirySweep(db, listings, claims);
        this.messages = new MessageRepository(new File(plugin.getDataFolder(), "lang.yml"));
        this.sounds = new SoundRegistry(new File(plugin.getDataFolder(), "sounds.yml"));
        this.gui = new GuiManager();
        this.platform = new PlatformDetector();

        this.economyEnabled = cfg.getBoolean("economy.enabled", false);
        this.maxListingsPerPlayer = cfg.getInt("limits.max-listings-per-player", 5);
        this.maxItemsPerOffer = cfg.getInt("limits.max-items-per-offer", 54);
        this.maxOffersPerPlayer = cfg.getInt("limits.max-offers-per-player", 10);
        this.defaultDurationMs = cfg.getLong("default-duration-ms", 86400000L);
        this.allowedDurationsMs = cfg.getLongList("durations-ms");
        this.sweepIntervalMs = cfg.getLong("sweep-interval-seconds", 60L) * 1000L;
        this.pageSize = cfg.getInt("page-size", 36);
        this.guiFiller = cfg.getString("fill-item", "BLACK_STAINED_GLASS_PANE");
    }

    public void close() { db.close(); }
    public ListingStore listings() { return listings; }
    public OfferStore offers() { return offers; }
    public ClaimStore claims() { return claims; }
    public SalesLogStore sales() { return sales; }
    public NotificationStore notifications() { return notifications; }
    public OfferDecisionService decisions() { return decisions; }
    public ExpirySweep sweep() { return sweep; }
    public MessageRepository messages() { return messages; }
    public SoundRegistry sounds() { return sounds; }
    public GuiManager gui() { return gui; }
    public PlatformDetector platform() { return platform; }
    public boolean isEconomyEnabled() { return economyEnabled; }
    public long defaultDurationMs() { return defaultDurationMs; }
    public List<Long> allowedDurationsMs() { return allowedDurationsMs; }
    public int maxListingsPerPlayer() { return maxListingsPerPlayer; }
    public int maxItemsPerOffer() { return maxItemsPerOffer; }
    public int maxOffersPerPlayer() { return maxOffersPerPlayer; }
    public long sweepIntervalMs() { return sweepIntervalMs; }
    public int pageSize() { return pageSize; }
    public String getGuiFiller() { return guiFiller; }
}