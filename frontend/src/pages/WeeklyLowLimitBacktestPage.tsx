import { Alert, Button, Card, Col, Radio, Row, Select, Space, Spin, Statistic, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useMemo, useState } from "react";
import { InstrumentSearch } from "../components/InstrumentSearch";
import { useInstrumentSearch } from "../hooks/useInstrumentSearch";
import { useWeeklyLowLimitBacktest } from "../hooks/useWeeklyLowLimitBacktest";
import type {
  InstrumentSearchResult,
  UniverseOptionsResponse,
  WeeklyLowLimitBacktestEntryRule,
  WeeklyLowLimitBacktestMode,
  WeeklyLowLimitBacktestTrade,
} from "../types";
import { getJson } from "../utils/api";

const { Text, Title } = Typography;
const WATCHLISTS_PATH = "/api/strategy/weekly-price-review/watchlists";

function formatNumber(value: number | null): string {
  return value == null ? "-" : `₹${value.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function formatPercent(value: number | null): string {
  return value == null ? "-" : `${value.toFixed(2)}%`;
}

function formatDateWithDay(value: string): string {
  const [year, month, day] = value.split("-").map(Number);
  const date = new Date(year, month - 1, day);
  const weekday = date.toLocaleDateString("en-IN", { weekday: "short" });
  return `${weekday}, ${value}`;
}

function outcomeColor(outcome: string): string {
  if (outcome === "TARGET_HIT") return "#389e0d";
  if (outcome === "STOP_LOSS") return "#cf1322";
  if (outcome === "PREMARKET_FILTER_SKIP") return "#d48806";
  if (outcome === "OPEN_DEVIATION_SKIP") return "#d48806";
  return "#595959";
}

function outcomeLabel(outcome: string): string {
  if (outcome === "PREMARKET_FILTER_SKIP") return "Skipped · prior close below limit";
  if (outcome === "OPEN_DEVIATION_SKIP") return "Skipped · open moved over 1%";
  return outcome;
}

function entryRuleLabel(entryRule: string): string {
  return entryRule === "FIRST_3_DAYS_WEEK_CLOSE" ? "Mon–Wed entry · Friday exit" : "Any-day entry · max 5 trading days";
}

export function WeeklyLowLimitBacktestPage() {
  const [mode, setMode] = useState<WeeklyLowLimitBacktestMode>("STOCK");
  const [entryRule, setEntryRule] = useState<WeeklyLowLimitBacktestEntryRule>("ANY_DAY_MAX_5_TRADING_DAYS");
  const [selectedInstrument, setSelectedInstrument] = useState<InstrumentSearchResult | null>(null);
  const [watchlists, setWatchlists] = useState<UniverseOptionsResponse["options"]>([]);
  const [watchlistKey, setWatchlistKey] = useState<string>();
  const [watchlistError, setWatchlistError] = useState<string | null>(null);
  const { allInstruments, loading: instrumentsLoading, error: instrumentsError } = useInstrumentSearch();
  const { data, loading, error, run } = useWeeklyLowLimitBacktest();
  const nseEquities = useMemo(
    () => allInstruments.filter((instrument) => instrument.exchange === "NSE" && instrument.instrument_type === "EQ"),
    [allInstruments],
  );

  useEffect(() => {
    void getJson<UniverseOptionsResponse>(WATCHLISTS_PATH)
      .then((response) => {
        setWatchlists(response.options);
        setWatchlistError(null);
      })
      .catch((cause) => setWatchlistError(cause instanceof Error ? cause.message : "Unable to load watchlists."));
  }, []);

  const columns: ColumnsType<WeeklyLowLimitBacktestTrade> = [
    { title: "Symbol", dataIndex: "symbol", key: "symbol", fixed: "left" },
    { title: "Entry week", dataIndex: "entryWeekStartDate", key: "entryWeekStartDate" },
    { title: "Previous week's low", dataIndex: "previousWeekLow", key: "previousWeekLow", render: formatNumber },
    { title: "Previous week's low date", dataIndex: "previousWeekLowDate", key: "previousWeekLowDate", render: formatDateWithDay },
    { title: "Previous week's close", dataIndex: "previousWeekLastClose", key: "previousWeekLastClose", render: formatNumber },
    { title: "Limit", dataIndex: "limitPrice", key: "limitPrice", render: formatNumber },
    { title: "Outcome", dataIndex: "outcome", key: "outcome", render: (value: string) => <Text style={{ color: outcomeColor(value) }}>{outcomeLabel(value)}</Text> },
    { title: "Open deviation", dataIndex: "entryOpenDeviationPct", key: "entryOpenDeviationPct", render: formatPercent },
    { title: "Entry", key: "entry", render: (_, row) => row.entryDate == null ? "-" : `${formatDateWithDay(row.entryDate)} @ ${formatNumber(row.entryPrice)}` },
    { title: "Stop", dataIndex: "stopPrice", key: "stopPrice", render: formatNumber },
    { title: "Target", dataIndex: "targetPrice", key: "targetPrice", render: formatNumber },
    { title: "Exit", key: "exit", render: (_, row) => row.exitDate == null ? "-" : `${formatDateWithDay(row.exitDate)} @ ${formatNumber(row.exitPrice)}` },
    { title: "Hold", dataIndex: "holdingTradingDays", key: "holdingTradingDays", render: (value: number | null) => value == null ? "-" : `${value}d` },
    { title: "Return", dataIndex: "returnPct", key: "returnPct", render: formatPercent },
    { title: "Flags", key: "flags", render: (_, row) => [row.gapFill && "Gap fill", row.exitWasAmbiguous && "Ambiguous"].filter(Boolean).join(", ") || "-" },
    { title: "Validation", key: "validation", render: (_, row) => <Button size="small" onClick={() => openDailyValidation(row)}>Validate daily path</Button> },
  ];

  const openDailyValidation = (trade: WeeklyLowLimitBacktestTrade): void => {
    const params = new URLSearchParams({
      symbol: trade.symbol,
      instrumentToken: String(trade.instrumentToken),
      previousWeekLowDate: trade.previousWeekLowDate,
      entryWeekStartDate: trade.entryWeekStartDate,
      ...(trade.entryDate ? { entryDate: trade.entryDate } : {}),
    });
    window.open(`${import.meta.env.BASE_URL}console/weekly-low-limit-validation?${params.toString()}`, "_blank", "noopener,noreferrer");
  };

  const runBacktest = (): void => {
    if (mode === "STOCK" && selectedInstrument) {
      void run({ mode, entryRule, symbol: selectedInstrument.trading_symbol, instrumentToken: selectedInstrument.instrument_token });
    }
    if (mode === "WATCHLIST" && watchlistKey) {
      void run({ mode, entryRule, watchlistKey });
    }
  };

  const canRun = mode === "STOCK" ? selectedInstrument != null : watchlistKey != null;
  const trades = data?.symbols.flatMap((symbol) => symbol.trades) ?? [];

  return (
    <div style={{ padding: 24 }}>
      <Space orientation="vertical" size={16} style={{ width: "100%" }}>
        <Card>
          <Space orientation="vertical" size={12} style={{ width: "100%" }}>
            <Title level={3} style={{ margin: 0 }}>Weekly Low Limit Backtest</Title>
            <Text type="secondary">Place a limit order 1% above the previous completed week&apos;s low with a 5% target and 5% stop.</Text>
            <Text type="secondary">Pre-market filter: skip the setup when the previous week&apos;s final close is already below the limit.</Text>
            <Radio.Group
              aria-label="Entry and holding rule"
              value={entryRule}
              onChange={(event) => setEntryRule(event.target.value as WeeklyLowLimitBacktestEntryRule)}
              options={[
                { label: "Any day · max 5 trading days", value: "ANY_DAY_MAX_5_TRADING_DAYS" },
                { label: "Mon–Wed only · Friday exit", value: "FIRST_3_DAYS_WEEK_CLOSE" },
              ]}
            />
            <Radio.Group value={mode} onChange={(event) => setMode(event.target.value as WeeklyLowLimitBacktestMode)} optionType="button" buttonStyle="solid" options={[{ label: "Single stock", value: "STOCK" }, { label: "Watchlist", value: "WATCHLIST" }]} />
            {mode === "STOCK" && (
              <div style={{ maxWidth: 420 }}>
                {instrumentsLoading ? <Spin size="small" /> : <InstrumentSearch instruments={nseEquities} value={selectedInstrument} onSelect={setSelectedInstrument} placeholder="Search an NSE equity" />}
                {instrumentsError && <Text type="danger">{instrumentsError}</Text>}
              </div>
            )}
            {mode === "WATCHLIST" && (
              <div style={{ maxWidth: 420 }}>
                <Select aria-label="Watchlist" value={watchlistKey} onChange={setWatchlistKey} placeholder="Select a watchlist" style={{ width: "100%" }} options={watchlists.map((watchlist) => ({ value: watchlist.value, label: `${watchlist.label} (${watchlist.count})` }))} loading={watchlists.length === 0 && watchlistError == null} />
                {watchlistError && <Text type="danger">{watchlistError}</Text>}
              </div>
            )}
            <Button type="primary" onClick={runBacktest} loading={loading} disabled={!canRun}>Run backtest</Button>
          </Space>
        </Card>

        {error && <Alert type="error" message={error} showIcon />}

        {data && <>
          <Card title={`${data.selection} · ${entryRuleLabel(data.entryRule)}: ${data.testedFromDate} to ${data.testedToDate}`}>
            <Row gutter={[12, 12]}>
              <Metric title="Setups" value={data.summary.setupCount} />
              <Metric title="No fills" value={data.summary.noFillCount} />
              <Metric title="Filled trades" value={data.summary.filledTradeCount} />
              <Metric title="Targets" value={data.summary.targetHitCount} />
              <Metric title="Stops" value={data.summary.stopLossCount} />
              <Metric title="Time exits" value={data.summary.timeExitCount} />
              <Metric title="Skipped (open)" value={data.summary.positionOpenSkipCount} />
              <Metric title="Skipped (prior close)" value={data.summary.premarketFilterSkipCount} />
              <Metric title="Skipped (open >1%)" value={data.summary.openDeviationSkipCount} />
              <Metric title="Average return" value={formatPercent(data.summary.averageReturnPct)} />
            </Row>
          </Card>
          <Card title="Weekly audit trail">
            <Table rowKey={(row) => `${row.symbol}-${row.entryWeekStartDate}`} columns={columns} dataSource={trades} pagination={{ pageSize: 30 }} scroll={{ x: 1500 }} size="small" />
          </Card>
        </>}
      </Space>
    </div>
  );
}

function Metric({ title, value }: { title: string; value: number | string }): JSX.Element {
  return <Col xs={12} sm={8} lg={4}><Statistic title={title} value={value} /></Col>;
}
