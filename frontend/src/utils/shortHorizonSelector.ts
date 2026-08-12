import type { WeeklyPriceWatchlistDay, WeeklyPriceWatchlistRow } from "../types";

export const SHORT_HORIZON_TARGET_PCT = 5;
export const SHORT_HORIZON_FORWARD_SESSIONS = 5;
export const SHORT_HORIZON_LOOKBACK_SESSIONS = 20;
export const SHORT_HORIZON_RECENT_SUCCESS_SESSIONS = 6;
export const SHORT_HORIZON_RECENT_HIGH_SESSIONS = 20;
export const SHORT_HORIZON_RECENT_VOLUME_SESSIONS = 5;
export const SHORT_HORIZON_VOLUME_BASELINE_SESSIONS = 20;
export const SHORT_HORIZON_SHORTLIST_FRACTION = 0.2;
export const SHORT_HORIZON_MAX_SHORTLIST_COUNT = 20;
export const SHORT_HORIZON_MIN_SUCCESSFUL_DAYS = 5;
export const SHORT_HORIZON_MIN_RECENT_SUCCESSFUL_DAYS = 2;
export const SHORT_HORIZON_MAX_PULLBACK_FROM_HIGH_PCT = -10;
export const SHORT_HORIZON_DECLINING_CLOSE_SESSIONS = 3;
export const SHORT_HORIZON_SUPPORT_LOOKBACK_SESSIONS = 5;
export const SHORT_HORIZON_CURRENT_MOVE_SESSIONS = 5;
export const SHORT_HORIZON_CURRENT_TREND_SESSIONS = 20;

export type ClosePositionBucket = "HIGH" | "MIDDLE" | "LOW";
export type PriceDirection = "UP" | "FLAT" | "DOWN";

export interface ShortHorizonSuccessDay {
  key: string;
  date: string;
  startClose: number;
  forwardHigh: number;
  movePct: number;
  closePositionPct: number;
  closePositionBucket: ClosePositionBucket;
  direction: PriceDirection;
}

export interface ShortHorizonStockRow {
  key: string;
  symbol: string;
  companyName: string;
  instrumentToken: number;
  latestDate: string | null;
  latestClose: number | null;
  latestClosePositionPct: number | null;
  latestClosePositionBucket: ClosePositionBucket | null;
  latestDirection: PriceDirection | null;
  recentHigh: number | null;
  recentHighDate: string | null;
  pullbackFromRecentHighPct: number | null;
  recentVolumeMultiple: number | null;
  recentVolumeDate: string | null;
  recentVolumeDirection: PriceDirection | null;
  currentFiveDayMovePct: number | null;
  currentTwentyDayMovePct: number | null;
  fiftyTwoWeekHigh: number | null;
  distanceFromFiftyTwoWeekHighPct: number | null;
  lastThreeClosesDeclining: boolean | null;
  previousFiveSessionLow: number | null;
  latestCloseBelowPreviousFiveSessionLow: boolean | null;
  eligibleDayCount: number;
  successfulDayCount: number;
  successRatePct: number | null;
  recentEligibleDayCount: number;
  recentSuccessfulDayCount: number;
  recentSuccessRatePct: number | null;
  successfulDays: ShortHorizonSuccessDay[];
  successCloseBuckets: Record<ClosePositionBucket, number>;
}

function sortDays(days: WeeklyPriceWatchlistDay[]): WeeklyPriceWatchlistDay[] {
  return [...days].sort((left, right) => left.date.localeCompare(right.date));
}

export function calculateClosePositionPct(day: WeeklyPriceWatchlistDay): number {
  const range = day.high - day.low;
  if (range <= 0) return 50;

  return Math.min(100, Math.max(0, ((day.close - day.low) / range) * 100));
}

export function classifyClosePosition(positionPct: number): ClosePositionBucket {
  if (positionPct >= 75) return "HIGH";
  if (positionPct <= 25) return "LOW";
  return "MIDDLE";
}

export function classifyPriceDirection(day: WeeklyPriceWatchlistDay): PriceDirection {
  if (day.close > day.open) return "UP";
  if (day.close < day.open) return "DOWN";
  return "FLAT";
}

function getEligibleReferenceDays(days: WeeklyPriceWatchlistDay[]): WeeklyPriceWatchlistDay[] {
  const sortedDays = sortDays(days);
  const lastEligibleIndex = sortedDays.length - SHORT_HORIZON_FORWARD_SESSIONS;
  if (lastEligibleIndex <= 0) return [];

  const firstEligibleIndex = Math.max(0, lastEligibleIndex - SHORT_HORIZON_LOOKBACK_SESSIONS);
  return sortedDays.slice(firstEligibleIndex, lastEligibleIndex);
}

