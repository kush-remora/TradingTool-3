import { ReloadOutlined } from "@ant-design/icons";
import { Alert, Button, Card, Empty, Space, Spin, Tabs, Tag, Typography } from "antd";
import { useEffect, useMemo, useState } from "react";
import { BacktestTab } from "../components/rsi/BacktestTab";
import { LifecycleTab } from "../components/rsi/LifecycleTab";
import { HistoryTab } from "../components/rsi/HistoryTab";
import { MultiSymbolHistoryTab } from "../components/rsi/MultiSymbolHistoryTab";
import { RsiMomentumBoardPanel } from "../components/rsi/RsiBoard";
import { useRsiMomentum } from "../hooks/useRsiMomentum";
import type { RsiMomentumSnapshot } from "../types";

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
        <Tabs
          type="card"
          defaultActiveKey="backtest"
          items={[
            {
              key: "board",
              label: "Board",
              children: (
                <Card>
                  <Empty description="No snapshot yet for this profile.">
                    <Button
                      type="primary"
                      icon={<ReloadOutlined />}
                      loading={refreshLoading}
                      onClick={onRetry}
                    >
                      Refresh to populate
                    </Button>
                  </Empty>
                </Card>
              ),
            },
            {
              key: "history",
              label: "History",
              children: <HistoryTab profileId={snapshot.profileId} />,
            },
            {
              key: "multi-history",
              label: "Multi-Symbol",
              children: <MultiSymbolHistoryTab profileId={snapshot.profileId} snapshot={snapshot} />,
            },
            {
              key: "backtest",
              label: "Backtest",
              children: <BacktestTab profileId={snapshot.profileId} />,
            },
            {
              key: "lifecycle",
              label: "Lifecycle",
              children: <LifecycleTab profileId={snapshot.profileId} snapshot={snapshot} />,
            },
          ]}
        />
      ) : (
        <Tabs
          type="card"
          defaultActiveKey="board"
          items={[
            {
              key: "board",
              label: "Board",
              children: <RsiMomentumBoardPanel snapshot={snapshot} watchThresholdPct={watchThresholdPct} skipThresholdPct={skipThresholdPct} />,
            },
            {
              key: "history",
              label: "History",
              children: <HistoryTab profileId={snapshot.profileId} />,
            },
            {
              key: "multi-history",
              label: "Multi-Symbol",
              children: <MultiSymbolHistoryTab profileId={snapshot.profileId} snapshot={snapshot} />,
            },
            {
              key: "backtest",
              label: "Backtest",
              children: <BacktestTab profileId={snapshot.profileId} />,
            },
            {
              key: "lifecycle",
              label: "Lifecycle",
              children: <LifecycleTab profileId={snapshot.profileId} snapshot={snapshot} />,
            },
          ]}
        />
      )}
    </Space>
  );
}
