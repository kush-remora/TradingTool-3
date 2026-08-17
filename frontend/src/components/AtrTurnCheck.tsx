import type { ReactNode } from "react";
import type { AdaptiveBreakoutRawStep, AdaptiveBreakoutScanResponse } from "../types";

interface AtrTurnCalculation {
  direction: "up" | "down";
  movement: number;
  movementMultiple: number;
  referencePrice: number;
  thresholdMultiple: number;
  thresholdPrice: number;
}

interface AtrTurnCheckProps {
  step: AdaptiveBreakoutRawStep;
  config: AdaptiveBreakoutScanResponse["config"];
}

function formatTurnPrice(value: number): string {
  return `₹${value.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function calculateAtrTurn({ step, config }: AtrTurnCheckProps): AtrTurnCalculation | null {
  if (step.decision === "FLOOR_CONFIRMED") {
    if (!Number.isFinite(step.candidateFloorAtr) || step.candidateFloorAtr <= 0) return null;
    const movement = step.close - step.candidateFloor;
    return {
      direction: "up",
      movement,
      movementMultiple: movement / step.candidateFloorAtr,
      referencePrice: step.candidateFloor,
      thresholdMultiple: config.floorReboundAtrMultiple,
      thresholdPrice: step.candidateFloorAtr * config.floorReboundAtrMultiple,
    };
  }
  if (step.decision === "CEILING_CONFIRMED" || step.decision === "COMPACT_CEILING_CANDIDATE") {
    if (!Number.isFinite(step.candidatePeakAtr) || step.candidatePeakAtr <= 0) return null;
    const movement = step.candidatePeak - step.close;
    const isCompact = step.decision === "COMPACT_CEILING_CANDIDATE" || step.ceilingType === "COMPACT_RANGE";
    return {
      direction: "down",
      movement,
      movementMultiple: movement / step.candidatePeakAtr,
      referencePrice: step.candidatePeak,
      thresholdMultiple: isCompact ? config.compactPeakRejectionAtrMultiple : config.peakRejectionAtrMultiple,
      thresholdPrice: step.candidatePeakAtr * (isCompact ? config.compactPeakRejectionAtrMultiple : config.peakRejectionAtrMultiple),
    };
  }
  return null;
}

export function AtrTurnCheck({ step, config }: AtrTurnCheckProps): ReactNode {
  const calculation = calculateAtrTurn({ step, config });
  if (calculation == null) return null;
  const isUp = calculation.direction === "up";
  const isCompactCandidate = step.decision === "COMPACT_CEILING_CANDIDATE";
  const ruleMeaning = isUp
    ? "floor confirmed"
    : isCompactCandidate
      ? `compact candidate; ${step.compactCeilingConfirmationCount ?? 0} of ${config.compactCeilingConfirmationSessions} contained sessions`
      : `ceiling confirmed; new down leg starts at ${formatTurnPrice(step.candidateFloor)}`;
  const accessibleLabel = [
    `${isUp ? "Up" : "Down"} move ${formatTurnPrice(calculation.movement)}`,
    `${calculation.movementMultiple.toFixed(2)} ATR`,
    `required ${calculation.thresholdMultiple.toFixed(2)} ATR or ${formatTurnPrice(calculation.thresholdPrice)}`,
    `${ruleMeaning}.`,
  ].join(", ");
  return (
    <div className={`adaptive-breakout-atr-turn ${calculation.direction}`} aria-label={accessibleLabel}>
      <span>
        <strong>{isUp ? "↑" : "↓"} {calculation.movementMultiple.toFixed(2)} ATR</strong>
        <b>≥ {calculation.thresholdMultiple.toFixed(2)} ✓</b>
      </span>
      <small>
        {isUp ? "close" : "peak"} {formatTurnPrice(isUp ? step.close : calculation.referencePrice)} − {isUp ? "floor" : "close"} {formatTurnPrice(isUp ? calculation.referencePrice : step.close)} = {formatTurnPrice(calculation.movement)}
        {isUp
          ? " · floor confirmed"
          : isCompactCandidate
            ? ` · candidate · contained ${step.compactCeilingConfirmationCount ?? 0}/${config.compactCeilingConfirmationSessions}`
            : ` · ceiling confirmed · down leg → ${formatTurnPrice(step.candidateFloor)}`}
      </small>
    </div>
  );
}