function calculateRecentHighEvidence(days: WeeklyPriceWatchlistDay[]): {
  recentHigh: number | null;
  recentHighDate: string | null;
  pullbackFromRecentHighPct: number | null;
} {
  const recentDays = days.slice(-SHORT_HORIZON_RECENT_HIGH_SESSIONS);
  const highDay = recentDays.reduce<WeeklyPriceWatchlistDay | null>(
    (currentHighDay, day) => currentHighDay == null || day.high > currentHighDay.high ? day : currentHighDay,
    null,
  );
  const latestClose = days.at(-1)?.close ?? null;

  return {
    recentHigh: highDay?.high ?? null,
    recentHighDate: highDay?.date ?? null,
    pullbackFromRecentHighPct: highDay == null || latestClose == null || highDay.high <= 0
      ? null
      : ((latestClose - highDay.high) / highDay.high) * 100,
  };
}

function calculateRecentWeaknessEvidence(days: WeeklyPriceWatchlistDay[]): {
  lastThreeClosesDeclining: boolean | null;
  previousFiveSessionLow: number | null;
  latestCloseBelowPreviousFiveSessionLow: boolean | null;
} {
  const latestDay = days.at(-1) ?? null;
  const previousDays = days.slice(-1 - SHORT_HORIZON_SUPPORT_LOOKBACK_SESSIONS, -1);
  const lastThreeDays = days.slice(-SHORT_HORIZON_DECLINING_CLOSE_SESSIONS);
  const lastThreeClosesDeclining = lastThreeDays.length < SHORT_HORIZON_DECLINING_CLOSE_SESSIONS
    ? null
    : lastThreeDays[0].close > lastThreeDays[1].close
      && lastThreeDays[1].close > lastThreeDays[2].close;
  const previousFiveSessionLow = previousDays.length < SHORT_HORIZON_SUPPORT_LOOKBACK_SESSIONS
    ? null
    : Math.min(...previousDays.map((day) => day.low));

  return {
    lastThreeClosesDeclining,
    previousFiveSessionLow,
    latestCloseBelowPreviousFiveSessionLow: latestDay == null || previousFiveSessionLow == null
      ? null
      : latestDay.close < previousFiveSessionLow,
  };
}

export function calculateShortHorizonShortlistSize(stockCount: number): number {
  return Math.min(
    SHORT_HORIZON_MAX_SHORTLIST_COUNT,
    Math.ceil(Math.max(0, stockCount) * SHORT_HORIZON_SHORTLIST_FRACTION),
  );
}

export function passesShortHorizonEvidenceGate(row: ShortHorizonStockRow): boolean {
  return row.eligibleDayCount >= SHORT_HORIZON_LOOKBACK_SESSIONS
    && row.successfulDayCount >= SHORT_HORIZON_MIN_SUCCESSFUL_DAYS
    && row.recentEligibleDayCount >= SHORT_HORIZON_RECENT_SUCCESS_SESSIONS
    && row.recentSuccessfulDayCount >= SHORT_HORIZON_MIN_RECENT_SUCCESSFUL_DAYS;
}

export function filterShortHorizonRowsByEvidenceGate(rows: ShortHorizonStockRow[]): ShortHorizonStockRow[] {
  return rows.filter(passesShortHorizonEvidenceGate);
}

export function passesShortHorizonPriceGuardrail(row: ShortHorizonStockRow): boolean {
  const hasRecentBreakdown = row.lastThreeClosesDeclining === true
    && row.latestCloseBelowPreviousFiveSessionLow === true;

  return row.pullbackFromRecentHighPct != null
    && row.pullbackFromRecentHighPct >= SHORT_HORIZON_MAX_PULLBACK_FROM_HIGH_PCT
    && !hasRecentBreakdown;
}

export function filterShortHorizonRowsByShortlistGuards(rows: ShortHorizonStockRow[]): ShortHorizonStockRow[] {
  return filterShortHorizonRowsByEvidenceGate(rows).filter(passesShortHorizonPriceGuardrail);
}

function calculateRecentVolumeEvidence(days: WeeklyPriceWatchlistDay[]): {
  recentVolumeMultiple: number | null;
  recentVolumeDate: string | null;
  recentVolumeDirection: PriceDirection | null;
} {
  const recentVolumeStart = Math.max(0, days.length - SHORT_HORIZON_RECENT_VOLUME_SESSIONS);
  const baselineDays = days
    .slice(Math.max(0, recentVolumeStart - SHORT_HORIZON_VOLUME_BASELINE_SESSIONS), recentVolumeStart)
    .filter((day) => day.volume > 0);
  const recentVolumeDays = days.slice(recentVolumeStart).filter((day) => day.volume > 0);
  const largestVolumeDay = recentVolumeDays.reduce<WeeklyPriceWatchlistDay | null>(
    (currentLargestDay, day) => currentLargestDay == null || day.volume > currentLargestDay.volume ? day : currentLargestDay,
    null,
  );
  const averageBaselineVolume = baselineDays.length === 0
    ? null
    : baselineDays.reduce((total, day) => total + day.volume, 0) / baselineDays.length;

  return {
    recentVolumeMultiple: largestVolumeDay == null || averageBaselineVolume == null || averageBaselineVolume <= 0
      ? null
      : largestVolumeDay.volume / averageBaselineVolume,
    recentVolumeDate: largestVolumeDay?.date ?? null,
    recentVolumeDirection: largestVolumeDay == null ? null : classifyPriceDirection(largestVolumeDay),
  };
}

