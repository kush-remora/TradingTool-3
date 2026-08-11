import { Alert, Button, Card, Col, InputNumber, Row, Select, Space, Statistic, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useState, type ReactElement } from "react";
import { useWeeklyLowAlignmentBacktest } from "../hooks/useWeeklyLowAlignmentBacktest";
import type { UniverseOptionsResponse, WeeklyLowAlignmentBacktestTrade } from "../types";
import { getJson } from "../utils/api";

const { Text, Title } = Typography;
const WATCHLISTS_PATH = "/api/strategy/weekly-price-review/watchlists";

function formatNumber(value: number | null): string {
  return value == null ? "-" : `₹${value.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function formatPercent(value: number | null): string {
  return value == null ? "-" : `${value.toFixed(2)}%`;
}

function formatDateWithDay(value: string | null): string {
  if (!value) return "-";
  const [year, month, day] = value.split("-").map(Number);
  const date = new Date(year, month - 1, day);
  return `${date.toLocaleDateString("en-IN", { weekday: "short" })}, ${value}`;
}

function outcomeColor(outcome: string): string {
  if (outcome === "TARGET_HIT") return "#389e0d";
  if (outcome === "TIME_EXIT") return "#d48806";
  if (outcome === "TOO_SOON_RETEST") return "#cf1322";
  return "#595959";
}

function outcomeLabel(outcome: string): string {
  if (outcome === "NO_RETEST") return "No retest";
  if (outcome === "TOO_SOON_RETEST") return "Retest too soon";
  if (outcome === "POSITION_OPEN_SKIP") return "Skipped · position open";
  if (outcome === "TARGET_HIT") return "Target hit";
  if (outcome === "TIME_EXIT") return "Time exit";
  return outcome;
}

export function WeeklyLowAlignmentBacktestPage() {
  const [watchlists, setWatchlists] = useState<UniverseOptionsResponse["options"]>([]);
  const [watchlistKey, setWatchlistKey] = useState<string>();
  const [targetPct, setTargetPct] = useState(5);
  const [maxHoldingTradingDays, setMaxHoldingTradingDays] = useState(5);
  const [watchlistError, setWatchlistError] = useState<string | null>(null);
  const { data, loading, error, run } = useWeeklyLowAlignmentBacktest();

  useEffect(() => {
    void getJson<UniverseOptionsResponse>(WATCHLISTS_PATH)
      .then((response) => {
        setWatchlists(response.options);
        setWatchlistError(null);
      })
      .catch((cause) => setWatchlistError(cause instanceof Error ? cause.message : "Unable to load watchlists."));
  }, []);

  const columns: ColumnsType<WeeklyLowAlignmentBacktestTrade> = [
    { title: "Stock", dataIndex: "symbol", key: "symbol", fixed: "left" },
    { title: "Entry week", dataIndex: "entryWeekStartDate", key: "entryWeekStartDate", render: formatDateWithDay },
    { title: "W-1 low", key: "low", render: (_, row) => `${formatNumber(row.previousWeekLow)} · ${formatDateWithDay(row.previousWeekLowDate)}` },
    { title: "Retest", key: "retest", render: (_, row) => row.retestDate == null ? "-" : `${formatDateWithDay(row.retestDate)} · ${formatNumber(row.retestLow)}` },
    { title: "Gap", key: "gap", render: (_, row) => row.retestGapTradingDays == null ? "-" : `${row.retestGapTradingDays} sessions` },
    { title: "Entry", dataIndex: "entryPrice", key: "entryPrice", render: formatNumber },
    { title: "Target", dataIndex: "targetPrice", key: "targetPrice", render: formatNumber },
    { title: "Outcome", dataIndex: "outcome", key: "outcome", render: (value: string) => <Text style={{ color: outcomeColor(value) }}>{outcomeLabel(value)}</Text> },
    { title: "Exit", key: "exit", render: (_, row) => row.exitDate == null ? "-" : `${formatDateWithDay(row.exitDate)} · ${formatNumber(row.exitPrice)}` },
    { title: "Hold", dataIndex: "holdingTradingDays", key: "holdingTradingDays", render: (value: number | null) => value == null ? "-" : `${value}d` },
    { title: "Return", dataIndex: "returnPct", key: "returnPct", render: formatPercent },
  ];

  const trades = data?.symbols.flatMap((symbol) => symbol.trades) ?? [];
  const canRun = watchlistKey != null && targetPct > 0 && maxHoldingTradingDays > 0;

  return (
    <div style={{ padding: 24 }}>
      <Space orientation="vertical" size={16} style={{ width: "100%" }}>
        <Card>
          <Space orientation="vertical" size={12} style={{ width: "100%" }}>
            <Title level={3} style={{ margin: 0 }}>Weekly Low Alignment Backtest</Title>
            <Text type="secondary">Six months · selected watchlist · previous-week low retest within 1% after at least five trading sessions.</Text>
            <Text type="secondary">Entry is fixed at W-1 low × 1.01. There is no stop-loss. Unreached targets exit at the holding limit.</Text>
            <Row gutter={[12, 12]}>
              <Col xs={24} sm={10} lg={7}>
                <Text strong>Watchlist</Text>
                <Select aria-label="Watchlist" value={watchlistKey} onChange={setWatchlistKey} placeholder="Select a watchlist" style={{ width: "100%" }} options={watchlists.map((watchlist) => ({ value: watchlist.value, label: `${watchlist.label} (${watchlist.count})` }))} loading={watchlists.length === 0 && watchlistError == null} />
                {watchlistError && <Text type="danger">{watchlistError}</Text>}
              </Col>
              <Col xs={12} sm={7} lg={4}>
                <Text strong>Target %</Text>
                <InputNumber aria-label="Target percentage" min={0.1} max={100} step={0.5} value={targetPct} onChange={(value) => setTargetPct(value ?? 5)} style={{ width: "100%" }} />
              </Col>
              <Col xs={12} sm={7} lg={4}>
                <Text strong>Max holding sessions</Text>
                <InputNumber aria-label="Maximum holding sessions" min={1} max={60} value={maxHoldingTradingDays} onChange={(value) => setMaxHoldingTradingDays(value ?? 5)} style={{ width: "100%" }} />
              </Col>
            </Row>
            <Button type="primary" onClick={() => watchlistKey && void run({ watchlistKey, targetPct, maxHoldingTradingDays })} loading={loading} disabled={!canRun}>Run six-month backtest</Button>
          </Space>
        </Card>

        {error && <Alert type="error" message={error} showIcon />}

        {data && <>
          <Card title={`${data.watchlistKey} · ${formatDateWithDay(data.testedFromDate)} to ${formatDateWithDay(data.testedToDate)}`}>
            <Row gutter={[12, 12]}>
              <Metric title="Setups" value={data.summary.setupCount} />
              <Metric title="No retest" value={data.summary.noRetestCount} />
              <Metric title="Too soon" value={data.summary.tooSoonRetestCount} />
              <Metric title="Filled trades" value={data.summary.filledTradeCount} />
              <Metric title="Targets" value={data.summary.targetHitCount} />
              <Metric title="Time exits" value={data.summary.timeExitCount} />
              <Metric title="Position skips" value={data.summary.positionOpenSkipCount} />
              <Metric title="Average return" value={formatPercent(data.summary.averageReturnPct)} />
            </Row>
          </Card>
          <Card title="Weekly audit trail">
            <Table rowKey={(row) => `${row.symbol}-${row.entryWeekStartDate}`} columns={columns} dataSource={trades} pagination={{ pageSize: 30 }} scroll={{ x: 1450 }} size="small" />
          </Card>
        </>}
      </Space>
    </div>
  );
}

function Metric({ title, value }: { title: string; value: number | string }): ReactElement {
  return <Col xs={12} sm={8} lg={4}><Statistic title={title} value={value} /></Col>;
}
