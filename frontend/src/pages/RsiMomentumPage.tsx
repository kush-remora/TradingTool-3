import { Alert, Button, Card, Col, Empty, Row, Space, Spin, Statistic, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { ReloadOutlined } from "@ant-design/icons";
import { useMemo } from "react";
import { useRsiMomentum } from "../hooks/useRsiMomentum";
import type { RsiMomentumRankedStock } from "../types";

const candidateColumns: ColumnsType<RsiMomentumRankedStock> = [
  {
    title: "Rank",
    dataIndex: "rank",
    key: "rank",
    width: 72,
    sorter: (a, b) => a.rank - b.rank,
    render: (rank: number) => <Typography.Text strong>#{rank}</Typography.Text>,
  },
  {
    title: "Stock",
    dataIndex: "symbol",
    key: "symbol",
    width: 220,
    render: (symbol: string, row: RsiMomentumRankedStock) => (
      <Space direction="vertical" size={0}>
        <Typography.Text strong>{symbol}</Typography.Text>
        <Typography.Text type="secondary" style={{ fontSize: 11 }}>
          {row.companyName}
        </Typography.Text>
      </Space>
    ),
  },
  {
    title: "Avg RSI",
    dataIndex: "avgRsi",
    key: "avgRsi",
    width: 110,
    sorter: (a, b) => a.avgRsi - b.avgRsi,
    render: (value: number) => (
      <Typography.Text strong style={{ color: value >= 60 ? "#1677ff" : undefined }}>
        {value.toFixed(2)}
      </Typography.Text>
    ),
  },
  {
    title: "RSI 22",
    dataIndex: "rsi22",
    key: "rsi22",
    width: 95,
    render: (value: number) => value.toFixed(2),
  },
  {
    title: "RSI 44",
    dataIndex: "rsi44",
    key: "rsi44",
    width: 95,
    render: (value: number) => value.toFixed(2),
  },
  {
    title: "RSI 66",
    dataIndex: "rsi66",
    key: "rsi66",
    width: 95,
    render: (value: number) => value.toFixed(2),
  },
  {
    title: "Avg Value",
    dataIndex: "avgTradedValueCr",
    key: "avgTradedValueCr",
    width: 120,
    sorter: (a, b) => a.avgTradedValueCr - b.avgTradedValueCr,
    render: (value: number) => `₹${value.toFixed(2)}Cr`,
  },
  {
    title: "Source",
    key: "source",
    width: 170,
    render: (_, row: RsiMomentumRankedStock) => (
      <Space size={4} wrap>
        {row.inBaseUniverse && <Tag color="blue">Base</Tag>}
        {row.inWatchlist && <Tag color="gold">Watchlist</Tag>}
      </Space>
    ),
  },
];

const holdingColumns: ColumnsType<RsiMomentumRankedStock> = [
  ...candidateColumns.slice(0, 3),
  {
    title: "Weight",
    dataIndex: "targetWeightPct",
    key: "targetWeightPct",
    width: 100,
    render: (value: number | null) => (value == null ? "-" : `${value.toFixed(2)}%`),
  },
  candidateColumns[6],
  candidateColumns[7],
];

export function RsiMomentumPage() {
  const { data, loading, refreshing, error, refresh } = useRsiMomentum();

  const lastRunText = useMemo(() => {
    if (!data?.runAt) return "Never";
    return new Date(data.runAt).toLocaleString();
  }, [data?.runAt]);

  const staleDescription = data?.stale
    ? "This page only reads the cached snapshot. It does not scan 250 stocks during normal page load."
    : null;

  return (
    <div style={{ padding: 24, background: "#f5f7fa", minHeight: "calc(100vh - 48px)" }}>
      <Space direction="vertical" size={16} style={{ width: "100%" }}>
        <div style={{ display: "flex", justifyContent: "space-between", gap: 16, alignItems: "center", flexWrap: "wrap" }}>
          <div>
            <Typography.Title level={4} style={{ margin: 0 }}>
              RSI Momentum
            </Typography.Title>
            <Typography.Text type="secondary">
              Weekly cached portfolio view. Top 20 board, top 10 holdings, and rebalance diff.
            </Typography.Text>
          </div>

          <Button
            type="primary"
            icon={<ReloadOutlined />}
            onClick={() => void refresh()}
            loading={refreshing}
          >
            Refresh Snapshot
          </Button>
        </div>

        {error && <Alert type="error" showIcon message={error} />}

        {data?.stale && (
          <Alert
            type="warning"
            showIcon
            message={data.message || "Latest momentum snapshot is stale."}
            description={staleDescription ?? undefined}
          />
        )}

        {!data?.stale && data?.message && (
          <Alert type="info" showIcon message={data.message} />
        )}

        {loading ? (
          <div style={{ textAlign: "center", padding: 64 }}>
            <Spin size="large" />
          </div>
        ) : !data || !data.available ? (
          <Card>
            <Empty description="No RSI momentum snapshot available yet." />
          </Card>
        ) : (
          <Space direction="vertical" size={16} style={{ width: "100%" }}>
            <Row gutter={[12, 12]}>
              <Col xs={12} md={6}>
                <Card size="small">
                  <Statistic title="Universe" value={data.resolvedUniverseCount} />
                </Card>
              </Col>
              <Col xs={12} md={6}>
                <Card size="small">
                  <Statistic title="Eligible" value={data.eligibleUniverseCount} />
                </Card>
              </Col>
              <Col xs={12} md={6}>
                <Card size="small">
                  <Statistic title="Top 20" value={data.topCandidates.length} />
                </Card>
              </Col>
              <Col xs={12} md={6}>
                <Card size="small">
                  <Statistic title="Holdings" value={data.holdings.length} />
                </Card>
              </Col>
            </Row>

            <Card size="small" style={{ borderRadius: 10 }}>
              <Space size={[8, 8]} wrap>
                <Tag color="blue">{data.config.baseUniversePreset}</Tag>
                <Tag>Run: {lastRunText}</Tag>
                {data.asOfDate && <Tag>As of: {data.asOfDate}</Tag>}
                <Tag color="green">Rebalance: {data.config.rebalanceDay} {data.config.rebalanceTime}</Tag>
                <Tag>Liquidity: ₹{data.config.minAverageTradedValue.toFixed(1)}Cr+</Tag>
              </Space>
            </Card>

            <Row gutter={[12, 12]}>
              <Col xs={24} xl={10}>
                <Card title="Top 10 Holdings" size="small" style={{ borderRadius: 10 }}>
                  <Table
                    columns={holdingColumns}
                    dataSource={data.holdings}
                    rowKey="symbol"
                    pagination={false}
                    size="small"
                    scroll={{ x: 760 }}
                  />
                </Card>
              </Col>

              <Col xs={24} xl={14}>
                <Card title="Rebalance" size="small" style={{ borderRadius: 10 }}>
                  <RebalanceGroup label="Entries" color="green" values={data.rebalance.entries} />
                  <RebalanceGroup label="Exits" color="red" values={data.rebalance.exits} />
                  <RebalanceGroup label="Holds" color="blue" values={data.rebalance.holds} />
                </Card>
              </Col>
            </Row>

            <Card title="Top 20 Ranked Board" size="small" style={{ borderRadius: 10 }}>
              <Table
                columns={candidateColumns}
                dataSource={data.topCandidates}
                rowKey="symbol"
                size="small"
                pagination={false}
                scroll={{ x: 980 }}
              />
            </Card>

            <Card title="Diagnostics" size="small" style={{ borderRadius: 10 }}>
              <Space direction="vertical" size={12} style={{ width: "100%" }}>
                <DiagnosticLine label="Base universe count" value={String(data.diagnostics.baseUniverseCount)} />
                <DiagnosticLine label="Watchlist count" value={String(data.diagnostics.watchlistCount)} />
                <DiagnosticLine label="Watchlist additions" value={String(data.diagnostics.watchlistAdditionsCount)} />
                <DiagnosticTags label="Backfilled" values={data.diagnostics.backfilledSymbols} color="cyan" />
                <DiagnosticTags label="Unresolved" values={data.diagnostics.unresolvedSymbols} color="red" />
                <DiagnosticTags label="Insufficient history" values={data.diagnostics.insufficientHistorySymbols} color="orange" />
                <DiagnosticTags label="Illiquid" values={data.diagnostics.illiquidSymbols} color="volcano" />
                <DiagnosticTags label="Failed" values={data.diagnostics.failedSymbols} color="magenta" />
              </Space>
            </Card>
          </Space>
        )}
      </Space>
    </div>
  );
}

function RebalanceGroup({
  label,
  color,
  values,
}: {
  label: string;
  color: string;
  values: string[];
}) {
  return (
    <div style={{ marginBottom: 12 }}>
      <Typography.Text strong style={{ display: "block", marginBottom: 6 }}>
        {label}
      </Typography.Text>
      {values.length === 0 ? (
        <Typography.Text type="secondary">None</Typography.Text>
      ) : (
        <Space size={[6, 6]} wrap>
          {values.map((value) => (
            <Tag key={`${label}-${value}`} color={color}>
              {value}
            </Tag>
          ))}
        </Space>
      )}
    </div>
  );
}

function DiagnosticLine({ label, value }: { label: string; value: string }) {
  return (
    <div style={{ display: "flex", justifyContent: "space-between", gap: 12 }}>
      <Typography.Text type="secondary">{label}</Typography.Text>
      <Typography.Text strong>{value}</Typography.Text>
    </div>
  );
}

function DiagnosticTags({
  label,
  values,
  color,
}: {
  label: string;
  values: string[];
  color: string;
}) {
  return (
    <div>
      <Typography.Text type="secondary" style={{ display: "block", marginBottom: 6 }}>
        {label}
      </Typography.Text>
      {values.length === 0 ? (
        <Typography.Text>None</Typography.Text>
      ) : (
        <Space size={[6, 6]} wrap>
          {values.map((value) => (
            <Tag key={`${label}-${value}`} color={color}>
              {value}
            </Tag>
          ))}
        </Space>
      )}
    </div>
  );
}

