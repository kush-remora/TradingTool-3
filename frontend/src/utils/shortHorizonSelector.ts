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
export const SHORT_HORIZON_VOLUME_ACTIVITY_LOOKBACK_SESSIONS = 3;
export const SHORT_HORIZON_VOLUME_ACTIVITY_VOLUME_BASELINE_SESSIONS = 10;
export const SHORT_HORIZON_VOLUME_ACTIVITY_WATCH_MULTIPLE = 1.5;
export const SHORT_HORIZON_STRONG_FINISH_LOOKBACK_SESSIONS = 5;
export const SHORT_HORIZON_STRONG_FINISH_MIN_CLOSE_POSITION_PCT = 60;
export const SHORT_HORIZON_BEST_ALIGNED_MIN_STRONG_FINISHES = 2;
export const SHORT_HORIZON_MOVE_QUALITY_LOOKBACK_SESSIONS = 5;
export const SHORT_HORIZON_MOVE_QUALITY_MIN_UP_CLOSES = 3;
export const SHORT_HORIZON_MOVE_QUALITY_MAX_CLEAN_DIRECTION_CHANGES = 1;
export const SHORT_HORIZON_MOVE_QUALITY_MIN_CLEAN_EFFICIENCY = 0.6;
export const SHORT_HORIZON_MOVE_QUALITY_WILD_DIRECTION_CHANGES = 3;
export const SHORT_HORIZON_MOVE_QUALITY_MAX_WILD_EFFICIENCY = 0.35;

export type ClosePositionBucket = "HIGH" | "MIDDLE" | "LOW";
export type PriceDirection = "UP" | "FLAT" | "DOWN";
export type MoveQuality = "CLEAN" | "MIXED" | "WILD";
export type VolumeActivity = "QUIET" | "WATCH";
export type MoveAccelerationState = "ACCELERATING" | "RECOVERING" | "WEAKENING" | "STEADY" | "UNKNOWN";
export type ShortHorizonTabTwoAccelerationFilter = MoveAccelerationState | "ANY";

export interface ShortHorizonTabTwoFilters {
  acceleration: ShortHorizonTabTwoAccelerationFilter;
  minimumStrongFinishCount: number;
}

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
  deliveryPercentage: number | null;
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
  volumeActivity: VolumeActivity | null;
  volumeActivityMultiple: number | null;
  volumeActivityDate: string | null;
  volumeActivityDirection: PriceDirection | null;
  currentFiveDayMovePct: number | null;
  currentPreviousFiveDayMovePct: number | null;
  currentPreviousTenDayMovePct: number | null;
  currentTwentyDayMovePct: number | null;
  recentStrongFinishCount: number;
  recentStrongFinishSessionCount: number;
  recentMoveQuality: MoveQuality | null;
  recentDailyEvidence: ShortHorizonDailyEvidence[];
  fiftyTwoWeekHigh: number | null;
  fiftyTwoWeekHighDate: string | null;
  fiftyTwoWeekHighSessionsAgo: number | null;
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

export function getShortHorizonMoveAccelerationState(row: ShortHorizonStockRow): MoveAccelerationState {
  if (row.currentFiveDayMovePct == null || row.currentPreviousFiveDayMovePct == null) return "UNKNOWN";
  const paceChange = row.currentFiveDayMovePct - row.currentPreviousFiveDayMovePct;
  if (row.currentFiveDayMovePct > 0 && paceChange >= SHORT_HORIZON_MOVE_ACCELERATION_TOLERANCE_PCT) return "ACCELERATING";
  if (row.currentFiveDayMovePct < 0 && row.currentPreviousFiveDayMovePct < 0 && paceChange > 0) return "RECOVERING";
  if (paceChange <= -SHORT_HORIZON_MOVE_ACCELERATION_TOLERANCE_PCT) return "WEAKENING";
  return "STEADY";
}

export function passesShortHorizonTabTwoFilters(
  row: ShortHorizonStockRow,
  filters: ShortHorizonTabTwoFilters,
): boolean {
  const accelerationMatches = filters.acceleration === "ANY"
    || getShortHorizonMoveAccelerationState(row) === filters.acceleration;
  const strongFinishMatches = filters.minimumStrongFinishCount === 0
    || (
      row.recentStrongFinishSessionCount >= SHORT_HORIZON_STRONG_FINISH_LOOKBACK_SESSIONS
      && row.recentStrongFinishCount >= filters.minimumStrongFinishCount
    );
  const hasStructuralWeakness = row.lastThreeClosesDeclining === true
    && row.latestCloseBelowPreviousFiveSessionLow === true;

  return accelerationMatches && strongFinishMatches && !hasStructuralWeakness;
}

export function filterShortHorizonRowsByTabTwoFilters(
  rows: ShortHorizonStockRow[],
  filters: ShortHorizonTabTwoFilters,
): ShortHorizonStockRow[] {
  return rows.filter((row) => passesShortHorizonTabTwoFilters(row, filters));
}

