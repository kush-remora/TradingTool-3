import { ReloadOutlined } from "@ant-design/icons";
import { Alert, Button, Card, Col, Empty, Row, Space, Spin, Statistic, Table, Tabs, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useMemo, useState } from "react";
import { useStockDetail } from "../hooks/useStockDetail";
import { useS4VolumeSpike } from "../hooks/useS4VolumeSpike";
import type { DayDetail, S4RankedCandidate, S4Snapshot } from "../types";

const classificationColors: Record<string, string> = {
  FRESH_SPIKE: "green",
  BUILDUP_BREAKOUT: "blue",
  EXTENDED_SPIKE: "orange",
};

const formatPct = (value: number | null | undefined): string => {
  if (typeof value !== "number") {
    return "-";
  }
  return `${value.toFixed(2)}%`;
};

const formatRatio = (value: number | null | undefined): string => {
  if (typeof value !== "number") {
    return "-";
  }
  return `${value.toFixed(2)}x`;
};

const formatCrore = (value: number | null | undefined): string => {
  if (typeof value !== "number") {
    return "-";
  }
  return `₹${value.toFixed(2)}Cr`;
};

const formatPoints = (value: number | null | undefined, maxPoints: number): string => {
  if (typeof value !== "number") {
    return "-";
  }
  return `${value.toFixed(2)} / ${maxPoints}`;
};

const drilldownColumns: ColumnsType<DayDetail> = [
  {
    title: "Date",
    dataIndex: "date",
    key: "date",
    width: 110,
  },
  {
    title: "Close",
    dataIndex: "close",
    key: "close",
    width: 90,
    render: (value: number) => value.toFixed(2),
  },
  {
    title: "Change %",
    dataIndex: "daily_change_pct",
    key: "daily_change_pct",
    width: 100,
    render: (value: number | null) => formatPct(value),
  },
  {
    title: "Volume",
    dataIndex: "volume",
    key: "volume",
    width: 110,
    render: (value: number) => value.toLocaleString("en-IN"),
  },
  {
    title: "Vol Ratio",
    dataIndex: "vol_ratio",
    key: "vol_ratio",
    width: 100,
    render: (value: number | null) => formatRatio(value),
  },
  {
    title: "RSI 14",
    dataIndex: "rsi14",
    key: "rsi14",
    width: 90,
    render: (value: number | null) => (typeof value === "number" ? value.toFixed(2) : "-"),
  },
];

function buildClassificationNarrative(candidate: S4RankedCandidate): string {
  if (candidate.classification === "FRESH_SPIKE") {
    return "Today is the first truly outsized volume burst. Recent 10-day buildup stayed limited, so the move still looks fresh.";
  }
  if (candidate.classification === "EXTENDED_SPIKE") {
    return "Volume has stayed elevated for a long stretch. The move is still strong, but it is no longer an early signal.";
  }
  return "Volume has been building for several sessions before the breakout. This is a buildup move, not a one-day surprise.";
}

