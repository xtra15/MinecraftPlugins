# Auction House Plugin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first plugin (`auction-house`) in a multi-module Minecraft Paper workspace, with a shared `core` library that future plugins will reuse.

**Architecture:** Gradle root with two modules. `core` is a headless domain library (SQLite storage, listings/offers/claims/sales-log domain logic, decision+sweep services) with zero server-runtime dependencies in its pure logic — fully unit-testable. `auction-house` is the Paper plugin that adapts core to Bukkit: chest GUIs, commands, listeners, configs, and wiring. The build shades `core` (with its SQLite driver) into the final self-contained jar and relocates the packages.

**Tech Stack:** Java 21, Gradle 8.10 + Shadow plugin, Paper API, SQLite (xerial JDBC, shaded), Adventure MiniMessage (server-provided), JUnit 5, Floodgate (optional, reflection-detected).

## Global Constraints

- Java toolchain 21; `group = "dev.ah"`, plugin version `1.0.0`.
- Module packages: `dev.ah.core` and `dev.ah.auctionhouse`; shadow relocates `dev.ah.core` → `dev.ah.auctionhouse.core` in the plugin jar.
- Paper API version is set in `gradle.properties` as `paperApiVersion`; must be bumped to match the target server (verified in Task 1).
- Economy is **disabled by default** (`economy.enabled: false`). Its code is a skeleton; no auction feature may depend on it while disabled.
- All market transfers route through claims. No item/money ever drops directly into a player's inventory on a trade.
- Item storage is NBT-preserving: base64 via `ItemBundleCodec` (list of `Map<String,Object>`), never material names.
- IDs are `LONG` from SQLite AUTOINCREMENT; UUIDs stored as strings; timestamps are epoch millis.
- Status strings: listings `ACTIVE/SOLD/EXPIRED/CANCELLED`; offers `PENDING/ACCEPTED/REJECTED/WITHDRAWN`.
- Default commands/permissions: `/ah` + alias `/auctionhouse`, permissions `ah.use` (default true), `ah.admin` (default op).
- Bedrock (Geyser) support: all player text entry happens in command args, never inside a GUI; all menus are chest GUIs.
- Windows workspace; scripts below use PowerShell 5.1 semantics.

---

## File Structure

```
settings.gradle.kts
build.gradle.kts
gradle.properties
.gitignore
gradle/wrapper/gradle-wrapper.properties          (bootstrap with `gradle wrapper`)
core/build.gradle.kts
core/src/main/java/dev/ah/core/
  misc/Pager.java
  misc/ItemBundleCodec.java
  db/SqliteDatabase.java
  listing/Listing.java
  listing/ListingStore.java
  listing/SqlListingStore.java
  offer/Offer.java
  offer/OfferStore.java
  offer/SqlOfferStore.java
  claim/ClaimRow.java
  claim/ClaimStore.java
  claim/SqlClaimStore.java
  saleslog/SalesLogRow.java
  saleslog/SalesLogStore.java
  saleslog/SqlSalesLogStore.java
  notification/Notification.java
  notification/NotificationStore.java
  notification/SqlNotificationStore.java
  decision/OfferDecisionService.java
  sweep/ExpirySweep.java
  economy/EconomyService.java
  economy/SqlBalanceStore.java
  msg/MessageRepository.java
  msg/SoundRegistry.java
  gui/ChestGui.java
  gui/DepositGui.java
  gui/GuiManager.java
  geyser/PlatformDetector.java
core/src/test/java/dev/ah/core/
  misc/PagerTest.java
  misc/ItemBundleCodecTest.java
  db/SqliteDatabaseTest.java
  listing/SqlListingStoreTest.java
  offer/SqlOfferStoreTest.java
  claim/SqlClaimStoreTest.java
  saleslog/SqlSalesLogStoreTest.java
  notification/SqlNotificationStoreTest.java
  decision/OfferDecisionServiceTest.java
  sweep/ExpirySweepTest.java
auction-house/build.gradle.kts
auction-house/src/main/java/dev/ah/auctionhouse/
  AuctionHousePlugin.java
  AhServices.java
  gui/AhMainGui.java
  gui/SellGui.java
  gui/OfferGui.java
  gui/OfferBoardGui.java
  gui/ClaimGui.java
  gui/AdminGui.java
  command/AhCommand.java
  listener/MenuListener.java
  listener/JoinNotifier.java
  notify/NotificationService.java
auction-house/src/main/resources/
  plugin.yml
  config.yml
  lang.yml
  gui.yml
  sounds.yml
```

---

### Task 1: Gradle Workspace Scaffold

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `.gitignore`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `core/build.gradle.kts`
- Create: `auction-house/build.gradle.kts`

**Interfaces:**
- Produces: empty compilable `core` and `auction-house` modules; the `paperApiVersion` Gradle property used by every later task.

- [ ] **Step 1: Write `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

rootProject.name = "minecraft-plugins"
include("core", "auction-house")
```

- [ ] **Step 2: Write `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx1g
org.gradle.parallel=true
paperApiVersion=1.21.4-R0.1-SNAPSHOT
pluginVersion=1.0.0
```

Note: `paperApiVersion` MUST match the running server's Minecraft version. The server's `git-Paper-<mc>-<build>` (check `/version` in-game or the chosen Paper download) maps to `io.papermc.paper:paper-api:<mc>-R0.1-SNAPSHOT`. Bump this before building for the real server.

- [ ] **Step 3: Write root `build.gradle.kts`**

```kotlin
plugins {
    id("com.gradleup.shadow") version "8.3.5" apply false
}

subprojects {
    group = "dev.ah"
    version = providers.gradleProperty("pluginVersion").get()
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}
```

- [ ] **Step 4: Write `.gitignore`**

```gitignore
.gradle/
build/
out/
*.iml
.idea/
.vscode/
run/
*.log
```

- [ ] **Step 5: Write `core/build.gradle.kts`**

```kotlin
plugins {
    `java-library`
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${property("paperApiVersion")}")
    implementation("org.xerial:sqlite-jdbc:3.45.3.0")
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "skipped", "failed") }
}
```

- [ ] **Step 6: Write `auction-house/build.gradle.kts`**

```kotlin
plugins {
    java
    id("com.gradleup.shadow")
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

dependencies {
    implementation(project(":core"))
    compileOnly("io.papermc.paper:paper-api:${property("paperApiVersion")}")
}

tasks.shadowJar {
    relocate("dev.ah.core", "dev.ah.auctionhouse.core")
    archiveFileName.set("AuctionHouse-${project.version}.jar")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
```

- [ ] **Step 7: Write `gradle/wrapper/gradle-wrapper.properties`**

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.10.2-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

- [ ] **Step 8: Bootstrap and verify**

Run: `gradle wrapper --gradle-version 8.10.2` (run once with any locally installed Gradle ≥ 8.x to generate `gradlew`, `gradlew.bat`, and the wrapper jar; if no Gradle is installed, install once via `winget install Gradle` or the distribution zip referenced above).
Run: `.\gradlew.bat build`
Expected: `BUILD SUCCESSFUL`. `core\build\libs\core-1.0.0.jar` and `auction-house\build\libs\auction-house-1.0.0.jar` exist.

- [ ] **Step 9: Commit**

```powershell
git add settings.gradle.kts build.gradle.kts gradle.properties .gitignore gradle core/auction-house
git commit -m "chore: scaffold gradle multi-module workspace"
```

---

### Task 2: Core — `Pager`

**Files:**
- Create: `core/src/main/java/dev/ah/core/misc/Pager.java`
- Test: `core/src/test/java/dev/ah/core/misc/PagerTest.java`

**Interfaces:**
- Produces: `public final class Pager<T> { public Pager(List<T> items, int perPage); public int pages(); public List<T> page(int pageIndex); public int pageSize(); }` — `pageIndex` is 0-based; `perPage >= 1` (IllegalArgumentException otherwise); out-of-range index returns empty list; 0 items yields 1 page with an empty result.

- [ ] **Step 1: Write the failing test**

```java
package dev.ah.core.misc;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PagerTest {

    @Test
    void pagesAcrossChunks() {
        Pager<Integer> p = new Pager<>(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), 4);
        assertEquals(3, p.pages());
        assertEquals(List.of(1, 2, 3, 4), p.page(0));
        assertEquals(List.of(5, 6, 7, 8), p.page(1));
        assertEquals(List.of(9, 10), p.page(2));
    }

    @Test
    void emptyInputYieldsOneEmptyPage() {
        Pager<String> p = new Pager<>(List.of(), 9);
        assertEquals(1, p.pages());
        assertTrue(p.page(0).isEmpty());
        assertTrue(p.page(5).isEmpty());
    }

    @Test
    void outOfRangeReturnsEmpty() {
        Pager<String> p = new Pager<>(List.of("a"), 9);
        assertTrue(p.page(2).isEmpty());
    }

    @Test
    void rejectsInvalidPageSize() {
        assertThrows(IllegalArgumentException.class, () -> new Pager<>(List.of("a"), 0));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :core:test --tests "dev.ah.core.misc.PagerTest"`
Expected: FAIL — `Pager` is not defined.

- [ ] **Step 3: Write minimal implementation**

```java
package dev.ah.core.misc;

import java.util.ArrayList;
import java.util.List;

public final class Pager<T> {
    private final List<T> items;
    private final int perPage;

    public Pager(List<T> items, int perPage) {
        if (perPage < 1) throw new IllegalArgumentException("perPage must be >= 1");
        this.items = new ArrayList<>(items);
        this.perPage = perPage;
    }

    public int pages() {
        return Math.max(1, (items.size() + perPage - 1) / perPage);
    }

    public List<T> page(int pageIndex) {
        int start = pageIndex * perPage;
        if (start < 0 || start >= items.size()) return List.of();
        return items.subList(start, Math.min(start + perPage, items.size()));
    }

    public int pageSize() {
        return perPage;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :core:test --tests "dev.ah.core.misc.PagerTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```powershell
