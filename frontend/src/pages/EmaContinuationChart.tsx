import {
  CandlestickSeries,
  ColorType,
  CrosshairMode,
  LineSeries,
  createChart,
  createSeriesMarkers,
  type CandlestickData,
  type LineData,
  type SeriesMarker,
  type Time,
} from "lightweight-charts";
import { useEffect, useRef } from "react";
import type { EmaContinuationDailyCandle, EmaContinuationSignal } from "../types";

interface EmaContinuationChartProps {
  candles: EmaContinuationDailyCandle[];
  signals: EmaContinuationSignal[];
}

const CHART_HEIGHT = 380;

export function EmaContinuationChart({ candles, signals }: EmaContinuationChartProps) {
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const container = containerRef.current;
    if (!container || candles.length === 0) return undefined;

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
      grid: { vertLines: { color: "#f0f2f5" }, horzLines: { color: "#f0f2f5" } },
      rightPriceScale: { borderColor: "#e4e7ec", scaleMargins: { top: 0.08, bottom: 0.08 } },
      timeScale: { borderColor: "#e4e7ec", rightOffset: 4, barSpacing: 8 },
      crosshair: {
        mode: CrosshairMode.Normal,
        vertLine: { color: "#98a2b3", labelBackgroundColor: "#344054" },
        horzLine: { color: "#98a2b3", labelBackgroundColor: "#344054" },
      },
    });

    const priceSeries = chart.addSeries(CandlestickSeries, {
      upColor: "#16a34a",
      downColor: "#dc2626",
      borderVisible: false,
      wickUpColor: "#16a34a",
      wickDownColor: "#dc2626",
    });
    priceSeries.setData(candles.map((candle): CandlestickData<Time> => ({
      time: candle.date,
      open: candle.open,
      high: candle.high,
      low: candle.low,
      close: candle.close,
    })));

    const addLine = (color: string, dataKey: "ema10" | "ema20" | "sma200") => {
      const series = chart.addSeries(LineSeries, { color, lineWidth: 2, crosshairMarkerVisible: false });
      const lineData = candles.flatMap((candle): LineData<Time>[] =>
        candle[dataKey] == null ? [] : [{ time: candle.date, value: candle[dataKey] }],
      );
      series.setData(lineData);
    };
    addLine("#2563eb", "ema10");
    addLine("#d97706", "ema20");
    addLine("#7c3aed", "sma200");

    const setupMarkers: SeriesMarker<Time>[] = candles
      .filter((candle) => candle.emaCross)
      .map((candle) => ({
        time: candle.date,
        position: "belowBar",
        shape: "circle",
        color: "#16a34a",
        text: "X",
      }));
    const breakoutMarkers: SeriesMarker<Time>[] = signals
      .filter((signal) => signal.type === "BASE_BREAKOUT")
      .map((signal) => ({
      time: signal.date,
      position: "belowBar",
      shape: "arrowUp",
      color: "#16a34a",
      text: "B",
      }));
    const markers = [...setupMarkers, ...breakoutMarkers].sort((left, right) => String(left.time).localeCompare(String(right.time)));
    if (markers.length > 0) createSeriesMarkers(priceSeries, markers);

    const resizeObserver = new ResizeObserver(() => {
      chart.applyOptions({ width: container.clientWidth });
    });
    resizeObserver.observe(container);
    chart.timeScale().fitContent();

    return () => {
      resizeObserver.disconnect();
      chart.remove();
    };
  }, [candles, signals]);

  return <div ref={containerRef} className="ema-continuation-chart" aria-label="EMA continuation daily chart" />;
}
