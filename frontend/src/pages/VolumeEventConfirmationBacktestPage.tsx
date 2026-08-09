import { Alert, Button, Card, Col, Input, Radio, Row, Select, Space, Statistic, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useMemo, useState } from "react";
import { useVolumeEventConfirmationBacktest } from "../hooks/useVolumeEventConfirmationBacktest";
import type {
  UniverseOptionsResponse,
  VolumeEventConfirmationBacktestReport,
  VolumeEventConfirmationObservation,
  VolumeEventConfirmationStatus,
  VolumeEventConfirmationSymbolReport,
  VolumeEventEntryMode,
} from "../types";
import { getJson } from "../utils/api";

const { Text, Title } = Typography;
const WATCHLISTS_PATH = "/api/strategy/weekly-price-review/watchlists";

type BacktestScope = "STOCK" | "WATCHLIST";

function formatNumber(value: number | null): string {
  return value == null ? "—" : value.toLocaleString("en-IN", { maximumFractionDigits: 2 });
}

function formatPercent(value: number | null): string {
  return value == null ? "—" : `${value >= 0 ? "+" : ""}${value.toFixed(2)}%`;
}

function formatPoints(value: number | null): string {
  return value == null ? "—" : `${value >= 0 ? "+" : ""}${value.toFixed(2)} pts`;
}

function formatDate(value: string | null): string {
  return value == null ? "—" : new Intl.DateTimeFormat("en-IN", { day: "2-digit", month: "short", timeZone: "UTC" }).format(new Date(`${value}T00:00:00Z`));
}

function statusColor(status: VolumeEventConfirmationStatus): string {
  if (status === "TARGET_HIT") return "green";
  if (status === "UNRESOLVED") return "orange";
  if (status === "NO_CONFIRMATION") return "red";
  if (status === "REJECTED_BEARISH_CONTEXT") return "red";
  if (status === "INSUFFICIENT_RSI_CALIBRATION") return "purple";
  if (status === "RSI_ABOVE_ADAPTIVE_CEILING") return "blue";
  if (status === "PAST_RSI_TREND_NOT_CONFIRMED") return "red";
  return "default";
}

function statusLabel(status: VolumeEventConfirmationStatus): string {
  return status.replaceAll("_", " ");
}

function formatFilter(value: boolean | null): string {
  return value == null ? "—" : value ? "PASS" : "FAIL";
}

const statusFilters: VolumeEventConfirmationStatus[] = [
  "TARGET_HIT",
  "UNRESOLVED",
  "NO_CONFIRMATION",
  "SKIPPED_WHILE_IN_POSITION",
  "INSUFFICIENT_FORWARD_DATA",
  "REJECTED_BEARISH_CONTEXT",
  "INSUFFICIENT_RSI_CALIBRATION",
  "RSI_ABOVE_ADAPTIVE_CEILING",
  "PAST_RSI_TREND_NOT_CONFIRMED",
];

function buildObservationColumns(symbols: string[]): ColumnsType<VolumeEventConfirmationObservation> {
  const symbolFilters = symbols.map((symbol) => ({ text: symbol, value: symbol }));

  return [
  {
    title: "Symbol",
    dataIndex: "symbol",
    key: "symbol",
    fixed: "left",
    filters: symbolFilters,
    filterSearch: true,
    onFilter: (value, record) => record.symbol === value,
  },
  { title: "Event", dataIndex: "eventDate", key: "eventDate", render: formatDate },
  { title: "Volume / 5D", dataIndex: "volumeRatio", key: "volumeRatio", render: (value: number) => `${value.toFixed(2)}×` },
  { title: "Event RSI", dataIndex: "eventRsi", key: "eventRsi", render: formatNumber },
  { title: "5D return", dataIndex: "lookbackReturnPct", key: "lookbackReturnPct", render: formatPercent },
  { title: "5D drawdown", dataIndex: "lookbackDrawdownPct", key: "lookbackDrawdownPct", render: formatPercent },
  { title: "RSI ceiling", dataIndex: "adaptiveRsiThreshold", key: "adaptiveRsiThreshold", render: formatNumber },
  { title: "Cal. samples", dataIndex: "rsiCalibrationSampleCount", key: "rsiCalibrationSampleCount", render: formatNumber },
  { title: "Cal. hit rate", dataIndex: "rsiCalibrationSelectedHitRatePct", key: "rsiCalibrationSelectedHitRatePct", render: formatPercent },
  { title: "Past RSI Δ (t-5→t-1)", dataIndex: "pastRsiChangePoints", key: "pastRsiChangePoints", render: formatPoints },
  { title: "Past RSI filter", dataIndex: "pastRsiTrendPassed", key: "pastRsiTrendPassed", render: formatFilter },
  { title: "Confirm RSI", dataIndex: "confirmationRsi", key: "confirmationRsi", render: formatNumber },
  { title: "RSI Δ", dataIndex: "rsiChangePoints", key: "rsiChangePoints", render: formatPoints },
  { title: "Entry", dataIndex: "entryDate", key: "entryDate", render: formatDate },
  { title: "Entry price", dataIndex: "entryPrice", key: "entryPrice", render: formatNumber },
  {
    title: "Status",
    dataIndex: "status",
    key: "status",
    filters: statusFilters.map((status) => ({ text: statusLabel(status), value: status })),
    filterSearch: true,
    onFilter: (value, record) => record.status === value,
    render: (value: VolumeEventConfirmationStatus) => <Tag color={statusColor(value)}>{statusLabel(value)}</Tag>,
  },
  {
    title: "Exit",
    dataIndex: "exitDate",
    key: "exitDate",
    filters: [
      { text: "Has exit date", value: "HAS_EXIT" },
      { text: "No exit date", value: "NO_EXIT" },
    ],
    onFilter: (value, record) => value === "HAS_EXIT" ? record.exitDate != null : record.exitDate == null,
    render: formatDate,
  },
  { title: "Hold", dataIndex: "holdingTradingDays", key: "holdingTradingDays", render: (value: number | null) => value == null ? "—" : `${value}d` },
  { title: "Unresolved close", dataIndex: "unresolvedCloseReturnPct", key: "unresolvedCloseReturnPct", render: formatPercent },
  ];
}

