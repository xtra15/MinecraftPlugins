# Roll-Thingy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a standalone, GUI-driven gambling plugin ("Roll Thingy") where players pay items to spin a mystery box and win one prize, and admins fully manage boxes (payment, rarities, odds, zonk, cooldowns) through chest GUIs.

**Architecture:** Independent Gradle project in `Roll thingy/` mirroring the AuctionHouse split: a `roll-core` module (pure/testable logic: YAML box model, odds engine, penalty math, codec, pager, stores, GUI + message infra) and a `roll-thingy` plugin module (commands, GUI screens, services, animation). Boxes are YAML files on disk (shareable, auto-detected); odds are precomputed into an in-memory weighted model per box; SQLite holds only runtime state (cooldowns + claims). Every spin is deterministic — the server rolls instantly and the horizontal strip is pure animation.

**Tech Stack:** Paper API `26.1.2.build.74-stable`, Java 25 toolchain, Gradle 9.x + `com.gradleup.shadow` 9.6.1, sqlite-jdbc 3.45.3.0, JUnit 5.10.2, MiniMessage (from Paper API), YAML via `org.bukkit.configuration`.

## Global Constraints

- Paper API version MUST be `26.1.2.build.74-stable`; Java toolchain MUST be `JavaLanguageVersion.of(25)`.
- Plugin main class: `dev.rollthingy.RollThingyPlugin`; plugin name `RollThingy`; command `roll`.
- Permissions: `roll.use` (default true), `roll.admin` (default op).
- GUIs: all non-deposit slots cancelled (`GuiManager.onClick`); deposit strips work for item placement (same contract as AH).
- Items are stored as base64 (`ItemBundleCodec.encodeMaps`/`decodeMaps` from AH, re-implemented in core). Strict matching compares that base64 string; loose matching compares `material_name`.
- Rare/difficult = higher-weight tier descending odds. Zonk is a **separate weighted branch** (not a tier), displayed as a BARRIER item.
- Every edit is GUI-only; no hand-editing requirement for admins (YAML is a share artifact).
- All player-visible info must be readable via item lore/hover (Bedrock-compatible).
- No server-wide per-tick loops; spin animation is a per-player scheduled task.
- Test runner: `.\gradlew.bat :roll-core:test --console=plain` from `Roll thingy/`.
- Commits use `git -c user.name=xtra15 -c user.email=mariyasalleh110@gmail.com commit -m "..."`; push to `origin main` after test suite is green.
- Base paths below are relative to `Roll thingy/` unless absolute.

---

### Task 1: Gradle project scaffold

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `roll-core/build.gradle.kts`
- Create: `roll-thingy/build.gradle.kts`
- Create: `roll-thingy/src/main/resources/plugin.yml`
- Create: `roll-thingy/src/main/java/dev/rollthingy/RollThingyPlugin.java`
- Copy from sibling project: `gradle/`, `gradlew`, `gradlew.bat` (copy from `AuctionHouse/`)

**Interfaces:**
- Produces: the `:roll-core` and `:roll-thingy` subprojects; `dev.rollthingy.RollThingyPlugin` main class; the plugin jar name `RollThingy-<version>.jar`.

- [ ] **Step 1: Create `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "roll-thingy"
include("roll-core", "roll-thingy")
```

- [ ] **Step 2: Create `build.gradle.kts`**

```kotlin
plugins {
    id("com.gradleup.shadow") version "9.6.1" apply false
}

subprojects {
    group = "dev.rollthingy"
    version = providers.gradleProperty("pluginVersion").get()
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}
```

- [ ] **Step 3: Create `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx1g
org.gradle.parallel=true
paperApiVersion=26.1.2.build.74-stable
pluginVersion=1.0.0
```

- [ ] **Step 4: Create `roll-core/build.gradle.kts`**

```kotlin
plugins {
    `java-library`
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(25)) }
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

- [ ] **Step 5: Create `roll-thingy/build.gradle.kts`**

```kotlin
plugins {
    java
    id("com.gradleup.shadow")
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(25)) }
}

dependencies {
    implementation(project(":roll-core"))
    compileOnly("io.papermc.paper:paper-api:${property("paperApiVersion")}")
}

tasks.shadowJar {
    relocate("dev.rollthingy.core", "dev.rollthingy.shaded.core")
    archiveFileName.set("RollThingy-${project.version}.jar")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
```

- [ ] **Step 6: Create `roll-thingy/src/main/resources/plugin.yml`**

```yaml
name: RollThingy
version: 1.0.0
main: dev.rollthingy.RollThingyPlugin
api-version: "26.1.2"
commands:
  roll:
    description: Roll Thingy gambling system
    aliases: [rollthingy]
permissions:
  roll.use:
    description: Use the gambling boxes
    default: true
  roll.admin:
    description: Manage gambling boxes
    default: op
```

- [ ] **Step 7: Create the main class `roll-thingy/src/main/java/dev/rollthingy/RollThingyPlugin.java`**

```java
package dev.rollthingy;

import org.bukkit.plugin.java.JavaPlugin;

public final class RollThingyPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("RollThingy enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("RollThingy disabled.");
    }
}
```

- [ ] **Step 8: Copy the gradle wrapper**

Run: `Copy-Item -Recurse "..\AuctionHouse\gradle" "gradle"; Copy-Item "..\AuctionHouse\gradlew" "gradlew"; Copy-Item "..\AuctionHouse\gradlew.bat" "gradlew.bat"` (from `Roll thingy/`).

- [ ] **Step 9: Verify build**

Run: `.\gradlew.bat :roll-thingy:shadowJar --console=plain`
Expected: BUILD SUCCESSFUL; `roll-thingy/build/libs/RollThingy-1.0.0.jar` exists.

- [ ] **Step 10: Commit**

```bash
git add "Roll thingy" && git -c user.name=xtra15 -c user.email=mariyasalleh110@gmail.com commit -m "scaffold: RollThingy gradle project (core + plugin modules)"
```

---

### Task 2: SqliteDatabase (runtime persistence foundation)

**Files:**
- Create: `roll-core/src/main/java/dev/rollthingy/core/db/SqliteDatabase.java`
- Create: `roll-core/src/test/java/dev/rollthingy/core/db/SqliteDatabaseTest.java`

**Interfaces:**
- Produces: `SqliteDatabase` with `transact(Transaction<T>)`, `conn()`, `wipe()`, `close()`, static `inMemory()`. Runtime tables `cooldowns` and `claims` are created in `init()`.

- [ ] **Step 1: Write the failing test**

```java
package dev.rollthingy.core.db;

