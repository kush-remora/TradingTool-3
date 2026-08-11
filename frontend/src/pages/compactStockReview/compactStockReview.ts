import type { DayDetail, DeliveryDayDetail, MomentumParticipationEvent } from "../../types";
import type { WeeklyStructure } from "../../utils/threeWeekStockReview";

export interface CompactDailyRow extends DayDetail {
  deliveryPct: number | null;
  volumeVsPrior5dPct: number | null;
  volumeVsPrior10dPct: number | null;
  openToClosePct: number | null;
  roc9Pct: number | null;
  spreadPct: number | null;
  closePositionPct: number | null;
}

export interface CompactWeeklyRow {
  weekStart: string;
  endDate: string;
  lowDate: string;
  highDate: string;
  weeklyMovePct: number | null;
  lowHighPct: number | null;
  lowHighDirection: "LOW_FIRST" | "HIGH_FIRST" | "SAME_DAY";
  low: number;
  high: number;
  lowVolume: number;
  highVolume: number;
  lowVolumeRatio: number | null;
  highVolumeRatio: number | null;
  lowDeliveryPct: number | null;
  highDeliveryPct: number | null;
  lowDayPct: number | null;
  highDayPct: number | null;
  structure: WeeklyStructure | null;
  isCurrent: boolean;
}

type FlowDirection = "UP" | "DOWN" | "FLAT";
type FlowStructure = "HL" | "LL" | "HH" | "LH" | "FLAT" | "MIXED";

export interface CompactFlowWeek {
  label: string;
  weekStart: string;
  lowDate: string;
  low: number;
  high: number;
}

export interface CompactThreeWeekFlow {
  weeks: CompactFlowWeek[];
  lowSteps: FlowDirection[];
  highSteps: FlowDirection[];
  lowStructure: FlowStructure;
  highStructure: FlowStructure;
  floorAligned: boolean;
  floorLow: number;
  floorHigh: number;
}

export interface CompactDeliveryContext {
  currentPct: number | null;
  averagePct: number | null;
  ratio: number | null;
  state: "STABLE" | "ERRATIC" | null;
}

export interface CompactStory {
  stateLabel: string;
  headline: string;
  detail: string;
  nextCondition: string;
}

const percentageChange = (value: number, reference: number): number | null => (
  reference === 0 ? null : ((value - reference) / reference) * 100
);

const getWeekStart = (date: string): string => {
  const value = new Date(`${date}T00:00:00Z`);
  value.setUTCDate(value.getUTCDate() - ((value.getUTCDay() + 6) % 7));
  return value.toISOString().slice(0, 10);
};

const compareStructure = (
  previous: Pick<CompactWeeklyRow, "low" | "high">,
  current: Pick<CompactWeeklyRow, "low" | "high">,
): WeeklyStructure => {
  if (current.low > previous.low && current.high > previous.high) return "UP";
  if (current.low < previous.low && current.high < previous.high) return "DOWN";
  return "SIDEWAYS";
};

export const buildCompactDailyRows = (
  days: DayDetail[],
  deliveryDays: DeliveryDayDetail[],
): CompactDailyRow[] => {
  const chronologicalDays = [...days].sort((left, right) => left.date.localeCompare(right.date));
  const deliveryByDate = new Map(deliveryDays.map((day) => [day.date, day.delivery_percentage]));

  return chronologicalDays.map((day, index) => {
    const priorFiveDays = chronologicalDays.slice(Math.max(0, index - 5), index);
    const priorTenDays = chronologicalDays.slice(Math.max(0, index - 10), index);
    const priorFiveDayVolumeAverage = priorFiveDays.length === 5
      ? priorFiveDays.reduce((total, priorDay) => total + priorDay.volume, 0) / priorFiveDays.length
      : null;
    const priorTenDayVolumeAverage = priorTenDays.length === 10
      ? priorTenDays.reduce((total, priorDay) => total + priorDay.volume, 0) / priorTenDays.length
      : null;
    const spread = day.high - day.low;
    const deliveryPct = deliveryByDate.get(day.date) ?? null;

    return {
      ...day,
      deliveryPct,
      volumeVsPrior5dPct: priorFiveDayVolumeAverage != null && priorFiveDayVolumeAverage > 0
        ? (day.volume / priorFiveDayVolumeAverage) * 100
        : null,
      volumeVsPrior10dPct: priorTenDayVolumeAverage != null && priorTenDayVolumeAverage > 0
        ? (day.volume / priorTenDayVolumeAverage) * 100
        : null,
      openToClosePct: percentageChange(day.close, day.open),
      roc9Pct: index >= 9 ? percentageChange(day.close, chronologicalDays[index - 9].close) : null,
      spreadPct: percentageChange(day.high, day.low),
      closePositionPct: spread > 0 ? ((day.close - day.low) / spread) * 100 : null,
    };
  });
};

