# Phase D Wake-Up Volume Signal

## Current Understanding

Kush found the current `Wake-Up` signal too hard to use because the volume rule only highlighted names once live volume reached `5x` the `T-1` day volume, and the label hid the actual multiple behind a generic `VOL 5x` tag. The desired behavior is simpler and more operational: trigger from `2x` onward, show the real live-vs-`T-1` multiple directly in the badge, and keep the `T-1` reference volume visible in the dashboard cell.

Follow-up validation showed a deeper issue: after market close the live stream is empty, and the dashboard row's `volume` field is not a fixed `T-1` value. It is the latest completed daily candle volume after fresh-field refresh. That meant the UI could show blank `Now` values and also mislabel the latest day as `T-1`.

## Implementation Outcome

The `Wake-Up` logic still uses a `2x` volume threshold and the `4%` price-move signal, but the data flow is now explicit:

- if live data exists, compare `live volume` against the row's latest completed day volume
- if live data is unavailable, compare the row's latest completed day volume against a new `previousDayVolume` field

The backend fresh-field calculator now stores both `volume` and `previousDayVolume`, and the dashboard payload includes both. On the frontend, the badge text shows the real ratio, for example `VOL 2.3x` or `MOVE + 3.8x`, and the cell now shows `Now` during live market hours or `Day` after market close, plus a truthful `T-1` line underneath. During earlier validation, the focused page test also exposed a real teardown issue in the shared live market singleton, so the hook was hardened to use browser-safe global timer APIs instead of assuming `window` still exists after test cleanup.

Validation is covered by:

- the focused Phase D page test for the new volume-context fallback behavior
- the focused Kotlin fresh-field calculator test for `previousDayVolume`
