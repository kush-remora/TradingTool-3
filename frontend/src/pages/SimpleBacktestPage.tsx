import { Alert, Button, Card, DatePicker, Empty, InputNumber, Space, Spin, Statistic, Table, Tabs, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import dayjs from "dayjs";
import { useEffect, useState } from "react";
import { useRsiMomentum } from "../hooks/useRsiMomentum";
import { useSimpleMomentumBacktest } from "../hooks/useSimpleMomentumBacktest";
import type {
  SimpleMomentumBacktestResult,
  SimpleMomentumPrepareRequest,
  SimpleMomentumPrepareResponse,
  SimpleMomentumTrade,
} from "../types";
import { postJson } from "../utils/api";

type DateRange = [dayjs.Dayjs, dayjs.Dayjs];

const { RangePicker } = DatePicker;

const fmtMoney = (value: number | null | undefined): string => {
  if (typeof value !== "number") return "—";
  return `₹${value.toFixed(2)}`;
};

const fmtPct = (value: number | null | undefined): React.ReactNode => {
  if (typeof value !== "number") return "—";
  const color = value >= 0 ? "#3f8600" : "#cf1322";
  return (
    <Typography.Text style={{ color }}>
      {value >= 0 ? "+" : ""}{value.toFixed(2)}%
    </Typography.Text>
  );
};

const tradeColumns: ColumnsType<SimpleMomentumTrade> = [
  { title: "Symbol", dataIndex: "symbol", key: "symbol", width: 120, fixed: "left" },
  {
    title: "Status",
    dataIndex: "status",
    key: "status",
    width: 90,
    render: (status: string) => <Tag color={status === "OPEN" ? "blue" : "default"}>{status}</Tag>,
  },
  { title: "Entry Date", dataIndex: "entryDate", key: "entryDate", width: 110 },
  { title: "Entry Rank", dataIndex: "entryRank", key: "entryRank", width: 90, render: (rank: number) => `#${rank}` },
  { title: "Entry Price", dataIndex: "entryPrice", key: "entryPrice", width: 110, render: fmtMoney },
  { title: "Qty", dataIndex: "quantity", key: "quantity", width: 90 },
  { title: "Invested", dataIndex: "investedAmount", key: "investedAmount", width: 120, render: fmtMoney },
  { title: "Exit Date", dataIndex: "exitDate", key: "exitDate", width: 110, render: (value: string | null) => value ?? "—" },
  { title: "Exit Rank", dataIndex: "exitRank", key: "exitRank", width: 90, render: (rank: number | null) => (rank == null ? "—" : `#${rank}`) },
  { title: "Exit Price", dataIndex: "exitPrice", key: "exitPrice", width: 110, render: fmtMoney },
  { title: "P&L", dataIndex: "pnlAmount", key: "pnlAmount", width: 120, render: fmtMoney },
  { title: "P&L %", dataIndex: "pnlPct", key: "pnlPct", width: 90, render: fmtPct },
  { title: "Days Held", dataIndex: "daysHeld", key: "daysHeld", width: 100 },
];

export function SimpleBacktestPage() {
  const { data, loading, error } = useRsiMomentum();
  const [activeProfileId, setActiveProfileId] = useState<string | null>(null);

  const profiles = data?.profiles ?? [];

  useEffect(() => {
    if (profiles.length === 0) {
      setActiveProfileId(null);
      return;
    }
    if (!activeProfileId || !profiles.some((profile) => profile.profileId === activeProfileId)) {
      setActiveProfileId(profiles[0].profileId);
    }
  }, [profiles, activeProfileId]);

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
          <Typography.Title level={4} style={{ margin: 0 }}>Simple Backtest</Typography.Title>
          <Typography.Text type="secondary">
            Select tag, choose date range, prepare candles + momentum snapshots, then run Top-5/Top-10 simple momentum backtest.
          </Typography.Text>
        </div>

        <Tabs
          activeKey={activeProfileId ?? undefined}
          onChange={(key) => setActiveProfileId(key)}
          items={profiles.map((profile) => ({
            key: profile.profileId,
            label: profile.profileLabel || profile.config.profileLabel,
            children: <SimpleBacktestPanel profileId={profile.profileId} profileLabel={profile.profileLabel || profile.config.profileLabel} />,
          }))}
        />
      </Space>
    </div>
  );
}