function buildCandidateColumns(): ColumnsType<S4RankedCandidate> {
  return [
    {
      title: "Rank",
      dataIndex: "rank",
      key: "rank",
      width: 72,
      render: (rank: number) => <Typography.Text strong>#{rank}</Typography.Text>,
    },
    {
      title: "Symbol",
      dataIndex: "symbol",
      key: "symbol",
      width: 180,
      render: (symbol: string, row: S4RankedCandidate) => (
        <Space orientation="vertical" size={0}>
          <Typography.Text strong>{symbol}</Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 11 }}>
            {row.companyName}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: "Class",
      dataIndex: "classification",
      key: "classification",
      width: 150,
      render: (classification: string) => (
        <Tag color={classificationColors[classification] ?? "default"} style={{ margin: 0 }}>
          {classification}
        </Tag>
      ),
    },
    {
      title: "Index Layer",
      key: "indexLayer",
      width: 120,
      render: (_: unknown, row: S4RankedCandidate) => (
        <Space orientation="vertical" size={0}>
          <Typography.Text strong>{`${row.indexRank} / ${row.indexSize}`}</Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 11 }}>
            {row.indexLayer}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: "Score",
      dataIndex: "score",
      key: "score",
      width: 90,
      sorter: (a, b) => a.score - b.score,
      render: (value: number) => <Typography.Text strong>{value.toFixed(2)}</Typography.Text>,
    },
    {
      title: "Today Vol",
      dataIndex: "todayVolumeRatio",
      key: "todayVolumeRatio",
      width: 120,
      render: (value: number) => formatRatio(value),
    },
    {
      title: "3D Vol",
      dataIndex: "recent3dAvgVolumeRatio",
      key: "recent3dAvgVolumeRatio",
      width: 110,
      render: (value: number) => formatRatio(value),
    },
    {
      title: "5D Max",
      dataIndex: "recent5dMaxVolumeRatio",
      key: "recent5dMaxVolumeRatio",
      width: 110,
      render: (value: number) => formatRatio(value),
    },
    {
      title: "Persist",
      dataIndex: "spikePersistenceDays5d",
      key: "spikePersistenceDays5d",
      width: 95,
      render: (value: number) => <Tag color="blue">{value}d</Tag>,
    },
    {
      title: "Today %",
      dataIndex: "todayPriceChangePct",
      key: "todayPriceChangePct",
      width: 100,
      render: (value: number) => <Typography.Text>{formatPct(value)}</Typography.Text>,
    },
    {
      title: "3D %",
      dataIndex: "priceReturn3dPct",
      key: "priceReturn3dPct",
      width: 100,
      render: (value: number) => <Typography.Text>{formatPct(value)}</Typography.Text>,
    },
    {
      title: "Breakout",
      dataIndex: "breakoutAbove20dHigh",
      key: "breakoutAbove20dHigh",
      width: 100,
      render: (value: boolean) => <Tag color={value ? "green" : "default"}>{value ? "YES" : "NO"}</Tag>,
    },
    {
      title: "Avg Value",
      dataIndex: "avgTradedValueCr20d",
      key: "avgTradedValueCr20d",
      width: 120,
      render: (value: number) => formatCrore(value),
    },
  ];
}

const volumeViewColumns: ColumnsType<S4RankedCandidate> = [
  {
    title: "Symbol",
    dataIndex: "symbol",
    key: "symbol",
    width: 140,
    render: (symbol: string) => <Typography.Text strong>{symbol}</Typography.Text>,
  },
  {
    title: "Avg Vol 20D",
    dataIndex: "avgVolume20d",
    key: "avgVolume20d",
    width: 130,
    render: (value: number) => value.toLocaleString("en-IN", { maximumFractionDigits: 0 }),
  },
  {
    title: "Today Vol Ratio",
    dataIndex: "todayVolumeRatio",
    key: "todayVolumeRatio",
    width: 130,
    render: (value: number) => formatRatio(value),
  },
  {
    title: "3D Avg Ratio",
    dataIndex: "recent3dAvgVolumeRatio",
    key: "recent3dAvgVolumeRatio",
    width: 130,
    render: (value: number) => formatRatio(value),
  },
  {
    title: "5D Max Ratio",
    dataIndex: "recent5dMaxVolumeRatio",
    key: "recent5dMaxVolumeRatio",
    width: 130,
    render: (value: number) => formatRatio(value),
  },
  {
    title: "Persistence",
    dataIndex: "spikePersistenceDays5d",
    key: "spikePersistenceDays5d",
    width: 110,
    render: (value: number) => `${value} days`,
  },
  {
    title: "10D Avg Ratio",
    dataIndex: "recent10dAvgVolumeRatio",
    key: "recent10dAvgVolumeRatio",
    width: 130,
    render: (value: number) => formatRatio(value),
  },
  {
    title: "10D Elevated",
    dataIndex: "elevatedVolumeDays10d",
    key: "elevatedVolumeDays10d",
    width: 120,
    render: (value: number) => `${value} days`,
  },
  {
    title: "Avg Traded Value",
    dataIndex: "avgTradedValueCr20d",
    key: "avgTradedValueCr20d",
    width: 140,
    render: (value: number) => formatCrore(value),
  },
];

