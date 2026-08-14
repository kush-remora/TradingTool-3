import { act, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { DayDetail } from "../../types";
import { buildCompactDailyRows } from "./compactStockReview";
import { CompactStockChart } from "./CompactStockChart";

const chartMocks = vi.hoisted(() => ({
  crosshairHandler: null as ((parameter: { time?: string }) => void) | null,
  chartOptions: null as { crosshair?: { mode?: number } } | null,
  barSeries: {},
  lineSeries: {},
  seriesTypes: [] as string[],
  priceLines: [] as Array<{ price: number; title: string }>,
  seriesDataCalls: [] as unknown[][],
  markerCalls: [] as Array<unknown[]>,
  visibleLogicalRanges: [] as Array<{ from: number; to: number }>,
}));

vi.mock("lightweight-charts", () => {
  const series = {
    setData: (data: unknown[]) => chartMocks.seriesDataCalls.push(data),
    priceScale: () => ({ applyOptions: vi.fn() }),
    createPriceLine: (line: { price: number; title: string }) => {
      chartMocks.priceLines.push(line);
    },
  };

  return {
    BarSeries: chartMocks.barSeries,
    ColorType: { Solid: "solid" },
    CrosshairMode: { Normal: 0 },
    HistogramSeries: {},
    LineSeries: chartMocks.lineSeries,
    LineStyle: { Dashed: 2 },
    createSeriesMarkers: (_series: unknown, markers: unknown[]) => chartMocks.markerCalls.push(markers),
    createChart: (_container: HTMLElement, options: { crosshair?: { mode?: number } }) => {
      chartMocks.chartOptions = options;
      return {
        addSeries: (seriesType: unknown) => {
          chartMocks.seriesTypes.push(seriesType === chartMocks.barSeries ? "bar" : seriesType === chartMocks.lineSeries ? "line" : "volume");
          return series;
        },
        applyOptions: vi.fn(),
        remove: vi.fn(),
        subscribeCrosshairMove: (handler: (parameter: { time?: string }) => void) => {
          chartMocks.crosshairHandler = handler;
        },
        unsubscribeCrosshairMove: vi.fn(),
        timeScale: () => ({
          fitContent: vi.fn(),
          setVisibleLogicalRange: (range: { from: number; to: number }) => chartMocks.visibleLogicalRanges.push(range),
        }),
      };
    },
  };
});

beforeEach(() => {
  chartMocks.crosshairHandler = null;
  chartMocks.chartOptions = null;
  chartMocks.seriesTypes = [];
  chartMocks.priceLines = [];
  chartMocks.seriesDataCalls = [];
  chartMocks.markerCalls = [];
  chartMocks.visibleLogicalRanges = [];
});

const day = (date: string, volume: number, open: number = 100, close: number = 105, rsi14: number | null = 55): DayDetail => ({
  date,
  open,
  high: 110,
  low: 95,
  close,
  volume,
  daily_change_pct: null,
  rsi14,
  vol_ratio: null,
});

describe("CompactStockChart", () => {
  it("shows the latest candle evidence and follows the hovered session", () => {
    const days = buildCompactDailyRows(
      [
        day("2026-08-04", 1_000_000),
        day("2026-08-05", 1_000_000),
        day("2026-08-06", 1_000_000),
        day("2026-08-07", 1_000_000),
        day("2026-08-10", 1_000_000),
        day("2026-08-11", 2_000_000, 100, 108),
        day("2026-08-12", 600_000, 105, 102),
      ],
      [
        { date: "2026-08-11", delivery_percentage: 28.25, delivered_quantity: 565_000, traded_quantity: 2_000_000 },
        { date: "2026-08-12", delivery_percentage: 22.5, delivered_quantity: 135_000, traded_quantity: 600_000 },
      ],
    );

    render(<CompactStockChart days={days} eventDates={new Set()} sma100={101} sma200={null} />);

    expect(chartMocks.chartOptions?.crosshair?.mode).toBe(0);
    expect(chartMocks.visibleLogicalRanges).toEqual([{ from: 0, to: days.length - 1 }]);
    expect(chartMocks.priceLines).toEqual(expect.arrayContaining([
      expect.objectContaining({ price: 101, title: "100 DMA" }),
    ]));
    expect(chartMocks.seriesTypes).toEqual(["bar", "volume"]);
    expect(screen.getByText("12 Aug")).toBeInTheDocument();
    expect(screen.getByText("50%")).toBeInTheDocument();
    expect(screen.getByText("22.50%")).toBeInTheDocument();
    expect(screen.getByText("55.00")).toBeInTheDocument();

    act(() => chartMocks.crosshairHandler?.({ time: "2026-08-11" }));

    expect(screen.getByText("11 Aug")).toBeInTheDocument();
    expect(screen.getByText("200%")).toBeInTheDocument();
    expect(screen.getByText("28.25%")).toBeInTheDocument();
    expect(screen.getByText("+8.00%")).toBeInTheDocument();
    expect(screen.getByText("+15.79%")).toBeInTheDocument();
  });

  it("shows 9-day ROC once enough history is available", () => {
    const days = buildCompactDailyRows(
      [
        ...Array.from({ length: 9 }, (_, index) => day(`2026-07-${String(index + 1).padStart(2, "0")}`, 1_000_000, 100, 100)),
        day("2026-08-01", 1_000_000, 100, 120, 63),
      ],
      [],
    );

    render(<CompactStockChart days={days} eventDates={new Set()} sma100={null} sma200={null} />);

    expect(screen.getByText("01 Aug")).toBeInTheDocument();
    expect(screen.getByText("63.00")).toBeInTheDocument();
    expect(screen.getAllByText("+20.00%").length).toBeGreaterThanOrEqual(2);
  });

  it("opens on the latest 60 sessions when more history is loaded", () => {
    const days = buildCompactDailyRows(
      Array.from({ length: 65 }, (_, index) => {
        const date = new Date(Date.UTC(2026, 0, index + 1)).toISOString().slice(0, 10);
        return day(date, 1_000_000);
      }),
      [],
    );

    render(<CompactStockChart days={days} eventDates={new Set()} sma100={null} sma200={null} />);

    expect(chartMocks.visibleLogicalRanges).toEqual([{ from: 5, to: 64 }]);
  });

  it("uses backend volume classifications and exposes chart toggles", () => {
    const days = buildCompactDailyRows(
      [day("2026-08-04", 1_000_000), day("2026-08-05", 2_000_000, 100, 108)],
      [],
    );
    days[1].volume_signal = "POCKET_PIVOT";
    days[1].bull_snort = true;
    days[1].relative_volume_50 = 3.12;

    render(<CompactStockChart days={days} eventDates={new Set()} sma100={null} sma200={null} />);

    expect(screen.getAllByText("Bull snort").length).toBeGreaterThanOrEqual(2);
    expect(screen.getByText("3.12×")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "PPV" })).toHaveAttribute("aria-pressed", "true");
    expect(chartMocks.seriesDataCalls[1]).toEqual(expect.arrayContaining([
      expect.objectContaining({ color: "rgba(37, 99, 235, 0.90)" }),
    ]));
    expect(chartMocks.markerCalls[0]).toEqual(expect.arrayContaining([
      expect.objectContaining({ text: "BS", color: "#7c3aed" }),
    ]));
  });

  it("switches the price pane between bar and line views", async () => {
    const days = buildCompactDailyRows(
      [day("2026-08-04", 1_000_000), day("2026-08-05", 2_000_000, 100, 108)],
      [],
    );

    render(<CompactStockChart days={days} eventDates={new Set()} sma100={null} sma200={null} />);

    expect(screen.getByRole("button", { name: "Bar" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: "Line" })).toHaveAttribute("aria-pressed", "false");
    expect(chartMocks.seriesTypes).toEqual(["bar", "volume"]);
    expect(chartMocks.seriesDataCalls[0]).toEqual(expect.arrayContaining([
      expect.objectContaining({ open: 100, high: 110, low: 95, close: 108 }),
    ]));

    await act(async () => {
      screen.getByRole("button", { name: "Line" }).click();
    });

    expect(screen.getByRole("button", { name: "Bar" })).toHaveAttribute("aria-pressed", "false");
    expect(screen.getByRole("button", { name: "Line" })).toHaveAttribute("aria-pressed", "true");
    expect(chartMocks.seriesTypes.slice(-2)).toEqual(["line", "volume"]);
    expect(chartMocks.seriesDataCalls.at(-2)).toEqual(expect.arrayContaining([
      expect.objectContaining({ value: 108 }),
    ]));
  });
});
