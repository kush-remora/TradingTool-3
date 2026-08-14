import { describe, expect, it } from "vitest";
import type { WeeklyPriceWatchlistRow } from "../types";
import {
  buildShortHorizonBestAlignedRows,
  buildShortHorizonStockRow,
  buildShortHorizonShortlistRows,
  buildShortHorizonTabTwoShortlistRows,
  calculateClosePositionPct,
  calculateShortHorizonShortlistSize,
  classifyClosePosition,
  filterShortHorizonRowsByTabTwoFilters,
  filterShortHorizonRowsByEvidenceGate,
  filterShortHorizonRowsByShortlistGuards,
  getShortHorizonMoveAccelerationState,
  isShortHorizonMoveExtended,
} from "./shortHorizonSelector";

describe("shortHorizonSelector", () => {
  it("counts a five-session target only from completed future sessions", () => {
    const row = buildRow(26, (index) => index === 10
      ? { close: 100 }
      : index === 12 ? { high: 106 } : undefined);
    const result = buildShortHorizonStockRow(row);

    expect(result.eligibleDayCount).toBe(20);
    expect(result.successfulDayCount).toBe(1);
    expect(result.successfulDays[0]).toMatchObject({ date: "2026-07-11", movePct: 6 });
  });

  it("explains where successful starting days closed in their daily range", () => {
    const row = buildRow(26, (index) => index === 10
      ? { high: 110, low: 90, close: 109 }
      : index === 12 ? { high: 115 } : undefined);
    const result = buildShortHorizonStockRow(row);

    expect(result.successCloseBuckets.HIGH).toBe(result.successfulDayCount);
    expect(result.successCloseBuckets.MIDDLE).toBe(0);
    expect(result.successCloseBuckets.LOW).toBe(0);
  });

  it("keeps flat candles in the middle instead of inventing strength", () => {
    const day = { date: "2026-08-01", open: 100, high: 100, low: 100, close: 100, volume: 1, deliveryPercentage: null };

    expect(calculateClosePositionPct(day)).toBe(50);
    expect(classifyClosePosition(50)).toBe("MIDDLE");
  });

  it("calculates recent high pullback and the largest recent volume bar", () => {
    const row = buildRow(30, (index) => ({
      high: index === 22 ? 150 : 102,
      close: index === 29 ? 140 : 101,
      volume: index === 27 ? 400 : 100,
      open: index === 27 ? 110 : 100,
    }));
    const result = buildShortHorizonStockRow(row);

    expect(result.recentHigh).toBe(150);
    expect(result.recentHighDate).toBe("2026-07-23");
    expect(result.pullbackFromRecentHighPct).toBeCloseTo(-6.666, 2);
    expect(result.recentVolumeMultiple).toBe(4);
    expect(result.recentVolumeDate).toBe("2026-07-28");
    expect(result.recentVolumeDirection).toBe("DOWN");
  });

  it("flags abnormal recent volume without interpreting entry or exit", () => {
    const buildSequenceRow = (
      pushVolume: number,
      failureClose: number,
      failureHigh = 103,
      failureLow = 99,
    ) => buildRow(30, (index) => {
      if (index === 9) return { close: 90, volume: 100 };
      if (index === 28) return { open: 100, high: 103, low: 99, close: 102, volume: pushVolume };
      if (index === 29) return { open: 102, high: failureHigh, low: failureLow, close: failureClose, volume: 100 };
      return { volume: 100 };
    });

    const quiet = buildShortHorizonStockRow(buildSequenceRow(100, 101));
    const watchPush = buildShortHorizonStockRow(buildSequenceRow(220, 103));
    const watchAtThreshold = buildShortHorizonStockRow(buildSequenceRow(160, 103));
    const watchFailure = buildShortHorizonStockRow(buildSequenceRow(220, 99));
    const caution = buildShortHorizonStockRow(buildSequenceRow(160, 99));

    expect(quiet.volumeActivity).toBe("QUIET");
    expect(watchPush.volumeActivity).toBe("WATCH");
    expect(watchPush.volumeActivityMultiple).toBe(2.2);
    expect(watchAtThreshold.volumeActivity).toBe("WATCH");
    expect(watchFailure.volumeActivity).toBe("WATCH");
    expect(watchFailure.volumeActivityMultiple).toBe(2.2);
    expect(caution.volumeActivity).toBe("WATCH");
    expect(caution.volumeActivityMultiple).toBe(1.6);
    expect(caution.volumeActivityDate).toBe("2026-07-29");
  });

  it("flags a high-volume middle-close day as Watch without requiring a strong finish", () => {
    const row = buildRow(30, (index) => {
      if (index === 9) return { close: 90, volume: 100 };
      if (index === 28) return { open: 100, high: 110, low: 90, close: 100, volume: 260 };
      if (index === 29) return { open: 100, high: 103, low: 99, close: 101, volume: 100 };
      return { volume: 100 };
    });

    const result = buildShortHorizonStockRow(row);

    expect(result.volumeActivity).toBe("WATCH");
    expect(result.volumeActivityMultiple).toBe(2.6);
    expect(result.volumeActivityDate).toBe("2026-07-29");
  });

  it("flags a high-volume latest session as Watch before follow-through exists", () => {
    const row = buildRow(30, (index) => {
      if (index === 9) return { close: 90, volume: 100 };
      if (index === 29) return { open: 100, high: 110, low: 90, close: 100, volume: 220 };
      return { volume: 100 };
    });

    const result = buildShortHorizonStockRow(row);

    expect(result.volumeActivity).toBe("WATCH");
    expect(result.volumeActivityMultiple).toBe(2.2);
    expect(result.volumeActivityDate).toBe("2026-07-30");
  });

  it("calculates current five-day and twenty-day moves from prior closes", () => {
    const row = buildRow(30, (index) => {
      if (index === 9) return { low: 80, close: 90 };
      if (index === 24) return { close: 100 };
      if (index === 29) return { close: 120 };
      return undefined;
    });

    const result = buildShortHorizonStockRow(row);

    expect(result.currentFiveDayMovePct).toBeCloseTo(20, 5);
    expect(result.currentPreviousFiveDayMovePct).toBeCloseTo(-0.9901, 4);
    expect(result.currentPreviousTenDayMovePct).toBeCloseTo(12.2222, 4);
    expect(result.currentTwentyDayMovePct).toBeCloseTo(33.333, 2);
  });

  it("warns only when the twenty-day move is above the extension threshold", () => {
    expect(isShortHorizonMoveExtended(25)).toBe(false);
    expect(isShortHorizonMoveExtended(25.01)).toBe(true);
    expect(isShortHorizonMoveExtended(null)).toBe(false);
  });

  it("builds the latest twenty daily evidence rows newest first", () => {
    const row = buildRow(25, (index) => ({
      open: 100 + index,
      high: 103 + index,
      low: 98 + index,
      close: 101 + index,
      volume: index === 24 ? 200 : 100,
      deliveryPercentage: index === 24 ? 58.4 : 40,
    }));

    const result = buildShortHorizonStockRow(row);

    expect(result.recentDailyEvidence).toHaveLength(20);
    expect(result.recentDailyEvidence[0]).toMatchObject({
      date: "2026-07-25",
      open: 124,
      high: 127,
      low: 122,
      close: 125,
    });
    expect(result.recentDailyEvidence.at(-1)).toMatchObject({ date: "2026-07-06" });
    expect(result.recentDailyEvidence[0].changePct).toBeCloseTo(0.80645, 3);
    expect(result.recentDailyEvidence[0].closeFromHighPct).toBeCloseTo(-1.5748, 3);
    expect(result.recentDailyEvidence[0].volumeMultiple).toBe(2);
    expect(result.recentDailyEvidence[0].deliveryPercentage).toBe(58.4);
  });

  it("counts strong finishes only across the latest five sessions", () => {
    const row = buildRow(7, (index) => {
      if (index === 0 || index === 1) return { high: 110, low: 90, close: 109 };
      if (index === 2) return { high: 110, low: 90, close: 101 };
      if (index === 3) return { high: 110, low: 90, close: 103 };
      if (index === 4) return { high: 110, low: 90, close: 108 };
      if (index === 5) return { high: 100, low: 100, close: 100 };
      return { high: 110, low: 90, close: 102 };
    });

    const result = buildShortHorizonStockRow(row);

    expect(result.recentStrongFinishCount).toBe(2);
    expect(result.recentStrongFinishSessionCount).toBe(5);
    expect(result.recentDailyEvidence.slice(0, 5).map((day) => day.isStrongFinish)).toEqual([false, false, true, true, false]);
  });

  it("classifies a controlled upward five-session move as clean", () => {
    const row = buildRow(5, (index) => ({
      close: [100, 102, 103, 105, 106][index],
      high: [101, 103, 104, 106, 107][index],
      low: [98, 100, 101, 103, 104][index],
    }));

    expect(buildShortHorizonStockRow(row).recentMoveQuality).toBe("CLEAN");
  });

  it("classifies a violent alternating five-session move as wild", () => {
    const row = buildRow(5, (index) => ({
      close: [100, 106, 101, 108, 103][index],
      high: [106, 108, 108, 110, 105][index],
      low: [99, 100, 100, 101, 100][index],
    }));

    expect(buildShortHorizonStockRow(row).recentMoveQuality).toBe("WILD");
  });

  it("classifies an upward move with one meaningful give-back as mixed", () => {
    const row = buildRow(5, (index) => ({
      close: [100, 104, 102, 105, 106][index],
      high: [105, 105, 104, 106, 107][index],
      low: [99, 100, 100, 101, 102][index],
    }));

    expect(buildShortHorizonStockRow(row).recentMoveQuality).toBe("MIXED");
  });

  it("counts target successes in the most recent six eligible days", () => {
    const row = buildRow(26, (index) => index === 15 || index === 17
      ? { close: 100 }
      : index === 16 || index === 18 ? { high: 106 } : undefined);

    const result = buildShortHorizonStockRow(row);

    expect(result.recentEligibleDayCount).toBe(6);
    expect(result.recentSuccessfulDayCount).toBe(2);
    expect(result.recentSuccessRatePct).toBeCloseTo(33.33, 2);
  });

  it("calculates an adaptive shortlist size with a cap of twenty per rule", () => {
    expect(calculateShortHorizonShortlistSize(10)).toBe(2);
    expect(calculateShortHorizonShortlistSize(50)).toBe(10);
    expect(calculateShortHorizonShortlistSize(100)).toBe(20);
    expect(calculateShortHorizonShortlistSize(200)).toBe(20);
  });

  it("rejects rows below either minimum evidence threshold", () => {
    const baseRow = buildShortHorizonStockRow(buildRow(26));

    expect(filterShortHorizonRowsByEvidenceGate([{
      ...baseRow,
      successfulDayCount: 2,
      recentSuccessfulDayCount: 6,
    }])).toHaveLength(0);
    expect(filterShortHorizonRowsByEvidenceGate([{
      ...baseRow,
      successfulDayCount: 5,
      recentSuccessfulDayCount: 1,
    }])).toHaveLength(0);
    expect(filterShortHorizonRowsByEvidenceGate([{
      ...baseRow,
      successfulDayCount: 5,
      recentSuccessfulDayCount: 2,
    }])).toHaveLength(1);
  });

  it("removes stocks more than ten percent below the recent high", () => {
    const baseRow = {
      ...buildShortHorizonStockRow(buildRow(26)),
      successfulDayCount: 5,
      recentSuccessfulDayCount: 2,
    };

    expect(filterShortHorizonRowsByShortlistGuards([{
      ...baseRow,
      pullbackFromRecentHighPct: -9.9,
    }])).toHaveLength(1);
    expect(filterShortHorizonRowsByShortlistGuards([{
      ...baseRow,
      pullbackFromRecentHighPct: -10,
    }])).toHaveLength(1);
    expect(filterShortHorizonRowsByShortlistGuards([{
      ...baseRow,
      pullbackFromRecentHighPct: -10.1,
    }])).toHaveLength(0);
  });

  it("rejects only a three-close decline that breaks the previous five-session floor", () => {
    const buildWeaknessRow = (latestClose: number) => buildShortHorizonStockRow(buildRow(26, (index) => {
      if (index >= 20 && index <= 24) return { low: 95, close: index === 23 ? 103 : index === 24 ? 101 : 100 };
      if (index === 25) return { low: 90, high: 102, close: latestClose };
      return undefined;
    }));
    const controlledPullback = buildWeaknessRow(99);
    const breakdown = buildWeaknessRow(94);

    expect(controlledPullback.lastThreeClosesDeclining).toBe(true);
    expect(controlledPullback.latestCloseBelowPreviousFiveSessionLow).toBe(false);
    expect(breakdown.latestCloseBelowPreviousFiveSessionLow).toBe(true);
    expect(filterShortHorizonRowsByShortlistGuards([{
      ...controlledPullback,
      successfulDayCount: 5,
      recentSuccessfulDayCount: 2,
      pullbackFromRecentHighPct: -1,
    }])).toHaveLength(1);
    expect(filterShortHorizonRowsByShortlistGuards([{
      ...breakdown,
      successfulDayCount: 5,
      recentSuccessfulDayCount: 2,
      pullbackFromRecentHighPct: -1,
    }])).toHaveLength(0);
  });

  it("filters Tab 2 to accelerating moves while keeping volume activity as evidence", () => {
    const baseRow = buildShortHorizonStockRow(buildRow(30));
    const acceleratingQuiet = {
      ...baseRow,
      key: "ACCELERATING-QUIET",
      currentFiveDayMovePct: 8,
      currentPreviousFiveDayMovePct: 2,
      recentStrongFinishCount: 2,
      recentStrongFinishSessionCount: 5,
      volumeActivity: "QUIET" as const,
    };
    const acceleratingWatch = {
      ...baseRow,
      key: "ACCELERATING-WATCH",
      currentFiveDayMovePct: 8,
      currentPreviousFiveDayMovePct: 2,
      recentStrongFinishCount: 3,
      recentStrongFinishSessionCount: 5,
      volumeActivity: "WATCH" as const,
    };
    const steady = {
      ...baseRow,
      key: "STEADY",
      currentFiveDayMovePct: 2.5,
      currentPreviousFiveDayMovePct: 2,
      volumeActivity: "QUIET" as const,
    };

    expect(getShortHorizonMoveAccelerationState(acceleratingQuiet)).toBe("ACCELERATING");
    expect(filterShortHorizonRowsByTabTwoFilters(
      [acceleratingQuiet, acceleratingWatch, steady],
      { acceleration: "ACCELERATING", minimumStrongFinishCount: 3 },
    ).map((row) => row.key)).toEqual(["ACCELERATING-WATCH"]);
  });

  it("does not call a continuing decline acceleration", () => {
    const baseRow = buildShortHorizonStockRow(buildRow(30));
    const recovering = {
      ...baseRow,
      currentFiveDayMovePct: -1.5,
      currentPreviousFiveDayMovePct: -5.2,
    };
    const weakening = {
      ...baseRow,
      currentFiveDayMovePct: -5.2,
      currentPreviousFiveDayMovePct: -1.5,
    };

    expect(getShortHorizonMoveAccelerationState(recovering)).toBe("RECOVERING");
    expect(getShortHorizonMoveAccelerationState(weakening)).toBe("WEAKENING");
    expect(filterShortHorizonRowsByTabTwoFilters([recovering], {
      acceleration: "ACCELERATING",
      minimumStrongFinishCount: 0,
    })).toHaveLength(0);
  });

  it("builds Best aligned from acceleration and strong finishes only", () => {
    const baseRow = buildShortHorizonStockRow(buildRow(30));
    const qualifying = {
      ...baseRow,
      key: "QUALIFYING",
      currentFiveDayMovePct: 8,
      currentPreviousFiveDayMovePct: 2,
      recentStrongFinishCount: 2,
      recentStrongFinishSessionCount: 5,
      recentMoveQuality: "CLEAN" as const,
      volumeActivity: "WATCH" as const,
      lastThreeClosesDeclining: true,
      latestCloseBelowPreviousFiveSessionLow: true,
    };
    const notAccelerating = { ...qualifying, key: "STEADY", currentFiveDayMovePct: 2.5 };
    const notStrongEnough = { ...qualifying, key: "ONE-STRONG", recentStrongFinishCount: 1 };
    const notClean = { ...qualifying, key: "MIXED", recentMoveQuality: "MIXED" as const };

    expect(buildShortHorizonBestAlignedRows([qualifying, notAccelerating, notStrongEnough, notClean]).map((row) => row.key))
      .toEqual(["QUALIFYING", "MIXED"]);
  });

  it("filters Tab 2 before historical reach ranking instead of applying old reach gates first", () => {
    const rows = Array.from({ length: 100 }, (_, index) => ({
      ...buildShortHorizonStockRow(buildRow(26)),
      key: `CURRENT-${index}`,
      symbol: `CURRENT-${index}`,
      successfulDayCount: index === 0 ? 1 : 0,
      recentSuccessfulDayCount: 0,
      currentFiveDayMovePct: 6,
      currentPreviousFiveDayMovePct: 0,
      volumeActivity: "QUIET" as const,
      lastThreeClosesDeclining: false,
      latestCloseBelowPreviousFiveSessionLow: false,
    }));

    const shortlistRows = buildShortHorizonTabTwoShortlistRows(rows, {
      acceleration: "ACCELERATING",
      minimumStrongFinishCount: 0,
    });

    expect(shortlistRows).toHaveLength(100);
  });

  it("unites the adaptive 20-day and recent-six rankings", () => {
    const rows = Array.from({ length: 100 }, (_, index) => ({
      ...buildShortHorizonStockRow(buildRow(26)),
      key: `STOCK-${index}`,
      symbol: `STOCK-${index}`,
      successfulDayCount: 120 - index,
      recentSuccessfulDayCount: index >= 80 ? 6 : 2,
    }));

    const shortlistRows = buildShortHorizonShortlistRows(rows);

    expect(shortlistRows).toHaveLength(40);
    expect(shortlistRows[0].symbol).toBe("STOCK-0");
    expect(shortlistRows[19].symbol).toBe("STOCK-19");
    expect(shortlistRows[20].symbol).toBe("STOCK-80");
    expect(shortlistRows.at(-1)?.symbol).toBe("STOCK-99");
  });

  it("keeps the shortlist limit based on the original watchlist size after filtering", () => {
    const rows = Array.from({ length: 100 }, (_, index) => ({
      ...buildShortHorizonStockRow(buildRow(26)),
      key: `STOCK-${index}`,
      symbol: `STOCK-${index}`,
      successfulDayCount: index < 20 ? 100 - index : 5,
      recentSuccessfulDayCount: index >= 20 && index < 36 ? 6 : 2,
      pullbackFromRecentHighPct: index < 36 ? -1 : -20,
    }));

    const shortlistRows = buildShortHorizonShortlistRows(rows);

    expect(shortlistRows).toHaveLength(36);
  });

});

function buildRow(
  dayCount: number,
  override: (index: number) => Partial<WeeklyPriceWatchlistRow["days"][number]> | undefined = () => undefined,
): WeeklyPriceWatchlistRow {
  return {
    symbol: "ABC",
    companyName: "ABC Limited",
    instrumentToken: 123,
    days: Array.from({ length: dayCount }, (_, index) => ({
      date: `2026-07-${String(index + 1).padStart(2, "0")}`,
      open: 100,
      high: index === 0 ? 106 : 102,
      low: 98,
      close: index === 0 ? 100 : 101,
      volume: 1,
      deliveryPercentage: null,
      ...override(index),
    })),
  };
}
