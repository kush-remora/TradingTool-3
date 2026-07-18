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

  const path = chunks.map((chunk) => labels[chunk.shape] ?? chunk.shape).join(" → ");
  const title = chunks.map((chunk) => `Part ${chunk.position}: ${labels[chunk.shape] ?? chunk.shape} (${chunk.startDate} → ${chunk.endDate})`).join("\n");
  return <Tooltip title={<span style={{ whiteSpace: "pre-line" }}>{title}</span>}><Typography.Text type="secondary" style={{ display: "block", fontSize: 11 }}>{path}</Typography.Text></Tooltip>;
}
