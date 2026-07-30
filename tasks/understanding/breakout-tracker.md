# Breakout Tracker

Kush needs a compact personal screen for recording an emerging breakout while it is still in a quiet Wyckoff accumulation. Each entry must retain the NSE instrument, breakout date, observed breakout price, and free-form evidence notes. The screen must show the latest price and percentage move from the recorded price so that candidates can be reviewed without treating an early signal as a buy instruction.

Phase 1 uses one persisted entry per instrument. Notes remain editable and accept pasted scanner evidence such as breakout volume and five-day delivery percentage. Current price is read through the existing quotes endpoint; no new market-data pipeline is introduced. Entries can be added, edited, and removed. Validation will cover API persistence and the primary frontend add/edit/performance flow.

## Plan

1. Add a small `breakout_tracker_entries` migration and Kotlin persistence service with list/create/update/delete endpoints.
2. Build a compact Breakout Tracker page using the existing NSE instrument search and quote hook.
3. Add the navigation route, focused frontend tests, and a feature journey record.
