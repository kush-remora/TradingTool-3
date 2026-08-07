import type { ReactNode } from "react";
import type { WeeklyStructure } from "../utils/threeWeekStockReview";

interface WeeklyStructureIndicatorProps {
  structure: WeeklyStructure | null;
}

const DISPLAY: Record<WeeklyStructure, { text: string; label: string; color: string }> = {
  UP: { text: "↑ Up", label: "Uptrend: higher high and higher low", color: "#389e0d" },
  DOWN: { text: "↓ Down", label: "Downtrend: lower high and lower low", color: "#cf1322" },
  SIDEWAYS: { text: "→ Sideways", label: "Sideways: no clear higher-high and higher-low or lower-high and lower-low structure", color: "#8c8c8c" },
};

export function WeeklyStructureIndicator({ structure }: WeeklyStructureIndicatorProps): ReactNode {
  if (structure === null) return "—";

  const display = DISPLAY[structure];
  return <span role="img" aria-label={display.label} style={{ color: display.color, fontWeight: 600 }}>{display.text}</span>;
}