export const buildCompactDeliveryContext = (
  dailyRows: CompactDailyRow[],
  lookbackSessions: number = 10,
): CompactDeliveryContext => {
  const currentPct = dailyRows.at(-1)?.deliveryPct ?? null;
  const priorValues = dailyRows
    .slice(-(lookbackSessions + 1), -1)
    .map((row) => row.deliveryPct)
    .filter((value): value is number => value != null && Number.isFinite(value));
  if (currentPct == null || priorValues.length < lookbackSessions) {
    return { currentPct, averagePct: null, ratio: null, state: null };
  }

  const averagePct = priorValues.reduce((total, value) => total + value, 0) / priorValues.length;
  const variance = priorValues.reduce((total, value) => total + ((value - averagePct) ** 2), 0) / priorValues.length;
  const coefficientOfVariation = averagePct > 0 ? Math.sqrt(variance) / averagePct : 0;

  return {
    currentPct,
    averagePct,
    ratio: averagePct > 0 ? currentPct / averagePct : null,
    state: coefficientOfVariation >= 0.3 ? "ERRATIC" : "STABLE",
  };
};

export const buildCompactWeeklyRows = (
  dailyRows: CompactDailyRow[],
  weeksToDisplay: number = 4,
): CompactWeeklyRow[] => {
  const daysByWeek = new Map<string, CompactDailyRow[]>();
  for (const day of dailyRows) {
    const weekStart = getWeekStart(day.date);
    daysByWeek.set(weekStart, [...(daysByWeek.get(weekStart) ?? []), day]);
  }

  const weeks = [...daysByWeek.entries()].slice(-weeksToDisplay).map(([weekStart, days]) => {
    const firstDay = days[0];
    const latestDay = days.at(-1) ?? firstDay;
    const lowDay = days.reduce((lowest, day) => day.low < lowest.low ? day : lowest, days[0]);
    const highDay = days.reduce((highest, day) => day.high > highest.high ? day : highest, days[0]);
    const low = Math.min(...days.map((day) => day.low));
    const high = Math.max(...days.map((day) => day.high));

    return {
      weekStart,
      endDate: latestDay.date,
      lowDate: lowDay.date,
      highDate: highDay.date,
      weeklyMovePct: percentageChange(latestDay.close, firstDay.open),
      lowHighPct: percentageChange(high, low),
      lowHighDirection: lowDay.date < highDay.date ? "LOW_FIRST" : lowDay.date > highDay.date ? "HIGH_FIRST" : "SAME_DAY",
      low,
      high,
      lowVolume: lowDay.volume,
      highVolume: highDay.volume,
      lowVolumeRatio: lowDay.volumeVsPrior10dPct == null ? null : lowDay.volumeVsPrior10dPct / 100,
      highVolumeRatio: highDay.volumeVsPrior10dPct == null ? null : highDay.volumeVsPrior10dPct / 100,
      lowDeliveryPct: lowDay.deliveryPct,
      highDeliveryPct: highDay.deliveryPct,
      lowDayPct: lowDay.daily_change_pct,
      highDayPct: highDay.daily_change_pct,
      structure: null,
      isCurrent: false,
    } satisfies CompactWeeklyRow;
  });

  return weeks.map((week, index) => ({
    ...week,
    structure: index === 0 ? null : compareStructure(weeks[index - 1], week),
    isCurrent: index === weeks.length - 1,
  })).reverse();
};

const compareFlowValue = (previous: number, current: number): FlowDirection => {
  if (current > previous) return "UP";
  if (current < previous) return "DOWN";
  return "FLAT";
};

const summarizeFlow = (steps: FlowDirection[], rising: FlowStructure, falling: FlowStructure): FlowStructure => {
  if (steps.length === 0 || steps.every((step) => step === "FLAT")) return "FLAT";
  if (steps.every((step) => step === "UP")) return rising;
  if (steps.every((step) => step === "DOWN")) return falling;
  return "MIXED";
};

