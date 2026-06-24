import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { PhaseDScannerPage, parseCsvOrTsv } from "./PhaseDScannerPage";

const fetchMock = vi.fn();

describe("PhaseDScannerPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.stubGlobal("fetch", fetchMock);
  });

  it("renders the redesigned watchlist summary", async () => {
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => [
        {
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
          netProfit3qAgo: 1000,
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
        },
      ],
    });

    render(<PhaseDScannerPage />);

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith("/api/strategy/phase-c/dashboard");
    });

    expect(screen.getByText("Build the ignition watchlist before the trigger engine exists.")).toBeInTheDocument();
    expect(screen.getByText("Phase C watchlist")).toBeInTheDocument();
    expect(screen.getByText("INFY")).toBeInTheDocument();
    expect(screen.getAllByText("Resolved").length).toBeGreaterThan(0);
  });

  it("filters unresolved candidates", async () => {
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => [
        {
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
          netProfit3qAgo: 1000,
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
        },
        {
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
          netProfit3qAgo: null,
          debtEquityRatio: null,
          volDry200dMinCount: 1,
          volDry60dMinCount: 2,
          volDry200dMin105Count: 4,
          volDry60dMin105Count: 5,
          indianPromoterPct: null,
          foreignPromoterPct: null,
          quarterlyGrossSales: null,
          high52w: null,
          low52w: null,
          dist200dHighPct: null,
          dist200dLowPct: null,
          atrLt2pctCount: null,
          addedOn: "2026-06-24",
          lastSeenOn: "2026-06-24",
          status: "chartinkFilter",
          instrumentToken: null,
        },
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
      "Sr.,Stock Name,Symbol,market_cap_bucket,close_price,pct_change,volume,sector,industry,vol_dry_200d_min_count,vol_dry_60d_min_count,vol_dry_200d_min_105_count,vol_dry_60d_min_105_count,roce_pct,ronw_pct,net_profit_3q_ago,debt_equity_ratio,atr_lt_2pct_count,indian_promoter_pct,foreign_promoter_pct,quarterly_gross_sales,high_52w,low_52w,dist_200d_high_pct,dist_200d_low_pct,Add Column",
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
      netProfit3qAgo: 102.6,
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
