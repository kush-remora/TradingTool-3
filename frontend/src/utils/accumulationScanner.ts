import type { WeeklyPriceWatchlistDay, WeeklyPriceWatchlistRow } from "../types";

export const ACCUMULATION_COUNT_LOOKBACK_SESSIONS = 30;
export const ACCUMULATION_HEATMAP_LOOKBACK_SESSIONS = 20;
export const ACCUMULATION_VOLUME_BASELINE_SESSIONS = 10;
export const ACCUMULATION_CLOSE_LOCATION_THRESHOLD_PCT = 70;
export const ACCUMULATION_QUIET_MOVE_THRESHOLD_PCT = 1;
export const ACCUMULATION_FILTER_ONE_MIN_BUYING_INTEREST_DAYS = 10;
export const ACCUMULATION_FILTER_ONE_MIN_QUIET_MOVE_DAYS = 8;
export const ACCUMULATION_FILTER_ONE_MIN_VOLUME_DRY_UP_DAYS = 6;
export const ACCUMULATION_FILTER_TWO_LOOKBACK_SESSIONS = 5;
export const ACCUMULATION_FILTER_TWO_MIN_BUYING_INTEREST_DAYS = 3;
export const ACCUMULATION_FILTER_TWO_MIN_GREEN_CLOSE_DAYS = 3;
export const ACCUMULATION_FILTER_TWO_MIN_FIVE_DAY_MOVE_PCT = 5;

export interface AccumulationHeatmapDay {
  date: string;
  closeChangePct: number | null;
  closeLocationPct: number | null;
  volume: number;
  averageVolume10: number | null;
  buyingInterest: boolean | null;
  greenClose: boolean | null;
  quietMove: boolean | null;
  volumeDryUp: boolean | null;
}

export interface AccumulationStockRow {
  key: string;
  symbol: string;
  companyName: string;
  instrumentToken: number;
  latestDate: string | null;
  latestClose: number | null;
  latestFiveDayMovePct: number | null;
  latestTwentyDayMovePct: number | null;
  countWindowSessions: number;
  heatmapWindowSessions: number;
  buyingInterestCount: number;
  greenCloseCount: number;
  quietMoveCount: number;
  volumeDryUpCount: number;
  volumeEligibleSessionCount: number;
  heatmap: AccumulationHeatmapDay[];
}

function sortDays(days: WeeklyPriceWatchlistDay[]): WeeklyPriceWatchlistDay[] {
  return [...days].sort((left, right) => left.date.localeCompare(right.date));
}

function calculateCloseChangePct(day: WeeklyPriceWatchlistDay, previousDay: WeeklyPriceWatchlistDay | undefined): number | null {
  if (previousDay == null || previousDay.close <= 0) return null;
  return ((day.close - previousDay.close) / previousDay.close) * 100;
}

function calculatePeriodMovePct(
  latestDay: WeeklyPriceWatchlistDay | undefined,
  startingDay: WeeklyPriceWatchlistDay | undefined,
): number | null {
  if (latestDay == null || startingDay == null || startingDay.close <= 0) return null;
  return ((latestDay.close - startingDay.close) / startingDay.close) * 100;
}

function calculateCloseLocationPct(day: WeeklyPriceWatchlistDay): number | null {
  const range = day.high - day.low;
  if (range <= 0) return null;
  return ((day.close - day.low) / range) * 100;
}

function calculateAverageVolume(
  days: WeeklyPriceWatchlistDay[],
  dayIndex: number,
): number | null {
  const baselineDays = days.slice(
    Math.max(0, dayIndex - ACCUMULATION_VOLUME_BASELINE_SESSIONS),
    dayIndex,
  );
  if (baselineDays.length < ACCUMULATION_VOLUME_BASELINE_SESSIONS) return null;
  return baselineDays.reduce((total, day) => total + day.volume, 0) / baselineDays.length;
}

function buildHeatmapDay(
  days: WeeklyPriceWatchlistDay[],
  dayIndex: number,
): AccumulationHeatmapDay {
  const day = days[dayIndex];
  const closeChangePct = calculateCloseChangePct(day, days[dayIndex - 1]);
  const closeLocationPct = calculateCloseLocationPct(day);
  const averageVolume10 = calculateAverageVolume(days, dayIndex);

  return {
    date: day.date,
    closeChangePct,
    closeLocationPct,
    volume: day.volume,
    averageVolume10,
    buyingInterest: closeLocationPct == null
      ? null
      : closeLocationPct >= ACCUMULATION_CLOSE_LOCATION_THRESHOLD_PCT,
    greenClose: closeChangePct == null ? null : closeChangePct > 0,
    quietMove: closeChangePct == null
      ? null
      : Math.abs(closeChangePct) < ACCUMULATION_QUIET_MOVE_THRESHOLD_PCT,
    volumeDryUp: averageVolume10 == null ? null : day.volume < averageVolume10,
  };
}

