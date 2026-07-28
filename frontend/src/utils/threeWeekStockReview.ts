import type { DayDetail } from "../types";

export interface WeeklyPriceSummary {
  weekLabel: string;
  low: number;
  lowDate: string;
  high: number;
  highDate: string;
  rangePct: number;
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

export function buildWeeklyPriceSummaries(days: DayDetail[], weeksToDisplay: number = 3): WeeklyPriceSummary[] {
  return latestWeekGroups(days, weeksToDisplay).map(([weekStart, weekDays]) => {
    const lowDay = weekDays.reduce((lowest, day) => day.low < lowest.low ? day : lowest);
    const highDay = weekDays.reduce((highest, day) => day.high > highest.high ? day : highest);
    const range = highDay.high - lowDay.low;

    return {
      weekLabel: `Week of ${weekStart}`,
      low: lowDay.low,
      lowDate: lowDay.date,
      high: highDay.high,
      highDate: highDay.date,
      rangePct: lowDay.low > 0 ? (range / lowDay.low) * 100 : 0,
    };
  });
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

function latestWeekGroups(days: DayDetail[], weeksToDisplay: number): [string, DayDetail[]][] {
  const groupedDays = new Map<string, DayDetail[]>();
  for (const day of [...days].sort((left, right) => left.date.localeCompare(right.date))) {
    const weekStart = getWeekStart(day.date);
    groupedDays.set(weekStart, [...(groupedDays.get(weekStart) ?? []), day]);
  }

  return [...groupedDays.entries()].slice(-weeksToDisplay);
}

function percentageChange(currentValue: number, previousValue: number): number {
  return previousValue === 0 ? 0 : ((currentValue - previousValue) / previousValue) * 100;
}
