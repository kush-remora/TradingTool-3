import type { DayDetail } from "../types";

export interface WeeklyPriceDay extends DayDetail {
  deliveryPercentage?: number | null;
}

export type WeeklyStructure = "UP" | "DOWN" | "SIDEWAYS";

export interface WeeklyPriceSummary {
  weekLabel: string;
  low: number;
  lowDate: string;
  high: number;
  highDate: string;
  rangePct: number;
  lowDayHasHigherVolumeAndDelivery: boolean;
  weekOnWeekStructure: WeeklyStructure | null;
}

export interface WeeklyLowAlignment {
  earlierWeekLabel: string;
  earlierLow: number;
  laterWeekLabel: string;
  laterLow: number;
  differencePct: number;
}

export interface CurrentWeekLowAlignment {
  earlierWeekLabel: string;
  earlierWeekLow: number;
  earlierWeekLowDate: string;
  previousWeekLabel: string;
  previousWeekLow: number;
  previousWeekLowDate: string;
  currentWeekLabel: string;
  currentWeekLow: number;
  currentWeekLowDate: string;
  currentWeekDifferencePct: number;
  currentVsPreviousWeekPct: number;
  previousVsEarlierWeekPct: number;
}

export interface WeeklyPriceTimeline {
  baseOpen: number;
  days: WeeklyPriceTimelineDay[];
}

export interface WeeklyPriceTimelineDay extends DayDetail {
  dailyMovePct: number;
  accumulatedWeeklyPct: number;
  isWeekLow: boolean;
  isWeekHigh: boolean;
}

function getWeekStart(date: string): string {
  const value = new Date(`${date}T00:00:00Z`);
  value.setUTCDate(value.getUTCDate() - ((value.getUTCDay() + 6) % 7));
  return value.toISOString().slice(0, 10);
}

export function buildWeeklyPriceSummaries(days: WeeklyPriceDay[], weeksToDisplay: number = 3): WeeklyPriceSummary[] {
  const summaries = latestWeekGroups(days, weeksToDisplay).map(([weekStart, weekDays]) => {
    const lowDay = weekDays.reduce((lowest, day) => day.low < lowest.low ? day : lowest);
    const highDay = weekDays.reduce((highest, day) => day.high > highest.high ? day : highest);
    const range = highDay.high - lowDay.low;
    const lowDayHasHigherVolumeAndDelivery = lowDay.volume > highDay.volume
      && lowDay.deliveryPercentage != null
      && highDay.deliveryPercentage != null
      && lowDay.deliveryPercentage > highDay.deliveryPercentage;

    return {
      weekLabel: `Week of ${weekStart}`,
      low: lowDay.low,
      lowDate: lowDay.date,
      high: highDay.high,
      highDate: highDay.date,
      rangePct: lowDay.low > 0 ? (range / lowDay.low) * 100 : 0,
      lowDayHasHigherVolumeAndDelivery,
      weekOnWeekStructure: null,
    };
  });

  return summaries.map((summary, index) => ({
    ...summary,
    weekOnWeekStructure: index === 0 ? null : compareWeeklyStructure(summaries[index - 1], summary),
  }));
}

export function findConsecutiveWeeklyLowAlignments(
  summaries: WeeklyPriceSummary[],
  maximumDifferencePct: number = 1,
): WeeklyLowAlignment[] {
  return summaries.slice(1).flatMap((laterWeek, index) => {
    const earlierWeek = summaries[index];
    if (!Number.isFinite(earlierWeek.low) || !Number.isFinite(laterWeek.low) || earlierWeek.low <= 0) return [];

    const differencePct = Math.abs(((laterWeek.low - earlierWeek.low) / earlierWeek.low) * 100);
    if (differencePct > maximumDifferencePct) return [];

    return [{
      earlierWeekLabel: earlierWeek.weekLabel,
      earlierLow: earlierWeek.low,
      laterWeekLabel: laterWeek.weekLabel,
      laterLow: laterWeek.low,
      differencePct,
    }];
  });
}

