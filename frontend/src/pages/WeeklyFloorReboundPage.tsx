import { Alert, Button, Card, Col, Input, InputNumber, Row, Space, Spin, Statistic, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useMemo, useState } from "react";
import { InstrumentSearch } from "../components/InstrumentSearch";
import { useInstrumentSearch } from "../hooks/useInstrumentSearch";
import { useWeeklyFloorReboundBacktest } from "../hooks/useWeeklyFloorReboundBacktest";
import type { InstrumentSearchResult, WeeklyFloorReboundDailyRow, WeeklyFloorReboundRow } from "../types";

const { Text, Title } = Typography;

function formatNumber(value: number | null): string {
  return value == null ? "-" : value.toLocaleString("en-IN", { maximumFractionDigits: 2, minimumFractionDigits: 2 });
}

function formatPercent(value: number | null): string {
  return value == null ? "-" : `${formatNumber(value)}%`;
}

function outcomeColor(outcome: string): string {
  if (outcome === "TARGET_HIT") return "#389e0d";
  if (outcome === "STOP_LOSS") return "#cf1322";
  return "#595959";
}

export function WeeklyFloorReboundPage() {
  const [selectedInstrument, setSelectedInstrument] = useState<InstrumentSearchResult | null>(null);
  const [supportFloor, setSupportFloor] = useState<number | null>(null);
  const [supportCeiling, setSupportCeiling] = useState<number | null>(null);
  const [activeFrom, setActiveFrom] = useState("");
  const { allInstruments, loading: instrumentsLoading, error: instrumentsError } = useInstrumentSearch();
  const { data, loading, error, run } = useWeeklyFloorReboundBacktest();
  const symbol = selectedInstrument?.trading_symbol ?? "NETWEB";
  const nseEquities = useMemo(
    () => allInstruments.filter((instrument) => instrument.exchange === "NSE" && instrument.instrument_type === "EQ"),
    [allInstruments],
  );

  const columns: ColumnsType<WeeklyFloorReboundRow> = [
    { title: "Zone", dataIndex: "zoneId", key: "zoneId" },
    { title: "Active from", dataIndex: "zoneCreatedDate", key: "zoneCreatedDate" },
    { title: "Outcome", dataIndex: "outcome", key: "outcome", render: (outcome: string) => <Text style={{ color: outcomeColor(outcome) }}>{outcome}</Text> },
    { title: "Support zone", key: "zone", render: (_, row) => `₹${formatNumber(row.zoneFloor)} – ₹${formatNumber(row.zoneCeiling)}` },
    { title: "Test", key: "test", render: (_, row) => row.testDate == null ? "-" : `${row.testDate} @ ₹${formatNumber(row.testLow)}` },
    { title: "Entry", key: "entry", render: (_, row) => row.entryDate == null ? "-" : `${row.entryDate} @ ₹${formatNumber(row.entryPrice)}` },
    { title: "Target", dataIndex: "targetPrice", key: "targetPrice", render: formatNumber },
    { title: "Exit", key: "exit", render: (_, row) => row.exitDate == null ? "-" : `${row.exitDate} @ ₹${formatNumber(row.exitPrice)}` },
    { title: "Hold (trading days)", dataIndex: "holdingTradingDays", key: "holdingTradingDays", render: (value: number | null) => value ?? "Open" },
    { title: "Return", dataIndex: "returnPct", key: "returnPct", render: (value: number | null) => <Text style={{ color: value != null && value < 0 ? "#cf1322" : "#389e0d" }}>{formatPercent(value)}</Text> },
    { title: "Flags", key: "flags", render: (_, row) => [row.gapStop && "Gap stop", row.exitWasAmbiguous && "Ambiguous"].filter(Boolean).join(", ") || "-" },
  ];
  const dailyColumns: ColumnsType<WeeklyFloorReboundDailyRow> = [
    { title: "Date", dataIndex: "date", key: "date" },
    { title: "Low", dataIndex: "low", key: "low", render: formatNumber },
    { title: "High", dataIndex: "high", key: "high", render: formatNumber },
    { title: "Manual zone", key: "base", render: (_, row) => row.baseFloor == null ? "-" : `₹${formatNumber(row.baseFloor)} – ₹${formatNumber(row.baseCeiling)} (${formatPercent(row.baseWidthPct)})` },
    { title: "1% rebound", dataIndex: "reboundTrigger", key: "reboundTrigger", render: formatNumber },
    { title: "5% target", dataIndex: "targetPrice", key: "targetPrice", render: formatNumber },
    { title: "Decision", dataIndex: "decision", key: "decision" },
  ];

  return (
    <div style={{ padding: 24 }}>
      <Space direction="vertical" size={16} style={{ width: "100%" }}>
        <Card>
          <Space direction="vertical" size={12} style={{ width: "100%" }}>
            <Title level={3} style={{ margin: 0 }}>Weekly Floor Rebound Backtest</Title>
            <Text type="secondary">You define one frozen support zone. The backtest only executes same-day 1% rebound trades with a 5% target.</Text>
            <div style={{ maxWidth: 420 }}>
              {instrumentsLoading ? <Spin size="small" /> : <InstrumentSearch instruments={nseEquities} value={selectedInstrument} onSelect={setSelectedInstrument} placeholder="NETWEB (default) or search an NSE equity" />}
              {instrumentsError && <Text type="danger">{instrumentsError}</Text>}
            </div>
            <Space wrap>
              <InputNumber placeholder="Support floor" value={supportFloor} onChange={setSupportFloor} min={0.01} />
              <InputNumber placeholder="Support ceiling" value={supportCeiling} onChange={setSupportCeiling} min={0.01} />
              <Input placeholder="Active from (YYYY-MM-DD)" value={activeFrom} onChange={(event) => setActiveFrom(event.target.value)} />
            </Space>
            <Button type="primary" onClick={() => supportFloor != null && supportCeiling != null && void run({ symbol, supportFloor, supportCeiling, activeFrom })} loading={loading} disabled={supportFloor == null || supportCeiling == null || activeFrom === ""}>Run {symbol} Backtest</Button>
          </Space>
        </Card>

        {error && <Alert type="error" message={error} showIcon />}

        {data && <>
          <Card title={`${data.symbol}: ${data.testedFromDate} to ${data.testedToDate}`}>
            <Row gutter={[12, 12]}>
              <Metric title="Trade signals" value={data.summary.zonesCreated} />
              <Metric title="Filled trades" value={data.summary.filledTrades} />
              <Metric title="5% targets hit" value={data.summary.targetHitCount} />
            </Row>
          </Card>
          <Card title="Weekly audit trail">
            <Table rowKey={(row) => row.zoneId} columns={columns} dataSource={data.trades} pagination={{ pageSize: 20 }} scroll={{ x: true }} size="small" />
          </Card>
          <Card title="Daily validation data">
            <Table rowKey={(row) => row.date} columns={dailyColumns} dataSource={data.dailyData} pagination={{ pageSize: 30 }} scroll={{ x: true }} size="small" />
          </Card>
        </>}
      </Space>
    </div>
  );
}

function Metric({ title, value, suffix }: { title: string; value: number | string | null; suffix?: string }) {
  return <Col xs={12} sm={8} lg={6}><Statistic title={title} value={value ?? "-"} precision={typeof value === "number" ? 2 : undefined} suffix={suffix} /></Col>;
}
