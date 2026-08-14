import { Alert, Button, Card, Col, Row, Select, Space, Statistic, Table, Tooltip, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useState, type ReactElement } from "react";
import { useTwoDayCloseStrengthBacktest } from "../hooks/useTwoDayCloseStrengthBacktest";
import type { TwoDayCloseStrengthObservation, UniverseOptionsResponse } from "../types";
import { getJson } from "../utils/api";

const { Text, Title } = Typography;
const WATCHLISTS_PATH = "/api/strategy/weekly-price-review/watchlists";

function formatPrice(value: number): string {
  return `₹${value.toLocaleString("en-IN", { maximumFractionDigits: 2 })}`;
}

function formatPercent(value: number | null): string {
  return value == null ? "-" : `${value.toFixed(2)}%`;
}

function formatDate(value: string): string {
  const [year, month, day] = value.split("-").map(Number);
  const date = new Date(year, month - 1, day);
  return `${date.toLocaleDateString("en-IN", { weekday: "short" })}, ${value}`;
}

function formatClosePositions(values: number[]): string {
  return values.map((value) => `${Math.round(value)}%`).join(" · ");
}

function exitReasonLabel(value: string): string {
  return value === "TARGET_HIT" ? "5% target" : "Thursday close";
}

export function TwoDayCloseStrengthBacktestPage() {
  const [watchlists, setWatchlists] = useState<UniverseOptionsResponse["options"]>([]);
  const [watchlistKey, setWatchlistKey] = useState<string>();
  const [watchlistError, setWatchlistError] = useState<string | null>(null);
  const { data, loading, error, run } = useTwoDayCloseStrengthBacktest();

  useEffect(() => {
    void getJson<UniverseOptionsResponse>(WATCHLISTS_PATH)
      .then((response) => {
        setWatchlists(response.options);
        setWatchlistError(null);
      })
      .catch((cause) => setWatchlistError(cause instanceof Error ? cause.message : "Unable to load watchlists."));
  }, []);

  const columns: ColumnsType<TwoDayCloseStrengthObservation> = [
    { title: "Stock", dataIndex: "symbol", key: "symbol", fixed: "left", width: 80 },
    { title: "Pattern", key: "pattern", width: 210, render: (_, row) => <Tooltip title={`${formatDate(row.patternStartDate)} to ${formatDate(row.patternEndDate)}`}><Text>{formatClosePositions(row.patternClosePositionPct)}</Text></Tooltip> },
    { title: "Entry", key: "entry", width: 140, render: (_, row) => `${formatDate(row.entryDate)} · ${formatPrice(row.entryPrice)}` },
    { title: "Target", dataIndex: "targetPrice", key: "targetPrice", render: formatPrice, width: 90 },
    { title: "Exit", key: "exit", width: 170, render: (_, row) => <Tooltip title={`${exitReasonLabel(row.exitReason)} · ${formatDate(row.exitDate)} · ${formatPrice(row.exitPrice)}`}><Text>{formatDate(row.exitDate)} · {formatPrice(row.exitPrice)}</Text></Tooltip> },
    { title: "Return", dataIndex: "realizedReturnPct", key: "realizedReturnPct", width: 90, render: (value: number) => <Text strong style={{ color: value < 0 ? "#cf1322" : "#389e0d" }}>{formatPercent(value)}</Text> },
  ];

  const canRun = watchlistKey != null;
  return (
    <div style={{ padding: 24 }}>
      <Space orientation="vertical" size={16} style={{ width: "100%" }}>
        <Card>
          <Space orientation="vertical" size={12} style={{ width: "100%" }}>
            <Title level={3} style={{ margin: 0 }}>Two-Day Close-Strength Backtest</Title>
            <Text type="secondary">Six months · first 3 sessions below 80% close-to-high · final 2 sessions at least 80%.</Text>
            <Text type="secondary">Buy the next session open. Take +5% through Wednesday; otherwise exit Thursday close.</Text>
            <Row gutter={[12, 12]}>
              <Col xs={24} sm={10} lg={7}>
                <Text strong>Watchlist</Text>
                <Select aria-label="Watchlist" value={watchlistKey} onChange={setWatchlistKey} placeholder="Select a watchlist" style={{ width: "100%" }} options={watchlists.map((watchlist) => ({ value: watchlist.value, label: `${watchlist.label} (${watchlist.count})` }))} loading={watchlists.length === 0 && watchlistError == null} />
                {watchlistError && <Text type="danger">{watchlistError}</Text>}
              </Col>
            </Row>
            <Button type="primary" onClick={() => watchlistKey && void run({ watchlistKey })} loading={loading} disabled={!canRun}>Run six-month backtest</Button>
          </Space>
        </Card>

        {error && <Alert type="error" message={error} showIcon />}

        {data && <>
          <Card title={`${data.watchlistKey} · ${formatDate(data.testedFromDate)} to ${formatDate(data.testedToDate)}`}>
            <Row gutter={[12, 12]}>
              <Metric title="Signals" value={data.summary.signalCount} />
              <Metric title="Target hits" value={data.summary.targetHitCount} />
              <Metric title="Thursday exits" value={data.summary.thursdayCloseExitCount} />
              <Metric title="Profitable" value={data.summary.profitableExitCount} />
              <Metric title="Losses" value={data.summary.lossExitCount} />
              <Metric title="Average realized" value={formatPercent(data.summary.averageRealizedReturnPct)} />
              <Metric title="Median realized" value={formatPercent(data.summary.medianRealizedReturnPct)} />
              <Metric title="Worst realized" value={formatPercent(data.summary.worstRealizedReturnPct)} />
            </Row>
          </Card>
          <Card title="Five-session pattern audit trail">
            <Table rowKey={(row) => `${row.symbol}-${row.patternEndDate}`} columns={columns} dataSource={data.observations} pagination={{ pageSize: 30 }} scroll={{ x: 780 }} size="small" />
          </Card>
        </>}
      </Space>
    </div>
  );
}

function Metric({ title, value }: { title: string; value: number | string }): ReactElement {
  return <Col xs={12} sm={8} lg={4}><Statistic title={title} value={value} /></Col>;
}
