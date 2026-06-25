import { Drawer, Space, Typography } from "antd";
import { ArrowDownOutlined, ArrowUpOutlined } from "@ant-design/icons";
import { useLiveMarketData } from "../hooks/useLiveMarketData";
import { TradeMarketHistoryPanel } from "./TradeMarketHistoryPanel";
import type { StockQuoteSnapshot } from "../types";

const { Text, Title } = Typography;

interface LiveMarketDetailDrawerProps {
  symbol: string;
  open: boolean;
  onClose: () => void;
  snapshot?: StockQuoteSnapshot | null;
  fallbackLtp?: number | null;
  fallbackChangePercent?: number | null;
}

function formatPrice(value: number | null | undefined): string {
  if (value == null) {
    return "-";
  }

  return `₹${value.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function formatVolume(value: number | null | undefined): string {
  if (value == null) {
    return "-";
  }

  if (value >= 10_000_000) return `${(value / 10_000_000).toFixed(2)} Cr`;
  if (value >= 100_000) return `${(value / 100_000).toFixed(2)} L`;
  if (value >= 1_000) return `${(value / 1_000).toFixed(1)} K`;
  return value.toString();
}

function resolvePressureSplit(
  buyQuantity: number | null | undefined,
  sellQuantity: number | null | undefined,
): { buyPct: number; sellPct: number; hasData: boolean } {
  if (buyQuantity == null || sellQuantity == null) {
    return { buyPct: 50, sellPct: 50, hasData: false };
  }

  const totalQuantity = buyQuantity + sellQuantity;
  if (totalQuantity <= 0) {
    return { buyPct: 50, sellPct: 50, hasData: false };
  }

  const buyPct = (buyQuantity / totalQuantity) * 100;
  return {
    buyPct,
    sellPct: 100 - buyPct,
    hasData: true,
  };
}

function resolveVolumeRatioTone(volumeRatioVsPreviousDay: number | null): string | undefined {
  if (volumeRatioVsPreviousDay == null) {
    return undefined;
  }

  if (volumeRatioVsPreviousDay >= 2.0) {
    return "#52c41a";
  }

  if (volumeRatioVsPreviousDay >= 1.3) {
    return "#d46b08";
  }

  return undefined;
}

export function LiveMarketDetailDrawer({
  symbol,
  open,
  onClose,
  snapshot = null,
  fallbackLtp = null,
  fallbackChangePercent = null,
}: LiveMarketDetailDrawerProps) {
  const data = useLiveMarketData(symbol);
  const displaySymbol = symbol.replace(/^NSE:/, "");

  const ltp = data?.ltp ?? snapshot?.ltp ?? fallbackLtp;
  const averagePrice = data?.averagePrice ?? snapshot?.average_price ?? null;
  const changePercent = data?.changePercent ?? snapshot?.change_percent ?? fallbackChangePercent;
  const openPrice = data?.open ?? snapshot?.day_open ?? null;
  const high = data?.high ?? snapshot?.day_high ?? null;
  const low = data?.low ?? snapshot?.day_low ?? null;
  const volume = data?.volume ?? snapshot?.volume ?? null;
  const previousDayVolume = snapshot?.previous_day_volume ?? null;
  const avgVol20d = data?.avgVol20d ?? null;
  const buyQuantity = data?.buyQuantity ?? null;
  const sellQuantity = data?.sellQuantity ?? null;
  const pressureSplit = resolvePressureSplit(buyQuantity, sellQuantity);
  const volumeRatioVsPreviousDay = volume != null && previousDayVolume != null && previousDayVolume > 0
    ? volume / previousDayVolume
    : null;
  const volumeRatioToneColor = resolveVolumeRatioTone(volumeRatioVsPreviousDay);
  const averageDeltaPct = averagePrice != null && averagePrice > 0 && ltp != null
    ? ((ltp - averagePrice) / averagePrice) * 100
    : null;

  const isPositive = (changePercent ?? 0) >= 0;
  const priceToneColor = isPositive ? "#52c41a" : "#ff4d4f";
  const AvgIcon = isPositive ? ArrowUpOutlined : ArrowDownOutlined;

  return (
    <Drawer
      title={<span style={{ fontWeight: 700, fontSize: 16 }}>{displaySymbol} Live Detail</span>}
      placement="right"
      open={open}
      onClose={onClose}
      size={520}
      styles={{ body: { paddingBottom: 32 } }}
    >
      <Space orientation="vertical" size={16} style={{ width: "100%" }}>
        <div
          style={{
            border: "1px solid #f0f0f0",
            borderRadius: 12,
            padding: 16,
            background: "#fafafa",
          }}
        >
          <Space orientation="vertical" size={10} style={{ width: "100%" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline", gap: 12 }}>
              <Title level={3} style={{ margin: 0 }}>{formatPrice(ltp)}</Title>
              <Text style={{ color: priceToneColor, fontWeight: 600, fontSize: 14 }}>
                <AvgIcon style={{ fontSize: 11 }} /> {changePercent != null ? `${Math.abs(changePercent).toFixed(2)}%` : "-"}
              </Text>
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "10px 16px" }}>
              <Text type="secondary">Open: <Text>{formatPrice(openPrice)}</Text></Text>
              <Text type="secondary">High: <Text>{formatPrice(high)}</Text></Text>
              <Text type="secondary">Low: <Text>{formatPrice(low)}</Text></Text>
              <Text type="secondary">
                Avg: <Text style={{ color: averageDeltaPct == null ? undefined : averageDeltaPct >= 0 ? "#52c41a" : "#ff4d4f" }}>
                  {formatPrice(averagePrice)}
                </Text>
              </Text>
              <Text type="secondary">Live Vol: <Text>{formatVolume(volume)}</Text></Text>
              <Text type="secondary">20D Avg Vol: <Text>{formatVolume(avgVol20d)}</Text></Text>
              <Text type="secondary">
                T-1 Vol: <Text>{formatVolume(previousDayVolume)}</Text>
              </Text>
              <Text type="secondary">
                T-1 Ratio: <Text style={{ color: volumeRatioToneColor }}>
                  {volumeRatioVsPreviousDay != null ? `${volumeRatioVsPreviousDay.toFixed(1)}x` : "-"}
                </Text>
              </Text>
            </div>
          </Space>
        </div>

        <div
          style={{
            border: "1px solid #f0f0f0",
            borderRadius: 12,
            padding: 16,
            background: "#fff",
          }}
        >
          <Space orientation="vertical" size={8} style={{ width: "100%" }}>
            <div style={{ display: "flex", justifyContent: "space-between", gap: 12 }}>
              <Text style={{ fontSize: 12, fontWeight: 600 }}>
                Buy {pressureSplit.hasData ? `${pressureSplit.buyPct.toFixed(0)}% (${formatVolume(buyQuantity)})` : "-"}
              </Text>
              <Text style={{ fontSize: 12, fontWeight: 600 }}>
                Sell {pressureSplit.hasData ? `${pressureSplit.sellPct.toFixed(0)}% (${formatVolume(sellQuantity)})` : "-"}
              </Text>
            </div>
            <div
              aria-label={`Drawer pressure bar buy ${pressureSplit.buyPct.toFixed(1)} percent sell ${pressureSplit.sellPct.toFixed(1)} percent`}
              style={{
                display: "flex",
                width: "100%",
                height: 10,
                borderRadius: 999,
                overflow: "hidden",
                background: "#f0f0f0",
              }}
            >
              <div style={{ width: `${pressureSplit.buyPct}%`, background: "#00b386" }} />
              <div style={{ width: `${pressureSplit.sellPct}%`, background: "#ff5a36" }} />
            </div>
          </Space>
        </div>

        <div
          style={{
            border: "1px solid #f0f0f0",
            borderRadius: 12,
            padding: 16,
            background: "#fff",
          }}
        >
          <TradeMarketHistoryPanel
            symbol={displaySymbol}
            days={10}
            defaultExpanded
            title="10D Context"
            showToggle={false}
          />
        </div>
      </Space>
    </Drawer>
  );
}