const symbolColumns: ColumnsType<VolumeEventConfirmationSymbolReport> = [
  { title: "Symbol", dataIndex: "symbol", key: "symbol", fixed: "left" },
  { title: "Data", dataIndex: "dataStatus", key: "dataStatus" },
  { title: "Setups", dataIndex: ["summary", "setupCount"], key: "setupCount" },
  { title: "Confirmed", dataIndex: ["summary", "confirmedSignalCount"], key: "confirmedSignalCount" },
  { title: "5% targets", dataIndex: ["summary", "targetHitCount"], key: "targetHitCount" },
  { title: "Target rate", dataIndex: ["summary", "targetHitRatePct"], key: "targetHitRatePct", render: formatPercent },
  { title: "No confirmation", dataIndex: ["summary", "noConfirmationCount"], key: "noConfirmationCount" },
  { title: "Insufficient forward data", dataIndex: ["summary", "insufficientForwardDataCount"], key: "insufficientForwardDataCount" },
];

export function VolumeEventConfirmationBacktestPage() {
  const [watchlists, setWatchlists] = useState<UniverseOptionsResponse["options"]>([]);
  const [watchlistKey, setWatchlistKey] = useState<string>();
  const [symbol, setSymbol] = useState("");
  const [scope, setScope] = useState<BacktestScope>("STOCK");
  const [entryMode, setEntryMode] = useState<VolumeEventEntryMode>("FIVE_DAY_PAST_RSI_EARLY_ENTRY");
  const [watchlistError, setWatchlistError] = useState<string | null>(null);
  const { data, loading, error, run } = useVolumeEventConfirmationBacktest();

  useEffect(() => {
    void getJson<UniverseOptionsResponse>(WATCHLISTS_PATH)
      .then((response) => setWatchlists(response.options))
      .catch((cause) => setWatchlistError(cause instanceof Error ? cause.message : "Unable to load watchlists."));
  }, []);

  const canRun = Boolean(watchlistKey) && (scope === "WATCHLIST" || symbol.trim().length > 0);
  const runBacktest = (): void => {
    if (!watchlistKey || !canRun) return;
    void run({
      watchlistKey,
      entryMode,
      ...(scope === "STOCK" ? { symbol: symbol.trim().toUpperCase() } : {}),
    });
  };

  const observations = useMemo(
    () => data?.symbols.flatMap((symbolReport) => symbolReport.observations) ?? [],
    [data],
  );

  return (
    <div style={{ padding: 24 }}>
      <Space orientation="vertical" size={16} style={{ width: "100%" }}>
        <Card>
          <Space orientation="vertical" size={12} style={{ width: "100%" }}>
            <Title level={3} style={{ margin: 0 }}>Volume Event Confirmation Backtest</Title>
            <Text type="secondary">
              Research-only test: compare the past-five-session RSI rule with the original future-confirmation rule. Run one stock first, then the same rule across a watchlist.
            </Text>
            <Text type="secondary">
              Rules: volume ≥ 2× prior 5-session average · stock-specific RSI ceiling learned from 252 prior completed sessions · no fallback ceiling when calibration is insufficient · prior 5-session return must be non-negative · event close must not be more than 5% below the prior 5-session peak · 5% target · maximum 15-session evaluation window · no stop-loss in v1.
            </Text>
            <Radio.Group aria-label="RSI entry mode" value={entryMode} onChange={(event) => setEntryMode(event.target.value as VolumeEventEntryMode)}>
              <Radio.Button value="FIVE_DAY_PAST_RSI_EARLY_ENTRY">Past 5-day RSI · early entry</Radio.Button>
              <Radio.Button value="FIVE_DAY_FUTURE_RSI_CONFIRMATION">Future 5-day RSI · confirmed entry</Radio.Button>
            </Radio.Group>
            <Text type="secondary">
              {entryMode === "FIVE_DAY_PAST_RSI_EARLY_ENTRY"
                ? "Early mode: RSI must improve from t-5 through t-1, excluding the event date; enter at the next session open."
                : "Confirmed mode: event-day RSI must be followed by a higher RSI five sessions later; enter at the next session open."}
            </Text>
            <Radio.Group aria-label="Backtest scope" value={scope} onChange={(event) => setScope(event.target.value as BacktestScope)}>
              <Radio.Button value="STOCK">Selected stock</Radio.Button>
              <Radio.Button value="WATCHLIST">Whole watchlist</Radio.Button>
            </Radio.Group>
            <Select
              aria-label="Watchlist"
              value={watchlistKey}
              onChange={setWatchlistKey}
              placeholder="Select a watchlist"
              style={{ maxWidth: 420, width: "100%" }}
              options={watchlists.map((watchlist) => ({ value: watchlist.value, label: `${watchlist.label} (${watchlist.count})` }))}
              loading={watchlists.length === 0 && watchlistError == null}
            />
            {scope === "STOCK" && <Input aria-label="Stock symbol" value={symbol} onChange={(event) => setSymbol(event.target.value)} placeholder="Enter a symbol, e.g. BHEL" style={{ maxWidth: 420 }} />}
            {watchlistError && <Text type="danger">{watchlistError}</Text>}
            <Button type="primary" onClick={runBacktest} loading={loading} disabled={!canRun}>Run backtest</Button>
          </Space>
        </Card>

        {error && <Alert type="error" message={error} showIcon />}
        {data && <BacktestResults data={data} observations={observations} />}
      </Space>
    </div>
  );
}