function ProfileDashboard({ snapshot }: { snapshot: S4Snapshot }) {
  const [selectedCandidate, setSelectedCandidate] = useState<S4RankedCandidate | null>(snapshot.topCandidates[0] ?? null);
  const { data: detailData, loading: detailLoading, error: detailError } = useStockDetail(selectedCandidate?.symbol ?? null, 10);

  useEffect(() => {
    setSelectedCandidate((current) => {
      if (snapshot.topCandidates.length === 0) {
        return null;
      }
      if (current == null) {
        return snapshot.topCandidates[0];
      }
      return snapshot.topCandidates.find((candidate) => candidate.symbol === current.symbol) ?? snapshot.topCandidates[0];
    });
  }, [snapshot.topCandidates]);

  return (
    <Space orientation="vertical" size={16} style={{ width: "100%" }}>
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} md={6}>
          <Card>
            <Statistic title="Run At" value={snapshot.runAt ? new Date(snapshot.runAt).toLocaleString("en-IN") : "-"} />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card>
            <Statistic title="Resolved Universe" value={snapshot.resolvedUniverseCount} />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card>
            <Statistic title="Eligible Candidates" value={snapshot.eligibleUniverseCount} />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card>
            <Statistic title="Illiquid / Failed" value={`${snapshot.diagnostics.illiquidSymbols.length} / ${snapshot.diagnostics.failedSymbols.length}`} />
          </Card>
        </Col>
      </Row>

      <Card title="S4 Candidates" extra={<Typography.Text type="secondary">Qualified momentum signals ranked by volume strength and price confirmation</Typography.Text>}>
        <Table
          columns={buildCandidateColumns()}
          dataSource={snapshot.topCandidates}
          rowKey={(row) => `${row.profileId}-${row.symbol}`}
          onRow={(row) => ({
            onClick: () => setSelectedCandidate(row),
            style: { cursor: "pointer" },
          })}
          size="small"
          pagination={{ pageSize: 15, showSizeChanger: false }}
          locale={{ emptyText: snapshot.message ?? "No S4 candidates available." }}
          scroll={{ x: 1420 }}
        />
      </Card>

      <Card title="Volume Regime View" extra={<Typography.Text type="secondary">Reusable volume metrics that future strategies can build on</Typography.Text>}>
        <Table
          columns={volumeViewColumns}
          dataSource={snapshot.topCandidates}
          rowKey={(row) => `volume-${row.profileId}-${row.symbol}`}
          size="small"
          pagination={false}
          locale={{ emptyText: "No reusable volume-regime metrics available yet." }}
          scroll={{ x: 1150 }}
        />
      </Card>

      <Card title="Candidate Drilldown" extra={<Typography.Text type="secondary">Click any row above to inspect the last 10 daily sessions</Typography.Text>}>
        {selectedCandidate == null ? (
          <Empty description="Select a candidate to inspect its recent volume regime." />
        ) : (
          <Space orientation="vertical" size={16} style={{ width: "100%" }}>
            <Row gutter={[16, 16]}>
              <Col xs={24} md={8}>
                <Card size="small">
                  <Space orientation="vertical" size={4}>
                    <Typography.Text strong>{selectedCandidate.symbol}</Typography.Text>
                    <Typography.Text type="secondary">{selectedCandidate.companyName}</Typography.Text>
                    <Tag color={classificationColors[selectedCandidate.classification] ?? "default"} style={{ width: "fit-content", margin: 0 }}>
                      {selectedCandidate.classification}
                    </Tag>
                  </Space>
                </Card>
              </Col>
              <Col xs={24} md={8}>
                <Card size="small">
                  <Space orientation="vertical" size={4}>
                    <Typography.Text type="secondary">10D Volume Regime</Typography.Text>
                    <Typography.Text strong>{formatRatio(selectedCandidate.recent10dAvgVolumeRatio)}</Typography.Text>
                    <Typography.Text type="secondary">{selectedCandidate.elevatedVolumeDays10d} elevated days in the last 10</Typography.Text>
                  </Space>
                </Card>
              </Col>
              <Col xs={24} md={8}>
                <Card size="small">
                  <Space orientation="vertical" size={4}>
                    <Typography.Text type="secondary">Why this label?</Typography.Text>
                    <Typography.Text>{buildClassificationNarrative(selectedCandidate)}</Typography.Text>
                  </Space>
                </Card>
              </Col>
            </Row>

            <Card size="small" title="Score Breakdown">
              <Row gutter={[16, 16]}>
                <Col xs={24} sm={12} md={6}>
                  <Statistic title="Today Volume" value={formatPoints(selectedCandidate.todayVolumeScore, 40)} />
                </Col>
                <Col xs={24} sm={12} md={6}>
                  <Statistic title="3D Volume Regime" value={formatPoints(selectedCandidate.recent3dVolumeScore, 30)} />
                </Col>
                <Col xs={24} sm={12} md={6}>
                  <Statistic title="Persistence" value={formatPoints(selectedCandidate.persistenceScore, 15)} />
                </Col>
                <Col xs={24} sm={12} md={6}>
                  <Statistic title="Price Strength" value={formatPoints(selectedCandidate.priceScore, 15)} />
                </Col>
              </Row>
            </Card>

            {detailError ? <Alert type="error" showIcon message={detailError} /> : null}
            {detailLoading ? (
              <div style={{ textAlign: "center", padding: 24 }}>
                <Spin />
              </div>
            ) : (
              <Table
                columns={drilldownColumns}
                dataSource={detailData?.days ?? []}
                rowKey={(row) => row.date}
                size="small"
                pagination={false}
                locale={{ emptyText: "No recent market history available for this symbol." }}
                scroll={{ x: 650 }}
              />
            )}
          </Space>
        )}
      </Card>
    </Space>
  );
}

