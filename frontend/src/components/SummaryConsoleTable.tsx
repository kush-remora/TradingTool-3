import { Button, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import type { ReactNode } from "react";
import type { SummaryConsoleRow } from "../types";

const { Text } = Typography;

interface SummaryConsoleTableProps {
  rows: SummaryConsoleRow[];
  onOpenStockReview: (symbol: string) => void;
}

function formatPrice(value: number | null): string {
  return value == null ? "—" : `₹${value.toLocaleString("en-IN", { maximumFractionDigits: 2, minimumFractionDigits: 2 })}`;
}

function formatPercent(value: number | null): string {
  if (value == null) return "—";
  return `${value >= 0 ? "+" : ""}${value.toFixed(2)}%`;
}

function formatRatio(value: number | null): string {
  return value == null ? "—" : `${value.toFixed(2)}×`;
}

function formatQuantity(value: number | null): string {
  return value == null ? "—" : value.toLocaleString("en-IN", { maximumFractionDigits: 0 });
}

function buildKiteChartUrl(symbol: string, instrumentToken: number): string {
  return `https://kite.zerodha.com/chart/web/tvc/NSE/${encodeURIComponent(symbol)}/${instrumentToken}`;
}

function renderEvent(value: boolean, label: string): ReactNode {
  return value ? <Tag color="green">{label}</Tag> : <Text type="secondary">—</Text>;
}

function renderBreakout(row: SummaryConsoleRow, window: 20 | 40 | 60): ReactNode {
  const level = row[`breakout${window}Level`];
  const crossed = row[`breakout${window}LevelCrossed`];
  const confirmed = row[`breakout${window}CloseConfirmed`];
  return (
    <Space orientation="vertical" size={0}>
      <Text type="secondary" style={{ fontSize: 11 }}>Level {formatPrice(level)}</Text>
      <Space size={2}>
        {renderEvent(crossed, "High")}
        {renderEvent(confirmed, "Close")}
      </Space>
    </Space>
  );
}

export function SummaryConsoleTable({ rows, onOpenStockReview }: SummaryConsoleTableProps) {
  const columns: ColumnsType<SummaryConsoleRow> = [
    {
      title: "Stock",
      key: "stock",
      fixed: "left",
      width: 190,
      render: (_value: unknown, row: SummaryConsoleRow) => (
        <Space orientation="vertical" size={2}>
          <a
            aria-label={`Open ${row.symbol} in Kite`}
            href={buildKiteChartUrl(row.symbol, row.instrumentToken)}
            target="_blank"
            rel="noopener noreferrer"
          >
            <Text strong>{row.symbol}</Text>
          </a>
          <Text type="secondary" style={{ fontSize: 11 }}>{row.companyName}</Text>
          <Button
            type="link"
            size="small"
            aria-label={`Open ${row.symbol} detail review`}
            onClick={() => onOpenStockReview(row.symbol)}
            style={{ padding: 0, height: "auto", width: "fit-content" }}
          >
            Detail
          </Button>
        </Space>
      ),
    },
    {
      title: "Watchlists",
      dataIndex: "watchlists",
      key: "watchlists",
      width: 180,
      render: (watchlists: string[]) => <Space size={2} wrap>{watchlists.map((watchlist) => <Tag key={watchlist}>{watchlist}</Tag>)}</Space>,
    },
    {
      title: "Move",
      key: "move",
      width: 115,
      render: (_value: unknown, row: SummaryConsoleRow) => (
        <Space orientation="vertical" size={0}>
          {row.largeMove ? <Tag color={row.dailyMovePct != null && row.dailyMovePct >= 0 ? "green" : "red"}>Move {formatPercent(row.dailyMovePct)}</Tag> : <Text type="secondary">—</Text>}
          <Text type="secondary" style={{ fontSize: 11 }}>Close {formatPrice(row.close)}</Text>
        </Space>
      ),
    },
    {
      title: "200 SMA",
      key: "sma200",
      width: 130,
      render: (_value: unknown, row: SummaryConsoleRow) => (
        <Space orientation="vertical" size={0}>
          {renderEvent(row.sma200Crossed, "Crossed")}
          <Text type="secondary" style={{ fontSize: 11 }}>{formatPrice(row.sma200)}</Text>
        </Space>
      ),
    },
    {
      title: "Volume",
      key: "volume",
      width: 135,
      render: (_value: unknown, row: SummaryConsoleRow) => (
        <Space orientation="vertical" size={0}>
          {row.volumeAnomaly ? <Tag color="orange">{formatRatio(row.volumeRatio)}</Tag> : <Text type="secondary">—</Text>}
          <Text type="secondary" style={{ fontSize: 11 }}>{formatQuantity(row.volume)} · avg {formatQuantity(row.averageVolume5)}</Text>
        </Space>
      ),
    },
    { title: "Delivery", dataIndex: "deliveryPercentage", key: "delivery", width: 85, render: (value: number | null) => value == null ? "—" : `${value.toFixed(2)}%` },
    { title: "20D breakout", key: "breakout20", width: 145, render: (_value: unknown, row: SummaryConsoleRow) => renderBreakout(row, 20) },
    { title: "40D breakout", key: "breakout40", width: 145, render: (_value: unknown, row: SummaryConsoleRow) => renderBreakout(row, 40) },
    { title: "60D breakout", key: "breakout60", width: 145, render: (_value: unknown, row: SummaryConsoleRow) => renderBreakout(row, 60) },
    { title: "Session", dataIndex: "asOfDate", key: "asOfDate", width: 105 },
  ];

  return (
    <Table<SummaryConsoleRow>
      size="small"
      rowKey={(row) => `${row.symbol}-${row.asOfDate}`}
      columns={columns}
      dataSource={rows}
      pagination={false}
      scroll={{ x: 1450 }}
    />
  );
}
