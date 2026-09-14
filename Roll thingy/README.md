# RollThingy

A GUI-driven gambling plugin for Paper servers (Java + Bedrock via Geyser). Players pay items to
spin a mystery box and win one prize; admins build and manage boxes entirely through chest GUIs.

- **Paper API:** `26.1.2.build.74-stable`
- **Java:** 25
- **Commands:** `/roll`, `/roll <box>`, `/roll claim`, `/roll admin`

## Install

1. Copy `roll-thingy/build/libs/RollThingy-1.0.0.jar` into your server's `plugins/` folder.
2. Restart the server (or `load` it). The plugin creates `plugins/RollThingy/` with:
   - `config.yml` — `page-size` (default 36) and `fill-item` (default `GRAY_STAINED_GLASS_PANE`)
   - `lang.yml` — every player/admin message (MiniMessage format)
   - `sounds.yml` — sounds for open/click/confirm/cancel/spin/win/win-rare/zonk/error
   - `boxes/` — one `.yml` file per box. Share these files to share boxes between servers.
   - `data.db` — SQLite runtime state (cooldowns + unclaimed prizes).

## Player usage

| Command | What it does |
| --- | --- |
| `/roll` | Open the main box browser (paginated). |
| `/roll <boxId>` | Open a box's detail screen. |
| `/roll claim` | Claim prizes that didn't fit your inventory. |

**To spin:**

1. Open a box's detail screen and click **Check items** to view all possible prizes
   (sorted rarest → easiest, each showing its rarity and chance %).
2. Click **Open** below. Deposit the required payment items into the white strip (slots 9–17)
   — shift-click or drag them in. Click **Spin!**.
3. The reel scrolls horizontally, decelerates, and lands on your prize. The result is decided
   instantly server-side; the animation is purely cosmetic.
4. Prize goes straight into your inventory; if it doesn't fit it goes to `/roll claim`.

**Payment rules:** a box may demand one item type or a list. Each payment requirement is either
*strict* (must be the exact item, same NBT/enchantments) or *loose* (any item of that type).
If you short-pay or include wrong items, a proportional penalty applies: rare-tier odds shrink and
the ZONK chance grows.

## Admin usage

`/roll admin` (permission `roll.admin`) opens the admin screen.

- **Create a box** — click the green block, type a name in chat, pick an icon from the picker.
- **Edit a box** — click any box icon:
  - **Save payment items** — place payment requirements in the strip (each stack = 1 requirement,
    amount = stack size). Use the arrow button to toggle strict / loose for the next save.
  - **Rarities & prizes** — add rarity tiers (name + weight), then inside a tier drop items to
    add prizes, click them to set a per-item weight, and adjust/delete the tier.
  - **Penalty** — chat-prompted `loose-value rare-cut zonk-feed`.
  - **Cooldown** — per-box cooldown in seconds (chat-prompted).
  - **Zonk** — toggles the ZONK branch on/off (ZONK shows as a barrier item in the reels/preview).
  - **Rename / icon / delete** (delete asks for confirmation).

Every change saves instantly to the box's `.yml` file and the live odds cache.

## Odds model

Each rarity tier has a weight; each prize item inside a tier has a weight. ZONK is a **separate
weighted branch** (not a tier) with `base-chance`. Very small weights (e.g. `0.000001`) are fully
supported and round-trip exactly through YAML. Weight = higher means *easier* to hit; the preview
screen always sorts rarest → easiest with the exact per-item probability.

## Permissions

| Permission | Default | Purpose |
| --- | --- | --- |
| `roll.use` | true | Open boxes, spin, claim. |
| `roll.admin` | op | Admin GUI, `/roll reload`. |

`/roll reload` re-reads all box `.yml` files from disk (useful after sharing files between servers).

## Backups / sharing

- Box definitions live in `plugins/RollThingy/boxes/*.yml` — back them up or copy them between
  servers freely.
- Cooldowns and unclaimed prizes live in `plugins/RollThingy/data.db`.