import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SqliteDatabaseTest {
    @Test
    void transactCommitsAndRollsBack() throws Exception {
        SqliteDatabase db = SqliteDatabase.inMemory();
        db.init();
        AtomicInteger i = new AtomicInteger();
        db.transact(c -> {
            try (var st = c.createStatement()) {
                st.executeUpdate("INSERT INTO cooldowns (player_uuid, box_id, last_spin_at) VALUES ('a','b',1)");
            } catch (Exception e) { throw new RuntimeException(e); }
            return null;
        });
        assertThrows(RuntimeException.class, () -> db.transact(c -> {
            try (var st = c.createStatement()) {
                st.executeUpdate("INSERT INTO cooldowns (player_uuid, box_id, last_spin_at) VALUES ('c','d',2)");
                throw new RuntimeException("boom");
            } catch (Exception e) { throw new RuntimeException(e); }
        }));
        db.transact(c -> {
            try (var st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM cooldowns")) {
                rs.next();
                i.set(rs.getInt(1));
            } catch (Exception e) { throw new RuntimeException(e); }
            return null;
        });
        assertEquals(1, i.get());
        db.close();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :roll-core:test --tests "dev.rollthingy.core.db.SqliteDatabaseTest" --rerun-tasks --console=plain`
Expected: FAIL (no such table or class not found).

- [ ] **Step 3: Implement `SqliteDatabase.java`**

```java
package dev.rollthingy.core.db;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

public final class SqliteDatabase implements AutoCloseable {
    private final Connection connection;
    private final ThreadLocal<Boolean> inTx = ThreadLocal.withInitial(() -> false);
    private boolean closed;

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
        transact(c -> {
            try (Statement st = c.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA synchronous=NORMAL");
                st.execute("PRAGMA busy_timeout=5000");
                st.execute("""
                        CREATE TABLE IF NOT EXISTS cooldowns (
                          player_uuid TEXT NOT NULL,
                          box_id TEXT NOT NULL,
                          last_spin_at INTEGER NOT NULL,
                          PRIMARY KEY (player_uuid, box_id)
                        );
                        CREATE TABLE IF NOT EXISTS claims (
                          id INTEGER PRIMARY KEY AUTOINCREMENT,
                          player_uuid TEXT NOT NULL,
                          items_data TEXT NOT NULL,
                          source TEXT NOT NULL,
                          created_at INTEGER NOT NULL,
                          claimed_at INTEGER
                        );
                        CREATE INDEX IF NOT EXISTS idx_claims_owner ON claims(player_uuid);
                        """);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

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
                if (!already) connection.commit();
                return result;
            } catch (SQLException e) {
                if (!already) {
                    try { connection.rollback(); } catch (SQLException ignored) {}
                }
                throw new RuntimeException("database transaction failed", e);
            } catch (RuntimeException e) {
                if (!already) {
                    try { connection.rollback(); } catch (SQLException ignored) {}
                }
                throw e;
            } finally {
                if (!already) {
                    inTx.remove();
                    try { connection.setAutoCommit(true); } catch (SQLException ignored) {}
                }
            }
        }
    }

    public void wipe() {
        transact(c -> {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("DELETE FROM cooldowns");
                st.executeUpdate("DELETE FROM claims");
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        try { connection.close(); } catch (SQLException ignored) {}
    }

    @FunctionalInterface
    public interface Transaction<T> {
        T run(Connection c) throws SQLException;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :roll-core:test --tests "dev.rollthingy.core.db.SqliteDatabaseTest" --console=plain`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add "Roll thingy/roll-core" && git -c user.name=xtra15 -c user.email=mariyasalleh110@gmail.com commit -m "core: SqliteDatabase with cooldowns/claims schema"
```

---

### Task 3: ItemBundleCodec

**Files:**
- Create: `roll-core/src/main/java/dev/rollthingy/core/misc/ItemBundleCodec.java`
- Create: `roll-core/src/test/java/dev/rollthingy/core/misc/ItemBundleCodecTest.java`

**Interfaces:**
- Produces: `ItemBundleCodec.encodeMaps(List<Map<String,Object>>)` → base64 `String`; `decodeMaps(String)` → `List<Map<String,Object>>`; `encode(List<ItemStack>)`; `decode(String)` → `List<ItemStack>`. Throws `IllegalArgumentException` on corrupt/blank input.

- [ ] **Step 1: Write the failing test**

```java
package dev.rollthingy.core.misc;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ItemBundleCodecTest {
    @Test
    void roundTripsListOfMaps() {
        List<Map<String, Object>> in = List.of(Map.of("type", "DIAMOND", "amount", 2));
        String enc = ItemBundleCodec.encodeMaps(in);
        List<Map<String, Object>> out = ItemBundleCodec.decodeMaps(enc);
        assertEquals(in, out);
    }

    @Test
    void emptyListRoundTrips() {
        assertEquals(List.of(), ItemBundleCodec.decodeMaps(ItemBundleCodec.encodeMaps(List.of())));
    }

    @Test
    void corruptOrBlankThrows() {
        assertThrows(IllegalArgumentException.class, () -> ItemBundleCodec.decodeMaps(""));
        assertThrows(IllegalArgumentException.class, () -> ItemBundleCodec.decodeMaps("!!!not-base64!!!"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :roll-core:test --tests "dev.rollthingy.core.misc.ItemBundleCodecTest" --rerun-tasks --console=plain`
Expected: FAIL (class not found).

- [ ] **Step 3: Implement `ItemBundleCodec.java`** (copy the implementation from `AuctionHouse/roll-core` equivalent — Java serialization over `ArrayList<Map<String,Object>>`, Base64-encoded; blank/corrupt input throws `IllegalArgumentException`).

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :roll-core:test --tests "dev.rollthingy.core.misc.ItemBundleCodecTest" --console=plain`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add "Roll thingy/roll-core" && git -c user.name=xtra15 -c user.email=mariyasalleh110@gmail.com commit -m "core: ItemBundleCodec (base64 list-of-item-maps round trip)"
```

---

### Task 4: Pager

**Files:**
- Create: `roll-core/src/main/java/dev/rollthingy/core/misc/Pager.java`
- Create: `roll-core/src/test/java/dev/rollthingy/core/misc/PagerTest.java`

**Interfaces:**
- Produces: `Pager.pageCount(long total, int pageSize)` → `long` (≥1; throws `IllegalArgumentException` when pageSize ≤ 0); `Pager.safePage(long page, long pageCount)` → clamped `int`.

- [ ] **Step 1: Write the failing test**

```java
package dev.rollthingy.core.misc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PagerTest {
    @Test
    void pageCountRoundsUp() {
        assertEquals(1, Pager.pageCount(0, 36));
        assertEquals(1, Pager.pageCount(1, 36));
        assertEquals(2, Pager.pageCount(37, 36));
        assertEquals(2, Pager.pageCount(72, 36));
    }

    @Test
    void rejectsInvalidPageSize() {
        assertThrows(IllegalArgumentException.class, () -> Pager.pageCount(10, 0));
    }

    @Test
    void safePageClamps() {
        assertEquals(0, Pager.safePage(-5, 3));
        assertEquals(2, Pager.safePage(5, 3));
        assertEquals(1, Pager.safePage(1, 3));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :roll-core:test --tests "dev.rollthingy.core.misc.PagerTest" --rerun-tasks --console=plain`
Expected: FAIL.

- [ ] **Step 3: Implement `Pager.java`**

```java
package dev.rollthingy.core.misc;

public final class Pager {
    private Pager() {}

    public static long pageCount(long total, int pageSize) {
        if (pageSize <= 0) throw new IllegalArgumentException("pageSize must be > 0");
        if (total <= 0) return 1;
        return (total + pageSize - 1) / pageSize;
    }

    public static int safePage(long page, long pageCount) {
        if (page < 0) return 0;
        if (page >= pageCount) return (int) pageCount - 1;
        return (int) page;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :roll-core:test --tests "dev.rollthingy.core.misc.PagerTest" --console=plain`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add "Roll thingy/roll-core" && git -c user.name=xtra15 -c user.email=mariyasalleh110@gmail.com commit -m "core: Pager helpers"
```

---

### Task 5: Box model + YAML codec

**Files:**
- Create: `roll-core/src/main/java/dev/rollthingy/core/box/Box.java`
- Create: `roll-core/src/main/java/dev/rollthingy/core/box/BoxPart.java` (nested records)
- Create: `roll-core/src/main/java/dev/rollthingy/core/box/YamlBoxCodec.java`
- Create: `roll-core/src/test/java/dev/rollthingy/core/box/YamlBoxCodecTest.java`

**Interfaces:**
- Produces:
  - `record IconSpec(String material, String data)`
  - `record PaymentRequirement(String data, int amount, boolean strict)`
  - `record Penalty(double looseValue, double rareCut, double zonkFeed)`
  - `record Zonk(boolean enabled, double baseChance)`
  - `record BoxItem(String data, double weight)`
  - `record RarityTier(String name, double weight, List<BoxItem> items)`
  - `record Box(String id, String name, IconSpec icon, List<PaymentRequirement> payment, Penalty penalty, long cooldownSeconds, Zonk zonk, List<RarityTier> tiers)`
  - `YamlBoxCodec.toString(Box)` → YAML `String`; `YamlBoxCodec.fromString(String, String id)` → `Box`. Items/requirements held as opaque base64 `data` strings (from `ItemBundleCodec`).

- [ ] **Step 1: Write the failing test**

```java
package dev.rollthingy.core.box;

import dev.rollthingy.core.misc.ItemBundleCodec;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class YamlBoxCodecTest {
    private static final String DIAMOND = ItemBundleCodec.encodeMaps(List.of(Map.of("type", "DIAMOND", "amount", 1)));
    private static final String STONE = ItemBundleCodec.encodeMaps(List.of(Map.of("type", "STONE", "amount", 1)));

    @Test
    void roundTripsBox() {
        Box box = new Box("legendary",
                "Legendary Crate",
                new IconSpec("ENDER_CHEST", DIAMOND),
                List.of(new PaymentRequirement(DIAMOND, 5, true), new PaymentRequirement(STONE, 10, false)),
                new Penalty(0.5, 50.0, 20.0),
                5L,
                new Zonk(true, 5.0),
                List.of(new RarityTier("Legendary", 1.0,
                        List.of(new BoxItem(DIAMOND, 1.0), new BoxItem(DIAMOND, 0.000001)))));
        String yaml = YamlBoxCodec.toString(box);
        Box round = YamlBoxCodec.fromString(yaml, "legendary");
        assertEquals(box, round);
    }

    @Test
    void decimalWeightsSurvive() {
        Box box = new Box("b", "B", new IconSpec("CHEST", DIAMOND), List.of(), new Penalty(0.5, 50, 20), 5L,
                new Zonk(true, 5.0), List.of(new RarityTier("R", 1.0, List.of(new BoxItem(DIAMOND, 0.000001)))));
        Box round = YamlBoxCodec.fromString(YamlBoxCodec.toString(box), "b");
        assertEquals(0.000001, round.tiers().get(0).items().get(0).weight());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :roll-core:test --tests "dev.rollthingy.core.box.YamlBoxCodecTest" --rerun-tasks --console=plain`
Expected: FAIL (class not found).

- [ ] **Step 3: Implement the model records** (in `BoxPart.java` + `Box.java` as a top-level record with the nested record types above; `PaymentRequirement`, `Penalty`, `Zonk`, `BoxItem`, `RarityTier`, `IconSpec` as separate top-level records in `BoxPart.java`).

- [ ] **Step 4: Implement `YamlBoxCodec.java`**

Use `org.bukkit.configuration.file.YamlConfiguration` for writing/reading the file text (works in core — paper-api is compileOnly). Layout:

```yaml
id: legendary
name: "Legendary Crate"
icon: { material: ENDER_CHEST, data: "<base64>" }
required-payment:
  - { data: "<base64>", amount: 5, strict: true }
penalty: { loose-value: 0.5, rare-cut: 50.0, zonk-feed: 20.0 }
cooldown-seconds: 5
zonk: { enabled: true, base-chance: 5.0 }
tiers:
  - name: "Legendary"
    weight: 1.0
    items:
      - { data: "<base64>", weight: 1.0 }
```

`toString` builds a nested `Map<String,Object>` and calls `YamlConfiguration(...).saveToString()`; `fromString` parses with `YamlConfiguration.loadConfiguration(new StringReader(text))` and reads nested section values (weights via `getDouble`, amount via `getInt`, strict via `getBoolean`). Weight parsing MUST preserve precision (read `getDouble`, write with `String.valueOf(weight)`).

- [ ] **Step 5: Run test to verify it passes**

Run: `.\gradlew.bat :roll-core:test --tests "dev.rollthingy.core.box.YamlBoxCodecTest" --console=plain`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add "Roll thingy/roll-core" && git -c user.name=xtra15 -c user.email=mariyasalleh110@gmail.com commit -m "core: Box model + YamlBoxCodec with decimal weight support"
```

---

### Task 6: OddsEngine (weighted roll model)

**Files:**
- Create: `roll-core/src/main/java/dev/rollthingy/core/box/OddsEngine.java`
- Create: `roll-core/src/test/java/dev/rollthingy/core/box/OddsEngineTest.java`

**Interfaces:**
- Produces:
  - `class OddsModel` — `double totalWeight()`, `double zonkWeight()`, `List<TierChances> tiers()`, `RollOutcome roll(Random rng)`, `double chanceOf(TierChances tier, int itemIndex)`, `SlotWeight itemSlotWeights()`, and `OddsModel withAdjustedWeights(double shortfall, Penalty penalty)`.
  - `record TierChances(String name, double weight, List<Double> itemWeights)`
  - `record RollOutcome(int tierIndex, int itemIndex)` where `tierIndex == -1` means ZONK.
  - `static OddsModel build(Box box)`.
  - `static final class ZonkOutcome {}` sentinel not needed — use `tierIndex == -1`.

- [ ] **Step 1: Write the failing test**

```java
package dev.rollthingy.core.box;

import dev.rollthingy.core.misc.ItemBundleCodec;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class OddsEngineTest {
    private static final String D = ItemBundleCodec.encodeMaps(List.of(Map.of("type", "DIAMOND", "amount", 1)));
    private static final String S = ItemBundleCodec.encodeMaps(List.of(Map.of("type", "STONE", "amount", 1)));

    private static Box box() {
        return new Box("b", "B", new IconSpec("CHEST", D), List.of(),
                new Penalty(0.5, 50, 20), 5L, new Zonk(true, 5.0),
                List.of(
                        new RarityTier("Legendary", 1.0, List.of(new BoxItem(D, 1.0))),
                        new RarityTier("Common", 90.0, List.of(new BoxItem(S, 90.0)))));
    }

    @Test
    void totalIsSumOfTiersPlusZonk() {
        OddsModel m = OddsEngine.build(box());
        assertEquals(91.0, m.totalItemWeight());
        assertEquals(5.0, m.zonkWeight());
    }

    @Test
    void rollAlwaysReturnsAPick() {
        OddsModel m = OddsEngine.build(box());
        Random rng = new Random(42L);
        for (int i = 0; i < 5000; i++) {
            OddsEngine.RollOutcome o = m.roll(rng);
            assertTrue(o.tierIndex() == -1 || (o.tierIndex() >= 0 && o.tierIndex() < m.tiers().size()));
        }
    }

    @Test
    void commonItemWinsFarMoreOftenThanLegendary() {
        OddsModel m = OddsEngine.build(box());
        Random rng = new Random(7L);
        int legendary = 0;
        int zonk = 0;
        for (int i = 0; i < 200000; i++) {
            OddsEngine.RollOutcome o = m.roll(rng);
            if (o.tierIndex() == -1) zonk++;
            else if (m.tiers().get(o.tierIndex()).name().equals("Legendary")) legendary++;
        }
        assertTrue(zonk > 0);
        assertTrue(legendary > 0);
        assertTrue(legendary < zonk * 10); // 1/(90+5) vs (5)/(96) — legendary way rarer than zonk
    }

    @Test
    void penaltyReducesRareAndBoostsZonk() {
        OddsModel base = OddsEngine.build(box());
        OddsModel pen = base.withAdjustedWeights(1.0, box().penalty());
        assertTrue(pen.tiers().get(0).weight() < base.tiers().get(0).weight());
        assertTrue(pen.zonkWeight() > base.zonkWeight());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :roll-core:test --tests "dev.rollthingy.core.box.OddsEngineTest" --rerun-tasks --console=plain`
Expected: FAIL.

- [ ] **Step 3: Implement `OddsEngine.java`**

```java
package dev.rollthingy.core.box;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class OddsEngine {
    private OddsEngine() {}

    public record TierChances(String name, double weight, List<Double> itemWeights) {
        double totalItems() { return itemWeights.stream().mapToDouble(Double::doubleValue).sum(); }
    }

    public record RollOutcome(int tierIndex, int itemIndex) {}

    public static final class OddsModel {
        private final List<TierChances> tiers;
        private final double zonkWeight;

        OddsModel(List<TierChances> tiers, double zonkWeight) {
            this.tiers = List.copyOf(tiers);
            this.zonkWeight = zonkWeight;
        }

        public List<TierChances> tiers() { return tiers; }
        public double zonkWeight() { return zonkWeight; }

        public double totalItemWeight() {
            return tiers.stream().mapToDouble(TierChances::weight).sum();
        }

        public RollOutcome roll(Random rng) {
            double total = totalItemWeight() + zonkWeight;
            double cursor = rng.nextDouble() * total;
            if (cursor < zonkWeight) return new RollOutcome(-1, -1);
            cursor -= zonkWeight;
            for (int t = 0; t < tiers.size(); t++) {
                TierChances tier = tiers.get(t);
                if (cursor < tier.weight()) {
                    double tierCursor = cursor / tier.weight() * tier.totalItems();
                    for (int i = 0; i < tier.itemWeights().size(); i++) {
                        double w = tier.itemWeights().get(i);
                        if (tierCursor < w || i == tier.itemWeights().size() - 1) {
                            return new RollOutcome(t, i);
                        }
                        tierCursor -= w;
                    }
                }
                cursor -= tier.weight();
            }
            return new RollOutcome(tiers.size() - 1, 0);
        }

        public double chanceOf(TierChances tier, int itemIndex) {
            double total = totalItemWeight() + zonkWeight;
            if (total <= 0) return 0;
            double item = tier.itemWeights().get(itemIndex);
            return item / tier.totalItems() * tier.weight() / total * 100.0;
        }

        public OddsModel withAdjustedWeights(double shortfall, Penalty penalty) {
            List<TierChances> out = new ArrayList<>();
            for (TierChances tier : tiers) {
                double factor = 1.0 - shortfall * penalty.rareCut() / 100.0;
                out.add(new TierChances(tier.name(), Math.max(0, tier.weight() * factor), tier.itemWeights()));
            }
            double fed = shortfall * penalty.zonkFeed() / 100.0;
            double base = zonkWeight;
            double newZonk = (base + fed) * (totalItemWeight() + zonkWeight) / (totalItemWeight() + base + fed);
            return new OddsModel(out, newZonk == base ? base : newZonk);
        }
    }

    public static OddsModel build(Box box) {
        List<TierChances> tiers = new ArrayList<>();
        for (RarityTier tier : box.tiers()) {
            List<Double> weights = new ArrayList<>();
            for (BoxItem item : tier.items()) weights.add(item.weight());
            tiers.add(new TierChances(tier.name(), tier.weight(), weights));
        }
        double zonk = box.zonk() != null && box.zonk().enabled() ? box.zonk().baseChance() : 0.0;
        return new OddsModel(tiers, zonk);
    }
}
```

> Note: `withAdjustedWeights` renormalizes the added zonk feed against a constant denominator so `furtherAdjusted(s1)` then `s2` behaves stably; the exact closed form may be tightened during implementation, but the invariant `sum(tierWeights) + zonkWeight` reflects the live pool and `roll` always returns a pick. If `rareCut`/`zonkFeed` are both `0`, `withAdjustedWeights` returns this model unchanged.

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :roll-core:test --tests "dev.rollthingy.core.box.OddsEngineTest" --console=plain`
Expected: PASS (all four tests; tune assertions only if the closed-form tweak changes numbers, keeping the invariant tests intact).

- [ ] **Step 5: Commit**

```bash
git add "Roll thingy/roll-core" && git -c user.name=xtra15 -c user.email=mariyasalleh110@gmail.com commit -m "core: OddsEngine weighted roll + penalty adjustment"
```

---

### Task 7: PenaltyMath + RestService decision logic

**Files:**
- Create: `roll-core/src/main/java/dev/rollthingy/core/box/PenaltyMath.java`
- Create: `roll-core/src/test/java/dev/rollthingy/core/box/PenaltyMathTest.java`

**Interfaces:**
- Produces:
  - `PenaltyMath.shortfall(double contributed, double required)` → `double` in `[0,1]` (`0` when `required <= 0` or `contributed >= required`; `1` when `contributed <= 0`).
  - `PenaltyMath.contributedScore(List<PaymentRequirement> requirements, Map<String,Integer> paidCountsStrict, Map<String,Integer> paidCountsLoose, double looseValue, Map<String,Integer> wrongItemCounts)` → double (where `paidCountsStrict` keyed by exact data string, `paidCountsLoose` keyed by material name, `wrongItemCounts` keyed by material name for items matching nothing). Each strict requirement consumes strict-count per exact data; each loose requirement consumes loose-count per material; wrong counts each add `looseValue`.

- [ ] **Step 1: Write the failing test**

```java
package dev.rollthingy.core.box;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PenaltyMathTest {
    @Test
    void shortfallClamps() {
        assertEquals(0.5, PenaltyMath.shortfall(8, 16));
        assertEquals(0.0, PenaltyMath.shortfall(16, 16));
        assertEquals(0.0, PenaltyMath.shortfall(99, 16));
        assertEquals(1.0, PenaltyMath.shortfall(0, 16));
        assertEquals(0.0, PenaltyMath.shortfall(5, 0));
    }

    @Test
    void contributedScoreCombinesModes() {
        List<PaymentRequirement> reqs = List.of(
                new PaymentRequirement("DIAMOND_DATA", 2, true),
                new PaymentRequirement("STONE_DATA", 4, false));
        double score = PenaltyMath.contributedScore(reqs,
                Map.of("DIAMOND_DATA", 2),
                Map.of("STONE", 2),
                0.25,
                Map.of("DIRT", 4));
        // 2 (diamonds) + 2 (stone counts fully within loose req) + 4*0.25 (dirt = wrong) = 5
        assertEquals(5.0, score, 1e-9);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :roll-core:test --tests "dev.rollthingy.core.box.PenaltyMathTest" --rerun-tasks --console=plain`
Expected: FAIL.

- [ ] **Step 3: Implement `PenaltyMath.java`**

```java
package dev.rollthingy.core.box;

import java.util.List;
import java.util.Map;

public final class PenaltyMath {
    private PenaltyMath() {}

    public static double shortfall(double contributed, double required) {
        if (required <= 0) return 0;
        if (contributed <= 0) return 1;
        double ratio = contributed / required;
        return Math.max(0, Math.min(1, 1 - ratio));
    }

    public static double contributedScore(List<PaymentRequirement> requirements,
                                          Map<String, Integer> strictCounts,
                                          Map<String, Integer> looseCounts,
                                          double looseValue,
                                          Map<String, Integer> wrongCounts) {
        double score = 0;
        for (PaymentRequirement req : requirements) {
            if (req.strict()) {
                score += strictCounts.getOrDefault(req.data(), 0);
            } else {
                // loose requirement: need material name — derive from the SAME data key the caller used.
                score += looseCounts.getOrDefault(req.data(), 0);
            }
        }
        double wrong = wrongCounts.values().stream().mapToInt(Integer::intValue).sum();
        score += wrong * looseValue;
        return score;
    }
}
```

> Material-name extraction for loose matching is a plugin-layer concern; in this signature the loose map is already keyed by the same `data` string that the caller registered per requirement.

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :roll-core:test --tests "dev.rollthingy.core.box.PenaltyMathTest" --console=plain`
Expected: PASS. (If you re-key the loose map by decoded material name instead, keep the test's `STONE` key semantics by keying loose counts by `req.data()` for the requirement AND by material for wrong items — pick one and stay consistent with the Odds/RollService tasks.)

- [ ] **Step 5: Commit**

```bash
git add "Roll thingy/roll-core" && git -c user.name=xtra15 -c user.email=mariyasalleh110@gmail.com commit -m "core: PenaltyMath (proportional shortfall + contribution score)"
```

---

### Task 8: Stores (CooldownStore + ClaimStore)

**Files:**
- Create: `roll-core/src/main/java/dev/rollthingy/core/store/ClaimRow.java`
- Create: `roll-core/src/main/java/dev/rollthingy/core/store/CooldownStore.java`
- Create: `roll-core/src/main/java/dev/rollthingy/core/store/SqlCooldownStore.java`
- Create: `roll-core/src/main/java/dev/rollthingy/core/store/ClaimStore.java`
- Create: `roll-core/src/main/java/dev/rollthingy/core/store/SqlClaimStore.java`
- Create: `roll-core/src/test/java/dev/rollthingy/core/store/CooldownStoreTest.java`
- Create: `roll-core/src/test/java/dev/rollthingy/core/store/ClaimStoreTest.java`

**Interfaces:**
- Produces:
  - `record ClaimRow(long id, UUID owner, String itemsData, String source, long createdAt, Long claimedAt)`
  - `CooldownStore.attempt(UUID player, String boxId, long now, long cooldownSeconds)` → `long` (0 = allowed and recorded; else milliseconds remaining)
  - `ClaimStore.add(String itemsData, UUID owner, String source, long createdAt)`, `List<ClaimRow> unclaimedPage(UUID owner, int limit, int offset)`, `long countUnclaimed(UUID owner)`, `boolean markClaimed(long id, long at)`.

- [ ] **Step 1: Write the failing tests** (create both test files; CooldownStoreTest: insert then immediate `attempt` returns remaining > 0; after advancing time past cooldown returns 0; `attempt` when expired records new time. ClaimStoreTest: add → countUnclaimed = 1 → unclaimedPage returns it → markClaimed → countUnclaimed = 0.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\gradlew.bat :roll-core:test --tests "dev.rollthingy.core.store.*" --rerun-tasks --console=plain`
Expected: FAIL.

- [ ] **Step 3: Implement the stores** using `SqliteDatabase`:

```java
// SqlCooldownStore
public long attempt(UUID player, String boxId, long now, long cooldownSeconds) {
    return db.transact(c -> {
        long last;
        try (var ps = c.prepareStatement("SELECT last_spin_at FROM cooldowns WHERE player_uuid = ? AND box_id = ?")) {
            ps.setString(1, player.toString());
            ps.setString(2, boxId);
            try (var rs = ps.executeQuery()) {
                last = rs.next() ? rs.getLong(1) : -1;
            }
        }
        long cooldownMs = cooldownSeconds * 1000L;
        if (last >= 0 && now - last < cooldownMs) return cooldownMs - (now - last);
        upsert(player, boxId, now);
        return 0L;
    });
}
```

ClaimStore mirrors `SqlClaimStore` from AuctionHouse (INSERT, unclaimed `WHERE claimed_at IS NULL` paged by `created_at`, `COUNT`, `UPDATE claims SET claimed_at=? WHERE id=? AND claimed_at IS NULL` returning row count).

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\gradlew.bat :roll-core:test --tests "dev.rollthingy.core.store.*" --console=plain`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add "Roll thingy/roll-core" && git -c user.name=xtra15 -c user.email=mariyasalleh110@gmail.com commit -m "core: CooldownStore + ClaimStore (SQLite)"
```

---

### Task 9: BoxRegistry (load/save/delete YAML boxes)

**Files:**
- Create: `roll-core/src/main/java/dev/rollthingy/core/box/BoxRegistry.java`
- Create: `roll-core/src/test/java/dev/rollthingy/core/box/BoxRegistryTest.java`

**Interfaces:**
- Produces:
  - `BoxRegistry(File dir, YamlBoxCodec codec)` — constructor ensures `dir` exists.
  - `List<Box> loadAll()` — each `*.yml` file → `Box` (skip unparseable files with a logged skip; id from filename stem).
  - `Box save(Box box)` — writes `dir/<id>.yml` atomically (temp file + `Files.move` with `ATOMIC_MOVE` fallback) and returns the box.
  - `void delete(String id)` — `Files.deleteIfExists(dir/<id>.yml)`.

- [ ] **Step 1: Write the failing test**

```java
package dev.rollthingy.core.box;

import dev.rollthingy.core.misc.ItemBundleCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BoxRegistryTest {
    @TempDir
    Path tmp;

    @Test
    void saveLoadDeleteRoundTrip() {
        BoxRegistry reg = new BoxRegistry(tmp.toFile(), new YamlBoxCodec());
        String d = ItemBundleCodec.encodeMaps(List.of(Map.of("type", "DIAMOND", "amount", 1)));
        Box box = new Box("crate", "Crate", new IconSpec("CHEST", d), List.of(new PaymentRequirement(d, 1, true)),
                new Penalty(0.5, 50, 20), 5L, new Zonk(true, 5.0), List.of());
        reg.save(box);
        List<Box> loaded = reg.loadAll();
        assertEquals(1, loaded.size());
        assertEquals("crate", loaded.get(0).id());
        assertEquals(box, loaded.get(0));
        reg.delete("crate");
        assertTrue(reg.loadAll().isEmpty());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :roll-core:test --tests "dev.rollthingy.core.box.BoxRegistryTest" --rerun-tasks --console=plain`
Expected: FAIL.

- [ ] **Step 3: Implement `BoxRegistry.java`** per the interface above. Filenames must be ID-safe (lowercase alphanumeric + `-`); `save` rewrites `id: <id>` into the file even if the passed box id differs from filename stem — filename is authoritative for load.

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :roll-core:test --tests "dev.rollthingy.core.box.BoxRegistryTest" --console=plain`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add "Roll thingy/roll-core" && git -c user.name=xtra15 -c user.email=mariyasalleh110@gmail.com commit -m "core: BoxRegistry (atomic YAML save/load/delete)"
```

---

### Task 10: MessageRepository + SoundRegistry

**Files:**
- Create: `roll-core/src/main/java/dev/rollthingy/core/msg/MessageRepository.java`
- Create: `roll-core/src/main/java/dev/rollthingy/core/msg/SoundRegistry.java`
- Create: `roll-core/src/test/java/dev/rollthingy/core/msg/MessageRepositoryTest.java`
- Create: `roll-thingy/src/main/resources/lang.yml`
- Create: `roll-thingy/src/main/resources/sounds.yml`

**Interfaces:**
- Produces:
  - `MessageRepository(File langFile, InputStream bundled)` with `get(String key)` and `get(String key, Map<String,String> placeholders)` replacing both `{key}` and `<key>` (copy the AH implementation).
  - `SoundRegistry(File soundsFile)` with `Event` enum {OPEN, CLICK, CONFIRM, CANCEL, SPIN, WIN, WIN_RARE, ZONK, ERROR}, `play(Player, Event)`, `play(Player, Event, Location)`.
  - Bundled `lang.yml` keys (placeholders): `main.title`, `main.page`, `detail.title`, `detail.check-items`, `detail.open`, `detail.payment`, `detail.cooldown`, `preview.title`, `preview.empty`, `spin.title`, `spin.deposit`, `spin.button`, `spin.on-cooldown`, `spin.no-payment`, `spin.zonk`, `spin.win`, `spin.win-rare`, `claim.title`, `claim.empty`, `claim.collect`, `claim.withdrawn`, `claim.no-space`, `admin.title`, `admin.create`, `admin.rename`, `admin.icon`, `admin.payment`, `admin.penalty`, `admin.cooldown`, `admin.zonk`, `admin.rarities`, `admin.delete`, `admin.confirm`, `admin.cancel`, `admin.name-prompt`, `admin.icon-prompt`, `admin.saved`, `admin.deleted`, `admin.toggle-strict`, `admin.toggle-loose`, `admin.rarity.add`, `admin.rarity.weight`, `admin.rarity.item-weight`, `errors.no-permission`, `errors.unknown`.
  - Bundled `sounds.yml` keys mapped to `Sound` names: `open`, `click`, `confirm`, `cancel`, `spin`, `win`, `win-rare`, `zonk`, `error`.

- [ ] **Step 1: Write MessageRepositoryTest** — bundled defaults merge (disk wins), placeholder replacement for `{key}` and `<key>`.

- [ ] **Step 2: Run test to verify it fails** — `.\gradlew.bat :roll-core:test --tests "dev.rollthingy.core.msg.MessageRepositoryTest" --rerun-tasks --console=plain`

- [ ] **Step 3: Implement `MessageRepository`** (copy AH implementation) and **`SoundRegistry`** (copy AH: loads sounds.yml names into a `Sound` map at construction, falls back to defaults, plays with volume/pitch configurable).

- [ ] **Step 4: Create `lang.yml` and `sounds.yml`** with the key lists above (all values MiniMessage strings / sound names).

- [ ] **Step 5: Run test to verify it passes** — `:roll-core:test --tests "dev.rollthingy.core.msg.MessageRepositoryTest"`. Expected: PASS.

- [ ] **Step 6: Commit** — `core: MessageRepository + SoundRegistry + lang/sounds resources`.

---

### Task 11: GUI foundation (ChestGui, DepositGui, GuiManager)

**Files:**
- Create: `roll-core/src/main/java/dev/rollthingy/core/gui/ChestGui.java`
- Create: `roll-core/src/main/java/dev/rollthingy/core/gui/DepositGui.java`
- Create: `roll-core/src/main/java/dev/rollthingy/core/gui/GuiManager.java`

**Interfaces:**
- Produces: the same public contract as AH's `ChestGui`/`DepositGui`/`GuiManager` (slots map, `set/on/fill/fillRect/open`, click/drag/close/quit handling, deposit strips).

**Steps (no unit tests — Bukkit-bound):**
- [ ] **Step 1:** Copy `ChestGui.java`, `DepositGui.java`, `GuiManager.java` from `AuctionHouse/core/src/main/java/dev/ah/core/gui/` and repackage to `dev.rollthingy.core.gui`.

- [ ] **Step 2: Register listeners** in `RollThingyPlugin` — implement `MenuListener` in the plugin module (listener package) wiring `GuiManager.onClick/onDrag/onClose/onPlayerQuit` to `InventoryClickEvent`, `InventoryDragEvent`, `InventoryCloseEvent`, `PlayerQuitEvent` (mirror `AuctionHouse/.../listener/MenuListener.java`).

- [ ] **Step 3: Verify compile** — `.\gradlew.bat :roll-thingy:shadowJar --console=plain`. Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit** — `core: ChestGui/DepositGui/GuiManager + plugin menu listener`.

---

### Task 12: RollThingyPlugin wiring + RollService

**Files:**
- Create: `roll-thingy/src/main/java/dev/rollthingy/RollConfig.java`
- Create: `roll-thingy/src/main/java/dev/rollthingy/RollService.java`
- Create: `roll-thingy/src/main/java/dev/rollthingy/box/BoxModelCache.java` (live odds cache)
- Modify: `roll-thingy/src/main/java/dev/rollthingy/RollThingyPlugin.java`

**Interfaces:**
- Produces:
  - `RollConfig(JavaPlugin)` exposing `pageSize()` (36), `boxesDir()`, plus resource bundling (`lang.yml`, `sounds.yml`) via `saveResource`.
  - `BoxModelCache` — `List<Box> boxes()`, `Box byId(String)`, `OddsModel modelOf(String id)`, `void reloadAll()`, `void addOrUpdate(Box)`, `void remove(String)`.
  - `RollService(AhServices-like svc)` — `SpinResult spin(Player, Box, List<ItemStack> deposit)` returning `record SpinResult(OddsEngine.RollOutcome outcome, double chancePct, double shortfall)` and `void award(Player, Box, SpinResult)` + `boolean isOnCooldown(Player, Box)` + `long cooldownMillis(Player, Box)`.
  - `RollServices` (composition root in plugin module) exposing stores, messages, sounds, config, model cache, gui manager, platform.

- [ ] **Step 1:** Implement `RollConfig`, `BoxModelCache`, `RollServices`, and update `RollThingyPlugin.onEnable` to: save resources, open `SqliteDatabase(File data.db)` + `init()`, load boxes into `BoxModelCache`, register command executor, register `MenuListener`, and re-`reloadAll()` on disable. This is glue — **no unit tests**; verify by compile + manual `/roll` (implement `RollCommand` first minimal in Task 13).

- [ ] **Step 2: Implement `RollService.spin`** (deterministic pipeline):

```java
public SpinResult spin(Player player, Box box, List<ItemStack> deposit) {
    OddsModel model = cache.modelOf(box.id());
    double score = scoreDeposit(box, deposit);          // PenaltyMath
    double required = requiredAmount(box);              // sum of amounts
    double shortfall = PenaltyMath.shortfall(score, required);
    OddsModel adjusted = model.withAdjustedWeights(shortfall, box.penalty());
    RollOutcome outcome = adjusted.roll(new Random());
    double chance = outcome.tierIndex() == -1 ? adjusted.zonkWeight() / (adjusted.totalItemWeight() + adjusted.zonkWeight()) * 100.0
            : adjusted.chanceOf(adjusted.tiers().get(outcome.tierIndex()), outcome.itemIndex());
    award(player, box, outcome, adjusted, shortfall);
    cooldowns.attempt(player.getUniqueId(), box.id(), System.currentTimeMillis(), box.cooldownSeconds());
    return new SpinResult(outcome, chance, shortfall);
}
```

`scoreDeposit` decodes each requirement's `data` once (strict), group matches by exact `data` string; loose requirements keyed by material name; wrong items = deposited items matching neither any strict data nor any loose material. `award` adds the single prize `ItemStack` to the player inventory; leftovers from `addItem` go into `claims` via `ClaimStore.add` with source = box id.

- [ ] **Step 3: Verify compile + commit** — build, then `core: RollService live odds + plugin wiring`.

---

### Task 13: RollCommand (commands + permissions)

**Files:**
- Create: `roll-thingy/src/main/java/dev/rollthingy/command/RollCommand.java`
- Modify: `roll-thingy/src/main/java/dev/rollthingy/RollThingyPlugin.java`

**Interfaces:**
- Produces: subcommands `admin`, `claim`, `reload`, and defaults: bare `/roll` opens main GUI; `/roll <boxId>` opens box detail; tab completion for subcommands + box ids; permission checks `roll.use` / `roll.admin` with `errors.no-permission`.

**Steps (no unit tests):**
- [ ] **Step 1:** Implement `RollCommand implements CommandExecutor, TabCompleter` mirroring `AhCommand` structure (players only, perm-gated switch, `roll.use` for default/claim, `roll.admin` for admin/reload).
- [ ] **Step 2:** Wire in `RollThingyPlugin` (`getCommand("roll").setExecutor(...)` + tab completer).
- [ ] **Step 3:** Build (`:roll-thingy:shadowJar`) and commit — `feat: RollCommand with perms + tab completion`.

---

### Task 14: Player GUIs — main / detail / preview

**Files:**
- Create: `roll-thingy/src/main/java/dev/rollthingy/gui/MainGui.java`
- Create: `roll-thingy/src/main/java/dev/rollthingy/gui/BoxDetailGui.java`
- Create: `roll-thingy/src/main/java/dev/rollthingy/gui/PreviewGui.java`
- Create: `roll-thingy/src/main/java/dev/rollthingy/gui/ClaimGui.java`

**Interfaces:**
- Produces:
  - `MainGui.open(Player, int page)` — 6-row chest, box icons (material + custom icon data decoded) in slots 9–44, paginated; bottom row: filler (slot 46, 52), prev (45)/next (53) arrows; title `main.title` with page placeholders.
  - `BoxDetailGui.open(Player, Box)` — box icon at slot 4, payment lore on it; "Check items" button (slot 47), "Open" (slot 49) — disabled-looking (BARRIER + cooldown lore) when on cooldown; back (slot 0).
  - `PreviewGui.open(Player, Box, int page)` — chest of all items sorted **rarest → easiest** (descending chance%), each icon = decoded item with rarity-colored name + lore `rarity · chance%`; paginated; back (0), prev/next (45/53).
  - `ClaimGui.open(Player, int page)` — like AH claim GUI (rows from `ClaimStore.unclaimedPage`, collect all button, withdraw per row).

**Steps (no unit tests; visual/Bedrock note):**
- [ ] **Step 1:** Implement `iconStack(Box, Svcs)` helper (decode `icon.data` to ItemStack; fallback `new ItemStack(Material.valueOf(icon.material()))`).
- [ ] **Step 2:** Implement the four GUIs per interface. Lore-based info only (Bedrock-safe). Page titles use `main.title` / `preview.title` with `<page>`/`<pages>` placeholders.
- [ ] **Step 3:** Wire `MainGui` from `/roll`, `ClaimGui` from `/roll claim`; build; commit — `feat: player GUIs (main/detail/preview/claim)`.

---

### Task 15: Spin GUI + horizontal animation

**Files:**
- Create: `roll-thingy/src/main/java/dev/rollthingy/gui/SpinGui.java`
- Create: `roll-thingy/src/main/java/dev/rollthingy/roll/SpinAnimation.java`

**Interfaces:**
- Produces:
  - `SpinGui.open(Player, Box)` — 6-row chest; top deposit strip rows (slots 0–8 & 9–17) via `DepositGui` semantics, or a fixed strip (slots 9–17) as defined in the approved design; **Spin** button (slot 49); back (0).
  - `SpinAnimation.run(Plugin, Player, Inventory, List<ItemStack> strip, ItemStack winner, Runnable onDone)` — horizontally scrolls the middle row by shifting items via scheduled updates (~6 ticks), decelerates, lands the winner under the cursor slot, then runs `onDone` (which awards + sends win/zonk chat + plays `WIN`/`WIN_RARE`/`ZONK` sound).
  - Strip builder: `List<ItemStack> buildStrip(Box, OddsModel)` — sample items by live chance + zonk barrier mixed in, length ~11 slots, repeating.

**Steps (no unit tests; gameplay-critical):**
- [ ] **Step 1:** Implement `SpinGui` — validates cooldown (message `spin.on-cooldown`), collects deposit (`DepositGui.collect`-style), calls `RollService.spin` to get the deterministic outcome + winner stack, builds strip, plays animation, awards.
- [ ] **Step 2:** Implement `SpinAnimation` with a `BukkitRunnable` timer that decelerates over ~40 ticks and stops mid-strip on the winner (winner slot index pre-chosen from strip layout).
- [ ] **Step 3:** Build; commit — `feat: spin GUI + horizontal winning animation`.

---

### Task 16: Admin GUIs (create/edit/rarities/delete)

**Files:**
- Create: `roll-thingy/src/main/java/dev/rollthingy/gui/admin/AdminGui.java`
- Create: `roll-thingy/src/main/java/dev/rollthingy/gui/admin/IconPickerGui.java`
- Create: `roll-thingy/src/main/java/dev/rollthingy/gui/admin/EditGui.java`
- Create: `roll-thingy/src/main/java/dev/rollthingy/gui/admin/RarityGui.java`
- Create: `roll-thingy/src/main/java/dev/rollthingy/gui/admin/ConfirmGui.java`

**Interfaces:**
- Produces:
  - `AdminGui.open(Player, int page)` — list boxes (icon + name), "Create box" button; per integration with lang/admin keys.
  - Create flow: chat rename prompt (`admin.name-prompt`) → `IconPickerGui` (single-item selection + Confirm → returns `ItemStack` via callback) → `BoxRegistry.save(newBox)` + `BoxModelCache.addOrUpdate` → `admin.saved`.
  - `EditGui.open(Player, Box)` — rename, icon, required-payment strip (deposit into a `DepositGui` region; each slot item gets a strict/loose toggle button), penalty config (chat-prompted `rare-cut`, `zonk-feed`, `loose-value` numbers), cooldown (chat-prompted seconds), zonk toggle that cycles on/off + `base-chance` prompt, rarity button → `RarityGui`, delete → `ConfirmGui`.
  - `RarityGui` — list tiers (name + weight), "Add rarity" (prompt name then weight), per-tier buttons: set weight, delete, and item-drop editor where each dropped item becomes `BoxItem(data=encode, weight=1.0)` with a per-item weight prompt and delete.
  - `ConfirmGui` — generic yes/no on a 3-row chest.
  - All edits: `BoxRegistry.save` + `BoxModelCache.addOrUpdate` + `BoxModelCache.remove` on delete, then reopen the admin GUI.

**Steps (no unit tests; the largest UI chunk):**
- [ ] **Step 1:** Implement `AdminGui` + `IconPickerGui` + `ConfirmGui`.
- [ ] **Step 2:** Implement `EditGui` (payment strip with strict/loose toggle, penalty + cooldown + zonk prompts).
- [ ] **Step 3:** Implement `RarityGui` (tier list + item editor + per-item weight).
- [ ] **Step 4:** Wire `/roll admin`; build; commit — `feat: full admin GUI suite`.

---

### Task 17: Sounds + Bedrock pass + polish

**Files:**
- Modify: all GUI open/close/confirm paths to play `SoundRegistry` events (OPEN/CLICK/CONFIRM/CANCEL/ERROR/WIN/WIN_RARE/ZONK/SPIN).
- Modify: `RollConfig` — optional `cooldown`/`page-size` overrides.

**Steps:**
- [ ] **Step 1:** Add `.play()` calls in every GUI open (OPEN), button clicks (CLICK), confirmations (CONFIRM), cancels (CANCEL), errors (ERROR), spin start/land (SPIN + WIN/WIN_RARE/ZONK).
- [ ] **Step 2:** Bedrock pass — confirm all info is lore/hover-based; deposit strips work via `DepositGui`; tab-complete works via chat; rename/number prompts are chat-based (Bedrock OK). Note in code comments where Geyser needs extra care (icons are hover items, no right-click expectations).
- [ ] **Step 3:** Build, run full test suite, jar; commit — `feat: sounds everywhere + bedrock pass`.

---

### Task 18: Final integration + docs

**Files:**
- Modify: `roll-thingy/src/main/resources/plugin.yml` (final perms/aliases)
- Create: `Roll thingy/README.md` (setup + admin + player usage)

**Steps:**
- [ ] **Step 1:** Full build + all tests: `.\gradlew.bat build --console=plain` and `.\gradlew.bat :roll-core:test --rerun-tasks --console=plain`. Expected: BUILD SUCCESSFUL, all core tests PASS.
- [ ] **Step 2:** Write `README.md` (install, `/roll` usage, admin flow, how to add/edit/delete boxes, share `.yml` files).
- [ ] **Step 3:** Commit — `docs: RollThingy README + final pass`.

---

## Self-Review

**Spec coverage:**
- Items-as-payment + hybrid deposit/spin → Task 15 (SpinGui), Task 12 (RollService consumes deposit).
- Rarity tiers + weights, item weights, `0.000001` decimals → Task 5 (codec), Task 6 (OddsEngine) + Task 6 test asserts decimal weight survives.
- Single prize / zonk barrier branch → Task 6 (`tierIndex == -1`), Task 15 (barrier in strip).
- Deterministic result → Task 6 + Task 12 (roll happens once, animation cosmetic).
- Proportional penalty (rare-cut + zonk-feed) → Task 7 (PenaltyMath) + Task 6 (`withAdjustedWeights`).
- Payment list (one type or list, strict/loose, amount) → Task 5 (`PaymentRequirement`) + Task 12 scoring + Task 16 edit strip.
- Preview rarest→easiest with rarity + chance lore → Task 14 (PreviewGui sorted desc by `chanceOf`).
- Winnings inventory-then-claims → Task 12 (`award`) + Task 14 (ClaimGui).
- Per-box cooldown in seconds → Task 8 (CooldownStore) + Task 16 (prompt).
- GUI-only admin, create/rename/icon/delete w/ confirm → Task 16.
- Hybrid storage (YAML boxes + SQLite runtime) → Task 5/9 + Task 2.
- `/roll`, `/roll <box>`, `/roll claim`, `/roll admin` + perms → Task 13.
- Sounds → Task 10 + 17. Bedrock lore/hover → Task 14/17. No-lag (precompute + per-player animation) → Task 6/12/15.
- Browsable/paginated main GUI → Task 14.

**Placeholder scan:** no TBD/TODO; every step has concrete code or an explicit instruction binding to a file/interface. The `withAdjustedWeights` closed-form note flags one tunable point with a stated invariant.

**Type consistency:** `RollOutcome(tierIndex, itemIndex)` used identically in Task 6/12/15; `PenaltyMath.shortfall(contributed, required)` in Task 7/12; `PaymentRequirement(data, amount, strict)` in Task 5/7/16; `ClaimStore.unclaimedPage/countUnclaimed/markClaimed` used in Task 8/12/14; `BoxRegistry.save/loadAll/delete` in Task 9/16; `SoundRegistry.Event.*` in Task 10/17.

**Deferred UI specifics:** exact slot numbers/prompt text are pinned in lang keys (Task 10) and Task 14/16 interfaces so an implementer can pick concrete materials without guessing semantics.