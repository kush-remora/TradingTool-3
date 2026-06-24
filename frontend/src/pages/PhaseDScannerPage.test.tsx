import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { PhaseDScannerPage, parseCsvOrTsv } from "./PhaseDScannerPage";

const fetchMock = vi.fn();

function buildRow(overrides: Record<string, unknown> = {}) {
  return {
    symbol: "INFY",
    stockName: "Infosys",
    marketCapBucket: "Large Cap",
    closePrice: 1540.5,
    pctChange: "2.10%",
    volume: 250000,
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
    convictionDays10d: null,
    convictionDays20d: null,
    ...overrides,
  };
}

describe("PhaseDScannerPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.stubGlobal("fetch", fetchMock);
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
            convictionDays10d: 2,
            convictionDays20d: 4,
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
  });

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
  });

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
  });

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
