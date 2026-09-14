# Roll-Thingy — Gambling / Lootbox Spin Plugin — Design

Date: 2026-09-14
Status: Approved (brainstormed with user)

## Overview

A highly configurable gambling plugin ("Roll thingy") in which players pay item(s)
to spin a horizontal slot-machine animation on a **mystery box** and win one
prize. Admins create and fully manage boxes (payment, rarities, odds, zonk,
cooldowns) through GUIs only. Runs on Paper + Geyser (Bedrock).

Skill flow per user choices: items-as-payment (not currency), chest-based GUIs,
Bedrock-safe, low-lag, sound-driven.

## Core decisions (from brainstorming)

1. **Payment**: players deposit items in a strip, then hit Spin; items are consumed.
2. **Odds model (B)**: rarity tiers each carry a weight; items inside a tier carry
   their own weight/chance. Engine picks tier first, then item. Supports decimal
   weights such as `0.000001` on a 1–100 style scale (normalized).
3. **Single prize** per roll. **Zonk** = barrier block, per-box base chance plus
   contribution from wrong-payment penalty.
4. **Result is deterministic** (server decides instantly; animation is cosmetic).
5. **Wrong payment penalty is proportional**: payment shortfall scales the reduction
   of rare chances and the increase of zonk chance. Accepts any deposited items;
   matching `required list` items count fully, non-matching count less.
6. **Required payment** supports one item type or a list; each has a strict/loose
   toggle — strict requires exact NBT/meta, loose matches by item type only.
   Amount = stack size (e.g. 16 iron).
7. **Preview screen**: clicking a box shows "Check items" and "Open". "Check items"
   opens a chest of all possible items sorted rarest → easiest, lore shows rarity
   color + real chance %. Hover-based reading (Bedrock-friendly).
8. **Winnings**: inventory first; if inventory full → claims screen (`/roll claim`).
9. **Cooldown** per box between spins (seconds), set in admin GUI. Result
   deterministic. Main GUI browsable & paginated like the AH shop.
10. **Admin edits**: GUI only. Create/rename/icon/payment/penalty/cooldown/zonk/
    rarity manager/delete, each with confirmation.
11. **Storage (hybrid)**: YAML box files (`boxes/<id>.yml`) + SQLite runtime tables
    (`cooldowns`, `claims`).
12. **Architecture (approach A)**: definition-first with a cached odds model.
13. Commands: `/roll`, `/roll <box>`, `/roll claim`, `/roll admin`.
    Permissions: `roll.use` (default true), `roll.admin` (default op).

## Architecture

- **Box YAML files** under `boxes/` folder — one file per box. New file → box
  auto-appears (scanned at startup; admin GUI writes file + swaps live model).
- **BoxRegistry** — loads/parses box files, exposes live `Box` objects, performs
  atomic model swap on save, ID generation, delete.
- **OddsModel** — pre-normalized weighted tree built at parse: tier total weights,
  item weights within tier, chance% computation for preview. Pure arithmetic.
- **RollService** — entry point for a spin: validates cooldown, computes payment
  score, applies proportional penalty, deterministically picks result (tier → item
  or zonk), awards prize (inventory or claim), persists cooldown timestamp.
- **ClaimService** — claims table CRUD; `/roll claim` GUI.
- **CooldownStore** — per player/box last-spin timestamp, SQLite.
- **SqliteDatabase** — reused pattern from AuctionHouse core (in-memory for tests).
- **SoundRegistry** — sounds.yml driven, keys below.
- **ChestGui / DepositGui** — reuse pattern from AuctionHouse core (event handling,
  pagination, slot actions).
- DMZ: no per-tick world/broadcast polls. Spin animation = bounded per-player
  scheduled tasks only.

## Data model

### Box YAML (boxes/<id>.yml)

```yaml
id: legendary-crate
name: "Legendary Crate"
icon:
  material: ENDER_CHEST
  amount: 1
  # full ItemStack (NBT/meta) if icon is a custom item
required-payment:
  - material: DIAMOND
    amount: 5
    strict: true
  - material: EMERALD
    amount: 10
    strict: false
penalty:
  loose-value: 0.5          # fraction a non-matching item is worth
  rare-cut: 50              # % of rare-tier odds removed at full shortfall
  zonk-feed: 20             # % of zonk odds added at full shortfall
cooldown-seconds: 5
zonk:
  enabled: true
  base-chance: 5.0
tiers:
  - name: "Legendary"
    weight: 1.0
    items:
      - item: { full ItemStack serialized }
        weight: 1.0
      - item: { ... }
        weight: 0.000001
  - name: "Common"
    weight: 90.0
    items: [ ... ]
```

- Weights may be decimal (`0.000001`). Engine normalizes over total box weight.
- Items stored with full NBT so enchanted/named/custom items survive.

### SQLite tables

