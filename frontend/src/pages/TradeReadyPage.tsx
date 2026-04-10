import { Alert, Card, Col, Empty, Row, Select, Space, Spin, Tag, Typography } from "antd";
import { useMemo, useState } from "react";
import { useLiveMarketData } from "../hooks/useLiveMarketData";
import { useStocks } from "../hooks/useStocks";
import { useTradeReadiness } from "../hooks/useTradeReadiness";
import type { TradeReadinessSymbol } from "../types";

const { Text } = Typography;

export function TradeReadyPage() {
  const { stocks, loading: stocksLoading, error: stocksError } = useStocks();
  const [selectedSymbols, setSelectedSymbols] = useState<string[]>([]);
  const { data, loading, error } = useTradeReadiness(selectedSymbols);

  const stockOptions = useMemo(
    () => stocks.map((stock) => ({
      value: stock.symbol,
      label: `${stock.symbol} · ${stock.company_name}`,
    })),
    [stocks],
  );

  const readinessBySymbol = useMemo(() => {
    const map = new Map<string, TradeReadinessSymbol>();
    data.symbols.forEach((row) => map.set(row.symbol, row));
    return map;
  }, [data.symbols]);

  return (
    <div style={{ padding: "20px 24px", background: "#f5f6fa", minHeight: "calc(100vh - 48px)" }}>
      <div style={{ marginBottom: 14 }}>
        <Text strong style={{ fontSize: 20 }}>TRADE READY</Text>
        <div>
          <Text type="secondary" style={{ fontSize: 13 }}>
            Minimal live execution view for 1 to 3 stocks.
          </Text>
        </div>
      </div>

      {(stocksError || error) && (
        <Alert
          type="error"
          message="Failed to load data"
          description={stocksError ?? error ?? "Unknown error"}
          showIcon
          style={{ marginBottom: 12 }}
        />
      )}

      <Card size="small" style={{ marginBottom: 14, borderRadius: 10 }}>
        <Space direction="vertical" size={8} style={{ width: "100%" }}>
          <Text style={{ fontWeight: 600, fontSize: 12 }}>Select Stocks (max 3)</Text>
          <Select
            mode="multiple"
            maxCount={3}
            allowClear
            style={{ width: "100%" }}
            placeholder="Select symbols from your watchlist"
            value={selectedSymbols}
            onChange={(values) => setSelectedSymbols(values)}
            options={stockOptions}
            loading={stocksLoading}
            optionFilterProp="label"
          />
        </Space>
      </Card>

      <Spin spinning={loading && selectedSymbols.length > 0}>
        {selectedSymbols.length === 0 ? (
          <Card style={{ borderRadius: 10 }}>
            <Empty description="Pick at least one stock to open the trade-ready view" />
          </Card>
        ) : (
          <Row gutter={[12, 12]}>
            {selectedSymbols.map((symbol) => (
              <Col xs={24} md={selectedSymbols.length > 1 ? 12 : 24} xl={selectedSymbols.length > 2 ? 8 : 12} key={symbol}>
                <TradeReadyCard
                  symbol={symbol}
                  readiness={readinessBySymbol.get(symbol)}
                />
              </Col>
            ))}
          </Row>
        )}
      </Spin>
    </div>
  );
}

