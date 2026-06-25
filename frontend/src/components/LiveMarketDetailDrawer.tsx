import { Drawer, Space, Typography } from "antd";
import { ArrowDownOutlined, ArrowUpOutlined } from "@ant-design/icons";
import { useStockDetail } from "../hooks/useStockDetail";
import { TradeMarketHistoryPanel } from "./TradeMarketHistoryPanel";
import type { LiveMarketUpdate, StockQuoteSnapshot } from "../types";

const { Text, Title } = Typography;

interface LiveMarketDetailDrawerProps {
  symbol: string;
  open: boolean;
  onClose: () => void;
  snapshot?: StockQuoteSnapshot | null;
  fallbackLtp?: number | null;
  fallbackChangePercent?: number | null;
  liveData?: LiveMarketUpdate | null;
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
  liveData = null,
}: LiveMarketDetailDrawerProps) {
  const displaySymbol = symbol.replace(/^NSE:/, "");
  const { data: detailData } = useStockDetail(open ? displaySymbol : null, 10);

  const ltp = liveData?.ltp ?? snapshot?.ltp ?? fallbackLtp;
  const averagePrice = liveData?.averagePrice ?? snapshot?.average_price ?? null;
  const changePercent = liveData?.changePercent ?? snapshot?.change_percent ?? fallbackChangePercent;
  const openPrice = liveData?.open ?? snapshot?.day_open ?? null;
  const high = liveData?.high ?? snapshot?.day_high ?? null;
  const low = liveData?.low ?? snapshot?.day_low ?? null;
  const volume = liveData?.volume ?? snapshot?.volume ?? null;
  const previousDayVolume = snapshot?.previous_day_volume ?? null;
  const avgVol20d = liveData?.avgVol20d ?? detailData?.avg_volume_20d ?? null;
  const pivotLevels = detailData?.pivot_levels ?? null;
  const buyQuantity = liveData?.buyQuantity ?? null;
  const sellQuantity = liveData?.sellQuantity ?? null;
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

        {pivotLevels != null && (
          <div
            style={{
              border: "1px solid #f0f0f0",
              borderRadius: 12,
              padding: 16,
              background: "#fff",
            }}
          >
            <Space orientation="vertical" size={12} style={{ width: "100%" }}>
              <Text style={{ fontSize: 18, fontWeight: 700, color: "#2f3751" }}>Support and Resistance</Text>
              <div style={{ display: "grid", gridTemplateColumns: "1fr auto", gap: "10px 16px" }}>
                <Text style={{ fontSize: 15, color: "#4a5168" }}>R3</Text>
                <Text style={{ fontSize: 15, fontWeight: 700, color: "#4a5168" }}>{pivotLevels.r3.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</Text>
                <Text style={{ fontSize: 15, color: "#4a5168" }}>R2</Text>
                <Text style={{ fontSize: 15, fontWeight: 700, color: "#4a5168" }}>{pivotLevels.r2.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</Text>
                <Text style={{ fontSize: 15, color: "#4a5168" }}>R1</Text>
                <Text style={{ fontSize: 15, fontWeight: 700, color: "#4a5168" }}>{pivotLevels.r1.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</Text>
              </div>

              <div style={{ position: "relative", padding: "10px 0 4px" }}>
                <div style={{ borderTop: "1px solid #d9dee8" }} />
                <div
                  style={{
                    position: "absolute",
                    left: "50%",
                    top: 0,
                    transform: "translateX(-50%)",
                    background: "#f4f6fb",
                    color: "#59627d",
                    borderRadius: 999,
                    padding: "3px 14px",
                    fontSize: 12,
                    fontWeight: 700,
                    letterSpacing: 1.2,
                  }}
                >
                  PIVOT {pivotLevels.pivot.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                </div>
                <div
                  style={{
                    position: "absolute",
                    left: "50%",
                    top: 28,
                    transform: "translateX(-50%)",
                    background: "#7d8faa",
                    color: "#fff",
                    borderRadius: 999,
                    padding: "3px 14px",
                    fontSize: 12,
                    fontWeight: 700,
                    letterSpacing: 1.2,
                  }}
                >
                  PRICE {ltp != null ? ltp.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : "-"}
                </div>
              </div>

              <div style={{ display: "grid", gridTemplateColumns: "1fr auto", gap: "10px 16px", marginTop: 16 }}>
                <Text style={{ fontSize: 15, color: "#4a5168" }}>S1</Text>
                <Text style={{ fontSize: 15, fontWeight: 700, color: "#4a5168" }}>{pivotLevels.s1.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</Text>
                <Text style={{ fontSize: 15, color: "#4a5168" }}>S2</Text>
                <Text style={{ fontSize: 15, fontWeight: 700, color: "#4a5168" }}>{pivotLevels.s2.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</Text>
                <Text style={{ fontSize: 15, color: "#4a5168" }}>S3</Text>
                <Text style={{ fontSize: 15, fontWeight: 700, color: "#4a5168" }}>{pivotLevels.s3.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</Text>
              </div>
            </Space>
          </div>
        )}

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