export function S4VolumeSpikePage() {
  const { data, loading, refreshing, error, refresh } = useS4VolumeSpike();
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
      <Space orientation="vertical" size={16} style={{ width: "100%" }}>
        <div style={{ display: "flex", justifyContent: "space-between", gap: 16, alignItems: "center", flexWrap: "wrap" }}>
          <div>
            <Typography.Title level={4} style={{ margin: 0 }}>
              S4 Volume Spike
            </Typography.Title>
            <Typography.Text type="secondary">
              Independent feature dashboard for ranked volume-spike candidates and reusable volume-regime analysis.
            </Typography.Text>
          </div>

          <Button type="primary" icon={<ReloadOutlined />} onClick={() => void refresh()} loading={refreshing}>
            Refresh S4
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
            <Empty description="No S4 profiles available yet." />
          </Card>
        ) : (
          <Tabs
            activeKey={activeProfileId}
            onChange={(nextProfileId) => setActiveProfileId(nextProfileId)}
            items={profiles.map((profile) => ({
              key: profile.profileId,
              label: profile.profileLabel,
              children: (
                <Space orientation="vertical" size={16} style={{ width: "100%" }}>
                  {perProfileErrors.get(profile.profileId) ? (
                    <Alert type="warning" showIcon message={perProfileErrors.get(profile.profileId)} />
                  ) : null}
                  {profile.stale ? (
                    <Alert type="warning" showIcon message={profile.message ?? "This S4 snapshot is stale."} />
                  ) : null}
                  <ProfileDashboard snapshot={profile} />
                </Space>
              ),
            }))}
          />
        )}
      </Space>
    </div>
  );
}
