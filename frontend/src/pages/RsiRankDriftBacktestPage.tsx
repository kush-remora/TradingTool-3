import { Alert, Button, Card, DatePicker, Empty, InputNumber, Select, Space, Spin, Statistic, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import dayjs from "dayjs";
import { useMemo, useState } from "react";
import { useRsiMomentum } from "../hooks/useRsiMomentum";
import { useRsiRankDriftBacktest } from "../hooks/useRsiRankDriftBacktest";
import type { BacktestTrade, RsiRankDriftBacktestRequest } from "../types";

const { RangePicker } = DatePicker;

const inr = (value: number): string => `₹${value.toFixed(2)}`;

const tradeColumns: ColumnsType<BacktestTrade> = [
  { title: "Symbol", dataIndex: "symbol", key: "symbol", width: 100, fixed: "left" },
  { title: "Entry Date", dataIndex: "entryDate", key: "entryDate", width: 110 },
  { title: "Exit Date", dataIndex: "exitDate", key: "exitDate", width: 110 },
  { title: "Entry Rank", dataIndex: "entryRank", key: "entryRank", width: 95, render: (value: number) => `#${value}` },
  { title: "Entry", dataIndex: "entryPrice", key: "entryPrice", width: 100, render: (value: number) => inr(value) },
  { title: "Exit", dataIndex: "exitPrice", key: "exitPrice", width: 100, render: (value: number) => inr(value) },
  { title: "Target", dataIndex: "targetPrice", key: "targetPrice", width: 100, render: (value: number) => inr(value) },
  { title: "Stop", dataIndex: "stopLossPrice", key: "stopLossPrice", width: 100, render: (value: number) => inr(value) },
  {
    title: "P&L %",
    dataIndex: "profitPct",
    key: "profitPct",
    width: 90,
    render: (value: number) => (
      <Typography.Text style={{ color: value >= 0 ? "#3f8600" : "#cf1322" }}>
        {value >= 0 ? "+" : ""}{value.toFixed(2)}%
      </Typography.Text>
    ),
  },
  { title: "Reason", dataIndex: "exitReason", key: "exitReason", width: 140 },
  { title: "Days", dataIndex: "holdingDays", key: "holdingDays", width: 80 },
];

export function RsiRankDriftBacktestPage() {
  const { data, loading, error } = useRsiMomentum();
  const profiles = data?.profiles ?? [];
  const profileOptions = useMemo(
    () => profiles.map((profile) => ({
      value: profile.profileId,
      label: `${profile.profileLabel || profile.config.profileLabel} (${profile.config.baseUniversePreset})`,
    })),
    [profiles],
  );

  const [profileId, setProfileId] = useState<string | null>(null);
  const [range, setRange] = useState<[dayjs.Dayjs, dayjs.Dayjs]>([dayjs().subtract(6, "month"), dayjs()]);
  const [capital, setCapital] = useState<number>(100000);
  const [targetPct, setTargetPct] = useState<number>(10);
  const [atrStopMultiplier, setAtrStopMultiplier] = useState<number>(1);
  const [entryRankMin, setEntryRankMin] = useState<number>(5);
  const [entryRankMax, setEntryRankMax] = useState<number>(10);

  const { report, loading: running, error: runError, runBacktest } = useRsiRankDriftBacktest();

  const onRun = (): void => {
    const selectedProfileId = profileId ?? profileOptions[0]?.value;
    if (!selectedProfileId) {
      return;
    }

    const request: RsiRankDriftBacktestRequest = {
      profileId: selectedProfileId,
      fromDate: range[0].format("YYYY-MM-DD"),
      toDate: range[1].format("YYYY-MM-DD"),
      initialCapital: capital,
      targetPct,
      atrStopMultiplier,
      entryRankMin,
      entryRankMax,
      priorBetterRankLookbackDays: 40,
      runBackfill: true,
    };
    void runBacktest(request);
  };

  if (loading) {
    return (
      <div style={{ textAlign: "center", padding: 64 }}>
        <Spin size="large" />
      </div>
    );
  }

  if (error) {
    return <Alert type="error" showIcon message={error} />;
  }

  if (profiles.length === 0) {
    return (
      <Card>
        <Empty description="No RSI momentum profiles available." />
      </Card>
    );
  }

  return (
    <div style={{ padding: 24, background: "#f5f7fa", minHeight: "calc(100vh - 48px)" }}>
      <Space direction="vertical" size={16} style={{ width: "100%" }}>
        <div>
          <Typography.Title level={4} style={{ margin: 0 }}>RSI Rank Drift Backtest</Typography.Title>
          <Typography.Text type="secondary">
            Single active trade only. Entry scan uses rank priority from start to end (for 20-25: try 20, then 21, then 22...). Stop loss is ATR trailing: it moves up with price, never down.
          </Typography.Text>
        </div>

        <Card size="small" title="Controls">
          <Space wrap align="center">
            <Typography.Text strong>Universe:</Typography.Text>
            <Select
              style={{ width: 320 }}
              options={profileOptions}
              value={profileId ?? profileOptions[0]?.value}
              onChange={(value) => setProfileId(value)}
            />

            <Typography.Text strong>Date Range:</Typography.Text>
            <RangePicker
              value={range}
              allowClear={false}
              onChange={(next) => {
                if (next?.[0] && next?.[1]) {
                  setRange([next[0], next[1]]);
                }
              }}
              format="YYYY-MM-DD"
            />

            <Typography.Text strong style={{ marginLeft: 12 }}>Capital:</Typography.Text>
            <InputNumber min={10000} step={10000} value={capital} addonAfter="INR" onChange={(value) => setCapital(value ?? 100000)} />

            <Typography.Text strong style={{ marginLeft: 12 }}>Target %:</Typography.Text>
            <InputNumber min={0.1} step={0.5} value={targetPct} onChange={(value) => setTargetPct(value ?? 10)} />

            <Typography.Text strong style={{ marginLeft: 12 }}>ATR Multiplier:</Typography.Text>
            <InputNumber min={0.5} max={5} step={0.25} value={atrStopMultiplier} onChange={(value) => setAtrStopMultiplier(value ?? 1)} />

            <Typography.Text strong style={{ marginLeft: 12 }}>Rank to Pick:</Typography.Text>
            <InputNumber min={1} value={entryRankMin} onChange={(value) => {
              const nextMin = Math.max(1, value ?? 1);
              setEntryRankMin(nextMin);
              setEntryRankMax((prev) => Math.max(prev, nextMin));
            }} />
            <Typography.Text>-</Typography.Text>
            <InputNumber min={entryRankMin} value={entryRankMax} onChange={(value) => setEntryRankMax(Math.max(entryRankMin, value ?? entryRankMin))} />

            <Typography.Text type="secondary" style={{ marginLeft: 12 }}>Rule Window: 40 days</Typography.Text>

            <Button type="primary" onClick={onRun} loading={running}>Run Backtest</Button>
          </Space>
        </Card>

        {runError && <Alert type="error" showIcon message={runError} />}

        {report && (
          <>
            <Space size={12} wrap>
              <Card size="small"><Statistic title="Final Capital" value={report.finalCapital.toFixed(2)} prefix="₹" /></Card>
              <Card size="small"><Statistic title="Total Profit" value={report.totalProfit.toFixed(2)} prefix="₹" valueStyle={{ color: report.totalProfit >= 0 ? "#3f8600" : "#cf1322" }} /></Card>
              <Card size="small"><Statistic title="Return %" value={report.totalProfitPct.toFixed(2)} suffix="%" valueStyle={{ color: report.totalProfitPct >= 0 ? "#3f8600" : "#cf1322" }} /></Card>
              <Card size="small"><Statistic title="Trades" value={report.totalTrades} /></Card>
              <Card size="small"><Statistic title="Win Rate" value={report.winRate.toFixed(2)} suffix="%" /></Card>
              <Card size="small"><Statistic title="Avg Hold" value={report.avgHoldingDays.toFixed(1)} suffix="days" /></Card>
              <Card size="small"><Statistic title="Rule Skips" value={report.entriesSkippedByPriorBetterRankRule} /></Card>
            </Space>

            <Alert
              type="info"
              message="Applied Rule"
              description={`For rank-start ${report.entryRankMin}, a stock is skipped if it had rank < ${report.entryRankMin} any time in the previous ${report.priorBetterRankLookbackDays} snapshot days. Stop is trailing: max(previous stop, peak close since entry - ATR${report.atrPeriod} x ${report.atrStopMultiplier}).`}
            />

            <Card
              size="small"
              title={`Trade Journal — Requested: ${report.fromDate} to ${report.toDate} · Rank band: ${report.entryRankMin}-${report.entryRankMax} · Target ${report.targetPct}% · Stop ATR${report.atrPeriod} x ${report.atrStopMultiplier}`}
            >
              <Table
                columns={tradeColumns}
                dataSource={report.trades}
                rowKey={(row) => `${row.symbol}-${row.entryDate}`}
                size="small"
                pagination={{ pageSize: 20, showSizeChanger: true }}
                scroll={{ x: 1250 }}
              />
            </Card>
          </>
        )}
      </Space>
    </div>
  );
}
