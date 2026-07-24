import { Alert, Button, Card, Col, Row, Space, Spin, Statistic, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useMemo, useState } from "react";
import { InstrumentSearch } from "../components/InstrumentSearch";
import { useInstrumentSearch } from "../hooks/useInstrumentSearch";
import { useWeeklyFloorReboundBacktest } from "../hooks/useWeeklyFloorReboundBacktest";
import type { InstrumentSearchResult, WeeklyFloorReboundRow } from "../types";

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
  const { allInstruments, loading: instrumentsLoading, error: instrumentsError } = useInstrumentSearch();
  const { data, loading, error, run } = useWeeklyFloorReboundBacktest();
  const symbol = selectedInstrument?.trading_symbol ?? "NETWEB";
  const nseEquities = useMemo(
    () => allInstruments.filter((instrument) => instrument.exchange === "NSE" && instrument.instrument_type === "EQ"),
    [allInstruments],
  );

  const columns: ColumnsType<WeeklyFloorReboundRow> = [
    { title: "Setup", dataIndex: "setupDate", key: "setupDate" },
    { title: "Outcome", dataIndex: "outcome", key: "outcome", render: (outcome: string) => <Text style={{ color: outcomeColor(outcome) }}>{outcome}</Text> },
    { title: "Reason", dataIndex: "eligibilityReason", key: "eligibilityReason", render: (value: string | null) => value ?? "-" },
    { title: "Floor", dataIndex: "baseFloor", key: "baseFloor", render: (value: number | null) => formatNumber(value) },
    { title: "Entry", key: "entry", render: (_, row) => row.entryDate == null ? "-" : `${row.entryDate} @ ₹${formatNumber(row.entryPrice)}` },
    { title: "Stop / Target", key: "risk", render: (_, row) => row.stopPrice == null ? "-" : `₹${formatNumber(row.stopPrice)} / ₹${formatNumber(row.targetPrice)}` },
    { title: "Exit", key: "exit", render: (_, row) => row.exitDate == null ? "-" : `${row.exitDate} @ ₹${formatNumber(row.exitPrice)}` },
    { title: "Return", dataIndex: "returnPct", key: "returnPct", render: (value: number | null) => <Text style={{ color: value != null && value < 0 ? "#cf1322" : "#389e0d" }}>{formatPercent(value)}</Text> },
    { title: "Flags", key: "flags", render: (_, row) => [row.gapEntry && "Gap entry", row.gapStop && "Gap stop", row.exitWasAmbiguous && "Ambiguous"].filter(Boolean).join(", ") || "-" },
  ];

  return (
    <div style={{ padding: 24 }}>
      <Space direction="vertical" size={16} style={{ width: "100%" }}>
        <Card>
          <Space direction="vertical" size={12} style={{ width: "100%" }}>
            <Title level={3} style={{ margin: 0 }}>Weekly Floor Rebound Backtest</Title>
            <Text type="secondary">Tests the latest 200 trading sessions. Default: NETWEB. Gross returns only; this is a price-structure experiment, not an accumulation verdict.</Text>
            <div style={{ maxWidth: 420 }}>
              {instrumentsLoading ? <Spin size="small" /> : <InstrumentSearch instruments={nseEquities} value={selectedInstrument} onSelect={setSelectedInstrument} placeholder="NETWEB (default) or search an NSE equity" />}
              {instrumentsError && <Text type="danger">{instrumentsError}</Text>}
            </div>
            <Button type="primary" onClick={() => void run({ symbol })} loading={loading}>Run {symbol} Backtest</Button>
          </Space>
        </Card>

        {error && <Alert type="error" message={error} showIcon />}

        {data && <>
          <Card title={`${data.symbol}: ${data.testedFromDate} to ${data.testedToDate}`}>
            <Row gutter={[12, 12]}>
              <Metric title="Reviewed weeks" value={data.summary.reviewedWeeks} />
              <Metric title="Eligible / filled" value={`${data.summary.eligibleSetups} / ${data.summary.filledTrades}`} />
              <Metric title="Targets / stops" value={`${data.summary.targetHitCount} / ${data.summary.stopLossCount}`} />
              <Metric title="Friday exits" value={data.summary.fridayExitCount} />
              <Metric title="Win rate" value={data.summary.winRatePct} suffix="%" />
              <Metric title="Average return" value={data.summary.averageReturnPct} suffix="%" />
              <Metric title="Expectancy" value={data.summary.expectancyPct} suffix="%" />
              <Metric title="Profit factor" value={data.summary.profitFactor} />
              <Metric title="Max drawdown" value={data.summary.maxDrawdownPct} suffix="%" />
            </Row>
          </Card>
          <Card title="Weekly audit trail">
            <Table rowKey={(row) => row.setupDate} columns={columns} dataSource={data.trades} pagination={{ pageSize: 20 }} scroll={{ x: true }} size="small" />
          </Card>
        </>}
      </Space>
    </div>
  );
}

function Metric({ title, value, suffix }: { title: string; value: number | string | null; suffix?: string }) {
  return <Col xs={12} sm={8} lg={6}><Statistic title={title} value={value ?? "-"} precision={typeof value === "number" ? 2 : undefined} suffix={suffix} /></Col>;
}
