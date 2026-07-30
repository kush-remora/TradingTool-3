# CSV Backtest Trade Detail Copy

## Why

Trade Details contains enough context to review an outcome, but its wide table makes preserving one result cumbersome.

## Implemented

Each row now has a Copy action that places a labeled plain-text trade record on the clipboard, including signal, breakout/delivery evidence, entry, drawdown, and exit fields.

Saving an Analyze review also creates a quantity-one `CSV_BACKTEST` Trade Journal row for an entered backtest trade. Existing journal rows are deliberately protected from automatic merging or closing.

## Decision

This is a local UI action only; it introduces no new API or persistence path. Failed clipboard access is shown as an explicit error.

## Validation

The focused CSV backtest page suite (10 tests) and the frontend production build pass.
