import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  buildDistinctNumberFilters,
  buildWakeUpSignal,
  PhaseDScannerPage,
  parseCsvOrTsv,
  resolveWakeUpSortRatio,
  resolveWakeUpVolumeContext,
} from "./PhaseDScannerPage";

const fetchMock = vi.fn();
const useStockQuotesMock = vi.fn();

vi.mock("../hooks/useStockQuotes", () => ({
  useStockQuotes: (...args: unknown[]) => useStockQuotesMock(...args),
}));

class MockEventSource {
  constructor(public url: string) {}
  onmessage: ((event: any) => void) | null = null;
  onerror: ((event: any) => void) | null = null;
  close() {}
}

function buildRow(overrides: Record<string, unknown> = {}) {
  return {
    symbol: "INFY",
    stockName: "Infosys",
    marketCapBucket: "Large Cap",
    closePrice: 1540.5,
    pctChange: "2.10%",
    volume: 250000,
    previousDayVolume: 180000,
    sector: "IT",
    industry: "Software",
    rocePct: 31.2,
    ronwPct: 28.1,
    netProfitAfterTax: 1000,
    debtEquityRatio: 0,
    volDry200dMinCount: 2,
    volDry60dMinCount: 3,
    volDry200dMin105Count: 5,
    volDry60dMin105Count: 6,
    indianPromoterPct: 14.8,
    foreignPromoterPct: 33.1,
    quarterlyGrossSales: 100,
    high52w: 1800,
    low52w: 1490,
    dist200dHighPct: -12.5,
    dist200dLowPct: 1,
    atrLt2pctCount: 4,
    addedOn: "2026-06-24",
    lastSeenOn: "2026-06-24",
    status: "chartinkFilter",
    instrumentToken: 408065,
    marketFieldsUpdatedOn: null,
    phase2DeliveryStatus: "NOT_RUN",
    phase2Reason: "awaiting_delivery_validation",
    phase2EvaluatedOn: null,
    deliveryQuantityToday: null,
    deliveryPctToday: null,
    wholesaleBaseDq: null,
    deliverySpikeRatio: null,
    deliverySpikeDays10d: null,
    deliverySpikeDays20d: null,
    deliverySupportDays10d: null,
    deliverySupportDays20d: null,
    ...overrides,
  };
}

