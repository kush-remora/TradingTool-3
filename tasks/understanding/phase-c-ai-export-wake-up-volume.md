# Phase C AI Export Wake-Up Volume

## Current Understanding

Kush wants the `Export AI JSON` payload on the Phase D screen to include the same practical volume context now shown in the dashboard Wake-Up column. The AI export should not force manual reconstruction from raw history arrays when a simple summary will do. The requested additions are the current completed-day volume, the previous trading day's volume, and an explicit indication of whether volume is already `2x` or more.

The export button only downloads the JSON returned by `/api/strategy/phase-c/export`, so the right implementation point is the backend export payload rather than the frontend button handler. The change should stay readable and local to the export contract.

## Implementation Outcome

Each exported stock now includes a `wakeUpVolume` block with:

- `latestDayVolume`
- `previousDayVolume`
- `volumeRatioVsPreviousDay`
- `volumeIs2xOrMore`

This block is built from the watchlist row's latest completed-day volume plus the new `previousDayVolume` field already added for the Wake-Up dashboard fallback. The export metadata schema also documents these fields so downstream AI consumers do not need to infer their meaning from naming alone.

Validation is covered by a focused Kotlin test for the wake-up export helper.
