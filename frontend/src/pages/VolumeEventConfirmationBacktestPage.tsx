import { ReloadOutlined } from "@ant-design/icons";
import { Alert, Button, Card, Empty, InputNumber, Select, Space, Spin, Table, Tag, Typography } from "antd";
import type { TableColumnsType } from "antd";
import { useEffect, useMemo, useState } from "react";
import { useVolumeEventConfirmationBacktest } from "../hooks/useVolumeEventConfirmationBacktest";
import type {
  UniverseOptionsResponse,
  VolumeEventConfirmationBacktestReport,
  VolumeEventConfirmationObservation,
} from "../types";
import { getJson } from "../utils/api";

const { Text, Title } = Typography;
const WATCHLISTS_PATH = "/api/strategy/weekly-price-review/watchlists";

type VolumeEventTradeRow = VolumeEventConfirmationObservation & {
  companyName: string;
  instrumentToken: number;
};

function formatPrice(value: number): string {
  return `₹${value.toLocaleString("en-IN", { maximumFractionDigits: 2 })}`;
}

function formatPercent(value: number): string {
  return `${value >= 0 ? "+" : ""}${value.toFixed(2)}%`;
}

function buildKiteChartUrl(symbol: string, instrumentToken: number): string {
  return `https://kite.zerodha.com/chart/web/tvc/NSE/${encodeURIComponent(symbol)}/${instrumentToken}`;
}

function statusColor(status: string): string {
  return status === "TARGET_HIT" ? "green" : "blue";
}

const tradeColumns: TableColumnsType<VolumeEventTradeRow> = [
  {
    title: "Symbol",
    dataIndex: "symbol",
    key: "symbol",
    sorter: (left, right) => left.symbol.localeCompare(right.symbol),
    render: (value: string, row) => (
      <a aria-label={`Open ${value} in Kite`} href={buildKiteChartUrl(value, row.instrumentToken)} target="_blank" rel="noopener noreferrer">
        <Text strong>{value}</Text>
      </a>
    ),
  },
  { title: "Signal date", dataIndex: "entrySignalDate", key: "entrySignalDate" },
  { title: "Previous shocker", dataIndex: "eventDate", key: "eventDate" },
  { title: "Entry date", dataIndex: "entryDate", key: "entryDate" },
  { title: "Entry price", dataIndex: "entryPrice", key: "entryPrice", render: (value: number | null) => value == null ? "-" : formatPrice(value) },
  { title: "LTP", dataIndex: "currentLtp", key: "currentLtp", render: (value: number | null) => value == null ? "-" : formatPrice(value) },
  { title: "% from entry", dataIndex: "currentLtpChangePct", key: "currentLtpChangePct", render: (value: number | null) => value == null ? "-" : formatPercent(value) },
  { title: "Target", dataIndex: "targetPrice", key: "targetPrice", render: (value: number | null) => value == null ? "-" : formatPrice(value) },
  { title: "Exit date", dataIndex: "exitDate", key: "exitDate", render: (value: string | null) => value ?? "Open" },
  { title: "Exit price", dataIndex: "exitPrice", key: "exitPrice", render: (value: number | null) => value == null ? "-" : formatPrice(value) },
  { title: "Status", dataIndex: "status", key: "status", render: (value: string) => <Tag color={statusColor(value)}>{value === "UNRESOLVED" ? "OPEN" : value}</Tag> },
  { title: "Holding days", dataIndex: "holdingTradingDays", key: "holdingTradingDays", sorter: (left, right) => (left.holdingTradingDays ?? 0) - (right.holdingTradingDays ?? 0), render: (value: number | null) => value == null ? "-" : value },
];

function buildTradeRows(report: VolumeEventConfirmationBacktestReport): VolumeEventTradeRow[] {
  return report.symbols.flatMap((symbolReport) => symbolReport.observations
    .filter((observation) => observation.entryDate != null)
    .map((observation) => ({
      ...observation,
      companyName: symbolReport.companyName,
      instrumentToken: symbolReport.instrumentToken,
    })))
    .sort((left, right) => (right.entryDate ?? "").localeCompare(left.entryDate ?? "") || left.symbol.localeCompare(right.symbol));
}

export function VolumeEventConfirmationBacktestPage() {
  const [watchlists, setWatchlists] = useState<UniverseOptionsResponse["options"]>([]);
  const [selectedWatchlists, setSelectedWatchlists] = useState<string[]>([]);
  const [targetPct, setTargetPct] = useState(10);
  const [loadingOptions, setLoadingOptions] = useState(true);
  const [watchlistError, setWatchlistError] = useState<string | null>(null);
  const { data, loading, error, run } = useVolumeEventConfirmationBacktest();

  useEffect(() => {
    void getJson<UniverseOptionsResponse>(WATCHLISTS_PATH)
      .then((response) => setWatchlists(response.options))
      .catch((cause) => setWatchlistError(cause instanceof Error ? cause.message : "Unable to load watchlists."))
      .finally(() => setLoadingOptions(false));
  }, []);

  const tradeRows = useMemo(() => data == null ? [] : buildTradeRows(data), [data]);
  const canRun = selectedWatchlists.length > 0;

  const runBacktest = (): void => {
    if (!canRun) return;
    void run({ watchlists: selectedWatchlists, targetPct });
  };

  return (
    <div style={{ padding: "24px 24px 160px" }}>
      <Space orientation="vertical" size={16} style={{ width: "100%" }}>
        <Card
          title={<Title level={3} style={{ margin: 0 }}>Volume Event Confirmation Backtest</Title>}
          extra={<Button aria-label="Run backtest" icon={<ReloadOutlined />} onClick={runBacktest} loading={loading} disabled={!canRun}>Run backtest</Button>}
        >
          <Space orientation="vertical" size={8} style={{ width: "100%" }}>
            <Text type="secondary">
              Six-month backtest using one rule: today must be a new volume shocker (at least 2× the prior five-session average) and today’s close must be below the previous shocker close. Entry is the next session open.
            </Text>
            <Space wrap>
              <Select
                aria-label="Watchlists"
                mode="multiple"
                loading={loadingOptions}
                value={selectedWatchlists}
                onChange={setSelectedWatchlists}
                placeholder="Select one or more watchlists"
                maxTagCount="responsive"
                style={{ width: 520, maxWidth: "100%" }}
                options={watchlists.map((watchlist) => ({ value: watchlist.value, label: `${watchlist.label} (${watchlist.count})` }))}
              />
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
            {watchlistError && <Text type="danger">{watchlistError}</Text>}
            {data && <Text type="secondary" style={{ fontSize: 12 }}>
              {data.testedFromDate} to {data.testedToDate} · target {data.config.targetPct}% · {data.summary.confirmedSignalCount} signals · {data.summary.targetHitCount} targets hit · {data.summary.unresolvedCount} open
            </Text>}
          </Space>
        </Card>

        {error && <Alert type="error" message={error} showIcon />}
        {loading && <Spin />}
        {!loading && selectedWatchlists.length === 0 && <Empty description="Select one or more watchlists to run the six-month backtest." />}
        {!loading && data && tradeRows.length === 0 && <Empty description="No volume-shocker entries were found in the six-month window." />}
        {!loading && tradeRows.length > 0 && (
          <Card title="Six-month volume-shocker backtest">
            <Table<VolumeEventTradeRow>
              dataSource={tradeRows}
              columns={tradeColumns}
              rowKey={(row) => `${row.symbol}-${row.entryDate}`}
              pagination={{ pageSize: 50 }}
              size="middle"
              bordered
              scroll={{ x: 1500 }}
            />
          </Card>
        )}
      </Space>
    </div>
  );
}
