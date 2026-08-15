import type { ReactNode } from "react";

interface DailyOhlcGlyphProps {
  open: number;
  high: number;
  low: number;
  close: number;
}

function formatGlyphPrice(value: number): string {
  return `₹${value.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

export function DailyOhlcGlyph({ open, high, low, close }: DailyOhlcGlyphProps): ReactNode {
  const chartTop = 4;
  const chartBottom = 38;
  const priceRange = high - low;
  const priceToY = (price: number): number => priceRange > 0
    ? chartTop + ((high - price) / priceRange) * (chartBottom - chartTop)
    : (chartTop + chartBottom) / 2;
  const openY = priceToY(open);
  const closeY = priceToY(close);
  const bodyTop = Math.min(openY, closeY);
  const bodyHeight = Math.max(Math.abs(openY - closeY), 2);
  const direction = close > open ? "up" : close < open ? "down" : "flat";
  const directionLabel = direction === "up" ? "close above open" : direction === "down" ? "close below open" : "close equal to open";
  const accessibleLabel = [
    `Daily range: open ${formatGlyphPrice(open)}`,
    `high ${formatGlyphPrice(high)}`,
    `low ${formatGlyphPrice(low)}`,
    `close ${formatGlyphPrice(close)}`,
    `${directionLabel}. Intraday high-low order is unknown.`,
  ].join(", ");

  return (
    <svg
      aria-label={accessibleLabel}
      className={`daily-ohlc-glyph daily-ohlc-glyph-${direction}`}
      role="img"
      viewBox="0 0 64 42"
    >
      <title>{accessibleLabel}</title>
      <text className="daily-ohlc-glyph-bound" x="2" y="7">H</text>
      <text className="daily-ohlc-glyph-bound" x="2" y="41">L</text>
      <line className="daily-ohlc-glyph-wick" x1="32" x2="32" y1={chartTop} y2={chartBottom} />
      <line className="daily-ohlc-glyph-cap" x1="27" x2="37" y1={chartTop} y2={chartTop} />
      <line className="daily-ohlc-glyph-cap" x1="27" x2="37" y1={chartBottom} y2={chartBottom} />
      <rect className="daily-ohlc-glyph-body" height={bodyHeight} width="8" x="28" y={bodyTop} rx="1" />
      <line className="daily-ohlc-glyph-marker" x1="19" x2="28" y1={openY} y2={openY} />
      <line className="daily-ohlc-glyph-marker" x1="36" x2="45" y1={closeY} y2={closeY} />
      <text className="daily-ohlc-glyph-point" x="12" y={Math.min(40, openY + 3)}>O</text>
      <text className="daily-ohlc-glyph-point" x="48" y={Math.min(40, closeY + 3)}>C</text>
    </svg>
  );
}