function calculateMoveFromPastClose(days: WeeklyPriceWatchlistDay[], lookbackSessions: number): number | null {
  const latestClose = days.at(-1)?.close ?? null;
  const pastClose = days.at(-1 - lookbackSessions)?.close ?? null;
  return latestClose == null || pastClose == null || pastClose <= 0
    ? null
    : ((latestClose - pastClose) / pastClose) * 100;
}

function buildSuccessfulDay(
  referenceDay: WeeklyPriceWatchlistDay,
  futureDays: WeeklyPriceWatchlistDay[],
): ShortHorizonSuccessDay | null {
  const forwardHigh = Math.max(...futureDays.map((day) => day.high));
  const movePct = ((forwardHigh - referenceDay.close) / referenceDay.close) * 100;
  if (movePct < SHORT_HORIZON_TARGET_PCT) return null;

  const closePositionPct = calculateClosePositionPct(referenceDay);
  return {
    key: referenceDay.date,
    date: referenceDay.date,
    startClose: referenceDay.close,
    forwardHigh,
    movePct,
    closePositionPct,
    closePositionBucket: classifyClosePosition(closePositionPct),
    direction: classifyPriceDirection(referenceDay),
  };
}

export function buildShortHorizonStockRow(row: WeeklyPriceWatchlistRow): ShortHorizonStockRow {
  const sortedDays = sortDays(row.days);
  const referenceDays = getEligibleReferenceDays(sortedDays);
  const recentHighEvidence = calculateRecentHighEvidence(sortedDays);
  const recentVolumeEvidence = calculateRecentVolumeEvidence(sortedDays);
  const recentWeaknessEvidence = calculateRecentWeaknessEvidence(sortedDays);
  const evaluatedDays = referenceDays
    .map((referenceDay) => {
      const referenceIndex = sortedDays.findIndex((day) => day.date === referenceDay.date);
      return buildSuccessfulDay(
        referenceDay,
        sortedDays.slice(referenceIndex + 1, referenceIndex + 1 + SHORT_HORIZON_FORWARD_SESSIONS),
      );
    });
  const successfulDays = evaluatedDays
    .filter((day): day is ShortHorizonSuccessDay => day != null)
    .reverse();
  const recentEvaluatedDays = evaluatedDays.slice(-SHORT_HORIZON_RECENT_SUCCESS_SESSIONS);
  const recentSuccessfulDayCount = recentEvaluatedDays.filter((day) => day != null).length;
  const latestDay = sortedDays.at(-1) ?? null;
  const successCloseBuckets: Record<ClosePositionBucket, number> = {
    HIGH: 0,
    MIDDLE: 0,
    LOW: 0,
  };
  successfulDays.forEach((day) => {
    successCloseBuckets[day.closePositionBucket] += 1;
  });

  return {
    key: row.symbol,
    symbol: row.symbol,
    companyName: row.companyName,
    instrumentToken: row.instrumentToken,
    latestDate: latestDay?.date ?? null,
    latestClose: latestDay?.close ?? null,
    latestClosePositionPct: latestDay == null ? null : calculateClosePositionPct(latestDay),
    latestClosePositionBucket: latestDay == null ? null : classifyClosePosition(calculateClosePositionPct(latestDay)),
    latestDirection: latestDay == null ? null : classifyPriceDirection(latestDay),
    ...recentHighEvidence,
    ...recentVolumeEvidence,
    currentFiveDayMovePct: calculateMoveFromPastClose(sortedDays, SHORT_HORIZON_CURRENT_MOVE_SESSIONS),
    currentTwentyDayMovePct: calculateMoveFromPastClose(sortedDays, SHORT_HORIZON_CURRENT_TREND_SESSIONS),
    fiftyTwoWeekHigh: row.momentum_evidence?.fifty_two_week_high ?? null,
    distanceFromFiftyTwoWeekHighPct: row.momentum_evidence?.distance_from_fifty_two_week_high_pct ?? null,
    ...recentWeaknessEvidence,
    eligibleDayCount: referenceDays.length,
    successfulDayCount: successfulDays.length,
    successRatePct: referenceDays.length === 0 ? null : (successfulDays.length / referenceDays.length) * 100,
    recentEligibleDayCount: recentEvaluatedDays.length,
    recentSuccessfulDayCount,
    recentSuccessRatePct: recentEvaluatedDays.length === 0 ? null : (recentSuccessfulDayCount / recentEvaluatedDays.length) * 100,
    successfulDays,
    successCloseBuckets,
  };
}

