import { Button, Empty, InputNumber, Space, Spin, Table, Tag, Typography } from "antd";
import React, { useEffect, useMemo, useState } from "react";
import { useStockDetail } from "../hooks/useStockDetail";
import type { DayDetail } from "../types";
import {
  computeTradeSessionSignals,
  type SessionSignalKind,
  type TradeSessionSignalConfig,
} from "../utils/tradeSessionSignals";

const { Text } = Typography;

interface TradeMarketHistoryPanelProps {
  symbol: string;
  days?: number;
  defaultExpanded?: boolean;
  title?: string;
  showToggle?: boolean;
  signalConfig?: TradeSessionSignalConfig;
  showSignalLegend?: boolean;
}

const WEEKDAY_LABELS = [
  "Sun",
  "Mon",
  "Tue",
  "Wed",
  "Thu",
  "Fri",
  "Sat",
] as const;

function formatPrice(value: number): string {
  return `₹${value.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function formatVolume(value: number): string {
  if (value >= 10_000_000) return `${(value / 10_000_000).toFixed(2)}Cr`;
  if (value >= 100_000) return `${(value / 100_000).toFixed(2)}L`;
  if (value >= 1_000) return `${(value / 1_000).toFixed(1)}K`;
  return value.toString();
}

function formatRange(value: number): string {
  return value.toLocaleString("en-IN", { maximumFractionDigits: 0 });
}

function formatPercent(value: number): string {
  const sign = value > 0 ? "+" : value < 0 ? "" : "";
  return `${sign}${value.toFixed(2)}%`;
}

function getMoveTone(value: number): string {
  if (value > 0) return "#237804";
  if (value < 0) return "#a8071a";
  return "#595959";
}

function getVolumeTone(value: number | null): string {
  if (value == null) return "#595959";
  if (value >= 2.0) return "#237804";
  if (value >= 1.3) return "#ad6800";
  return "#595959";
}

function getWeekdayLabel(date: string): string {
  const [year, month, day] = date.split("-").map((part) => Number.parseInt(part, 10));
  if (!year || !month || !day) return "-";
  const weekday = new Date(Date.UTC(year, month - 1, day)).getUTCDay();
  return WEEKDAY_LABELS[weekday] ?? "-";
}

function rowBackgroundColor(dominantSignal: "dip_recovery" | "dip_in_zone" | "bearish_close" | "neutral"): string | undefined {
  if (dominantSignal === "dip_recovery") return "#f6ffed";
  if (dominantSignal === "dip_in_zone") return "#fffbe6";
  if (dominantSignal === "bearish_close") return "#fff1f0";
  return undefined;
}

interface SignalBadgeStyle {
  label: string;
  compactLabel: string;
  color: string;
  background: string;
  border: string;
}

const SIGNAL_BADGE: Record<SessionSignalKind, SignalBadgeStyle> = {
  dip_recovery: {
    label: "↑ dip + recovery",
    compactLabel: "DR",
    color: "#237804",
    background: "#f6ffed",
    border: "#b7eb8f",
  },
  dip_in_zone: {
    label: "↘ dip in zone",
    compactLabel: "DZ",
    color: "#874d00",
    background: "#fff7e6",
    border: "#ffd591",
  },
  bearish_close: {
    label: "↓ bearish close",
    compactLabel: "BC",
    color: "#a8071a",
    background: "#fff1f0",
    border: "#ffa39e",
  },
  high_vol: {
    label: "↑ high vol",
    compactLabel: "HV",
    color: "#0958d9",
    background: "#e6f4ff",
    border: "#91caff",
  },
  range_compression: {
    label: "↔ compression",
    compactLabel: "CMP",
    color: "#595959",
    background: "#fafafa",
    border: "#d9d9d9",
  },
};

function renderSignalTag(signal: SessionSignalKind, compact = false): React.ReactElement {
  const style = SIGNAL_BADGE[signal];
  return (
    <Tag
      key={signal}
      style={{
        margin: 0,
        borderRadius: 6,
        fontWeight: 600,
        fontSize: compact ? 11 : undefined,
        lineHeight: compact ? "16px" : undefined,
        paddingInline: compact ? 6 : undefined,
        color: style.color,
        background: style.background,
        borderColor: style.border,
      }}
      title={style.label}
    >
      {compact ? style.compactLabel : style.label}
    </Tag>
  );
}

export function TradeMarketHistoryPanel({
  symbol,
  days = 10,
  defaultExpanded = false,
  title,
  showToggle = true,
  signalConfig,
  showSignalLegend = false,
}: TradeMarketHistoryPanelProps) {
  const [expanded, setExpanded] = useState(defaultExpanded);
  const [selectedDays, setSelectedDays] = useState(days);
  const { data, loading, error } = useStockDetail(expanded ? symbol : null, selectedDays);

  useEffect(() => {
    setSelectedDays(days);
  }, [days]);

  const signalState = useMemo(
    () => computeTradeSessionSignals(data?.days ?? [], signalConfig),
    [data?.days, signalConfig],
  );

  const displayedDays = useMemo(
    () => [...(data?.days ?? [])].sort((a, b) => b.date.localeCompare(a.date)),
    [data?.days],
  );

  const columns = useMemo(
    () => [
      {
        title: "Date",
        key: "date",
        width: 120,
        render: (_: unknown, record: DayDetail) => (
          <div style={{ display: "flex", alignItems: "baseline", gap: 6, whiteSpace: "nowrap" }}>
            <Text style={{ fontWeight: 600 }}>{record.date}</Text>
            <Text type="secondary" style={{ fontSize: 11 }}>{getWeekdayLabel(record.date)}</Text>
          </div>
        ),
      },
      {
        title: "Px",
        key: "price",
        width: 180,
        render: (_: unknown, record: DayDetail) => {
          const dailyChangePct = record.daily_change_pct ?? (record.open > 0
            ? ((record.close - record.open) / record.open) * 100
            : 0);
          const tone = getMoveTone(dailyChangePct);
          const rangePct = record.low > 0 ? ((record.high - record.low) / record.low) * 100 : 0;

          return (
            <div style={{ display: "flex", flexDirection: "column", gap: 2 }}>
              <Text style={{ fontWeight: 600 }}>
                C {formatPrice(record.close)}
              </Text>
              <div style={{ display: "flex", alignItems: "baseline", gap: 8, flexWrap: "wrap" }}>
                <Text style={{ color: tone, fontSize: 12, fontWeight: 600 }}>
                  {formatPercent(dailyChangePct)}
                </Text>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  O {formatPrice(record.open)} · R {rangePct.toFixed(1)}%
                </Text>
              </div>
            </div>
          );
        },
      },
      {
        title: "Vol / Sig",
        key: "volume_signal",
        width: 170,
        render: (_: unknown, record: DayDetail) => {
          const rowSignal = signalState.rowsByDate[record.date];
          const signalTags = rowSignal?.signals ?? [];
          const volumeRatioLabel = record.vol_ratio != null ? `${record.vol_ratio.toFixed(1)}x` : "-";

          return (
            <div style={{ display: "flex", flexDirection: "column", gap: 2 }}>
              <Text style={{ fontWeight: 600 }}>
                {formatVolume(record.volume)}
              </Text>
              <div style={{ display: "flex", alignItems: "center", gap: 6, flexWrap: "wrap", minHeight: 18 }}>
                <Text style={{ color: getVolumeTone(record.vol_ratio), fontSize: 12, fontWeight: 600 }}>
                  {volumeRatioLabel}
                </Text>
                {signalTags.length > 0
                  ? signalTags.map((signal) => renderSignalTag(signal, true))
                  : <Text type="secondary" style={{ fontSize: 12 }}>-</Text>}
              </div>
            </div>
          );
        },
      },
    ],
    [signalState.rowsByDate],
  );

  const panelTitle = title ?? `${selectedDays}D Context`;

  return (
    <div style={{ width: "100%" }}>
      {showToggle && (
        <Button
          size="small"
          type={expanded ? "default" : "text"}
          onClick={() => setExpanded((prev) => !prev)}
        >
          {panelTitle}
        </Button>
      )}

      {expanded && (
        <div style={{ marginTop: showToggle ? 8 : 0 }}>
          <div style={{ marginBottom: 8 }}>
            <Space size={10} wrap>
              <Text style={{ fontSize: 12, fontWeight: 600 }}>{symbol} · Last {selectedDays} Sessions</Text>
              <Space size={6}>
                <Text type="secondary" style={{ fontSize: 12 }}>Days</Text>
                <InputNumber
                  size="small"
                  min={1}
                  max={200}
                  value={selectedDays}
                  onChange={(value) => {
                    if (typeof value === "number" && Number.isFinite(value)) {
                      setSelectedDays(Math.max(1, Math.min(200, Math.round(value))));
                    }
                  }}
                />
              </Space>
            </Space>
          </div>

          {loading && <Spin size="small" />}

          {!loading && error && (
            <Text type="danger" style={{ fontSize: 12 }}>
              {error}
            </Text>
          )}

          {!loading && !error && displayedDays.length === 0 && (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No recent market data" />
          )}

          {!loading && !error && displayedDays.length > 0 && (
            <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
              <div style={{ display: "flex", gap: 14, flexWrap: "wrap" }}>
                <Text style={{ fontSize: 12 }}>
                  Avg vol: <Text strong>{formatVolume(signalState.avgVolume)}</Text>
                </Text>
                <Text style={{ fontSize: 12 }}>
                  Support zone: <Text strong>{signalState.supportZone ? `${formatPrice(signalState.supportZone.low)}-${formatPrice(signalState.supportZone.high)}` : "-"}</Text>
                </Text>
                <Text style={{ fontSize: 12 }}>
                  Your entry: <Text strong>{signalState.entryPrice !== null ? formatPrice(signalState.entryPrice) : "-"}</Text>
                </Text>
              </div>

              {showSignalLegend && (
                <Space size={6} wrap>
                  {renderSignalTag("dip_recovery")}
                  {renderSignalTag("dip_in_zone")}
                  {renderSignalTag("high_vol")}
                  {renderSignalTag("bearish_close")}
                  <Tag style={{ margin: 0, borderRadius: 6, fontWeight: 600 }}>⚠ low in buy zone</Tag>
                  <Tag style={{ margin: 0, borderRadius: 6, fontWeight: 600 }}>🔴 low at/below entry</Tag>
                </Space>
              )}

              <Table<DayDetail>
                rowKey="date"
                size="small"
                pagination={false}
                dataSource={displayedDays}
                columns={columns}
                onRow={(record) => {
                  const rowSignal = signalState.rowsByDate[record.date];
                  return {
                    style: {
                      background: rowSignal ? rowBackgroundColor(rowSignal.dominantSignal) : undefined,
                    },
                  };
                }}
              />
            </div>
          )}
        </div>
      )}
    </div>
  );
}
