# Phase D Wake-Up Volume Signal

## Current Understanding

Kush found the current `Wake-Up` signal too hard to use because the volume rule only highlighted names once live volume reached `5x` the `T-1` day volume, and the label hid the actual multiple behind a generic `VOL 5x` tag. The desired behavior is simpler and more operational: trigger from `2x` onward, show the real live-vs-`T-1` multiple directly in the badge, and keep the `T-1` reference volume visible in the dashboard cell.

This is mainly a frontend clarity change on `PhaseDScannerPage`. The live market hook should continue recalculating the signal whenever the dashboard loads and refreshes live data, but the label should now explain the current state instead of forcing mental math.

## Implementation Outcome

The `Wake-Up` logic now uses a `2x` live-volume threshold against `T-1` volume while keeping the existing `4%` price-move signal. The badge text now shows the real ratio, for example `VOL 2.3x` or `MOVE + 3.8x`, and the cell shows both the current live volume and the `T-1` baseline under the tag. During validation, the focused page test also exposed a real teardown issue in the shared live market singleton, so the hook was hardened to use browser-safe global timer APIs instead of assuming `window` still exists after test cleanup.

Validation is covered by the focused Phase D page test that checks the new `2x` threshold and ratio-based labels.
