import { useEffect, useMemo, useState } from "react";
import { EyeOutlined } from "@ant-design/icons";
import { Alert, Button, Card, Empty, Input, Select, Space, Spin, Switch, Table, Tag, Tooltip, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useAccumulationAnalysis } from "../hooks/useAccumulationAnalysis";
import type { AccumulationAnalysisPeriod, AccumulationCaseSnapshot } from "../types";
import { AccumulationEvidenceLaneView } from "../components/AccumulationEvidenceLane";
import { AccumulationShapeMetric } from "../components/AccumulationShapeMetric";
import { AccumulationShapeLabel } from "../components/AccumulationShapeLabel";
import { AccumulationShapePath } from "../components/AccumulationShapePath";

const universes = ["nifty_100", "nifty_midcap_150", "nifty_smallcap_250", "nifty_microcap_250"];
const replayPeriods: Array<{ value: AccumulationAnalysisPeriod; label: string }> = [
  { value: "ONE_DAY", label: "1 day" },
  { value: "ONE_WEEK", label: "1 week" },
  { value: "ONE_MONTH", label: "1 month" },
  { value: "THREE_MONTHS", label: "3 months" },
  { value: "SIX_MONTHS", label: "6 months" },
  { value: "NINE_MONTHS", label: "9 months" },
];

function periodLabel(period: AccumulationAnalysisPeriod): string {
  return replayPeriods.find((item) => item.value === period)?.label ?? period;
}

function filters(values: Array<string | number | null>): Array<{ text: string; value: string }> {
  return [...new Set(values.map((value) => value?.toString() ?? "-"))]
    .sort()
    .map((value) => ({ text: value, value }));
}

function base(row: AccumulationCaseSnapshot): string {
  return `${row.chainStartDate} → ${row.chainEndDate}`;
}

function shapeSlope(row: AccumulationCaseSnapshot): number {
  const strictFlat = row.shape === "FLAT" || row.shape === "FLAT_GOLDEN";
  return strictFlat ? row.lineFit?.slopePerTenSessions ?? 0 : row.shapeMetrics?.centerSlopePerTenSessions ?? 0;
}

function days(row: AccumulationCaseSnapshot): string {
  return `${row.sessionsToPhaseD ?? "-"} / ${row.sessionsToBreakout ?? "-"}`;
}

function evidenceDatesInRun(
  row: AccumulationCaseSnapshot,
  source: "phaseD" | "freshBreakout",
  fromDate?: string,
  toDate?: string,
): string[] {
  if (!row.sixMonthEvidence || !fromDate || !toDate) return [];

  return row.sixMonthEvidence[source].filter((date) => date >= fromDate && date <= toDate);
}

function compactEvidenceDate(dates: string[]): string {
  if (dates.length === 0) return "-";
  const latestDate = latestEvidenceDate(dates);
  if (dates.length === 1) return latestDate;
  return `${latestDate} +${dates.length - 1}`;
}

function latestEvidenceDate(dates: string[]): string {
  return dates.at(-1) ?? "";
}

export function compareConfirmationRows(
  left: AccumulationCaseSnapshot,
  right: AccumulationCaseSnapshot,
  evidenceDates: (row: AccumulationCaseSnapshot) => string[],
  keepStockRowsTogether: boolean,
): number {
  const confirmationComparison = latestEvidenceDate(evidenceDates(left)).localeCompare(latestEvidenceDate(evidenceDates(right)));
  if (!keepStockRowsTogether || confirmationComparison !== 0) return confirmationComparison;

  return left.symbol.localeCompare(right.symbol) || left.chainEndDate.localeCompare(right.chainEndDate);
}

interface ForwardAccumulationAnalysisPageProps {
  onOpenTimeline?: (runId: number, row: AccumulationCaseSnapshot) => void;
}

