import {
  CandlestickSeries,
  ColorType,
  CrosshairMode,
  HistogramSeries,
  LineStyle,
  createChart,
  createSeriesMarkers,
  type CandlestickData,
  type HistogramData,
  type SeriesMarker,
  type Time,
} from "lightweight-charts";
import { useEffect, useRef, useState } from "react";
import {
  formatPrice,
  formatQuantity,
  formatShortDate,
  formatSignedPercent,
  type CompactDailyRow,
} from "./compactStockReview";

interface CompactStockChartProps {
  days: CompactDailyRow[];
  eventDates: Set<string>;
  sma100: number | null;
  sma200: number | null;
}

const CHART_HEIGHT = 344;
const DEFAULT_VISIBLE_SESSIONS = 60;
type VolumeSignalVisibility = {
  pocketPivot: boolean;
  highVolume: boolean;
  dryVolume: boolean;
  bullSnort: boolean;
};

const DEFAULT_VOLUME_SIGNAL_VISIBILITY: VolumeSignalVisibility = {
  pocketPivot: true,
  highVolume: true,
  dryVolume: true,
  bullSnort: true,
};

const volumeSignalColor = (
  day: CompactDailyRow,
  visibility: VolumeSignalVisibility,
  isVolumeEvent: boolean,
): string => {
  const fallbackColor = day.close >= day.open
    ? "rgba(22, 163, 74, 0.34)"
    : "rgba(220, 38, 38, 0.30)";
  if (day.volume_signal === "POCKET_PIVOT" && visibility.pocketPivot) return "rgba(37, 99, 235, 0.90)";
  if (day.volume_signal === "HIGH_VOLUME_UP" && visibility.highVolume) return "rgba(22, 163, 74, 0.90)";
  if (day.volume_signal === "HIGH_VOLUME_DOWN" && visibility.highVolume) return "rgba(220, 38, 38, 0.90)";
  if (day.volume_signal === "DRY" && visibility.dryVolume) return "rgba(217, 119, 6, 0.86)";
  if (isVolumeEvent) return "rgba(217, 119, 6, 0.86)";
  return fallbackColor;
};

const volumeSignalLabel = (day: CompactDailyRow): string => {
  if (day.bull_snort) return "Bull snort";
  if (day.volume_signal === "POCKET_PIVOT") return "Pocket pivot";
  if (day.volume_signal === "HIGH_VOLUME_UP") return "High-volume up";
  if (day.volume_signal === "HIGH_VOLUME_DOWN") return "High-volume down";
  if (day.volume_signal === "DRY") return "Dry volume";
  if (day.volume_signal === "INSUFFICIENT_DATA") return "Warm-up";
  return "Normal";
};

