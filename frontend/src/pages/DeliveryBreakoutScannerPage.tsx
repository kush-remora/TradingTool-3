import { useEffect, useMemo } from "react";
import { Alert, Button, Card, Empty, Space, Spin, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useDeliveryBreakoutScanner } from "../hooks/useDeliveryBreakoutScanner";
import { useStockQuotes } from "../hooks/useStockQuotes";
import { renderLiveMarketCell, resolveMarketChangePercent } from "../components/liveMarketCell";
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
  const quoteSymbols = useMemo(() => (data?.rows ?? []).map((row) => row.symbol), [data?.rows]);
  const { quotesBySymbol } = useStockQuotes(quoteSymbols);

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
          title: "Live Market",
          dataIndex: "close",
          key: "liveMarket",
          render: (_value: unknown, record: DeliveryBreakoutDashboardRow) => renderLiveMarketCell({
            symbol: record.symbol,
            snapshot: quotesBySymbol[record.symbol.toUpperCase()],
            fallbackLtp: record.close,
            fallbackChangePercent: record.close_pct_change,
          }),
          sorter: (left, right) =>
            (resolveMarketChangePercent(left.symbol, quotesBySymbol[left.symbol.toUpperCase()], left.close_pct_change) ?? Number.NEGATIVE_INFINITY) -
            (resolveMarketChangePercent(right.symbol, quotesBySymbol[right.symbol.toUpperCase()], right.close_pct_change) ?? Number.NEGATIVE_INFINITY),
          filters: getFilters("close", formatNumber),
          onFilter: (value, record) => String(record.close) === value,
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
          title: "Prev Vol",
          dataIndex: "prev_volume",
          key: "prev_volume",
          render: (value: number) => formatInteger(value),
          sorter: (left, right) => left.prev_volume - right.prev_volume,
          filters: getFilters("prev_volume", formatInteger),
          onFilter: (value, record) => String(record.prev_volume) === value,
        },
        {
          title: "Prev Delivery Qty",
          dataIndex: "prev_delivery_quantity",
          key: "prev_delivery_quantity",
          render: (value: number) => formatInteger(value),
          sorter: (left, right) => left.prev_delivery_quantity - right.prev_delivery_quantity,
          filters: getFilters("prev_delivery_quantity", formatInteger),
          onFilter: (value, record) => String(record.prev_delivery_quantity) === value,
        },
        {
          title: "Vol Ratio",
          dataIndex: "volume_ratio",
          key: "volume_ratio",
          render: (value: number) => formatRatio(value),
          sorter: (left, right) => left.volume_ratio - right.volume_ratio,
          filters: getFilters("volume_ratio", formatRatio),
          onFilter: (value, record) => String(record.volume_ratio) === value,
        },
        {
          title: "Delivery Ratio",
          dataIndex: "delivery_ratio",
          key: "delivery_ratio",
          render: (value: number) => formatRatio(value),
          sorter: (left, right) => left.delivery_ratio - right.delivery_ratio,
          filters: getFilters("delivery_ratio", formatRatio),
          onFilter: (value, record) => String(record.delivery_ratio) === value,
        },

      ];
    },
    [data, quotesBySymbol],
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
