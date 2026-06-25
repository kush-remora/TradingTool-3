import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { DayDetail } from "../types";
import { TradeMarketHistoryPanel } from "./TradeMarketHistoryPanel";

const useStockDetailMock = vi.fn();

vi.mock("../hooks/useStockDetail", () => ({
  useStockDetail: (...args: unknown[]) => useStockDetailMock(...args),
}));

function makeDay(
  date: string,
  open: number,
  close: number,
  low: number,
  high: number,
  volume: number,
): DayDetail {
  return {
    date,
    open,
    close,
    low,
    high,
    volume,
    daily_change_pct: null,
    rsi14: null,
    vol_ratio: null,
  };
}

describe("TradeMarketHistoryPanel", () => {
  it("renders summary strip, legend, and signal tags when signalConfig is passed", () => {
    useStockDetailMock.mockReturnValue({
      data: {
        symbol: "NETWEB",
        avg_volume_20d: null,
        days: [
          {
            ...makeDay("2026-04-01", 100, 102, 99, 105, 200),
            daily_change_pct: 2.0,
            vol_ratio: 2.0,
          },
          {
            ...makeDay("2026-04-02", 100, 99, 99, 104, 100),
            daily_change_pct: -1.0,
            vol_ratio: 1.0,
          },
        ],
      },
      loading: false,
      error: null,
    });

    render(
      <TradeMarketHistoryPanel
        symbol="NETWEB"
        days={10}
        defaultExpanded
        showToggle={false}
        signalConfig={{ entryPrice: 100, entryDayLow: 96, stopPrice: 90 }}
        showSignalLegend
      />,
    );

    expect(screen.getByText(/Avg vol:/i)).toBeInTheDocument();
    expect(screen.getByText(/Support zone:/i)).toBeInTheDocument();
    expect(screen.getByText(/Your entry:/i)).toBeInTheDocument();

    expect(screen.getAllByText(/dip \+ recovery/i).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/high vol/i).length).toBeGreaterThan(0);
    expect(screen.getByText(/low in buy zone/i)).toBeInTheDocument();
    expect(screen.getByText(/low at\/below entry/i)).toBeInTheDocument();
    expect(screen.getAllByText("Px").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Vol / Sig").length).toBeGreaterThan(0);
    expect(screen.getByText("Wed")).toBeInTheDocument();
    expect(screen.getByText("C ₹99.00")).toBeInTheDocument();
    expect(screen.getAllByText(/0x|1\.0x|2\.0x/i).length).toBeGreaterThan(0);
    expect(screen.getAllByText("HV").length).toBeGreaterThan(0);
  });

  it("falls back cleanly without signalConfig", () => {
    useStockDetailMock.mockReturnValue({
      data: {
        symbol: "NETWEB",
        avg_volume_20d: null,
        days: [makeDay("2026-04-01", 100, 101, 99, 104, 100)],
      },
      loading: false,
      error: null,
    });

    render(
      <TradeMarketHistoryPanel
        symbol="NETWEB"
        defaultExpanded
        showToggle={false}
      />,
    );

    expect(screen.getByText(/NETWEB/i)).toBeInTheDocument();
    expect(screen.getByText(/Avg vol:/i)).toBeInTheDocument();
    expect(screen.queryByText(/low in buy zone/i)).not.toBeInTheDocument();
  });

  it("allows custom session window changes from default 10", async () => {
    useStockDetailMock.mockReturnValue({
      data: {
        symbol: "NETWEB",
        avg_volume_20d: null,
        days: [makeDay("2026-04-01", 100, 101, 99, 104, 100)],
      },
      loading: false,
      error: null,
    });

    render(
      <TradeMarketHistoryPanel
        symbol="NETWEB"
        days={10}
        defaultExpanded
        showToggle={false}
      />,
    );

    const dayInput = screen.getByRole("spinbutton");
    fireEvent.change(dayInput, { target: { value: "20" } });
    fireEvent.blur(dayInput);

    await waitFor(() => {
      expect(useStockDetailMock).toHaveBeenLastCalledWith("NETWEB", 20);
    });
  });
});
