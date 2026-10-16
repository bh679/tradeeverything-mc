# Product Engineer — Trade Everything

Sibling mod of the Dungeon Train family (AIN / AIS / PlayerMob / ECP / **TE**).

## Quick Reference

| | |
|---|---|
| Mod id | `tradeeverything` |
| Group | `games.brennan.tradeeverything` |
| Version | `gradle.properties` → `mod_version` |
| MC versions | 1.20.1 (fabric, forge) · 1.21.1 + 1.21.11 (fabric, forge, neoforge) · 26.3 (fabric, forge, neoforge) |
| Build | `./gradlew collectReleaseJars` (all variants → `build/release/`), `./gradlew chiseledTest` |
| Key jars | `build/release/tradeeverything-<loader>-<v>[+mc<mc>].jar` — 1.21.1 has no suffix |
| Release | `gh workflow run release.yml -f tag=v<version>` (creates the tag; never tag manually) |
| Repo | `bh679/tradeeverything-mc` |

## What it does

Every villager (and wandering trader, config-toggleable) gets one extra synthetic
trade at the TOP of its list. The slot accepts **any item**; the villager pays out
an item it is buying (first non-emerald cost item across its real offers; emerald
fallback). Exchange rates come from a per-item value system in sixteenths of an
emerald: vanilla `Rarity` defaults (COMMON=1 → 16 items/emerald) + config overrides
(`<config>/tradeeverything.json`) + a runtime API for consumer mods.

## Structure

Stonecutter multi-version build (adapted from FusionMods/stonecutter-architectury-template).
`src/` is the common code, written against **1.21.1** (`vcsVersion`); Stonecutter copies
it per version into `versions/<mc>/`. Pure renames between versions are string
replacements in `stonecutter.gradle.kts`; behaviour/signature differences are
`//? if >=1.21.11 { … }` blocks (1.20.1 = `<1.20.5`, 26.x = `>=26`). `compat/Costs`
hides ItemCost (1.20.5+) vs ItemStack costs (1.20.1). 26.x builds via
`loom-no-remap` (`build.26.gradle.kts`). Forge needs `MixinConfigs` in the jar manifest (`forge.mixinConfig`) — it ignores `[[mixins]]` in mods.toml.
Common compiles against sponge-mixin 0.16.5 (single `@At` in `@Redirect`; 0.17.4's `At[]` crashes Forge/NeoForge MixinExtras).
Builds run serially (`org.gradle.parallel=false`):
Architectury Transformer races across MC versions otherwise.

- `src/` — all logic. `config/TradeEverythingConfig` (Gson, immutable snapshot),
  `trade/` (ItemValuation, TradePricer, SyntheticOfferFactory, DefaultBuyItemSelector,
  OfferResync, SyntheticOffer duck), `api/TradeEverythingApi` (consumer-facing),
  `mixin/` — the whole feature is mixin-driven:
  - `AbstractVillagerTradingMixin` — inject/remove the synthetic offer per trading
    session; strip-on-save so it NEVER reaches NBT; resetUses after each trade.
  - `MerchantMenuMixin` — server-side dynamic repricing on `slotsChanged` + offer resync.
  - `MerchantOfferMixin` — synthetic flag duck.
- `fabric/`, `forge/`, `neoforge/` — thin entrypoints (ConfigDir.set + init). No loader events.

## Invariants

- Synthetic offers: `xp=0`, `priceMultiplier=0`, huge maxUses + resetUses — no
  profession XP, no restock impact, exact counts.
- The synthetic offer must live in the entity's REAL offers list during a session
  (server trade matching is index-based) and must never be saved.
- Placeholder cost is a named barrier — the CUSTOM_NAME lives in the ItemCost
  predicate so the client renders the label with zero client-side code.

## Standards

SemVer in `gradle.properties`: PATCH every commit, MINOR on release. bh679 Gate
workflow applies (see `~/.claude/` rules/playbooks). Releases only via release.yml
dispatch — it creates the tag, GitHub Release, and publishes to Modrinth/CurseForge
when `MODRINTH_PROJECT_ID`/`CURSEFORGE_PROJECT_ID` vars + tokens are set.

Dungeon Train consumes the **neoforge** jar via its shared `bh679` Ivy repo:
asset name MUST stay `tradeeverything-neoforge-<v>.jar` (flat, no `+mc` suffix).
