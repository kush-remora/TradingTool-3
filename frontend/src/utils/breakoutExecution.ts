import type { BreakoutDayQualityResponse, BreakoutQualityDecision } from "../types";

export interface NextMorningExecutionResult {
  decision: Exclude<BreakoutQualityDecision, "CONTEXT_ONLY">;
  summary: string;
  openingExtensionAtr: number | null;
  roomToObstacleAtr: number | null;
  considerUpToPrice: number | null;
  doNotChaseAbovePrice: number | null;
}

export function evaluateNextMorningOpen(
  report: BreakoutDayQualityResponse,
  nextOpen: number | null,
): NextMorningExecutionResult | null {
  if (nextOpen == null || nextOpen <= 0) return null;
  const line = report.breakoutLine;
  const atr = report.atr;
  if (line == null || atr <= 0) {
    return result("WAIT", "Cannot assess the open because the breakout line or ATR is unavailable.", null, null, null, null);
  }

  const openingExtensionAtr = (nextOpen - line) / atr;
  const obstacle = report.chartContext.nextObstaclePrice;
  const roomToObstacleAtr = obstacle == null ? null : (obstacle - nextOpen) / atr;
  const considerUpToPrice = line + 0.25 * atr;
  const doNotChaseAbovePrice = line + 0.5 * atr;

  if (report.structureDecision !== "FRESH_BREAKOUT") {
    return result("REJECT", "Do not treat this as a next-morning entry: the selected day was not a fresh breakout.", openingExtensionAtr, roomToObstacleAtr, considerUpToPrice, doNotChaseAbovePrice);
  }
  if (report.overallDecision === "REJECT") {
    return result("REJECT", "Do not buy: the completed breakout candle failed a quality check.", openingExtensionAtr, roomToObstacleAtr, considerUpToPrice, doNotChaseAbovePrice);
  }
  if (report.chartContext.overallDecision === "REJECT") {
    return result("REJECT", `Do not buy: ${report.chartContext.decisionSummary}`, openingExtensionAtr, roomToObstacleAtr, considerUpToPrice, doNotChaseAbovePrice);
  }
  if (nextOpen < line) {
    return result("REJECT", "Do not buy at the open: price opened back below the breakout line.", openingExtensionAtr, roomToObstacleAtr, considerUpToPrice, doNotChaseAbovePrice);
  }
  if (roomToObstacleAtr != null && roomToObstacleAtr < 1.0) {
    return result("REJECT", "Do not buy: less than 1 ATR remains before the next overhead obstacle.", openingExtensionAtr, roomToObstacleAtr, considerUpToPrice, doNotChaseAbovePrice);
  }
  if (openingExtensionAtr > 0.5) {
    return result("REJECT", "Do not chase: the opening price is more than 0.50 ATR above the breakout line.", openingExtensionAtr, roomToObstacleAtr, considerUpToPrice, doNotChaseAbovePrice);
  }
  if (openingExtensionAtr > 0.25) {
    return result("WAIT", "Wait for a controlled pullback or use a limit; the open is 0.25–0.50 ATR above the line.", openingExtensionAtr, roomToObstacleAtr, considerUpToPrice, doNotChaseAbovePrice);
  }
  if (report.overallDecision !== "PASS" || report.chartContext.overallDecision !== "PASS") {
    return result("WAIT", "The opening price is acceptable, but the breakout-day or chart-context checks are still mixed.", openingExtensionAtr, roomToObstacleAtr, considerUpToPrice, doNotChaseAbovePrice);
  }
  return result("PASS", "Opening price is in the consideration zone. Complete the stop, position-size, news, and market checks before buying.", openingExtensionAtr, roomToObstacleAtr, considerUpToPrice, doNotChaseAbovePrice);
}

function result(
  decision: Exclude<BreakoutQualityDecision, "CONTEXT_ONLY">,
  summary: string,
  openingExtensionAtr: number | null,
  roomToObstacleAtr: number | null,
  considerUpToPrice: number | null,
  doNotChaseAbovePrice: number | null,
): NextMorningExecutionResult {
  return { decision, summary, openingExtensionAtr, roomToObstacleAtr, considerUpToPrice, doNotChaseAbovePrice };
}
