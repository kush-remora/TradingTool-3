import { Typography, Space, Tooltip } from "antd";
import { useState } from "react";
import { useLiveMarketData } from "../hooks/useLiveMarketData";
import { ArrowUpOutlined, ArrowDownOutlined } from "@ant-design/icons";
import { LiveMarketDetailDrawer } from "./LiveMarketDetailDrawer";
import type { StockQuoteSnapshot } from "../types";

const { Text } = Typography;

interface LiveMarketWidgetProps {
  symbol: string;
  showDetails?: boolean;
  showChange?: boolean;
  mode?: "standard" | "wide";
  snapshot?: StockQuoteSnapshot | null;
  fallbackLtp?: number | null;
  fallbackChangePercent?: number | null;
}

type PressureSplit = {
  buyPct: number;
  sellPct: number;
  hasData: boolean;
};

function resolvePressureSplit(
  buyQuantity: number | null | undefined,
  sellQuantity: number | null | undefined,
): PressureSplit {
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

function PressureBar({
  buyPct,
  sellPct,
  compact = false,
}: {
  buyPct: number;
  sellPct: number;
  compact?: boolean;
}) {
  return (
    <div
      aria-label={`Pressure bar buy ${buyPct.toFixed(1)} percent sell ${sellPct.toFixed(1)} percent`}
      style={{
        display: "flex",
        width: compact ? 38 : "100%",
        minWidth: compact ? 38 : undefined,
        height: compact ? 6 : 8,
        borderRadius: 999,
        overflow: "hidden",
        background: "#f0f0f0",
      }}
    >
      <div style={{ width: `${buyPct}%`, background: "#00b386" }} />
      <div style={{ width: `${sellPct}%`, background: "#ff5a36" }} />
    </div>
  );
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

export function LiveMarketWidget({
  symbol,
  showDetails = true,
  showChange = true,
  mode = "standard",
  snapshot = null,
  fallbackLtp = null,
  fallbackChangePercent = null,
}: LiveMarketWidgetProps) {
  const data = useLiveMarketData(symbol);
  const [detailOpen, setDetailOpen] = useState(false);

  const ltp = data?.ltp ?? snapshot?.ltp ?? fallbackLtp;
  const averagePrice = data?.averagePrice ?? snapshot?.average_price ?? null;
  const changePercent = data?.changePercent ?? snapshot?.change_percent ?? fallbackChangePercent;
  const open = data?.open ?? snapshot?.day_open ?? null;
  const high = data?.high ?? snapshot?.day_high ?? null;
  const low = data?.low ?? snapshot?.day_low ?? null;
  const volume = data?.volume ?? snapshot?.volume ?? null;
  const previousDayVolume = snapshot?.previous_day_volume ?? null;
  const buyQuantity = data?.buyQuantity ?? null;
  const sellQuantity = data?.sellQuantity ?? null;
  const pressureSplit = resolvePressureSplit(buyQuantity, sellQuantity);
  const hasDetailSnapshot = open != null || high != null || low != null || volume != null;

  if (ltp === null) {
    return <Text type="secondary" style={{ fontSize: 12 }}>Loading...</Text>;
  }

  const isPositive = (changePercent ?? 0) >= 0;
  const color = isPositive ? "#52c41a" : "#ff4d4f";
  const Icon = isPositive ? ArrowUpOutlined : ArrowDownOutlined;
  const averageDeltaPct = averagePrice != null && averagePrice > 0
    ? ((ltp - averagePrice) / averagePrice) * 100
    : null;
  const volumeRatioVsPreviousDay = volume != null && previousDayVolume != null && previousDayVolume > 0
    ? volume / previousDayVolume
    : null;

  const formatVolume = (vol: number) => {
    if (vol >= 10000000) return `${(vol / 10000000).toFixed(2)} Cr`;
    if (vol >= 100000) return `${(vol / 100000).toFixed(2)} L`;
    if (vol >= 1000) return `${(vol / 1000).toFixed(1)} K`;
    return vol.toString();
  };

  const averageToneLabel = averageDeltaPct == null
    ? "Avg -"
    : `${averageDeltaPct >= 0 ? "Above Avg" : "Below Avg"} ${averageDeltaPct >= 0 ? "+" : ""}${averageDeltaPct.toFixed(1)}%`;
  const averageToneColor = averageDeltaPct == null
    ? undefined
    : averageDeltaPct >= 0 ? "#52c41a" : "#ff4d4f";
  const volumeRatioLabel = volumeRatioVsPreviousDay == null
    ? "Vol -"
    : `Vol ${volumeRatioVsPreviousDay.toFixed(1)}x T-1`;
  const volumeRatioToneColor = resolveVolumeRatioTone(volumeRatioVsPreviousDay);

  if (mode === "standard") {
    return (
      <>
        <div
          role="button"
          aria-label={`Open live detail for ${symbol}`}
          tabIndex={0}
          onClick={() => setDetailOpen(true)}
          onKeyDown={(event) => {
            if (event.key === "Enter" || event.key === " ") {
              event.preventDefault();
              setDetailOpen(true);
            }
          }}
          style={{ display: "flex", flexDirection: "column", minWidth: 146, cursor: "pointer" }}
        >
          <Space size={4} align="baseline">
            <Text strong style={{ fontSize: 16 }}>₹{ltp.toLocaleString("en-IN", { minimumFractionDigits: 2 })}</Text>
            {showChange && changePercent !== null && (
              <Text style={{ color, fontSize: 12, fontWeight: 500 }}>
                <Icon style={{ fontSize: 10 }} /> {Math.abs(changePercent).toFixed(2)}%
              </Text>
            )}
          </Space>
          {showDetails && (
            <Space orientation="vertical" size={0} style={{ marginTop: 4 }}>
              <Text style={{ fontSize: 11, color: averageToneColor ?? "#8c8c8c", fontWeight: 500 }}>
                {averageToneLabel}
              </Text>
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 8 }}>
                <Text type="secondary" style={{ fontSize: 11, color: volumeRatioToneColor }}>
                  {volumeRatioLabel}
                </Text>
                <PressureBar buyPct={pressureSplit.buyPct} sellPct={pressureSplit.sellPct} compact />
              </div>
            </Space>
          )}
        </div>
        <LiveMarketDetailDrawer
          symbol={symbol}
          open={detailOpen}
          onClose={() => setDetailOpen(false)}
          snapshot={snapshot}
          fallbackLtp={fallbackLtp}
          fallbackChangePercent={fallbackChangePercent}
        />
      </>
    );
  }

  return (
    <>
      <div
        role="button"
        aria-label={`Open live detail for ${symbol}`}
        tabIndex={0}
        onClick={() => setDetailOpen(true)}
        onKeyDown={(event) => {
          if (event.key === "Enter" || event.key === " ") {
            event.preventDefault();
            setDetailOpen(true);
          }
        }}
        style={{ display: "flex", flexDirection: "column", minWidth: 120, cursor: "pointer" }}
      >
        <Space size={4} align="baseline">
          <Text strong style={{ fontSize: 16 }}>₹{ltp.toLocaleString("en-IN", { minimumFractionDigits: 2 })}</Text>
          {showChange && changePercent !== null && (
            <Text style={{ color, fontSize: 12, fontWeight: 500 }}>
              <Icon style={{ fontSize: 10 }} /> {Math.abs(changePercent).toFixed(2)}%
            </Text>
          )}
        </Space>

        {showDetails && hasDetailSnapshot && (
          <div style={{ display: "flex", flexDirection: "column", gap: 6, marginTop: 4, width: "100%" }}>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "2px 8px", width: "100%" }}>
              <Text type="secondary" style={{ fontSize: 11 }}>
                O: <Text style={{ fontSize: 11 }}>{open != null ? open.toLocaleString("en-IN") : "-"}</Text>
              </Text>
              <Text type="secondary" style={{ fontSize: 11 }}>
                H: <Text style={{ fontSize: 11 }}>{high != null ? high.toLocaleString("en-IN") : "-"}</Text>
              </Text>
              <Text type="secondary" style={{ fontSize: 11 }}>
                L: <Text style={{ fontSize: 11 }}>{low != null ? low.toLocaleString("en-IN") : "-"}</Text>
              </Text>
              <Tooltip title={`Current: ${volume?.toLocaleString("en-IN") || "-"} | Avg: ${data?.avgVol20d?.toLocaleString("en-IN") || "-"}`}>
                <Text type="secondary" style={{ fontSize: 11, display: "flex", alignItems: "center", gap: 4 }}>
                  V: <Text style={{ fontSize: 11 }}>{volume != null ? formatVolume(volume) : "-"}</Text>
                  {data?.volumeHeat != null && data.volumeHeat > 1.5 && (
                    <span style={{ color: "#d46b08", fontSize: 10, fontWeight: 500 }}>
                      ({data.volumeHeat.toFixed(1)}x)
                    </span>
                  )}
                </Text>
              </Tooltip>
              <Text type="secondary" style={{ fontSize: 11 }}>
                Avg: <Text style={{ fontSize: 11, color: averageToneColor }}>{averagePrice != null ? averagePrice.toLocaleString("en-IN", { maximumFractionDigits: 2 }) : "-"}</Text>
              </Text>
              <Text type="secondary" style={{ fontSize: 11 }}>
                T-1: <Text style={{ fontSize: 11, color: volumeRatioToneColor }}>{volumeRatioVsPreviousDay != null ? `${volumeRatioVsPreviousDay.toFixed(1)}x` : "-"}</Text>
              </Text>
            </div>
            <div style={{ display: "flex", flexDirection: "column", gap: 4 }}>
              <div style={{ display: "flex", justifyContent: "space-between", gap: 8 }}>
                <Text type="secondary" style={{ fontSize: 10, fontWeight: 500 }}>
                  Buy {pressureSplit.hasData ? `${pressureSplit.buyPct.toFixed(0)}% (${formatVolume(buyQuantity ?? 0)})` : "-"}
                </Text>
                <Text type="secondary" style={{ fontSize: 10, fontWeight: 500 }}>
                  Sell {pressureSplit.hasData ? `${pressureSplit.sellPct.toFixed(0)}% (${formatVolume(sellQuantity ?? 0)})` : "-"}
                </Text>
              </div>
              <PressureBar buyPct={pressureSplit.buyPct} sellPct={pressureSplit.sellPct} />
            </div>
          </div>
        )}
      </div>
      <LiveMarketDetailDrawer
        symbol={symbol}
        open={detailOpen}
        onClose={() => setDetailOpen(false)}
        snapshot={snapshot}
        fallbackLtp={fallbackLtp}
        fallbackChangePercent={fallbackChangePercent}
      />
    </>
  );
}