export const buildCompactThreeWeekFlow = (
  dailyRows: CompactDailyRow[],
  floorTolerancePct: number = 1,
): CompactThreeWeekFlow | null => {
  const weeklyRows = buildCompactWeeklyRows(dailyRows, 4);
  if (weeklyRows.length === 0) return null;

  const chronologicalWeeks = [...weeklyRows].reverse();
  const weeks = chronologicalWeeks.map((week, index) => ({
    label: index === chronologicalWeeks.length - 1 ? "WTD" : `W−${chronologicalWeeks.length - 1 - index}`,
    weekStart: week.weekStart,
    lowDate: week.lowDate,
    low: week.low,
    high: week.high,
  }));
  const lowSteps = weeks.slice(1).map((week, index) => compareFlowValue(weeks[index].low, week.low));
  const highSteps = weeks.slice(1).map((week, index) => compareFlowValue(weeks[index].high, week.high));
  const floorAligned = weeks.slice(1).every((week, index) => {
    const previousLow = weeks[index].low;
    return Math.abs(((week.low - previousLow) / previousLow) * 100) <= floorTolerancePct;
  });

  return {
    weeks,
    lowSteps,
    highSteps,
    lowStructure: summarizeFlow(lowSteps, "HL", "LL"),
    highStructure: summarizeFlow(highSteps, "HH", "LH"),
    floorAligned,
    floorLow: Math.min(...weeks.map((week) => week.low)),
    floorHigh: Math.max(...weeks.map((week) => week.low)),
  };
};

export const buildCompactStory = (
  latestDay: CompactDailyRow | null,
  latestWeek: CompactWeeklyRow | null,
): CompactStory => {
  if (!latestDay || !latestWeek) {
    return {
      stateLabel: "WAITING FOR DATA",
      headline: "Market story unavailable.",
      detail: "Select a stock with enough recent daily history.",
      nextCondition: "Wait for complete price and volume evidence.",
    };
  }

  const dayChange = latestDay.daily_change_pct ?? 0;
  const volumePct = latestDay.volumeVsPrior10dPct;
  const closePosition = latestDay.closePositionPct;
  if (dayChange < 0 && volumePct != null && volumePct < 70) {
    return {
      stateLabel: latestWeek.structure === "UP" ? "UPTREND · PULLBACK" : "SUPPLY CONTRACTING",
      headline: "Low-volume pullback.",
      detail: "Price weakened, but selling effort contracted versus the recent baseline. This supports consolidation, not a confirmed reversal.",
      nextCondition: `Hold above ${formatPrice(latestWeek.low)} and watch for demand near ${formatPrice(latestWeek.high)}.`,
    };
  }

  if (dayChange < 0 && volumePct != null && volumePct >= 130) {
    return {
      stateLabel: "SUPPLY EXPANDING",
      headline: "Decline arrived with elevated effort.",
      detail: "Price and volume moved together on the downside. Treat this as active supply until a narrower retest proves otherwise.",
      nextCondition: `Watch whether ${formatPrice(latestWeek.low)} holds on lower volume.`,
    };
  }

  if (dayChange > 0 && volumePct != null && volumePct >= 130 && (closePosition ?? 0) >= 70) {
    return {
      stateLabel: "DEMAND EXPANDING",
      headline: "Strong close with expanding volume.",
      detail: "Demand produced both effort and result. Confirmation still requires acceptance above the current weekly high.",
      nextCondition: `Look for acceptance above ${formatPrice(latestWeek.high)} without immediate rejection.`,
    };
  }

  return {
    stateLabel: latestWeek.structure === "UP" ? "STRUCTURE IMPROVING" : latestWeek.structure === "DOWN" ? "STRUCTURE WEAK" : "RANGE UNRESOLVED",
    headline: "No decisive effort-versus-result change.",
    detail: "Today did not materially change the recent price-volume story. Keep the prior thesis until new evidence appears.",
    nextCondition: `Watch the active range: ${formatPrice(latestWeek.low)}–${formatPrice(latestWeek.high)}.`,
  };
};

export const participationEventDates = (events: MomentumParticipationEvent[]): Set<string> => (
  new Set(events.map((event) => event.event_date))
);

export const formatPrice = (value: number | null): string => (
  value == null ? "—" : `₹${value.toLocaleString("en-IN", { maximumFractionDigits: 2 })}`
);

export const formatQuantity = (value: number | null): string => {
  if (value == null) return "—";
  if (value >= 10_000_000) return `${(value / 10_000_000).toFixed(2)} Cr`;
  if (value >= 100_000) return `${(value / 100_000).toFixed(2)} L`;
  if (value >= 1_000) return `${(value / 1_000).toFixed(1)} K`;
  return value.toLocaleString("en-IN");
};

export const formatSignedPercent = (value: number | null, digits: number = 2): string => (
  value == null ? "—" : `${value > 0 ? "+" : ""}${value.toFixed(digits)}%`
);

export const formatShortDate = (date: string): string => (
  new Intl.DateTimeFormat("en-IN", { day: "2-digit", month: "short", timeZone: "UTC" })
    .format(new Date(`${date}T00:00:00Z`))
);

export const formatWeekdayDate = (date: string): string => (
  new Intl.DateTimeFormat("en-IN", { weekday: "short", day: "2-digit", month: "short", timeZone: "UTC" })
    .format(new Date(`${date}T00:00:00Z`))
);