export function findCurrentWeekLowAlignment(
  days: WeeklyPriceDay[],
  maximumDifferencePct: number = 1,
): CurrentWeekLowAlignment | null {
  const chronologicalDays = [...days].sort((left, right) => left.date.localeCompare(right.date));
  const summaries = buildWeeklyPriceSummaries(chronologicalDays, 3);
  if (summaries.length < 3) return null;

  const earlierWeek = summaries[0];
  const previousWeek = summaries[1];
  const currentWeek = summaries[2];
  const hasValidWeeklyLows = Number.isFinite(previousWeek.low)
    && previousWeek.low > 0
    && Number.isFinite(currentWeek.low)
    && Number.isFinite(earlierWeek.low)
    && earlierWeek.low > 0;
  if (!hasValidWeeklyLows) return null;

  const currentVsPreviousWeekPct = ((currentWeek.low - previousWeek.low) / previousWeek.low) * 100;
  if (Math.abs(currentVsPreviousWeekPct) > maximumDifferencePct) return null;

  return {
    earlierWeekLabel: earlierWeek.weekLabel,
    earlierWeekLow: earlierWeek.low,
    earlierWeekLowDate: earlierWeek.lowDate,
    previousWeekLabel: previousWeek.weekLabel,
    previousWeekLow: previousWeek.low,
    previousWeekLowDate: previousWeek.lowDate,
    currentWeekLabel: currentWeek.weekLabel,
    currentWeekLow: currentWeek.low,
    currentWeekLowDate: currentWeek.lowDate,
    currentWeekDifferencePct: Math.abs(currentVsPreviousWeekPct),
    currentVsPreviousWeekPct,
    previousVsEarlierWeekPct: ((previousWeek.low - earlierWeek.low) / earlierWeek.low) * 100,
  };
}

export function getWeekStartLabel(date: string): string {
  return `Week of ${getWeekStart(date)}`;
}

export function buildWeeklyPriceTimelines(days: DayDetail[], weeksToDisplay: number = 3): WeeklyPriceTimeline[] {
  return latestWeekGroups(days, weeksToDisplay).map(([, weekDays]) => {
    const baseOpen = weekDays[0].open;
    const weekLow = Math.min(...weekDays.map((day) => day.low));
    const weekHigh = Math.max(...weekDays.map((day) => day.high));

    return {
      baseOpen,
      days: weekDays.map((day) => ({
        ...day,
        dailyMovePct: percentageChange(day.close, day.open),
        accumulatedWeeklyPct: percentageChange(day.close, baseOpen),
        isWeekLow: day.low === weekLow,
        isWeekHigh: day.high === weekHigh,
      })),
    };
  });
}

function latestWeekGroups(days: WeeklyPriceDay[], weeksToDisplay: number): [string, WeeklyPriceDay[]][] {
  const groupedDays = new Map<string, WeeklyPriceDay[]>();
  for (const day of [...days].sort((left, right) => left.date.localeCompare(right.date))) {
    const weekStart = getWeekStart(day.date);
    groupedDays.set(weekStart, [...(groupedDays.get(weekStart) ?? []), day]);
  }

  return [...groupedDays.entries()].slice(-weeksToDisplay);
}

function compareWeeklyStructure(previousWeek: WeeklyPriceSummary, currentWeek: WeeklyPriceSummary): WeeklyStructure {
  if (currentWeek.low > previousWeek.low && currentWeek.high > previousWeek.high) {
    return "UP";
  }
  if (currentWeek.low < previousWeek.low && currentWeek.high < previousWeek.high) {
    return "DOWN";
  }
  return "SIDEWAYS";
}

function percentageChange(currentValue: number, previousValue: number): number {
  return previousValue === 0 ? 0 : ((currentValue - previousValue) / previousValue) * 100;
}
