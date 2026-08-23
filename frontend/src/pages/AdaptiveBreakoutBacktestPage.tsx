import { FileSearchOutlined } from "@ant-design/icons";
import { Alert, Button, Card, Col, Empty, InputNumber, Radio, Row, Select, Space, Spin, Statistic, Table, Tabs, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useMemo, useState } from "react";
import { InstrumentSearch } from "../components/InstrumentSearch";
import { useAdaptiveBreakoutBacktest } from "../hooks/useAdaptiveBreakoutBacktest";
import type {
  AdaptiveBreakoutBacktestExitReason,
  AdaptiveBreakoutBacktestSymbolReport,
  AdaptiveBreakoutBacktestTrade,
  AdaptiveBreakoutQualityFilter,
  InstrumentSearchResult,
  UniverseOptionsResponse,
} from "../types";
import { useInstrumentSearch } from "../hooks/useInstrumentSearch";
import { getJson } from "../utils/api";
import "./adaptiveBreakoutBacktest.css";

const { Text, Title } = Typography;
const WATCHLISTS_PATH = "/api/strategy/adaptive-breakout/watchlists";
type RunScope = "STOCK" | "WATCHLIST";

function formatPrice(value: number): string {
  return `₹${value.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function formatPercent(value: number | null): string {
  return value == null ? "—" : `${value >= 0 ? "+" : ""}${value.toFixed(2)}%`;
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat("en-IN", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    timeZone: "UTC",
  }).format(new Date(`${value}T00:00:00Z`));
}

function reasonLabel(trade: AdaptiveBreakoutBacktestTrade, targetPct: number, stopLossPct: number): string {
  if (trade.exitReason === "TARGET_HIT") return `Target +${targetPct}%`;
  if (trade.exitReason === "STOP_LOSS") return `Stop −${stopLossPct}%`;
  if (trade.exitReason === "STOP_LOSS_SAME_CANDLE") return `Stop −${stopLossPct}% (same candle)`;
  if (trade.exitReason === "SKIPPED_ENTRY_GAP") return `Skipped · entry gap ${formatPercent(trade.entryGapPct)}`;
  if (trade.exitReason === "FILTERED_BY_QUALITY") return "Filtered by quality";
  return "End of test";
}

function reasonColor(reason: AdaptiveBreakoutBacktestExitReason): string {
  if (reason === "TARGET_HIT") return "green";
  if (reason === "END_OF_TEST") return "blue";
  if (reason === "SKIPPED_ENTRY_GAP") return "orange";
  if (reason === "FILTERED_BY_QUALITY") return "orange";
  return "red";
}

function triggerLabel(trigger: "FRESH_BREAKOUT" | "CEILING_RECLAIM"): string {
  return trigger === "CEILING_RECLAIM" ? "Ceiling reclaim" : "Fresh breakout";
}

function qualityFilterLabel(filter: AdaptiveBreakoutQualityFilter): string {
  if (filter === "BASE_QUALITY") return "Base quality only";
  return "No quality filter";
}

export function AdaptiveBreakoutBacktestPage() {
  const [scope, setScope] = useState<RunScope>("STOCK");
  const [selectedInstrument, setSelectedInstrument] = useState<InstrumentSearchResult | null>(null);
  const [watchlists, setWatchlists] = useState<UniverseOptionsResponse["options"]>([]);
  const [watchlistKey, setWatchlistKey] = useState<string>();
  const [watchlistsLoading, setWatchlistsLoading] = useState(false);
  const [watchlistError, setWatchlistError] = useState<string | null>(null);
  const [targetPct, setTargetPct] = useState(5);
  const [stopLossPct, setStopLossPct] = useState(5);
  const [months, setMonths] = useState(6);
  const [entryTrigger, setEntryTrigger] = useState<"FRESH_BREAKOUT" | "CEILING_RECLAIM">("FRESH_BREAKOUT");
  const [qualityFilter, setQualityFilter] = useState<AdaptiveBreakoutQualityFilter>("NONE");
  const { allInstruments, loading: instrumentsLoading, error: instrumentsError } = useInstrumentSearch();
  const { data, loading, error, run } = useAdaptiveBreakoutBacktest();
  const nseEquities = useMemo(
    () => allInstruments.filter((instrument) => instrument.exchange === "NSE" && instrument.instrument_type === "EQ"),
    [allInstruments],
  );

  useEffect(() => {
    if (scope !== "WATCHLIST" || watchlists.length > 0 || watchlistError != null) return;
    setWatchlistsLoading(true);
    void getJson<UniverseOptionsResponse>(WATCHLISTS_PATH)
      .then((response) => {
        setWatchlists(response.options);
        setWatchlistError(null);
      })
      .catch((cause) => setWatchlistError(cause instanceof Error ? cause.message : "Unable to load watchlists."))
      .finally(() => setWatchlistsLoading(false));
  }, [scope, watchlistError, watchlists.length]);

  const runBacktest = (): void => {
    if (scope === "WATCHLIST") {
      if (!watchlistKey) return;
      void run({ watchlistKey, months, targetPct, stopLossPct, entryTrigger, qualityFilter });
      return;
    }
    if (!selectedInstrument) return;
    void run({
      symbol: selectedInstrument.trading_symbol,
      instrumentToken: selectedInstrument.instrument_token,
      months,
      targetPct,
      stopLossPct,
      entryTrigger,
      qualityFilter,
    });
  };

  const columns: ColumnsType<AdaptiveBreakoutBacktestTrade> = [
    { title: "Stock", dataIndex: "symbol", key: "symbol", fixed: "left" },
    { title: `${triggerLabel(entryTrigger)} date`, dataIndex: "breakoutDate", key: "breakoutDate", render: formatDate },
    { title: "Breakout close", dataIndex: "breakoutClose", key: "breakoutClose", render: formatPrice },
    { title: "Entry (later open)", dataIndex: "entryDate", key: "entryDate", render: (_: string | null, row) => row.entryDate && row.entryPrice != null ? <span>{formatDate(row.entryDate)} · {formatPrice(row.entryPrice)}</span> : "—" },
    { title: "Target", dataIndex: "targetPrice", key: "targetPrice", render: (val: number | null) => val != null ? formatPrice(val) : "—" },
    { title: "Stop", dataIndex: "stopPrice", key: "stopPrice", render: (val: number | null) => val != null ? formatPrice(val) : "—" },
    { title: "Exit", dataIndex: "exitDate", key: "exitDate", render: (_: string | null, row) => row.exitDate && row.exitPrice != null ? <span>{formatDate(row.exitDate)} · {formatPrice(row.exitPrice)}</span> : "—" },
    {
      title: "Result",
      key: "result",
      render: (_, row) => <Space size={5}><Tag color={reasonColor(row.exitReason)}>{reasonLabel(row, data?.targetPct ?? targetPct, data?.stopLossPct ?? stopLossPct)}</Tag>{row.gapThroughExit && <Text type="secondary">gap fill</Text>}{row.ambiguousSameCandle && <Text type="secondary">OHLC order unknown</Text>}</Space>,
    },
    { title: "Held", dataIndex: "holdingSessions", key: "holdingSessions", render: (value: number | null) => value != null ? `${value} day${value === 1 ? "" : "s"}` : "—" },
    { title: "Net return", dataIndex: "returnPct", key: "returnPct", render: (value: number | null) => value != null ? <Text type={value >= 0 ? "success" : "danger"}>{formatPercent(value)}</Text> : "—" },
    {
      title: "Review",
      key: "review",
      render: (_, row) => (
        <Button
          type="text"
          icon={<FileSearchOutlined />}
          href={`${import.meta.env.BASE_URL}console/breakout-buy-review?symbol=${encodeURIComponent(row.symbol)}&date=${row.breakoutDate}`}
          target="_blank"
          aria-label="Open buy review"
        />
      ),
    },
  ];

  const stockColumns: ColumnsType<AdaptiveBreakoutBacktestSymbolReport> = [
    {
      title: "Stock",
      key: "symbol",
      fixed: "left",
      render: (_, row) => (
        <Space orientation="vertical" size={0}>
          <Text strong>{row.symbol}</Text>
          {row.companyName && <Text type="secondary">{row.companyName}</Text>}
        </Space>
      ),
    },
    { title: "Signals", key: "signals", sorter: (a, b) => a.summary.signalCount - b.summary.signalCount, render: (_, row) => row.summary.signalCount },
    { title: "Entered", key: "entered", sorter: (a, b) => a.summary.enteredTradeCount - b.summary.enteredTradeCount, render: (_, row) => row.summary.enteredTradeCount },
    { title: "Targets", key: "targets", sorter: (a, b) => a.summary.targetHitCount - b.summary.targetHitCount, render: (_, row) => <Text type="success">{row.summary.targetHitCount}</Text> },
    { title: "Stops", key: "stops", sorter: (a, b) => a.summary.stopLossCount - b.summary.stopLossCount, render: (_, row) => <Text type="danger">{row.summary.stopLossCount}</Text> },
    { title: "Gap skips", key: "gapSkips", sorter: (a, b) => a.summary.skippedEntryGapCount - b.summary.skippedEntryGapCount, render: (_, row) => row.summary.skippedEntryGapCount },
    { title: "Win rate", key: "winRate", sorter: (a, b) => (a.summary.winRatePct ?? -1) - (b.summary.winRatePct ?? -1), render: (_, row) => formatPercent(row.summary.winRatePct) },
    {
      title: "Expectancy",
      key: "expectancy",
      defaultSortOrder: "ascend",
      sorter: (a, b) => (a.summary.expectancyPct ?? Number.NEGATIVE_INFINITY) - (b.summary.expectancyPct ?? Number.NEGATIVE_INFINITY),
      render: (_, row) => <Text type={(row.summary.expectancyPct ?? 0) >= 0 ? "success" : "danger"}>{formatPercent(row.summary.expectancyPct)}</Text>,
    },
    { title: "Profit factor", key: "profitFactor", sorter: (a, b) => (a.summary.profitFactor ?? -1) - (b.summary.profitFactor ?? -1), render: (_, row) => row.summary.profitFactor == null ? "—" : row.summary.profitFactor.toFixed(2) },
    { title: "End of test", key: "endOfTest", sorter: (a, b) => a.summary.endOfTestCount - b.summary.endOfTestCount, render: (_, row) => row.summary.endOfTestCount },
  ];

  const renderTradeTable = (trades: AdaptiveBreakoutBacktestTrade[]): JSX.Element => (
    <Table
      rowKey={(row) => `${row.symbol}-${row.breakoutDate}-${row.entryDate}`}
      columns={columns}
      dataSource={trades}
      pagination={{ pageSize: 30 }}
      size="small"
      scroll={{ x: 1200 }}
    />
  );

  return (
    <div className="adaptive-breakout-backtest-page">
      <Card className="adaptive-breakout-backtest-control">
        <Space orientation="vertical" size={10} style={{ width: "100%" }}>
          <div>
            <Title level={3} style={{ margin: 0 }}>Adaptive Breakout Backtest · {months}-Month Test</Title>
            <Text type="secondary">Replay each selected {triggerLabel(entryTrigger).toLowerCase()} and enter at the first later tradable open.</Text>
          </div>
          <Space wrap style={{ width: "100%" }}>
            <Radio.Group aria-label="Run scope" buttonStyle="solid" value={scope} onChange={(event) => setScope(event.target.value as RunScope)} options={[{ label: "Single stock", value: "STOCK" }, { label: "Watchlist", value: "WATCHLIST" }]} />
            {scope === "STOCK" ? (
              <div className="adaptive-breakout-backtest-search">
                {instrumentsLoading ? <Spin size="small" /> : <InstrumentSearch
                  instruments={nseEquities}
                  value={selectedInstrument}
                  onSelect={setSelectedInstrument}
                  placeholder="Select an NSE stock"
                />}
                {instrumentsError && <Text type="danger">{instrumentsError}</Text>}
              </div>
            ) : (
              <div className="adaptive-breakout-backtest-watchlist">
                <Select
                  aria-label="Watchlist"
                  value={watchlistKey}
                  onChange={setWatchlistKey}
                  placeholder="Select a watchlist"
                  options={watchlists.map((watchlist) => ({ value: watchlist.value, label: `${watchlist.label} (${watchlist.count})` }))}
                  loading={watchlistsLoading}
                  style={{ width: 280 }}
                />
                {watchlistError && <Text type="danger">{watchlistError}</Text>}
              </div>
            )}
            <label>
              <Text strong>History</Text>
              <InputNumber
                aria-label="Backtest history months"
                min={1}
                max={120}
                step={1}
                precision={0}
                value={months}
                onChange={(value) => value != null && setMonths(value)}
                style={{ width: 100 }}
              />
              <Text type="secondary">months</Text>
            </label>
            <label>
              <Text strong>Target %</Text>
              <InputNumber
                aria-label="Target percentage"
                min={0.1}
                max={100}
                step={0.5}
                precision={2}
                value={targetPct}
                onChange={(value) => value != null && setTargetPct(value)}
                style={{ width: 110 }}
              />
              <Text type="secondary">%</Text>
            </label>
            <label>
              <Text strong style={{ marginRight: 8 }}>Quality</Text>
              <Select
                aria-label="Quality filter"
                value={qualityFilter}
                onChange={setQualityFilter}
                options={(["NONE", "BASE_QUALITY"] as AdaptiveBreakoutQualityFilter[]).map((value) => ({ value, label: qualityFilterLabel(value) }))}
                style={{ width: 180 }}
              />
            </label>
            <label>
              <Text strong>Stop loss %</Text>
              <InputNumber
                aria-label="Stop loss percentage"
                min={0.1}
                max={100}
                step={0.5}
                precision={2}
                value={stopLossPct}
                onChange={(value) => value != null && setStopLossPct(value)}
                style={{ width: 110 }}
              />
              <Text type="secondary">%</Text>
            </label>
            <label>
              <Text strong style={{ marginRight: 8 }}>Entry on</Text>
              <Select
                value={entryTrigger}
                onChange={setEntryTrigger}
                options={[
                  { value: "FRESH_BREAKOUT", label: "Fresh Breakout" },
                  { value: "CEILING_RECLAIM", label: "Ceiling Reclaim" },
                ]}
                style={{ width: 140 }}
              />
            </label>
            <Button type="primary" onClick={runBacktest} loading={loading} disabled={scope === "WATCHLIST" ? !watchlistKey : !selectedInstrument}>
              Run {scope === "WATCHLIST" ? watchlistKey ?? "watchlist" : selectedInstrument?.trading_symbol ?? "stock"} test
            </Button>
          </Space>
        </Space>
      </Card>

      {error && <Alert type="error" showIcon message={error} />}
      {!data && !loading && !error && <Empty description={scope === "WATCHLIST" ? "Select a watchlist to see its selected-signal trades." : "Select a stock to see its selected-signal trades."} />}
      {data && (
        <Space orientation="vertical" size={12} style={{ width: "100%" }}>
          <Card title={`${data.watchlistKey ?? data.symbol ?? "Backtest"} · ${formatDate(data.testedFromDate)} to ${formatDate(data.testedToDate)}`}>
            <Row gutter={[12, 12]}>
              {data.watchlistKey && <Metric title="Stocks tested" value={data.symbols.length} />}
              <Metric title={`${triggerLabel(data.entryTrigger)} signals`} value={data.summary.signalCount} />
              <Metric title="Gap skips" value={data.summary.skippedEntryGapCount} />
              <Metric title="Quality filtered" value={data.summary.qualityFilteredCount ?? 0} />
              <Metric title="Trades entered" value={data.summary.enteredTradeCount} />
              <Metric title="Targets hit" value={data.summary.targetHitCount} />
              <Metric title="Stops hit" value={data.summary.stopLossCount} />
              <Metric title="Win rate" value={data.summary.winRatePct == null ? "—" : `${data.summary.winRatePct.toFixed(1)}%`} />
              <Metric title="Expectancy" value={data.summary.expectancyPct == null ? "—" : formatPercent(data.summary.expectancyPct)} />
              <Metric title="Avg win" value={data.summary.averageWinningReturnPct == null ? "—" : formatPercent(data.summary.averageWinningReturnPct)} />
              <Metric title="Avg loss" value={data.summary.averageLosingReturnPct == null ? "—" : formatPercent(data.summary.averageLosingReturnPct)} />
              <Metric title="Payoff" value={data.summary.payoffRatio == null ? "—" : `${data.summary.payoffRatio.toFixed(2)}×`} />
              <Metric title="Profit factor" value={data.summary.profitFactor == null ? "—" : data.summary.profitFactor.toFixed(2)} />
              <Metric title="Average hold" value={data.summary.averageHoldingSessions == null ? "—" : `${data.summary.averageHoldingSessions.toFixed(1)} days`} />
            </Row>
          </Card>
          <Card title="Backtest inspection" extra={<Text type="secondary">Entry is the first later tradable open</Text>}>
            <Alert className="adaptive-breakout-backtest-note" type="info" showIcon message={`${triggerLabel(data.entryTrigger)} · target +${data.targetPct}% · stop −${data.stopLossPct}% · first later tradable open`} description={data.ambiguousCandleRule} />
            <Tabs
              defaultActiveKey="stock-wise"
              items={[
                {
                  key: "stock-wise",
                  label: `Stock-wise results (${data.symbols.length})`,
                  children: data.symbols.length === 0 ? <Empty description="No stock-level reports are available." /> : (
                    <Table
                      rowKey={(row) => row.symbol}
                      columns={stockColumns}
                      dataSource={data.symbols}
                      pagination={{ pageSize: 30 }}
                      size="small"
                      scroll={{ x: 1100 }}
                      expandable={{
                        expandedRowRender: (row) => row.trades.length === 0 ? <Empty description="No trades for this stock." /> : renderTradeTable(row.trades),
                        rowExpandable: (row) => row.trades.length > 0,
                      }}
                    />
                  ),
                },
                {
                  key: "all-trades",
                  label: `All trades (${data.trades.length})`,
                  children: data.trades.length === 0 ? <Empty description={`No selected signal produced an entry in this ${months}-month window.`} /> : renderTradeTable(data.trades),
                },
              ]}
            />
          </Card>
        </Space>
      )}
    </div>
  );
}

function Metric({ title, value }: { title: string; value: number | string }) {
  return <Col xs={12} sm={8} lg={4}><Statistic title={title} value={value} /></Col>;
}
