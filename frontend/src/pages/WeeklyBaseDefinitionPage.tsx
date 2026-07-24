import { Alert, Button, Card, Space, Spin, Statistic, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useMemo, useState } from "react";
import { InstrumentSearch } from "../components/InstrumentSearch";
import { useInstrumentSearch } from "../hooks/useInstrumentSearch";
import { useWeeklyBaseDefinition } from "../hooks/useWeeklyBaseDefinition";
import type { InstrumentSearchResult, WeeklyBaseDefinitionRow } from "../types";

const { Text, Title } = Typography;

function formatPrice(value: number): string {
  return `₹${value.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

export function WeeklyBaseDefinitionPage() {
  const [selectedInstrument, setSelectedInstrument] = useState<InstrumentSearchResult | null>(null);
  const { allInstruments, loading: instrumentsLoading, error: instrumentsError } = useInstrumentSearch();
  const { data, loading, error, run } = useWeeklyBaseDefinition();
  const symbol = selectedInstrument?.trading_symbol ?? "NETWEB";
  const nseEquities = useMemo(
    () => allInstruments.filter((instrument) => instrument.exchange === "NSE" && instrument.instrument_type === "EQ"),
    [allInstruments],
  );
  const validRows = useMemo(() => data?.rows.filter((row) => row.isValid) ?? [], [data]);

  const columns: ColumnsType<WeeklyBaseDefinitionRow> = [
    { title: "Evaluation date", dataIndex: "evaluationDate", key: "evaluationDate" },
    { title: "Week 1 low", key: "firstWeek", render: (_, row) => `${row.firstWeekStartDate}: ${formatPrice(row.firstWeekLow)}` },
    { title: "Week 2 low", key: "secondWeek", render: (_, row) => `${row.secondWeekStartDate}: ${formatPrice(row.secondWeekLow)}` },
    { title: "Week 3 low", key: "thirdWeek", render: (_, row) => `${row.thirdWeekStartDate}: ${formatPrice(row.thirdWeekLow)}` },
    { title: "Support zone", key: "zone", render: (_, row) => `${formatPrice(row.zoneFloor)} – ${formatPrice(row.zoneCeiling)}` },
    { title: "Width", dataIndex: "zoneWidthPct", key: "zoneWidthPct", render: (value: number) => `${value.toFixed(2)}%` },
    { title: "200 SMA", dataIndex: "sma200", key: "sma200", render: formatPrice },
    { title: "SMA distance", dataIndex: "distanceFromSma200Pct", key: "distanceFromSma200Pct", render: (value: number) => `${value.toFixed(2)}%` },
    { title: "Base", key: "isValid", render: (_, row) => <Tag color={row.isValid ? "green" : "default"}>{formatValidityReason(row.validityReason)}</Tag> },
  ];

  return (
    <div style={{ padding: 24 }}>
      <Space direction="vertical" size={16} style={{ width: "100%" }}>
        <Card>
          <Space direction="vertical" size={12} style={{ width: "100%" }}>
            <Title level={3} style={{ margin: 0 }}>Weekly Base Definition</Title>
            <Text type="secondary">A valid base needs three weekly lows within the configured width and a close inside the configured 200-SMA range.</Text>
            <div style={{ maxWidth: 420 }}>
              {instrumentsLoading ? <Spin size="small" /> : <InstrumentSearch instruments={nseEquities} value={selectedInstrument} onSelect={setSelectedInstrument} placeholder="NETWEB (default) or search an NSE equity" />}
              {instrumentsError && <Text type="danger">{instrumentsError}</Text>}
            </div>
            <Button type="primary" onClick={() => void run({ symbol })} loading={loading}>Find {symbol} Bases</Button>
          </Space>
        </Card>

        {error && <Alert type="error" message={error} showIcon />}

        {data && <>
          <Card title={`${data.symbol}: ${data.testedFromDate} to ${data.testedToDate}`}>
            <Statistic title="Valid daily base checks" value={data.validBaseCount} />
          </Card>
          <Card title={`Valid bases (${validRows.length})`}>
            <Table rowKey="evaluationDate" columns={columns} dataSource={validRows} pagination={{ pageSize: 20 }} scroll={{ x: true }} size="small" />
          </Card>
          <Card title="Daily base checks">
            <Table rowKey="evaluationDate" columns={columns} dataSource={data.rows} pagination={{ pageSize: 30 }} scroll={{ x: true }} size="small" />
          </Card>
        </>}
      </Space>
    </div>
  );
}

function formatValidityReason(value: string): string {
  return value.replaceAll("_", " ");
}
