import { ReloadOutlined } from "@ant-design/icons";
import { Alert, Button, Card, Empty, InputNumber, Select, Space, Spin, Table, Tabs, Tag, Typography } from "antd";
import type { TableColumnsType } from "antd";
import { useCallback, useEffect, useState } from "react";
import type { Rule5ApiResponse, Rule5BacktestResponse, Rule5BacktestSignal, Rule5BacktestTrade, Rule5BreakoutDay, Rule5SymbolResult, UniverseOption, UniverseOptionsResponse } from "../types";
import { getJson } from "../utils/api";

const { Text, Title } = Typography;
const BREAKOUT_PERIOD_OPTIONS = [20, 40, 60, 100, 200].map((period) => ({
  value: period,
  label: `${period}D`,
}));

function formatPrice(value: number): string {
  return `₹${value.toLocaleString("en-IN", { maximumFractionDigits: 2 })}`;
}

function formatPercent(value: number): string {
  return `${value >= 0 ? "+" : ""}${value.toFixed(2)}%`;
}

function buildKiteChartUrl(symbol: string, instrumentToken: number): string {
  return `https://kite.zerodha.com/chart/web/tvc/NSE/${encodeURIComponent(symbol)}/${instrumentToken}`;
}

const breakoutColumns: TableColumnsType<Rule5BreakoutDay> = [
  { title: "Date", dataIndex: "date", key: "date" },
  { title: "Day high", dataIndex: "high", key: "high", render: (value: number) => formatPrice(value) },
  { title: "Close", dataIndex: "close", key: "close", render: (value: number) => formatPrice(value) },
  { title: "Prior period high", dataIndex: "referenceHigh", key: "referenceHigh", render: (value: number) => formatPrice(value) },
  { title: "Prior high days ago", dataIndex: "referenceHighDaysAgo", key: "referenceHighDaysAgo", render: (value: number) => `${value} trading days` },
  { title: "Close vs prior high", dataIndex: "closeVsReferenceHighPct", key: "closeVsReferenceHighPct", render: (value: number) => formatPercent(value) },
];

const symbolColumns: TableColumnsType<Rule5SymbolResult> = [
  { title: "Symbol", dataIndex: "symbol", key: "symbol", sorter: (left, right) => left.symbol.localeCompare(right.symbol), render: (value: string) => <Text strong>{value}</Text> },
  { title: "Company", dataIndex: "companyName", key: "companyName" },
  {
    title: "Watchlists",
    dataIndex: "watchlists",
    key: "watchlists",
    render: (watchlists: string[]) => <Space wrap size={[4, 4]}>{watchlists.map((watchlist) => <Tag key={watchlist}>{watchlist}</Tag>)}</Space>,
  },
  { title: "Latest fresh breakout", dataIndex: "latestBreakoutDate", key: "latestBreakoutDate", sorter: (left, right) => left.latestBreakoutDate.localeCompare(right.latestBreakoutDate) },
  { title: "Day high", dataIndex: "latestHigh", key: "latestHigh", sorter: (left, right) => left.latestHigh - right.latestHigh, render: (value: number) => formatPrice(value) },
  { title: "Close", dataIndex: "latestClose", key: "latestClose", sorter: (left, right) => left.latestClose - right.latestClose, render: (value: number) => formatPrice(value) },
  { title: "Prior period high", dataIndex: "latestReferenceHigh", key: "latestReferenceHigh", sorter: (left, right) => left.latestReferenceHigh - right.latestReferenceHigh, render: (value: number) => formatPrice(value) },
  { title: "Prior high days ago", dataIndex: "latestReferenceHighDaysAgo", key: "latestReferenceHighDaysAgo", sorter: (left, right) => left.latestReferenceHighDaysAgo - right.latestReferenceHighDaysAgo, render: (value: number) => `${value} trading days` },
  { title: "Close vs prior high", dataIndex: "latestCloseVsReferenceHighPct", key: "latestCloseVsReferenceHighPct", sorter: (left, right) => left.latestCloseVsReferenceHighPct - right.latestCloseVsReferenceHighPct, render: (value: number) => formatPercent(value) },
];

