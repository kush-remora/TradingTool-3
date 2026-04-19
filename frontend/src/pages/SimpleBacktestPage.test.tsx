import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { SimpleBacktestPage } from "./SimpleBacktestPage";

const useRsiMomentumMock = vi.fn();
const useSimpleMomentumBacktestMock = vi.fn();
const postJsonMock = vi.fn();

vi.mock("../hooks/useRsiMomentum", () => ({
  useRsiMomentum: () => useRsiMomentumMock(),
}));

vi.mock("../hooks/useSimpleMomentumBacktest", () => ({
  useSimpleMomentumBacktest: () => useSimpleMomentumBacktestMock(),
}));

vi.mock("../utils/api", () => ({
  postJson: (...args: unknown[]) => postJsonMock(...args),
}));

describe("SimpleBacktestPage", () => {
  const runMock = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();

    useRsiMomentumMock.mockReturnValue({
      loading: false,
      error: null,
      data: {
        profiles: [
          {
            profileId: "largemidcap250",
            profileLabel: "LargeMidcap250",
            config: { profileLabel: "LargeMidcap250" },
          },
          {
            profileId: "smallcap250",
            profileLabel: "Smallcap250",
            config: { profileLabel: "Smallcap250" },
          },
        ],
      },
    });

    useSimpleMomentumBacktestMock.mockReturnValue({
      data: null,
      loading: false,
      error: null,
      run: runMock,
    });

    postJsonMock.mockResolvedValue({
      profileId: "largemidcap250",
      profileLabel: "LargeMidcap250",
      baseUniversePreset: "NIFTY_LARGEMIDCAP_250",
      requestedFromDate: "2025-01-01",
      requestedToDate: "2026-01-01",
      symbolsTargeted: 250,
      candleSync: {
        fromDate: "2025-01-01",
        toDate: "2026-01-01",
        totalSymbols: 250,
        symbolsSynced: 250,
        symbolsFailed: 0,
        failedSymbols: [],
        dailyCandlesUpserted: 10000,
      },
      snapshotBackfill: {
        profileId: "largemidcap250",
        fromDate: "2025-01-01",
        toDate: "2026-01-01",
        tradingDatesFound: 250,
        datesSkipped: 0,
        datesProcessed: 250,
        datesFailed: 0,
        message: "ok",
      },
      warnings: [],
    });
  });

  it("renders profile tabs and controls", () => {
    render(<SimpleBacktestPage />);

    expect(screen.getByRole("tab", { name: "LargeMidcap250" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Smallcap250" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Prepare Data" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Run Backtest" })).toBeInTheDocument();
    expect(screen.getByText("Capital:")).toBeInTheDocument();
  });

  it("calls prepare endpoint and run backtest with selected profile", async () => {
    render(<SimpleBacktestPage />);

    fireEvent.click(screen.getByRole("button", { name: "Prepare Data" }));

    await waitFor(() => {
      expect(postJsonMock).toHaveBeenCalled();
    });

    expect(postJsonMock).toHaveBeenCalledWith(
      "/api/strategy/rsi-momentum/backtest/simple/prepare",
      expect.objectContaining({ profileId: "largemidcap250" }),
    );

    fireEvent.click(screen.getByRole("button", { name: "Run Backtest" }));

    await waitFor(() => {
        expect(runMock).toHaveBeenCalledWith(
          expect.objectContaining({
            profileId: "largemidcap250",
            initialCapital: 200000,
            entryRankMin: 1,
            entryRankMax: 5,
            holdRankMax: 10,
          }),
        );
    });
  });
});
