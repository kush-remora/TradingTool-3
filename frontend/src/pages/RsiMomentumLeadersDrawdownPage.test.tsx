import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { RsiMomentumLeadersDrawdownPage } from "./RsiMomentumLeadersDrawdownPage";

const useRsiMomentumLeadersDrawdownMock = vi.fn();

vi.mock("../hooks/useRsiMomentumLeadersDrawdown", () => ({
  useRsiMomentumLeadersDrawdown: (...args: unknown[]) => useRsiMomentumLeadersDrawdownMock(...args),
}));

describe("RsiMomentumLeadersDrawdownPage", () => {
  const setQueryMock = vi.fn();
  const reloadMock = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    useRsiMomentumLeadersDrawdownMock.mockReturnValue({
      query: {
        fromDate: "2025-04-19",
        toDate: "2026-04-19",
        topN: 10,
      },
      setQuery: setQueryMock,
      reload: reloadMock,
      loading: false,
      error: null,
      data: {
        meta: {
          fromDate: "2025-04-19",
          toDate: "2026-04-19",
          asOfDate: "2026-04-19",
          topN: 10,
          profileIds: ["largemidcap250", "smallcap250", "nifty50"],
        },
        combined: {
          rowCount: 2,
          ddTodayBucketSummary: {
            atLeast20Pct: 2,
            atLeast30Pct: 1,
            atLeast40Pct: 1,
            atLeast50Pct: 0,
            atLeast60Pct: 0,
          },
          dd20dMinBucketSummary: {
            atLeast20Pct: 2,
            atLeast30Pct: 2,
            atLeast40Pct: 1,
            atLeast50Pct: 1,
            atLeast60Pct: 0,
          },
          warnings: [],
          rows: [
            {
              symbol: "ABC",
              companyName: "ABC LTD",
              instrumentToken: 1,
              profileIds: ["largemidcap250"],
              entryCount: 15,
              bestRank: 1,
              firstSeen: "2025-06-01",
              lastSeen: "2026-04-15",
              high1yClose: 200,
              todayClose: 150,
              minClose20d: 140,
              ddTodayPct: -25,
              dd20dMinPct: -30,
              ddTodayBuckets: {
                atLeast20Pct: true,
                atLeast30Pct: false,
                atLeast40Pct: false,
                atLeast50Pct: false,
                atLeast60Pct: false,
              },
              dd20dMinBuckets: {
                atLeast20Pct: true,
                atLeast30Pct: true,
                atLeast40Pct: false,
                atLeast50Pct: false,
                atLeast60Pct: false,
              },
            },
            {
              symbol: "XYZ",
              companyName: "XYZ LTD",
              instrumentToken: 2,
              profileIds: ["smallcap250", "nifty50"],
              entryCount: 10,
              bestRank: 2,
              firstSeen: "2025-07-01",
              lastSeen: "2026-04-10",
              high1yClose: 100,
              todayClose: 60,
              minClose20d: 50,
              ddTodayPct: -40,
              dd20dMinPct: -50,
              ddTodayBuckets: {
                atLeast20Pct: true,
                atLeast30Pct: true,
                atLeast40Pct: true,
                atLeast50Pct: false,
                atLeast60Pct: false,
              },
              dd20dMinBuckets: {
                atLeast20Pct: true,
                atLeast30Pct: true,
                atLeast40Pct: true,
                atLeast50Pct: true,
                atLeast60Pct: false,
              },
            },
          ],
        },
        profiles: [
          {
            profileId: "largemidcap250",
            profileLabel: "LargeMidcap250",
            rowCount: 1,
            ddTodayBucketSummary: {
              atLeast20Pct: 1,
              atLeast30Pct: 0,
              atLeast40Pct: 0,
              atLeast50Pct: 0,
              atLeast60Pct: 0,
            },
            dd20dMinBucketSummary: {
              atLeast20Pct: 1,
              atLeast30Pct: 1,
              atLeast40Pct: 0,
              atLeast50Pct: 0,
              atLeast60Pct: 0,
            },
            warnings: [],
            rows: [],
          },
        ],
      },
    });
  });

  it("renders combined tab and supports refresh + date apply", () => {
    render(<RsiMomentumLeadersDrawdownPage />);

    expect(screen.getByText("RSI Leaders Drawdown")).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Combined" })).toBeInTheDocument();
    expect(screen.getByText("ABC")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /Refresh/i }));
    expect(reloadMock).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByRole("button", { name: "Apply Date Range" }));
    expect(setQueryMock).toHaveBeenCalled();
  });

  it("switches to 20D mode and supports search filtering", () => {
    render(<RsiMomentumLeadersDrawdownPage />);

    fireEvent.click(screen.getByText("20D Min DD"));

    const search = screen.getByPlaceholderText("Search symbol/company");
    fireEvent.change(search, { target: { value: "XYZ" } });

    expect(screen.getByText("XYZ")).toBeInTheDocument();
    expect(screen.queryByText("ABC")).not.toBeInTheDocument();
  });
});