function SimpleBacktestPanel({ profileId, profileLabel }: { profileId: string; profileLabel: string }) {
  const [range, setRange] = useState<DateRange>([dayjs().subtract(1, "year"), dayjs()]);
  const [capital, setCapital] = useState<number>(200000);
  const [entryRankMin, setEntryRankMin] = useState<number>(1);
  const [entryRankMax, setEntryRankMax] = useState<number>(5);
  const [holdRankMax, setHoldRankMax] = useState<number>(10);
  const [prepareLoading, setPrepareLoading] = useState(false);
  const [prepareError, setPrepareError] = useState<string | null>(null);
  const [prepareResult, setPrepareResult] = useState<SimpleMomentumPrepareResponse | null>(null);

  const {
    data: backtestResult,
    loading: backtestLoading,
    error: backtestError,
    run,
  } = useSimpleMomentumBacktest();

  const fromDate = range[0].format("YYYY-MM-DD");
  const toDate = range[1].format("YYYY-MM-DD");

  const updateEntryRankMin = (value: number | null): void => {
    const nextMin = Math.max(1, value ?? 1);
    const computedMax = Math.max(nextMin, entryRankMax);
    setEntryRankMin(nextMin);
    setEntryRankMax(computedMax);
    setHoldRankMax((prevHold) => Math.max(prevHold, computedMax));
  };

  const updateEntryRankMax = (value: number | null): void => {
    const nextMax = Math.max(entryRankMin, value ?? entryRankMin);
    setEntryRankMax(nextMax);
    setHoldRankMax((prevHold) => Math.max(prevHold, nextMax));
  };

  const updateHoldRankMax = (value: number | null): void => {
    const nextHold = Math.max(entryRankMax, value ?? entryRankMax);
    setHoldRankMax(nextHold);
  };

  const runPrepare = async (): Promise<void> => {
    setPrepareLoading(true);
    setPrepareError(null);
    setPrepareResult(null);

    const payload: SimpleMomentumPrepareRequest = { profileId, fromDate, toDate };

    try {
      const result = await postJson<SimpleMomentumPrepareResponse>("/api/strategy/rsi-momentum/backtest/simple/prepare", payload);
      setPrepareResult(result);
    } catch (error) {
      setPrepareError(error instanceof Error ? error.message : "Prepare data failed.");
    } finally {
      setPrepareLoading(false);
    }
  };

  const runBacktest = (): void => {
      void run({
        profileId,
        fromDate,
        toDate,
        initialCapital: capital,
        entryRankMin,
        entryRankMax,
        holdRankMax,
      });
  };

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <Card size="small" title={`Controls — ${profileLabel}`}>
        <Space wrap align="center">
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
          <InputNumber
            min={10000}
            step={10000}
            value={capital}
            onChange={(value) => setCapital(value || 200000)}
            addonAfter="INR"
          />

          <Typography.Text strong style={{ marginLeft: 12 }}>Entry Rank:</Typography.Text>
          <InputNumber min={1} value={entryRankMin} onChange={updateEntryRankMin} />
          <Typography.Text>-</Typography.Text>
          <InputNumber min={entryRankMin} value={entryRankMax} onChange={updateEntryRankMax} />

          <Typography.Text strong style={{ marginLeft: 12 }}>Sell If Rank &gt;</Typography.Text>
          <InputNumber min={entryRankMax} value={holdRankMax} onChange={updateHoldRankMax} />

          <Button onClick={() => void runPrepare()} loading={prepareLoading}>Prepare Data</Button>
          <Button type="primary" onClick={runBacktest} loading={backtestLoading}>Run Backtest</Button>
        </Space>
      </Card>

      {prepareError && <Alert type="error" showIcon message={prepareError} />}
      {backtestError && <Alert type="error" showIcon message={backtestError} />}

      {prepareResult && (
        <Card size="small" title="Prepare Summary">
          <Space size={24} wrap>
            <Statistic title="Symbols Targeted" value={prepareResult.symbolsTargeted} />
            <Statistic title="Symbols Synced" value={prepareResult.candleSync.symbolsSynced} />
            <Statistic title="Daily Candles Upserted" value={prepareResult.candleSync.dailyCandlesUpserted} />
            <Statistic title="Snapshot Dates Processed" value={prepareResult.snapshotBackfill.datesProcessed} />
            <Statistic title="Snapshot Dates Skipped" value={prepareResult.snapshotBackfill.datesSkipped} />
            <Statistic title="Snapshot Dates Failed" value={prepareResult.snapshotBackfill.datesFailed} />
          </Space>

          {prepareResult.warnings.length > 0 && (
            <Alert
              style={{ marginTop: 16 }}
              type="warning"
              showIcon
              message={prepareResult.warnings.join(" | ")}
            />
          )}
        </Card>
      )}

      {backtestResult && <SimpleBacktestResultPanel result={backtestResult} />}
    </Space>
  );
}

