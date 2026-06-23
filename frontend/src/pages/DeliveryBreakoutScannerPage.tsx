import { useEffect, useMemo } from "react";
import { Alert, Button, Card, Empty, Space, Spin, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useDeliveryBreakoutScanner } from "../hooks/useDeliveryBreakoutScanner";
import type { DeliveryBreakoutDashboardRow } from "../types";

function formatNumber(value: number | null | undefined, fractionDigits: number = 2): string {
  if (value == null) {
    return "-";
  }
  return value.toLocaleString("en-IN", {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  });
}

function formatInteger(value: number | null | undefined): string {
  if (value == null) {
    return "-";
  }
  return value.toLocaleString("en-IN", { maximumFractionDigits: 0 });
}

function formatPercent(value: number | null | undefined): string {
  if (value == null) {
    return "-";
  }
  return `${formatNumber(value, 2)}%`;
}

function formatRatio(value: number | null | undefined): string {
  if (value == null) {
    return "-";
  }
  return `${formatNumber(value, 2)}x`;
}

export function DeliveryBreakoutScannerPage() {
  const { data, loading, error, loadDashboard } = useDeliveryBreakoutScanner();

  useEffect(() => {
    void loadDashboard().catch(() => undefined);
  }, [loadDashboard]);

  const columns = useMemo<ColumnsType<DeliveryBreakoutDashboardRow>>(
    () => {
      const getFilters = (dataIndex: keyof DeliveryBreakoutDashboardRow, formatter?: (val: any) => string) => {
        if (!data?.rows) return undefined;
        const uniqueValues = Array.from(new Set(data.rows.map(r => r[dataIndex]))).filter(v => v !== null && v !== undefined);
        return uniqueValues.map(v => ({
          text: formatter ? formatter(v) : String(v),
          value: String(v),
        })).sort((a, b) => {
          const numA = Number(a.value);
          const numB = Number(b.value);
          if (!isNaN(numA) && !isNaN(numB) && a.value !== "" && b.value !== "") {
            return numA - numB;
          }
          return a.text.localeCompare(b.text);
        });
      };

      return [
        {
          title: "Symbol",
          dataIndex: "symbol",
          key: "symbol",
          sorter: (left, right) => left.symbol.localeCompare(right.symbol),
          filters: getFilters("symbol"),
          onFilter: (value, record) => String(record.symbol) === value,
        },
        {
          title: "Close",
          dataIndex: "close",
          key: "close",
          render: (value: number | null) => formatNumber(value),
          sorter: (left, right) => (left.close ?? Number.NEGATIVE_INFINITY) - (right.close ?? Number.NEGATIVE_INFINITY),
          filters: getFilters("close", formatNumber),
          onFilter: (value, record) => String(record.close) === value,
        },
        {
          title: "Close %",
          dataIndex: "close_pct_change",
          key: "close_pct_change",
          render: (value: number | null) => formatPercent(value),
          sorter: (left, right) => (left.close_pct_change ?? Number.NEGATIVE_INFINITY) - (right.close_pct_change ?? Number.NEGATIVE_INFINITY),
          filters: getFilters("close_pct_change", formatPercent),
          onFilter: (value, record) => String(record.close_pct_change) === value,
        },
        {
          title: "Volume",
          dataIndex: "volume",
          key: "volume",
          render: (value: number) => formatInteger(value),
          sorter: (left, right) => left.volume - right.volume,
          filters: getFilters("volume", formatInteger),
          onFilter: (value, record) => String(record.volume) === value,
        },
        {
          title: "Delivery Qty",
          dataIndex: "delivery_quantity",
          key: "delivery_quantity",
          render: (value: number) => formatInteger(value),
          sorter: (left, right) => left.delivery_quantity - right.delivery_quantity,
          filters: getFilters("delivery_quantity", formatInteger),
          onFilter: (value, record) => String(record.delivery_quantity) === value,
        },
        {
          title: "Delivery %",
          dataIndex: "delivery_percentage",
          key: "delivery_percentage",
          render: (value: number | null) => formatPercent(value),
          sorter: (left, right) => (left.delivery_percentage ?? Number.NEGATIVE_INFINITY) - (right.delivery_percentage ?? Number.NEGATIVE_INFINITY),
          filters: getFilters("delivery_percentage", formatPercent),
          onFilter: (value, record) => String(record.delivery_percentage) === value,
        },
        {
          title: "Vol vs 10D Max",
          dataIndex: "volume_ratio_vs_10d_max",
          key: "volume_ratio_vs_10d_max",
          render: (value: number) => formatRatio(value),
          sorter: (left, right) => left.volume_ratio_vs_10d_max - right.volume_ratio_vs_10d_max,
          filters: getFilters("volume_ratio_vs_10d_max", formatRatio),
          onFilter: (value, record) => String(record.volume_ratio_vs_10d_max) === value,
        },
        {
          title: "Delivery vs 10D Max",
          dataIndex: "delivery_ratio_vs_10d_max",
          key: "delivery_ratio_vs_10d_max",
          render: (value: number) => formatRatio(value),
          sorter: (left, right) => left.delivery_ratio_vs_10d_max - right.delivery_ratio_vs_10d_max,
          filters: getFilters("delivery_ratio_vs_10d_max", formatRatio),
          onFilter: (value, record) => String(record.delivery_ratio_vs_10d_max) === value,
        },
        {
          title: "Quiet Clue",
          dataIndex: "has_quiet_clue",
          key: "has_quiet_clue",
          render: (value: boolean, row) =>
            value ? <Tag color="green">YES {row.quiet_clue_day}</Tag> : <Tag>NO</Tag>,
          sorter: (left, right) => Number(left.has_quiet_clue) - Number(right.has_quiet_clue),
          filters: [
            { text: "YES", value: "true" },
            { text: "NO", value: "false" },
          ],
          onFilter: (value, record) => String(record.has_quiet_clue) === value,
        },
        {
          title: "Breakout Today",
          dataIndex: "is_confirmed_breakout_today",
          key: "is_confirmed_breakout_today",
          render: (value: boolean) => (value ? <Tag color="blue">YES</Tag> : <Tag>NO</Tag>),
          sorter: (left, right) => Number(left.is_confirmed_breakout_today) - Number(right.is_confirmed_breakout_today),
          filters: [
            { text: "YES", value: "true" },
            { text: "NO", value: "false" },
          ],
          onFilter: (value, record) => String(record.is_confirmed_breakout_today) === value,
        },
        {
          title: "200 SMA",
          dataIndex: "sma200",
          key: "sma200",
          render: (value: number | null) => formatNumber(value),
          sorter: (left, right) => (left.sma200 ?? Number.NEGATIVE_INFINITY) - (right.sma200 ?? Number.NEGATIVE_INFINITY),
          filters: getFilters("sma200", formatNumber),
          onFilter: (value, record) => String(record.sma200) === value,
        },
        {
          title: "% From 200 SMA",
          dataIndex: "distance_from_sma200_pct",
          key: "distance_from_sma200_pct",
          render: (value: number | null) => formatPercent(value),
          sorter: (left, right) => (left.distance_from_sma200_pct ?? Number.NEGATIVE_INFINITY) - (right.distance_from_sma200_pct ?? Number.NEGATIVE_INFINITY),
          filters: getFilters("distance_from_sma200_pct", formatPercent),
          onFilter: (value, record) => String(record.distance_from_sma200_pct) === value,
        },
        {
          title: "Zone",
          dataIndex: "is_near_200_sma",
          key: "is_near_200_sma",
          render: (value: boolean | null) => {
            if (value == null) {
              return <Tag>NO_SMA</Tag>;
            }
            return value ? <Tag color="gold">NEAR_200_SMA</Tag> : <Tag>EXTENDED_ABOVE</Tag>;
          },
          sorter: (left, right) => {
            const l = left.is_near_200_sma === null ? -1 : (left.is_near_200_sma ? 1 : 0);
            const r = right.is_near_200_sma === null ? -1 : (right.is_near_200_sma ? 1 : 0);
            return l - r;
          },
          filters: [
            { text: "NEAR_200_SMA", value: "true" },
            { text: "EXTENDED_ABOVE", value: "false" },
            { text: "NO_SMA", value: "null" },
          ],
          onFilter: (value, record) => String(record.is_near_200_sma) === value,
        },
        {
          title: "Label",
          dataIndex: "label",
          key: "label",
          render: (value: string) => <Tag>{value}</Tag>,
          sorter: (left, right) => (left.label || "").localeCompare(right.label || ""),
          filters: getFilters("label"),
          onFilter: (value, record) => String(record.label) === value,
        },
      ];
    },
    [data],
  );

  return (
    <div style={{ padding: 24 }}>
      <Card
        title="Delivery Breakout Validation"
        extra={
          <Button onClick={() => void loadDashboard().catch(() => undefined)} loading={loading}>
            Reload
          </Button>
        }
      >
        <Space orientation="vertical" size={16} style={{ width: "100%" }}>
          <Typography.Text type="secondary">
            Delivery-first scan across the full delivery table, ranked for confirmed breakouts first and quiet
            pre-breakout clues next.
          </Typography.Text>

          {data ? (
            <Space wrap size={12}>
              <Tag color="blue">Date {data.meta.trade_date}</Tag>
              <Tag>Scanned {formatInteger(data.meta.scanned_count)}</Tag>
              <Tag>Liquidity OK {formatInteger(data.meta.liquidity_eligible_count)}</Tag>
              <Tag color="green">Shortlisted {formatInteger(data.meta.shortlisted_count)}</Tag>
              <Tag color="purple">Breakouts {formatInteger(data.meta.confirmed_breakout_count)}</Tag>
              <Tag color="gold">Quiet Clues {formatInteger(data.meta.quiet_clue_count)}</Tag>
            </Space>
          ) : null}

          {error ? <Alert type="error" message={error} showIcon /> : null}

          {loading && !data ? (
            <div style={{ display: "flex", justifyContent: "center", padding: "48px 0" }}>
              <Spin size="large" />
            </div>
          ) : null}

          {!loading && data && data.rows.length === 0 ? (
            <Empty description="No delivery-breakout candidates matched the current rules." />
          ) : null}

          {data && data.rows.length > 0 ? (
            <Table<DeliveryBreakoutDashboardRow>
              rowKey={(row) => `${row.symbol}-${row.trade_date}`}
              columns={columns}
              dataSource={data.rows}
              pagination={{ pageSize: 25, showSizeChanger: true }}
              scroll={{ x: 1800 }}
            />
          ) : null}
        </Space>
      </Card>
    </div>
  );
}