```sql
cooldowns(player_uuid TEXT, box_id TEXT, last_spin_at INTEGER, PRIMARY KEY(player_uuid, box_id));
claims(id INTEGER PK, player_uuid TEXT, items_data TEXT, source TEXT, created_at INTEGER, claimed_at INTEGER);
```

### Penalty math (proportional)

- Required value R = sum over required items of `amount * (strict ? 1 : loose-value)`
  — strict items only counted if exact NBT.
  Actually: required list defines the target; deposits are scored:
  `score = Σ matched-amount (strict item exact match → full; loose item type match → full; anything else → loose-value per item)`.
  Shortfall `s = clamp(1 - score/R, 0, 1)`.
- Apply: rare/difficult tier weights are multiplied by `(1 - s * rare-cut / 100)`;
  zonk chance gets `+ s * zonk-feed / 100`; remaining difference renormalized
  proportionally across other tiers. (Exact formula to be finalized in plan;
  invariant: total probabilities sum to 1.)

### Zonk placement in the odds

Zonk is a **separate weighted branch** from the item tiers — not a tier. During a
roll the engine first chooses between (a) the item pool (tiers) and (b) the zonk
pool, using the total box weight vs total zonk weight (base-chance plus any
penalty feed). If the item pool is chosen it then pick a tier, then an item inside
it. The preview screen and the spin strip include the zonk (barrier block) as a
possible slot so the displayed odds always sum honestly to the real odds.

## Player UX

### `/roll` main GUI
- Chest, boxes as icons in content grid, paginated.
- Bottom row: filler glass, prev / next arrows (corners).
- Click box → detail screen.

### Detail screen
- Box icon, required-payment lore, buttons: "Check items", "Open" (and cooldown
  shown on the Open button if active).

### Check-items screen
- Chest of all items sorted rarest → easiest; lore = rarity color+name and chance %.
- Paginated if many.

### Spin screen
- Deposit strip (top area) for payment items; Spin button.
- On Spin: cooldown check → consume deposit → deterministic result → animation.
- Horizontal strip of slots scrolling for ~2–3s, decelerating onto center cursor;
  mixture weighted by real chances (includes zonk barrier).
- Award: inventory first, else claim.
- Sounds during animation; win vs zonk sounds on landing.

### `/roll claim`
- Claims list GUI with collect per-row and collect-all.

### Bedrock (Geyser)
- All UI is chest slots + item lore (hoverable). No chat-driven store interaction
  besides optional rename prompts (admin).
- Deposit strip works via Geyser; animation uses inventory updates (Geyser renders).

## Admin UX (all GUI)

### `/roll admin` — management GUI
- List boxes (icon + name), paginated; "Create box" button.
- Delete per box with confirm.

### Create box
- Chat rename prompt → icon picker (item selection chest, pick one, confirm)
  → writes `boxes/<id>.yml` and live-registers.

### Edit box screen
- Rename, Icon, Required-payment strip (drop items; strict/loose toggle per slot;
  amount = stack size), Penalty config, Cooldown (seconds), Zonk (on/off + %),
  Rarity manager, Delete (confirm).

### Rarity manager
- List tiers with weight; add/rename/weight/delete.
- Click tier → item editor: drop items, set weight/chance, delete per item.

## Sounds (sounds.yml keys)

`open`, `click`, `confirm`, `cancel`, `spin`, `win`, `win-rare`, `zonk`, `error`.

## Commands & permissions

| Command | Permission | Default |
|---|---|---|
| `/roll` | `roll.use` | true |
| `/roll <box>` | `roll.use` | true |
| `/roll claim` | `roll.use` | true |
| `/roll admin` | `roll.admin` | op |

Tab-completion for subcommands and box names.

## Performance requirements

- Odds precomputed per box; spin = O(1) arithmetic.
- YAML parse: startup + admin-save only; atomic swap.
- Cooldown write: 1 small SQLite write per spin.
- Claim: only when inventory full.
- Spin animation: per-player scheduled tasks; no server-wide loops.
- ChestGui foundation reused (single inventory query per player, batched).

## Testing

- OddsModel: normalization, decimal weights (e.g. 0.000001), tier→item selection,
  chance% computations, penalty shortfall scaling, renorm invariant (sum = 1).
- YAML parse/round-trip: strict/loose, full NBT item storage.
- RollService: deterministic result, cooldown enforcement, payment scoring,
  claims on full inventory.
- CooldownStore / ClaimStore: CRUD + paging.
- GUI flows (via MockBukkit-style harness where feasible): pagination, admin
  create/edit/delete, detail/check/spin screens, Bedrock-compat (lore-based info).

## Non-goals (YAGNI)

- No currency/economy integration (payment is items only).
- No physical world chests/entities; all GUIs.
- No auto-shop/wandering traders.
- No multi-win per spin.