const backtestSignalColumns: TableColumnsType<Rule5BacktestSignal> = [
  { title: "Symbol", dataIndex: "symbol", key: "symbol", sorter: (left, right) => left.symbol.localeCompare(right.symbol), render: (value: string) => <Text strong>{value}</Text> },
  { title: "Signal date", dataIndex: "signalDate", key: "signalDate" },
  { title: "Breakout high", dataIndex: "breakoutHigh", key: "breakoutHigh", render: (value: number) => formatPrice(value) },
  { title: "Breakout close", dataIndex: "breakoutClose", key: "breakoutClose", render: (value: number) => formatPrice(value) },
  { title: "Prior period high", dataIndex: "referenceHigh", key: "referenceHigh", render: (value: number) => formatPrice(value) },
  { title: "Prior high days ago", dataIndex: "referenceHighDaysAgo", key: "referenceHighDaysAgo", render: (value: number) => `${value} trading days` },
  { title: "Close vs prior high", dataIndex: "closeVsReferenceHighPct", key: "closeVsReferenceHighPct", render: (value: number) => formatPercent(value) },
  { title: "Outcome", dataIndex: "outcome", key: "outcome", render: (value: string) => <Tag color={value === "ENTERED" ? "green" : "orange"}>{value}</Tag> },
  { title: "Entry", dataIndex: "entryPrice", key: "entryPrice", render: (value: number | null) => value == null ? "-" : formatPrice(value) },
  { title: "Target", dataIndex: "targetPrice", key: "targetPrice", render: (value: number | null) => value == null ? "-" : formatPrice(value) },
  { title: "Trade status", dataIndex: "tradeStatus", key: "tradeStatus", render: (value: string | null) => value == null ? "-" : <Tag>{value}</Tag> },
];

const backtestTradeColumns: TableColumnsType<Rule5BacktestTrade> = [
  {
    title: "Symbol",
    dataIndex: "symbol",
    key: "symbol",
    sorter: (left, right) => left.symbol.localeCompare(right.symbol),
    render: (value: string, row) => <a aria-label={`Open ${value} in Kite`} href={buildKiteChartUrl(value, row.instrumentToken)} target="_blank" rel="noopener noreferrer"><Text strong>{value}</Text></a>,
  },
  { title: "Entry date", dataIndex: "entryDate", key: "entryDate" },
  { title: "Entry price", dataIndex: "entryPrice", key: "entryPrice", render: (value: number) => formatPrice(value) },
  { title: "LTP", dataIndex: "latestPrice", key: "latestPrice", render: (value: number) => formatPrice(value) },
  { title: "% from entry", dataIndex: "changeFromEntryPct", key: "changeFromEntryPct", render: (value: number) => formatPercent(value) },
  { title: "Target", dataIndex: "targetPrice", key: "targetPrice", render: (value: number) => formatPrice(value) },
  { title: "Exit date", dataIndex: "exitDate", key: "exitDate", render: (value: string | null) => value ?? "Open" },
  { title: "Exit price", dataIndex: "exitPrice", key: "exitPrice", render: (value: number | null) => value == null ? "-" : formatPrice(value) },
  { title: "Status", dataIndex: "status", key: "status", render: (value: string) => <Tag color={value === "TARGET_HIT" ? "green" : "blue"}>{value}</Tag> },
  { title: "Holding days", dataIndex: "holdingTradingDays", key: "holdingTradingDays", sorter: (left, right) => left.holdingTradingDays - right.holdingTradingDays },
];

