import type { WeeklyPriceWatchlistDay, WeeklyPriceWatchlistRow } from "../types";

export const SHORT_HORIZON_TARGET_PCT = 5;
export const SHORT_HORIZON_FORWARD_SESSIONS = 5;
export const SHORT_HORIZON_LOOKBACK_SESSIONS = 20;
export const SHORT_HORIZON_RECENT_SUCCESS_SESSIONS = 6;
export const SHORT_HORIZON_RECENT_HIGH_SESSIONS = 20;
export const SHORT_HORIZON_RECENT_VOLUME_SESSIONS = 5;
export const SHORT_HORIZON_VOLUME_AVERAGE_SESSIONS = 10;
export const SHORT_HORIZON_VOLUME_BASELINE_SESSIONS = 20;
export const SHORT_HORIZON_SHORTLIST_FRACTION = 0.2;
export const SHORT_HORIZON_MAX_SHORTLIST_COUNT = 20;
export const SHORT_HORIZON_MIN_SUCCESSFUL_DAYS = 5;
export const SHORT_HORIZON_MIN_RECENT_SUCCESSFUL_DAYS = 2;
export const SHORT_HORIZON_MAX_PULLBACK_FROM_HIGH_PCT = -10;
export const SHORT_HORIZON_DECLINING_CLOSE_SESSIONS = 3;
export const SHORT_HORIZON_SUPPORT_LOOKBACK_SESSIONS = 5;
export const SHORT_HORIZON_CURRENT_MOVE_SESSIONS = 5;
export const SHORT_HORIZON_CURRENT_INTERMEDIATE_MOVE_SESSIONS = 10;
export const SHORT_HORIZON_CURRENT_TREND_SESSIONS = 20;
export const SHORT_HORIZON_MOVE_ACCELERATION_TOLERANCE_PCT = 1;
export const SHORT_HORIZON_OVEREXTENDED_TWENTY_DAY_MOVE_PCT = 25;
export const SHORT_HORIZON_EXIT_PRESSURE_LOOKBACK_SESSIONS = 3;
export const SHORT_HORIZON_EXIT_PRESSURE_VOLUME_BASELINE_SESSIONS = 10;
export const SHORT_HORIZON_EXIT_PRESSURE_VOLUME_SPIKE_MULTIPLE = 1.5;
export const SHORT_HORIZON_EXIT_PRESSURE_WEAK_CLOSE_POSITION_PCT = 40;
export const SHORT_HORIZON_EXIT_PRESSURE_MIN_FAILURE_MOVE_PCT = -1;
export const SHORT_HORIZON_STRONG_FINISH_LOOKBACK_SESSIONS = 5;
export const SHORT_HORIZON_STRONG_FINISH_MIN_CLOSE_POSITION_PCT = 60;
export const SHORT_HORIZON_MOVE_QUALITY_LOOKBACK_SESSIONS = 5;
export const SHORT_HORIZON_MOVE_QUALITY_MIN_UP_CLOSES = 3;
export const SHORT_HORIZON_MOVE_QUALITY_MAX_CLEAN_DIRECTION_CHANGES = 1;
export const SHORT_HORIZON_MOVE_QUALITY_MIN_CLEAN_EFFICIENCY = 0.6;
export const SHORT_HORIZON_MOVE_QUALITY_WILD_DIRECTION_CHANGES = 3;
export const SHORT_HORIZON_MOVE_QUALITY_MAX_WILD_EFFICIENCY = 0.35;

export type ClosePositionBucket = "HIGH" | "MIDDLE" | "LOW";
export type PriceDirection = "UP" | "FLAT" | "DOWN";
export type MoveQuality = "CLEAN" | "MIXED" | "WILD";
export type ExitPressure = "QUIET" | "WATCH" | "CAUTION";

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

