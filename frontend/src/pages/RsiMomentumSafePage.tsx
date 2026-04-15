import { ReloadOutlined, SafetyCertificateOutlined } from "@ant-design/icons";
import { Alert, Button, Card, Empty, Space, Spin, Tabs, Tag, Typography, Table, Tooltip } from "antd";
import { useEffect, useMemo, useState } from "react";
import { useRsiMomentum } from "../hooks/useRsiMomentum";
import type { RsiMomentumRankedStock, RsiMomentumSnapshot } from "../types";
import { buildCandidateColumns, formatNumber, getExtensionColor, renderEntryAction, formatInr, formatPct } from "../components/rsi/RsiBoard";

export function RsiMomentumSafePage() {
  const { data, loading, refreshing, refreshingProfileId, error, refresh } = useRsiMomentum();
  const profiles = data?.profiles ?? [];
  const [activeProfileId, setActiveProfileId] = useState<string | undefined>(undefined);

  useEffect(() => {
    if (profiles.length === 0) {
      setActiveProfileId(undefined);
      return;
    }
    const hasActive = activeProfileId != null && profiles.some((p) => p.profileId === activeProfileId);
    if (!hasActive) setActiveProfileId(profiles[0].profileId);
  }, [profiles, activeProfileId]);

  return (
    <div style={{ padding: 24, background: "#f5f7fa", minHeight: "calc(100vh - 48px)" }}>
      <Space direction="vertical" size={16} style={{ width: "100%" }}>
        <div style={{ display: "flex", justifyContent: "space-between", gap: 16, alignItems: "center", flexWrap: "wrap" }}>
          <div>
            <Typography.Title level={4} style={{ margin: 0 }}>
              <SafetyCertificateOutlined style={{ color: "#52c41a", marginRight: 8 }} />
              RSI Safe Variant
            </Typography.Title>
            <Typography.Text type="secondary">
              Focused on Rank 1-15, Rank Improvement, and strict 20DMA extension rules ({"<10%"}).
            </Typography.Text>
          </div>

          <Button
            type="primary"
            icon={<ReloadOutlined />}
            onClick={() => void refresh()}
            loading={refreshing && refreshingProfileId == null}
          >
            Refresh All
          </Button>
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
                <RsiSafePanel
                  snapshot={profile}
                  refreshLoading={refreshing && refreshingProfileId === profile.profileId}
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
  const safeCandidates = useMemo(() => {
    return snapshot.topCandidates
      .filter((s) => s.rank <= 25) // Look at top 25 to find the best 15 safe ones
      .filter((s) => s.extensionAboveSma20Pct <= 10.0)
      .filter((s) => s.maxDailyMove5dPct <= 8.0)
      .sort((a, b) => (b.rankImprovement || 0) - (a.rankImprovement || 0))
      .slice(0, 15);
  }, [snapshot.topCandidates]);

  const columns = useMemo(() => {
    const base = buildCandidateColumns(7.0, 10.0); // Strict thresholds for Safe view
    
    // Insert Rank Improvement after Rank
    const enhanced = [...base];
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
            <Typography.Text type={val > 8 ? "danger" : "secondary"}>
                {val.toFixed(2)}%
            </Typography.Text>
        )
    });

    return enhanced;
  }, []);

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <Card title="Top 15 Safe Candidates (Rank 1-25 Filtered)" size="small">
        <Table
          columns={columns}
          dataSource={safeCandidates}
          rowKey="symbol"
          pagination={false}
          size="small"
          loading={refreshLoading}
          scroll={{ x: 1800 }}
        />
      </Card>
      
      <Alert 
        type="info" 
        showIcon 
        message="Safe Rules Applied" 
        description={
          <ul style={{ margin: 0, paddingLeft: 16 }}>
            <li>Rank must be in Top 25</li>
            <li>Price extension above SMA20 must be &le; 10%</li>
            <li>No single-day move &gt; 8% in last 5 days</li>
            <li>Sorted by Rank Improvement (Rank 5d ago vs Now)</li>
          </ul>
        }
      />
    </Space>
  );
}
