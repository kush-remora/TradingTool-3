# Strategy Docs

## Working Reference

- [strategy-map.md](strategy-map.md): normalized strategy map built from the Gemini chats plus the existing strategy docs. Start here.
- [remora-philosophy.md](remora-philosophy.md): the core intuition behind Remora and the design philosophy that should guide related UI, backend, and product decisions.
- [rsi-momentum-v1.md](rsi-momentum-v1.md): implementation summary for Strategy 1 weekly RSI momentum ranking.
- [rsi-momentum-research-framework.md](rsi-momentum-research-framework.md): explainable backtesting and anti-overfitting framework for RSI momentum experiments (classic, target, and rank-jump variants).
- [rsi-mean-reversion-confirmation-v1.md](rsi-mean-reversion-confirmation-v1.md): requirements note for the daily RSI-floor mean-reversion setup that waits for stabilization and breakout confirmation before entry.
- [statistical-mean-reversion-waterfall-v1.md](statistical-mean-reversion-waterfall-v1.md): broader daily mean-reversion note built around Bollinger stretch, Z-score / DMA distance, RSI oversold filtering, and optional VWAP context.
- [volume-burst-momentum-v1.md](volume-burst-momentum-v1.md): short swing momentum spec for extreme intraday cumulative-volume bursts with `+5%` target, `-2%` stop, and `3-day` max hold.
- [high-volume-reversal-v1.md](high-volume-reversal-v1.md): long-only event-study spec for large down days on high abnormal volume in Indian cash equities.
- [overnight-volume-shock-v1.md](overnight-volume-shock-v1.md): overnight-only spec for buying close and selling next open based on daily volume shocks.
- [remora-backend.md](remora-backend.md): current Remora scanner behavior and backend flow.
- [weekly-seasonality.md](weekly-seasonality.md): current weekly-pattern screener logic and scoring.
- [pre-earnings-momentum.docx](pre-earnings-momentum.docx): long-form concept note for the earnings catalyst strategy.

## Notes

- The files under `Gemini chat/` are research transcripts, not clean product specs.
- Use [strategy-map.md](strategy-map.md) as the canonical summary before building or changing a strategy.
- Use [remora-philosophy.md](remora-philosophy.md) as a standing design constraint when building anything related to institutional-footprint detection, breakout confirmation, or Remora UI.
- When a new idea appears, decide first whether it is:
  - a scanner,
  - a ranking model,
  - a trade manager,
  - or just a research hypothesis.
