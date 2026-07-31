import { ExportOutlined, UploadOutlined } from "@ant-design/icons";
import { useMemo, useState } from "react";
import { Alert, Button, Card, InputNumber, Select, Space, Statistic, Switch, Table, Tag, Tooltip, Typography, Upload, message } from "antd";
import type { ColumnsType } from "antd/es/table";
import type { UploadFile, UploadProps } from "antd/es/upload/interface";

import { useSilentBreakoutBacktest } from "../hooks/useSilentBreakoutBacktest";
import type { SilentBreakoutBacktestRow } from "../types";

type WyckoffLabel = "ACCUMULATION" | "DISTRIBUTION" | "UNCLEAR";

const labelOptions = [
  { value: "ACCUMULATION", label: "Accumulation" },
  { value: "DISTRIBUTION", label: "Distribution" },
  { value: "UNCLEAR", label: "Unclear" },
];

const futurePerformanceColumnKeys = new Set([
  "entryPrice",
  "target",
  "nextFiveSessionsLow",
  "nextFiveSessionsLowMovePct",
  "nextFiveSessionsLowDays",
  "forward20SessionReturnPct",
  "forward40SessionReturnPct",
  "maxGain40SessionsPct",
  "maxDrawdown40SessionsPct",
]);

function formatPercent(value: number | null): string {
  return value == null ? "—" : `${value > 0 ? "+" : ""}${value.toFixed(2)}%`;
}

function formatPrice(value: number | null): string {
  return value == null ? "—" : `₹${value.toFixed(2)}`;
}

function percentNode(value: number | null): React.ReactNode {
  if (value == null) return "—";
  return <Typography.Text type={value > 0 ? "success" : value < 0 ? "danger" : undefined}>{formatPercent(value)}</Typography.Text>;
}

function dataStatusTag(status: SilentBreakoutBacktestRow["dataStatus"]): React.ReactNode {
  if (status === "AVAILABLE") return <Tag>Available</Tag>;
  return <Tag color="orange">{status === "MISSING_SIGNAL_CANDLE" ? "Signal candle missing" : "Partial history"}</Tag>;
}

interface SilentBreakoutBacktestPageProps {
  onOpenStockReview: (symbol: string) => void;
}

