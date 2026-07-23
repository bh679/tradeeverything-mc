# Trade Everything

A Minecraft mod (Fabric / Forge / NeoForge, MC 1.21.1) that gives every villager an
extra **"Trade Anything"** slot at the top of its trade list.

Put **any item** in — the villager exchanges it for an item it's buying (wheat from a
farmer, coal from an armorer, paper from a librarian…). Exchange rates are driven by a
per-item value system based on item rarity: roughly **10–20 common items = 1 emerald's
worth**, scaling all the way up (one diamond buys a stack of coal).

## Features

- Works on villagers and wandering traders (toggleable)
- Exact-stack matching: enchanted or damaged items are valued and matched precisely
- No profession XP, no restock interference, never saved to villager data
- Fully configurable: `config/tradeeverything.json` — rarity tier values, per-item
  overrides, result multiplier, count caps
- Java API for other mods: `games.brennan.tradeeverything.api.TradeEverythingApi`

## Notes

- If the item you insert also matches one of the villager's real trades, the top slot
  takes priority for auto-matching — click the specific trade row to use the real one.
- Third-party mods listening to villager trade events will observe synthetic trades.

## Building

```bash
./gradlew build
```

Jars land in `{fabric,forge,neoforge}/build/libs/`.

## Licence

PolyForm Shield 1.0.0 — see [LICENSE](LICENSE).