function countMatches(days: AccumulationHeatmapDay[], key: keyof Pick<
  AccumulationHeatmapDay,
  "buyingInterest" | "greenClose" | "quietMove" | "volumeDryUp"
>): number {
  return days.filter((day) => day[key] === true).length;
}

export function buildAccumulationStockRow(row: WeeklyPriceWatchlistRow): AccumulationStockRow {
  const days = sortDays(row.days);
  const countDays = days.slice(-ACCUMULATION_COUNT_LOOKBACK_SESSIONS);
  const countStartIndex = Math.max(0, days.length - countDays.length);
  const countEvidence = countDays.map((_, index) => buildHeatmapDay(days, countStartIndex + index));
  const heatmap = countEvidence.slice(-ACCUMULATION_HEATMAP_LOOKBACK_SESSIONS);

  return {
    key: `${row.instrumentToken}-${row.symbol}`,
    symbol: row.symbol,
    companyName: row.companyName,
    instrumentToken: row.instrumentToken,
    latestDate: days.at(-1)?.date ?? null,
    latestClose: days.at(-1)?.close ?? null,
    latestFiveDayMovePct: calculatePeriodMovePct(days.at(-1), days.at(-1 - ACCUMULATION_FILTER_TWO_LOOKBACK_SESSIONS)),
    latestTwentyDayMovePct: calculatePeriodMovePct(days.at(-1), days.at(-1 - ACCUMULATION_HEATMAP_LOOKBACK_SESSIONS)),
    countWindowSessions: countEvidence.length,
    heatmapWindowSessions: heatmap.length,
    buyingInterestCount: countMatches(countEvidence, "buyingInterest"),
    greenCloseCount: countMatches(countEvidence, "greenClose"),
    quietMoveCount: countMatches(countEvidence, "quietMove"),
    volumeDryUpCount: countMatches(countEvidence, "volumeDryUp"),
    volumeEligibleSessionCount: countEvidence.filter((day) => day.volumeDryUp != null).length,
    heatmap,
  };
}

export function buildAccumulationRows(rows: WeeklyPriceWatchlistRow[]): AccumulationStockRow[] {
  return rows
    .map(buildAccumulationStockRow)
    .sort((left, right) => left.symbol.localeCompare(right.symbol));
}

export function buildAccumulationFilterOneRows(rows: AccumulationStockRow[]): AccumulationStockRow[] {
  return rows.filter((row) => (
    row.countWindowSessions === ACCUMULATION_COUNT_LOOKBACK_SESSIONS
    && row.buyingInterestCount >= ACCUMULATION_FILTER_ONE_MIN_BUYING_INTEREST_DAYS
    && row.quietMoveCount >= ACCUMULATION_FILTER_ONE_MIN_QUIET_MOVE_DAYS
    && row.volumeDryUpCount >= ACCUMULATION_FILTER_ONE_MIN_VOLUME_DRY_UP_DAYS
  ));
}

export function countRecentBuyingInterestDays(row: AccumulationStockRow): number {
  return row.heatmap
    .slice(-ACCUMULATION_FILTER_TWO_LOOKBACK_SESSIONS)
    .filter((day) => day.buyingInterest === true)
    .length;
}

export function countRecentGreenCloseDays(row: AccumulationStockRow): number {
  return row.heatmap
    .slice(-ACCUMULATION_FILTER_TWO_LOOKBACK_SESSIONS)
    .filter((day) => day.greenClose === true)
    .length;
}

export function buildAccumulationFilterTwoRows(rows: AccumulationStockRow[]): AccumulationStockRow[] {
  return rows.filter((row) => (
    row.heatmap.length >= ACCUMULATION_FILTER_TWO_LOOKBACK_SESSIONS
    && countRecentBuyingInterestDays(row) >= ACCUMULATION_FILTER_TWO_MIN_BUYING_INTEREST_DAYS
    && countRecentGreenCloseDays(row) >= ACCUMULATION_FILTER_TWO_MIN_GREEN_CLOSE_DAYS
    && row.latestFiveDayMovePct != null
    && row.latestFiveDayMovePct >= ACCUMULATION_FILTER_TWO_MIN_FIVE_DAY_MOVE_PCT
  ));
}