export function SilentBreakoutBacktestPage({ onOpenStockReview }: SilentBreakoutBacktestPageProps) {
  const { data, loading, error, run } = useSilentBreakoutBacktest();
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [labels, setLabels] = useState<Record<string, WyckoffLabel>>({});
  const [targetPct, setTargetPct] = useState(20);
  const [blindReview, setBlindReview] = useState(true);

  const uploadProps: UploadProps = {
    accept: ".csv",
    beforeUpload: () => false,
    fileList,
    maxCount: 1,
    onChange: ({ fileList: nextFileList }) => setFileList(nextFileList),
  };

  const runBacktest = async (): Promise<void> => {
    const file = fileList[0]?.originFileObj;
    if (!file) {
      message.error("Select a CSV with symbol and date columns.");
      return;
    }
    try {
      await run(await file.text(), targetPct);
      setLabels({});
      message.success("Silent breakout backtest completed.");
    } catch {
      // The hook exposes the request error in the page alert.
    }
  };

  const columns = useMemo<ColumnsType<SilentBreakoutBacktestRow>>(() => ([
    {
      title: "Symbol", dataIndex: "symbol", key: "symbol", fixed: "left", sorter: (left, right) => left.symbol.localeCompare(right.symbol),
      render: (symbol: string, row) => <Space direction="vertical" size={0}>
        {row.instrumentToken ? <a href={`https://kite.zerodha.com/chart/web/tvc/NSE/${symbol}/${row.instrumentToken}`} target="_blank" rel="noreferrer"><Typography.Text strong>{symbol}</Typography.Text></a> : <Typography.Text strong>{symbol}</Typography.Text>}
        <Button aria-label={`Open ${symbol} three-week review`} type="link" size="small" icon={<ExportOutlined />} onClick={() => onOpenStockReview(symbol)} style={{ padding: 0, height: "auto" }}>Review</Button>
      </Space>,
    },
    { title: "Signal date", dataIndex: "signalDate", key: "signalDate", sorter: (left, right) => left.signalDate.localeCompare(right.signalDate) },
    { title: "Data", dataIndex: "dataStatus", key: "dataStatus", render: dataStatusTag },
    { title: "vs 52W high", dataIndex: "distanceFromFiftyTwoWeekHighPct", key: "distanceFromFiftyTwoWeekHighPct", sorter: (left, right) => (left.distanceFromFiftyTwoWeekHighPct ?? -Infinity) - (right.distanceFromFiftyTwoWeekHighPct ?? -Infinity), render: percentNode },
    { title: "20D move", dataIndex: "roc20Pct", key: "roc20Pct", sorter: (left, right) => (left.roc20Pct ?? -Infinity) - (right.roc20Pct ?? -Infinity), render: percentNode },
    { title: "vs 200 DMA", dataIndex: "distanceFromSma200Pct", key: "distanceFromSma200Pct", sorter: (left, right) => (left.distanceFromSma200Pct ?? -Infinity) - (right.distanceFromSma200Pct ?? -Infinity), render: percentNode },
    { title: "Late-stage", dataIndex: "lateStageRisk", key: "lateStageRisk", render: (risk: boolean | null) => risk == null ? "—" : <Tag color={risk ? "red" : "green"}>{risk ? "Risk" : "No"}</Tag> },
    { title: "Entry · next open", dataIndex: "entryPrice", key: "entryPrice", sorter: (left, right) => (left.entryPrice ?? -Infinity) - (right.entryPrice ?? -Infinity), render: formatPrice },
    { title: "Max delivery · 5D", dataIndex: "priorFiveSessionsMaxDeliveryPct", key: "priorFiveSessionsMaxDeliveryPct", sorter: (left, right) => (left.priorFiveSessionsMaxDeliveryPct ?? -Infinity) - (right.priorFiveSessionsMaxDeliveryPct ?? -Infinity), render: percentNode },
    { title: "Target", key: "target", render: (_, row) => row.targetAchieved == null ? "—" : row.targetAchieved ? <Tag color="green">Hit · {row.targetAchievedDays}D</Tag> : <Tag color="default">Not hit</Tag> },
    { title: "5D low", dataIndex: "nextFiveSessionsLow", key: "nextFiveSessionsLow", sorter: (left, right) => (left.nextFiveSessionsLow ?? -Infinity) - (right.nextFiveSessionsLow ?? -Infinity), render: formatPrice },
    { title: "5D low vs signal", dataIndex: "nextFiveSessionsLowMovePct", key: "nextFiveSessionsLowMovePct", sorter: (left, right) => (left.nextFiveSessionsLowMovePct ?? -Infinity) - (right.nextFiveSessionsLowMovePct ?? -Infinity), render: percentNode },
    { title: "Days to 5D low", dataIndex: "nextFiveSessionsLowDays", key: "nextFiveSessionsLowDays", sorter: (left, right) => (left.nextFiveSessionsLowDays ?? Infinity) - (right.nextFiveSessionsLowDays ?? Infinity), render: (value: number | null) => value == null ? "—" : `${value}D` },
    { title: "Forward 20D", dataIndex: "forward20SessionReturnPct", key: "forward20SessionReturnPct", sorter: (left, right) => (left.forward20SessionReturnPct ?? -Infinity) - (right.forward20SessionReturnPct ?? -Infinity), render: percentNode },
    { title: "Forward 40D", dataIndex: "forward40SessionReturnPct", key: "forward40SessionReturnPct", sorter: (left, right) => (left.forward40SessionReturnPct ?? -Infinity) - (right.forward40SessionReturnPct ?? -Infinity), render: percentNode },
    { title: "Max gain 40D", dataIndex: "maxGain40SessionsPct", key: "maxGain40SessionsPct", render: percentNode },
    { title: "Max DD 40D", dataIndex: "maxDrawdown40SessionsPct", key: "maxDrawdown40SessionsPct", render: percentNode },
    { title: "Chart verdict", key: "label", render: (_, row) => <Select aria-label={`Wyckoff verdict for ${row.symbol} on ${row.signalDate}`} allowClear placeholder="Review chart" options={labelOptions} value={labels[`${row.symbol}-${row.signalDate}`]} onChange={(value: WyckoffLabel) => setLabels((current) => ({ ...current, [`${row.symbol}-${row.signalDate}`]: value }))} style={{ width: 145 }} /> },
  ]).filter((column) => !blindReview || !futurePerformanceColumnKeys.has(String(column.key))), [blindReview, labels, onOpenStockReview]);

  return <div style={{ padding: 24, maxWidth: 1800, margin: "0 auto" }}>
    <Space orientation="vertical" size={16} style={{ width: "100%" }}>
      <Card size="small" title="Silent Breakout Backtesting Engine">
        <Space orientation="vertical" size={12} style={{ width: "100%" }}>
          <Typography.Text type="secondary">Upload a Chartink export with <Typography.Text code>symbol</Typography.Text> and <Typography.Text code>date</Typography.Text>. The scan rule stays external; this tool measures price action after each signal and flags a possible late-stage move.</Typography.Text>
          <Space wrap><Upload {...uploadProps}><Button icon={<UploadOutlined />}>Select CSV</Button></Upload><InputNumber aria-label="Target percentage" value={targetPct} min={0.1} max={1000} step={0.5} addonAfter="% target" onChange={(value) => setTargetPct(value ?? 20)} /><Button type="primary" loading={loading} disabled={fileList.length === 0} onClick={() => void runBacktest()}>Run Backtest</Button></Space>
          <Typography.Text type="secondary">Late-stage risk = a 20D move of at least 20%. The 52-week-high distance and 200 DMA remain chart-review context, not automatic exclusions.</Typography.Text>
        </Space>
      </Card>
      {error && <Alert type="error" showIcon message={error} />}
      {data && <>
        <Card size="small"><Space size={36} wrap><Statistic title="Signals" value={data.summary.signalCount} /><Statistic title="Analysed" value={data.summary.availableCount} /><Statistic title="Late-stage risk" value={data.summary.lateStageRiskCount} /><Statistic title="Average forward 20D" value={formatPercent(data.summary.averageForward20SessionReturnPct)} /><Statistic title="Average forward 40D" value={formatPercent(data.summary.averageForward40SessionReturnPct)} /></Space></Card>
        <Card size="small" title="Signal review" extra={<Tooltip title="Hide every post-signal metric before inspecting the chart."><Space size={6}><Typography.Text type="secondary" style={{ fontSize: 12 }}>Blind review</Typography.Text><Switch aria-label="Blind review" size="small" checked={blindReview} onChange={setBlindReview} /></Space></Tooltip>}><Table size="small" sticky={{ offsetHeader: 64 }} rowKey={(row) => `${row.symbol}-${row.signalDate}`} columns={columns} dataSource={data.rows} pagination={{ pageSize: 50 }} scroll={{ x: 1500 }} /></Card>
      </>}
    </Space>
  </div>;
}