git add core/src/main/java/dev/ah/core/misc/Pager.java core/src/test/java/dev/ah/core/misc/PagerTest.java
git commit -m "feat(core): add Pager pagination util"
```

---

### Task 3: Core — `ItemBundleCodec` (NBT-preserving item storage)

**Files:**
- Create: `core/src/main/java/dev/ah/core/misc/ItemBundleCodec.java`
- Test: `core/src/test/java/dev/ah/core/misc/ItemBundleCodecTest.java`

**Interfaces:**
- Produces:
```java
public final class ItemBundleCodec {
    // Pure (unit-testable, no Bukkit runtime):
    public static String encodeMaps(List<Map<String, Object>> maps);
    public static List<Map<String, Object>> decodeMaps(String base64);   // throws IllegalArgumentException on corrupt data
    // Bukkit adapters (server-side only):
    public static String encode(List<ItemStack> items);
    public static List<ItemStack> decode(String base64);                 // throws IllegalArgumentException on corrupt data
}
```
Encoding is Java serialization of `List<Map<String,Object>>` → base64. Corrupt input throws `IllegalArgumentException`.

- [ ] **Step 1: Write the failing test**

```java
package dev.ah.core.misc;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ItemBundleCodecTest {

    private static Map<String, Object> fixtureItem(String type) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("display-name", "Custom Sword");
        meta.put("damage", 0);
        Map<String, Object> item = new HashMap<>();
        item.put("type", type);
        item.put("amount", 1);
        item.put("meta", meta);
        return item;
    }

    @Test
    void roundTripsListOfMaps() {
        List<Map<String, Object>> original = List.of(fixtureItem("DIAMOND_SWORD"), fixtureItem("NETHERITE_PICKAXE"));
        String encoded = ItemBundleCodec.encodeMaps(original);
        List<Map<String, Object>> decoded = ItemBundleCodec.decodeMaps(encoded);
        assertEquals(original, decoded);
    }

    @Test
    void emptyListRoundTrips() {
        assertEquals(List.of(), ItemBundleCodec.decodeMaps(ItemBundleCodec.encodeMaps(List.of())));
    }

    @Test
    void corruptInputThrows() {
        assertThrows(IllegalArgumentException.class, () -> ItemBundleCodec.decodeMaps("not-base64!!!"));
        assertThrows(IllegalArgumentException.class, () -> ItemBundleCodec.decodeMaps(""));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :core:test --tests "dev.ah.core.misc.ItemBundleCodecTest"`
Expected: FAIL — class not defined.

- [ ] **Step 3: Write minimal implementation**

```java
package dev.ah.core.misc;

import org.bukkit.inventory.ItemStack;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public final class ItemBundleCodec {
    private ItemBundleCodec() {}

    public static String encodeMaps(List<Map<String, Object>> maps) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(new ArrayList<>(maps));
            out.flush();
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("failed to encode item data", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> decodeMaps(String base64) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalArgumentException("empty item data");
        }
        try (ByteArrayInputStream bytes = new ByteArrayInputStream(Base64.getDecoder().decode(base64));
             ObjectInputStream in = new ObjectInputStream(bytes)) {
            return (List<Map<String, Object>>) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalArgumentException("corrupt item data", e);
        }
    }

    public static String encode(List<ItemStack> items) {
        List<Map<String, Object>> maps = new ArrayList<>();
        for (ItemStack item : items) maps.add(item.serialize());
        return encodeMaps(maps);
    }

    public static List<ItemStack> decode(String base64) {
        List<ItemStack> items = new ArrayList<>();
        for (Map<String, Object> map : decodeMaps(base64)) items.add(ItemStack.deserialize(map));
        return items;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :core:test --tests "dev.ah.core.misc.ItemBundleCodecTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```powershell
git add core/src/main/java/dev/ah/core/misc/ItemBundleCodec.java core/src/test/java/dev/ah/core/misc/ItemBundleCodecTest.java
git commit -m "feat(core): add NBT-preserving ItemBundleCodec"
```

---

### Task 4: Core — `SqliteDatabase` (schema + transactions)

**Files:**
- Create: `core/src/main/java/dev/ah/core/db/SqliteDatabase.java`
- Test: `core/src/test/java/dev/ah/core/db/SqliteDatabaseTest.java`

**Interfaces:**
- Produces:
```java
public final class SqliteDatabase implements AutoCloseable {
    public SqliteDatabase(File file);
    public static SqliteDatabase inMemory();                 // test helper
    public void init();                                       // creates schema + indexes
    public <T> T transact(Transaction<T> body);               // begin/commit/rollback, throws RuntimeException on failure
    public void close();
    @FunctionalInterface public interface Transaction<T> { T run(Connection c) throws SQLException; }
}
```
Schema includes tables `listings`, `offers`, `claims`, `sales_log`, `balances`, `notifications` (see SQL below). AutoCommit is restored to previous value after each transaction.

- [ ] **Step 1: Write the failing test**

```java
package dev.ah.core.db;

import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import static org.junit.jupiter.api.Assertions.*;

class SqliteDatabaseTest {

    @Test
    void missingTablesAreCreated() throws SQLException {
        SqliteDatabase db = SqliteDatabase.inMemory();
        db.init();
        try (Connection c = db.conn();
             ResultSet rs = c.getMetaData().getTables(null, null, "%", null)) {
            java.util.Set<String> tables = new java.util.HashSet<>();
            while (rs.next()) tables.add(rs.getString("TABLE_NAME").toLowerCase());
            for (String expect : new String[]{"listings", "offers", "claims", "sales_log", "balances", "notifications"}) {
                assertTrue(tables.contains(expect), "missing table " + expect);
            }
        }
        db.close();
    }

    @Test
    void transactionCommits() {
        SqliteDatabase db = SqliteDatabase.inMemory();
        db.init();
        Long id = db.transact(c -> {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("INSERT INTO notifications (player_uuid, message_key, created_at) VALUES ('u', 'x', 1)");
            } catch (SQLException e) { throw new RuntimeException(e); }
            return 1L;
        });
        assertEquals(1L, id);
        db.close();
    }

    @Test
    void transactionRollsBackOnFailure() throws SQLException {
        SqliteDatabase db = SqliteDatabase.inMemory();
        db.init();
        try (Statement st = db.conn().createStatement()) {
            st.executeUpdate("INSERT INTO notifications (player_uuid, message_key, created_at) VALUES ('u', 'x', 1)");
        }
        assertThrows(RuntimeException.class, () -> db.transact(c -> {
            throw new SQLException("boom");
        }));
        try (Statement st = db.conn().createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM notifications")) {
            rs.next();
            assertEquals(0, rs.getInt(1));
        }
        db.close();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :core:test --tests "dev.ah.core.db.SqliteDatabaseTest"`
Expected: FAIL — class not defined.

- [ ] **Step 3: Write minimal implementation**

```java
package dev.ah.core.db;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

public final class SqliteDatabase implements AutoCloseable {
    private final Connection connection;

    public SqliteDatabase(File file) {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        } catch (ClassNotFoundException | SQLException e) {
            throw new IllegalStateException("failed to open database", e);
        }
    }

    public static SqliteDatabase inMemory() {
        try {
            Class.forName("org.sqlite.JDBC");
            Connection c = DriverManager.getConnection(
                    "jdbc:sqlite:file:" + UUID.randomUUID() + "?mode=memory&cache=shared");
            return new SqliteDatabase(c);
        } catch (ClassNotFoundException | SQLException e) {
            throw new IllegalStateException("failed to open in-memory database", e);
        }
    }

    private SqliteDatabase(Connection c) {
        this.connection = c;
    }

    public Connection conn() {
        return connection;
    }

    public void init() {
        transactAll(DDL);
    }

    public <T> T transact(Transaction<T> body) {
        synchronized (this) {
            boolean previousAutoCommit;
            try {
                previousAutoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);
                T result = body.run(connection);
                connection.commit();
                return result;
            } catch (SQLException e) {
                try { connection.rollback(); } catch (SQLException ignored) {}
                throw new RuntimeException("database transaction failed", e);
            } finally {
                try { connection.setAutoCommit(previousAutoCommit); } catch (SQLException ignored) {}
            }
        }
    }

    private void transactAll(String sql) {
        transact(c -> {
            try (Statement st = c.createStatement()) {
                for (String statement : sql.split(";")) {
                    if (!statement.isBlank()) st.executeUpdate(statement);
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    @Override
    public synchronized void close() {
        try { connection.close(); } catch (SQLException ignored) {}
    }

    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS listings (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              owner_uuid TEXT NOT NULL,
              item_data TEXT NOT NULL,
              price REAL,
              duration_ms INTEGER NOT NULL,
              created_at INTEGER NOT NULL,
              expires_at INTEGER NOT NULL,
              status TEXT NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_listings_status ON listings(status);
            CREATE INDEX IF NOT EXISTS idx_listings_owner ON listings(owner_uuid);
            CREATE TABLE IF NOT EXISTS offers (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              listing_id INTEGER NOT NULL,
              offerer_uuid TEXT NOT NULL,
              items_data TEXT NOT NULL,
              status TEXT NOT NULL,
              created_at INTEGER NOT NULL,
              decided_at INTEGER
            );
            CREATE INDEX IF NOT EXISTS idx_offers_listing ON offers(listing_id);
            CREATE INDEX IF NOT EXISTS idx_offers_offerer ON offers(offerer_uuid);
            CREATE TABLE IF NOT EXISTS claims (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              owner_uuid TEXT NOT NULL,
              items_data TEXT NOT NULL,
              source_type TEXT NOT NULL,
              source_id INTEGER NOT NULL,
              created_at INTEGER NOT NULL,
              claimed_at INTEGER
            );
            CREATE INDEX IF NOT EXISTS idx_claims_owner ON claims(owner_uuid);
            CREATE TABLE IF NOT EXISTS sales_log (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              listing_id INTEGER NOT NULL,
              seller_uuid TEXT NOT NULL,
              buyer_uuid TEXT NOT NULL,
              item_data TEXT NOT NULL,
              outcome TEXT NOT NULL,
              accepted_at INTEGER NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_sales_log_seller ON sales_log(seller_uuid);
            CREATE INDEX IF NOT EXISTS idx_sales_log_buyer ON sales_log(buyer_uuid);
            CREATE TABLE IF NOT EXISTS balances (
              uuid TEXT PRIMARY KEY,
              balance REAL NOT NULL
            );
            CREATE TABLE IF NOT EXISTS notifications (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              player_uuid TEXT NOT NULL,
              message_key TEXT NOT NULL,
              created_at INTEGER NOT NULL,
              read_at INTEGER
            );
            CREATE INDEX IF NOT EXISTS idx_notifications_player ON notifications(player_uuid);
            """;

    @FunctionalInterface
    public interface Transaction<T> {
        T run(Connection c) throws SQLException;
    }
}
```

Note: `transactAll` splits on `;`. DDL contains no semicolons inside string literals, so the split is safe. The `import java.io.*`/`UncheckedIOException` are unused — remove them.

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :core:test --tests "dev.ah.core.db.SqliteDatabaseTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```powershell
git add core/src/main/java/dev/ah/core/db/SqliteDatabase.java core/src/test/java/dev/ah/core/db/SqliteDatabaseTest.java
git commit -m "feat(core): add SqliteDatabase with schema and transactions"
```

---

### Task 5: Core — `Listing` + `ListingStore`

**Files:**
- Create: `core/src/main/java/dev/ah/core/listing/Listing.java`
- Create: `core/src/main/java/dev/ah/core/listing/ListingStore.java`
- Create: `core/src/main/java/dev/ah/core/listing/SqlListingStore.java`
- Test: `core/src/test/java/dev/ah/core/listing/SqlListingStoreTest.java`

**Interfaces:**
- Produces:
```java
public record Listing(long id, UUID owner, String itemData, Double price, long durationMs,
                      long createdAt, long expiresAt, String status) {
    public boolean isActive();
}
public interface ListingStore {
    long create(Listing listing);
    java.util.Optional<Listing> byId(long id);
    java.util.List<Listing> activePage(int limit, int offset);           // newest first
    java.util.List<Listing> activePageOldest(int limit, int offset);     // oldest first
    java.util.List<Listing> byOwner(UUID owner, int limit, int offset);
    java.util.List<Listing> expiredActiveBefore(long nowMs);             // status ACTIVE, expires_at <= nowMs
    void updateStatus(long id, String status);
    long countActive();
    long countActiveBy(UUID owner);
}
```
`create` returns the generated id. Records come back fully populated.

- [ ] **Step 1: Write the failing test**

```java
package dev.ah.core.listing;

import dev.ah.core.db.SqliteDatabase;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SqlListingStoreTest {

    private SqlListingStore store(SqliteDatabase db) {
        db.init();
        return new SqlListingStore(db);
    }

    @Test
    void createAndFetchById() {
        SqliteDatabase db = SqliteDatabase.inMemory();
        SqlListingStore s = store(db);
        UUID owner = UUID.randomUUID();
        long id = s.create(new Listing(0, owner, "AAA", null, 86_400_000L, 1000L, 1000L + 86_400_000L, "ACTIVE"));
        Optional<Listing> found = s.byId(id);
        assertTrue(found.isPresent());
        assertEquals(owner, found.get().owner());
        assertEquals("AAA", found.get().itemData());
        assertEquals(1000L, found.get().createdAt());
        db.close();
    }

    @Test
    void activePageIsNewestFirstAndPaginates() {
        SqliteDatabase db = SqliteDatabase.inMemory();
        SqlListingStore s = store(db);
        for (int i = 1; i <= 5; i++) {
            s.create(new Listing(0, UUID.randomUUID(), "D" + i, null, 0, i, i + 1000, "ACTIVE"));
        }
        List<Listing> page1 = s.activePage(3, 0);
        List<Listing> page2 = s.activePage(3, 3);
        assertEquals("D5", page1.get(0).itemData());
        assertEquals(3, page1.size());
        assertEquals(2, page2.size());
        assertEquals("D2", page2.get(0).itemData());
        db.close();
    }

    @Test
    void activePageOldestFirst() {
        SqliteDatabase db = SqliteDatabase.inMemory();
        SqlListingStore s = store(db);
        for (int i = 1; i <= 3; i++) {
            s.create(new Listing(0, UUID.randomUUID(), "O" + i, null, 0, i, i + 1000, "ACTIVE"));
        }
        assertEquals("O1", s.activePageOldest(5, 0).get(0).itemData());
        db.close();
    }

    @Test
    void expiredActiveBeforeReturnsOnlyExpiredActive() {
        SqliteDatabase db = SqliteDatabase.inMemory();
        SqlListingStore s = store(db);
        s.create(new Listing(0, UUID.randomUUID(), "old", null, 0, 1, 100, "ACTIVE"));
        s.create(new Listing(0, UUID.randomUUID(), "new", null, 0, 1, 500, "ACTIVE"));
        s.create(new Listing(0, UUID.randomUUID(), "sold", null, 0, 1, 100, "SOLD"));
        List<Listing> expired = s.expiredActiveBefore(200L);
        assertEquals(1, expired.size());
        assertEquals("old", expired.get(0).itemData());
        db.close();
    }

    @Test
    void updateStatusAndCounts() {
        SqliteDatabase db = SqliteDatabase.inMemory();
        SqlListingStore s = store(db);
        UUID bob = UUID.randomUUID();
        long id = s.create(new Listing(0, bob, "AAA", null, 0, 1, 100, "ACTIVE"));
        s.create(new Listing(0, UUID.randomUUID(), "BBB", null, 0, 1, 100, "ACTIVE"));
        assertEquals(2, s.countActive());
        assertEquals(1, s.countActiveBy(bob));
        s.updateStatus(id, "CANCELLED");
        assertEquals(1, s.countActive());
        assertFalse(s.byId(id).get().isActive());
        db.close();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :core:test --tests "dev.ah.core.listing.SqlListingStoreTest"`
Expected: FAIL — classes not defined.

- [ ] **Step 3: Write the implementation**

`Listing.java`:
```java
package dev.ah.core.listing;

import java.util.UUID;

public record Listing(long id, UUID owner, String itemData, Double price, long durationMs,
                      long createdAt, long expiresAt, String status) {
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
}
```

`ListingStore.java`:
```java
package dev.ah.core.listing;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ListingStore {
    long create(Listing listing);
    Optional<Listing> byId(long id);
    List<Listing> activePage(int limit, int offset);
    List<Listing> activePageOldest(int limit, int offset);
    List<Listing> byOwner(UUID owner, int limit, int offset);
    List<Listing> expiredActiveBefore(long nowMs);
    void updateStatus(long id, String status);
    long countActive();
    long countActiveBy(UUID owner);
}
```

`SqlListingStore.java`:
```java
package dev.ah.core.listing;

import dev.ah.core.db.SqliteDatabase;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SqlListingStore implements ListingStore {
    private final SqliteDatabase db;

    public SqlListingStore(SqliteDatabase db) {
        this.db = db;
    }

    @Override
    public long create(Listing listing) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO listings (owner_uuid, item_data, price, duration_ms, created_at, expires_at, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?)""", new String[]{"id"})) {
                ps.setString(1, listing.owner().toString());
                ps.setString(2, listing.itemData());
                if (listing.price() == null) ps.setNull(3, java.sql.Types.DOUBLE); else ps.setDouble(3, listing.price());
                ps.setLong(4, listing.durationMs());
                ps.setLong(5, listing.createdAt());
                ps.setLong(6, listing.expiresAt());
                ps.setString(7, listing.status());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    return keys.getLong(1);
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public Optional<Listing> byId(long id) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM listings WHERE id = ?")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.empty();
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public List<Listing> activePage(int limit, int offset) {
        return query("SELECT * FROM listings WHERE status = 'ACTIVE' ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?", limit, offset);
    }

    @Override
    public List<Listing> activePageOldest(int limit, int offset) {
        return query("SELECT * FROM listings WHERE status = 'ACTIVE' ORDER BY created_at ASC, id ASC LIMIT ? OFFSET ?", limit, offset);
    }

    @Override
    public List<Listing> byOwner(UUID owner, int limit, int offset) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT * FROM listings WHERE owner_uuid = ? ORDER BY created_at DESC LIMIT ? OFFSET ?")) {
                ps.setString(1, owner.toString());
                ps.setInt(2, limit);
                ps.setInt(3, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    List<Listing> out = new ArrayList<>();
                    while (rs.next()) out.add(map(rs));
                    return out;
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public List<Listing> expiredActiveBefore(long nowMs) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT * FROM listings WHERE status = 'ACTIVE' AND expires_at <= ? ORDER BY expires_at ASC")) {
                ps.setLong(1, nowMs);
                try (ResultSet rs = ps.executeQuery()) {
                    List<Listing> out = new ArrayList<>();
                    while (rs.next()) out.add(map(rs));
                    return out;
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public void updateStatus(long id, String status) {
        db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE listings SET status = ? WHERE id = ?")) {
                ps.setString(1, status);
                ps.setLong(2, id);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    @Override
    public long countActive() {
        return db.transact(c -> {
            try (var ps = c.prepareStatement("SELECT COUNT(*) FROM listings WHERE status = 'ACTIVE'");
                 var rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public long countActiveBy(UUID owner) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM listings WHERE owner_uuid = ? AND status = 'ACTIVE'")) {
                ps.setString(1, owner.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getLong(1);
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private List<Listing> query(String sql, int limit, int offset) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, limit);
                ps.setInt(2, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    List<Listing> out = new ArrayList<>();
                    while (rs.next()) out.add(map(rs));
                    return out;
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private Listing map(ResultSet rs) throws SQLException {
        return new Listing(
                rs.getLong("id"),
                UUID.fromString(rs.getString("owner_uuid")),
                rs.getString("item_data"),
                rs.getObject("price") == null ? null : rs.getDouble("price"),
                rs.getLong("duration_ms"),
                rs.getLong("created_at"),
                rs.getLong("expires_at"),
                rs.getString("status"));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :core:test --tests "dev.ah.core.listing.SqlListingStoreTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```powershell
git add core/src/main/java/dev/ah/core/listing core/src/test/java/dev/ah/core/listing
git commit -m "feat(core): add Listing entity and SQLite store"
```

---

### Task 6: Core — `Offer` + `OfferStore`

**Files:**
- Create: `core/src/main/java/dev/ah/core/offer/Offer.java`
- Create: `core/src/main/java/dev/ah/core/offer/OfferStore.java`
- Create: `core/src/main/java/dev/ah/core/offer/SqlOfferStore.java`
- Test: `core/src/test/java/dev/ah/core/offer/SqlOfferStoreTest.java`

**Interfaces:**
- Produces:
```java
public record Offer(long id, long listingId, UUID offerer, String itemsData,
                    String status, long createdAt, Long decidedAt) {
    public boolean isPending();
}
public interface OfferStore {
    long create(Offer offer);
    java.util.Optional<Offer> byId(long id);
    java.util.List<Offer> byListing(long listingId);
    long countPendingByListing(long listingId);
    long countPendingForSeller(UUID sellerUuid);
    void updateStatusIfPending(long id, String newStatus, long decidedAt);
}
```
`updateStatusIfPending` only changes the row when its current status is `PENDING` (guards double-decisions).

- [ ] **Step 1: Write the failing test**

```java
package dev.ah.core.offer;

import dev.ah.core.db.SqliteDatabase;
import dev.ah.core.listing.Listing;
import dev.ah.core.listing.SqlListingStore;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SqlOfferStoreTest {

    @Test
    void createFetchListAndCounts() {
        SqliteDatabase db = SqliteDatabase.inMemory();
        db.init();
        SqlListingStore l = new SqlListingStore(db);
        SqlOfferStore o = new SqlOfferStore(db);
        UUID seller = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();
        long listingId = l.create(new Listing(0, seller, "AAA", null, 0, 1, 100, "ACTIVE"));
        long offerId = o.create(new Offer(0, listingId, buyer, "BBB", "PENDING", 5L, null));

        var offer = o.byId(offerId).orElseThrow();
        assertEquals(listingId, offer.listingId());
        assertEquals(buyer, offer.offerer());
        assertTrue(offer.isPending());

        assertEquals(1, o.countPendingByListing(listingId));
        assertEquals(1, o.countPendingForSeller(seller));

        o.updateStatusIfPending(offerId, "ACCEPTED", 99L);
        assertEquals("ACCEPTED", o.byId(offerId).get().status());
        assertEquals(99L, o.byId(offerId).get().decidedAt());
        assertEquals(0, o.countPendingByListing(listingId));
        assertEquals(0, o.countPendingForSeller(seller));

        o.updateStatusIfPending(offerId, "REJECTED", 100L); // no-op, already accepted
        assertEquals("ACCEPTED", o.byId(offerId).get().status());
        db.close();
    }

    @Test
    void listsOffersForListing() {
        SqliteDatabase db = SqliteDatabase.inMemory();
        db.init();
        SqlOfferStore o = new SqlOfferStore(db);
        long listingId = 7;
        o.create(new Offer(0, listingId, UUID.randomUUID(), "a", "PENDING", 1, null));
        o.create(new Offer(0, listingId, UUID.randomUUID(), "b", "PENDING", 2, null));
        o.create(new Offer(0, 8, UUID.randomUUID(), "c", "PENDING", 3, null));
        List<Offer> offers = o.byListing(listingId);
        assertEquals(2, offers.size());
        db.close();
    }

    @Test
    void unacceptedOfferIsStable() {
        SqliteDatabase db = SqliteDatabase.inMemory();
        db.init();
        SqlOfferStore o = new SqlOfferStore(db);
        long offerId = o.create(new Offer(0, 1, UUID.randomUUID(), "a", "PENDING", 1, null));
        o.updateStatusIfPending(offerId, "REJECTED", 2L);
        assertFalse(o.byId(offerId).get().isPending());
        db.close();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :core:test --tests "dev.ah.core.offer.SqlOfferStoreTest"`
Expected: FAIL — classes not defined.

- [ ] **Step 3: Write the implementation**

`Offer.java`:
```java
package dev.ah.core.offer;

import java.util.UUID;

public record Offer(long id, long listingId, UUID offerer, String itemsData,
                    String status, long createdAt, Long decidedAt) {
    public boolean isPending() {
        return "PENDING".equals(status);
    }
}
```

`OfferStore.java`:
```java
package dev.ah.core.offer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OfferStore {
    long create(Offer offer);
    Optional<Offer> byId(long id);
    List<Offer> byListing(long listingId);
    long countPendingByListing(long listingId);
    long countPendingForSeller(UUID sellerUuid);
    void updateStatusIfPending(long id, String newStatus, long decidedAt);
}
```

`SqlOfferStore.java`:
```java
package dev.ah.core.offer;

import dev.ah.core.db.SqliteDatabase;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SqlOfferStore implements OfferStore {
    private final SqliteDatabase db;

    public SqlOfferStore(SqliteDatabase db) {
        this.db = db;
    }

    @Override
    public long create(Offer offer) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO offers (listing_id, offerer_uuid, items_data, status, created_at, decided_at)
                    VALUES (?, ?, ?, ?, ?, ?)""", new String[]{"id"})) {
                ps.setLong(1, offer.listingId());
                ps.setString(2, offer.offerer().toString());
                ps.setString(3, offer.itemsData());
                ps.setString(4, offer.status());
                ps.setLong(5, offer.createdAt());
                if (offer.decidedAt() == null) ps.setNull(6, java.sql.Types.INTEGER); else ps.setLong(6, offer.decidedAt());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    return keys.getLong(1);
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public Optional<Offer> byId(long id) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM offers WHERE id = ?")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.empty();
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public List<Offer> byListing(long listingId) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT * FROM offers WHERE listing_id = ? ORDER BY created_at DESC, id DESC")) {
                ps.setLong(1, listingId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<Offer> out = new ArrayList<>();
                    while (rs.next()) out.add(map(rs));
                    return out;
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public long countPendingByListing(long listingId) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM offers WHERE listing_id = ? AND status = 'PENDING'")) {
                ps.setLong(1, listingId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getLong(1);
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public long countPendingForSeller(UUID sellerUuid) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT COUNT(*) FROM offers
                    WHERE status = 'PENDING'
                      AND listing_id IN (SELECT id FROM listings WHERE owner_uuid = ?)""")) {
                ps.setString(1, sellerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getLong(1);
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public void updateStatusIfPending(long id, String newStatus, long decidedAt) {
        db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE offers SET status = ?, decided_at = ? WHERE id = ? AND status = 'PENDING'")) {
                ps.setString(1, newStatus);
                ps.setLong(2, decidedAt);
                ps.setLong(3, id);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    private Offer map(ResultSet rs) throws SQLException {
        long decided = rs.getLong("decided_at");
        return new Offer(
                rs.getLong("id"),
                rs.getLong("listing_id"),
                UUID.fromString(rs.getString("offerer_uuid")),
                rs.getString("items_data"),
                rs.getString("status"),
                rs.getLong("created_at"),
                rs.wasNull() ? null : decided);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :core:test --tests "dev.ah.core.offer.SqlOfferStoreTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```powershell
git add core/src/main/java/dev/ah/core/offer core/src/test/java/dev/ah/core/offer
git commit -m "feat(core): add Offer entity and SQLite store"
```

---

### Task 7: Core — `ClaimRow` + `ClaimStore`

**Files:**
- Create: `core/src/main/java/dev/ah/core/claim/ClaimRow.java`
- Create: `core/src/main/java/dev/ah/core/claim/ClaimStore.java`
- Create: `core/src/main/java/dev/ah/core/claim/SqlClaimStore.java`
- Test: `core/src/test/java/dev/ah/core/claim/SqlClaimStoreTest.java`

**Interfaces:**
- Produces:
```java
public record ClaimRow(long id, UUID owner, String itemsData, String sourceType,
                       long sourceId, long createdAt, Long claimedAt) {}
public interface ClaimStore {
    long add(ClaimRow row);
    java.util.Optional<ClaimRow> byId(long id);
    java.util.List<ClaimRow> unclaimedFor(UUID owner);
    long countUnclaimed(UUID owner);
    void markClaimed(long id, long claimedAt);
}
```

- [ ] **Step 1: Write the failing test**

```java
package dev.ah.core.claim;

import dev.ah.core.db.SqliteDatabase;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SqlClaimStoreTest {

    @Test
    void addAndListUnclaimedForOwner() {
        SqliteDatabase db = SqliteDatabase.inMemory();
        db.init();
        SqlClaimStore s = new SqlClaimStore(db);
        UUID bob = UUID.randomUUID();
        s.add(new ClaimRow(0, bob, "ITEM1", "OFFER_REJECTED", 1, 10L, null));
        s.add(new ClaimRow(0, bob, "ITEM2", "LISTING_SOLD", 2, 11L, null));
        s.add(new ClaimRow(0, UUID.randomUUID(), "ITEM3", "OFFER_REJECTED", 3, 12L, null));

        assertEquals(2, s.countUnclaimed(bob));
        List<ClaimRow> rows = s.unclaimedFor(bob);
        assertEquals(2, rows.size());

        long id = rows.get(0).id();
        s.markClaimed(id, 99L);
        assertEquals(1, s.countUnclaimed(bob));
        assertEquals(99L, s.byId(id).get().claimedAt());
        db.close();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :core:test --tests "dev.ah.core.claim.SqlClaimStoreTest"`
Expected: FAIL — classes not defined.

- [ ] **Step 3: Write the implementation**

`ClaimRow.java`:
```java
package dev.ah.core.claim;

import java.util.UUID;

public record ClaimRow(long id, UUID owner, String itemsData, String sourceType,
                       long sourceId, long createdAt, Long claimedAt) {}
```

`ClaimStore.java`:
```java
package dev.ah.core.claim;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClaimStore {
    long add(ClaimRow row);
    Optional<ClaimRow> byId(long id);
    List<ClaimRow> unclaimedFor(UUID owner);
    long countUnclaimed(UUID owner);
    void markClaimed(long id, long claimedAt);
}
```

`SqlClaimStore.java`:
```java
package dev.ah.core.claim;

import dev.ah.core.db.SqliteDatabase;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SqlClaimStore implements ClaimStore {
    private final SqliteDatabase db;

    public SqlClaimStore(SqliteDatabase db) {
        this.db = db;
    }

    @Override
    public long add(ClaimRow row) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO claims (owner_uuid, items_data, source_type, source_id, created_at, claimed_at)
                    VALUES (?, ?, ?, ?, ?, ?)""", new String[]{"id"})) {
                ps.setString(1, row.owner().toString());
                ps.setString(2, row.itemsData());
                ps.setString(3, row.sourceType());
                ps.setLong(4, row.sourceId());
                ps.setLong(5, row.createdAt());
                if (row.claimedAt() == null) ps.setNull(6, java.sql.Types.INTEGER); else ps.setLong(6, row.claimedAt());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    return keys.getLong(1);
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public Optional<ClaimRow> byId(long id) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM claims WHERE id = ?")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.empty();
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public List<ClaimRow> unclaimedFor(UUID owner) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT * FROM claims WHERE owner_uuid = ? AND claimed_at IS NULL ORDER BY created_at DESC, id DESC")) {
                ps.setString(1, owner.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    List<ClaimRow> out = new ArrayList<>();
                    while (rs.next()) out.add(map(rs));
                    return out;
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public long countUnclaimed(UUID owner) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM claims WHERE owner_uuid = ? AND claimed_at IS NULL")) {
                ps.setString(1, owner.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getLong(1);
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public void markClaimed(long id, long claimedAt) {
        db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE claims SET claimed_at = ? WHERE id = ?")) {
                ps.setLong(1, claimedAt);
                ps.setLong(2, id);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    private ClaimRow map(ResultSet rs) throws SQLException {
        long claimed = rs.getLong("claimed_at");
        return new ClaimRow(
                rs.getLong("id"),
                UUID.fromString(rs.getString("owner_uuid")),
                rs.getString("items_data"),
                rs.getString("source_type"),
                rs.getLong("source_id"),
                rs.getLong("created_at"),
                rs.wasNull() ? null : claimed);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :core:test --tests "dev.ah.core.claim.SqlClaimStoreTest"`
Expected: PASS (1 test).

- [ ] **Step 5: Commit**

```powershell
git add core/src/main/java/dev/ah/core/claim core/src/test/java/dev/ah/core/claim
git commit -m "feat(core): add Claim store"
```

---

### Task 8: Core — `SalesLogStore`

**Files:**
- Create: `core/src/main/java/dev/ah/core/saleslog/SalesLogRow.java`
- Create: `core/src/main/java/dev/ah/core/saleslog/SalesLogStore.java`
- Create: `core/src/main/java/dev/ah/core/saleslog/SqlSalesLogStore.java`
- Test: `core/src/test/java/dev/ah/core/saleslog/SqlSalesLogStoreTest.java`

**Interfaces:**
- Produces:
```java
public record SalesLogRow(long id, long listingId, UUID seller, UUID buyer,
                          String itemData, String outcome, long acceptedAt) {}
public interface SalesLogStore {
    long add(SalesLogRow row);
    java.util.List<SalesLogRow> recent(int limit);
    java.util.List<SalesLogRow> bySeller(UUID seller, int limit);
    java.util.List<SalesLogRow> byBuyer(UUID buyer, int limit);
}
```

- [ ] **Step 1: Write the failing test**

```java
package dev.ah.core.saleslog;

import dev.ah.core.db.SqliteDatabase;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SqlSalesLogStoreTest {

    @Test
    void addAndQueryBySellerBuyerRecent() {
        SqliteDatabase db = SqliteDatabase.inMemory();
        db.init();
        SqlSalesLogStore s = new SqlSalesLogStore(db);
        UUID seller = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();
        s.add(new SalesLogRow(0, 1, seller, buyer, "SWORD", "ACCEPTED", 100L));
        s.add(new SalesLogRow(0, 2, seller, buyer, "PICK", "ACCEPTED", 200L));
        s.add(new SalesLogRow(0, 3, seller, UUID.randomUUID(), "AXE", "ACCEPTED", 300L));
        s.add(new SalesLogRow(0, 4, UUID.randomUUID(), buyer, "BOOTS", "ACCEPTED", 400L));

        assertEquals(4, s.recent(10).size());
        assertEquals(2, s.recent(2).size());
        assertEquals("AXE", s.recent(2).get(0).itemData());

        List<SalesLogRow> bySeller = s.bySeller(seller, 10);
        assertEquals(3, bySeller.size());

        List<SalesLogRow> byBuyer = s.byBuyer(buyer, 10);
        assertEquals(2, byBuyer.size());
        db.close();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :core:test --tests "dev.ah.core.saleslog.SqlSalesLogStoreTest"`
Expected: FAIL — classes not defined.

- [ ] **Step 3: Write the implementation**

`SalesLogRow.java`:
```java
package dev.ah.core.saleslog;

import java.util.UUID;

public record SalesLogRow(long id, long listingId, UUID seller, UUID buyer,
                          String itemData, String outcome, long acceptedAt) {}
```

`SalesLogStore.java`:
```java
package dev.ah.core.saleslog;

import java.util.List;
import java.util.UUID;

public interface SalesLogStore {
    long add(SalesLogRow row);
    List<SalesLogRow> recent(int limit);
    List<SalesLogRow> bySeller(UUID seller, int limit);
    List<SalesLogRow> byBuyer(UUID buyer, int limit);
}
```

`SqlSalesLogStore.java`:
```java
package dev.ah.core.saleslog;

import dev.ah.core.db.SqliteDatabase;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SqlSalesLogStore implements SalesLogStore {
    private final SqliteDatabase db;

    public SqlSalesLogStore(SqliteDatabase db) {
        this.db = db;
    }

    @Override
    public long add(SalesLogRow row) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO sales_log (listing_id, seller_uuid, buyer_uuid, item_data, outcome, accepted_at)
                    VALUES (?, ?, ?, ?, ?, ?)""", new String[]{"id"})) {
                ps.setLong(1, row.listingId());
                ps.setString(2, row.seller().toString());
                ps.setString(3, row.buyer().toString());
                ps.setString(4, row.itemData());
                ps.setString(5, row.outcome());
                ps.setLong(6, row.acceptedAt());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    return keys.getLong(1);
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private List<SalesLogRow> query(String sql, UUID uuid, int limit) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    List<SalesLogRow> out = new ArrayList<>();
                    while (rs.next()) out.add(map(rs));
                    return out;
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public List<SalesLogRow> recent(int limit) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM sales_log ORDER BY accepted_at DESC, id DESC LIMIT ?")) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    List<SalesLogRow> out = new ArrayList<>();
                    while (rs.next()) out.add(map(rs));
                    return out;
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public List<SalesLogRow> bySeller(UUID seller, int limit) {
        return query("SELECT * FROM sales_log WHERE seller_uuid = ? ORDER BY accepted_at DESC, id DESC LIMIT ?", seller, limit);
    }

    @Override
    public List<SalesLogRow> byBuyer(UUID buyer, int limit) {
        return query("SELECT * FROM sales_log WHERE buyer_uuid = ? ORDER BY accepted_at DESC, id DESC LIMIT ?", buyer, limit);
    }

    private SalesLogRow map(ResultSet rs) throws SQLException {
        return new SalesLogRow(
                rs.getLong("id"),
                rs.getLong("listing_id"),
                UUID.fromString(rs.getString("seller_uuid")),
                UUID.fromString(rs.getString("buyer_uuid")),
                rs.getString("item_data"),
                rs.getString("outcome"),
                rs.getLong("accepted_at"));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :core:test --tests "dev.ah.core.saleslog.SqlSalesLogStoreTest"`
Expected: PASS (1 test).

- [ ] **Step 5: Commit**

```powershell
git add core/src/main/java/dev/ah/core/saleslog core/src/test/java/dev/ah/core/saleslog
git commit -m "feat(core): add SalesLog store"
```

---

### Task 9: Core — `Notification` + `NotificationStore`

**Files:**
- Create: `core/src/main/java/dev/ah/core/notification/Notification.java`
- Create: `core/src/main/java/dev/ah/core/notification/NotificationStore.java`
- Create: `core/src/main/java/dev/ah/core/notification/SqlNotificationStore.java`
- Test: `core/src/test/java/dev/ah/core/notification/SqlNotificationStoreTest.java`

**Interfaces:**
- Produces:
```java
public record Notification(long id, UUID player, String messageKey, long createdAt, Long readAt) {}
public interface NotificationStore {
    long add(Notification n);
    java.util.List<Notification> unread(UUID player);
    void markRead(long id, long readAt);
}
```

- [ ] **Step 1: Write the failing test**

```java
package dev.ah.core.notification;

import dev.ah.core.db.SqliteDatabase;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SqlNotificationStoreTest {

    @Test
    void addUnreadAndMarkRead() {
        SqliteDatabase db = SqliteDatabase.inMemory();
        db.init();
        SqlNotificationStore s = new SqlNotificationStore(db);
        UUID p = UUID.randomUUID();
        long n1 = s.add(new Notification(0, p, "offer.accepted", 1L, null));
        s.add(new Notification(0, p, "offer.rejected", 2L, null));
        assertEquals(2, s.unread(p).size());
        s.markRead(n1, 9L);
        assertEquals(1, s.unread(p).size());
        db.close();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :core:test --tests "dev.ah.core.notification.SqlNotificationStoreTest"`
Expected: FAIL — classes not defined.

- [ ] **Step 3: Write the implementation**

`Notification.java`:
```java
package dev.ah.core.notification;

import java.util.UUID;

public record Notification(long id, UUID player, String messageKey, long createdAt, Long readAt) {}
```

`NotificationStore.java`:
```java
package dev.ah.core.notification;

import java.util.List;
import java.util.UUID;

public interface NotificationStore {
    long add(Notification n);
    List<Notification> unread(UUID player);
    void markRead(long id, long readAt);
}
```

`SqlNotificationStore.java`:
```java
package dev.ah.core.notification;

import dev.ah.core.db.SqliteDatabase;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SqlNotificationStore implements NotificationStore {
    private final SqliteDatabase db;

    public SqlNotificationStore(SqliteDatabase db) {
        this.db = db;
    }

    @Override
    public long add(Notification n) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO notifications (player_uuid, message_key, created_at, read_at)
                    VALUES (?, ?, ?, ?)""", new String[]{"id"})) {
                ps.setString(1, n.player().toString());
                ps.setString(2, n.messageKey());
                ps.setLong(3, n.createdAt());
                if (n.readAt() == null) ps.setNull(4, java.sql.Types.INTEGER); else ps.setLong(4, n.readAt());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    return keys.getLong(1);
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public List<Notification> unread(UUID player) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT * FROM notifications WHERE player_uuid = ? AND read_at IS NULL ORDER BY created_at ASC, id ASC")) {
                ps.setString(1, player.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    List<Notification> out = new ArrayList<>();
                    while (rs.next()) {
                        long read = rs.getLong("read_at");
                        out.add(new Notification(
                                rs.getLong("id"),
                                UUID.fromString(rs.getString("player_uuid")),
                                rs.getString("message_key"),
                                rs.getLong("created_at"),
                                rs.wasNull() ? null : read));
                    }
                    return out;
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public void markRead(long id, long readAt) {
        db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE notifications SET read_at = ? WHERE id = ?")) {
                ps.setLong(1, readAt);
                ps.setLong(2, id);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :core:test --tests "dev.ah.core.notification.SqlNotificationStoreTest"`
Expected: PASS (1 test).

- [ ] **Step 5: Commit**

```powershell
git add core/src/main/java/dev/ah/core/notification core/src/test/java/dev/ah/core/notification
git commit -m "feat(core): add Notification store"
```

---

### Task 10: Core — `OfferDecisionService`

**Files:**
- Create: `core/src/main/java/dev/ah/core/decision/OfferDecisionService.java`
- Test: `core/src/test/java/dev/ah/core/decision/OfferDecisionServiceTest.java`

**Interfaces:**
- Produces:
```java
public class OfferDecisionService {
    public enum Result { SUCCESS, NOT_FOUND, NOT_OWNER, NOT_PENDING, ERROR }
    public OfferDecisionService(ListingStore listings, OfferStore offers, ClaimStore claims, SalesLogStore sales);
    public Result accept(UUID actor, long offerId);
    public Result reject(UUID actor, long offerId);
}
```
Semantics — all in one DB transaction:
**accept:** if offer not found → `NOT_FOUND`; if not PENDING → `NOT_PENDING`; if actor ≠ listing owner → `NOT_OWNER`. Otherwise: claim `offer.itemsData` (seller, `OFFER_SOLD`, source=offer id), claim `listing.itemData` (offerer, `LISTING_SOLD`, source=listing id), sales log `(listing.id, seller, offerer, listing.itemData, "ACCEPTED", now)`, offer → `ACCEPTED`, listing → `SOLD`, and **every other PENDING offer on that listing** is auto-rejected with its items returned to its own offerer (`OFFER_REJECTED` claims).
**reject:** if not found → `NOT_FOUND`; not pending → `NOT_PENDING`; not owner → `NOT_OWNER`. Otherwise claim `offer.itemsData` (offerer, `OFFER_REJECTED`) and offer → `REJECTED`. Listing stays ACTIVE.
Any checked exception inside the transaction → `ERROR` with rollback.

- [ ] **Step 1: Write the failing test**

```java
package dev.ah.core.decision;

import dev.ah.core.claim.ClaimRow;
import dev.ah.core.claim.SqlClaimStore;
import dev.ah.core.db.SqliteDatabase;
import dev.ah.core.listing.Listing;
import dev.ah.core.listing.SqlListingStore;
import dev.ah.core.offer.Offer;
import dev.ah.core.offer.SqlOfferStore;
import dev.ah.core.saleslog.SqlSalesLogStore;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class OfferDecisionServiceTest {

    private record Fixture(SqliteDatabase db, SqlListingStore listings, SqlOfferStore offers,
                           SqlClaimStore claims, SqlSalesLogStore sales, OfferDecisionService svc) {}

    private Fixture fixture() {
        SqliteDatabase db = SqliteDatabase.inMemory();
        db.init();
        var listings = new SqlListingStore(db);
        var offers = new SqlOfferStore(db);
        var claims = new SqlClaimStore(db);
        var sales = new SqlSalesLogStore(db);
        return new Fixture(db, listings, offers, claims, sales,
                new OfferDecisionService(listings, offers, claims, sales));
    }

    @Test
    void acceptMovesItemsToClaimsAndClosesListing() {
        Fixture f = fixture();
        UUID seller = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();
        long listing = f.listings.create(new Listing(0, seller, "AUCTIONED", null, 0, 1, 999999, "ACTIVE"));
        long offer = f.offers.create(new Offer(0, listing, buyer, "PAYMENT", "PENDING", 2, null));

        assertEquals(OfferDecisionService.Result.SUCCESS, f.svc.accept(seller, offer));

        assertEquals("SOLD", f.listings.byId(listing).get().status());
        assertFalse(f.offers.byId(offer).get().isPending());

        List<ClaimRow> sellerClaims = f.claims.unclaimedFor(seller);
        List<ClaimRow> buyerClaims = f.claims.unclaimedFor(buyer);
        assertEquals(1, sellerClaims.size());
        assertEquals("PAYMENT", sellerClaims.get(0).itemsData());
        assertEquals("OFFER_SOLD", sellerClaims.get(0).sourceType());
        assertEquals(1, buyerClaims.size());
        assertEquals("AUCTIONED", buyerClaims.get(0).itemsData());
        assertEquals("LISTING_SOLD", buyerClaims.get(0).sourceType());

        assertEquals(1, f.sales.recent(10).size());
        assertEquals(seller, f.sales.recent(10).get(0).seller());
        assertEquals(buyer, f.sales.recent(10).get(0).buyer());
        db.close();
    }

    @Test
    void acceptAutoRejectsOtherPendingOffers() {
        Fixture f = fixture();
        UUID seller = UUID.randomUUID();
        UUID loser = UUID.randomUUID();
        UUID winner = UUID.randomUUID();
        long listing = f.listings.create(new Listing(0, seller, "AUCTIONED", null, 0, 1, 999999, "ACTIVE"));
        long losingOffer = f.offers.create(new Offer(0, listing, loser, "LOSS", "PENDING", 2, null));
        long winningOffer = f.offers.create(new Offer(0, listing, winner, "WIN", "PENDING", 3, null));

        assertEquals(OfferDecisionService.Result.SUCCESS, f.svc.accept(seller, winningOffer));

        assertFalse(f.offers.byId(losingOffer).get().isPending());
        assertEquals("REJECTED", f.offers.byId(losingOffer).get().status());
        assertEquals("LOSS", f.claims.unclaimedFor(loser).get(0).itemsData());
        assertEquals("OFFER_REJECTED", f.claims.unclaimedFor(loser).get(0).sourceType());
        db.close();
    }

    @Test
    void rejectReturnsItemsToOffererAndKeepsListingActive() {
        Fixture f = fixture();
        UUID seller = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();
        long listing = f.listings.create(new Listing(0, seller, "AUCTIONED", null, 0, 1, 999999, "ACTIVE"));
        long offer = f.offers.create(new Offer(0, listing, buyer, "PAYMENT", "PENDING", 2, null));

        assertEquals(OfferDecisionService.Result.SUCCESS, f.svc.reject(seller, offer));

        assertEquals("ACTIVE", f.listings.byId(listing).get().status());
        assertEquals("REJECTED", f.offers.byId(offer).get().status());
        assertEquals("PAYMENT", f.claims.unclaimedFor(buyer).get(0).itemsData());
        assertTrue(f.sales.recent(10).isEmpty());
        db.close();
    }

    @Test
    void nonOwnerCannotDecide() {
        Fixture f = fixture();
        UUID seller = UUID.randomUUID();
        UUID intruder = UUID.randomUUID();
        long listing = f.listings.create(new Listing(0, seller, "AUCTIONED", null, 0, 1, 999999, "ACTIVE"));
        long offer = f.offers.create(new Offer(0, listing, UUID.randomUUID(), "PAYMENT", "PENDING", 2, null));

        assertEquals(OfferDecisionService.Result.NOT_OWNER, f.svc.accept(intruder, offer));
        assertEquals(OfferDecisionService.Result.NOT_OWNER, f.svc.reject(intruder, offer));
        assertEquals("PENDING", f.offers.byId(offer).get().status());
        db.close();
    }

    @Test
    void doubleDecisionRejected() {
        Fixture f = fixture();
        UUID seller = UUID.randomUUID();
        long listing = f.listings.create(new Listing(0, seller, "AUCTIONED", null, 0, 1, 999999, "ACTIVE"));
        long offer = f.offers.create(new Offer(0, listing, UUID.randomUUID(), "PAYMENT", "PENDING", 2, null));

        assertEquals(OfferDecisionService.Result.SUCCESS, f.svc.accept(seller, offer));
        assertEquals(OfferDecisionService.Result.NOT_PENDING, f.svc.accept(seller, offer));
        db.close();
    }

    @Test
    void unknownOfferYieldsNotFound() {
        Fixture f = fixture();
        assertEquals(OfferDecisionService.Result.NOT_FOUND, f.svc.accept(UUID.randomUUID(), 12345L));
        db.close();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :core:test --tests "dev.ah.core.decision.OfferDecisionServiceTest"`
Expected: FAIL — class not defined.

- [ ] **Step 3: Make `SqliteDatabase.transact` reentrant (Task 4 file)**

The decision service calls several store methods (claims, offers, listings, sales) and each store method opens its own `db.transact(...)`. For atomicity, nested `transact` calls must join the outermost transaction instead of committing separately. Replace the `transact` method in `SqliteDatabase.java` with this reentrant, thread-scoped version and add the `inTx` field:

```java
    private final ThreadLocal<Boolean> inTx = ThreadLocal.withInitial(() -> false);

    public <T> T transact(Transaction<T> body) {
        synchronized (this) {
            boolean already = Boolean.TRUE.equals(inTx.get());
            if (!already) {
                try {
                    connection.setAutoCommit(false);
                    inTx.set(true);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
            try {
                T result = body.run(connection);
                if (!already) {
                    connection.commit();
                }
                return result;
            } catch (SQLException e) {
                if (!already) {
                    try { connection.rollback(); } catch (SQLException ignored) {}
                }
                throw new RuntimeException("database transaction failed", e);
            } finally {
                if (!already) {
                    inTx.set(false);
                    try { connection.setAutoCommit(true); } catch (SQLException ignored) {}
                }
            }
        }
    }
```

Nested calls reuse the outermost transaction; the outermost commits/rolls back. All writes run on the Bukkit main thread or the scheduled sweep task, so thread-scoping is safe.

- [ ] **Step 4: Write the `OfferDecisionService` implementation**

```java
package dev.ah.core.decision;

import dev.ah.core.claim.ClaimRow;
import dev.ah.core.claim.ClaimStore;
import dev.ah.core.listing.Listing;
import dev.ah.core.listing.ListingStore;
import dev.ah.core.offer.Offer;
import dev.ah.core.offer.OfferStore;
import dev.ah.core.saleslog.SalesLogRow;
import dev.ah.core.saleslog.SalesLogStore;
import java.util.Optional;
import java.util.UUID;

public class OfferDecisionService {
    private final ListingStore listings;
    private final OfferStore offers;
    private final ClaimStore claims;
    private final SalesLogStore sales;

    public OfferDecisionService(ListingStore listings, OfferStore offers, ClaimStore claims, SalesLogStore sales) {
        this.listings = listings;
        this.offers = offers;
        this.claims = claims;
        this.sales = sales;
    }

    public enum Result { SUCCESS, NOT_FOUND, NOT_OWNER, NOT_PENDING, ERROR }

    public Result accept(UUID actor, long offerId) {
        return decide(actor, offerId, true);
    }

    public Result reject(UUID actor, long offerId) {
        return decide(actor, offerId, false);
    }

    private Result decide(UUID actor, long offerId, boolean accept) {
        Optional<Offer> maybeOffer = offers.byId(offerId);
        if (maybeOffer.isEmpty()) return Result.NOT_FOUND;
        Offer offer = maybeOffer.get();
        Optional<Listing> maybeListing = listings.byId(offer.listingId());
        if (maybeListing.isEmpty()) return Result.NOT_FOUND;
        Listing listing = maybeListing.get();
        if (!listing.owner().equals(actor)) return Result.NOT_OWNER;
        if (!offer.isPending()) return Result.NOT_PENDING;

        try {
            long now = System.currentTimeMillis();
            if (accept) {
                // seller receives the offered items
                claims.add(new ClaimRow(0, listing.owner(), offer.itemsData(), "OFFER_SOLD", offer.id(), now, null));
                // buyer receives the auctioned items
                claims.add(new ClaimRow(0, offer.offerer(), listing.itemData(), "LISTING_SOLD", listing.id(), now, null));
                sales.add(new SalesLogRow(0, listing.id(), listing.owner(), offer.offerer(),
                        listing.itemData(), "ACCEPTED", now));
                offers.updateStatusIfPending(offer.id(), "ACCEPTED", now);
                listings.updateStatus(listing.id(), "SOLD");
                // auto-reject any other pending offers, returning their items
                for (Offer other : offers.byListing(listing.id())) {
                    if (other.id() != offer.id() && other.isPending()) {
                        claims.add(new ClaimRow(0, other.offerer(), other.itemsData(), "OFFER_REJECTED", other.id(), now, null));
                        offers.updateStatusIfPending(other.id(), "REJECTED", now);
                    }
                }
            } else {
                claims.add(new ClaimRow(0, offer.offerer(), offer.itemsData(), "OFFER_REJECTED", offer.id(), now, null));
                offers.updateStatusIfPending(offer.id(), "REJECTED", now);
            }
            return Result.SUCCESS;
        } catch (RuntimeException e) {
            return Result.ERROR;
        }
    }
}
```

Because every store method runs inside the shared reentrant `transact`, all reads/writes above commit or roll back together.

- [ ] **Step 5: Add reentrancy test to Task 10's test class**

Add `import java.sql.SQLException;` and this test to `OfferDecisionServiceTest`:

```java
    @Test
    void nestedTransactionsCommitTogether() {
        Fixture f = fixture();
        UUID bob = UUID.randomUUID();
        f.db.transact(c -> {
            f.claims.add(new ClaimRow(0, bob, "A", "T", 1, 1L, null));
            f.claims.add(new ClaimRow(0, bob, "B", "T", 1, 2L, null)); // inner add joins outer tx
            return null;
        });
        assertEquals(2, f.claims.countUnclaimed(bob)); // outer commit committed both

        assertThrows(RuntimeException.class, () -> f.db.transact(c -> {
            f.claims.add(new ClaimRow(0, bob, "C", "T", 1, 3L, null));
            throw new SQLException("boom");
        }));
        assertEquals(2, f.claims.countUnclaimed(bob)); // outer rollback undid inner writes
        db.close();
    }
```

- [ ] **Step 6: Run test to verify it passes**

Run: `.\gradlew.bat :core:test --tests "dev.ah.core.decision.OfferDecisionServiceTest" --tests "dev.ah.core.db.SqliteDatabaseTest"`
Expected: PASS — all decision + reentrancy tests.

- [ ] **Step 7: Commit**

```powershell
git add core/src/main/java/dev/ah/core/db/SqliteDatabase.java core/src/main/java/dev/ah/core/decision/OfferDecisionService.java core/src/test/java/dev/ah/core/decision/OfferDecisionServiceTest.java
git commit -m "feat(core): add atomic OfferDecisionService with reentrant transactions"
```

---

### Task 11: Core — `ExpirySweep`

**Files:**
- Create: `core/src/main/java/dev/ah/core/sweep/ExpirySweep.java`
- Test: `core/src/test/java/dev/ah/core/sweep/ExpirySweepTest.java`

**Interfaces:**
- Produces:
```java
public class ExpirySweep {
    public ExpirySweep(ListingStore listings, ClaimStore claims);
    public int sweep(long nowMs);   // returns number of listings expired
}
```
For each listing from `expiredActiveBefore(nowMs)`: mark `EXPIRED` and put `listing.itemData` into the owner's claim (`sourceType = "EXPIRED"`, source = listing.id). All inside one transaction per listing.

- [ ] **Step 1: Write the failing test**

```java
package dev.ah.core.sweep;

import dev.ah.core.claim.ClaimRow;
import dev.ah.core.claim.SqlClaimStore;
import dev.ah.core.db.SqliteDatabase;
import dev.ah.core.listing.Listing;
import dev.ah.core.listing.SqlListingStore;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ExpirySweepTest {

    @Test
    void expiresOverdueListingsAndClaimsThem() {
        SqliteDatabase db = SqliteDatabase.inMemory();
        db.init();
        var listings = new SqlListingStore(db);
        var claims = new SqlClaimStore(db);
        ExpirySweep sweep = new ExpirySweep(listings, claims);
        UUID owner = UUID.randomUUID();
        long l1 = listings.create(new Listing(0, owner, "EXPIRED_ITEM", null, 0, 1, 100, "ACTIVE"));
        listings.create(new Listing(0, owner, "FUTURE_ITEM", null, 0, 1, 9999, "ACTIVE"));

        assertEquals(1, sweep.sweep(500L));

        assertEquals("EXPIRED", listings.byId(l1).get().status());
        assertEquals(1, claims.countUnclaimed(owner));
        assertEquals("EXPIRED_ITEM", claims.unclaimedFor(owner).get(0).itemsData());
        assertEquals("EXPIRED", claims.unclaimedFor(owner).get(0).sourceType());
        db.close();
    }

    @Test
    void noOpWhenNothingExpired() {
        SqliteDatabase db = SqliteDatabase.inMemory();
        db.init();
        var listings = new SqlListingStore(db);
        var claims = new SqlClaimStore(db);
        listings.create(new Listing(0, UUID.randomUUID(), "FUTURE", null, 0, 1, 9999, "ACTIVE"));
        assertEquals(0, new ExpirySweep(listings, claims).sweep(1L));
        db.close();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :core:test --tests "dev.ah.core.sweep.ExpirySweepTest"`
Expected: FAIL — class not defined.

- [ ] **Step 3: Write the implementation**

```java
package dev.ah.core.sweep;

import dev.ah.core.claim.ClaimRow;
import dev.ah.core.claim.ClaimStore;
import dev.ah.core.listing.Listing;
import dev.ah.core.listing.ListingStore;

public class ExpirySweep {
    private final ListingStore listings;
    private final ClaimStore claims;

    public ExpirySweep(ListingStore listings, ClaimStore claims) {
        this.listings = listings;
        this.claims = claims;
    }

    public int sweep(long nowMs) {
        int count = 0;
        for (Listing listing : listings.expiredActiveBefore(nowMs)) {
            listings.updateStatus(listing.id(), "EXPIRED");
            claims.add(new ClaimRow(0, listing.owner(), listing.itemData(), "EXPIRED", listing.id(), nowMs, null));
            count++;
        }
        return count;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :core:test --tests "dev.ah.core.sweep.ExpirySweepTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```powershell
git add core/src/main/java/dev/ah/core/sweep/ExpirySweep.java core/src/test/java/dev/ah/core/sweep/ExpirySweepTest.java
git commit -m "feat(core): add ExpirySweep"
```

---

### Task 12: Core — Economy skeleton

**Files:**
- Create: `core/src/main/java/dev/ah/core/economy/EconomyService.java`
- Create: `core/src/main/java/dev/ah/core/economy/SqlBalanceStore.java`
- Test: `core/src/test/java/dev/ah/core/economy/SqlBalanceStoreTest.java`

**Interfaces:**
- Produces:
```java
public interface EconomyService {
    java.util.OptionalDouble balance(UUID player);
    boolean take(UUID player, double amount);      // false if insufficient
    void give(UUID player, double amount);
    JavaPlugin? NONE — plugin-independent.
}
public class SqlBalanceStore {
    public SqlBalanceStore(SqliteDatabase db);
    public java.util.OptionalDouble balance(UUID player);
    public void set(UUID player, double amount);
    public boolean tryTake(UUID player, double amount);  // atomic check+subtract
}
```
This is the skeleton for the disabled-by-default economy; nothing in auction-house calls it while `economy.enabled` is false.

- [ ] **Step 1: Write the failing test**

```java
package dev.ah.core.economy;

import dev.ah.core.db.SqliteDatabase;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SqlBalanceStoreTest {

    @Test
    void setBalanceTryTakeAndGive() {
        SqliteDatabase db = SqliteDatabase.inMemory();
        db.init();
        SqlBalanceStore s = new SqlBalanceStore(db);
        UUID p = UUID.randomUUID();
        assertTrue(s.balance(p).isEmpty());
        s.set(p, 1000.0);
        assertEquals(1000.0, s.balance(p).getAsDouble(), 0.001);
        assertTrue(s.tryTake(p, 400.0));
        assertEquals(600.0, s.balance(p).getAsDouble(), 0.001);
        assertFalse(s.tryTake(p, 601.0));
        assertEquals(600.0, s.balance(p).getAsDouble(), 0.001);
        db.close();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :core:test --tests "dev.ah.core.economy.SqlBalanceStoreTest"`
Expected: FAIL — classes not defined.

- [ ] **Step 3: Write the implementation**

```java
package dev.ah.core.economy;

import dev.ah.core.db.SqliteDatabase;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.OptionalDouble;
import java.util.UUID;

public class SqlBalanceStore {
    private final SqliteDatabase db;

    public SqlBalanceStore(SqliteDatabase db) {
        this.db = db;
    }

    public OptionalDouble balance(UUID player) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT balance FROM balances WHERE uuid = ?")) {
                ps.setString(1, player.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? OptionalDouble.of(rs.getDouble("balance")) : OptionalDouble.empty();
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void set(UUID player, double amount) {
        db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO balances (uuid, balance) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET balance = excluded.balance")) {
                ps.setString(1, player.toString());
                ps.setDouble(2, amount);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    public boolean tryTake(UUID player, double amount) {
        return db.transact(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE balances SET balance = balance - ? WHERE uuid = ? AND balance >= ?")) {
                ps.setDouble(1, amount);
                ps.setString(2, player.toString());
                ps.setDouble(3, amount);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
```

`EconomyService.java`:
```java
package dev.ah.core.economy;

import java.util.OptionalDouble;
import java.util.UUID;

/**
 * Optional economy facade. Default installation uses the disabled path:
 * auction-house behaves as pure item trading. Implementations are wired only
 * when config economy.enabled is true.
 */
public interface EconomyService {
    OptionalDouble balance(UUID player);
    boolean take(UUID player, double amount);
    void give(UUID player, double amount);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :core:test --tests "dev.ah.core.economy.SqlBalanceStoreTest"`
Expected: PASS (1 test).

- [ ] **Step 5: Commit**

```powershell
git add core/src/main/java/dev/ah/core/economy core/src/test/java/dev/ah/core/economy
git commit -m "feat(core): add economy skeleton (disabled by default)"
```

---

### Task 13: Core — Messaging (`MessageRepository`, `SoundRegistry`)

**Files:**
- Create: `core/src/main/java/dev/ah/core/msg/MessageRepository.java`
- Create: `core/src/main/java/dev/ah/core/msg/SoundRegistry.java`

**Interfaces:**
- Produces:
```java
public class MessageRepository {
    public MessageRepository(File langFile);
    public String get(String key);                                     // raw MiniMessage string
    public String get(String key, Map<String, String> placeholders);   // {key} replaced, then returned raw
    public net.kyori.adventure.text.Component parse(Player player, String raw); // MiniMessage parse
    public java.util.Set<String> keys();
}
public class SoundRegistry {
    public enum Event { OPEN, CLICK, OFFER_RECEIVED, OFFER_ACCEPTED, OFFER_REJECTED, SALE, EXPIRY, ERROR }
    public SoundRegistry(File soundsFile);
    public void play(Player player, Event event);   // no-op if disabled
}
```
File formats: `lang.yml` maps message keys → MiniMessage strings; `sounds.yml` maps event name → `{enabled, sound, pitch, volume}`. Both load with Bukkit `YamlConfiguration` at startup. `parse` uses `MiniMessage.miniMessage().deserialize(raw)`; placeholders are substituted before parsing, and Adventure serializes fine for both Java and Bedrock (Geyser respects standard chat component text).

- [ ] **Step 1: Write `MessageRepository`**

```java
package dev.ah.core.msg;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import java.io.File;
import java.util.Map;
import java.util.Set;

public class MessageRepository {
    private final YamlConfiguration lang;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public MessageRepository(File langFile) {
        this.lang = YamlConfiguration.loadConfiguration(langFile);
    }

    public String get(String key) {
        String value = lang.getString(key);
        return value == null ? "<red>Missing message: " + key : value;
    }

    public String get(String key, Map<String, String> placeholders) {
        String value = get(key);
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            value = value.replace("{" + e.getKey() + "}", e.getValue());
        }
        return value;
    }

    public Component parse(Player player, String raw) {
        return MM.parse(raw);
    }

    public Set<String> keys() {
        return lang.getKeys(false);
    }
}
```

- [ ] **Step 2: Write `SoundRegistry`**

```java
package dev.ah.core.msg;

import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import java.io.File;
import java.util.EnumMap;
import java.util.Map;

public class SoundRegistry {
    public enum Event { OPEN, CLICK, OFFER_RECEIVED, OFFER_ACCEPTED, OFFER_REJECTED, SALE, EXPIRY, ERROR }

    private final Map<Event, Entry> entries = new EnumMap<>(Event.class);

    public record Entry(boolean enabled, String sound, float pitch, float volume) {}

    public SoundRegistry(File soundsFile) {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(soundsFile);
        for (Event event : Event.values()) {
            String path = event.name().toLowerCase();
            boolean enabled = cfg.getBoolean(path + ".enabled", true);
            String sound = cfg.getString(path + ".sound", event == Event.ERROR
                    ? Sound.ENTITY_VILLAGER_NO.name() : Sound.UI_BUTTON_CLICK.name());
            float pitch = (float) cfg.getDouble(path + ".pitch", 1.0);
            float volume = (float) cfg.getDouble(path + ".volume", 1.0);
            entries.put(event, new Entry(enabled, sound, pitch, volume));
        }
    }

    public void play(Player player, Event event) {
        Entry e = entries.get(event);
        if (e == null || !e.enabled()) return;
        try {
            Sound sound = Sound.valueOf(e.sound());
            player.playSound(player.getLocation(), sound, e.volume(), e.pitch());
        } catch (IllegalArgumentException ignored) {
            // bad sound name in config — ignore, no crash
        }
    }
}
```

- [ ] **Step 3: Verify compile**

Run: `.\gradlew.bat :core:compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```powershell
git add core/src/main/java/dev/ah/core/msg
git commit -m "feat(core): add MessageRepository and SoundRegistry"
```

---

### Task 14: Core — GUI framework (`ChestGui`, `DepositGui`, `GuiManager`)

**Files:**
- Create: `core/src/main/java/dev/ah/core/gui/ChestGui.java`
- Create: `core/src/main/java/dev/ah/core/gui/DepositGui.java`
- Create: `core/src/main/java/dev/ah/core/gui/GuiManager.java`

**Interfaces:**
- Produces:
```java
public class ChestGui {
    public record Click(Player player, int slot) {}
    public record Slot(ItemStack item, Consumer<Click> action) {}
    public ChestGui(int rows, String title);
    public ChestGui title(String title);
    public ChestGui set(int slot, ItemStack item);
    public ChestGui on(int slot, Runnable action);                               // convenience for normal clicks
    public ChestGui on(int slot, Consumer<Click> action);
    public void fill(ItemStack filler);
    public void fillRect(int fromSlot, int toSlot, ItemStack filler);
    public void open(Player player);
    public Inventory inventory();
    public String title();
    public Map<Integer, Slot> slots();
}
public class DepositGui extends ChestGui {
    public DepositGui(int rows, String title, int minSlot, int maxSlot);          // inclusive deposit area
    public void open(Player player);                                               // ALSO opens a placeholder "Confirm / Cancel" bottom row pattern
    public List<ItemStack> collect();                                              // items in deposit area at confirm time
    public boolean cancelAndReturn(Player player);                                 // return deposit items back to player inventory, close
}
public class GuiManager {
    public GuiManager(JavaPlugin plugin);
    public void register(ChestGui gui, Inventory inventory);
    public void unregister(Inventory inventory);
    public void onClose(InventoryCloseEvent event);
    public void onClick(InventoryClickEvent event);   // cancels, dispatches to gui slot action
    public void onPlayerQuit(PlayerQuitEvent event);  // returns any DepositGui items
}
```
`GuiManager.register` maps `Inventory → ChestGui`. MenuListener (Task 18) delegates the three events here. `ChestGui` uses a static global registry keyed by `Inventory` identity via `WeakHashMap`.

- [ ] **Step 1: Write `ChestGui`**

```java
package dev.ah.core.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ChestGui {
    public record Click(Player player, int slot) {}
    public record Slot(ItemStack item, Consumer<Click> action) {}

    private static final Map<Inventory, ChestGui> OPEN = new ConcurrentHashMap<>();

    private final int rows;
    private final Map<Integer, Slot> slots = new LinkedHashMap<>();
    private Inventory inventory;
    private Component title;

    public ChestGui(int rows, String title) {
        this.rows = rows;
        this.title = Component.text(title);
    }

    public ChestGui title(String title) {
        this.title = Component.text(title);
        return this;
    }

    public ChestGui set(int slot, ItemStack item) {
        slots.put(slot, new Slot(item, clk -> {}));
        return this;
    }

    public ChestGui on(int slot, Runnable action) {
        return on(slot, clk -> action.run());
    }

    public ChestGui on(int slot, Consumer<Click> action) {
        slots.put(slot, new Slot(slots.containsKey(slot) ? slots.get(slot).item() : null, action));
        return this;
    }

    public void fill(ItemStack filler) {
        for (int i = 0; i < rows * 9; i++) {
            slots.putIfAbsent(i, new Slot(filler, clk -> {}));
        }
    }

    public void fillRect(int fromSlot, int toSlot, ItemStack filler) {
        for (int i = fromSlot; i <= toSlot; i++) {
            slots.putIfAbsent(i, new Slot(filler, clk -> {}));
        }
    }

    public void open(Player player) {
        if (inventory == null) {
            inventory = Bukkit.createInventory(null, rows * 9, title);
            for (Map.Entry<Integer, Slot> e : slots.entrySet()) {
                inventory.setItem(e.getKey(), e.getValue().item());
            }
        }
        OPEN.put(inventory, this);
        player.openInventory(inventory);
    }

    public Inventory inventory() {
        return inventory;
    }

    public String title() {
        return title.toString();
    }

    public Map<Integer, Slot> slots() {
        return Collections.unmodifiableMap(slots);
    }

    public static ChestGui of(Inventory inventory) {
        return OPEN.get(inventory);
    }

    public static void close(Inventory inventory) {
        OPEN.remove(inventory);
    }
}
```

- [ ] **Step 2: Write `DepositGui`**

```java
package dev.ah.core.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.List;

public class DepositGui extends ChestGui {
    private final int minSlot;
    private final int maxSlot;
    private boolean confirmed;

    public DepositGui(int rows, String title, int minSlot, int maxSlot) {
        super(rows, title);
        this.minSlot = minSlot;
        this.maxSlot = maxSlot;
        this.confirmed = false;
    }

    @Override
    public void open(Player player) {
        super.open(player);
        // the window we own; deposit area has no default filler
    }

    public List<ItemStack> collect() {
        List<ItemStack> out = new ArrayList<>();
        if (inventory() == null) return out;
        for (int i = minSlot; i <= maxSlot; i++) {
            ItemStack item = inventory().getItem(i);
            if (item != null && item.getType() != Material.AIR) out.add(item.clone());
        }
        return out;
    }

    public boolean cancelAndReturn(Player player) {
        if (inventory() == null) return false;
        for (int i = minSlot; i <= maxSlot; i++) {
            ItemStack item = inventory().getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                player.getInventory().addItem(item);
            }
        }
        player.closeInventory();
        return true;
    }

    public void markConfirmed() {
        this.confirmed = true;
    }

    public boolean isConfirmed() {
        return confirmed;
    }
}
```

- [ ] **Step 3: Write `GuiManager`**

```java
package dev.ah.core.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.concurrent.ConcurrentHashMap;

public class GuiManager {
    private static final ConcurrentHashMap<Player, DepositGui> OPEN_DEPOSITS = new ConcurrentHashMap<>();

    public void registerOpen(Player player, DepositGui gui) {
        OPEN_DEPOSITS.put(player, gui);
    }

    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player p) {
            OPEN_DEPOSITS.remove(p);
        }
        if (event.getInventory() != null) ChestGui.close(event.getInventory());
    }

    public void onClick(InventoryClickEvent event) {
        Inventory inv = event.getClickedInventory();
        if (inv == null) return;
        ChestGui gui = ChestGui.of(inv);
        if (gui == null) return;
        event.setCancelled(true);
        int slot = event.getSlot();
        var slotDef = gui.slots().get(slot);
        if (slotDef != null && slotDef.action() != null && event.getWhoClicked() instanceof Player p) {
            slotDef.action().accept(new ChestGui.Click(p, slot));
        }
    }

    public void onPlayerQuit(Player player) {
        DepositGui gui = OPEN_DEPOSITS.remove(player);
        if (gui != null) gui.cancelAndReturn(player);
    }
}
```

- [ ] **Step 4: Verify compile**

Run: `.\gradlew.bat :core:compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```powershell
git add core/src/main/java/dev/ah/core/gui
git commit -m "feat(core): add ChestGui, DepositGui, GuiManager"
```

---

### Task 15: Core — `PlatformDetector` (Geyser)

**Files:**
- Create: `core/src/main/java/dev/ah/core/geyser/PlatformDetector.java`

**Interfaces:**
- Produces:
```java
public class PlatformDetector {
    public PlatformDetector();                       // probes Floodgate once
    public boolean isBedrock(Player player);         // false if Floodgate absent
}
```
Uses reflection so the plugin runs without Floodgate installed (Geyser-compatibility via chest GUIs, not API calls).

- [ ] **Step 1: Write the implementation**

```java
package dev.ah.core.geyser;

import org.bukkit.entity.Player;
import java.lang.reflect.Method;
import java.util.UUID;

public class PlatformDetector {
    private final Method floodgatePlayerMethod;

    public PlatformDetector() {
        Method method = null;
        try {
            Class<?> api = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            method = api.getMethod("isFloodgatePlayer", UUID.class);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            // Floodgate not installed — Java-only fallback
        }
        this.floodgatePlayerMethod = method;
    }

    public boolean isBedrock(Player player) {
        if (floodgatePlayerMethod == null) return false;
        try {
            Object api = Class.forName("org.geysermc.floodgate.api.FloodgateApi")
                    .getMethod("getInstance").invoke(null);
            return (boolean) floodgatePlayerMethod.invoke(api, player.getUniqueId());
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `.\gradlew.bat :core:compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```powershell
git add core/src/main/java/dev/ah/core/geyser/PlatformDetector.java
git commit -m "feat(core): add Floodgate PlatformDetector"
```

---

### Task 16: AuctionHouse — Plugin shell, configs, `AhServices`

**Files:**
- Create: `auction-house/src/main/resources/plugin.yml`
- Create: `auction-house/src/main/resources/config.yml`
- Create: `auction-house/src/main/resources/lang.yml`
- Create: `auction-house/src/main/resources/gui.yml`
- Create: `auction-house/src/main/resources/sounds.yml`
- Create: `auction-house/src/main/java/dev/ah/auctionhouse/AhServices.java`
- Create: `auction-house/src/main/java/dev/ah/auctionhouse/AuctionHousePlugin.java`

**Interfaces:**
- Produces:
```java
public class AhServices {
    public AhServices(JavaPlugin plugin);
    public ListingStore listings();
    public OfferStore offers();
    public ClaimStore claims();
    public SalesLogStore sales();
    public NotificationStore notifications();
    public OfferDecisionService decisions();
    public ExpirySweep sweep();
    public MessageRepository messages();
    public SoundRegistry sounds();
    public GuiManager gui();
    public PlatformDetector platform();
    public boolean isEconomyEnabled();
    public long defaultDurationMs();
    public List<Long> allowedDurationsMs();
    public int maxListingsPerPlayer();
    public int maxItemsPerOffer();
    public long sweepIntervalMs();
}
```
`AuctionHousePlugin.onEnable()`: copy default resources (`lang.yml`, `gui.yml`, `sounds.yml` if missing), load `config.yml`, open `plugins/AuctionHouse/data.db`, `db.init()`, build `AhServices`, register `AhCommand` (main + tab completer), register `MenuListener` + `JoinNotifier`, schedule `ExpirySweep` sync-repeating every `sweep-interval-seconds`. `onDisable()`: cancel task, `db.close()`.

- [ ] **Step 1: Write `plugin.yml`**

```yaml
name: AuctionHouse
version: 1.0.0
main: dev.ah.auctionhouse.AuctionHousePlugin
api-version: "1.21"
commands:
  ah:
    description: Auction House
    aliases: [auctionhouse]
permissions:
  ah.use:
    description: Use the auction house
    default: true
  ah.admin:
    description: Admin auction house features
    default: op
```

- [ ] **Step 2: Write `config.yml`**

```yaml
economy:
  enabled: false
limits:
  max-listings-per-player: 5
  max-items-per-offer: 54
durations-ms:
  - 86400000        # 1 day
  - 604800000       # 7 days
  - 2592000000      # 30 days
default-duration-ms: 86400000
sweep-interval-seconds: 60
page-size: 36
```

- [ ] **Step 3: Write `lang.yml`** (subset; extend freely)

```yaml
prefix: "<gold>[AH]</gold> "
commands:
  help: "Available: sell, search <item>, my, claim, admin, reload"
checks:
  cannot-offer-self: "<red>You cannot offer on your own listing."
  max-listings: "<red>You have reached the listing limit."
  max-offer-items: "<red>Too many items in this offer."
  invalid-items: "<red>You must deposit at least one item."
  listing-expired: "<red>This listing has expired."
listings:
  created: "<green>Listing created."
  cancelled: "<green>Listing cancelled; items sent to your claims."
  expired: "<yellow>One of your listings expired and its items are in your claims."
offers:
  made: "<green>Offer sent."
  received: "<yellow>You have a new offer on one of your listings."
  accepted-by-seller: "<green>Your offer was accepted. Claim your item(s)!"
  rejected-by-seller: "<red>Your offer was rejected."
  accepted-seller: "<green>You accepted the offer."
  rejected-seller: "<green>You rejected the offer."
claims:
  unclaimed: "<yellow>You have <count> unclaimed item(s). Use /ah claim."
  empty: "<gray>No unclaimed items."
  withdrawn: "<green>Items withdrawn."
  no-space: "<red>Your inventory is full. Some items stayed in claims."
admin:
  no-permission: "<red>You do not have permission."
  unclear: "<red>Select a user first."
errors:
  economy: "<red>Economy is disabled."
  unknown: "<red>Something went wrong."
```

- [ ] **Step 4: Write `gui.yml`**

```yaml
main:
  title: "Auctions"
  rows: 6
  listing-area: "9-44"
sell:
  title: "List an item"
  rows: 4
  deposit-area: "9-17"
offer:
  title: "Make an offer"
  rows: 4
  deposit-area: "9-17"
offer-board:
  title: "Offers"
  rows: 6
claim:
  title: "Claim items"
  rows: 6
admin:
  title: "Admin"
  rows: 6
fill-item: "BLACK_STAINED_GLASS_PANE"
confirm-slot: 39
cancel-slot: 41
```

- [ ] **Step 5: Write `sounds.yml`**

```yaml
open:     { enabled: true,  sound: "UI_BUTTON_CLICK", pitch: 1.0, volume: 0.6 }
click:    { enabled: true,  sound: "UI_BUTTON_CLICK", pitch: 1.0, volume: 0.6 }
offer_received:   { enabled: true,  sound: "ENTITY_EXPERIENCE_ORB_PICKUP", pitch: 1.2, volume: 0.8 }
offer_accepted:   { enabled: true,  sound: "ENTITY_PLAYER_LEVELUP", pitch: 1.0, volume: 0.8 }
offer_rejected:   { enabled: true,  sound: "BLOCK_NOTE_BLOCK_BASS", pitch: 0.7, volume: 0.8 }
sale:     { enabled: true,  sound: "ENTITY_PLAYER_LEVELUP", pitch: 1.4, volume: 0.8 }
expiry:   { enabled: true,  sound: "ENTITY_ITEM_PICKUP", pitch: 0.8, volume: 0.8 }
error:    { enabled: true,  sound: "ENTITY_VILLAGER_NO", pitch: 1.0, volume: 0.8 }
```

- [ ] **Step 6: Write `AhServices`**

```java
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
    private final long sweepIntervalMs;
    private final int pageSize;

    public AhServices(JavaPlugin plugin) {
        File dataFolder = plugin.getDataFolder();
        File conf = new File(dataFolder, "config.yml");
        plugin.saveResource("config.yml", false);
        plugin.saveResource("lang.yml", false);
        plugin.saveResource("gui.yml", false);
        plugin.saveResource("sounds.yml", false);
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(conf);

        this.db = new SqliteDatabase(new File(dataFolder, "data.db"));
        db.init();
        this.listings = new SqlListingStore(db);
        this.offers = new SqlOfferStore(db);
        this.claims = new SqlClaimStore(db);
        this.sales = new SqlSalesLogStore(db);
        this.notifications = new SqlNotificationStore(db);
        this.decisions = new OfferDecisionService(listings, offers, claims, sales);
        this.sweep = new ExpirySweep(listings, claims);
        this.messages = new MessageRepository(new File(dataFolder, "lang.yml"));
        this.sounds = new SoundRegistry(new File(dataFolder, "sounds.yml"));
        this.gui = new GuiManager();
        this.platform = new PlatformDetector();

        this.economyEnabled = cfg.getBoolean("economy.enabled", false);
        this.maxListingsPerPlayer = cfg.getInt("limits.max-listings-per-player", 5);
        this.maxItemsPerOffer = cfg.getInt("limits.max-items-per-offer", 54);
        this.defaultDurationMs = cfg.getLong("default-duration-ms", 86400000L);
        this.allowedDurationsMs = cfg.getLongList("durations-ms");
        this.sweepIntervalMs = cfg.getLong("sweep-interval-seconds", 60L) * 1000L;
        this.pageSize = cfg.getInt("page-size", 36);
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
    public long sweepIntervalMs() { return sweepIntervalMs; }
    public int pageSize() { return pageSize; }
}
```

- [ ] **Step 7: Write `AuctionHousePlugin`**

```java
package dev.ah.auctionhouse;

import dev.ah.auctionhouse.command.AhCommand;
import dev.ah.auctionhouse.listener.JoinNotifier;
import dev.ah.auctionhouse.listener.MenuListener;
import org.bukkit.plugin.java.JavaPlugin;

public class AuctionHousePlugin extends JavaPlugin {
    private AhServices services;
    private org.bukkit.scheduler.BukkitTask sweepTask;

    @Override
    public void onEnable() {
        services = new AhServices(this);
        AhCommand command = new AhCommand(services);
        getCommand("ah").setExecutor(command);
        getCommand("ah").setTabCompleter(command);
        getServer().getPluginManager().registerEvents(new MenuListener(services.gui()), this);
        getServer().getPluginManager().registerEvents(new JoinNotifier(services), this);

        long interval = services.sweepIntervalMs();
        sweepTask = getServer().getScheduler().runTaskTimer(this, () -> {
            int expired = services.sweep().sweep(System.currentTimeMillis());
            if (expired > 0) getLogger().info(expired + " listings expired");
        }, interval, interval);

        getLogger().info("AuctionHouse enabled. Economy=" + (services.isEconomyEnabled() ? "ON" : "OFF (item trading)"));
    }

    @Override
    public void onDisable() {
        if (sweepTask != null) sweepTask.cancel();
        if (services != null) services.close();
    }

    public AhServices services() {
        return services;
    }
}
```

- [ ] **Step 8: Verify compile**

Run: `.\gradlew.bat :auction-house:compileJava`
Expected: `BUILD SUCCESSFUL` (MenuListener, JoinNotifier, AhCommand do not exist yet — add temporary empty stubs or defer; **implement stubs now**):

```java
// auction-house/src/main/java/dev/ah/auctionhouse/command/AhCommand.java
package dev.ah.auctionhouse.command;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import java.util.List;

public class AhCommand implements CommandExecutor, TabCompleter {
    private final dev.ah.auctionhouse.AhServices services;
    public AhCommand(dev.ah.auctionhouse.AhServices services) { this.services = services; }
    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) { return true; }
    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) { return List.of(); }
}
```

```java
// auction-house/src/main/java/dev/ah/auctionhouse/listener/MenuListener.java
package dev.ah.auctionhouse.listener;
import dev.ah.core.gui.GuiManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class MenuListener implements Listener {
    private final GuiManager gui;
    public MenuListener(GuiManager gui) { this.gui = gui; }
    @EventHandler public void onClick(InventoryClickEvent e) { gui.onClick(e); }
    @EventHandler public void onClose(InventoryCloseEvent e) { gui.onClose(e); }
    @EventHandler public void onQuit(PlayerQuitEvent e) { gui.onPlayerQuit(e.getPlayer()); }
}
```

```java
// auction-house/src/main/java/dev/ah/auctionhouse/listener/JoinNotifier.java
package dev.ah.auctionhouse.listener;
import dev.ah.auctionhouse.AhServices;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class JoinNotifier implements Listener {
    private final AhServices services;
    public JoinNotifier(AhServices services) { this.services = services; }
    @EventHandler public void onJoin(PlayerJoinEvent e) { /* Task 21 */ }
}
```

- [ ] **Step 9: Commit**

```powershell
git add auction-house
git commit -m "feat(ah): plugin shell, configs, AhServices, main class"
```

---

### Task 17: AuctionHouse — Listing & offer creation verbs (`AhActions`)

**Files:**
- Create: `auction-house/src/main/java/dev/ah/auctionhouse/AhActions.java`

**Interfaces:**
- Produces:
```java
public class AhActions {
    public enum CreateResult { SUCCESS, LIMIT_REACHED, NO_ITEMS, INVALID }
    public enum OfferResult { SUCCESS, NOT_ACTIVE, SELF_OFFER, TOO_MANY_ITEMS, NO_ITEMS, LIMIT_REACHED }
    public AhActions(AhServices services);
    public CreateResult createListing(Player seller, List<ItemStack> items, long durationMs);
    public OfferResult makeOffer(Player buyer, long listingId, List<ItemStack> items);
}
```
`createListing` encodes the bundle via `ItemBundleCodec.encode`, respects `maxListingsPerPlayer` and non-empty items, stores duration from allowed set (defaults to `defaultDurationMs`, clamps to min/allowed). `makeOffer` blocks self-offer, non-active listing, empty bundle, bundle > `maxItemsPerOffer`, and claim-limit (config: `max-offers-per-player` default 10, added to config). Set created_at/expires_at from `System.currentTimeMillis()`.

- [ ] **Step 1: Write `AhActions`**

```java
package dev.ah.auctionhouse;

import dev.ah.core.listing.Listing;
import dev.ah.core.misc.ItemBundleCodec;
import dev.ah.core.offer.Offer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.List;
import java.util.Optional;

public class AhActions {
    private final AhServices services;

    public AhActions(AhServices services) {
        this.services = services;
    }

    public enum CreateResult { SUCCESS, LIMIT_REACHED, NO_ITEMS, INVALID }
    public enum OfferResult { SUCCESS, NOT_ACTIVE, SELF_OFFER, TOO_MANY_ITEMS, NO_ITEMS }

    public CreateResult createListing(Player seller, List<ItemStack> items, long durationMs) {
        List<ItemStack> clean = items == null ? List.of() : items.stream()
                .filter(i -> i != null && !i.getType().isAir()).toList();
        if (clean.isEmpty()) return CreateResult.NO_ITEMS;
        if (services.listings().countActiveBy(seller.getUniqueId()) >= services.maxListingsPerPlayer()) {
            return CreateResult.LIMIT_REACHED;
        }
        long now = System.currentTimeMillis();
        long expires = now + services.allowedDurationsMs().stream()
                .filter(d -> d <= durationMs).max(Long::compareTo).orElse(services.defaultDurationMs());
        services.listings().create(new Listing(0, seller.getUniqueId(), ItemBundleCodec.encode(clean), null, expires - now, now, expires, "ACTIVE"));
        return CreateResult.SUCCESS;
    }

    public OfferResult makeOffer(Player buyer, long listingId, List<ItemStack> items) {
        List<ItemStack> clean = items == null ? List.of() : items.stream()
                .filter(i -> i != null && !i.getType().isAir()).toList();
        if (clean.isEmpty()) return OfferResult.NO_ITEMS;
        if (clean.size() > services.maxItemsPerOffer()) return OfferResult.TOO_MANY_ITEMS;
        Optional<Listing> listing = services.listings().byId(listingId);
        if (listing.isEmpty() || !listing.get().isActive()) return OfferResult.NOT_ACTIVE;
        if (listing.get().owner().equals(buyer.getUniqueId())) return OfferResult.SELF_OFFER;
        long now = System.currentTimeMillis();
        services.offers().create(new Offer(0, listingId, buyer.getUniqueId(), ItemBundleCodec.encode(clean), "PENDING", now, null));
        return OfferResult.SUCCESS;
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `.\gradlew.bat :auction-house:compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```powershell
git add auction-house/src/main/java/dev/ah/auctionhouse/AhActions.java auction-house/src/main/resources/config.yml
git commit -m "feat(ah): add listing and offer creation verbs"
```

---

### Task 18: AuctionHouse — Claim GUI + withdrawal

**Files:**
- Create: `auction-house/src/main/java/dev/ah/auctionhouse/gui/ClaimGui.java`
- Modify: `auction-house/src/main/java/dev/ah/auctionhouse/AhServices.java` (expose `pageSize` via `int pageSize()`)

**Interfaces:**
- Produces:
```java
public class ClaimGui {
    public ClaimGui(AhServices services);
    public void open(Player player, int page);
}
```
Renders `ClaimRow`s for `player` into a paged `ChestGui` (page size from `services.pageSize()`), using `ItemBundleCodec.decode(row.itemsData()).get(0)` as the icon (first item), lore with source + time. Clicking a row: withdraw — decode all items, add to player inventory if space, else keep in claim and send `claims.no-space`; on full success `claims.markClaimed`. Filler fills empty cells.

- [ ] **Step 1: Write `ClaimGui`**

```java
package dev.ah.auctionhouse.gui;

import dev.ah.auctionhouse.AhServices;
import dev.ah.core.claim.ClaimRow;
import dev.ah.core.gui.ChestGui;
import dev.ah.core.misc.ItemBundleCodec;
import dev.ah.core.msg.SoundRegistry;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class ClaimGui {
    private final AhServices services;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public ClaimGui(AhServices services) {
        this.services = services;
    }

    public void open(Player player, int page) {
        List<ClaimRow> rows = services.claims().unclaimedFor(player.getUniqueId());
        int perPage = services.pageSize();
        var pager = new dev.ah.core.misc.Pager<>(rows, perPage);
        int totalPages = pager.pages();

        final int pageIndex;
        if (page < 0) pageIndex = 0;
        else if (page >= totalPages) pageIndex = totalPages - 1;
        else pageIndex = page;

        ChestGui gui = new ChestGui(6, services.messages().get("claims.title", Map.of("page", String.valueOf(pageIndex + 1), "pages", String.valueOf(totalPages))));
        gui.fill(new ItemStack(Material.valueOf(services.getGuiFiller()), 1));
        gui.fillRect(9, 44, null);

        int slot = 9;
        for (ClaimRow row : pager.page(pageIndex)) {
            if (slot > 44) break;
            List<ItemStack> items = ItemBundleCodec.decode(row.itemsData());
            if (items.isEmpty()) { slot++; continue; }
            ItemStack icon = items.get(0).clone();
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(MM.parse(services.messages().get("claims.row", Map.of(
                    "count", String.valueOf(items.size()),
                    "date", new SimpleDateFormat("MMM d HH:mm").format(new Date(row.createdAt()))))));
            meta.lore(items.size() > 1 ? List.of(MM.parse(services.messages().get("claims.more", Map.of("extra", String.valueOf(items.size() - 1))))) : List.of());
            icon.setItemMeta(meta);
            final int rowId = row.id();
            gui.on(slot, clk -> withdraw(player, rowId, row.itemsData())).set(slot, icon);
            slot++;
        }

        gui.on(53, () -> open(player, pageIndex + 1));
        gui.on(45, () -> open(player, pageIndex - 1));
        gui.on(49, () -> player.closeInventory());
        services.sounds().play(player, SoundRegistry.Event.OPEN);
        gui.open(player);
    }

    private void withdraw(Player player, long rowId, String itemsData) {
        List<ItemStack> items = ItemBundleCodec.decode(itemsData);
        boolean allFit = true;
        for (ItemStack item : items) {
            var leftover = player.getInventory().addItem(item);
            if (!leftover.isEmpty()) allFit = false;
        }
        if (allFit) {
            services.claims().markClaimed(rowId, System.currentTimeMillis());
            player.sendMessage(MM.parse(services.messages().get("claims.withdrawn")));
            services.sounds().play(player, SoundRegistry.Event.CLICK);
            open(player, 0);
        } else {
            player.sendMessage(MM.parse(services.messages().get("claims.no-space")));
            services.sounds().play(player, SoundRegistry.Event.ERROR);
        }
    }
}
```

- [ ] **Step 2: Add `getGuiFiller` and `pageSize`, `claims.title`, `claims.row`, `claims.more` to services/config/lang**

`AhServices`:
```java
private final String guiFiller;
...
this.guiFiller = cfg.getString("fill-item", "BLACK_STAINED_GLASS_PANE");
public String getGuiFiller() { return guiFiller; }
```
`config.yml` add `max-offers-per-player: 10` (used in Task 19). `lang.yml` add:
```yaml
claims:
  title: "<gold>Claims <gray>(page <page>/<pages>)"
  row: "<gold>Claim <gray>· <date>"
  more: "<gray>+<extra> more item(s)"
  withdrawn: "<green>Items withdrawn."
  empty: "<gray>No unclaimed items."
  no-space: "<red>Your inventory is full."
```

- [ ] **Step 3: Verify compile**

Run: `.\gradlew.bat :auction-house:compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```powershell
git add auction-house/src/main/java/dev/ah/auctionhouse/gui/ClaimGui.java auction-house/src/main/java/dev/ah/auctionhouse/AhServices.java auction-house/src/main/resources
git commit -m "feat(ah): add ClaimGui with withdrawal"
```

---

### Task 19: AuctionHouse — Offer & Sell GUIs (deposit flows)

**Files:**
- Create: `auction-house/src/main/java/dev/ah/auctionhouse/gui/OfferGui.java`
- Create: `auction-house/src/main/java/dev/ah/auctionhouse/gui/SellGui.java`

**Interfaces:**
- Produces:
```java
public class SellGui {
    public SellGui(AhServices services);
    public void open(Player player);
}
public class OfferGui {
    public OfferGui(AhServices services, long listingId);
    public void open(Player player);
}
```
Both are `DepositGui`-backed: deposit area slots `9-17`; a confirm button (`CONFIRM_SLOT` from gui.yml, default 39) and cancel (`CANCEL_SLOT`, default 41). On confirm: `collect()`, invoke `AhActions`, handle result messages + sounds, then return the deposit items to the player's inventory when the action fails (so items never vanish); on cancel/close, `cancelAndReturn`. `OfferGui` shows the listing item icon in a preview slot.

- [ ] **Step 1: Write `SellGui`**

```java
package dev.ah.auctionhouse.gui;

import dev.ah.auctionhouse.AhActions;
import dev.ah.auctionhouse.AhServices;
import dev.ah.core.gui.DepositGui;
import dev.ah.core.msg.SoundRegistry;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.List;
import java.util.Map;

public class SellGui {
    private final AhServices services;
    private final AhActions actions;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public SellGui(AhServices services) {
        this.services = services;
        this.actions = new AhActions(services);
    }

    public void open(Player player) {
        DepositGui gui = new DepositGui(4, services.messages().get("sell.title"), 9, 17);
        gui.fill(new ItemStack(Material.valueOf(services.getGuiFiller()), 1));
        gui.fillRect(9, 17, null);
        gui.set(0, new ItemStack(Material.NAME_TAG, 1));
        gui.on(39, () -> onConfirm(player, gui));
        gui.on(41, () -> onCancel(player, gui));
        gui.set(39, confirmItem("CONFIRM"));
        gui.set(41, cancelItem("CANCEL"));
        services.gui().registerOpen(player, gui);
        services.sounds().play(player, SoundRegistry.Event.OPEN);
        gui.open(player);
    }

    private void onConfirm(Player player, DepositGui gui) {
        List<ItemStack> items = gui.collect();
        long duration = services.defaultDurationMs();
        AhActions.CreateResult r = actions.createListing(player, items, duration);
        if (r == AhActions.CreateResult.SUCCESS) {
            player.sendMessage(MM.parse(services.messages().get("listings.created")));
            services.sounds().play(player, SoundRegistry.Event.SALE);
            player.closeInventory();
        } else {
            player.sendMessage(MM.parse(services.messages().get(r == AhActions.CreateResult.LIMIT_REACHED ? "checks.max-listings" : "checks.invalid-items")));
            gui.cancelAndReturn(player);
        }
    }

    private void onCancel(Player player, DepositGui gui) {
        gui.cancelAndReturn(player);
    }

    private ItemStack confirmItem(String label) {
        ItemStack i = new ItemStack(Material.LIME_DYE, 1);
        i.editMeta(m -> m.displayName(MM.parse("<green>" + label)));
        return i;
    }

    private ItemStack cancelItem(String label) {
        ItemStack i = new ItemStack(Material.BARRIER, 1);
        i.editMeta(m -> m.displayName(MM.parse("<red>" + label)));
        return i;
    }
}
```

- [ ] **Step 2: Write `OfferGui`** (same skeleton; the confirm path calls `actions.makeOffer(player, listingId, items)` and shows `offers.made`/error messages with `cancelAndReturn` on failure). Complete code:

```java
package dev.ah.auctionhouse.gui;

import dev.ah.auctionhouse.AhActions;
import dev.ah.auctionhouse.AhServices;
import dev.ah.core.gui.DepositGui;
import dev.ah.core.listing.Listing;
import dev.ah.core.misc.ItemBundleCodec;
import dev.ah.core.msg.SoundRegistry;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.List;

public class OfferGui {
    private final AhServices services;
    private final long listingId;
    private final Listing listing;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public OfferGui(AhServices services, long listingId) {
        this.services = services;
        this.listingId = listingId;
        this.listing = services.listings().byId(listingId).orElse(null);
    }

    public void open(Player player) {
        if (listing == null || !listing.isActive()) {
            player.sendMessage(MM.parse(services.messages().get("checks.listing-expired")));
            return;
        }
        DepositGui gui = new DepositGui(4, services.messages().get("offer.title"), 9, 17);
        gui.fill(new ItemStack(Material.valueOf(services.getGuiFiller()), 1));
        gui.fillRect(9, 17, null);
        List<ItemStack> preview = ItemBundleCodec.decode(listing.itemData());
        if (!preview.isEmpty()) gui.set(0, preview.get(0).clone());
        gui.on(39, () -> onConfirm(player, gui));
        gui.on(41, () -> gui.cancelAndReturn(player));
        gui.set(39, new ItemStack(Material.LIME_DYE, 1));
        gui.set(41, new ItemStack(Material.BARRIER, 1));
        services.gui().registerOpen(player, gui);
        services.sounds().play(player, SoundRegistry.Event.OPEN);
        gui.open(player);
    }

    private void onConfirm(Player player, DepositGui gui) {
        List<ItemStack> items = gui.collect();
        AhActions.OfferResult r = new AhActions(services).makeOffer(player, listingId, items);
        if (r == AhActions.OfferResult.SUCCESS) {
            player.sendMessage(MM.parse(services.messages().get("offers.made")));
            services.sounds().play(player, SoundRegistry.Event.CLICK);
            player.closeInventory();
        } else {
            String key = switch (r) {
                case NOT_ACTIVE -> "checks.listing-expired";
                case SELF_OFFER -> "checks.cannot-offer-self";
                case TOO_MANY_ITEMS -> "checks.max-offer-items";
                case NO_ITEMS -> "checks.invalid-items";
                default -> "errors.unknown";
            };
            player.sendMessage(MM.parse(services.messages().get(key)));
            gui.cancelAndReturn(player);
        }
    }
}
```

- [ ] **Step 3: Verify compile**

Run: `.\gradlew.bat :auction-house:compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```powershell
git add auction-house/src/main/java/dev/ah/auctionhouse/gui/SellGui.java auction-house/src/main/java/dev/ah/auctionhouse/gui/OfferGui.java
git commit -m "feat(ah): add SellGui and OfferGui deposit flows"
```

---

### Task 20: AuctionHouse — Main menu, Offer board, Admin GUI

**Files:**
- Create: `auction-house/src/main/java/dev/ah/auctionhouse/gui/AhMainGui.java`
- Create: `auction-house/src/main/java/dev/ah/auctionhouse/gui/OfferBoardGui.java`
- Create: `auction-house/src/main/java/dev/ah/auctionhouse/gui/AdminGui.java`

**Interfaces:**
- Produces:
```java
public class AhMainGui { public AhMainGui(AhServices services); public void open(Player player); public void openSearch(Player player, String term); public void openSorted(Player player, String sort, String search, int page); }
public class OfferBoardGui { public OfferBoardGui(AhServices services, long listingId, Player viewer); public void open(); }
public class AdminGui { public AdminGui(AhServices services); public void open(Player admin); public void openSalesLog(Player admin); }
```
`AhMainGui`: 6 rows, listing area `9-44`, icons from `ItemBundleCodec.decode`, lore includes price (only if economy enabled), expiry, pending offer count; sort state `newest`/`oldest`; search term filters via `String.contains` (case-insensitive) on item display name; prev/next/close buttons; top row shows help. `OfferBoardGui`: left column (row 2, slots 18-22) shows the auctioned item(s); right column (slots 24-35) shows the lowest offer's item(s); buttons Accept (slot 36) / Reject (slot 38) / Open-item (slot 40); Open-item opens a plain read-only `ChestGui` with the offer items; clicking Accept/Reject calls `services.decisions()`. `AdminGui`: navigates users → their listings → OfferBoardGui; Sales Log tab renders `sales.recent(36)` rows.

- [ ] **Step 1: Write `OfferBoardGui`** (the core screen)

```java
package dev.ah.auctionhouse.gui;

import dev.ah.auctionhouse.AhServices;
import dev.ah.core.decision.OfferDecisionService;
import dev.ah.core.gui.ChestGui;
import dev.ah.core.listing.Listing;
import dev.ah.core.misc.ItemBundleCodec;
import dev.ah.core.msg.SoundRegistry;
import dev.ah.core.offer.Offer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.List;

public class OfferBoardGui {
    private final AhServices services;
    private final long listingId;
    private final Player viewer;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public OfferBoardGui(AhServices services, long listingId, Player viewer) {
        this.services = services;
        this.listingId = listingId;
        this.viewer = viewer;
    }

    public void open() {
        Listing listing = services.listings().byId(listingId).orElse(null);
        if (listing == null) return;
        ChestGui gui = new ChestGui(6, services.messages().get("offer-board.title"));
        gui.fill(new ItemStack(Material.valueOf(services.getGuiFiller()), 1));

        List<ItemStack> auctioned = ItemBundleCodec.decode(listing.itemData());
        for (int i = 0; i < Math.min(auctioned.size(), 5) && i < 5; i++) {
            gui.set(18 + i, auctioned.get(i).clone());
        }

        List<Offer> pending = services.offers().byListing(listingId).stream()
                .filter(Offer::isPending)
                .toList();
        if (!pending.isEmpty()) {
            Offer offer = pending.get(0);
            List<ItemStack> offered = ItemBundleCodec.decode(offer.itemsData());
            for (int i = 0; i < Math.min(offered.size(), 12) && i < 12; i++) {
                gui.set(24 + i, offered.get(i).clone());
            }
            long offerId = offer.id();
            if (viewer.getUniqueId().equals(listing.owner()) || viewer.hasPermission("ah.admin")) {
                gui.on(36, () -> decide(offerId, true)).set(36, new ItemStack(Material.LIME_DYE, 1));
                gui.on(38, () -> decide(offerId, false)).set(38, new ItemStack(Material.RED_DYE, 1));
                gui.on(40, () -> openItems(offer)).set(40, new ItemStack(Material.DIAMOND, 1));
            }
        }
        services.sounds().play(viewer, SoundRegistry.Event.OPEN);
        gui.open(viewer);
    }

    private void decide(long offerId, boolean accept) {
        OfferDecisionService.Result r = accept
                ? services.decisions().accept(viewer.getUniqueId(), offerId)
                : services.decisions().reject(viewer.getUniqueId(), offerId);
        if (r == OfferDecisionService.Result.SUCCESS) {
            viewer.sendMessage(MM.parse(services.messages().get(accept ? "offers.accepted-seller" : "offers.rejected-seller")));
            services.sounds().play(viewer, SoundRegistry.Event.OFFER_ACCEPTED);
        } else {
            services.sounds().play(viewer, SoundRegistry.Event.ERROR);
        }
        open();
    }

    private void openItems(Offer offer) {
        ChestGui viewerGui = new ChestGui(6, services.messages().get("offer-board.items-viewer"));
        viewerGui.fill(new ItemStack(Material.valueOf(services.getGuiFiller()), 1));
        List<ItemStack> items = ItemBundleCodec.decode(offer.itemsData());
        for (int i = 0; i < Math.min(items.size(), 45); i++) {
            viewerGui.set(i, items.get(i).clone());
        }
        viewerGui.open(viewer);
    }
}
```

- [ ] **Step 2: Write `AhMainGui`**

```java
package dev.ah.auctionhouse.gui;

import dev.ah.auctionhouse.AhServices;
import dev.ah.core.gui.ChestGui;
import dev.ah.core.listing.Listing;
import dev.ah.core.misc.ItemBundleCodec;
import dev.ah.core.misc.Pager;
import dev.ah.core.msg.SoundRegistry;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AhMainGui {
    private final AhServices services;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private String sort = "newest";
    private String search = "";

    public AhMainGui(AhServices services) {
        this.services = services;
    }

    public void open(Player player) {
        open(player, 0);
    }

    public void openSearch(Player player, String term) {
        this.search = term == null ? "" : term.toLowerCase(Locale.ROOT);
        open(player, 0);
    }

    public void openSorted(Player player, String sort, String search, int page) {
        this.sort = sort;
        this.search = search == null ? null : search.toLowerCase(Locale.ROOT);
        open(player, page);
    }

    private void open(Player player, int page) {
        List<Listing> active = sort.equals("oldest")
                ? services.listings().activePageOldest(10_000, 0)
                : services.listings().activePage(10_000, 0);
        List<Listing> filtered = active.stream()
                .filter(l -> {
                    if (search == null || search.isBlank()) return true;
                    String name = ItemBundleCodec.decode(l.itemData()).stream()
                            .map(ItemStack::getDisplayName).reduce("", String::concat);
                    return name.toLowerCase(Locale.ROOT).contains(search);
                })
                .toList();

        Pager<Listing> pager = new Pager<>(filtered, services.pageSize());
        int pageIndex = Math.max(0, Math.min(page, pager.pages() - 1));

        ChestGui gui = new ChestGui(6, services.messages().get("gui.main.title"));
        gui.fill(new ItemStack(Material.valueOf(services.getGuiFiller()), 1));
        gui.fillRect(9, 44, null);

        int slot = 9;
        for (Listing listing : pager.page(pageIndex)) {
            if (slot > 44) break;
            List<ItemStack> items = ItemBundleCodec.decode(listing.itemData());
            if (items.isEmpty()) { slot++; continue; }
            ItemStack icon = items.get(0).clone();
            ItemMeta meta = icon.getItemMeta();
            long offers = services.offers().countPendingByListing(listing.id());
            String price = services.isEconomyEnabled() && listing.price() != null
                    ? String.valueOf(listing.price()) : "—";
            meta.lore(List.of(
                    MM.parse(services.messages().get("listings.lore.price", Map.of("price", price))),
                    MM.parse(services.messages().get("listings.lore.offers", Map.of("count", String.valueOf(offers)))),
                    MM.parse(services.messages().get("listings.lore.remaining", Map.of("minutes", String.valueOf((listing.expiresAt() - System.currentTimeMillis()) / 60000))))));
            icon.setItemMeta(meta);
            long listingId = listing.id();
            gui.on(slot, clk -> { player.closeInventory(); new OfferGui(services, listingId).open(clk.player()); });
            gui.set(slot, icon);
            slot++;
        }

        gui.on(53, () -> open(player, pageIndex + 1)).set(53, arrow("NEXT"));
        gui.on(45, () -> open(player, pageIndex - 1)).set(45, arrow("PREV"));
        gui.on(49, () -> toggleSort()).set(49, new ItemStack(Material.COMPASS, 1));
        gui.on(47, clk -> {
            player.closeInventory();
            player.performCommand("ah search ");
        }).set(47, new ItemStack(Material.OAK_SIGN, 1));
        gui.on(50, () -> new dev.ah.auctionhouse.gui.ClaimGui(services).open(player, 0)).set(50, new ItemStack(Material.CHEST, 1));
        services.sounds().play(player, SoundRegistry.Event.OPEN);
        gui.open(player);
    }

    private void toggleSort() {
        sort = sort.equals("newest") ? "oldest" : "newest";
    }

    private ItemStack arrow(String label) {
        ItemStack i = new ItemStack(label.equals("NEXT") ? Material.ARROW : Material.ARROW, 1);
        i.editMeta(m -> m.displayName(MM.parse("<gray>" + label)));
        return i;
    }
}
```

- [ ] **Step 3: Write `AdminGui`** — user index + sales log tab

```java
package dev.ah.auctionhouse.gui;

import dev.ah.auctionhouse.AhServices;
import dev.ah.core.gui.ChestGui;
import dev.ah.core.listing.Listing;
import dev.ah.core.misc.ItemBundleCodec;
import dev.ah.core.msg.SoundRegistry;
import dev.ah.core.saleslog.SalesLogRow;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AdminGui {
    private final AhServices services;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public AdminGui(AhServices services) {
        this.services = services;
    }

    public void open(Player admin) {
        ChestGui gui = new ChestGui(6, services.messages().get("admin.title"));
        gui.fill(new ItemStack(Material.valueOf(services.getGuiFiller()), 1));
        gui.fillRect(9, 44, null);

        Set<UUID> players = new HashSet<>();
        for (Listing l : services.listings().activePage(10_000, 0)) players.add(l.owner());
        int slot = 9;
        for (UUID uuid : players) {
            if (slot > 44) break;
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            String name = op.getName() == null ? uuid.toString().substring(0, 8) : op.getName();
            ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
            head.editMeta(meta -> meta.displayName(MM.parse("<gold>" + name)));
            gui.on(slot, clk -> openUser(admin, uuid)).set(slot, head);
            slot++;
        }

        gui.on(49, () -> openSalesLog(admin)).set(49, new ItemStack(Material.BOOK, 1));
        services.sounds().play(admin, SoundRegistry.Event.OPEN);
        gui.open(admin);
    }

    private void openUser(Player admin, UUID uuid) {
        ChestGui gui = new ChestGui(6, services.messages().get("admin.user.title"));
        gui.fill(new ItemStack(Material.valueOf(services.getGuiFiller()), 1));
        gui.fillRect(9, 44, null);
        int slot = 9;
        for (Listing l : services.listings().byOwner(uuid, 10_000, 0)) {
            if (slot > 44) break;
            ItemStack icon = ItemBundleCodec.decode(l.itemData()).get(0).clone();
            icon.editMeta(m -> m.lore(List.of(MM.parse(services.messages().get("admin.user.listing", Map.of(
                    "id", String.valueOf(l.id()),
                    "status", l.status(),
                    "offers", String.valueOf(services.offers().countPendingByListing(l.id()))))))));
            long listingId = l.id();
            gui.on(slot, clk -> new OfferBoardGui(services, listingId, admin).open());
            gui.set(slot, icon);
            slot++;
        }
        gui.open(admin);
    }

    /** Reused by /ah my — shows the player's own listings. */
    public void openMyListings(Player player) {
        openUser(player, player.getUniqueId());
    }

    private void openSalesLog(Player admin) {
        ChestGui gui = new ChestGui(6, services.messages().get("admin.sales.title"));
        gui.fill(new ItemStack(Material.valueOf(services.getGuiFiller()), 1));
        gui.fillRect(9, 44, null);
        int slot = 9;
        for (SalesLogRow row : services.sales().recent(36)) {
            if (slot > 44) break;
            String seller = Bukkit.getOfflinePlayer(row.seller()).getName();
            String buyer = Bukkit.getOfflinePlayer(row.buyer()).getName();
            ItemStack icon = ItemBundleCodec.decode(row.itemData()).get(0).clone();
            icon.editMeta(m -> m.lore(List.of(MM.parse(services.messages().get("admin.sales.row", Map.of(
                    "seller", seller == null ? "?" : seller,
                    "buyer", buyer == null ? "?" : buyer,
                    "outcome", row.outcome()))))));
            gui.set(slot, icon);
            slot++;
        }
        gui.open(admin);
    }
}
```

- [ ] **Step 4: Add needed lang keys** to `lang.yml`:

```yaml
gui:
  main:
    title: "<gold>Auctions"
offer-board:
  title: "<gold>Offers <gray>· listing <red>#<id>"
  items-viewer: "<gold>Offered items"
admin:
  title: "<gold>Admin <gray>· users"
  user:
    title: "<gold>Listings"
    listing: "<gray>#<id> · <status> · <offers> offer(s)"
  sales:
    title: "<gold>Sales log"
    row: "<gray><seller> → <buyer> · <outcome>"
listings:
  lore:
    price: "<gray>BIN: <white><price>"
    offers: "<gray>Offers: <white><count>"
    remaining: "<gray>Expires in: <white><minutes>m"
```

- [ ] **Step 5: Verify compile**

Run: `.\gradlew.bat :auction-house:compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```powershell
git add auction-house/src/main/java/dev/ah/auctionhouse/gui auction-house/src/main/resources/lang.yml
git commit -m "feat(ah): add main menu, offer board, admin GUI"
```

---

### Task 21: AuctionHouse — Command tree + Join notifications + NotificationService

**Files:**
- Create: `auction-house/src/main/java/dev/ah/auctionhouse/notify/NotificationService.java`
- Modify: `auction-house/src/main/java/dev/ah/auctionhouse/command/AhCommand.java` (real implementation)
- Modify: `auction-house/src/main/java/dev/ah/auctionhouse/listener/JoinNotifier.java` (real implementation)

**Interfaces:**
- Produces:
```java
public class NotificationService {
    public NotificationService(AhServices services);
    public void notifyOfferDecision(UUID offerer, boolean accepted);  // online: send now; offline: store row
    public void flush(Player player);                                 // deliver stored rows, mark read
}
public class AhCommand implements CommandExecutor, TabCompleter { public AhCommand(AhServices services); }
```

- [ ] **Step 1: Write `NotificationService`**

```java
package dev.ah.auctionhouse.notify;

import dev.ah.auctionhouse.AhServices;
import dev.ah.core.msg.SoundRegistry;
import dev.ah.core.notification.Notification;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.UUID;

public class NotificationService {
    private final AhServices services;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public NotificationService(AhServices services) {
        this.services = services;
    }

    public void notifyOfferDecision(UUID offerer, boolean accepted) {
        String key = accepted ? "offers.accepted-by-seller" : "offers.rejected-by-seller";
        Player online = Bukkit.getPlayer(offerer);
        if (online != null) {
            online.sendMessage(MM.parse(services.messages().get(key)));
            services.sounds().play(online, accepted ? SoundRegistry.Event.OFFER_ACCEPTED : SoundRegistry.Event.OFFER_REJECTED);
        } else {
            services.notifications().add(new Notification(0, offerer, key, System.currentTimeMillis(), null));
        }
    }

    public void flush(Player player) {
        for (Notification n : services.notifications().unread(player.getUniqueId())) {
            player.sendMessage(MM.parse(services.messages().get(n.messageKey())));
            services.notifications().markRead(n.id(), System.currentTimeMillis());
        }
    }
}
```

- [ ] **Step 2: Wire `OfferBoardGui` to notification** — modify Task 20's `decide` method in `OfferBoardGui.java` to notify the offer owner after a successful decision:

```java
    private void decide(long offerId, boolean accept) {
        Offer offer = services.offers().byId(offerId).orElse(null);
        if (offer == null) return;
        OfferDecisionService.Result r = accept
                ? services.decisions().accept(viewer.getUniqueId(), offerId)
                : services.decisions().reject(viewer.getUniqueId(), offerId);
        if (r == OfferDecisionService.Result.SUCCESS) {
            viewer.sendMessage(MM.parse(services.messages().get(accept ? "offers.accepted-seller" : "offers.rejected-seller")));
            services.sounds().play(viewer, SoundRegistry.Event.OFFER_ACCEPTED);
            new NotificationService(services).notifyOfferDecision(offer.offerer(), accept);
        } else {
            services.sounds().play(viewer, SoundRegistry.Event.ERROR);
        }
        open();
    }
```
Add `import dev.ah.auctionhouse.notify.NotificationService;` to `OfferBoardGui.java`.

- [ ] **Step 3: Write the real `AhCommand`**

```java
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
            case "sell" -> { if (require(player, "ah.use")) new SellGui(services).open(player); }
            case "search" -> {
                if (require(player, "ah.use")) {
                    String term = args.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : "";
                    new AhMainGui(services).openSearch(player, term);
                }
            }
            case "my" -> { if (require(player, "ah.use")) new AdminGui(services).openMyListings(player); }
            case "claim" -> { if (require(player, "ah.use")) new ClaimGui(services).open(player, 0); }
            case "admin" -> {
                if (require(player, "ah.admin")) new AdminGui(services).open(player);
            }
            case "reload" -> { if (require(player, "ah.admin")) services.sounds().play(player, dev.ah.core.msg.SoundRegistry.Event.CLICK); }
            case "help" -> player.sendMessage(MM.parse(services.messages().get("commands.help")));
            default -> player.sendMessage(MM.parse(services.messages().get("commands.help")));
        }
        return true;
    }

    private boolean require(Player player, String perm) {
        if (player.hasPermission(perm)) return true;
        player.sendMessage(MM.parse(services.messages().get("admin.no-permission")));
        return false;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return SUBCOMMANDS.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).collect(java.util.stream.Collectors.toList());
        if (args.length == 2 && args[0].equalsIgnoreCase("search")) return List.of("<item name>");
        return List.of();
    }
}
```

- [ ] **Step 4: Write the real `JoinNotifier`**

```java
package dev.ah.auctionhouse.listener;

import dev.ah.auctionhouse.AhServices;
import dev.ah.auctionhouse.notify.NotificationService;
import dev.ah.core.msg.SoundRegistry;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import java.util.Map;

public class JoinNotifier implements Listener {
    private final AhServices services;
    private final NotificationService notifications;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public JoinNotifier(AhServices services) {
        this.services = services;
        this.notifications = new NotificationService(services);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var p = event.getPlayer();
        notifications.flush(p);

        long unclaimed = services.claims().countUnclaimed(p.getUniqueId());
        long pending = services.offers().countPendingForSeller(p.getUniqueId());
        if (unclaimed > 0) {
            p.sendMessage(MM.parse(services.messages().get("claims.unclaimed", Map.of("count", String.valueOf(unclaimed)))));
        }
        if (pending > 0) {
            p.sendMessage(MM.parse(services.messages().get("offers.received")));
            services.sounds().play(p, SoundRegistry.Event.OFFER_RECEIVED);
        }
    }
}
```

- [ ] **Step 5: Verify build incl. shadow jar**

Run: `.\gradlew.bat build`
Expected: `BUILD SUCCESSFUL`, and `auction-house\build\libs\AuctionHouse-1.0.0.jar` is a self-contained jar containing relocated core classes.

- [ ] **Step 6: Commit**

```powershell
git add auction-house
git commit -m "feat(ah): command tree, join notifications, full build"
```

---

### Task 22: Manual server QA checklist + README

**Files:**
- Create: `README.md` (update repo placeholder)
- Create: `docs/superpowers/plans/2026-09-06-auction-house-QA.md` (checklist)

**Interfaces:**
- Produces: installable jar + documented flow.

- [ ] **Step 1: Write the QA checklist** (`docs/superpowers/plans/2026-09-06-auction-house-QA.md`)

```markdown
# Auction House — Manual QA
Server: Paper with Geyser + Floodgate on the local machine. Drop AuctionHouse-1.0.0.jar into plugins/.

1. Java flow:
   - [ ] /ah opens main menu; listings paginate; sort + search work
   - [ ] /ah sell → deposit item(s) → confirm → listing appears; item leaves inventory
   - [ ] Second player /ah → clicks listing → /ah offer screen → deposit bundle → confirm
   - [ ] Seller sees "new offer" on join; /ah my → offer board: left=item, right=offer; Open-item shows shulker contents; Accept
   - [ ] Buyer gets accepted notification; /ah claim shows BOUGHT item; withdraw to inventory
   - [ ] Reject path returns offer items to loser's claim
   - [ ] Expiry: list item → wait past duration (set a 1ms duration in a test config) → item lands in seller's claims; sweep log line printed
   - [ ] Custom item with NBT (e.g. renamed+enchanted diamond sword) round-trips through listing → purchase; name/enchantments preserved
2. Bedrock flow (Geyser client):
   - [ ] All above via Bedrock client; chest GUIs render correctly
   - [ ] /ah search <text> works without in-GUI typing
3. Economy off guard:
   - [ ] With economy.enabled:false no price UI appears anywhere
4. Persistence:
   - [ ] restart server; listings/offers/claims still present; notifications flushed on next join
```

- [ ] **Step 2: Update `README.md`**

```markdown
# MinecraftPlugins

A list of custom plugins created by me.

| Plugin | Description |
|---|---|
| AuctionHouse | Offer-based auction house. List items, others offer item bundles, seller accepts/rejects; everything routes through a claims menu. Item-for-item (optional economy support disabled by default). Java + Bedrock (Geyser). |

Build: `gradlew.bat build` -> jars in `auction-house/build/libs/`.
Spec: `docs/superpowers/specs/2026-09-06-auction-house-design.md`
```

- [ ] **Step 3: Commit and push**

```powershell
git add README.md docs/superpowers/plans
git commit -m "docs: QA checklist and README"
git push origin main
```

---

## Self-Review Notes

- **Spec coverage:** features 1–11 all map to tasks (listing/offer/decision/claims/notifications/admin GUI/expiry/customization). Economy optional wiring is Task 12 + `isEconomyEnabled` guards in GUIs.
- **Cross-task type consistency:** `OfferDecisionService.Result`, store interfaces, and `AhServices` accessors used identically across Tasks 10/17–21. `ClaimGui` uses `services.getGuiFiller()`/`pageSize()` added in Task 18.
- **Task 10 correction** (reentrant transactions) is the only cross-file schema deviation and is documented inline where it applies.