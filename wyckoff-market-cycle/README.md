# Wyckoff Market Cycle

This is a standalone module for the **Wyckoff Market Cycle** implementation.

## Separation Rules
- This module is intentionally kept outside active parent `<modules>` wiring.
- No existing service/core/frontend module imports this module.
- Any integration must happen later through explicit API contracts.

## Current Scope
- Minimal skeleton only.
- One marker class (`WyckoffMarketCycleModule`) and one basic test.
