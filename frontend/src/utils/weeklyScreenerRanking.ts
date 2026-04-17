export interface RankMetricWeights {
  vcpTightness: number;
  volumeSignature: number;
  strikeRate: number;
  baselineDistance: number;
  reboundConsistency: number;
  expectedSwing: number;
}

export interface RankMetricValues {
  symbol: string;
  vcpTightness: number | null;
  volumeSignature: number | null;
  strikeRate: number | null;
  baselineDistance: number | null;
  reboundConsistency: number | null;
  expectedSwing: number | null;
}

export interface RankScoreBySymbol {
  symbol: string;
  score: number;
}

const DEFAULT_NEUTRAL_SCORE = 50;

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

function normalizeValue(value: number, min: number, max: number, inverse: boolean): number {
  if (max <= min) return DEFAULT_NEUTRAL_SCORE;
  const ratio = (value - min) / (max - min);
  const normalized = inverse ? (1 - ratio) * 100 : ratio * 100;
  return clamp(normalized, 0, 100);
}

function metricRange(values: Array<number | null>): { min: number; max: number } | null {
  const present = values.filter((value): value is number => value !== null);
  if (present.length === 0) return null;
  return {
    min: Math.min(...present),
    max: Math.max(...present),
  };
}

export function normalizeMetricSeries(values: Array<number | null>, inverse: boolean): Array<number | null> {
  const range = metricRange(values);
  if (!range) return values.map(() => null);
  return values.map((value) => {
    if (value === null) return null;
    return normalizeValue(value, range.min, range.max, inverse);
  });
}

export function computeCustomRankScores(
  rows: RankMetricValues[],
  weights: RankMetricWeights,
): RankScoreBySymbol[] {
  const vcp = normalizeMetricSeries(rows.map((row) => row.vcpTightness), true);
  const volume = normalizeMetricSeries(rows.map((row) => row.volumeSignature), false);
  const strike = normalizeMetricSeries(rows.map((row) => row.strikeRate), false);
  const baseline = normalizeMetricSeries(rows.map((row) => row.baselineDistance), true);
  const rebound = normalizeMetricSeries(rows.map((row) => row.reboundConsistency), false);
  const swing = normalizeMetricSeries(rows.map((row) => row.expectedSwing), false);

  const totalWeight =
    weights.vcpTightness +
    weights.volumeSignature +
    weights.strikeRate +
    weights.baselineDistance +
    weights.reboundConsistency +
    weights.expectedSwing;

  const safeTotalWeight = totalWeight > 0 ? totalWeight : 1;

  return rows.map((row, index) => {
    const score = (
      (vcp[index] ?? DEFAULT_NEUTRAL_SCORE) * weights.vcpTightness +
      (volume[index] ?? DEFAULT_NEUTRAL_SCORE) * weights.volumeSignature +
      (strike[index] ?? DEFAULT_NEUTRAL_SCORE) * weights.strikeRate +
      (baseline[index] ?? DEFAULT_NEUTRAL_SCORE) * weights.baselineDistance +
      (rebound[index] ?? DEFAULT_NEUTRAL_SCORE) * weights.reboundConsistency +
      (swing[index] ?? DEFAULT_NEUTRAL_SCORE) * weights.expectedSwing
    ) / safeTotalWeight;

    return {
      symbol: row.symbol,
      score: Math.round(score * 100) / 100,
    };
  });
}

export const DEFAULT_RANK_WEIGHTS: RankMetricWeights = {
  vcpTightness: 20,
  volumeSignature: 15,
  strikeRate: 20,
  baselineDistance: 15,
  reboundConsistency: 15,
  expectedSwing: 15,
};
