import { Tooltip, Typography } from "antd";
import type { AccumulationShapeChunk } from "../types";

interface AccumulationShapePathProps {
  chunks?: AccumulationShapeChunk[];
}

const labels: Record<string, string> = {
  FLAT: "flat",
  FLAT_GOLDEN: "golden flat",
  CUP: "cup",
  DOWNWARD_DRIFT: "falling",
  UPWARD_DRIFT: "rising",
  INVALID: "invalid",
  UNCLASSIFIED: "review",
};

export function AccumulationShapePath({ chunks = [] }: AccumulationShapePathProps) {
  if (chunks.length === 0) return null;

  const warning = (chunk: AccumulationShapeChunk) => (chunk.shape === "FLAT" || chunk.shape === "FLAT_GOLDEN") && chunk.lineFit?.ignoredOutlierDate;
  const label = (chunk: AccumulationShapeChunk) => `20D ${labels[chunk.shape] ?? chunk.shape}${warning(chunk) ? " ⚠" : ""}`;
  const path = chunks.map(label).join(" → ");
  const title = chunks.map((chunk) => `Part ${chunk.position} (oldest → newest): ${label(chunk)} (${chunk.startDate} → ${chunk.endDate})${warning(chunk) ? ` · ignored shock: ${chunk.lineFit?.ignoredOutlierDate}` : ""}`).join("\n");
  return <Tooltip title={<span style={{ whiteSpace: "pre-line" }}>{title}</span>}><Typography.Text type="secondary" style={{ display: "block", fontSize: 11 }}>{path}</Typography.Text></Tooltip>;
}
