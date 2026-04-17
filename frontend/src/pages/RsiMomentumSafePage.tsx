import { ReloadOutlined, SafetyCertificateOutlined, TableOutlined } from "@ant-design/icons";
import { Alert, Button, Card, Empty, Space, Spin, Tabs, Tag, Typography, Table, Tooltip } from "antd";
import { useMemo, useState, useEffect } from "react";
import { useRsiMomentum } from "../hooks/useRsiMomentum";
import { RsiMomentumSnapshot } from "../types";
import { buildCandidateColumns } from "../components/rsi/RsiBoard";
import { RsiSniperBacktest } from "../components/rsi/RsiSniperBacktest";

export default function RsiMomentumSafePage() {
  const { data, loading, error, refresh } = useRsiMomentum();
  const profiles = data?.profiles ?? [];
  const [activeKey, setActiveKey] = useState<string | undefined>(undefined);
  const [refreshing, setRefreshing] = useState(false);

  useEffect(() => {
    if (!activeKey && profiles.length > 0) {
      setActiveKey(profiles[0].profileId);
    }
  }, [profiles, activeKey]);

  const handleTabChange = (key: string) => {
    setActiveKey(key);
  };

  const handleRefresh = async () => {
    setRefreshing(true);
    try {
      await refresh();
    } finally {
      setRefreshing(false);
    }
  };

  if (loading && profiles.length === 0) return <div style={{ padding: 40, textAlign: "center" }}><Spin size="large" tip="Loading profiles..." /></div>;
  if (error) return <Alert type="error" message="Error loading profiles" description={error} showIcon style={{ margin: 24 }} />;
  if (profiles.length === 0) return <Empty description="No RSI Momentum profiles configured" style={{ marginTop: 100 }} />;

  return (
    <div style={{ padding: 24 }}>
      <Space direction="vertical" size={24} style={{ width: "100%" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <div>
            <Typography.Title level={2} style={{ margin: 0 }}>
              <SafetyCertificateOutlined style={{ marginRight: 12, color: "#52c41a" }} />
              RSI Safe (Sniper)
            </Typography.Title>
            <Typography.Text type="secondary">
              Low-risk entries based on rank improvement, price exhaustion, and volume conviction.
            </Typography.Text>
          </div>
          <Button 
            type="primary" 
            icon={<ReloadOutlined />} 
            loading={refreshing} 
            onClick={handleRefresh}
          >
            Refresh All
          </Button>
        </div>

        <Tabs
          activeKey={activeKey}
          onChange={handleTabChange}
          type="card"
          items={profiles.map((snapshot) => ({
            key: snapshot.profileId,
            label: (
              <span>
                <TableOutlined /> {snapshot.profileLabel}
              </span>
            ),
            children: <RsiSafePanel snapshot={snapshot} refreshLoading={refreshing} />,
          }))}
        />
      </Space>
    </div>
  );
}

function RsiSafePanel({ snapshot, refreshLoading }: { snapshot: RsiMomentumSnapshot; refreshLoading: boolean }) {
  const { safeRules } = snapshot.config;

  const safeCandidates = useMemo(() => {
    return snapshot.topCandidates
      .filter((s) => s.rank <= safeRules.initialRankFilter)
      .filter((s) => s.moveFrom30DayLowPct <= safeRules.maxMoveFrom30DayLowPct)
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
    
    // Replace % Above SMA20 with Move from 30D Low
    const enhanced = base.map(col => {
        if (col.key === "extensionAboveSma20Pct" || col.key === "moveFrom3WeekLowPct") {
            return {
                title: "30D Low Move",
                dataIndex: "moveFrom30DayLowPct",
                key: "moveFrom30DayLowPct",
                width: 140,
                sorter: (a: any, b: any) => a.moveFrom30DayLowPct - b.moveFrom30DayLowPct,
                render: (val: number) => (
                    <Typography.Text strong type={val > safeRules.maxMoveFrom30DayLowPct ? "danger" : undefined}>
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
            <li>Price move from 30-day low must be &le; {safeRules.maxMoveFrom30DayLowPct}% (Exhaustion Filter)</li>
            {safeRules.minVolumeExhaustionRatio != null && (
                <li>Volume Ratio (3d/20d) must be &ge; {safeRules.minVolumeExhaustionRatio}x (Conviction Filter)</li>
            )}
            <li>No single-day move &gt; {safeRules.maxDailyMove5dPct}% in last 5 days</li>
            <li>Blocked entry days: {snapshot.config.blockedEntryDays.length > 0 ? snapshot.config.blockedEntryDays.join(", ") : "None"}</li>
            <li>Sorted by Rank Improvement (Rank 5d ago vs Now)</li>
          </ul>
        }
      />

      <Card title="Configurable Backtest" size="small">
        <RsiSniperBacktest profileId={snapshot.profileId} />
      </Card>
    </Space>
  );
}

function formatQty(value: number | null | undefined): string {
    if (typeof value !== "number") return "-";
    return value.toLocaleString("en-IN");
}
