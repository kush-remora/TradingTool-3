# Wyckoff Market Cycle

Standalone module for Wyckoff-focused workflows, kept isolated from active parent runtime wiring.

## Current Scope
- Module marker and basic Kotlin unit test.
- Config-driven index constituent sync inputs (`config/index_sync_config.json`).
- Isolated SQL contract for `index_constituents` (`sql/index_constituents.sql`).
- Wyckoff-scoped strategy package for Delivery Threshold 10% backtest logic (`core/.../strategy/wyckoff/deliverythreshold`).
- Module-specific docs and journey log under `docs/`.

## Boundary Rules
- This module is intentionally outside active parent `<modules>` wiring.
- Existing `core`, `service`, `event-service`, and `frontend` modules must not import from this module directly.
- Integration should happen later through explicit contracts (API/job boundaries), not cross-module coupling.

## Directory Layout
- `src/main/kotlin/com/tradingtool/wyckoffcycle/`: module marker code.
- `src/test/kotlin/com/tradingtool/wyckoffcycle/`: module unit tests.
- `config/`: sync configuration for supported indices.
- `sql/`: isolated schema contract for index constituents.
- `docs/`: design and implementation notes, including journey logs.

## Index Constituents Contract
The module defines `public.index_constituents` as the stock-universe source of truth. Index memberships use their configured keys, and the maintained Groww watchlist uses `index_key = 'groww'`.

Key fields include:
- identity: `index_key`, `symbol`, `instrument_token`
- metadata: `company_name`, `industry`, `series`, `isin_code`
- sync lifecycle: `is_active`, `source_url`, `last_synced_at`, timestamps

See [index_constituents.sql](/Users/kushbhardwaj/Documents/github/TradingTool-3/wyckoff-market-cycle/sql/index_constituents.sql).

## Sync Configuration
`config/index_sync_config.json` controls:
- `batchSize` for upsert operations
- enabled/disabled indices
- source CSV URLs per index

Current config includes multiple Nifty index feeds; keep additions config-only unless behavior changes are required.

## Validation Baseline
- Module unit test verifies marker behavior (`WyckoffMarketCycleModuleTest`).
- Latest implementation notes and validation runs are tracked in [2026-05-16.md](/Users/kushbhardwaj/Documents/github/TradingTool-3/wyckoff-market-cycle/docs/journeys/2026-05-16.md).

## Related Docs
- [docs/README.md](/Users/kushbhardwaj/Documents/github/TradingTool-3/wyckoff-market-cycle/docs/README.md)
