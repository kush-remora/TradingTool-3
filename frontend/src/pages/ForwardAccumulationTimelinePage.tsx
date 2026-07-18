import { useEffect } from "react";
import { ArrowLeftOutlined } from "@ant-design/icons";
import { Alert, Button, Card, Empty, Space, Spin, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useAccumulationAnalysis } from "../hooks/useAccumulationAnalysis";
import type { AccumulationCaseSnapshot } from "../types";
import { AccumulationEvidenceDates, AccumulationEvidenceLaneView } from "../components/AccumulationEvidenceLane";
import { AccumulationShapeMetric } from "../components/AccumulationShapeMetric";

interface ForwardAccumulationTimelinePageProps {
  runId: number;
  symbol: string;
  chainStartDate: string | null;
  chainEndDate: string | null;
  onBack: () => void;
}

const columns: ColumnsType<AccumulationCaseSnapshot> = [
  { title: "As of", dataIndex: "asOfDate", sorter: (left, right) => left.asOfDate.localeCompare(right.asOfDate) },
  { title: "Base start", dataIndex: "chainStartDate", sorter: (left, right) => left.chainStartDate.localeCompare(right.chainStartDate) },
  { title: "Base end", dataIndex: "chainEndDate", sorter: (left, right) => left.chainEndDate.localeCompare(right.chainEndDate) },
  { title: "Sessions", dataIndex: "chainLengthSessions", sorter: (left, right) => left.chainLengthSessions - right.chainLengthSessions },
  { title: "Hits", dataIndex: "hitCount", sorter: (left, right) => left.hitCount - right.hitCount },
  { title: "Shape", dataIndex: "shape", sorter: (left, right) => left.shape.localeCompare(right.shape), render: (shape) => <Tag color={shape === "INVALID" ? "red" : shape === "UNCLASSIFIED" ? "gold" : "green"}>{shape}</Tag> },
  { title: "Decision metric", key: "metric", sorter: (left, right) => (left.shapeMetrics?.centerSlopePerTenSessions ?? 0) - (right.shapeMetrics?.centerSlopePerTenSessions ?? 0), render: (_, row) => <AccumulationShapeMetric metrics={row.shapeMetrics} /> },
  { title: "Decision", dataIndex: "shapeDecision", sorter: (left, right) => left.shapeDecision.localeCompare(right.shapeDecision) },
  { title: "Phase D", key: "phaseD", render: (_, row) => <DateTags dates={row.confirmationDates.phaseD} /> },
  { title: "Fresh breakout", key: "breakout", render: (_, row) => <DateTags dates={row.confirmationDates.freshBreakout} /> },
  { title: "New 52W high", key: "fiftyTwoWeekHigh", render: (_, row) => <DateTags dates={row.confirmationDates.fiftyTwoWeekHigh} /> },
];

function DateTags({ dates }: { dates: string[] }) {
  return dates.length === 0 ? "-" : <Space size={2} wrap>{dates.map((date) => <Tag key={date}>{date}</Tag>)}</Space>;
}

export function ForwardAccumulationTimelinePage({ runId, symbol, chainStartDate, chainEndDate, onBack }: ForwardAccumulationTimelinePageProps) {
  const { timeline, timelineLoading, error, loadTimeline } = useAccumulationAnalysis();

  useEffect(() => {
    void loadTimeline(runId, symbol, chainStartDate, chainEndDate).catch(() => undefined);
  }, [chainEndDate, chainStartDate, loadTimeline, runId, symbol]);

  return <div style={{ padding: 24 }}><Space orientation="vertical" size={16} style={{ width: "100%" }}>
    <Button icon={<ArrowLeftOutlined />} onClick={onBack}>Back to analysis</Button>
    <div><Typography.Title level={4} style={{ margin: 0 }}>{symbol} · {chainStartDate ?? "selected"} → {chainEndDate ?? "base"}</Typography.Title><Typography.Text type="secondary">Run #{runId} daily accumulation progression.</Typography.Text></div>
    {timeline?.rows[0]?.curatedWatchlists.length ? <Space size={4}>Watchlists: {timeline.rows[0].curatedWatchlists.map((watchlist) => <Tag color="blue" key={watchlist}>{watchlist}</Tag>)}</Space> : null}
    {error && <Alert type="error" message={error} />}
    {timeline?.rows[0] && <Card size="small" title="Six-month evidence"><AccumulationEvidenceLaneView evidence={timeline.rows[0].sixMonthEvidence} /><div style={{ marginTop: 8 }}><AccumulationEvidenceDates evidence={timeline.rows[0].sixMonthEvidence} /></div></Card>}
    <Card size="small" title={timeline?.isStale ? "Daily snapshots · stale evidence" : "Daily snapshots"}>
      {timelineLoading && <Spin />}
      {!timelineLoading && !timeline && <Empty description="No timeline snapshots found" />}
      {!timelineLoading && timeline?.rows.length === 0 && <Empty description="No timeline snapshots found" />}
      {!timelineLoading && timeline && timeline.rows.length > 0 && <Table rowKey={(row) => `${row.chainStartDate}-${row.chainEndDate}-${row.asOfDate}`} columns={columns} dataSource={timeline.rows} pagination={false} scroll={{ x: 1350 }} />}
    </Card>
  </Space></div>;
}
