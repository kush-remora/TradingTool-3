import { Tag, Tooltip } from "antd";
import type { AccumulationGoldenFlatNode } from "../types";

interface AccumulationShapeLabelProps {
  shape: string;
  goldenFlatNode: AccumulationGoldenFlatNode | null;
}

export function AccumulationShapeLabel({ shape, goldenFlatNode }: AccumulationShapeLabelProps) {
  const color = shape === "INVALID" ? "red" : shape === "UNCLASSIFIED" ? "gold" : "green";
  if (shape !== "FLAT_GOLDEN" || !goldenFlatNode) return <Tag color={color}>{shape}</Tag>;

  return <Tooltip title={`Strict flat ${goldenFlatNode.windowSessions}-session node: ${goldenFlatNode.startDate} → ${goldenFlatNode.endDate}`}><Tag color="gold">GOLDEN FLAT · {goldenFlatNode.windowSessions}D</Tag></Tooltip>;
}
