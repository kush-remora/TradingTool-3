import { Alert, Radio, Space, Spin, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useState } from "react";
import { useRemoraSignals } from "../hooks/useRemoraSignals";
import type { RemoraSignal } from "../types";

type FilterType = "ALL" | "ACCUMULATION" | "DISTRIBUTION";

const columns: ColumnsType<RemoraSignal> = [
  {
    title: "Symbol",
    dataIndex: "symbol",
    key: "symbol",
    render: (symbol: string, row: RemoraSignal) => (
      <Space direction="vertical" size={0}>
        <Typography.Text strong>{symbol}</Typography.Text>
        <Typography.Text type="secondary" style={{ fontSize: 11 }}>
          {row.exchange}
        </Typography.Text>
      </Space>
    ),
  },
  {
    title: "Company",
    dataIndex: "company_name",
    key: "company_name",
    ellipsis: true,
  },
  {
    title: "Signal",
    dataIndex: "signal_type",
    key: "signal_type",
    render: (type: string) => (
      <Tag color={type === "ACCUMULATION" ? "green" : "red"}>{type}</Tag>
    ),
  },
  {
    title: "Volume Ratio",
    dataIndex: "volume_ratio",
    key: "volume_ratio",
    sorter: (a, b) => a.volume_ratio - b.volume_ratio,
    render: (v: number) => (
      <Typography.Text strong style={{ color: "#1677ff" }}>
        {v.toFixed(2)}x
      </Typography.Text>
    ),
  },
  {
    title: "Price Change",
    dataIndex: "price_change_pct",
    key: "price_change_pct",
    sorter: (a, b) => a.price_change_pct - b.price_change_pct,
    render: (v: number) => (
      <Typography.Text style={{ color: v >= 0 ? "#52c41a" : "#f5222d" }}>
        {v >= 0 ? "+" : ""}
        {v.toFixed(2)}%
      </Typography.Text>
    ),
  },
  {
    title: "Deliv %",
    dataIndex: "delivery_pct",
    key: "delivery_pct",
    sorter: (a, b) => a.delivery_pct - b.delivery_pct,
    render: (v: number) => (
      <Typography.Text strong style={{ color: v >= 35 ? "#52c41a" : undefined }}>
        {v.toFixed(1)}%
      </Typography.Text>
    ),
  },
  {
    title: "Deliv Ratio",
    dataIndex: "delivery_ratio",
    key: "delivery_ratio",
    sorter: (a, b) => a.delivery_ratio - b.delivery_ratio,
    render: (v: number) => (
      <Typography.Text strong style={{ color: v >= 1.2 ? "#52c41a" : undefined }}>
        {v.toFixed(2)}x
      </Typography.Text>
    ),
  },
  {
    title: "Consecutive Days",
    dataIndex: "consecutive_days",
    key: "consecutive_days",
    sorter: (a, b) => a.consecutive_days - b.consecutive_days,
    defaultSortOrder: "descend",
    render: (v: number) => <Tag color="blue">{v}d</Tag>,
  },
  {
    title: "Signal Date",
    dataIndex: "signal_date",
    key: "signal_date",
    sorter: (a, b) => a.signal_date.localeCompare(b.signal_date),
  },
];

export function RemoraPage() {
  const [filter, setFilter] = useState<FilterType>("ALL");
  const { signals, asOfDate, isStale, staleReason, loading, error } = useRemoraSignals(
    filter === "ALL" ? undefined : filter
  );

  return (
    <div style={{ padding: 24 }}>
      <Space direction="vertical" size={16} style={{ width: "100%" }}>
        <Space align="center">
          <Typography.Title level={4} style={{ margin: 0 }}>
            Remora Strategy
          </Typography.Title>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            Stocks showing institutional-level volume with muted price movement
          </Typography.Text>
        </Space>

        {isStale && (
          <Alert
            type="warning"
            showIcon
            message={`Delivery data is stale (As of: ${asOfDate || "Unknown"}). ${staleReason || "Source fetch may have failed."}`}
            style={{ marginBottom: 0 }}
          />
        )}

        {error && <Alert type="error" message={error} showIcon />}

        <Radio.Group
          value={filter}
          onChange={(e) => setFilter(e.target.value as FilterType)}
          optionType="button"
          buttonStyle="solid"
        >
          <Radio.Button value="ALL">All</Radio.Button>
          <Radio.Button value="ACCUMULATION">Accumulation</Radio.Button>
          <Radio.Button value="DISTRIBUTION">Distribution</Radio.Button>
        </Radio.Group>

        {loading ? (
          <div style={{ textAlign: "center", padding: 48 }}>
            <Spin size="large" />
          </div>
        ) : (
          <Table
            columns={columns}
            dataSource={signals}
            rowKey="id"
            size="small"
            pagination={{ pageSize: 50, showSizeChanger: false }}
            locale={{ emptyText: "No signals yet — cron runs daily at 9:15 AM IST." }}
          />
        )}
      </Space>
    </div>
  );
}