export function FiftyTwoWeekMomentumRule5Page() {
  const [options, setOptions] = useState<UniverseOption[]>([]);
  const [selectedWatchlists, setSelectedWatchlists] = useState<string[]>([]);
  const [breakoutPeriodSessions, setBreakoutPeriodSessions] = useState(200);
  const [nearHighTolerancePct, setNearHighTolerancePct] = useState(2);
  const [targetPct, setTargetPct] = useState(10);
  const [report, setReport] = useState<Rule5ApiResponse | null>(null);
  const [backtestReport, setBacktestReport] = useState<Rule5BacktestResponse | null>(null);
  const [loadingOptions, setLoadingOptions] = useState(true);
  const [loadingScan, setLoadingScan] = useState(false);
  const [loadingBacktest, setLoadingBacktest] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadScan = useCallback((): void => {
    if (selectedWatchlists.length === 0) {
      setReport(null);
      return;
    }

    setLoadingScan(true);
    setError(null);
    const watchlistQuery = encodeURIComponent(selectedWatchlists.join(","));
    void getJson<Rule5ApiResponse>(
      `/api/strategy/52w-momentum/rule5/scan?watchlists=${watchlistQuery}&breakoutPeriodSessions=${breakoutPeriodSessions}&nearHighTolerancePct=${nearHighTolerancePct}`,
      { useCache: false },
    )
      .then(setReport)
      .catch((requestError: unknown) => {
        setError(requestError instanceof Error ? requestError.message : "Failed to scan the selected watchlists.");
      })
      .finally(() => setLoadingScan(false));
  }, [breakoutPeriodSessions, nearHighTolerancePct, selectedWatchlists]);

  const runBacktest = useCallback((): void => {
    if (selectedWatchlists.length === 0) {
      setBacktestReport(null);
      return;
    }

    setLoadingBacktest(true);
    setError(null);
    const watchlistQuery = encodeURIComponent(selectedWatchlists.join(","));
    void getJson<Rule5BacktestResponse>(
      `/api/strategy/52w-momentum/rule5/backtest?watchlists=${watchlistQuery}&breakoutPeriodSessions=${breakoutPeriodSessions}&nearHighTolerancePct=${nearHighTolerancePct}&targetPct=${targetPct}`,
      { useCache: false },
    )
      .then(setBacktestReport)
      .catch((requestError: unknown) => {
        setError(requestError instanceof Error ? requestError.message : "Failed to run the breakout backtest.");
      })
      .finally(() => setLoadingBacktest(false));
  }, [breakoutPeriodSessions, nearHighTolerancePct, selectedWatchlists, targetPct]);

  useEffect(() => {
    let active = true;
    void getJson<UniverseOptionsResponse>("/api/strategy/52w-momentum/rule5/watchlists")
      .then((response) => {
        if (active) setOptions(response.options);
      })
      .catch((requestError: unknown) => {
        if (active) setError(requestError instanceof Error ? requestError.message : "Failed to load watchlists.");
      })
      .finally(() => {
        if (active) setLoadingOptions(false);
      });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    if (selectedWatchlists.length === 0) setBacktestReport(null);
    loadScan();
  }, [loadScan, selectedWatchlists.length]);

  return (
    <div style={{ padding: "24px 24px 160px" }}>
      <Space orientation="vertical" size={16} style={{ width: "100%" }}>
        <Card
          title={<Title level={3} style={{ margin: 0 }}>52W Momentum · Rule 5</Title>}
          extra={<Button icon={<ReloadOutlined />} onClick={loadScan} loading={loadingScan} disabled={selectedWatchlists.length === 0}>Reload</Button>}
        >
          <Space orientation="vertical" size={8} style={{ width: "100%" }}>
            <Text type="secondary">
              Select watchlists, the lookback period, and how far below the prior period high the closing price may be. For example, a ₹100 prior high with a 2% tolerance accepts a ₹98 close.
            </Text>
            <Select
              aria-label="Watchlists"
              mode="multiple"
              loading={loadingOptions}
              value={selectedWatchlists}
              onChange={setSelectedWatchlists}
              placeholder="Select one or more watchlists"
              maxTagCount="responsive"
              style={{ width: 520, maxWidth: "100%" }}
              options={options.map((option) => ({ value: option.value, label: `${option.label} (${option.count})` }))}
            />
            <Select
              aria-label="Breakout period"
              value={breakoutPeriodSessions}
              onChange={setBreakoutPeriodSessions}
              options={BREAKOUT_PERIOD_OPTIONS}
              style={{ width: 180 }}
            />
            <Space wrap size={8}>
              <Text>Allow below prior high</Text>
              <InputNumber
                aria-label="Near-high tolerance percentage"
                min={0}
                max={25}
                precision={2}
                value={nearHighTolerancePct}
                onChange={(value) => setNearHighTolerancePct(value ?? 0)}
              />
              <Text>%</Text>
            </Space>
            {report && <Text type="secondary" style={{ fontSize: 12 }}>
              {report.breakoutPeriodSessions}D prior-high proximity · up to {report.nearHighTolerancePct}% below high · latest {report.lookbackSessions} trading sessions through {report.requestedAsOfDate} · scanned {report.scannedCount} stocks · {report.breakoutStockCount} signals
            </Text>}
          </Space>
        </Card>

        {error && <Alert type="error" message={error} showIcon />}
        {loadingScan && <Spin />}
        {!loadingScan && selectedWatchlists.length === 0 && !loadingOptions && <Empty description="Select one or more watchlists to find recent fresh breakouts." />}
        {!loadingScan && report && report.results.length === 0 && <Empty description={`No stocks entered the ${breakoutPeriodSessions}D near-high band in the latest five trading sessions.`} />}
        {!loadingScan && report && report.results.length > 0 && (
          <Table<Rule5SymbolResult>
            dataSource={report.results}
            columns={symbolColumns}
            rowKey="symbol"
            pagination={{ pageSize: 100 }}
            size="middle"
            bordered
            expandable={{
              expandedRowRender: (record) => <Table<Rule5BreakoutDay> dataSource={record.freshBreakoutDays} columns={breakoutColumns} rowKey="date" pagination={false} size="small" />,
            }}
          />
        )}

        <Card
          title="Six-month breakout backtest"
          extra={<Button type="primary" onClick={runBacktest} loading={loadingBacktest} disabled={selectedWatchlists.length === 0}>Run backtest</Button>}
        >
          <Text type="secondary">
            Entry is the signal-day close. The signal is the first close entering the configured band below the preceding period high. Exit is the first later trading day whose high reaches the selected target; trades that do not reach the target remain open through the backtest end date.
            </Text>
            <Space wrap>
              <Text>Target</Text>
              <InputNumber
                aria-label="Backtest target percentage"
                min={0.1}
                max={100}
                precision={2}
                value={targetPct}
                onChange={(value) => setTargetPct(value ?? 10)}
              />
              <Text>%</Text>
            </Space>
          {backtestReport && <Space orientation="vertical" size={12} style={{ width: "100%", marginTop: 16 }}>
            <Text type="secondary">
              {backtestReport.periodStartDate} to {backtestReport.requestedAsOfDate} · {backtestReport.breakoutPeriodSessions}D · up to {backtestReport.nearHighTolerancePct}% below high · target {backtestReport.targetPct}% · {backtestReport.signalCount} signals · {backtestReport.enteredTradeCount} entered trades · {backtestReport.targetHitCount} targets hit · {backtestReport.openTradeCount} open
            </Text>
            <Tabs items={[
              {
                key: "details",
                label: "Detailed results",
                children: <Table<Rule5BacktestSignal> dataSource={backtestReport.signals} columns={backtestSignalColumns} rowKey={(row) => `${row.symbol}-${row.signalDate}-${row.outcome}`} pagination={{ pageSize: 50 }} size="small" bordered />,
              },
              {
                key: "trades",
                label: "Entered trades",
                children: <Table<Rule5BacktestTrade> dataSource={backtestReport.trades} columns={backtestTradeColumns} rowKey={(row) => `${row.symbol}-${row.entryDate}`} pagination={{ pageSize: 50 }} size="small" bordered />,
              },
            ]} />
          </Space>}
        </Card>
      </Space>
    </div>
  );
}
