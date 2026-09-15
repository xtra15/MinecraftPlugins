# RollThingy Admin/Player GUI UX Redesign

Date: 2026-09-15

## Problem

The user reported the admin UX is confusing and "it doesn't show the item needed when you first
open". Quoting the reports:

- The icon picker screen doesn't clearly preview the current icon.
- The box edit screen opens with an empty middle row and no "drop payment items here" guidance or
  current requirements listed.
- The player spin screen doesn't list which payment items are required.
- Overall: the whole admin flow (create -> edit -> rarities -> icons -> payment) is confusing.

Root causes found in exploration:

- Every deposit screen is a `DepositGui` (strip logic) but renders as a blank row with no prompt,
  no instruction, and no preview of current saved values.
- `EditGui` is simultaneously a drop screen (payment strip slots 9-17) AND an options screen
  (buttons 45-53 for rarities/rename/icon/penalty/cooldown/zonk/delete), so it does two jobs in one
  screen.
- Chat prompts (rename, cooldown, penalty numbers, tier/item weights) are terse and give no
  example, no current value, and generic error text on bad input.

## Decisions (from brainstorm)

- All four drop screens get guidance; no screen is left as a blank strip.
- Keep chat input for names/numbers but make prompts self-describing (current value + example +
  formatted error on bad input). No GUI number steppers.
- Show prompt + current values by default on every drop screen.
- Split the box editor: `EditGui` becomes options-only; payment editing moves to a new
  `PaymentGui`.

## Design

### Section 1 — Shared drop-screen pattern

Applied to every deposit screen:

- Back always at slot 0 (top-left, spectral arrow).
- Confirm always at bottom-right (slot 53 on 6-row screens, slot 26 on 3-row screens).
- Every screen shows an **instruction banner** (paper item with step-by-step lore) in a fixed spot.
- Every screen shows a **live current-values preview** — never requires clicking.
- The strip stays empty to accept drops, framed by colored "bracket" panes at each end so the drop
  zone reads as a slot.

### Player SpinGui

- Row 0: back (0), box icon (4) with lore "Place the required items in the green row, then click
  Spin".
- Strip 9-17 unchanged.
- New "Required payment" panel in row 2 (slots 18-26): one tile per requirement showing the actual
  item (strict) or a material placeholder (type-only), with lore like `x3 · exact` /
  `x3 · any type`. No payment set -> "Spin for free!".
- Spin button (49) unchanged, gains a hint in lore.

### TierEditGui item-drop screen

- Add instruction banner and "current tier items" preview (existing items with weights) in the
  panel row.
- Confirm moved from 40 to 53.

### Section 2 — Admin: split the box editor

EditGui becomes a plain options screen (no deposit strip):

- Box icon at 4 with id lore.
- One button per setting, stacked 45-53, each button's lore shows its current value:
  - 45 Payment (SHULKER_BOX): lists current requirements e.g. `3x DIAMOND (exact)` or `None set` -> opens new PaymentGui
  - 46 Rarities: `N tiers`
  - 47 Rename: `Current: <name>`
  - 48 Icon: `Current: DIAMOND`
  - 49 Penalty: `loose 0.5 · rare-cut 50 · zonk-feed 20`
  - 50 Cooldown: `5 seconds`
  - 51 Zonk: `enabled · base 5%`
  - 53 Delete (lava bucket)

New PaymentGui (admin, DepositGui):

- Title `Payment — <box>`, back at 0, strip 9-17, instruction banner, strict/loose toggle button
  (48, label shows current saved mode), save at 53.
- Row 2 preview panel shows existing requirements (icons + counts + exact/type-only) so the admin
  sees what's already set before touching anything.
- Saving replaces current requirements (same semantics as today).

IconPickerGui (3-row):

- Strip 0-8 stays; current icon preview at 13 shows icon + name + material lore ("Current icon:
  DIAMOND"); instruction banner added; confirm moved 22 -> 26 (bottom-right), cancel 16 -> 18;
  drop-zone bracket panes at 9 and 17.

### Section 3 — Chat prompts + lang keys

- Rename: `Type a new box name in chat (current: <name>)` -> on blank, clear re-prompt.
- Cooldown: `Type cooldown in seconds (current: 5s), e.g. "30"`.
- Penalty: `Type: <loose-value> <rare-cut> <zonk-feed> (current: 0.5 50 20), e.g. "0.7 30 15"` ->
  on malformed input, error re-states the exact format.
- Tier/item weights: `Type weight (current: 1.0), e.g. "2.5"` -> invalid weight re-prompts with
  format.
- New strings go in `lang.yml` under `admin.*`: `payment-save`, `payment-current`,
  `penalty-prompt`, `cooldown-prompt`, `weight-prompt`, `item-weight-prompt`, `rename-prompt`,
  `drop-banner`, `spin.required-payment`, `icon.current`.

## Scope guard

- No changes to core store/db/odds/payout/penalty math logic.
- No new dependencies.
- Verification: build + existing core tests + manual click-through of every screen.

## Files touched (planned)

- `roll-thingy/.../gui/SpinGui.java`
- `roll-thingy/.../gui/BoxDetailGui.java`
- `roll-thingy/.../gui/admin/EditGui.java`
- `roll-thingy/.../gui/admin/PaymentGui.java` (new)
- `roll-thingy/.../gui/admin/IconPickerGui.java`
- `roll-thingy/.../gui/admin/TierEditGui.java`
- `roll-thingy/.../gui/admin/RarityGui.java`
- `roll-thingy/.../gui/admin/AdminGui.java` (create-flow icon prompt wording)
- `roll-thingy/.../resources/lang.yml`