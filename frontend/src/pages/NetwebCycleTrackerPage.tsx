import { Alert, Button, Card, Col, Row, Space, Statistic, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useMemo } from "react";
import { useInstrumentSearch } from "../hooks/useInstrumentSearch";
import { useNetwebCycle } from "../hooks/useNetwebCycle";
import type { NetwebCyclePhase, NetwebCycleSegment, NetwebCycleSnapshot } from "../types";

const { Text, Title } = Typography;
const SYMBOL = "NETWEB";

const PHASE_LABELS: Record<NetwebCyclePhase, string> = {
  WEEKLY_ROTATION: "Weekly rotation",
  BULL_RUN: "Bull run",
  DRAWDOWN: "Drawdown",
  NEW_BASE: "New base",
};

const PHASE_COLORS: Record<NetwebCyclePhase, string> = {
  WEEKLY_ROTATION: "blue",
  BULL_RUN: "green",
  DRAWDOWN: "red",
  NEW_BASE: "gold",
};

function formatPrice(value: number | null): string {
  return value == null ? "—" : `₹${value.toLocaleString("en-IN", { maximumFractionDigits: 2 })}`;
}

function formatPct(value: number | null): string {
  return value == null ? "—" : `${value >= 0 ? "+" : ""}${value.toFixed(2)}%`;
}

function phaseLabel(phase: NetwebCyclePhase): string {
  return PHASE_LABELS[phase];
}

export function NetwebCycleTrackerPage() {
  const { loading: instrumentsLoading, error: instrumentsError } = useInstrumentSearch();
  const { data, loading, error, run } = useNetwebCycle();

  useEffect(() => {
    if (!instrumentsLoading && !instrumentsError) {
      void run({ symbol: SYMBOL });
    }
  }, [instrumentsError, instrumentsLoading, run]);

  const snapshotColumns = useMemo<ColumnsType<NetwebCycleSnapshot>>(
    () => [
      { title: "Date", dataIndex: "date", key: "date" },
      {
        title: "Phase",
        dataIndex: "phase",
        key: "phase",
        render: (phase: NetwebCyclePhase) => <Tag color={PHASE_COLORS[phase]}>{phaseLabel(phase)}</Tag>,
      },
      { title: "Close", dataIndex: "currentPrice", key: "currentPrice", render: formatPrice },
      { title: "Daily", dataIndex: "dailyChangePct", key: "dailyChangePct", render: formatPct },
      { title: "5D", dataIndex: "fiveDayReturnPct", key: "fiveDayReturnPct", render: formatPct },
      { title: "Base", key: "base", render: (_value: unknown, row: NetwebCycleSnapshot) => `${formatPrice(row.baseLow)} – ${formatPrice(row.baseHigh)}` },
      { title: "From peak", dataIndex: "drawdownFromPeakPct", key: "drawdownFromPeakPct", render: formatPct },
      { title: "Why", dataIndex: "evidence", key: "evidence", render: (evidence: string[]) => evidence[0] },
    ],
    [],
  );

  const segmentColumns = useMemo<ColumnsType<NetwebCycleSegment>>(
    () => [
      {
        title: "Phase",
        dataIndex: "phase",
        key: "phase",
        render: (phase: NetwebCyclePhase) => <Tag color={PHASE_COLORS[phase]}>{phaseLabel(phase)}</Tag>,
      },
      { title: "Period", key: "period", render: (_value: unknown, row: NetwebCycleSegment) => `${row.startDate} → ${row.endDate}` },
      { title: "Days", dataIndex: "tradingDays", key: "tradingDays" },
      { title: "Move", dataIndex: "returnPct", key: "returnPct", render: formatPct },
    ],
    [],
  );

  const current = data?.current;

  return (
    <div style={{ padding: 24 }}>
      <Space direction="vertical" size={16} style={{ width: "100%" }}>
        <Card>
          <Space direction="vertical" size={8} style={{ width: "100%" }}>
            <Title level={3} style={{ margin: 0 }}>NETWEB Cycle Tracker</Title>
            <Text type="secondary">
              Classifies the current move by its relationship to NETWEB&apos;s active base and expansion peak.
            </Text>
            <Button type="primary" onClick={() => void run({ symbol: SYMBOL })} loading={loading || instrumentsLoading}>
              Refresh {SYMBOL}
            </Button>
          </Space>
        </Card>

        {instrumentsError && <Alert type="error" message={instrumentsError} showIcon />}
        {error && <Alert type="error" message={error} showIcon />}

        {current && (
          <>
            <Card title={`Current phase — ${current.date}`}>
              <Row gutter={[16, 16]}>
                <Col xs={24} md={8}>
                  <Statistic title="Phase" value={phaseLabel(current.phase)} />
                  <Tag color={PHASE_COLORS[current.phase]}>{current.confidencePct}% confidence</Tag>
                </Col>
                <Col xs={12} md={4}><Statistic title="Close" value={formatPrice(current.currentPrice)} /></Col>
                <Col xs={12} md={4}><Statistic title="Phase age" value={`${current.phaseAgeTradingDays} days`} /></Col>
                <Col xs={12} md={4}><Statistic title="5% moves" value={current.fivePercentMoveCount} /></Col>
                <Col xs={12} md={4}><Statistic title="From peak" value={formatPct(current.drawdownFromPeakPct)} /></Col>
              </Row>
              <Card size="small" style={{ marginTop: 16, background: "#fafafa" }}>
                <Text strong>Candidate action: </Text>{current.action}
                <div style={{ marginTop: 8 }}>
                  <Text strong>Active base: </Text>{formatPrice(current.baseLow)} – {formatPrice(current.baseHigh)}
                  {current.baseWidthPct != null && <Text type="secondary"> ({current.baseWidthPct.toFixed(2)}% wide)</Text>}
                </div>
              </Card>
              <div style={{ marginTop: 16 }}>
                <Text strong>Evidence</Text>
                <ul style={{ marginTop: 8, marginBottom: 0 }}>
                  {current.evidence.map((item) => <li key={item}>{item}</li>)}
                </ul>
              </div>
            </Card>

            <Card title="Phase history">
              <Table rowKey={(row) => `${row.startDate}-${row.phase}`} columns={segmentColumns} dataSource={data.segments} pagination={false} size="small" scroll={{ x: true }} />
            </Card>

            <Card title={`Daily evidence — ${data.testedFromDate} to ${data.testedToDate}`}>
              <Table rowKey="date" columns={snapshotColumns} dataSource={data.dailySnapshots} pagination={{ pageSize: 20 }} size="small" scroll={{ x: true }} />
            </Card>
          </>
        )}
      </Space>
    </div>
  );
}
