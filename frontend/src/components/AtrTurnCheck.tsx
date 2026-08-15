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
  if (step.atr <= 0) return null;
  if (step.decision === "FLOOR_CONFIRMED") {
    const movement = step.close - step.candidateFloor;
    return {
      direction: "up",
      movement,
      movementMultiple: movement / step.atr,
      referencePrice: step.candidateFloor,
      thresholdMultiple: config.floorReboundAtrMultiple,
      thresholdPrice: step.atr * config.floorReboundAtrMultiple,
    };
  }
  if (step.decision === "CEILING_CANDIDATE") {
    const movement = step.candidatePeak - step.close;
    return {
      direction: "down",
      movement,
      movementMultiple: movement / step.atr,
      referencePrice: step.candidatePeak,
      thresholdMultiple: config.peakRejectionAtrMultiple,
      thresholdPrice: step.atr * config.peakRejectionAtrMultiple,
    };
  }
  return null;
}

export function AtrTurnCheck({ step, config }: AtrTurnCheckProps): ReactNode {
  const calculation = calculateAtrTurn({ step, config });
  if (calculation == null) return null;
  const isUp = calculation.direction === "up";
  const ruleMeaning = isUp
    ? "floor confirmed"
    : `new down leg; candidate low starts ${formatTurnPrice(step.candidateFloor)}`;
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
        {isUp ? " · floor confirmed" : ` · candidate low → ${formatTurnPrice(step.candidateFloor)}`}
      </small>
    </div>
  );
}
