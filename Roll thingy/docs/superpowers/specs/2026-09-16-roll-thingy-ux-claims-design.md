# RollThingy: UX & Claim System Overhaul

Date: 2026-09-16
Status: Approved

## Summary

Improve first-time usability of the RollThingy plugin: players must immediately
understand (a) exactly what items they have to pay to spin a box, (b) that their
prize goes to a Claims screen when their inventory is full (and how to get it),
and (c) that extremely rare prizes are not "0%". Add an in-game Help book and
tighten labels so the whole flow is self-explanatory for Java and Bedrock
(Geyser) players.

## Problems being fixed

1. **Invisible claims / item loss.** `RollService.award` silently stores a prize
   that doesn't fit, and only stores `leftover.get(0)` — extra leftover stacks are
   destroyed. Players never learn the prize exists, where it went, or how to
   retrieve it.
2. **"0%" rarity display.** `PreviewGui.formatChance` uses `%.6f` for chances
   below 1%; any chance < ~0.0000005 renders as `0%`, which misleads players into
   thinking a prize is unwinnable.
3. **Payment not obvious.** Box detail only shows payment as icon lore text;
   players can't tell what to pay even after clicking a box.
4. **No guidance.** The admin screens and menu don't explain the flow
   (pay → spin → win → claim).

## Design

### 1. Payment panel on BoxDetailGui

- Add a dedicated **Payment required** section to the box detail screen: a row
  (or grid) of slots, one per `PaymentRequirement`, showing the actual payment
  item icon with lore `x<amount>` and the mode (`exact` / `any type`), reusing
  `services.cache().item(req.data())` like SpinGui already does.
- Keep the box icon lore but reduce it to a compact one-line summary
  (e.g. "Payment: 3 items").
- Layout: 6-row ChestGui (slots 9-44 available). Payment section starts at a
  fixed slot (e.g. 22) and flows to the right; an emerald "Pay & Spin" button
  stays prominent (slot 49) at the bottom.
- A header item labeled "Payment required" (green) sits above the section.

### 2. Claim system — visibility + loss fix

- **`RollService.award`**: encode **all** leftover stacks
  (`leftover.values()`), not just `leftover.get(0)`, into one claim row. No prize
  stack is ever dropped.
- **Result screen (`SpinGui.showResult`)**: when the win overflows the inventory,
  the result GUI prize lore + chat message tell the player the prize was stored:
  "Inventory full — prize stored in Claims!".
- **MainGui**: add a "Your prizes (N)" button (bottom row) that opens
  `ClaimGui`; N = live unclaimed count for the player. Show a dim/disabled
  version when N == 0 (still opens the empty claims screen if desired — decide:
  always clickable, shows empty state).
- **Join notification**: new listener on `PlayerJoinEvent`; if
  `claims.countUnclaimed(uuid) > 0`, send a message with the count and how to
  claim (`/roll claim` or the menu button).

### 3. Rarity % — "1 in X"

- `PreviewGui.formatChance`:
  - `chance >= 0.0001` → normal trimmed percentage (existing behavior).
  - `chance < 0.0001` → `1 in <round(1/chance)>`, using grouped formatting for
    readability (e.g. "1 in 1,000,000"), never "0".
- Keep the existing rarity color coding (which already previews "rarest → common").

### 4. Help book

- A writable `PaperBookMeta` book ("Roll Thingy Help", author "RollThingy"),
  built from `lang.yml` strings. Pages:
  1. **How to play** — `/roll` opens the menu; click a box; the detail screen
     shows what to pay; click Pay & Spin.
  2. **Payment & luck** — pay exactly what's shown; wrong items count for less;
     under-paying lowers rare odds and increases the zonk chance.
  3. **Prizes & claims** — prizes go straight to your inventory; if it's full the
     prize is stored; get it via the menu's *Your prizes* button or `/roll claim`.
  4. **Rarity** — how to read the % / "1 in X" rarity readout in *View prizes*.
  5. **Cooldowns** — each box has a cooldown; the button shows remaining seconds.
- Help buttons on: **MainGui**, **BoxDetailGui**, **SpinGui result screen** (and
  optionally the deposit screen header). Clicking opens the book via
  `player.openBook()`.

### 5. Small UX polish

- Spin screen: title/labels make clear the green row is the **payment items**
  zone (update `spin.title` / `spin.deposit` copy).
- Detail screen spin button label: "Pay & Spin" (was "Click to spin").
- All user-visible text lives in `lang.yml` so admins can reword without a code
  change.

## Files touched

- `roll-thingy/src/main/java/dev/rollthingy/RollService.java` (award leftover fix)
- `roll-thingy/src/main/java/dev/rollthingy/gui/BoxDetailGui.java` (payment panel)
- `roll-thingy/src/main/java/dev/rollthingy/gui/SpinGui.java` (result overflow
  message, help button, labels)
- `roll-thingy/src/main/java/dev/rollthingy/gui/MainGui.java` (prizes button, help)
- `roll-thingy/src/main/java/dev/rollthingy/gui/PreviewGui.java` (1-in-X)
- `roll-thingy/src/main/java/dev/rollthingy/gui/ClaimGui.java` (if reachable from
  menu; help)
- NEW `roll-thingy/src/main/java/dev/rollthingy/gui/HelpBook.java` (book builder)
- NEW `roll-thingy/src/main/java/dev/rollthingy/listener/ClaimNoticeListener.java`
  (join notification)
- `roll-thingy/src/main/resources/lang.yml` (all copy + book pages)
- NEW `roll-core/src/test/java/dev/rollthingy/core/misc/ChanceFormatTest.java`
  (1-in-X never 0)
- NEW `roll-thingy` test for award storing multiple leftovers (or merge into
  existing service test if one exists)

## Testing

- `ChanceFormatTest`: borderline cases (0.0001, 0.0000999, 5e-10, 0, 50) assert
  never "0", correct "1 in X", grouping.
- Award fix test: inventory full → multiple leftover stacks all present in one
  claim row, nothing dropped.
- Build green: `.\gradlew.bat :roll-thingy:shadowJar :roll-core:test --console=plain --rerun-tasks`.
- Manual Geyser note: books and GUI items render fine on Bedrock; all actions are
  plain clicks (no right-click needed).

## Non-goals

- No economy integration.
- No new permissions.
- No change to odds math or admin flows (admin labels stay as-is unless their
  wording is already covered by this pass).
- No persistence schema changes.