import { Alert, Button, Card, Col, Row, Select, Space, Statistic, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useState, type ReactElement } from "react";
import { useTwoDayGreenCandleBacktest } from "../hooks/useTwoDayGreenCandleBacktest";
import type {
  TwoDayGreenCandleBacktestTrade,
  TwoDayGreenCandleObservation,
  UniverseOptionsResponse,
} from "../types";
import { getJson } from "../utils/api";

const { Text, Title } = Typography;
const WATCHLISTS_PATH = "/api/strategy/weekly-price-review/watchlists";

const formatNumber = (value: number | null): string => value == null ? "-" : value.toLocaleString("en-IN", { maximumFractionDigits: 2 });
const formatPercent = (value: number | null): string => value == null ? "-" : `${value.toFixed(2)}%`;
const formatBoolean = (value: boolean): string => value ? "Yes" : "No";

export function TwoDayGreenCandleBacktestPage() {
  const [watchlists, setWatchlists] = useState<UniverseOptionsResponse["options"]>([]);
  const [watchlistKey, setWatchlistKey] = useState<string>();
  const [watchlistError, setWatchlistError] = useState<string | null>(null);
  const { data, loading, error, run } = useTwoDayGreenCandleBacktest();

  useEffect(() => {
    void getJson<UniverseOptionsResponse>(WATCHLISTS_PATH)
      .then((response) => setWatchlists(response.options))
      .catch((cause) => setWatchlistError(cause instanceof Error ? cause.message : "Unable to load watchlists."));
  }, []);

  const runBacktest = (): void => {
    if (watchlistKey) void run({ watchlistKey });
  };

  const trades = data?.symbols.flatMap((symbol) => symbol.trades) ?? [];
  const columns: ColumnsType<TwoDayGreenCandleBacktestTrade> = [
    { title: "Symbol", dataIndex: "symbol", key: "symbol", fixed: "left" },
    { title: "Setup 1", dataIndex: ["setupDayOne", "date"], key: "setupDayOne" },
    { title: "Setup 2", dataIndex: ["setupDayTwo", "date"], key: "setupDayTwo" },
    { title: "Buy day", dataIndex: ["buyDay", "date"], key: "buyDay" },
    { title: "Volume rising", dataIndex: "setupVolumeRising", key: "setupVolumeRising", render: formatBoolean },
    { title: "Move rising", dataIndex: "setupMoveRising", key: "setupMoveRising", render: formatBoolean },
    { title: "Entry", dataIndex: "entryPrice", key: "entryPrice", render: formatNumber },
    { title: "Target", dataIndex: "targetPrice", key: "targetPrice", render: formatNumber },
    { title: "Outcome", dataIndex: "outcome", key: "outcome", render: (value: string) => <Tag color={value === "TARGET_HIT" ? "green" : "orange"}>{value}</Tag> },
    { title: "Hold", dataIndex: "holdingTradingDays", key: "holdingTradingDays", render: (value: number | null) => value == null ? "-" : `${value} session${value === 1 ? "" : "s"}` },
    { title: "Max high", dataIndex: "maximumHighSinceEntryPct", key: "maximumHighSinceEntryPct", render: formatPercent },
    { title: "Unresolved close", dataIndex: "unresolvedCloseReturnPct", key: "unresolvedCloseReturnPct", render: formatPercent },
  ];

  return (
    <div style={{ padding: 24 }}>
      <Space orientation="vertical" size={16} style={{ width: "100%" }}>
        <Card>
          <Space orientation="vertical" size={12} style={{ width: "100%" }}>
            <Title level={3} style={{ margin: 0 }}>Two-Day Green Candle Backtest</Title>
            <Text type="secondary">Buy at the third day&apos;s open when the two prior days closed above their opens and gained more than 1% close-to-close. Test window: latest 40 trading sessions; target: 5%.</Text>
            <Text type="secondary">Volume progression and volatility are observations only. Buy-day values are shown after the session and are not entry filters.</Text>
            <Select aria-label="Watchlist" value={watchlistKey} onChange={setWatchlistKey} placeholder="Select a watchlist" style={{ maxWidth: 420, width: "100%" }} options={watchlists.map((watchlist) => ({ value: watchlist.value, label: `${watchlist.label} (${watchlist.count})` }))} loading={watchlists.length === 0 && watchlistError == null} />
            {watchlistError && <Text type="danger">{watchlistError}</Text>}
            <Button type="primary" onClick={runBacktest} loading={loading} disabled={!watchlistKey}>Run backtest</Button>
          </Space>
        </Card>

        {error && <Alert type="error" message={error} showIcon />}
        {data && <>
          <Card title={`${data.watchlistKey} · ${data.testedFromDate} to ${data.testedToDate}`}>
            <Row gutter={[12, 12]}>
              <Metric title="Setups" value={data.summary.setupCount} />
              <Metric title="5% targets" value={data.summary.targetHitCount} />
              <Metric title="Unresolved" value={data.summary.unresolvedCount} />
              <Metric title="Target rate" value={formatPercent(data.summary.targetHitRatePct)} />
              <Metric title="Average hold" value={data.summary.averageHoldingTradingDays == null ? "-" : `${data.summary.averageHoldingTradingDays.toFixed(1)} sessions`} />
            </Row>
          </Card>
          <Card title="Setup and outcome audit trail">
            <Table rowKey={(row) => `${row.symbol}-${row.buyDay.date}`} columns={columns} dataSource={trades} expandable={{ expandedRowRender: (row) => <ObservationTable trade={row} /> }} pagination={{ pageSize: 30 }} scroll={{ x: 1500 }} size="small" />
          </Card>
        </>}
      </Space>
    </div>
  );
}

function ObservationTable({ trade }: { trade: TwoDayGreenCandleBacktestTrade }): ReactElement {
  const rows = [
    ["Setup day 1", trade.setupDayOne],
    ["Setup day 2", trade.setupDayTwo],
    ["Buy day", trade.buyDay],
  ] as const;
  return <Table<ObservationRow> rowKey="label" pagination={false} size="small" dataSource={rows.map(([label, observation]) => ({ label, ...observation }))} columns={observationColumns} />;
}

type ObservationRow = TwoDayGreenCandleObservation & { label: string };
const observationColumns: ColumnsType<ObservationRow> = [
  { title: "Day", dataIndex: "label", key: "label" },
  { title: "Date", dataIndex: "date", key: "date" },
  { title: "Open", dataIndex: "open", key: "open", render: formatNumber },
  { title: "High", dataIndex: "high", key: "high", render: formatNumber },
  { title: "Low", dataIndex: "low", key: "low", render: formatNumber },
  { title: "Close", dataIndex: "close", key: "close", render: formatNumber },
  { title: "Volume", dataIndex: "volume", key: "volume", render: formatNumber },
  { title: "Daily move", dataIndex: "dailyChangePct", key: "dailyChangePct", render: formatPercent },
  { title: "Open → close", dataIndex: "openToClosePct", key: "openToClosePct", render: formatPercent },
  { title: "Low → high", dataIndex: "lowToHighPct", key: "lowToHighPct", render: formatPercent },
  { title: "Close location", dataIndex: "closeLocationPct", key: "closeLocationPct", render: formatPercent },
];

function Metric({ title, value }: { title: string; value: number | string }): ReactElement {
  return <Col xs={12} sm={8} lg={4}><Statistic title={title} value={value} /></Col>;
}
