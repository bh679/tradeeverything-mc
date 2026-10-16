# Trade Everything

A Minecraft mod that gives every villager an extra **"Trade Anything"** slot at the
top of its trade list.

| Minecraft | Fabric | Forge | NeoForge |
|---|---|---|---|
| 1.20.1  | ✅ | ✅ | — (use the Forge jar) |
| 1.21.1  | ✅ | ✅ | ✅ |
| 1.21.11 | ✅ | ✅ | ✅ |
| 26.3    | ✅ | ✅ | ✅ |

Put **any item** in — the villager exchanges it for an item it's buying (wheat from a
farmer, coal from an armorer, paper from a librarian…). Exchange rates are driven by a
per-item value system based on item rarity: roughly **10–20 common items = 1 emerald's
worth**, scaling all the way up (one diamond buys a stack of coal).

## Features

- Works on villagers and wandering traders (toggleable)
- **Space** refills the slot with the last item you traded, as many as you're
  carrying — the repeat-trade shortcut vanilla gives its own trades
- Exact-stack matching: enchanted or damaged items are valued and matched precisely
- No profession XP, no restock interference, never saved to villager data
- One item at a time: anything worth at least one payout item is quoted singly with the
  change rounded down (1 ominous banner → 3 emeralds); only items too cheap to buy a
  single payout item are batched. Set `prefer_single_item_trades: false` for the old
  exact-change batching
- Fully configurable: `config/tradeeverything.json` — rarity tier values, per-item
  overrides, result multiplier, count caps
- Java API for other mods: `games.brennan.tradeeverything.api.TradeEverythingApi`

## Notes

- If the item you insert also matches one of the villager's real trades, the top slot
  takes priority for auto-matching — click the specific trade row to use the real one.
- Third-party mods listening to villager trade events will observe synthetic trades.
- The space-bar refill is client-side: on a server it works for players who have the
  mod installed, and vanilla clients simply keep filling the slot by hand.

## Building

The build is [Stonecutter](https://stonecutter.kikugie.dev/) + Architectury: one
source tree (`src/`, written against 1.21.1) is copied into each Minecraft version,
with version differences as `//? if` blocks and the pure renames as replacements in
`stonecutter.gradle.kts`. Per-version dependency numbers live in
`versions/<mc>/gradle.properties`.

```bash
./gradlew collectReleaseJars   # every version x loader → build/release/
./gradlew chiseledTest         # unit tests on every version
./gradlew :fabric:1.20.1:runClient
```

The 1.21.1 jars are named `tradeeverything-<loader>-<version>.jar`; every other
version adds `+mc<version>`. Gradle picks up whichever JDK each version needs (17,
21 or 25).

## Licence

PolyForm Shield 1.0.0 — see [LICENSE](LICENSE).
