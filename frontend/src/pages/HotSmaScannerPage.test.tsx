import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { HotSmaScannerPage } from "./HotSmaScannerPage";

const useHotSmaScannerMock = vi.fn();

vi.mock("../hooks/useHotSmaScanner", () => ({
  useHotSmaScanner: () => useHotSmaScannerMock(),
}));

vi.mock("../utils/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../utils/api")>();
  return {
    ...actual,
    getJson: vi.fn().mockResolvedValue({
      options: [
        { label: "NIFTY_50", value: "NIFTY_50", count: 50 },
        { label: "NIFTY_100", value: "NIFTY_100", count: 100 },
      ],
    }),
  };
});

describe("HotSmaScannerPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();

    vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
    Object.defineProperty(URL, "createObjectURL", {
      writable: true,
      value: vi.fn().mockReturnValue("blob:test-url"),
    });
    Object.defineProperty(URL, "revokeObjectURL", {
      writable: true,
      value: vi.fn(),
    });
  });

  it("runs scanner with selected index key", async () => {
    const run = vi.fn().mockResolvedValue(undefined);

    useHotSmaScannerMock.mockReturnValue({
      data: null,
      loading: false,
      error: null,
      run,
      sendTelegramForRow: vi.fn(),
    });

    render(<HotSmaScannerPage />);

    await waitFor(() => {
      expect(screen.getByRole("button", { name: /Run Scan/i })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: /Run Scan/i }));

    await waitFor(() => {
      expect(run).toHaveBeenCalledTimes(1);
    });

    expect(run).toHaveBeenCalledWith({ indexKey: "NIFTY_50" });
  });

  it("filters rows by signal and allows csv export", async () => {
    const run = vi.fn().mockResolvedValue(undefined);

    useHotSmaScannerMock.mockReturnValue({
      loading: false,
      error: null,
      run,
      sendTelegramForRow: vi.fn(),
      data: {
        runAt: "2026-06-02T00:00:00Z",
        selectedIndexKey: "NIFTY_50",
        config: {
          touchLookbackDays: 5,
          watchZoneUpperPct: 10,
          rsiPeriod: 14,
          sma50Window: 50,
          sma100Window: 100,
          sma200Window: 200,
          useAvailableHistoryForSma200: true,
        },
        summary: {
          totalSignals: 2,
          aggressiveCount: 1,
          standardCount: 1,
          watchCount: 0,
        },
        rows: [
          {
            symbol: "RELIANCE",
            companyName: "Reliance Industries",
            indexKey: "NIFTY_50",
            instrumentToken: 1,
            latestDate: "2026-06-02",
            currentPrice: 100,
            sma50: 95,
            sma100: 90,
            sma200: 85,
            pctToSma50: 5.26,
            pctToSma100: 11.11,
            pctToSma200: 17.64,
            rsi14: 63,
            sma100TouchedInLast5d: false,
            sma100TouchDate: null,
            sma200TouchedInLast5d: true,
            sma200TouchDate: "2026-05-30",
            signalTag: "AGGRESSIVE_BUY",
          },
          {
            symbol: "INFY",
            companyName: "Infosys",
            indexKey: "NIFTY_50",
            instrumentToken: 2,
            latestDate: "2026-06-02",
            currentPrice: 100,
            sma50: 98,
            sma100: 97,
            sma200: 90,
            pctToSma50: 2.04,
            pctToSma100: 3.09,
            pctToSma200: 11.11,
            rsi14: 52,
            sma100TouchedInLast5d: true,
            sma100TouchDate: "2026-05-31",
            sma200TouchedInLast5d: false,
            sma200TouchDate: null,
            signalTag: "STANDARD_BUY",
          },
        ],
      },
    });

    render(<HotSmaScannerPage />);

    await waitFor(() => {
      expect(screen.getByText("RELIANCE")).toBeInTheDocument();
      expect(screen.getByText("INFY")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("radio", { name: "Aggressive" }));

    await waitFor(() => {
      expect(screen.getByText("RELIANCE")).toBeInTheDocument();
      expect(screen.queryByText("INFY")).not.toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: /Export CSV/i }));

    await waitFor(() => {
      expect(URL.createObjectURL).toHaveBeenCalledTimes(1);
    });
  });
});