function TradeReadyCard({ symbol, readiness }: { symbol: string; readiness: TradeReadinessSymbol | undefined }) {
  const live = useLiveMarketData(`NSE:${symbol}`);

  const reboundHit = useMemo(() => {
    if (!live || live.low <= 0) return false;
    return live.high >= live.low * 1.01;
  }, [live]);

  const formatPrice = (value: number | null | undefined) => {
    if (value == null) return "-";
    return `₹${value.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  };

  const formatPct = (value: number | null | undefined) => {
    if (value == null) return "-";
    return `${value.toFixed(1)}%`;
  };

  return (
    <Card
      size="small"
      style={{ borderRadius: 10, border: "1px solid #e8e8e8", height: "100%" }}
      title={
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <Text strong>{symbol}</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>{readiness?.company_name ?? "-"}</Text>
        </div>
      }
    >
      <Space direction="vertical" size={10} style={{ width: "100%" }}>
        <MetricRow label="LTP" value={formatPrice(live?.ltp ?? null)} strong />
        <MetricRow label="RSI (14)" value={readiness?.rsi14?.toFixed(2) ?? "-"} />
        <MetricRow label="RSI (15m)" value={readiness?.rsi15m?.toFixed(2) ?? "-"} />

        <div style={{ borderTop: "1px solid #f0f0f0", paddingTop: 8 }}>
          <Text type="secondary" style={{ fontSize: 11 }}>Pressure</Text>
          <MetricRow label="Buy Qty" value={live?.buyQuantity?.toLocaleString("en-IN") ?? "-"} />
          <MetricRow label="Sell Qty" value={live?.sellQuantity?.toLocaleString("en-IN") ?? "-"} />
          <MetricRow label="Buy %" value={formatPct(live?.buyPressurePct)} />
          <MetricRow label="Sell %" value={formatPct(live?.sellPressurePct)} />
          <div style={{ marginTop: 4 }}>
            <Tag color={
              live?.pressureSide === "BUYERS_AGGRESSIVE"
                ? "green"
                : live?.pressureSide === "SELLERS_AGGRESSIVE"
                  ? "red"
                  : "default"
            }>
              {live?.pressureSide === "BUYERS_AGGRESSIVE"
                ? "Buyers Aggressive"
                : live?.pressureSide === "SELLERS_AGGRESSIVE"
                  ? "Sellers Aggressive"
                  : "Neutral"}
            </Tag>
          </div>
        </div>

        <div style={{ borderTop: "1px solid #f0f0f0", paddingTop: 8 }}>
          <Text type="secondary" style={{ fontSize: 11 }}>Day Metrics</Text>
          <MetricRow label="Day Low" value={formatPrice(live?.low ?? null)} />
          <MetricRow label="Day High" value={formatPrice(live?.high ?? null)} />
          <MetricRow label="1% Rebound" value={reboundHit ? "Hit" : "Not hit"} />
        </div>

        <div style={{ borderTop: "1px solid #f0f0f0", paddingTop: 8 }}>
          <Text type="secondary" style={{ fontSize: 11 }}>Telegram Alerts</Text>
          {readiness?.alerts?.length ? (
            <Space direction="vertical" size={6} style={{ width: "100%" }}>
              {readiness.alerts.slice(0, 2).map((alert, index) => (
                <div key={`${alert.received_at}-${index}`} style={{ background: "#fafafa", border: "1px solid #f0f0f0", borderRadius: 8, padding: "8px 9px" }}>
                  <Space size={6} wrap>
                    {alert.action && <Tag color={alert.action === "BUY" ? "green" : "red"}>{alert.action}</Tag>}
                    {alert.limit_price != null && <Text style={{ fontSize: 12 }}>Limit: {formatPrice(alert.limit_price)}</Text>}
                    {alert.target_price != null && <Text style={{ fontSize: 12 }}>Target: {formatPrice(alert.target_price)}</Text>}
                  </Space>
                  <div style={{ marginTop: 4 }}>
                    <Text type="secondary" style={{ fontSize: 11 }}>{new Date(alert.received_at).toLocaleString()}</Text>
                  </div>
                  <div style={{ marginTop: 3 }}>
                    <Text style={{ fontSize: 12 }}>{alert.raw_text}</Text>
                  </div>
                </div>
              ))}
            </Space>
          ) : (
            <Text type="secondary" style={{ fontSize: 12 }}>No parsed alerts for this symbol.</Text>
          )}
        </div>
      </Space>
    </Card>
  );
}

function MetricRow({ label, value, strong = false }: { label: string; value: string; strong?: boolean }) {
  return (
    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
      <Text type="secondary" style={{ fontSize: 12 }}>{label}</Text>
      <Text style={{ fontSize: 12, fontWeight: strong ? 700 : 500 }}>{value}</Text>
    </div>
  );
}
