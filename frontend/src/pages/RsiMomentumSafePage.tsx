import { ReloadOutlined, SafetyCertificateOutlined, TableOutlined, PlayCircleOutlined } from "@ant-design/icons";
import { Alert, Button, Card, Empty, Space, Spin, Tabs, Tag, Typography, Table, Tooltip } from "antd";
import { useEffect, useMemo, useState } from "react";
import { useRsiMomentum } from "../hooks/useRsiMomentum";
import type { RsiMomentumSnapshot } from "../types";
import { buildCandidateColumns, formatInr } from "../components/rsi/RsiBoard";
import { RsiSniperBacktest } from "../components/rsi/RsiSniperBacktest";

export function RsiMomentumSafePage() {
  const queryParams = new URLSearchParams(window.location.search);
  const historyDate = queryParams.get("date");
  const targetProfileId = queryParams.get("profileId");

  const { data, loading, refreshing, refreshingProfileId, error, refresh } = useRsiMomentum(historyDate);
  const profiles = data?.profiles ?? [];
  const [activeProfileId, setActiveProfileId] = useState<string | undefined>(undefined);

  useEffect(() => {
    if (profiles.length === 0) {
      setActiveProfileId(undefined);
      return;
    }
    
    // Priority: 1. targetProfileId from URL, 2. current activeProfileId, 3. first profile in list
    const initialId = targetProfileId || activeProfileId;
    const hasActive = initialId != null && profiles.some((p) => p.profileId === initialId);
    
    if (hasActive) {
        if (activeProfileId !== initialId) setActiveProfileId(initialId as string);
    } else {
        setActiveProfileId(profiles[0].profileId);
    }
  }, [profiles, activeProfileId, targetProfileId]);

  const handleBackToLatest = () => {
    window.location.href = window.location.pathname;
  };

  return (
    <div style={{ padding: 24, background: "#f5f7fa", minHeight: "calc(100vh - 48px)" }}>
      <Space direction="vertical" size={16} style={{ width: "100%" }}>
        <div style={{ display: "flex", justifyContent: "space-between", gap: 16, alignItems: "center", flexWrap: "wrap" }}>
          <div>
            <Typography.Title level={4} style={{ margin: 0 }}>
              <SafetyCertificateOutlined style={{ color: "#52c41a", marginRight: 8 }} />
              RSI Safe Variant {historyDate && <Tag color="blue" style={{ marginLeft: 8 }}>Historical: {historyDate}</Tag>}
            </Typography.Title>
            <Typography.Text type="secondary">
              Focused on Rank 1-15, Rank Improvement, and strict 20DMA extension rules ({"<10%"}).
            </Typography.Text>
          </div>

          <Space>
            {historyDate && (
                <Button onClick={handleBackToLatest}>Back to Latest</Button>
            )}
            {!historyDate && (
                <Button
                    type="primary"
                    icon={<ReloadOutlined />}
                    onClick={() => void refresh()}
                    loading={refreshing && refreshingProfileId == null}
                >
                    Refresh All
                </Button>
            )}
          </Space>
        </div>

        {error && <Alert type="error" showIcon message={error} />}

        {loading ? (
          <div style={{ textAlign: "center", padding: 64 }}>
            <Spin size="large" />
          </div>
        ) : profiles.length === 0 ? (
          <Card>
            <Empty description="No RSI momentum profiles available." />
          </Card>
        ) : (
          <Tabs
            activeKey={activeProfileId}
            onChange={(id) => setActiveProfileId(id)}
            items={profiles.map((profile) => ({
              key: profile.profileId,
              label: profile.config.profileLabel || profile.config.baseUniversePreset,
              children: (
                <Tabs
                  defaultActiveKey="board"
                  items={[
                    {
                      key: "board",
                      label: (
                        <span>
                          <TableOutlined /> Board
                        </span>
                      ),
                      children: (
                        <RsiSafePanel
                          snapshot={profile}
                          refreshLoading={refreshing && refreshingProfileId === profile.profileId}
                        />
                      ),
                    },
                    {
                      key: "backtest",
                      label: (
                        <span>
                          <PlayCircleOutlined /> Backtest
                        </span>
                      ),
                      children: <RsiSniperBacktest profileId={profile.profileId} />,
                    },
                  ]}
                />
              ),
            }))}
          />
        )}
      </Space>
    </div>
  );
}

