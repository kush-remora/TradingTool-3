import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { HotSmaPage } from "./HotSmaPage";

const useHotSmaScannerMock = vi.fn();

vi.mock("../hooks/useHotSmaScanner", () => ({
  useHotSmaScanner: () => useHotSmaScannerMock(),
}));

vi.mock("../utils/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../utils/api")>();
  return {
    ...actual,
    getJson: vi.fn().mockImplementation((path: string) => {
      if (path === "/api/strategy/hot-sma/universes") {
        return Promise.resolve([
          { value: "WATCHLIST", count: 20 },
          { value: "NIFTY_50", count: 50 },
        ]);
      }
      throw new Error(`Unexpected path: ${path}`);
    }),
  };
});

describe("HotSmaPage", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  it("runs with the selected universes", async () => {
    const run = vi.fn().mockResolvedValue(undefined);
    useHotSmaScannerMock.mockReturnValue({ data: null, loading: false, error: null, run });

    render(<HotSmaPage />);

    await waitFor(() => {
      expect(screen.getByRole("button", { name: /Run Scanner/i })).toBeInTheDocument();
    });

    fireEvent.mouseDown(screen.getByRole("combobox"));
    fireEvent.click(await screen.findByText("NIFTY_50 (50)"));
    fireEvent.click(screen.getByRole("button", { name: /Run Scanner/i }));

    await waitFor(() => {
      expect(run).toHaveBeenCalledWith({ indexKeys: ["WATCHLIST", "NIFTY_50"] });
    });
  });

  it("renders buy zone rows and summary", async () => {
    useHotSmaScannerMock.mockReturnValue({
      loading: false,
      error: null,
      run: vi.fn(),
      data: {
        runAt: "2026-06-23T00:00:00Z",
        selectedIndexKeys: ["WATCHLIST"],
        config: {
          rsiPeriod: 14,
          sma50Window: 50,
          sma100Window: 100,
          sma200Window: 200,
          buyZoneUpperPct: 5,
          drawdownWindow20: 20,
          drawdownWindow60: 60,
          useAvailableHistoryForSma200: true,
        },
        summary: {
          totalStocks: 2,
          buyZoneCount: 1,
          aboveBuyZoneCount: 1,
          noSma200Count: 0,
        },
        rows: [
          {
            symbol: "INFY",
            companyName: "Infosys",
            indexKey: "WATCHLIST",
            instrumentToken: 101,
            latestDate: "2026-06-23",
            currentPrice: 100,
            sma50: 99,
            sma100: 98,
            sma200: 97,
            pctToSma50: 1,
            pctToSma100: 2,
            pctToSma200: 3,
            distanceToSma200AbsPct: 3,
            rsi14: 48,
            drawdownFromHigh20Pct: -4,
            drawdownFromHigh60Pct: -8,
            consecutiveRedDays: 2,
            move3dPct: -1.5,
            sma100TouchedInLast5d: false,
            sma100TouchDate: null,
            sma200TouchedInLast5d: false,
            sma200TouchDate: null,
            signalTag: null,
            zoneStatus: "BUY_ZONE",
          },
          {
            symbol: "TCS",
            companyName: "TCS",
            indexKey: "WATCHLIST",
            instrumentToken: 102,
            latestDate: "2026-06-23",
            currentPrice: 110,
            sma50: 100,
            sma100: 99,
            sma200: 98,
            pctToSma50: 10,
            pctToSma100: 11,
            pctToSma200: 12,
            distanceToSma200AbsPct: 12,
            rsi14: 55,
            drawdownFromHigh20Pct: -2,
            drawdownFromHigh60Pct: -5,
            consecutiveRedDays: 0,
            move3dPct: 0.5,
            sma100TouchedInLast5d: false,
            sma100TouchDate: null,
            sma200TouchedInLast5d: false,
            sma200TouchDate: null,
            signalTag: null,
            zoneStatus: "ABOVE_BUY_ZONE",
          },
        ],
      },
    });

    render(<HotSmaPage />);

    await waitFor(() => {
      expect(screen.getByText("Stocks: 2 | Buy zone: 1 | Above zone: 1")).toBeInTheDocument();
      expect(screen.getByText("BUY_ZONE")).toBeInTheDocument();
      expect(screen.getByText("ABOVE_BUY_ZONE")).toBeInTheDocument();
    });
  });
});
