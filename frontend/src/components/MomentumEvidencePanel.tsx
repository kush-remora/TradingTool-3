import { Card, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import type { ReactNode } from "react";
import type { MomentumEvidence, MomentumParticipationEvent, MomentumRocState, MomentumWeeklyRoc } from "../types";

const { Text } = Typography;

function formatSignedPercent(value: number | null): string {
  if (value == null) return "—";
  return `${value > 0 ? "+" : ""}${value.toFixed(2)}%`;
}

function formatPrice(value: number | null): string {
  return value == null ? "—" : `₹${value.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function formatVolume(value: number): string {
  if (value >= 100_000) return `${(value / 100_000).toFixed(2)} L`;
  if (value >= 1_000) return `${(value / 1_000).toFixed(1)} K`;
  return value.toLocaleString("en-IN");
}

function formatDelivery(value: number | null): string {
  return value == null ? "—" : `${value.toFixed(2)}%`;
}

function formatAgeInDays(eventDate: string, referenceDate: string): string {
  const eventTime = Date.parse(`${eventDate}T00:00:00Z`);
  const referenceTime = Date.parse(`${referenceDate}T00:00:00Z`);
  if (!Number.isFinite(eventTime) || !Number.isFinite(referenceTime)) return "—";
  return `${Math.max(0, Math.round((referenceTime - eventTime) / 86_400_000))}d`;
}

function formatDistanceFromCurrentPrice(currentPrice: number | null, eventClose: number): string {
  if (currentPrice == null || eventClose === 0) return "—";
  return formatSignedPercent(((currentPrice - eventClose) / eventClose) * 100);
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat("en-IN", { day: "2-digit", month: "short", timeZone: "UTC" }).format(new Date(`${value}T00:00:00Z`));
}

function getPercentColor(value: number | null): string | undefined {
  return value == null ? undefined : value > 0 ? "#389e0d" : value < 0 ? "#cf1322" : undefined;
}

function getTrendTag(evidence: MomentumEvidence): ReactNode {
  if (evidence.above_sma200 == null) return <Tag color="orange">200 DMA unavailable</Tag>;
  return evidence.above_sma200 ? <Tag color="green">Above 200 DMA</Tag> : <Tag color="default">Below 200 DMA</Tag>;
}

function getRocStateLabel(state: MomentumRocState): string {
  switch (state) {
    case "RISING_FROM_NEGATIVE": return "Rising from negative";
    case "RISING_POSITIVE": return "Positive and rising";
    case "FALLING": return "Falling";
    case "FLAT": return "Flat";
    default: return "Not enough weeks";
  }
}

function getRocStateColor(state: MomentumRocState): string | undefined {
  if (state === "RISING_FROM_NEGATIVE") return "gold";
  if (state === "RISING_POSITIVE") return "green";
  if (state === "FALLING") return "red";
  return undefined;
}

export function MomentumRocSummary({ roc }: { roc: MomentumWeeklyRoc | null | undefined }) {
  if (!roc) return <Text type="secondary">ROC unavailable</Text>;

  return (
    <Space orientation="vertical" size={0} aria-label="Weekly ROC">
      <Space size={4}>
        <Tag color={getRocStateColor(roc.state)} style={{ marginInlineEnd: 0 }}>{getRocStateLabel(roc.state)}</Tag>
        <Text>ROC {formatSignedPercent(roc.current_roc_pct)}</Text>
      </Space>
      <Text type="secondary">Δ ROC {roc.change_pct_points == null ? "—" : `${roc.change_pct_points >= 0 ? "+" : ""}${roc.change_pct_points.toFixed(2)} pp`} · {roc.lookback_weeks}W</Text>
    </Space>
  );
}

export function MomentumEvidenceSummary({ evidence }: { evidence: MomentumEvidence | null | undefined }) {
  if (!evidence) return <Text type="secondary">Momentum evidence unavailable.</Text>;
  const recentFirstWeeklyReturns = [...evidence.weekly_returns].reverse();

  return (
    <div data-testid="momentum-evidence-summary" style={{ display: "flex", flexWrap: "wrap", alignItems: "center", gap: 8 }}>
      {getTrendTag(evidence)}
      {evidence.distance_from_sma200_pct != null && <Text type="secondary">{formatSignedPercent(evidence.distance_from_sma200_pct)} vs 200 DMA</Text>}
      {evidence.distance_from_fifty_two_week_high_pct != null && <Text type="secondary">{formatSignedPercent(evidence.distance_from_fifty_two_week_high_pct)} from 52-week high</Text>}
      <MomentumRocSummary roc={evidence.weekly_roc} />
      <div style={{ flexBasis: "100%", display: "flex", flexDirection: "column", gap: 2 }}>
        <Text type="secondary">High-volume days: {evidence.participation_events.length} · lookback: {evidence.participation_lookback_days} days</Text>
        {evidence.participation_events.length > 0 && <Text type="secondary">Dates: {evidence.participation_events.map((event) => formatDate(event.event_date)).join(", ")}</Text>}
      </div>
      <div style={{ display: "flex", flexWrap: "wrap", alignItems: "center", gap: 4 }} aria-label="Four weekly returns">
        <Text type="secondary" style={{ fontSize: 11 }}>4 completed weeks · latest first</Text>
        {recentFirstWeeklyReturns.map((weeklyReturn, index) => (
          <Tag key={weeklyReturn.week_start} style={{ marginInlineEnd: 0, color: getPercentColor(weeklyReturn.return_pct) }}>
            W{index + 1} {formatSignedPercent(weeklyReturn.return_pct)}
          </Tag>
        ))}
      </div>
    </div>
  );
}

function participationColumns(evidence: MomentumEvidence, currentPrice: number | null, currentLtp: number | null | undefined): ColumnsType<MomentumParticipationEvent> {
  return [
    { title: "Date", dataIndex: "event_date", key: "event_date", width: 90, render: formatDate },
    { title: "Age", dataIndex: "event_date", key: "age", width: 58, render: (value: string) => formatAgeInDays(value, evidence.as_of_date) },
    { title: "Close", dataIndex: "close", key: "close", width: 90, render: formatPrice },
    { title: "Volume", dataIndex: "volume", key: "volume", width: 90, render: formatVolume },
    { title: "Vol / prior 10D avg", dataIndex: "volume_ratio", key: "volume_ratio", width: 135, render: (value: number) => `${value.toFixed(2)}×` },
    { title: "Delivery", dataIndex: "delivery_percentage", key: "delivery_percentage", width: 90, render: formatDelivery },
    {
      title: currentLtp != null ? "LTP vs day" : "Close vs day",
      key: "distance_from_current_price",
      width: 95,
      render: (_value: unknown, event: MomentumParticipationEvent) => {
        const value = currentPrice == null || event.close === 0 ? null : ((currentPrice - event.close) / event.close) * 100;
        return <span style={{ color: getPercentColor(value), fontWeight: 600 }}>{formatDistanceFromCurrentPrice(currentPrice, event.close)}</span>;
      },
    },
    {
      title: "Day %",
      dataIndex: "daily_return_pct",
      key: "daily_return_pct",
      width: 78,
      render: (value: number | null) => <span style={{ color: getPercentColor(value), fontWeight: 600 }}>{formatSignedPercent(value)}</span>,
    },
    {
      title: "Since event",
      dataIndex: "price_since_event_pct",
      key: "price_since_event_pct",
      width: 95,
      render: (value: number) => <span style={{ color: getPercentColor(value), fontWeight: 600 }}>{formatSignedPercent(value)}</span>,
    },
  ];
}

interface MomentumParticipationTableProps {
  evidence: MomentumEvidence;
  currentLtp?: number | null;
}

export function MomentumParticipationTable({ evidence, currentLtp }: MomentumParticipationTableProps) {
  const currentPrice = currentLtp ?? evidence.current_close;
  const currentPriceLabel = currentLtp != null ? "Current LTP" : "Latest close";

  return (
    <div data-testid="momentum-participation-table">
      <div style={{ display: "flex", flexWrap: "wrap", gap: 18, marginBottom: 8, fontSize: 12 }}>
        <span><Text type="secondary">{currentPriceLabel}</Text><br /><Text strong>{formatPrice(currentPrice)}</Text></span>
        <span><Text type="secondary">Price reference</Text><br /><Text strong>{currentLtp != null ? "Live market price" : `As of ${formatDate(evidence.as_of_date)}`}</Text></span>
      </div>
      <Text type="secondary" style={{ display: "block", marginBottom: 4, fontSize: 12 }}>
        High-volume days · last {evidence.participation_lookback_days} days · rule: volume ≥ {evidence.participation_threshold.toFixed(1)}× prior 10D average · exact multiple and delivery shown below
      </Text>
      <Table<MomentumParticipationEvent>
        size="small"
        pagination={false}
        scroll={{ x: true, y: 240 }}
        rowKey="event_date"
        columns={participationColumns(evidence, currentPrice, currentLtp)}
        dataSource={evidence.participation_events}
        locale={{ emptyText: `No high-volume days in the last ${evidence.participation_lookback_days} days.` }}
      />
    </div>
  );
}

export function MomentumEvidencePanel({ evidence, currentLtp }: { evidence: MomentumEvidence | null | undefined; currentLtp?: number | null }) {
  if (!evidence) return null;

  return (
    <Card
      size="small"
      data-testid="momentum-evidence-panel"
      title="Momentum evidence"
      extra={<Text type="secondary" style={{ fontSize: 11 }}>Raw facts · as of {formatDate(evidence.as_of_date)}</Text>}
    >
      <Space orientation="vertical" size={10} style={{ width: "100%" }}>
        <MomentumEvidenceSummary evidence={evidence} />
        <div style={{ display: "flex", flexWrap: "wrap", gap: 18, fontSize: 12 }}>
          <span><Text type="secondary">Current close</Text><br /><Text strong>{formatPrice(evidence.current_close)}</Text></span>
          <span><Text type="secondary">200 DMA</Text><br /><Text strong>{formatPrice(evidence.sma200)}</Text></span>
          <span><Text type="secondary">52-week high</Text><br /><Text strong>{formatPrice(evidence.fifty_two_week_high)}</Text></span>
          <span><Text type="secondary">Distance from 52-week high</Text><br /><Text strong style={{ color: getPercentColor(evidence.distance_from_fifty_two_week_high_pct) }}>{formatSignedPercent(evidence.distance_from_fifty_two_week_high_pct)}</Text></span>
          <span><Text type="secondary">High-volume days</Text><br /><Text strong>{evidence.participation_events.length}</Text></span>
          <span><Text type="secondary">Event window</Text><br /><Text strong>Last {evidence.participation_lookback_days} days</Text></span>
        </div>
        <MomentumParticipationTable evidence={evidence} currentLtp={currentLtp} />
      </Space>
    </Card>
  );
}
