import { Alert, Button, Card, Col, Row, Select, Space, Statistic, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useState } from "react";
import { useFridayCloseStrengthBacktest } from "../hooks/useFridayCloseStrengthBacktest";
import type { FridayCloseStrengthObservation, UniverseOptionsResponse } from "../types";
import { getJson } from "../utils/api";

const { Text, Title } = Typography;
const WATCHLISTS_PATH = "/api/strategy/summary-console/watchlists";

function formatPrice(value: number): string {
  return `₹${value.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function formatPercent(value: number | null): string {
  return value == null ? "-" : `${value.toFixed(2)}%`;
}

function formatDate(value: string): string {
  const [year, month, day] = value.split("-").map(Number);
  const date = new Date(year, month - 1, day);
  return `${date.toLocaleDateString("en-IN", { weekday: "short" })}, ${value}`;
}

export function FridayCloseStrengthBacktestPage() {
  const [watchlists, setWatchlists] = useState<UniverseOptionsResponse["options"]>([]);
  const [watchlistKey, setWatchlistKey] = useState<string>();
  const [watchlistError, setWatchlistError] = useState<string | null>(null);
  const { data, loading, error, run } = useFridayCloseStrengthBacktest();

  useEffect(() => {
    void getJson<UniverseOptionsResponse>(WATCHLISTS_PATH)
      .then((response) => {
        setWatchlists(response.options);
        setWatchlistError(null);
      })
      .catch((cause) => setWatchlistError(cause instanceof Error ? cause.message : "Unable to load watchlists."));
  }, []);

  const columns: ColumnsType<FridayCloseStrengthObservation> = [
    { title: "Stock", dataIndex: "symbol", key: "symbol", fixed: "left" },
    { title: "Friday", dataIndex: "signalDate", key: "signalDate", render: formatDate },
    { title: "Fri move", dataIndex: "fridayMovePct", key: "fridayMovePct", render: formatPercent },
    { title: "Close to high", dataIndex: "fridayClosePositionPct", key: "fridayClosePositionPct", render: formatPercent },
    { title: "Entry", key: "entry", render: (_, row) => `${formatDate(row.entryDate)} · ${formatPrice(row.entryPrice)}` },
    { title: "Week high", key: "weekHigh", render: (_, row) => `${formatDate(row.followingWeekHighDate)} · ${formatPrice(row.followingWeekHigh)}` },
    { title: "Max upside", dataIndex: "maximumUpsidePct", key: "maximumUpsidePct", render: (value: number) => <Text strong style={{ color: value >= 5 ? "#389e0d" : "#595959" }}>{formatPercent(value)}</Text> },
  ];

  const canRun = watchlistKey != null;
  return (
    <div style={{ padding: 24 }}>
      <Space orientation="vertical" size={16} style={{ width: "100%" }}>
        <Card>
          <Space orientation="vertical" size={12} style={{ width: "100%" }}>
            <Title level={3} style={{ margin: 0 }}>Friday Close-Strength Backtest</Title>
            <Text type="secondary">Six months · Friday close at least 70% of its range high · Friday close more than 2% above Thursday close.</Text>
            <Text type="secondary">Entry is the next available session open. Max upside is the following completed week&apos;s highest high, so it is a retrospective opportunity metric—not an executable exit.</Text>
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
              <Metric title="≥2% upside" value={formatPercent(data.summary.maximumUpsideAtLeast2PctRatePct)} />
              <Metric title="≥5% signals" value={data.summary.maximumUpsideAtLeast5PctCount} />
              <Metric title="Average max upside" value={formatPercent(data.summary.averageMaximumUpsidePct)} />
              <Metric title="Median max upside" value={formatPercent(data.summary.medianMaximumUpsidePct)} />
            </Row>
          </Card>
          <Card title="Friday signal audit trail">
            <Table rowKey={(row) => `${row.symbol}-${row.signalDate}`} columns={columns} dataSource={data.observations} pagination={{ pageSize: 30 }} scroll={{ x: 1100 }} size="small" />
          </Card>
        </>}
      </Space>
    </div>
  );
}

function Metric({ title, value }: { title: string; value: number | string }) {
  return <Col xs={12} sm={8} lg={4}><Statistic title={title} value={value} /></Col>;
}
