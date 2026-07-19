import { Tag, Tooltip } from "antd";
import type { AccumulationGoldenFlatNode, AccumulationLineFitMetrics } from "../types";

interface AccumulationShapeLabelProps {
  shape: string;
  goldenFlatNode: AccumulationGoldenFlatNode | null;
  lineFit: AccumulationLineFitMetrics | null;
}

export function AccumulationShapeLabel({ shape, goldenFlatNode, lineFit }: AccumulationShapeLabelProps) {
  const color = shape === "INVALID" ? "red" : shape === "UNCLASSIFIED" ? "gold" : "green";
  const strictFlat = shape === "FLAT" || shape === "FLAT_GOLDEN";
  const warning = strictFlat && lineFit?.ignoredOutlierDate ? " ⚠" : "";
  if (shape !== "FLAT_GOLDEN") return <Tag color={color}>{shape}{warning}</Tag>;

  const node = goldenFlatNode;
  const title = node
    ? `Strict flat ${node.windowSessions}-session node: ${node.startDate} → ${node.endDate}${warning ? ` · ignored shock: ${lineFit?.ignoredOutlierDate}` : ""}`
    : `Strict Golden Flat${warning ? ` · ignored shock: ${lineFit?.ignoredOutlierDate}` : ""}`;
  return <Tooltip title={title}><Tag color="gold">GOLDEN FLAT{node ? ` · ${node.windowSessions}D` : ""}{warning}</Tag></Tooltip>;
}
