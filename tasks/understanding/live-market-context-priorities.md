# Live market context priorities

This note captures the intended hierarchy for live-market information across the app so we do not drift into showing fields that feel interesting but do not actually help fast trading decisions. The core question is not "what can Kite send?" but "what best explains whether today's move has real participation and intraday strength?"

Priority order should be:

- `LTP` and `% change` first, because they define the immediate state.
- `average_price` next, because `LTP vs average_price` gives a compact read on intraday strength or weakness.
- `today volume vs T-1 volume` next, because this is the most practical confirmation that today's tape is materially more active than yesterday's and that people are more interested today.
- `total buy quantity vs total sell quantity` after that, as supportive order-book context only.

Interpretation rules to preserve:

- If `LTP > average_price`, intraday tone is generally stronger.
- If `LTP < average_price`, intraday tone is generally weaker.
- If `today volume / T-1 volume` is materially above `1.0x`, the session is showing unusual participation.
- Large buy quantity relative to sell quantity can support bullish tone, and the reverse can support bearish tone, but this must stay secondary because these are book-pressure fields, not clean proof of executed aggression.

Design implication:

- The live market widget should eventually show `average_price` and a compact `today volume vs T-1` comparison.
- If screen density becomes tight, prefer keeping `LTP vs average_price` and `today volume vs T-1` before adding more order-book data.
- Do not build primary buy/sell decisions from buy/sell quantity alone.
