import { ReloadOutlined } from "@ant-design/icons";
import { Alert, Button, Card, Col, Empty, Row, Space, Spin, Statistic, Table, Tabs, Tag, Tooltip, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useMemo, useState } from "react";
import { LiveMarketWidget } from "../components/LiveMarketWidget";
import { useLiveMarketData } from "../hooks/useLiveMarketData";
import { useRsiMomentum } from "../hooks/useRsiMomentum";
import type { RsiMomentumRankedStock, RsiMomentumSnapshot } from "../types";

const GROUP_RANGES: Array<[number, number]> = [
  [1, 10],
  [11, 20],
  [21, 30],
  [31, 40],
];

const actionColors: Record<RsiMomentumRankedStock["entryAction"], string> = {
  ENTRY: "green",
  HOLD: "blue",
  SKIP: "red",
  WATCH_PULLBACK: "orange",
  WATCH: "default",
};

const formatInr = (value: number | null | undefined): string => {
  if (typeof value !== "number") {
    return "-";
  }
  return `₹${value.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
};

const formatPct = (value: number | null | undefined): string => {
  if (typeof value !== "number") {
    return "-";
  }
  return `${value.toFixed(2)}%`;
};

const formatQty = (value: number | null | undefined): string => {
  if (typeof value !== "number") {
    return "-";
  }
  return value.toLocaleString("en-IN");
};

const formatNumber = (value: number | null | undefined): string => {
  if (typeof value !== "number") {
    return "-";
  }
  return value.toFixed(2);
};

function getExtensionColor(extensionPct: number, watchThresholdPct: number, skipThresholdPct: number): string | undefined {
  if (extensionPct > skipThresholdPct) {
    return "#cf1322";
  }
  if (extensionPct > watchThresholdPct) {
    return "#d46b08";
  }
  return undefined;
}

function renderEntryAction(row: RsiMomentumRankedStock) {
  const tag = (
    <Tag color={actionColors[row.entryAction]} style={{ margin: 0 }}>
      {row.entryAction}
    </Tag>
  );

  if (row.entryAction !== "SKIP" && row.entryAction !== "WATCH_PULLBACK") {
    return tag;
  }

  return (
    <Tooltip title={row.entryBlockReason ?? "Entry filtered by extension rule"}>
      {tag}
    </Tooltip>
  );
}

function buildCandidateColumns(
  watchThresholdPct: number,
  skipThresholdPct: number,
): ColumnsType<RsiMomentumRankedStock> {
  return [
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
      title: "Entry Action",
      key: "entryAction",
      width: 110,
      render: (_, row: RsiMomentumRankedStock) => renderEntryAction(row),
    },
    {
      title: "Avg RSI (Score)",
      dataIndex: "avgRsi",
      key: "avgRsi",
      width: 130,
      sorter: (a, b) => a.avgRsi - b.avgRsi,
      render: (value: number) => <Typography.Text strong>{formatNumber(value)}</Typography.Text>,
    },
    {
      title: "Current Price",
      dataIndex: "close",
      key: "close",
      width: 130,
      render: (value: number) => <Typography.Text strong>{formatInr(value)}</Typography.Text>,
    },
    {
      title: "LTP (Live)",
      key: "liveLtp",
      width: 170,
      render: (_, row: RsiMomentumRankedStock) => (
        <LiveMarketWidget
          symbol={`NSE:${row.symbol}`}
          fallbackLtp={row.close}
          fallbackChangePercent={null}
          showDetails={false}
          showChange
        />
      ),
    },
    {
      title: "SMA20 (20-day avg)",
      dataIndex: "sma20",
      key: "sma20",
      width: 140,
      render: (value: number) => formatInr(value),
    },
    {
      title: "% Above SMA20",
      dataIndex: "extensionAboveSma20Pct",
      key: "extensionAboveSma20Pct",
      width: 140,
      sorter: (a, b) => a.extensionAboveSma20Pct - b.extensionAboveSma20Pct,
      render: (value: number) => {
        const color = getExtensionColor(value, watchThresholdPct, skipThresholdPct);
        return (
          <Typography.Text strong style={{ color }}>
            {formatPct(value)}
          </Typography.Text>
        );
      },
    },
    {
      title: "Buy Zone (10w)",
      key: "buyZone10w",
      width: 200,
      render: (_, row: RsiMomentumRankedStock) => (
        <Typography.Text>
          {formatInr(row.buyZoneLow10w)} - {formatInr(row.buyZoneHigh10w)}
        </Typography.Text>
      ),
    },
    {
      title: "RSI Bound 50D",
      key: "rsiBounds50d",
      width: 160,
      render: (_, row: RsiMomentumRankedStock) => (
        <Typography.Text>
          {formatNumber(row.lowestRsi50d)} - {formatNumber(row.highestRsi50d)}
        </Typography.Text>
      ),
    },
    {
      title: "Pressure (B/S)",
      key: "pressure",
      width: 180,
      render: (_, row: RsiMomentumRankedStock) => <LivePressureCell symbol={row.symbol} />,
    },
    {
      title: "RSI 22/44/66",
      key: "rsiBundle",
      width: 180,
      render: (_, row: RsiMomentumRankedStock) => (
        <Typography.Text type="secondary">
          {row.rsi22.toFixed(1)} / {row.rsi44.toFixed(1)} / {row.rsi66.toFixed(1)}
        </Typography.Text>
      ),
    },
    {
      title: "Avg Value",
      dataIndex: "avgTradedValueCr",
      key: "avgTradedValueCr",
      width: 120,
      sorter: (a, b) => a.avgTradedValueCr - b.avgTradedValueCr,
      render: (value: number) => `₹${value.toFixed(2)}Cr`,
    },
  ];
}

const holdingColumns: ColumnsType<RsiMomentumRankedStock> = [
  {
    title: "Rank",
    dataIndex: "rank",
    key: "rank",
    width: 72,
    render: (rank: number) => <Typography.Text strong>#{rank}</Typography.Text>,
  },
  {
    title: "Stock",
    dataIndex: "symbol",
    key: "symbol",
    width: 200,
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
    title: "Action",
    key: "entryAction",
    width: 95,
    render: (_, row: RsiMomentumRankedStock) => renderEntryAction(row),
  },
  {
    title: "Avg RSI",
    dataIndex: "avgRsi",
    key: "avgRsi",
    width: 95,
    render: (value: number) => formatNumber(value),
  },
  {
    title: "Weight",
    dataIndex: "targetWeightPct",
    key: "targetWeightPct",
    width: 90,
    render: (value: number | null) => (value == null ? "-" : `${value.toFixed(2)}%`),
  },
  {
    title: "Price",
    dataIndex: "close",
    key: "close",
    width: 120,
    render: (value: number) => formatInr(value),
  },
  {
    title: "Buy Zone (10w)",
    key: "buyZone10w",
    width: 190,
    render: (_, row: RsiMomentumRankedStock) => (
      <Typography.Text>
        {formatInr(row.buyZoneLow10w)} - {formatInr(row.buyZoneHigh10w)}
      </Typography.Text>
    ),
  },
  {
    title: "% Above SMA20",
    dataIndex: "extensionAboveSma20Pct",
    key: "extensionAboveSma20Pct",
    width: 130,
    render: (value: number) => formatPct(value),
  },
  {
    title: "RSI Bound 50D",
    key: "rsiBounds50d",
    width: 150,
    render: (_, row: RsiMomentumRankedStock) => `${formatNumber(row.lowestRsi50d)} - ${formatNumber(row.highestRsi50d)}`,
  },
];

function buildGroupedCandidates(rows: RsiMomentumRankedStock[]) {
  return GROUP_RANGES.map(([start, end]) => ({
    key: `${start}-${end}`,
    label: `Rank ${start}-${end}`,
    rows: rows.filter((row) => row.rank >= start && row.rank <= end),
  }));
}

export function RsiMomentumPage() {
  const { data, loading, refreshing, refreshingProfileId, error, refresh } = useRsiMomentum();
  const profiles = data?.profiles ?? [];
  const [activeProfileId, setActiveProfileId] = useState<string | undefined>(undefined);

  useEffect(() => {
    if (profiles.length === 0) {
      setActiveProfileId(undefined);
      return;
    }

    const hasActive = activeProfileId != null && profiles.some((profile) => profile.profileId === activeProfileId);
    if (!hasActive) {
      setActiveProfileId(profiles[0].profileId);
    }
  }, [profiles, activeProfileId]);

  const perProfileErrors = useMemo(() => {
    const entries = (data?.errors ?? []).map((item) => [item.profileId, item.message] as const);
    return new Map(entries);
  }, [data?.errors]);

  return (
    <div style={{ padding: 24, background: "#f5f7fa", minHeight: "calc(100vh - 48px)" }}>
      <Space direction="vertical" size={16} style={{ width: "100%" }}>
        <div style={{ display: "flex", justifyContent: "space-between", gap: 16, alignItems: "center", flexWrap: "wrap" }}>
          <div>
            <Typography.Title level={4} style={{ margin: 0 }}>
              RSI Momentum
            </Typography.Title>
            <Typography.Text type="secondary">
              Multi-universe board with profile tabs, Top 40 display, and Top 20 rebalance buffer.
            </Typography.Text>
          </div>

          <Button
            type="primary"
            icon={<ReloadOutlined />}
            onClick={() => void refresh()}
            loading={refreshing && refreshingProfileId == null}
          >
            Refresh All Profiles
          </Button>
        </div>

        {error && <Alert type="error" showIcon message={error} />}
        {data?.errors.length ? (
          <Alert
            type={data.partialSuccess ? "warning" : "error"}
            showIcon
            message={data.partialSuccess ? "Partial refresh completed" : "Profile refresh failed"}
            description={data.errors.map((entry) => `${entry.profileId}: ${entry.message}`).join(" | ")}
          />
        ) : null}

        {loading ? (
          <div style={{ textAlign: "center", padding: 64 }}>
            <Spin size="large" />
          </div>
        ) : profiles.length === 0 ? (
          <Card>
            <Empty description="No RSI momentum profiles available yet." />
          </Card>
        ) : (
          <Tabs
            activeKey={activeProfileId}
            onChange={(nextProfileId) => setActiveProfileId(nextProfileId)}
            items={profiles.map((profile) => ({
              key: profile.profileId,
              label: profile.config.profileLabel || profile.config.baseUniversePreset,
              children: (
                <RsiMomentumProfilePanel
                  snapshot={profile}
                  refreshLoading={refreshing && refreshingProfileId === profile.profileId}
                  profileError={perProfileErrors.get(profile.profileId) ?? null}
                  onRetry={() => void refresh(profile.profileId)}
                />
              ),
            }))}
          />
        )}
      </Space>
    </div>
  );
}

function RsiMomentumProfilePanel({
  snapshot,
  refreshLoading,
  profileError,
  onRetry,
}: {
  snapshot: RsiMomentumSnapshot;
  refreshLoading: boolean;
  profileError: string | null;
  onRetry: () => void;
}) {
  const watchThresholdPct = snapshot.config.maxExtensionAboveSma20ForNewEntryPct
    ?? (snapshot.config.maxExtensionAboveSma20ForNewEntry * 100);
  const skipThresholdPct = snapshot.config.maxExtensionAboveSma20ForSkipNewEntryPct
    ?? (snapshot.config.maxExtensionAboveSma20ForSkipNewEntry * 100);

  const skippedCount = snapshot.topCandidates.filter((row) => row.entryAction === "SKIP").length;
  const pullbackWatchCount = snapshot.topCandidates.filter((row) => row.entryAction === "WATCH_PULLBACK").length;

  const groupedCandidates = useMemo(() => buildGroupedCandidates(snapshot.topCandidates), [snapshot.topCandidates]);
  const candidateColumns = useMemo(
    () => buildCandidateColumns(watchThresholdPct, skipThresholdPct),
    [watchThresholdPct, skipThresholdPct],
  );

  const lastRunText = useMemo(() => {
    if (!snapshot.runAt) return "Never";
    return new Date(snapshot.runAt).toLocaleString();
  }, [snapshot.runAt]);

  const staleDescription = snapshot.stale
    ? "This page reads cached snapshot data. Use refresh when you want a fresh scan."
    : null;

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 12, flexWrap: "wrap" }}>
        <Space size={[8, 8]} wrap>
          <Tag color="blue">{snapshot.config.baseUniversePreset}</Tag>
          <Tag>{snapshot.profileLabel || snapshot.config.profileLabel}</Tag>
          <Tag>Run: {lastRunText}</Tag>
          {snapshot.asOfDate && <Tag>As of: {snapshot.asOfDate}</Tag>}
        </Space>
        <Button icon={<ReloadOutlined />} loading={refreshLoading} onClick={onRetry}>
          Retry This Profile
        </Button>
      </div>

      {profileError && <Alert type="warning" showIcon message={`Profile error: ${profileError}`} />}

      {snapshot.stale && (
        <Alert
          type="warning"
          showIcon
          message={snapshot.message || "Latest momentum snapshot is stale."}
          description={staleDescription ?? undefined}
        />
      )}

      {!snapshot.stale && snapshot.message && (
        <Alert type="info" showIcon message={snapshot.message} />
      )}

      {!snapshot.available ? (
        <Card>
          <Empty description="No RSI momentum snapshot available for this profile." />
        </Card>
      ) : (
        <Space direction="vertical" size={16} style={{ width: "100%" }}>
          <Row gutter={[12, 12]}>
            <Col xs={12} md={8} xl={4}>
              <Card size="small">
                <Statistic title="Universe" value={snapshot.resolvedUniverseCount} />
              </Card>
            </Col>
            <Col xs={12} md={8} xl={4}>
              <Card size="small">
                <Statistic title="Eligible" value={snapshot.eligibleUniverseCount} />
              </Card>
            </Col>
            <Col xs={12} md={8} xl={4}>
              <Card size="small">
                <Statistic title="Board" value={snapshot.topCandidates.length} />
              </Card>
            </Col>
            <Col xs={12} md={8} xl={4}>
              <Card size="small">
                <Statistic title="Skipped" value={skippedCount} />
              </Card>
            </Col>
            <Col xs={12} md={8} xl={4}>
              <Card size="small">
                <Statistic title="Watch Pullback" value={pullbackWatchCount} />
              </Card>
            </Col>
            <Col xs={12} md={8} xl={4}>
              <Card size="small">
                <Statistic title="Holdings" value={snapshot.holdings.length} />
              </Card>
            </Col>
          </Row>

          <Card size="small" style={{ borderRadius: 10 }}>
            <Space size={[8, 8]} wrap>
              <Tag color="green">Rebalance: Top {snapshot.config.candidateCount} buffer</Tag>
              <Tag color="geekblue">Board: Top {snapshot.config.boardDisplayCount}</Tag>
              <Tag color="purple">Replacement Pool: Top {snapshot.config.replacementPoolCount}</Tag>
              <Tag color="orange">Watch Pullback If &gt; SMA20 + {formatNumber(watchThresholdPct)}%</Tag>
              <Tag color="red">Skip If &gt; SMA20 + {formatNumber(skipThresholdPct)}%</Tag>
              <Tag>Liquidity: ₹{snapshot.config.minAverageTradedValue.toFixed(1)}Cr+</Tag>
            </Space>
          </Card>

          <Row gutter={[12, 12]}>
            <Col xs={24} xl={10}>
              <Card title="Top 10 Holdings" size="small" style={{ borderRadius: 10 }}>
                <Table
                  columns={holdingColumns}
                  dataSource={snapshot.holdings}
                  rowKey="symbol"
                  pagination={false}
                  size="small"
                  scroll={{ x: 760 }}
                />
              </Card>
            </Col>

            <Col xs={24} xl={14}>
              <Card title="Rebalance" size="small" style={{ borderRadius: 10 }}>
                <RebalanceGroup label="Entries" color="green" values={snapshot.rebalance.entries} />
                <RebalanceGroup label="Exits" color="red" values={snapshot.rebalance.exits} />
                <RebalanceGroup label="Holds" color="blue" values={snapshot.rebalance.holds} />
              </Card>
            </Col>
          </Row>

          <Card title={`Top ${snapshot.config.boardDisplayCount} Ranked Board`} size="small" style={{ borderRadius: 10 }}>
            <Row gutter={[12, 12]}>
              {groupedCandidates.map((group) => (
                <Col xs={24} xl={12} key={group.key}>
                  <Card
                    size="small"
                    title={group.label}
                    styles={{ body: { padding: 0 }, header: { minHeight: 40 } }}
                  >
                    <Table
                      columns={candidateColumns}
                      dataSource={group.rows}
                      rowKey="symbol"
                      size="small"
                      pagination={false}
                      scroll={{ x: 1780 }}
                    />
                  </Card>
                </Col>
              ))}
            </Row>
          </Card>

          <Card title="Diagnostics" size="small" style={{ borderRadius: 10 }}>
            <Space direction="vertical" size={12} style={{ width: "100%" }}>
              <DiagnosticLine label="Base universe count" value={String(snapshot.diagnostics.baseUniverseCount)} />
              <DiagnosticLine label="Watchlist count" value={String(snapshot.diagnostics.watchlistCount)} />
              <DiagnosticLine label="Watchlist additions" value={String(snapshot.diagnostics.watchlistAdditionsCount)} />
              <DiagnosticTags label="Backfilled" values={snapshot.diagnostics.backfilledSymbols} color="cyan" />
              <DiagnosticTags label="Unresolved" values={snapshot.diagnostics.unresolvedSymbols} color="red" />
              <DiagnosticTags label="Insufficient history" values={snapshot.diagnostics.insufficientHistorySymbols} color="orange" />
              <DiagnosticTags label="Illiquid" values={snapshot.diagnostics.illiquidSymbols} color="volcano" />
              <DiagnosticTags label="Failed" values={snapshot.diagnostics.failedSymbols} color="magenta" />
            </Space>
          </Card>
        </Space>
      )}
    </Space>
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

function LivePressureCell({ symbol }: { symbol: string }) {
  const live = useLiveMarketData(`NSE:${symbol}`);

  if (!live) {
    return <Typography.Text type="secondary">-</Typography.Text>;
  }

  const pressureColor = live.pressureSide === "BUYERS_AGGRESSIVE"
    ? "green"
    : live.pressureSide === "SELLERS_AGGRESSIVE"
      ? "red"
      : "default";
  const pressureLabel = live.pressureSide === "BUYERS_AGGRESSIVE"
    ? "BUY"
    : live.pressureSide === "SELLERS_AGGRESSIVE"
      ? "SELL"
      : "NEUTRAL";

  return (
    <Space direction="vertical" size={2}>
      <Typography.Text style={{ fontSize: 12 }}>
        B: {formatQty(live.buyQuantity)}
      </Typography.Text>
      <Typography.Text style={{ fontSize: 12 }}>
        S: {formatQty(live.sellQuantity)}
      </Typography.Text>
      <Tag color={pressureColor} style={{ margin: 0, width: "fit-content" }}>
        {pressureLabel}
      </Tag>
    </Space>
  );
}
