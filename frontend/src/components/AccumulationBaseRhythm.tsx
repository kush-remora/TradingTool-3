import { Tag, Tooltip, Typography } from "antd";
import type { AccumulationBaseRhythm } from "../types";

interface AccumulationBaseRhythmProps {
  rhythm: AccumulationBaseRhythm | null;
}

const directionLabel = { FALLING: "falling", FLAT: "flat", RISING: "rising" } as const;
const stateLabel = { CONTRACTING: "contracting", STEADY: "steady", EXPANDING: "expanding" } as const;
const directionColor = { FALLING: "red", FLAT: "blue", RISING: "green" } as const;

export function AccumulationBaseRhythm({ rhythm }: AccumulationBaseRhythmProps) {
  if (!rhythm || rhythm.blocks.length === 0) return <Typography.Text type="secondary">Not available</Typography.Text>;

  const title = rhythm.blocks.map((block) => {
    const change = `${block.closeChangePercent >= 0 ? "+" : ""}${block.closeChangePercent.toFixed(1)}%`;
    return `Part ${block.position}: ${block.startDate} → ${block.endDate} · ${directionLabel[block.direction]} (${change}) · range ${stateLabel[block.rangeState]} (${block.rangePercent.toFixed(1)}%) · volume ${stateLabel[block.volumeState]}`;
  }).join("\n");

  return <Tooltip title={<span style={{ whiteSpace: "pre-line" }}>{title}</span>}><div>
    <Typography.Text type="secondary" style={{ display: "block", fontSize: 11 }}>60D base rhythm · {rhythm.startDate} → {rhythm.endDate}</Typography.Text>
    {rhythm.blocks.map((block) => <Tag key={block.position} color={directionColor[block.direction]} style={{ marginBottom: 2 }}>{block.position}. {directionLabel[block.direction]}</Tag>)}
  </div></Tooltip>;
}