function RsiSafePanel({ snapshot, refreshLoading }: { snapshot: RsiMomentumSnapshot; refreshLoading: boolean }) {
  const { safeRules } = snapshot.config;

  const safeCandidates = useMemo(() => {
    return snapshot.topCandidates
      .filter((s) => s.rank <= safeRules.initialRankFilter)
      .filter((s) => s.moveFrom3WeekLowPct <= safeRules.maxMoveFrom3WeekLowPct)
      .filter((s) => s.maxDailyMove5dPct <= safeRules.maxDailyMove5dPct)
      .filter((s) => {
        if (safeRules.minVolumeExhaustionRatio == null) return true;
        return s.volumeRatio >= safeRules.minVolumeExhaustionRatio;
      })
      .sort((a, b) => (b.rankImprovement || 0) - (a.rankImprovement || 0))
      .slice(0, safeRules.displayCount);
  }, [snapshot.topCandidates, safeRules]);

  const columns = useMemo(() => {
    const base = buildCandidateColumns(15, 20); // Dummies for threshold as we are replacing column
    
    // Replace % Above SMA20 with Move from 3W Low
    const enhanced = base.map(col => {
        if (col.key === "extensionAboveSma20Pct") {
            return {
                title: "3W Low Move",
                dataIndex: "moveFrom3WeekLowPct",
                key: "moveFrom3WeekLowPct",
                width: 140,
                sorter: (a: any, b: any) => a.moveFrom3WeekLowPct - b.moveFrom3WeekLowPct,
                render: (val: number) => (
                    <Typography.Text strong type={val > safeRules.maxMoveFrom3WeekLowPct ? "danger" : undefined}>
                        {val.toFixed(2)}%
                    </Typography.Text>
                )
            };
        }
        return col;
    });

    // Insert Rank Improvement after Rank
    enhanced.splice(1, 0, {
      title: "Rank Imp.",
      key: "rankImprovement",
      width: 100,
      sorter: (a, b) => (a.rankImprovement || 0) - (b.rankImprovement || 0),
      render: (_, row) => {
        const val = row.rankImprovement;
        if (val == null) return <Typography.Text type="secondary">-</Typography.Text>;
        const color = val > 0 ? "#52c41a" : val < 0 ? "#f5222d" : "default";
        const prefix = val > 0 ? "+" : "";
        return (
          <Tooltip title={`Rank 5d ago: #${row.rank5DaysAgo || '?'}`}>
            <Tag color={color} style={{ fontWeight: 600 }}>{prefix}{val}</Tag>
          </Tooltip>
        );
      }
    });

    // Add Max Daily Move
    enhanced.splice(6, 0, {
        title: "Max Day Move",
        dataIndex: "maxDailyMove5dPct",
        key: "maxDailyMove5dPct",
        width: 120,
        render: (val: number) => (
            <Typography.Text type={val > safeRules.maxDailyMove5dPct ? "danger" : "secondary"}>
                {val.toFixed(2)}%
            </Typography.Text>
        )
    });

    // Add Volume Ratio
    enhanced.splice(7, 0, {
        title: "Vol Ratio (3d/20d)",
        dataIndex: "volumeRatio",
        key: "volumeRatio",
        width: 140,
        render: (val: number, row) => (
            <Tooltip title={`Avg Vol 3d: ${formatQty(row.avgVol3d)} | 20d: ${formatQty(row.avgVol20d)}`}>
                <Typography.Text strong type={safeRules.minVolumeExhaustionRatio && val < safeRules.minVolumeExhaustionRatio ? "danger" : undefined}>
                    {val.toFixed(2)}x
                </Typography.Text>
            </Tooltip>
        )
    });

    return enhanced;
  }, [safeRules]);

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <Card title={`Top ${safeCandidates.length} Safe Candidates (Rank 1-${safeRules.initialRankFilter} Filtered)`} size="small">
        <Table
          columns={columns}
          dataSource={safeCandidates}
          rowKey="symbol"
          pagination={false}
          size="small"
          loading={refreshLoading}
          scroll={{ x: 2000 }}
        />
      </Card>
      
      <Alert 
        type="info" 
        showIcon 
        message="Safe Rules Applied" 
        description={
          <ul style={{ margin: 0, paddingLeft: 16 }}>
            <li>Rank must be in Top {safeRules.initialRankFilter}</li>
            <li>Price move from 3-week low must be &le; {safeRules.maxMoveFrom3WeekLowPct}% (Exhaustion Filter)</li>
            {safeRules.minVolumeExhaustionRatio != null && (
                <li>Volume Ratio (3d/20d) must be &ge; {safeRules.minVolumeExhaustionRatio}x (Conviction Filter)</li>
            )}
            <li>No single-day move &gt; {safeRules.maxDailyMove5dPct}% in last 5 days</li>
            <li>Blocked entry days: {snapshot.config.blockedEntryDays.length > 0 ? snapshot.config.blockedEntryDays.join(", ") : "None"}</li>
            <li>Sorted by Rank Improvement (Rank 5d ago vs Now)</li>
          </ul>
        }
      />
    </Space>
  );
}

function formatQty(value: number | null | undefined): string {
    if (typeof value !== "number") return "-";
    return value.toLocaleString("en-IN");
}