export interface ShortHorizonDailyEvidence {
  key: string;
  date: string;
  open: number;
  high: number;
  low: number;
  close: number;
  changePct: number | null;
  closePositionPct: number;
  closePositionBucket: ClosePositionBucket;
  direction: PriceDirection;
  closeFromHighPct: number | null;
  volumeMultiple: number | null;
  isStrongFinish: boolean;
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
  exitPressure: ExitPressure | null;
  exitPressureVolumeMultiple: number | null;
  exitPressureDate: string | null;
  exitPressureDirection: PriceDirection | null;
  currentFiveDayMovePct: number | null;
  currentPreviousFiveDayMovePct: number | null;
  currentPreviousTenDayMovePct: number | null;
  currentTwentyDayMovePct: number | null;
  recentStrongFinishCount: number;
  recentStrongFinishSessionCount: number;
  recentMoveQuality: MoveQuality | null;
  recentDailyEvidence: ShortHorizonDailyEvidence[];
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

export function isShortHorizonMoveExtended(movePct: number | null): boolean {
  return movePct != null && movePct > SHORT_HORIZON_OVEREXTENDED_TWENTY_DAY_MOVE_PCT;
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

function calculateRecentExitPressureEvidence(
  days: WeeklyPriceWatchlistDay[],
  currentTwentyDayMovePct: number | null,
): {
  exitPressure: ExitPressure | null;
  exitPressureVolumeMultiple: number | null;
  exitPressureDate: string | null;
  exitPressureDirection: PriceDirection | null;
} {
  const recentStart = Math.max(0, days.length - SHORT_HORIZON_EXIT_PRESSURE_LOOKBACK_SESSIONS);
  const recentDays = days.slice(recentStart);
  const pushCandidates = recentDays.slice(0, -1).map((pushDay, index) => {
    const pushDayIndex = recentStart + index;
    const failureDay = recentDays[index + 1];
    const volumeMultiple = calculateVolumeMultiple(
      pushDay,
      days.slice(Math.max(0, pushDayIndex - SHORT_HORIZON_EXIT_PRESSURE_VOLUME_BASELINE_SESSIONS), pushDayIndex),
    );
    const closePositionPct = calculateClosePositionPct(pushDay);
    const previousClose = days[pushDayIndex - 1]?.close ?? null;
    const isStrongClosePush = closePositionPct > SHORT_HORIZON_STRONG_FINISH_MIN_CLOSE_POSITION_PCT
      && previousClose != null
      && pushDay.close > previousClose;
    const isStrongPush = volumeMultiple != null
      && volumeMultiple >= SHORT_HORIZON_EXIT_PRESSURE_VOLUME_SPIKE_MULTIPLE
      && isStrongClosePush;
    const failureMovePct = pushDay.close <= 0
      ? null
      : ((failureDay.close - pushDay.close) / pushDay.close) * 100;
    const failedFollowThrough = isStrongClosePush
      && failureDay.close < pushDay.close
      && (
        calculateClosePositionPct(failureDay) <= SHORT_HORIZON_EXIT_PRESSURE_WEAK_CLOSE_POSITION_PCT
        || failureDay.close < pushDay.low
        || (failureMovePct != null && failureMovePct <= SHORT_HORIZON_EXIT_PRESSURE_MIN_FAILURE_MOVE_PCT)
      );

    return {
      pushDay,
      failureDay,
      volumeMultiple,
      isStrongClosePush,
      isStrongPush,
      failedFollowThrough,
    };
  }).filter((candidate) => candidate.volumeMultiple != null);

  const hasRecentRun = currentTwentyDayMovePct != null && currentTwentyDayMovePct > 0;
  const strongCloseCandidates = pushCandidates.filter((candidate) => candidate.isStrongClosePush);
  const strongestCandidate = strongCloseCandidates.reduce<typeof strongCloseCandidates[number] | null>(
    (currentCandidate, candidate) => currentCandidate == null || (candidate.volumeMultiple ?? 0) > (currentCandidate.volumeMultiple ?? 0)
      ? candidate
      : currentCandidate,
    null,
  );
  const failedStrongPush = pushCandidates.find((candidate) => candidate.isStrongPush && candidate.failedFollowThrough);
  const strongPush = strongCloseCandidates.find((candidate) => candidate.isStrongPush);
  const anyFailedFollowThrough = strongCloseCandidates.find((candidate) => candidate.failedFollowThrough);

  if (!hasRecentRun || strongestCandidate == null) {
    return {
      exitPressure: "QUIET",
      exitPressureVolumeMultiple: strongestCandidate?.volumeMultiple ?? null,
      exitPressureDate: strongestCandidate?.pushDay.date ?? null,
      exitPressureDirection: strongestCandidate == null ? null : classifyPriceDirection(strongestCandidate.pushDay),
    };
  }

  return {
    exitPressure: failedStrongPush != null || anyFailedFollowThrough != null
      ? failedStrongPush != null ? "CAUTION" : "WATCH"
      : strongPush != null ? "WATCH" : "QUIET",
    exitPressureVolumeMultiple: strongestCandidate.volumeMultiple,
    exitPressureDate: strongestCandidate.pushDay.date,
    exitPressureDirection: classifyPriceDirection(strongestCandidate.pushDay),
  };
}

function calculateMoveFromPastClose(days: WeeklyPriceWatchlistDay[], lookbackSessions: number): number | null {
  const latestClose = days.at(-1)?.close ?? null;
  const pastClose = days.at(-1 - lookbackSessions)?.close ?? null;
  return latestClose == null || pastClose == null || pastClose <= 0
    ? null
    : ((latestClose - pastClose) / pastClose) * 100;
}

function calculateMoveBetweenPastCloses(
  days: WeeklyPriceWatchlistDay[],
  newerLookbackSessions: number,
  olderLookbackSessions: number,
): number | null {
  const newerClose = days.at(-1 - newerLookbackSessions)?.close ?? null;
  const olderClose = days.at(-1 - olderLookbackSessions)?.close ?? null;
  return newerClose == null || olderClose == null || olderClose <= 0
    ? null
    : ((newerClose - olderClose) / olderClose) * 100;
}

function calculateRecentStrongFinishEvidence(days: WeeklyPriceWatchlistDay[]): {
  recentStrongFinishCount: number;
  recentStrongFinishSessionCount: number;
} {
  const recentDays = days.slice(-SHORT_HORIZON_STRONG_FINISH_LOOKBACK_SESSIONS);
  const recentStrongFinishCount = recentDays.filter((day) =>
    isStrongFinishPosition(calculateClosePositionPct(day)),
  ).length;

  return {
    recentStrongFinishCount,
    recentStrongFinishSessionCount: recentDays.length,
  };
}

function isStrongFinishPosition(positionPct: number): boolean {
  return positionPct > SHORT_HORIZON_STRONG_FINISH_MIN_CLOSE_POSITION_PCT;
}

function calculateRecentMoveQuality(days: WeeklyPriceWatchlistDay[]): MoveQuality | null {
  const recentDays = days.slice(-SHORT_HORIZON_MOVE_QUALITY_LOOKBACK_SESSIONS);
  if (recentDays.length < SHORT_HORIZON_MOVE_QUALITY_LOOKBACK_SESSIONS) return null;

  const closeReturns = recentDays.slice(1).map((day, index) => {
    const previousClose = recentDays[index].close;
    return previousClose <= 0 ? 0 : (day.close - previousClose) / previousClose;
  });
  const upCloseCount = closeReturns.filter((move) => move > 0).length;
  const startingClose = recentDays[0].close;
  const endingClose = recentDays.at(-1)?.close ?? startingClose;
  const netMove = startingClose <= 0 ? 0 : (endingClose - startingClose) / startingClose;
  const pathMovement = closeReturns.reduce((total, move) => total + Math.abs(move), 0);
  const pathEfficiency = pathMovement === 0 ? 0 : Math.abs(netMove) / pathMovement;
  const directionChanges = closeReturns.slice(1).reduce((count, move, index) => {
    const previousMove = closeReturns[index];
    return move !== 0 && previousMove !== 0 && Math.sign(move) !== Math.sign(previousMove)
      ? count + 1
      : count;
  }, 0);
  const recentStrongFinishCount = calculateRecentStrongFinishEvidence(recentDays).recentStrongFinishCount;

  if (
    netMove > 0
    && upCloseCount >= SHORT_HORIZON_MOVE_QUALITY_MIN_UP_CLOSES
    && recentStrongFinishCount >= SHORT_HORIZON_MOVE_QUALITY_MIN_UP_CLOSES
    && directionChanges <= SHORT_HORIZON_MOVE_QUALITY_MAX_CLEAN_DIRECTION_CHANGES
    && pathEfficiency >= SHORT_HORIZON_MOVE_QUALITY_MIN_CLEAN_EFFICIENCY
  ) {
    return "CLEAN";
  }

  if (
    directionChanges >= SHORT_HORIZON_MOVE_QUALITY_WILD_DIRECTION_CHANGES
    || pathEfficiency < SHORT_HORIZON_MOVE_QUALITY_MAX_WILD_EFFICIENCY
  ) {
    return "WILD";
  }

  return "MIXED";
}

function calculateVolumeMultiple(
  day: WeeklyPriceWatchlistDay,
  previousDays: WeeklyPriceWatchlistDay[],
): number | null {
  const baselineDays = previousDays.filter((previousDay) => previousDay.volume > 0);
  if (day.volume <= 0 || baselineDays.length < SHORT_HORIZON_VOLUME_AVERAGE_SESSIONS) return null;

  const averageVolume = baselineDays.reduce((total, previousDay) => total + previousDay.volume, 0) / baselineDays.length;
  return averageVolume <= 0 ? null : day.volume / averageVolume;
}

function calculateRecentDailyEvidence(days: WeeklyPriceWatchlistDay[]): ShortHorizonDailyEvidence[] {
  const recentStartIndex = Math.max(0, days.length - SHORT_HORIZON_RECENT_HIGH_SESSIONS);

  return days
    .slice(recentStartIndex)
    .map((day, index) => {
      const absoluteDayIndex = recentStartIndex + index;
      const previousClose = days[absoluteDayIndex - 1]?.close ?? null;
      const closePositionPct = calculateClosePositionPct(day);

      return {
        key: day.date,
        date: day.date,
        open: day.open,
        high: day.high,
        low: day.low,
        close: day.close,
        changePct: previousClose == null || previousClose <= 0
          ? null
          : ((day.close - previousClose) / previousClose) * 100,
        closePositionPct,
        closePositionBucket: classifyClosePosition(closePositionPct),
        direction: classifyPriceDirection(day),
        closeFromHighPct: day.high <= 0 ? null : ((day.close - day.high) / day.high) * 100,
        volumeMultiple: calculateVolumeMultiple(
          day,
          days.slice(Math.max(0, absoluteDayIndex - SHORT_HORIZON_VOLUME_AVERAGE_SESSIONS), absoluteDayIndex),
        ),
        isStrongFinish: isStrongFinishPosition(closePositionPct),
      };
    })
    .reverse();
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
  const currentTwentyDayMovePct = calculateMoveFromPastClose(sortedDays, SHORT_HORIZON_CURRENT_TREND_SESSIONS);
  const recentExitPressureEvidence = calculateRecentExitPressureEvidence(sortedDays, currentTwentyDayMovePct);
  const recentWeaknessEvidence = calculateRecentWeaknessEvidence(sortedDays);
  const recentStrongFinishEvidence = calculateRecentStrongFinishEvidence(sortedDays);
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
    currentPreviousFiveDayMovePct: calculateMoveBetweenPastCloses(
      sortedDays,
      SHORT_HORIZON_CURRENT_MOVE_SESSIONS,
      SHORT_HORIZON_CURRENT_INTERMEDIATE_MOVE_SESSIONS,
    ),
    currentPreviousTenDayMovePct: calculateMoveBetweenPastCloses(
      sortedDays,
      SHORT_HORIZON_CURRENT_INTERMEDIATE_MOVE_SESSIONS,
      SHORT_HORIZON_CURRENT_TREND_SESSIONS,
    ),
    currentTwentyDayMovePct,
    ...recentExitPressureEvidence,
    ...recentStrongFinishEvidence,
    recentMoveQuality: calculateRecentMoveQuality(sortedDays),
    recentDailyEvidence: calculateRecentDailyEvidence(sortedDays),
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
  return `Require 5D reach of at least ${SHORT_HORIZON_MIN_SUCCESSFUL_DAYS}/${SHORT_HORIZON_LOOKBACK_SESSIONS} and Recent tested 6D reach of at least ${SHORT_HORIZON_MIN_RECENT_SUCCESSFUL_DAYS}/${SHORT_HORIZON_RECENT_SUCCESS_SESSIONS}; stay within ${Math.abs(SHORT_HORIZON_MAX_PULLBACK_FROM_HIGH_PCT)}% of the recent ${SHORT_HORIZON_RECENT_HIGH_SESSIONS}-session high; reject only if the last ${SHORT_HORIZON_DECLINING_CLOSE_SESSIONS} closes fall in a row and today's close breaks below the previous ${SHORT_HORIZON_SUPPORT_LOOKBACK_SESSIONS}-session low.`;
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
