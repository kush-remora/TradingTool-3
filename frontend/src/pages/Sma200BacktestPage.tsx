import { Alert, Button, Card, Col, Row, Select, Space, Spin, Statistic, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useMemo, useState } from "react";
import { InstrumentSearch } from "../components/InstrumentSearch";
import { useInstrumentSearch } from "../hooks/useInstrumentSearch";
import { useSma200Backtest } from "../hooks/useSma200Backtest";
import type { InstrumentSearchResult, Sma200BacktestRequest, Sma200BacktestTrade } from "../types";

const { Text, Title } = Typography;

function formatNumber(value: number | null): string {
  return value == null ? "-" : value.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function formatPercent(value: number | null): string {
  return value == null ? "-" : `${formatNumber(value)}%`;
}

function returnColor(value: number | null): string | undefined {
  if (value == null) return undefined;
  return value >= 0 ? "#389e0d" : "#cf1322";
}

export function Sma200BacktestPage() {
  const [selectedInstrument, setSelectedInstrument] = useState<InstrumentSearchResult | null>(null);
  const [entrySmaPeriod, setEntrySmaPeriod] = useState<Sma200BacktestRequest["entrySmaPeriod"]>(200);
  const { allInstruments, loading: instrumentsLoading, error: instrumentsError } = useInstrumentSearch();
  const { data, loading, error, run } = useSma200Backtest();
  const nseEquities = useMemo(
    () => allInstruments.filter((instrument) => instrument.exchange === "NSE" && instrument.instrument_type === "EQ"),
    [allInstruments],
  );

  const columns: ColumnsType<Sma200BacktestTrade> = [
    { title: "Entry date", dataIndex: "entryDate", key: "entryDate" },
    { title: "Entry price", dataIndex: "entryPrice", key: "entryPrice", render: formatNumber },
    { title: "Entry close", dataIndex: "entryClose", key: "entryClose", render: formatNumber },
    { title: "SMA100", dataIndex: "sma100", key: "sma100", render: formatNumber },
    { title: "% From SMA100", dataIndex: "pctToSma100", key: "pctToSma100", render: formatPercent },
    { title: "SMA200", dataIndex: "sma200", key: "sma200", render: formatNumber },
    { title: "% From SMA200", dataIndex: "pctToSma200", key: "pctToSma200", render: formatPercent },
    { title: "Abs % To SMA200", dataIndex: "distanceToSma200AbsPct", key: "distanceToSma200AbsPct", render: formatPercent },
    { title: "RSI14", dataIndex: "rsi14", key: "rsi14", render: formatNumber },
    { title: "DD 20D High", dataIndex: "drawdownFromHigh20Pct", key: "drawdownFromHigh20Pct", render: formatPercent },
    { title: "DD 60D High", dataIndex: "drawdownFromHigh60Pct", key: "drawdownFromHigh60Pct", render: formatPercent },
    { title: "Red days", dataIndex: "consecutiveRedDays", key: "consecutiveRedDays" },
    { title: "3D move", dataIndex: "move3dPct", key: "move3dPct", render: formatPercent },
    { title: "10D profit", key: "return10dPct", render: (_, row) => <Text style={{ color: returnColor(row.return10dPct) }}>{formatPercent(row.return10dPct)}</Text> },
    { title: "20D profit", key: "return20dPct", render: (_, row) => <Text style={{ color: returnColor(row.return20dPct) }}>{formatPercent(row.return20dPct)}</Text> },
    { title: "40D profit", key: "return40dPct", render: (_, row) => <Text style={{ color: returnColor(row.return40dPct) }}>{formatPercent(row.return40dPct)}</Text> },
  ];

  const runBacktest = (): void => {
    if (!selectedInstrument) return;
    void run({ symbol: selectedInstrument.trading_symbol, instrumentToken: selectedInstrument.instrument_token, entrySmaPeriod });
  };

  return (
    <div style={{ padding: 24 }}>
      <Space orientation="vertical" size={16} style={{ width: "100%" }}>
        <Card>
          <Space orientation="vertical" size={12} style={{ width: "100%" }}>
            <Title level={3} style={{ margin: 0 }}>SMA Limit-Entry Backtest</Title>
            <Text type="secondary">One trade at a time. Enter at the selected SMA when the day&apos;s low touches it, then measure the close after 10, 20, and 40 trading days.</Text>
            <div style={{ maxWidth: 420 }}>
              {instrumentsLoading ? <Spin size="small" /> : <InstrumentSearch instruments={nseEquities} value={selectedInstrument} onSelect={setSelectedInstrument} placeholder="Search an NSE equity" />}
              {instrumentsError && <Text type="danger">{instrumentsError}</Text>}
            </div>
            <Space wrap>
              <Select
                aria-label="Entry SMA"
                value={entrySmaPeriod}
                onChange={setEntrySmaPeriod}
                options={[50, 100, 200].map((period) => ({ label: `SMA${period}`, value: period }))}
                style={{ width: 120 }}
              />
              <Button type="primary" onClick={runBacktest} loading={loading} disabled={!selectedInstrument}>Run {selectedInstrument?.trading_symbol ?? "Stock"} Backtest</Button>
            </Space>
          </Space>
        </Card>

        {error && <Alert type="error" message={error} showIcon />}

        {data && <>
          <Card title={`${data.symbol}: SMA${data.entrySmaPeriod} entry | ${data.testedFromDate} to ${data.testedToDate}`}>
            <Row gutter={[12, 12]}>
              <Metric title={`SMA${data.entrySmaPeriod} touches`} value={data.summary.smaTouchCount} />
              <Metric title="Trades entered" value={data.summary.tradeCount} />
              <Metric title="Touches ignored" value={data.summary.ignoredTouchCount} />
              <Metric title="Completed 10D" value={data.summary.completed10dCount} />
              <Metric title="Completed 20D" value={data.summary.completed20dCount} />
              <Metric title="Completed 40D" value={data.summary.completed40dCount} />
            </Row>
          </Card>
          <Card title="Trade entries and forward returns">
            <Table rowKey={(row) => row.entryDate} columns={columns} dataSource={data.trades} pagination={{ pageSize: 20 }} scroll={{ x: 2200 }} size="small" />
          </Card>
        </>}
      </Space>
    </div>
  );
}

function Metric({ title, value }: { title: string; value: number }): JSX.Element {
  return <Col xs={12} sm={8} lg={4}><Statistic title={title} value={value} /></Col>;
}
