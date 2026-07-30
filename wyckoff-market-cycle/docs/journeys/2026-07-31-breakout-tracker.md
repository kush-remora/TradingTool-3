# Breakout Tracker

## Why

Quiet, flat accumulation candidates need a durable place to be monitored before a Phase C/D confirmation. This prevents a scanner result from becoming an impulsive trade.

## Implemented

The tracker records one NSE instrument with its observed breakout date, price, and editable evidence notes. It shows the current price and percentage move from the recorded price through the existing quote feed. A copy control in each notes cell copies the full saved evidence block for reuse.

## Decisions

The evidence is deliberately free-form so the existing accumulation-output fields can be pasted intact. One entry per instrument keeps the personal review list uncluttered; a future iteration can add a separate history if repeated setups become useful.

## Validation

Frontend production build, focused tracker hook tests, a Kotlin mapper test, and the Kotlin core/resources/service compilation pass.

## Follow-up

Add richer outcome review only after a meaningful set of candidates has accumulated.