export function ForwardAccumulationAnalysisPage({ onOpenTimeline }: ForwardAccumulationAnalysisPageProps) {
  const { runs, summary, loading, error, loadRuns, loadSummary, run } = useAccumulationAnalysis();
  const [universeKey, setUniverseKey] = useState("nifty_100");
  const [period, setPeriod] = useState<AccumulationAnalysisPeriod>("SIX_MONTHS");
  const [search, setSearch] = useState("");
  const [keepStockRowsTogether, setKeepStockRowsTogether] = useState(true);

  useEffect(() => { void loadRuns().catch(() => undefined); }, [loadRuns]);

  const rows = useMemo(
    () => summary?.rows.filter((row) => row.symbol.toLowerCase().includes(search.trim().toLowerCase())) ?? [],
    [search, summary],
  );

  const phaseDDate = (row: AccumulationCaseSnapshot): string[] => evidenceDatesInRun(
    row,
    "phaseD",
    summary?.run.fromDate,
    summary?.run.toDate,
  );
  const breakoutDate = (row: AccumulationCaseSnapshot): string[] => evidenceDatesInRun(
    row,
    "freshBreakout",
    summary?.run.fromDate,
    summary?.run.toDate,
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
    { title: "Shape", dataIndex: "shape", key: "shape", sorter: (left, right) => left.shape.localeCompare(right.shape), filters: filters(rows.map((row) => row.shape)), onFilter: (value, row) => row.shape === value, render: (shape, row) => <div><AccumulationShapeLabel shape={shape} goldenFlatNode={row.goldenFlatNode} lineFit={row.lineFit} /><AccumulationShapePath chunks={row.shapeChunks} /></div> },
    { title: "Latest 20D fit", key: "metric", sorter: (left, right) => shapeSlope(left) - shapeSlope(right), render: (_, row) => <AccumulationShapeMetric shape={row.shape} metrics={row.shapeMetrics} lineFit={row.lineFit} /> },
    { title: "Decision", dataIndex: "shapeDecision", key: "decision", sorter: (left, right) => left.shapeDecision.localeCompare(right.shapeDecision), filters: filters(rows.map((row) => row.shapeDecision)), onFilter: (value, row) => row.shapeDecision === value },
    {
      title: "Phase D", key: "phase", defaultSortOrder: "descend", sorter: (left, right) => compareConfirmationRows(left, right, phaseDDate, keepStockRowsTogether),
      filters: filters(rows.map((row) => compactEvidenceDate(phaseDDate(row)))), filterSearch: true,
      onFilter: (value, row) => compactEvidenceDate(phaseDDate(row)) === value,
      render: (_, row) => {
        const dates = phaseDDate(row);
        const value = compactEvidenceDate(dates);
        return dates.length > 1 ? <Tooltip title={dates.join(", ")}>{value}</Tooltip> : value;
      },
    },
    {
      title: "Breakout", key: "breakout", sorter: (left, right) => compareConfirmationRows(left, right, breakoutDate, keepStockRowsTogether),
      filters: filters(rows.map((row) => compactEvidenceDate(breakoutDate(row)))), filterSearch: true,
      onFilter: (value, row) => compactEvidenceDate(breakoutDate(row)) === value,
      render: (_, row) => {
        const dates = breakoutDate(row);
        const value = compactEvidenceDate(dates);
        return dates.length > 1 ? <Tooltip title={dates.join(", ")}>{value}</Tooltip> : value;
      },
    },
    { title: "Days to D / BO", key: "days", sorter: (left, right) => days(left).localeCompare(days(right)), filters: filters(rows.map(days)), onFilter: (value, row) => days(row) === value, render: (_, row) => days(row) },
  ], [keepStockRowsTogether, onOpenTimeline, rows, summary]);

  return <div style={{ padding: 24 }}><Space orientation="vertical" size={16} style={{ width: "100%" }}>
    <div><Typography.Title level={4} style={{ margin: 0 }}>Forward Accumulation Analysis</Typography.Title><Typography.Text type="secondary">Replay saved daily accumulation states without using future evidence.</Typography.Text></div>
    {error && <Alert type="error" message={error} />}
    <Card size="small" title="Manual run"><Space><Select value={universeKey} onChange={setUniverseKey} options={universes.map((value) => ({ value, label: value.replaceAll("_", " ") }))} /><Select value={period} onChange={setPeriod} options={replayPeriods} /><Button type="primary" loading={loading} onClick={() => void run({ universeKey, period }).catch(() => undefined)}>Run replay</Button></Space></Card>
    <Card size="small" title="Saved runs">{runs.map((item) => <Button key={item.id} size="small" style={{ marginRight: 8, marginBottom: 8 }} onClick={() => void loadSummary(item.id)}>{item.universeKey} · {periodLabel(item.period)} · #{item.id}</Button>)}</Card>
    <Card size="small" title={summary ? `Run #${summary.run.id}${summary.isStale ? " · stale" : ""}` : "Run results"} extra={<Space size={8}><Tooltip title="When sorting Phase D or Breakout, keep every base for the same stock together."><Space size={4}><Typography.Text type="secondary" style={{ fontSize: 12 }}>Group stocks</Typography.Text><Switch aria-label="Keep stock rows together" size="small" checked={keepStockRowsTogether} onChange={setKeepStockRowsTogether} /></Space></Tooltip><Input allowClear placeholder="Search symbol" value={search} onChange={(event) => setSearch(event.target.value)} style={{ width: 180 }} /></Space>}>
      {loading ? <Spin /> : !summary ? <Empty description="Run a universe to view saved snapshots" /> : <Table size="small" rowKey={(row) => `${row.symbol}-${row.chainStartDate}-${row.chainEndDate}`} columns={columns} dataSource={rows} pagination={false} scroll={{ x: 1120 }} />}
    </Card>
  </Space></div>;
}
