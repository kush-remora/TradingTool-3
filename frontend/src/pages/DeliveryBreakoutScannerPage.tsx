import { useEffect, useMemo, useState } from "react";
import { Alert, Button, Card, Empty, Space, Spin, Table, Tag, Typography, InputNumber } from "antd";
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

  const [minVolMultiplier, setMinVolMultiplier] = useState<number | null>(null);
  const [minDelMultiplier, setMinDelMultiplier] = useState<number | null>(null);
  const [minPricePct, setMinPricePct] = useState<number | null>(null);
  const [maxPricePct, setMaxPricePct] = useState<number | null>(null);
  const [limitN, setLimitN] = useState<number | null>(null);

  useEffect(() => {
    void loadDashboard().catch(() => undefined);
  }, [loadDashboard]);

  const filteredRows = useMemo(() => {
    if (!data?.rows) return [];
    let rows = data.rows.filter(row => {
      const livePct = quotesBySymbol[row.symbol.toUpperCase()]?.change_percent ?? row.close_pct_change;
      if (minVolMultiplier !== null && row.volume_ratio < minVolMultiplier) return false;
      if (minDelMultiplier !== null && row.delivery_ratio < minDelMultiplier) return false;
      if (minPricePct !== null && livePct !== null && livePct < minPricePct) return false;
      if (maxPricePct !== null && livePct !== null && livePct > maxPricePct) return false;
      return true;
    });
    if (limitN !== null && limitN > 0) {
      rows = rows.slice(0, limitN);
    }
    return rows;
  }, [data?.rows, minVolMultiplier, minDelMultiplier, minPricePct, maxPricePct, limitN, quotesBySymbol]);

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
          render: (value: string) => <Typography.Text strong>{value}</Typography.Text>,
        },
        {
          title: "Price Context",
          dataIndex: "close",
          key: "liveMarket",
          render: (_value: unknown, record: DeliveryBreakoutDashboardRow) => {
            const ltp = quotesBySymbol[record.symbol.toUpperCase()]?.ltp ?? record.close;
            const pct = quotesBySymbol[record.symbol.toUpperCase()]?.change_percent ?? record.close_pct_change;
            const color = pct == null ? "inherit" : pct >= 0 ? "green" : "red";
            return (
              <div>
                <Space size={4} align="baseline">
                  <Typography.Text strong>₹{formatNumber(ltp)}</Typography.Text>
                  {pct != null && (
                     <span style={{ color, fontSize: 12, fontWeight: 500 }}>
                       {pct > 0 ? "↑" : ""} {formatNumber(pct)}%
                     </span>
                  )}
                </Space>
                <div style={{ fontSize: 11, color: "gray", marginTop: -2 }}>
                  Prev: {formatNumber(record.prev_close)}
                </div>
              </div>
            );
          },
          sorter: (left, right) =>
            (resolveMarketChangePercent(left.symbol, quotesBySymbol[left.symbol.toUpperCase()], left.close_pct_change) ?? Number.NEGATIVE_INFINITY) -
            (resolveMarketChangePercent(right.symbol, quotesBySymbol[right.symbol.toUpperCase()], right.close_pct_change) ?? Number.NEGATIVE_INFINITY),
        },
        {
          title: "Volume Shock",
          key: "volumeShock",
          render: (_value: unknown, record: DeliveryBreakoutDashboardRow) => {
            const multiplier = record.volume_ratio;
            const badgeColor = multiplier >= 5 ? "green" : multiplier >= 2 ? "blue" : "default";
            return (
              <div>
                <Space size={4} align="center">
                  <Typography.Text strong>{formatInteger(record.volume)}</Typography.Text>
                  <Tag color={badgeColor} style={{ margin: 0, fontWeight: "bold", fontSize: 10, lineHeight: "16px", padding: "0 4px" }}>{formatRatio(multiplier)}</Tag>
                </Space>
                <div style={{ fontSize: 11, color: "gray", marginTop: -2 }}>
                  Prev: {formatInteger(record.prev_volume)}
                </div>
              </div>
            );
          },
          sorter: (left, right) => left.volume_ratio - right.volume_ratio,
        },
        {
          title: "Delivery Shock",
          key: "deliveryShock",
          render: (_value: unknown, record: DeliveryBreakoutDashboardRow) => {
            const multiplier = record.delivery_ratio;
            const badgeColor = multiplier >= 5 ? "green" : multiplier >= 2 ? "blue" : "default";
            return (
              <div>
                <Space size={4} align="center">
                  <Typography.Text strong>{formatInteger(record.delivery_quantity)}</Typography.Text>
                  <Tag color={badgeColor} style={{ margin: 0, fontWeight: "bold", fontSize: 10, lineHeight: "16px", padding: "0 4px" }}>{formatRatio(multiplier)}</Tag>
                </Space>
                <div style={{ fontSize: 11, color: "gray", marginTop: -2 }}>
                  Prev: {formatInteger(record.prev_delivery_quantity)} | {formatPercent(record.delivery_percentage)} Del
                </div>
              </div>
            );
          },
          sorter: (left, right) => left.delivery_ratio - right.delivery_ratio,
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
            <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
              <Space wrap size={12}>
                <Tag color="blue">Date {data.meta.trade_date}</Tag>
                <Tag>Scanned {formatInteger(data.meta.scanned_count)}</Tag>
                <Tag>Liquidity OK {formatInteger(data.meta.liquidity_eligible_count)}</Tag>
                <Tag color="green">Shortlisted {formatInteger(data.meta.shortlisted_count)}</Tag>
              </Space>
              
              <Space wrap size={12} style={{ background: "#fafafa", padding: "12px 16px", borderRadius: 8, border: "1px solid #f0f0f0" }}>
                <Typography.Text strong style={{ marginRight: 8 }}>Filters:</Typography.Text>
                <InputNumber placeholder="Vol > 2x" min={0} step={0.5} value={minVolMultiplier} onChange={setMinVolMultiplier} style={{ width: 110 }} />
                <InputNumber placeholder="Del > 2x" min={0} step={0.5} value={minDelMultiplier} onChange={setMinDelMultiplier} style={{ width: 110 }} />
                <InputNumber placeholder="Min Pct %" value={minPricePct} onChange={setMinPricePct} style={{ width: 110 }} />
                <InputNumber placeholder="Max Pct %" value={maxPricePct} onChange={setMaxPricePct} style={{ width: 110 }} />
                <InputNumber placeholder="Top N" min={1} value={limitN} onChange={setLimitN} style={{ width: 100 }} />
                <Button size="small" onClick={() => {
                  setMinVolMultiplier(null);
                  setMinDelMultiplier(null);
                  setMinPricePct(null);
                  setMaxPricePct(null);
                  setLimitN(null);
                }}>Reset</Button>
              </Space>
            </div>
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

          {data && filteredRows.length > 0 ? (
            <Table<DeliveryBreakoutDashboardRow>
              rowKey={(row) => `${row.symbol}-${row.trade_date}`}
              columns={columns}
              dataSource={filteredRows}
              pagination={{ pageSize: 50, showSizeChanger: true }}
              size="small"
            />
          ) : null}
        </Space>
      </Card>
    </div>
  );
}
