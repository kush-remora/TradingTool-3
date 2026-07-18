import { useEffect, useMemo, useState } from "react";
import { EyeOutlined } from "@ant-design/icons";
import { Alert, Button, Card, Empty, Input, Select, Space, Spin, Table, Tag, Tooltip, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useAccumulationAnalysis } from "../hooks/useAccumulationAnalysis";
import type { AccumulationCaseSnapshot } from "../types";
import { AccumulationEvidenceLaneView } from "../components/AccumulationEvidenceLane";

const universes = ["nifty_100", "nifty_midcap_150", "nifty_smallcap_250", "nifty_microcap_250"];

function filters(values: Array<string | number | null>): Array<{ text: string; value: string }> {
  return [...new Set(values.map((value) => value?.toString() ?? "-"))]
    .sort()
    .map((value) => ({ text: value, value }));
}

function base(row: AccumulationCaseSnapshot): string {
  return `${row.chainStartDate} → ${row.chainEndDate}`;
}

function days(row: AccumulationCaseSnapshot): string {
  return `${row.sessionsToPhaseD ?? "-"} / ${row.sessionsToBreakout ?? "-"}`;
}

interface ForwardAccumulationAnalysisPageProps {
  onOpenTimeline?: (runId: number, row: AccumulationCaseSnapshot) => void;
}

export function ForwardAccumulationAnalysisPage({ onOpenTimeline }: ForwardAccumulationAnalysisPageProps) {
  const { runs, summary, loading, error, loadRuns, loadSummary, run } = useAccumulationAnalysis();
  const [universeKey, setUniverseKey] = useState("nifty_100");
  const [months, setMonths] = useState(6);
  const [search, setSearch] = useState("");

  useEffect(() => { void loadRuns().catch(() => undefined); }, [loadRuns]);

  const rows = useMemo(
    () => summary?.rows.filter((row) => row.symbol.toLowerCase().includes(search.trim().toLowerCase())) ?? [],
    [search, summary],
  );

  const columns = useMemo<ColumnsType<AccumulationCaseSnapshot>>(() => [
    {
      title: "Symbol / evidence", dataIndex: "symbol", key: "symbol", width: 290, sorter: (left, right) => left.symbol.localeCompare(right.symbol),
      filters: filters(rows.map((row) => row.symbol)), filterSearch: true,
      onFilter: (value, row) => row.symbol === value,
      render: (symbol, row) => <div>
        <Space size={3} wrap={false}>
          <Typography.Text copyable={{ text: symbol }}>{symbol}</Typography.Text>
          {row.curatedWatchlists.length > 0 && <Tooltip title={row.curatedWatchlists.join(", ")}><Tag color="blue" style={{ marginInlineEnd: 0 }}>WL {row.curatedWatchlists.length}</Tag></Tooltip>}
          <Button aria-label={`View ${symbol} timeline`} icon={<EyeOutlined />} type="text" size="small" onClick={() => summary && onOpenTimeline?.(summary.run.id, row)} />
        </Space>
        <div style={{ marginTop: 3 }}><AccumulationEvidenceLaneView evidence={row.sixMonthEvidence} /></div>
      </div>,
    },
    { title: "Base", key: "base", sorter: (left, right) => base(left).localeCompare(base(right)), filters: filters(rows.map(base)), filterSearch: true, onFilter: (value, row) => base(row) === value, render: (_, row) => base(row) },
    { title: "Sessions", dataIndex: "chainLengthSessions", key: "length", sorter: (left, right) => left.chainLengthSessions - right.chainLengthSessions, filters: filters(rows.map((row) => row.chainLengthSessions)), onFilter: (value, row) => row.chainLengthSessions.toString() === value },
    { title: "Hits", dataIndex: "hitCount", key: "hits", sorter: (left, right) => left.hitCount - right.hitCount, filters: filters(rows.map((row) => row.hitCount)), onFilter: (value, row) => row.hitCount.toString() === value },
    { title: "Shape", dataIndex: "shape", key: "shape", sorter: (left, right) => left.shape.localeCompare(right.shape), filters: filters(rows.map((row) => row.shape)), onFilter: (value, row) => row.shape === value, render: (shape) => <Tag color={shape === "INVALID" ? "red" : shape === "UNCLASSIFIED" ? "gold" : "green"}>{shape}</Tag> },
    { title: "Decision", dataIndex: "shapeDecision", key: "decision", sorter: (left, right) => left.shapeDecision.localeCompare(right.shapeDecision), filters: filters(rows.map((row) => row.shapeDecision)), onFilter: (value, row) => row.shapeDecision === value },
    { title: "Phase D", dataIndex: "firstPhaseDDate", key: "phase", sorter: (left, right) => (left.firstPhaseDDate ?? "").localeCompare(right.firstPhaseDDate ?? ""), filters: filters(rows.map((row) => row.firstPhaseDDate)), filterSearch: true, onFilter: (value, row) => (row.firstPhaseDDate ?? "-") === value },
    { title: "Breakout", dataIndex: "firstBreakoutDate", key: "breakout", sorter: (left, right) => (left.firstBreakoutDate ?? "").localeCompare(right.firstBreakoutDate ?? ""), filters: filters(rows.map((row) => row.firstBreakoutDate)), filterSearch: true, onFilter: (value, row) => (row.firstBreakoutDate ?? "-") === value },
    { title: "Days to D / BO", key: "days", sorter: (left, right) => days(left).localeCompare(days(right)), filters: filters(rows.map(days)), onFilter: (value, row) => days(row) === value, render: (_, row) => days(row) },
  ], [onOpenTimeline, rows, summary]);

  return <div style={{ padding: 24 }}><Space orientation="vertical" size={16} style={{ width: "100%" }}>
    <div><Typography.Title level={4} style={{ margin: 0 }}>Forward Accumulation Analysis</Typography.Title><Typography.Text type="secondary">Replay saved daily accumulation states without using future evidence.</Typography.Text></div>
    {error && <Alert type="error" message={error} />}
    <Card size="small" title="Manual run"><Space><Select value={universeKey} onChange={setUniverseKey} options={universes.map((value) => ({ value, label: value.replaceAll("_", " ") }))} /><Select value={months} onChange={setMonths} options={[1, 3, 6, 9].map((value) => ({ value, label: `${value} months` }))} /><Button type="primary" loading={loading} onClick={() => void run({ universeKey, months }).catch(() => undefined)}>Run replay</Button></Space></Card>
    <Card size="small" title="Saved runs">{runs.map((item) => <Button key={item.id} size="small" style={{ marginRight: 8, marginBottom: 8 }} onClick={() => void loadSummary(item.id)}>{item.universeKey} · {item.months}m · #{item.id}</Button>)}</Card>
    <Card size="small" title={summary ? `Run #${summary.run.id}${summary.isStale ? " · stale" : ""}` : "Run results"} extra={<Input allowClear placeholder="Search symbol" value={search} onChange={(event) => setSearch(event.target.value)} style={{ width: 180 }} />}>
      {loading ? <Spin /> : !summary ? <Empty description="Run a universe to view saved snapshots" /> : <Table size="small" rowKey={(row) => `${row.symbol}-${row.chainStartDate}-${row.chainEndDate}`} columns={columns} dataSource={rows} pagination={false} scroll={{ x: 1120 }} />}
    </Card>
  </Space></div>;
}
