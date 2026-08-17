import { Alert, Button, Card, Col, Empty, Row, Space, Spin, Statistic, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useMemo, useState } from "react";
import { InstrumentSearch } from "../components/InstrumentSearch";
import { useAdaptiveBreakoutBacktest } from "../hooks/useAdaptiveBreakoutBacktest";
import type {
  AdaptiveBreakoutBacktestExitReason,
  AdaptiveBreakoutBacktestTrade,
  InstrumentSearchResult,
} from "../types";
import { useInstrumentSearch } from "../hooks/useInstrumentSearch";
import "./adaptiveBreakoutBacktest.css";

const { Text, Title } = Typography;

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

function reasonLabel(reason: AdaptiveBreakoutBacktestExitReason): string {
  if (reason === "TARGET_HIT") return "Target +5%";
  if (reason === "STOP_LOSS") return "Stop −5%";
  if (reason === "STOP_LOSS_SAME_CANDLE") return "Stop (same candle)";
  return "End of test";
}

function reasonColor(reason: AdaptiveBreakoutBacktestExitReason): string {
  if (reason === "TARGET_HIT") return "green";
  if (reason === "END_OF_TEST") return "blue";
  return "red";
}

export function AdaptiveBreakoutBacktestPage() {
  const [selectedInstrument, setSelectedInstrument] = useState<InstrumentSearchResult | null>(null);
  const { allInstruments, loading: instrumentsLoading, error: instrumentsError } = useInstrumentSearch();
  const { data, loading, error, run } = useAdaptiveBreakoutBacktest();
  const nseEquities = useMemo(
    () => allInstruments.filter((instrument) => instrument.exchange === "NSE" && instrument.instrument_type === "EQ"),
    [allInstruments],
  );

  const runBacktest = (): void => {
    if (!selectedInstrument) return;
    void run({
      symbol: selectedInstrument.trading_symbol,
      instrumentToken: selectedInstrument.instrument_token,
      months: 6,
      targetPct: 5,
      stopLossPct: 5,
    });
  };

  const columns: ColumnsType<AdaptiveBreakoutBacktestTrade> = [
    { title: "Fresh breakout", dataIndex: "breakoutDate", key: "breakoutDate", render: formatDate },
    { title: "Breakout close", dataIndex: "breakoutClose", key: "breakoutClose", render: formatPrice },
    { title: "Entry (next open)", dataIndex: "entryDate", key: "entryDate", render: (_: string, row) => <span>{formatDate(row.entryDate)} · {formatPrice(row.entryPrice)}</span> },
    { title: "Target", dataIndex: "targetPrice", key: "targetPrice", render: formatPrice },
    { title: "Stop", dataIndex: "stopPrice", key: "stopPrice", render: formatPrice },
    { title: "Exit", dataIndex: "exitDate", key: "exitDate", render: (_: string, row) => <span>{formatDate(row.exitDate)} · {formatPrice(row.exitPrice)}</span> },
    {
      title: "Result",
      key: "result",
      render: (_, row) => <Space size={5}><Tag color={reasonColor(row.exitReason)}>{reasonLabel(row.exitReason)}</Tag>{row.ambiguousSameCandle && <Text type="secondary">OHLC order unknown</Text>}</Space>,
    },
    { title: "Held", dataIndex: "holdingSessions", key: "holdingSessions", render: (value: number) => `${value} day${value === 1 ? "" : "s"}` },
    { title: "Return", dataIndex: "returnPct", key: "returnPct", render: (value: number) => <Text type={value >= 0 ? "success" : "danger"}>{formatPercent(value)}</Text> },
  ];

  return (
    <div className="adaptive-breakout-backtest-page">
      <Card className="adaptive-breakout-backtest-control">
        <Space orientation="vertical" size={10} style={{ width: "100%" }}>
          <div>
            <Title level={3} style={{ margin: 0 }}>Fresh Breakout · 6-Month Test</Title>
            <Text type="secondary">Price-only validation: buy the next session open after each fresh breakout, then use a fixed +5% target and −5% stop.</Text>
          </div>
          <Space wrap style={{ width: "100%" }}>
            <div className="adaptive-breakout-backtest-search">
              {instrumentsLoading ? <Spin size="small" /> : <InstrumentSearch
                instruments={nseEquities}
                value={selectedInstrument}
                onSelect={setSelectedInstrument}
                placeholder="Select an NSE stock"
              />}
              {instrumentsError && <Text type="danger">{instrumentsError}</Text>}
            </div>
            <Button type="primary" onClick={runBacktest} loading={loading} disabled={!selectedInstrument}>
              Run {selectedInstrument?.trading_symbol ?? "stock"} test
            </Button>
          </Space>
        </Space>
      </Card>

      {error && <Alert type="error" showIcon message={error} />}
      {!data && !loading && !error && <Empty description="Select a stock to see its fresh-breakout trades." />}
      {data && (
        <Space orientation="vertical" size={12} style={{ width: "100%" }}>
          <Card title={`${data.symbol} · ${formatDate(data.testedFromDate)} to ${formatDate(data.testedToDate)}`}>
            <Row gutter={[12, 12]}>
              <Metric title="Fresh breakouts" value={data.summary.freshBreakoutCount} />
              <Metric title="Trades entered" value={data.summary.enteredTradeCount} />
              <Metric title="Targets hit" value={data.summary.targetHitCount} />
              <Metric title="Stops hit" value={data.summary.stopLossCount} />
              <Metric title="Win rate" value={data.summary.winRatePct == null ? "—" : `${data.summary.winRatePct.toFixed(1)}%`} />
              <Metric title="Average hold" value={data.summary.averageHoldingSessions == null ? "—" : `${data.summary.averageHoldingSessions.toFixed(1)} days`} />
            </Row>
          </Card>
          <Card title="Trade-by-trade replay" extra={<Text type="secondary">Entry is always the next session open</Text>}>
            <Alert className="adaptive-breakout-backtest-note" type="info" showIcon message={data.entryRule} description={data.ambiguousCandleRule} />
            {data.trades.length === 0 ? <Empty description="No fresh breakout produced an entry in this six-month window." /> : <Table rowKey={(row) => `${row.breakoutDate}-${row.entryDate}`} columns={columns} dataSource={data.trades} pagination={false} size="small" scroll={{ x: 1100 }} />}
          </Card>
        </Space>
      )}
    </div>
  );
}

function Metric({ title, value }: { title: string; value: number | string }): JSX.Element {
  return <Col xs={12} sm={8} lg={4}><Statistic title={title} value={value} /></Col>;
}