export function CompactStockChart({ days, eventDates, sma100, sma200 }: CompactStockChartProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const latestDay = days.at(-1) ?? null;
  const [selectedDay, setSelectedDay] = useState<CompactDailyRow | null>(latestDay);
  const [visibility, setVisibility] = useState<VolumeSignalVisibility>(DEFAULT_VOLUME_SIGNAL_VISIBILITY);

  useEffect(() => {
    setSelectedDay(latestDay);
  }, [latestDay]);

  useEffect(() => {
    const container = containerRef.current;
    if (!container || days.length === 0) return undefined;

    const chart = createChart(container, {
      width: container.clientWidth,
      height: CHART_HEIGHT,
      layout: {
        background: { type: ColorType.Solid, color: "#ffffff" },
        textColor: "#667085",
        fontFamily: "Inter, system-ui, sans-serif",
        fontSize: 11,
        attributionLogo: true,
      },
      grid: {
        vertLines: { color: "#f0f2f5" },
        horzLines: { color: "#f0f2f5" },
      },
      rightPriceScale: { borderColor: "#e4e7ec", scaleMargins: { top: 0.08, bottom: 0.28 } },
      timeScale: { borderColor: "#e4e7ec", timeVisible: false, rightOffset: 3, barSpacing: 8 },
      crosshair: {
        mode: CrosshairMode.Normal,
        vertLine: { color: "#98a2b3", labelBackgroundColor: "#344054" },
        horzLine: { color: "#98a2b3", labelBackgroundColor: "#344054" },
      },
    });

    const candles = chart.addSeries(CandlestickSeries, {
      upColor: "#16a34a",
      downColor: "#dc2626",
      borderUpColor: "#16a34a",
      borderDownColor: "#dc2626",
      wickUpColor: "#16a34a",
      wickDownColor: "#dc2626",
    });
    candles.setData(days.map((day): CandlestickData<Time> => ({
      time: day.date,
      open: day.open,
      high: day.high,
      low: day.low,
      close: day.close,
    })));

    const volume = chart.addSeries(HistogramSeries, {
      priceFormat: { type: "volume" },
      priceScaleId: "",
      lastValueVisible: false,
      priceLineVisible: false,
    });
    volume.priceScale().applyOptions({ scaleMargins: { top: 0.8, bottom: 0 } });
    volume.setData(days.map((day): HistogramData<Time> => ({
      time: day.date,
      value: day.volume,
      color: volumeSignalColor(day, visibility, eventDates.has(day.date)),
    })));

    const eventMarkers: SeriesMarker<Time>[] = days
      .filter((day) => eventDates.has(day.date))
      .map((day) => ({
        time: day.date,
        position: "aboveBar",
        shape: "circle",
        color: "#d97706",
        text: "V",
        size: 1,
      }));
    const bullSnortMarkers: SeriesMarker<Time>[] = days
      .filter((day) => visibility.bullSnort && day.bull_snort)
      .map((day) => ({
        time: day.date,
        position: "aboveBar",
        shape: "square",
        color: "#7c3aed",
        text: "BS",
        size: 1,
      }));
    createSeriesMarkers(candles, [...eventMarkers, ...bullSnortMarkers]);

    if (sma200 != null) {
      candles.createPriceLine({
        price: sma200,
        color: "#7c3aed",
        lineWidth: 1,
        lineStyle: LineStyle.Dashed,
        axisLabelVisible: true,
        title: "200 DMA",
      });
    }

    if (sma100 != null) {
      candles.createPriceLine({
        price: sma100,
        color: "#0284c7",
        lineWidth: 1,
        lineStyle: LineStyle.Dashed,
        axisLabelVisible: true,
        title: "100 DMA",
      });
    }

    const timeScale = chart.timeScale();
    timeScale.fitContent();
    // Start with a readable recent window while keeping the full history loaded
    // for pan and zoom.
    const firstVisibleIndex = Math.max(0, days.length - DEFAULT_VISIBLE_SESSIONS);
    timeScale.setVisibleLogicalRange({ from: firstVisibleIndex, to: days.length - 1 });
    const daysByDate = new Map(days.map((day) => [day.date, day]));
    const handleCrosshairMove = (parameter: { time?: Time }) => {
      const date = typeof parameter.time === "string" ? parameter.time : null;
      setSelectedDay(date ? (daysByDate.get(date) ?? latestDay) : latestDay);
    };
    chart.subscribeCrosshairMove(handleCrosshairMove);

    const resizeObserver = new ResizeObserver(([entry]) => {
      chart.applyOptions({ width: entry.contentRect.width });
    });
    resizeObserver.observe(container);

    return () => {
      chart.unsubscribeCrosshairMove(handleCrosshairMove);
      resizeObserver.disconnect();
      chart.remove();
    };
  }, [days, eventDates, sma100, sma200, visibility]);

  if (days.length === 0) {
    return <div className="compact-review-chart-empty">No chart data available.</div>;
  }

  return (
    <div className="compact-review-chart-frame">
      <div className="compact-review-chart-controls" aria-label="Volume signal display controls">
        <span className="compact-review-chart-controls-label">Signals</span>
        <SignalToggle label="PPV" active={visibility.pocketPivot} onClick={() => setVisibility((current) => ({ ...current, pocketPivot: !current.pocketPivot }))} tone="blue" />
        <SignalToggle label="High vol" active={visibility.highVolume} onClick={() => setVisibility((current) => ({ ...current, highVolume: !current.highVolume }))} tone="green" />
        <SignalToggle label="Dry" active={visibility.dryVolume} onClick={() => setVisibility((current) => ({ ...current, dryVolume: !current.dryVolume }))} tone="orange" />
        <SignalToggle label="Bull snort" active={visibility.bullSnort} onClick={() => setVisibility((current) => ({ ...current, bullSnort: !current.bullSnort }))} tone="purple" />
      </div>
      {selectedDay && <ChartSessionReadout day={selectedDay} />}
      <div ref={containerRef} className="compact-review-chart" aria-label="Price and volume chart" />
    </div>
  );
}

interface SignalToggleProps {
  label: string;
  active: boolean;
  tone: "blue" | "green" | "orange" | "purple";
  onClick: () => void;
}

function SignalToggle({ label, active, tone, onClick }: SignalToggleProps) {
  return (
    <button
      type="button"
      className={`compact-signal-toggle compact-signal-toggle-${tone}${active ? " compact-signal-toggle-active" : ""}`}
      aria-pressed={active}
      onClick={onClick}
    >
      {label}
    </button>
  );
}

interface ChartSessionReadoutProps {
  day: CompactDailyRow;
}

export function ChartSessionReadout({ day }: ChartSessionReadoutProps) {
  const directionClass = day.openToClosePct == null || day.openToClosePct === 0
    ? ""
    : day.openToClosePct < 0 ? "compact-negative" : "compact-positive";

  return (
    <div className="compact-review-chart-readout">
      <div className="compact-review-chart-readout-row">
        <strong>{formatShortDate(day.date)}</strong>
        <span>O <b>{formatPrice(day.open)}</b></span>
        <span>H <b>{formatPrice(day.high)}</b></span>
        <span>L <b>{formatPrice(day.low)}</b></span>
        <span>C <b>{formatPrice(day.close)}</b></span>
      </div>
      <div className="compact-review-chart-readout-row compact-muted">
        <span>Vol <b>{formatQuantity(day.volume)}</b></span>
        <span>Vol / 5D <b>{day.volumeVsPrior5dPct == null ? "—" : `${day.volumeVsPrior5dPct.toFixed(0)}%`}</b></span>
        <span>Del <b>{day.deliveryPct == null ? "—" : `${day.deliveryPct.toFixed(2)}%`}</b></span>
        <span>Signal <b>{volumeSignalLabel(day)}</b></span>
        <span>RVol 50 <b>{day.relative_volume_50 == null ? "—" : `${day.relative_volume_50.toFixed(2)}×`}</b></span>
        <span>O→C <b className={directionClass}>{formatSignedPercent(day.openToClosePct)}</b></span>
        <span>L→H <b>{formatSignedPercent(day.spreadPct)}</b></span>
      </div>
      <div className="compact-review-chart-readout-row compact-muted">
        <span>RSI14 <b>{day.rsi14 == null ? "—" : day.rsi14.toFixed(2)}</b></span>
        <span>ROC 9D <b>{formatSignedPercent(day.roc9Pct)}</b></span>
      </div>
    </div>
  );
}