export function filterShortHorizonRowsByBestAlignedFilters(rows: ShortHorizonStockRow[]): ShortHorizonStockRow[] {
  return rows.filter((row) =>
    getShortHorizonMoveAccelerationState(row) === "ACCELERATING"
    && row.recentStrongFinishSessionCount >= SHORT_HORIZON_STRONG_FINISH_LOOKBACK_SESSIONS
    && row.recentStrongFinishCount >= SHORT_HORIZON_BEST_ALIGNED_MIN_STRONG_FINISHES,
  );
}

export function buildShortHorizonBestAlignedRows(rows: ShortHorizonStockRow[]): ShortHorizonStockRow[] {
  return filterShortHorizonRowsByBestAlignedFilters(rows);
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

function calculateRecentVolumeActivityEvidence(
  days: WeeklyPriceWatchlistDay[],
): {
  volumeActivity: VolumeActivity | null;
  volumeActivityMultiple: number | null;
  volumeActivityDate: string | null;
  volumeActivityDirection: PriceDirection | null;
} {
  const recentStart = Math.max(0, days.length - SHORT_HORIZON_VOLUME_ACTIVITY_LOOKBACK_SESSIONS);
  const recentDays = days.slice(recentStart);
  const volumeEvents = recentDays.map((day, index) => {
    const dayIndex = recentStart + index;
    return {
      day,
      volumeMultiple: calculateVolumeMultiple(
        day,
        days.slice(Math.max(0, dayIndex - SHORT_HORIZON_VOLUME_ACTIVITY_VOLUME_BASELINE_SESSIONS), dayIndex),
      ),
    };
  }).filter((candidate) => candidate.volumeMultiple != null);
  const selectedEvent = [...volumeEvents]
    .reverse()
    .find((candidate) => (candidate.volumeMultiple ?? 0) >= SHORT_HORIZON_VOLUME_ACTIVITY_WATCH_MULTIPLE) ?? null;

  if (selectedEvent == null) {
    return {
      volumeActivity: "QUIET",
      volumeActivityMultiple: null,
      volumeActivityDate: null,
      volumeActivityDirection: null,
    };
  }

  return {
    volumeActivity: "WATCH",
    volumeActivityMultiple: selectedEvent.volumeMultiple,
    volumeActivityDate: selectedEvent.day.date,
    volumeActivityDirection: classifyPriceDirection(selectedEvent.day),
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
        deliveryPercentage: day.deliveryPercentage,
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
  const recentVolumeActivityEvidence = calculateRecentVolumeActivityEvidence(sortedDays);
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
    ...recentVolumeActivityEvidence,
    ...recentStrongFinishEvidence,
    recentMoveQuality: calculateRecentMoveQuality(sortedDays),
    recentDailyEvidence: calculateRecentDailyEvidence(sortedDays),
    fiftyTwoWeekHigh: row.momentum_evidence?.fifty_two_week_high ?? null,
    fiftyTwoWeekHighDate: row.momentum_evidence?.fifty_two_week_high_date ?? null,
    fiftyTwoWeekHighSessionsAgo: row.momentum_evidence?.fifty_two_week_high_sessions_ago ?? null,
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

function buildShortHorizonShortlistRowsFromCandidates(
  candidateRows: ShortHorizonStockRow[],
  originalStockCount: number,
): ShortHorizonStockRow[] {
  const shortlistSizePerRule = calculateShortHorizonShortlistSize(originalStockCount);
  const longWindowRows = rankShortHorizonRowsByLongWindow(candidateRows).slice(0, shortlistSizePerRule);
  const recentWindowRows = rankShortHorizonRowsByRecentWindow(candidateRows).slice(0, shortlistSizePerRule);
  const longWindowKeys = new Set(longWindowRows.map((row) => row.key));

  return [...longWindowRows, ...recentWindowRows.filter((row) => !longWindowKeys.has(row.key))]
    .slice(0, SHORT_HORIZON_MAX_SHORTLIST_COUNT * 2);
}

export function buildShortHorizonShortlistRows(rows: ShortHorizonStockRow[]): ShortHorizonStockRow[] {
  const eligibleRows = filterShortHorizonRowsByShortlistGuards(rows);
  return buildShortHorizonShortlistRowsFromCandidates(eligibleRows, rows.length);
}

export function buildShortHorizonTabTwoShortlistRows(
  rows: ShortHorizonStockRow[],
  filters: ShortHorizonTabTwoFilters,
): ShortHorizonStockRow[] {
  const currentCandidates = filterShortHorizonRowsByTabTwoFilters(rows, filters);
  const longWindowRows = rankShortHorizonRowsByLongWindow(currentCandidates);
  const recentWindowRows = rankShortHorizonRowsByRecentWindow(currentCandidates);
  const longWindowKeys = new Set(longWindowRows.map((row) => row.key));

  return [
    ...longWindowRows,
    ...recentWindowRows.filter((row) => !longWindowKeys.has(row.key)),
  ];
}
