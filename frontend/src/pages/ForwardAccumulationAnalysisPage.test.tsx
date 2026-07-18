import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { compareConfirmationRows, ForwardAccumulationAnalysisPage } from "./ForwardAccumulationAnalysisPage";
import type { AccumulationCaseSnapshot } from "../types";

const getJsonMock = vi.fn();
vi.mock("../utils/api", () => ({ getJson: (...args: unknown[]) => getJsonMock(...args), postJson: vi.fn() }));

describe("ForwardAccumulationAnalysisPage", () => {
  beforeEach(() => { getJsonMock.mockResolvedValue([]); });

  it("shows manual universe replay controls", async () => {
    render(<ForwardAccumulationAnalysisPage />);
    await waitFor(() => expect(getJsonMock).toHaveBeenCalledWith("/api/strategy/accumulation-analysis/runs", { useCache: false }));
    expect(screen.getByText("Forward Accumulation Analysis")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Run replay" })).toBeInTheDocument();
    expect(screen.getByText("Run a universe to view saved snapshots")).toBeInTheDocument();
  });

  it("keeps a stock's bases together when sorting confirmation dates", () => {
    const olderAlpha = snapshot("ALPHA", "2026-02-01");
    const latestAlpha = snapshot("ALPHA", "2026-04-01");
    const beta = snapshot("BETA", "2026-03-01");
    const phaseD = (row: AccumulationCaseSnapshot) => row.symbol === "BETA" ? ["2026-07-10"] : ["2026-07-15"];

    const rows = [olderAlpha, beta, latestAlpha].sort((left, right) => -compareConfirmationRows(left, right, phaseD, true));

    expect(rows.map((row) => `${row.symbol}-${row.chainEndDate}`)).toEqual(["ALPHA-2026-04-01", "ALPHA-2026-02-01", "BETA-2026-03-01"]);
  });
});

function snapshot(symbol: string, chainEndDate: string): AccumulationCaseSnapshot {
  return {
    symbol,
    chainStartDate: "2025-11-01",
    chainEndDate,
    asOfDate: chainEndDate,
    chainLengthSessions: 60,
    hitCount: 1,
    shape: "FLAT",
    shapeDecision: "VALID",
    valid: true,
    firstPhaseDDate: null,
    firstBreakoutDate: null,
    sessionsToPhaseD: null,
    sessionsToBreakout: null,
    confirmationDates: { phaseD: [], freshBreakout: [], fiftyTwoWeekHigh: [] },
    curatedWatchlists: [],
    sixMonthEvidence: null,
    shapeMetrics: null,
  };
}