function BacktestResults({ data, observations }: { data: VolumeEventConfirmationBacktestReport; observations: VolumeEventConfirmationObservation[] }) {
  const observationSymbols = [...new Set(observations.map((observation) => observation.symbol))].sort();
  const columns = buildObservationColumns(observationSymbols);

  return (
    <>
      <Card title={`${data.selectedSymbol ?? data.watchlistKey} · ${data.testedFromDate ?? "—"} to ${data.testedToDate ?? "—"}`}>
        <Row gutter={[12, 12]}>
          <Metric title="Setups" value={data.summary.setupCount} />
          <Metric title="Confirmed" value={data.summary.confirmedSignalCount} />
          <Metric title="5% targets" value={data.summary.targetHitCount} />
          <Metric title="Target rate" value={formatPercent(data.summary.targetHitRatePct)} />
          <Metric title="No confirmation" value={data.summary.noConfirmationCount} />
          <Metric title="Insufficient data" value={data.summary.insufficientForwardDataCount} />
          <Metric title="Bearish rejected" value={data.summary.rejectedBearishContextCount} />
          <Metric title="Insufficient RSI calibration" value={data.summary.insufficientRsiCalibrationCount} />
          <Metric title="RSI above ceiling" value={data.summary.rsiAboveAdaptiveCeilingCount} />
          <Metric title="Past RSI rejected" value={data.summary.pastRsiTrendRejectedCount} />
          <Metric title="Average hold" value={data.summary.averageHoldingTradingDays == null ? "—" : `${data.summary.averageHoldingTradingDays.toFixed(1)}d`} />
        </Row>
        <Text type="secondary" style={{ display: "block", marginTop: 12 }}>
          {data.config.entryMode === "FIVE_DAY_PAST_RSI_EARLY_ENTRY"
            ? "Early-entry rate counts events that passed the past-five-session RSI trend and produced a next-session entry."
            : "Confirmation rate counts confirmed trades against events with a completed five-session confirmation decision."} Unresolved trades exit at the end of the 15-session evaluation window; insufficient forward data is shown separately.
        </Text>
      </Card>
      <Card title="Per-symbol results">
        <Table<VolumeEventConfirmationSymbolReport> rowKey="symbol" columns={symbolColumns} dataSource={data.symbols} pagination={{ pageSize: 50 }} scroll={{ x: 900 }} size="small" />
      </Card>
      <Card title="Event audit trail">
        <Table<VolumeEventConfirmationObservation> rowKey={(row) => `${row.symbol}-${row.eventDate}`} columns={columns} dataSource={observations} pagination={{ pageSize: 30 }} scroll={{ x: 1500 }} size="small" />
      </Card>
    </>
  );
}

function Metric({ title, value }: { title: string; value: number | string }) {
  return <Col xs={12} sm={8} lg={4}><Statistic title={title} value={value} /></Col>;
}
