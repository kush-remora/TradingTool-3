# Base Rhythm Analysis — Product Understanding

## Problem Statement

The strict 20-session flat classifier correctly rejects directional or volatile closes, but it does not explain the visible 60-session Wyckoff sequence that a trader sees: markdown, stabilization, short contraction, test, and later expansion. KALYANKJIL demonstrates this gap: its 20-session window ending 2026-06-18 was not flat, while the full chart later reveals a broader Phase C/Phase D sequence.

## Proposed Direction

Add a separate, forward-safe **Base Rhythm** diagnostic to the Forward Accumulation Analysis timeline. It must not change `FLAT_GOLDEN`, candidate eligibility, or ranking. For every as-of date, describe the immediately preceding 60 trading sessions as six contiguous 10-session blocks. Each block reports price direction, range contraction/expansion, and relative volume; the UI presents a compact chronological sequence instead of an opaque score.

## Initial Acceptance Criteria

- Uses only candles on or before the snapshot as-of date.
- Shows six chronological 10-session blocks for the last 60 sessions.
- Labels each block with a simple direction (`falling`, `flat`, `rising`) and range state (`contracting`, `steady`, `expanding`).
- Adds an explanatory Base Rhythm panel to the symbol timeline only; it does not alter current valid/invalid decisions.
- Stores the computed facts in the existing snapshot details so historical runs remain reproducible.

## Decision

Prefer this over replacing 20-20-20 with 10-10-10-10-10-10. The 20-session strict classifier remains the mathematical gate; the 10-session sequence becomes the human-readable structural context.

## Delivery Outcome

Implemented as a stored, forward-safe timeline diagnostic. Every newly run snapshot now includes six 10-session blocks over its preceding 60 sessions; each block shows direction plus range and volume behavior compared with the preceding block. Existing runs are intentionally stale after the algorithm version bump and need a rerun to populate the new facts.