export function getShortHorizonShortlistRuleDescription(): string {
  return `Pass ${SHORT_HORIZON_MIN_SUCCESSFUL_DAYS}/${SHORT_HORIZON_LOOKBACK_SESSIONS} and ${SHORT_HORIZON_MIN_RECENT_SUCCESSFUL_DAYS}/${SHORT_HORIZON_RECENT_SUCCESS_SESSIONS}; stay within ${Math.abs(SHORT_HORIZON_MAX_PULLBACK_FROM_HIGH_PCT)}% of the recent ${SHORT_HORIZON_RECENT_HIGH_SESSIONS}-session high; reject only if the last ${SHORT_HORIZON_DECLINING_CLOSE_SESSIONS} closes fall in a row and today's close breaks below the previous ${SHORT_HORIZON_SUPPORT_LOOKBACK_SESSIONS}-session low.`;
}

export function buildShortHorizonRows(rows: WeeklyPriceWatchlistRow[]): ShortHorizonStockRow[] {
  return rows
    .map(buildShortHorizonStockRow)
    .sort((left, right) =>
      right.successfulDayCount - left.successfulDayCount
      || (right.successRatePct ?? -1) - (left.successRatePct ?? -1)
      || (right.latestClosePositionPct ?? -1) - (left.latestClosePositionPct ?? -1)
      || left.symbol.localeCompare(right.symbol),
    );
}

function rankShortHorizonRowsByLongWindow(rows: ShortHorizonStockRow[]): ShortHorizonStockRow[] {
  return [...rows].sort((left, right) =>
    right.successfulDayCount - left.successfulDayCount
    || (right.successRatePct ?? -1) - (left.successRatePct ?? -1)
    || right.recentSuccessfulDayCount - left.recentSuccessfulDayCount
    || left.symbol.localeCompare(right.symbol),
  );
}

function rankShortHorizonRowsByRecentWindow(rows: ShortHorizonStockRow[]): ShortHorizonStockRow[] {
  return [...rows].sort((left, right) =>
    right.recentSuccessfulDayCount - left.recentSuccessfulDayCount
    || (right.recentSuccessRatePct ?? -1) - (left.recentSuccessRatePct ?? -1)
    || right.successfulDayCount - left.successfulDayCount
    || left.symbol.localeCompare(right.symbol),
  );
}

export function buildShortHorizonShortlistRows(rows: ShortHorizonStockRow[]): ShortHorizonStockRow[] {
  const eligibleRows = filterShortHorizonRowsByShortlistGuards(rows);
  const shortlistSizePerRule = calculateShortHorizonShortlistSize(rows.length);
  const longWindowRows = rankShortHorizonRowsByLongWindow(eligibleRows).slice(0, shortlistSizePerRule);
  const recentWindowRows = rankShortHorizonRowsByRecentWindow(eligibleRows).slice(0, shortlistSizePerRule);
  const longWindowKeys = new Set(longWindowRows.map((row) => row.key));

  return [...longWindowRows, ...recentWindowRows.filter((row) => !longWindowKeys.has(row.key))]
    .slice(0, SHORT_HORIZON_MAX_SHORTLIST_COUNT * 2);
}

export function buildShortHorizonCoreRows(rows: ShortHorizonStockRow[]): ShortHorizonStockRow[] {
  const eligibleRows = filterShortHorizonRowsByShortlistGuards(rows);
  const shortlistSizePerRule = calculateShortHorizonShortlistSize(rows.length);
  const longWindowKeys = new Set(
    rankShortHorizonRowsByLongWindow(eligibleRows)
      .slice(0, shortlistSizePerRule)
      .map((row) => row.key),
  );
  const recentWindowKeys = new Set(
    rankShortHorizonRowsByRecentWindow(eligibleRows)
      .slice(0, shortlistSizePerRule)
      .map((row) => row.key),
  );

  return eligibleRows
    .filter((row) => longWindowKeys.has(row.key) && recentWindowKeys.has(row.key))
    .sort((left, right) =>
      (right.distanceFromFiftyTwoWeekHighPct ?? -Infinity) - (left.distanceFromFiftyTwoWeekHighPct ?? -Infinity)
      || right.successfulDayCount - left.successfulDayCount
      || right.recentSuccessfulDayCount - left.recentSuccessfulDayCount
      || left.symbol.localeCompare(right.symbol),
    );
}
