import type { WeeklyPriceWatchlistDay } from "../types";

export const BASE_CANDIDATE_SESSION_COUNT = 10;
export const BASE_LOOKBACK_SESSION_COUNT = 20;
export const BASE_LOW_TOLERANCE = 0.01;

export interface BaseConsolidationObservation {
  key: string;
  date: string;
  low: number;
  hitCount: number | null;
}

/**
 * Builds the ten newest candidate lows and compares each with its previous
 * twenty completed sessions. A null count makes incomplete history visible
 * during validation instead of silently counting fewer than twenty sessions.
 */
export function buildBaseConsolidationObservations(
  days: WeeklyPriceWatchlistDay[],
): BaseConsolidationObservation[] {
  const sortedDays = [...days].sort((left, right) => left.date.localeCompare(right.date));
  const analysisDays = sortedDays.slice(-(BASE_CANDIDATE_SESSION_COUNT + BASE_LOOKBACK_SESSION_COUNT));
  const firstCandidateIndex = Math.max(0, analysisDays.length - BASE_CANDIDATE_SESSION_COUNT);

  return analysisDays
    .slice(firstCandidateIndex)
    .reverse()
    .map((candidate, reverseIndex) => {
      const candidateIndex = analysisDays.length - 1 - reverseIndex;
      const historyStart = Math.max(0, candidateIndex - BASE_LOOKBACK_SESSION_COUNT);
      const historyDays = analysisDays.slice(historyStart, candidateIndex);
      const lowerBound = candidate.low * (1 - BASE_LOW_TOLERANCE);
      const upperBound = candidate.low * (1 + BASE_LOW_TOLERANCE);
      const hitCount = historyDays.length === BASE_LOOKBACK_SESSION_COUNT
        ? historyDays.filter((day) => day.low >= lowerBound && day.low <= upperBound).length
        : null;

      return {
        key: candidate.date,
        date: candidate.date,
        low: candidate.low,
        hitCount,
      };
    });
}
