# Trade Everything — Config Guide

Every villager & wandering trader gets one extra trade at the **top** of its list that accepts **any item**. What each item is worth is controlled by a config file you can edit.

## Where's the file?

`.minecraft/config/tradeeverything.json`

It's created automatically the first time the mod runs. Edit it, then reload the world (or restart) to apply.

## How values work

Values are in **sixteenths of an emerald**:

- `16` = one emerald's worth
- `1` = 16 of that item per emerald
- `64` = 4 emeralds each

**Resolution order** for any item: item override → recipe-derived value → rarity default.

## The two things you'll usually edit

**`item_overrides_sixteenths`** — set an exact price for a specific item by ID:

```json
"minecraft:diamond": 64
```

Add any `"modid:item": value` line here. Works for modded items too.

**`rarity_values_sixteenths`** — fallback price for anything *not* overridden, by vanilla rarity tier (`common` / `uncommon` / `rare` / `epic`). Broad, since most items are `common`.

## Editing rules (important)

- **Valid edits always apply.** If your JSON parses, your changes take effect.
- A **bad value** (negative, zero, or not a number) → just that one line is dropped, the rest is kept. Check the log for a warning.
- A **missing key** → filled from defaults. **Unknown key** → ignored.
- A file with a **syntax error** (missing comma, unclosed brace) can't be read at all → the mod uses defaults for that run and **leaves your file untouched** so you can fix the typo. Look for `failed to read ... — using defaults` in the log.

> Tip: paste your file into a JSON validator before loading if trades aren't changing.

## Other settings

| Key | What it does |
|---|---|
| `result_multiplier` | Villager's payout margin. `0.75` = pays 75% of value (default). `1.0` = full value. |
| `max_cost_count` / `max_result_count` | Clamp trade stack sizes (1–64). |
| `allow_undervalued_trades` | Allow trades that pay less than item value. |
| `enable_wandering_trader` | Give wandering traders the slot too. |
| `derive_values_from_recipes` | Auto-price craftable items from their recipes. |
| `enchantment_value_per_level_sixteenths` | Added value per enchantment level. |
