# Auction House Plugin — Design

**Date:** 2026-09-06
**Project:** Minecraft Paper Plugins Custom (repo: https://github.com/xtra15/MinecraftPlugins)
**Target server:** Paper 1.26.x, Java + Bedrock (Geyser/Floodgate) players, small private server.

## Overview

An offer-based Auction House plugin. Players list items for trade; other players make **item-bundle offers** via a chest GUI. The seller accepts or rejects each offer. Every won or returned item routes through a **claim system** (mailbox-style chest GUI) rather than dropping directly into inventory. Economy support exists in code but is **disabled by default** (`economy.enabled: false`) so the plugin's core function is pure item trading.

This is the first plugin in a multi-plugin workspace: a `core` library (economics, claims, GUI, messaging, DB) lives in a graded module and is shaded + relocated into each plugin jar so future plugins share it.

## Workspace layout

```
Minecraft Paper Plugins Custom/
├── settings.gradle.kts            # includes :core and :auction-house
├── build.gradle.kts               # root convention (Java 21+, shadow, relocation)
├── gradle.properties
├── core/                          # shared library
│   └── src/main/java/dev/ah/core/
│       ├── economy/               # optional: balances + ledger (unused while disabled)
│       ├── claim/                 # item claims store + GUI
│       ├── db/                    # SQLite wrapper + migrations
│       ├── gui/                   # ChestGui builder, pages, confirm dialogs
│       ├── msg/                   # MiniMessage, sound registry, lang.yml loader
│       ├── geyser/                # Bedrock detection (Floodgate if present)
│       └── misc/                  # ItemCodec (NBT Base64), pagination, uuid utils
├── auction-house/                 # the plugin
│   └── src/main/java/dev/ah/auctionhouse/
│       ├── listing/               # listings, offers, expiry sweep
│       ├── gui/                   # AH menus
│       ├── command/               # /ah command tree + tab completion
│       └── listener/              # join notifications, click handlers
└── build/                         # auction-house-<ver>.jar (core shaded inside)
```

Each plugin produces **one self-contained jar** (core shaded in, packages relocated to `.auctionhouse.core`). No external runtime dependencies.

## Feature list

1. Sellers list items (any item, full NBT preserved) with a chosen duration.
2. Buyers make offers consisting of **bundles of items**, via a deposit GUI with a confirm step.
3. Sellers review offers on an **offer board** and Accept / Reject / Open-item.
   - Left column = the auctioned item(s); right column = the offered item(s).
   - "Open item" opens a separate viewer to inspect shulkers and NBT before deciding.
4. Accepted offers: buyer's offered items are taken, auctioned item(s) go into the **buyer's claim**; offered items go into the **seller's claim**.
5. Rejected offers: offered items return to the **offerer's claim**.
6. Notifications:
   - Seller gets an in-chat notice each join while their listing has pending offers.
   - Offerer gets notified (in-game; if offline, on next join) when their offer is accepted or rejected.
   - Players are reminded each join about unclaimed items until they claim them.
7. Main AH menu: paginated listings, sort options, command-based search, custom sounds.
8. `/ah` command tree (`/ah`, `/ah sell`, `/ah search <term>`, `/ah my`, `/ah offers`, `/ah claim`, `/ah admin`, `/ah help`, `/ah reload`).
9. **Admin GUI** (permission `ah.admin`): user list → user detail → listing offers → offer board, plus a **Sales Log** of who sold to whom.
10. Expiry: timed listings; on lapse the auctioned item returns to the seller's claim. 60-second expiry sweep.
11. Everything customizable: config.yml, lang.yml, gui.yml, sounds.yml.

## Data model (SQLite)

| Table | Columns / purpose |
|---|---|
| `listings` | id, owner_uuid, item_data (Base64 full-NBT), price (nullable, only used when economy on), duration_ms, created_at, expires_at, status (ACTIVE/SOLD/EXPIRED/CANCELLED) |
| `offers` | id, listing_id, offerer_uuid, items_data (Base64), status (PENDING/ACCEPTED/REJECTED/WITHDRAWN), created_at, decided_at |
| `claims` | id, owner_uuid, items_data, source_type, source_id, created_at, claimed_at |
| `sales_log` | listing_id, seller_uuid, buyer_uuid, item_data, outcome (ACCEPTED/BOUGHT), accepted_at |
| `balances` | uuid, balance (only written when economy enabled) |

**Persistence rules**

- Item storage through `ItemCodec` (Base64 of full serialized ItemStack + extra NBT), never by name or material — custom items and NBT survive round-trips.
- Transactions are atomic: writing an accepted offer (buyer items in → seller claim; auctioned items → buyer claim) happens in one SQLite transaction. An item is never lost between steps.
- Expiry sweep writes listing status + claim return atomically.

## GUI inventory

All GUIs are chest inventories (Bedrock-native via Geyser translation). Titles, sizes, fill items, lore formats, and slot positions are config-driven.

1. **Main AH menu** — paginated listing rows (item icon + price/lore), sort toggle buttons, search slot.
2. **Sell** — deposit auctioned item(s); duration picker; confirm page. (Price input only when economy enabled.)
3. **Offer** — buyer deposits a bundle of items; confirm summary page before the offer goes live.
4. **My listings** — each row shows pending offer count; click to open that listing's Offer board.
5. **Offer board** — left column: auctioned item. Right column: the offer's items. Buttons: Accept, Reject, Open-item. "Open item" opens a read-only viewer (useful for shulkers/NBT).
6. **Claim** — paginated withdraw menu; claim rows take items back to inventory; empty rows collapse.
7. **Admin GUI** — user list → user detail (listings + counts) → listing → offer board; tabs: Overview / Sales Log.

## Commands

`/ah` main menu. Subcommands with tab completion:
default (`ah`), `sell`, `search <term>`, `my`, `offers`, `claim`, `admin`, `help`, `reload`.

- Roles: `ah.use` (default) for player commands; `ah.admin` for `admin` + `reload`.
- Search and duration entry are command/quick-choose based so Bedrock players never need to type inside a GUI.

## Notifications

- `PlayerJoinEvent`: count pending offers on the player's listings and unclaimed claim rows; compose configurable messages (chat or actionbar via config). Repeat each join until resolved.
- Offer outcome messages (accepted/rejected) are sent immediately if the offerer is online, otherwise stored and flushed on next join. Sound plays on deliver.

## Economy (optional, disabled by default)

`config.yml` `economy.enabled: false`. When enabled:

- Sellers may set a `price` (BIN) on listings; buyers may buy instantly or money-offer.
- Buyer balance is charged on BIN/acceptance; seller money routes to the seller's claim.
- `balances` table and ledger are operationalized by the optional `core/economy` module (Vault-independent, self-managed).
- The plugin's core item-trading path never depends on the economy path; toggling the flag only changes visible UI fields and transaction logic.

## Customization files

| File | Contents |
|---|---|
| `config.yml` | economy.enabled, limits (max listings/offers per player, max items per offer), durations list, defaults, sweep interval, permission defaults |
| `lang.yml` | every user-facing message, MiniMessage tags + placeholders |
| `gui.yml` | GUI titles/sizes, fill items, lore formats, button slots per screen |
| `sounds.yml` | sound name, pitch, volume, and enabled per event (open, click, offer received, offer accepted, offer rejected, sale, expiry) |

## Geyser compatibility

- Chest GUIs render natively for Bedrock players via Geyser's inventory translation — no custom forms needed.
- All text entry happens through command arguments (`/ah search <term>`) so Bedrock typing works.
- Floodgate API is detected and used only if present (for Bedrock player detection/QoL); everything else degrades gracefully to Java behavior.

## Error handling & reliability

- Insufficient stack / invalid item (air, too large) rejected at deposit time.
- Inventory-full guard on claim withdrawal (leftover stays in claim).
- Self-offer on own listing is blocked.
- Expired listings cannot be offered on.
- SQLite write failures surface in console with the player refunded/retained safely via transaction rollback.
- All stateful writes go through the `db` layer; the GUI is a stateless view of the DB.

## Testing

- JUnit for core logic: ItemCodec round-trip, offer accept/reject state machine, SQLite store (listings/offers/claims), expiry sweep, pagination.
- Manual server checklist: full Java flow, full Bedrock (Geyser) flow, reload persistence.

## Out of scope (this version)

- Bidding wars / live auction countdown timers.
- Economy features are skeleton-only until `economy.enabled` is turned on.
- Cross-server / bungee sync.