describe("PhaseDScannerPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useStockQuotesMock.mockReturnValue({ quotesBySymbol: {}, loading: false, error: null });
    vi.stubGlobal("fetch", fetchMock);
    vi.stubGlobal("EventSource", MockEventSource);
  });

  it("renders the phase 1 and phase 2 summary", async () => {
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => [buildRow()],
    });

    render(<PhaseDScannerPage />);

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith("/api/strategy/phase-c/dashboard");
    });

    expect(screen.getByText("Review quiet Phase 1 candidates, then validate delivery conviction on demand.")).toBeInTheDocument();
    expect(screen.getByText("Run Delivery Validation")).toBeInTheDocument();
    expect(screen.getByText("Update Fresh Fields")).toBeInTheDocument();
    expect(screen.getByText("All Phase 1 Stocks (1)")).toBeInTheDocument();
    expect(screen.getByText("INFY")).toBeInTheDocument();
    expect(screen.getAllByText("Resolved").length).toBeGreaterThan(0);
  });

  it("shows a freshness warning when rows are not refreshed yet", async () => {
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => [buildRow({ marketFieldsUpdatedOn: null })],
    });

    render(<PhaseDScannerPage />);

    await waitFor(() => {
      expect(screen.getByText("Fresh market fields are not updated yet.")).toBeInTheDocument();
    });

    expect(
      screen.getByText("1 of 1 rows are still showing uploaded CSV values. Run Update Fresh Fields to pull daily candle data from Kite."),
    ).toBeInTheDocument();
  });

  it("runs delivery validation and refreshes the dashboard", async () => {
    fetchMock
      .mockResolvedValueOnce({
        ok: true,
        json: async () => [buildRow()],
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          evaluatedOn: "2026-06-24",
          totalStocks: 1,
          passed: 1,
          watch: 0,
          notPassed: 0,
          notRun: 0,
          dataMissing: 0,
        }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => [
          buildRow({
            phase2DeliveryStatus: "PASSED",
            phase2Reason: "strong_delivery_support",
            phase2EvaluatedOn: "2026-06-24",
            deliveryQuantityToday: 125000,
            deliveryPctToday: 58.4,
            wholesaleBaseDq: 82000,
            deliverySpikeRatio: 1.52,
            deliverySpikeDays10d: 2,
            deliverySpikeDays20d: 4,
            deliverySupportDays10d: 2,
            deliverySupportDays20d: 4,
          }),
        ],
      });

    render(<PhaseDScannerPage />);

    await waitFor(() => {
      expect(screen.getByText("INFY")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: /Run Delivery Validation/i }));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith("/api/strategy/phase-c/delivery-validation/run", {
        method: "POST",
      });
    });

    expect(screen.getByText("Validated 1 stocks: 1 passed, 0 watch, 0 not passed, 0 data missing.")).toBeInTheDocument();
    expect(screen.getByText("Passed")).toBeInTheDocument();
    expect(screen.getByText("strong_delivery_support")).toBeInTheDocument();
  }, 10000);

  it("updates fresh fields and reloads the dashboard", async () => {
    fetchMock
      .mockResolvedValueOnce({
        ok: true,
        json: async () => [buildRow({ marketFieldsUpdatedOn: null })],
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          refreshedCount: 1,
          refreshedOn: "2026-06-24",
        }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => [buildRow({ marketFieldsUpdatedOn: "2026-06-24", closePrice: 1555.25 })],
      });

    render(<PhaseDScannerPage />);

    await waitFor(() => {
      expect(screen.getByText("INFY")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: /Update Fresh Fields/i }));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith("/api/strategy/phase-c/fresh-fields/update", {
        method: "POST",
      });
    });

    expect(screen.getByText("Updated fresh market fields for 1 rows.")).toBeInTheDocument();
    expect(screen.getByText("Latest market-data date applied: 2026-06-24")).toBeInTheDocument();
    expect(screen.getByText("Fresh market fields are up to date.")).toBeInTheDocument();
    expect(screen.getByText("All 1 rows are using market data from 2026-06-24.")).toBeInTheDocument();
  }, 10000);

  it("shows refresh errors and preserves the current dashboard data", async () => {
    fetchMock
      .mockResolvedValueOnce({
        ok: true,
        json: async () => [buildRow({ marketFieldsUpdatedOn: null })],
      })
      .mockResolvedValueOnce({
        ok: false,
        json: async () => ({
          detail: "Cannot refresh fresh fields. Missing instrument token for: IDEA",
        }),
      });

    render(<PhaseDScannerPage />);

    await waitFor(() => {
      expect(screen.getByText("INFY")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: /Update Fresh Fields/i }));

    await waitFor(() => {
      expect(screen.getByText(/Cannot refresh fresh fields\./i)).toBeInTheDocument();
    });

    expect(screen.getByText("INFY")).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(2);
  }, 10000);

  it("shows only passed rows in the delivery conviction tab", async () => {
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => [
        buildRow({
          symbol: "INFY",
          phase2DeliveryStatus: "PASSED",
          phase2Reason: "strong_delivery_support",
        }),
        buildRow({
          symbol: "IDEA",
          stockName: "Vodafone Idea",
          instrumentToken: null,
          phase2DeliveryStatus: "NOT_PASSED",
          phase2Reason: "no_delivery_confirmation",
        }),
      ],
    });

    render(<PhaseDScannerPage />);

    await waitFor(() => {
      expect(screen.getByText("INFY")).toBeInTheDocument();
      expect(screen.getByText("IDEA")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("tab", { name: "Delivery Conviction (1)" }));

    expect(screen.getByText("INFY")).toBeInTheDocument();
    expect(screen.queryByText("IDEA")).not.toBeInTheDocument();
  });

  it("filters unresolved candidates", async () => {
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => [
        buildRow(),
        buildRow({
          symbol: "IDEA",
          stockName: "Vodafone Idea",
          marketCapBucket: "Mid Cap",
          closePrice: 15.2,
          pctChange: "-1.00%",
          volume: 500000,
          sector: "Telecom",
          industry: "Wireless",
          rocePct: null,
          ronwPct: null,
          netProfitAfterTax: null,
          debtEquityRatio: null,
          quarterlyGrossSales: null,
          high52w: null,
          low52w: null,
          dist200dHighPct: null,
          dist200dLowPct: null,
          atrLt2pctCount: null,
          instrumentToken: null,
          phase2DeliveryStatus: "DATA_MISSING",
          phase2Reason: "missing_instrument_token",
        }),
      ],
    });

    render(<PhaseDScannerPage />);

    await waitFor(() => {
      expect(screen.getByText("INFY")).toBeInTheDocument();
      expect(screen.getByText("IDEA")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: "Unresolved" }));

    expect(screen.queryByText("INFY")).not.toBeInTheDocument();
    expect(screen.getByText("IDEA")).toBeInTheDocument();
  }, 10000);

  it("sorts live market by today's percent change", async () => {
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => [
        buildRow({
          symbol: "INFY",
          pctChange: "-1.25%",
        }),
        buildRow({
          symbol: "TCS",
          stockName: "TCS",
          instrumentToken: 2953217,
          pctChange: "2.10%",
        }),
      ],
    });

    const { container } = render(<PhaseDScannerPage />);

    await waitFor(() => {
      expect(screen.getByText("INFY")).toBeInTheDocument();
      expect(screen.getAllByText("TCS").length).toBeGreaterThan(0);
    });

    fireEvent.click(screen.getAllByText("Live Market")[0]);

    const tableRows = Array.from(container.querySelectorAll(".ant-table-tbody > tr.ant-table-row"));
    expect(tableRows[0]?.textContent).toContain("TCS");
    expect(tableRows[1]?.textContent).toContain("INFY");
  });

  it("sorts wake-up by latest day volume vs t-1 ratio", async () => {
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => [
        buildRow({
          symbol: "INFY",
          volume: 200000,
          previousDayVolume: 100000,
        }),
        buildRow({
          symbol: "TCS",
          stockName: "TCS",
          instrumentToken: 2953217,
          volume: 450000,
          previousDayVolume: 100000,
        }),
      ],
    });

    const { container } = render(<PhaseDScannerPage />);

    await waitFor(() => {
      expect(screen.getByText("INFY")).toBeInTheDocument();
      expect(screen.getAllByText("TCS").length).toBeGreaterThan(0);
    });

    fireEvent.click(screen.getAllByText("Wake-Up")[0]);

    const tableRows = Array.from(container.querySelectorAll(".ant-table-tbody > tr.ant-table-row"));
    expect(tableRows[0]?.textContent).toContain("TCS");
    expect(tableRows[1]?.textContent).toContain("INFY");
  });

  it("builds distinct number filters for grouped column menus", () => {
    expect(buildDistinctNumberFilters([3, 1, 3, null], "60D Min")).toEqual([
      { text: "60D Min 1", value: "1" },
      { text: "60D Min 3", value: "3" },
    ]);

    expect(buildDistinctNumberFilters([7, 3, 7, null], "ATR Count")).toEqual([
      { text: "ATR Count 3", value: "3" },
      { text: "ATR Count 7", value: "7" },
    ]);

    expect(buildDistinctNumberFilters([5, 2, 5, null], "DQ Hits 10D")).toEqual([
      { text: "DQ Hits 10D 2", value: "2" },
      { text: "DQ Hits 10D 5", value: "5" },
    ]);
  });

  it("builds wake-up signals from price move and t-1 volume ratio", () => {
    expect(buildWakeUpSignal(4.2, 250000, 100000)).toMatchObject({
      score: 3,
      label: "MOVE + 2.5x",
    });

    expect(buildWakeUpSignal(1.2, 200000, 100000)).toMatchObject({
      score: 2,
      label: "VOL 2.0x",
    });

    expect(buildWakeUpSignal(-4.5, 150000, 100000)).toMatchObject({
      score: 1,
      label: "MOVE 4%",
    });

    expect(buildWakeUpSignal(1.5, 150000, 100000)).toMatchObject({
      score: 0,
      label: "Quiet",
    });
  });

  it("resolves wake-up volumes from live data first and daily fallback after market close", () => {
    expect(resolveWakeUpVolumeContext(600000, 250000, 180000)).toEqual({
      currentVolume: 600000,
      currentVolumeLabel: "Now",
      comparisonVolume: 180000,
      comparisonVolumeLabel: "T-1",
      volumeDiffPct: 233.33333333333334,
    });

    expect(resolveWakeUpVolumeContext(null, 250000, 180000)).toEqual({
      currentVolume: 250000,
      currentVolumeLabel: "Day",
      comparisonVolume: 180000,
      comparisonVolumeLabel: "T-1",
      volumeDiffPct: 38.88888888888889,
    });
  });

  it("resolves wake-up sort ratio from latest day volume vs t-1", () => {
    expect(resolveWakeUpSortRatio(250000, 100000)).toBe(2.5);
    expect(resolveWakeUpSortRatio(250000, 0)).toBeNull();
    expect(resolveWakeUpSortRatio(null, 100000)).toBeNull();
  });

  it("parses the cleaned CSV headers without losing columns", () => {
    const csv = [
      "Sr.,Stock Name,Symbol,market_cap_bucket,close_price,pct_change,volume,sector,industry,vol_dry_200d_min_count,vol_dry_60d_min_count,vol_dry_200d_min_105_count,vol_dry_60d_min_105_count,roce_pct,ronw_pct,net_profit_after_tax,debt_equity_ratio,atr_lt_2pct_count,indian_promoter_pct,foreign_promoter_pct,quarterly_gross_sales,high_52w,low_52w,dist_200d_high_pct,dist_200d_low_pct,Add Column",
      '"1","Sanofi India Limited","SANOFI","midcap","3,410.2","2%","34,239","healthcare","pharmaceuticals - multinational","0","2","0","2","54.17","40.59","102.6","0.02","9","0","60.4","472.3","6410","3174.6","-34.11","11.66",""',
    ].join("\n");

    const rows = parseCsvOrTsv(csv);

    expect(rows).toHaveLength(1);
    expect(rows[0]).toMatchObject({
      symbol: "SANOFI",
      stockName: "Sanofi India Limited",
      marketCapBucket: "midcap",
      closePrice: 3410.2,
      pctChange: "2%",
      volume: 34239,
      sector: "healthcare",
      industry: "pharmaceuticals - multinational",
      volDry200dMinCount: 0,
      volDry60dMinCount: 2,
      volDry200dMin105Count: 0,
      volDry60dMin105Count: 2,
      rocePct: 54.17,
      ronwPct: 40.59,
      netProfitAfterTax: 102.6,
      debtEquityRatio: 0.02,
      atrLt2pctCount: 9,
      indianPromoterPct: 0,
      foreignPromoterPct: 60.4,
      quarterlyGrossSales: 472.3,
      high52w: 6410,
      low52w: 3174.6,
      dist200dHighPct: -34.11,
      dist200dLowPct: 11.66,
    });
  });
});
