import { ArrowDownOutlined, ArrowUpOutlined, MinusOutlined } from "@ant-design/icons";
import { Empty, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import type { ReactNode } from "react";
import type { ClosePositionBucket, PriceDirection } from "../utils/shortHorizonSelector";
import "./recentDailyEvidenceTable.css";

const { Text } = Typography;

export interface RecentDailyEvidenceRow {
  key: string;
  date: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volumeMultiple: number | null;
  deliveryPercentage: number | null;
  changePct: number | null;
  closePositionPct: number | null;
  closePositionBucket: ClosePositionBucket | null;
  direction: PriceDirection | null;
}

function formatPrice(value: number): string {
  return `₹${value.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat("en-IN", { day: "2-digit", month: "short", timeZone: "UTC" })
    .format(new Date(`${value}T00:00:00Z`));
}

function formatPercent(value: number | null, digits = 1): string {
  if (value == null) return "—";
  return `${value >= 0 ? "+" : ""}${value.toFixed(digits)}%`;
}

function formatMultiple(value: number | null): string {
  return value == null ? "—" : `${value.toFixed(1)}×`;
}

function lowToHighPct(day: RecentDailyEvidenceRow): number | null {
  return day.low > 0 ? ((day.high - day.low) / day.low) * 100 : null;
}

function bucketLabel(bucket: ClosePositionBucket | null): string {
  if (bucket === "HIGH") return "HIGH";
  if (bucket === "LOW") return "LOW";
  if (bucket === "MIDDLE") return "MID";
  return "—";
}

function directionIcon(direction: PriceDirection | null): ReactNode {
  if (direction === "UP") return <ArrowUpOutlined />;
  if (direction === "DOWN") return <ArrowDownOutlined />;
  if (direction === "FLAT") return <MinusOutlined />;
  return null;
}

export function ClosePositionBar({
  positionPct,
  bucket,
  direction,
}: {
  positionPct: number | null;
  bucket: ClosePositionBucket | null;
  direction: PriceDirection | null;
}): ReactNode {
  const position = Math.max(0, Math.min(100, positionPct ?? 50));
  const state = bucket?.toLowerCase() ?? "unknown";
  return (
    <div className="recent-daily-close-cell" aria-label={`Close ${bucketLabel(bucket)}, ${positionPct == null ? "—" : `${positionPct.toFixed(0)}%`} of the way from low to high`}>
      <div className="recent-daily-close-label">
        <span className={`recent-daily-direction recent-daily-direction-${state}`}>{directionIcon(direction)}</span>
        <strong>{bucketLabel(bucket)}</strong>
        <span>{positionPct == null ? "—" : `${positionPct.toFixed(0)}%`}</span>
      </div>
      <div className="recent-daily-range" aria-hidden="true">
        <span>L</span>
        <span className="recent-daily-range-track">
          <span className={`recent-daily-range-dot recent-daily-range-dot-${state}`} style={{ left: `${position}%` }} />
        </span>
        <span>H</span>
      </div>
    </div>
  );
}

export function RecentDailyEvidenceTable({ days }: { days: RecentDailyEvidenceRow[] }): ReactNode {
  const columns: ColumnsType<RecentDailyEvidenceRow> = [
    { title: "Date", dataIndex: "date", key: "date", width: 100, render: formatDate },
    { title: "Open", dataIndex: "open", key: "open", width: 100, render: formatPrice },
    { title: "High", dataIndex: "high", key: "high", width: 100, render: formatPrice },
    { title: "Low", dataIndex: "low", key: "low", width: 100, render: formatPrice },
    { title: "Close", dataIndex: "close", key: "close", width: 100, render: formatPrice },
    {
      title: "Volume vs 10D avg",
      dataIndex: "volumeMultiple",
      key: "volumeMultiple",
      width: 130,
      sorter: (left, right) => (left.volumeMultiple ?? -Infinity) - (right.volumeMultiple ?? -Infinity),
      render: formatMultiple,
    },
    {
      title: "Delivery %",
      dataIndex: "deliveryPercentage",
      key: "deliveryPercentage",
      width: 105,
      sorter: (left, right) => (left.deliveryPercentage ?? -Infinity) - (right.deliveryPercentage ?? -Infinity),
      render: (value: number | null) => value == null ? "—" : `${value.toFixed(1)}%`,
    },
    {
      title: "Change %",
      dataIndex: "changePct",
      key: "changePct",
      width: 100,
      sorter: (left, right) => (left.changePct ?? -Infinity) - (right.changePct ?? -Infinity),
      render: (value: number | null) => (
        <span className={`recent-daily-change-${value == null || value === 0 ? "flat" : value > 0 ? "up" : "down"}`}>
          {formatPercent(value)}
        </span>
      ),
    },
    {
      title: "Close position",
      key: "closePosition",
      width: 155,
      sorter: (left, right) => (left.closePositionPct ?? -1) - (right.closePositionPct ?? -1),
      render: (_, day) => (
        <ClosePositionBar
          positionPct={day.closePositionPct}
          bucket={day.closePositionBucket}
          direction={day.direction}
        />
      ),
    },
    {
      title: "Low → high %",
      key: "lowHighPct",
      width: 105,
      sorter: (left, right) => (lowToHighPct(left) ?? -Infinity) - (lowToHighPct(right) ?? -Infinity),
      render: (_, day) => <span className="recent-daily-low-high">{formatPercent(lowToHighPct(day))}</span>,
    },
  ];

  return (
    <div className="recent-daily-details">
      <div className="recent-daily-summary">
        <div><strong>{days.length}</strong><span>recent completed sessions</span></div>
        <b>Newest first</b>
      </div>
      <Text type="secondary">Change is close versus the previous close. Close position shows where the close finished between the day's low and high; Low → high % is the session range; volume compares with the preceding ten sessions.</Text>
      {days.length > 0 ? (
        <Table<RecentDailyEvidenceRow> className="recent-daily-table" size="small" rowKey="key" pagination={false} columns={columns} dataSource={days} scroll={{ x: true }} />
      ) : (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No recent daily evidence available." />
      )}
    </div>
  );
}