function SimpleBacktestResultPanel({ result }: { result: SimpleMomentumBacktestResult }) {
  const summary = result.summary;
  const snapshotCoverage = result.firstSnapshotDate && result.lastSnapshotDate
    ? `${result.firstSnapshotDate} to ${result.lastSnapshotDate}`
    : "No snapshots";

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <RowSummary
        summary={summary}
        entryRankMin={result.entryRankMin}
        entryRankMax={result.entryRankMax}
        holdRankMax={result.holdRankMax}
        drawdownGuardLookbackDays={result.drawdownGuardLookbackDays}
        drawdownGuardThresholdPct={result.drawdownGuardThresholdPct}
        trailingStopPct={result.trailingStopPct}
      />

      <Card
        size="small"
        title={`Simple Momentum Journal — Requested: ${result.fromDate} to ${result.toDate} · Snapshot coverage: ${snapshotCoverage} · ${result.snapshotDaysUsed} snapshot days`}
      >
        <Table
          columns={tradeColumns}
          dataSource={result.trades}
          rowKey={(row) => `${row.symbol}-${row.entryDate}`}
          size="small"
          pagination={{ pageSize: 25, showSizeChanger: true }}
          scroll={{ x: 1300 }}
        />
      </Card>
    </Space>
  );
}

function RowSummary({
  summary,
  entryRankMin,
  entryRankMax,
  holdRankMax,
  drawdownGuardLookbackDays,
  drawdownGuardThresholdPct,
  trailingStopPct,
}: {
  summary: SimpleMomentumBacktestResult["summary"];
  entryRankMin: number;
  entryRankMax: number;
  holdRankMax: number;
  drawdownGuardLookbackDays: number;
  drawdownGuardThresholdPct: number;
  trailingStopPct: number;
}) {
  return (
    <Space size={12} wrap>
      <Card size="small"><Statistic title="Final Capital" value={summary.finalCapital.toFixed(2)} prefix="₹" /></Card>
      <Card size="small"><Statistic title="Total Profit" value={summary.totalProfit.toFixed(2)} prefix="₹" valueStyle={{ color: summary.totalProfit >= 0 ? "#3f8600" : "#cf1322" }} /></Card>
      <Card size="small"><Statistic title="Return %" value={summary.totalProfitPct.toFixed(2)} suffix="%" valueStyle={{ color: summary.totalProfitPct >= 0 ? "#3f8600" : "#cf1322" }} /></Card>
      <Card size="small"><Statistic title="Total Trades" value={summary.totalTrades} /></Card>
      <Card size="small"><Statistic title="Closed Trades" value={summary.closedTrades} /></Card>
      <Card size="small"><Statistic title="Open Positions" value={summary.openPositions} /></Card>
      <Card size="small"><Statistic title="Entry Rank Band" value={`${entryRankMin}-${entryRankMax}`} /></Card>
      <Card size="small"><Statistic title="Sell If Rank >" value={holdRankMax} /></Card>
      <Card size="small"><Statistic title="Drawdown Guard" value={`>${drawdownGuardThresholdPct}% from ${drawdownGuardLookbackDays}D high`} /></Card>
      <Card size="small"><Statistic title="Trailing Stop" value={`${trailingStopPct}% from peak`} /></Card>
      <Card size="small"><Statistic title="Guard Skips" value={summary.entriesSkippedByDrawdownGuard} /></Card>
      <Card size="small"><Statistic title="Trailing Exits" value={summary.exitsByTrailingStop} /></Card>
    </Space>
  );
}
