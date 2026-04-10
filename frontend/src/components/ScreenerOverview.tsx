import { ReloadOutlined } from "@ant-design/icons";
import { Button, Segmented, Table, Tag, Typography, message } from "antd";
import type { TableColumnsType } from "antd";
import { useEffect, useMemo, useState } from "react";
import { useStockQuotes } from "../hooks/useStockQuotes";
import type { WeeklyPatternListResponse, WeeklyPatternResult } from "../types";
import { clearCache, getJson, postJson } from "../utils/api";
import {
  compareByNearestBuyZone,
  computeBuyZoneMetrics,
  matchesBuyZoneFilter,
  type BuyZoneFilter,
  type BuyZoneMetrics,
  type BuyZoneStatus,
} from "../utils/screenerBuyZone";
import { StockBadge } from "./StockBadge";

const { Text, Title } = Typography;

type PatternFilter = "All stocks" | "Weekly pattern" | "Strong (score > 70)" | "No pattern";

interface ScreenerRow extends WeeklyPatternResult {
  buyZoneMetrics: BuyZoneMetrics;
}

function roundPrice(value: number): number {
  return Math.round(value * 100) / 100;
}

function formatPrice(value: number | null | undefined): string {
  if (value === null || value === undefined) return "-";
  return `₹${value.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function formatSignedPercent(value: number | null): string {
  if (value === null) return "-";
  return `${value > 0 ? "+" : ""}${value.toFixed(2)}%`;
}

function formatSignedPrice(value: number | null): string {
  if (value === null) return "-";
  return `₹${value > 0 ? "+" : ""}${value.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function buyZoneStatusLabel(status: BuyZoneStatus): string {
  if (status === "below") return "Below zone";
  if (status === "inside") return "Inside zone";
  if (status === "above") return "Above zone";
  return "-";
}

function buyZoneStatusColor(status: BuyZoneStatus): "success" | "warning" | "processing" | "default" {
  if (status === "below") return "success";
  if (status === "inside") return "processing";
  if (status === "above") return "warning";
  return "default";
}

interface ScreenerOverviewProps {
  onSelectSymbol: (symbol: string) => void;
}

export function ScreenerOverview({ onSelectSymbol }: ScreenerOverviewProps) {
  const [data, setData] = useState<WeeklyPatternListResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [patternFilter, setPatternFilter] = useState<PatternFilter>("All stocks");
  const [buyZoneFilter, setBuyZoneFilter] = useState<BuyZoneFilter>("All");

  const fetchData = async (forceRefresh = false) => {
    setLoading(true);
    try {
      const path = "/api/screener/weekly-pattern";
      if (forceRefresh) clearCache(path);

      const json = await getJson<WeeklyPatternListResponse>(path);
      setData(json);
    } catch (err) {
      console.error(err);
      message.error("Failed to fetch pattern data");
    } finally {
      setLoading(false);
    }
  };

  const handleSync = async () => {
    setSyncing(true);
    try {
      await postJson("/api/screener/sync", {});
      message.success("Sync complete");
      await fetchData(true);
    } catch (err) {
      message.error("Sync failed");
    } finally {
      setSyncing(false);
    }
  };

  useEffect(() => {
    void fetchData();
  }, []);

  const patternFilteredResults = useMemo(() => {
    const results = data?.results ?? [];
    return results.filter((row) => {
      if (patternFilter === "Weekly pattern") return row.patternConfirmed;
      if (patternFilter === "Strong (score > 70)") return row.compositeScore > 70;
      if (patternFilter === "No pattern") return !row.patternConfirmed;
      return true;
    });
  }, [data, patternFilter]);

  const { quotesBySymbol } = useStockQuotes(patternFilteredResults.map((row) => row.symbol));
  const todayIst = new Intl.DateTimeFormat("en-US", { weekday: "short", timeZone: "Asia/Kolkata" }).format(new Date());

  const rowsWithBuyZone = useMemo<ScreenerRow[]>(() => {
    return patternFilteredResults.map((row) => {
      const quote = quotesBySymbol[row.symbol.toUpperCase()];
      const metrics = computeBuyZoneMetrics({
        ltp: quote?.ltp ?? null,
        buyDayLowMin: row.buyDayLowMin,
        buyDayLowMax: row.buyDayLowMax,
      });

      return {
        ...row,
        buyZoneMetrics: metrics,
      };
    });
  }, [patternFilteredResults, quotesBySymbol]);

  const tableRows = useMemo(() => {
    return rowsWithBuyZone
      .filter((row) => matchesBuyZoneFilter(row.buyZoneMetrics, buyZoneFilter))
      .sort((a, b) => compareByNearestBuyZone(a.buyZoneMetrics, b.buyZoneMetrics));
  }, [rowsWithBuyZone, buyZoneFilter]);

  const getTradeSignal = (record: WeeklyPatternResult) => {
    const quote = quotesBySymbol[record.symbol.toUpperCase()];
    const todayLow = quote?.day_low ?? null;
    const todayHigh = quote?.day_high ?? null;
    const entryPrice = todayLow !== null
      ? roundPrice(todayLow * (1 + record.entryReboundPct / 100))
      : null;
    const reboundHit = entryPrice !== null && todayHigh !== null
      ? todayHigh >= entryPrice
      : null;

    return {
      isRecommendedToday: todayIst === record.buyDay,
      todayLow,
      todayHigh,
      entryPrice,
      reboundHit,
      ltp: quote?.ltp ?? null,
    };
  };

  const lookbackWeeks = data?.lookbackWeeks ?? 8;
  const buyZoneLookbackWeeks = data?.buyZoneLookbackWeeks ?? lookbackWeeks;
  const columns: TableColumnsType<ScreenerRow> = [
    {
      title: "Stock",
      dataIndex: "symbol",
      key: "symbol",
      fixed: "left",
      width: 190,
      render: (text: string, record: ScreenerRow) => (
        <div style={{ display: "flex", flexDirection: "column" }}>
          <StockBadge symbol={text} instrumentToken={record.instrumentToken} companyName={record.companyName} fontSize={15} />
          <Text type="secondary" style={{ fontSize: 12 }}>{record.companyName}</Text>
        </div>
      ),
    },
    {
      title: "Today",
      key: "today",
      width: 170,
      render: (_: unknown, record: ScreenerRow) => {
        const signal = getTradeSignal(record);
        return (
          <div style={{ display: "flex", flexDirection: "column", gap: 4 }}>
            <Tag color={signal.isRecommendedToday ? "success" : "default"} style={{ width: "fit-content", margin: 0, fontWeight: 600 }}>
              {signal.isRecommendedToday ? "Recommended" : "Not today"}
            </Tag>
            <Text strong style={{ fontSize: 13 }}>Buy {record.buyDay}</Text>
            <Text type="secondary" style={{ fontSize: 11 }}>
              {signal.isRecommendedToday
                ? "Use live trigger below"
                : "Override only if you want"}
            </Text>
          </div>
        );
      },
    },
    {
      title: "Live Market",
      key: "live",
      width: 170,
      render: (_: unknown, record: ScreenerRow) => {
        const signal = getTradeSignal(record);
        if (signal.ltp === null) {
          return <Text type="secondary" style={{ fontSize: 12 }}>Loading...</Text>;
        }

        return (
          <div style={{ display: "flex", flexDirection: "column", gap: 2 }}>
            <Text strong style={{ fontSize: 15 }}>{formatPrice(signal.ltp)}</Text>
            <Text type="secondary" style={{ fontSize: 11 }}>
              L: {formatPrice(signal.todayLow)}
            </Text>
            <Text type="secondary" style={{ fontSize: 11 }}>
              H: {formatPrice(signal.todayHigh)}
            </Text>
          </div>
        );
      },
    },
    {
      title: "Buy Zone Insights",
      children: [
        {
          title: `Buy Zone (${buyZoneLookbackWeeks}w)`,
          key: "buyZoneRange",
          width: 180,
          render: (_: unknown, record: ScreenerRow) => {
            const color = record.patternConfirmed ? "#0958d9" : "#8c8c8c";
            return (
              <div style={{ display: "flex", flexDirection: "column" }}>
                <Text strong style={{ color }}>{formatPrice(record.buyDayLowMin)} - {formatPrice(record.buyDayLowMax)}</Text>
                <Text type="secondary" style={{ fontSize: 11 }}>Hist. {record.buyDay} low range</Text>
              </div>
            );
          },
        },
        {
          title: "LTP vs Min Low %",
          key: "ltpVsMin",
          width: 150,
          defaultSortOrder: "ascend",
          sorter: (a: ScreenerRow, b: ScreenerRow) => compareByNearestBuyZone(a.buyZoneMetrics, b.buyZoneMetrics),
          render: (_: unknown, record: ScreenerRow) => {
            const pct = record.buyZoneMetrics.ltpVsMinLowPct;
            if (pct === null) return <Text type="secondary">-</Text>;

            const color = pct <= 5 ? "#389e0d" : pct <= 15 ? "#fa8c16" : "#8c8c8c";
            return <Text strong style={{ color }}>{formatSignedPercent(pct)}</Text>;
          },
        },
        {
          title: "Distance ₹",
          key: "distanceFromMin",
          width: 145,
          render: (_: unknown, record: ScreenerRow) => {
            const distance = record.buyZoneMetrics.distanceFromMin;
            if (distance === null) return <Text type="secondary">-</Text>;
            const color = distance <= 0 ? "#389e0d" : "#8c8c8c";
            return <Text style={{ color }}>{formatSignedPrice(distance)}</Text>;
          },
        },
        {
          title: "Zone Status",
          key: "zoneStatus",
          width: 125,
          render: (_: unknown, record: ScreenerRow) => {
            const status = record.buyZoneMetrics.status;
            return (
              <Tag color={buyZoneStatusColor(status)} style={{ margin: 0, fontWeight: 600 }}>
                {buyZoneStatusLabel(status)}
              </Tag>
            );
          },
        },
        {
          title: "Zone Position",
          key: "zonePosition",
          width: 160,
          render: (_: unknown, record: ScreenerRow) => {
            const metrics = record.buyZoneMetrics;
            if (metrics.zonePositionPctClamped === null || metrics.zonePositionPct === null) {
              return <Text type="secondary">-</Text>;
            }

            const rawPosition = metrics.zonePositionPct;
            const displayPosition = rawPosition < 0
              ? "<0%"
              : rawPosition > 100
                ? ">100%"
                : `${Math.round(rawPosition)}%`;

            const fillColor = metrics.status === "below"
              ? "#52c41a"
              : metrics.status === "inside"
                ? "#1677ff"
                : "#faad14";

            return (
              <div style={{ display: "flex", flexDirection: "column", gap: 4 }}>
                <div style={{ width: 100, height: 8, borderRadius: 999, background: "#f0f0f0", overflow: "hidden" }}>
                  <div
                    style={{
                      width: `${metrics.zonePositionPctClamped}%`,
                      height: "100%",
                      background: fillColor,
                    }}
                  />
                </div>
                <Text type="secondary" style={{ fontSize: 11 }}>{displayPosition}</Text>
              </div>
            );
          },
        },
      ],
    },
    {
      title: "Cycle",
      dataIndex: "cycleType",
      key: "cycleType",
      width: 100,
      render: (val: string) => {
        let color = "default";
        if (val === "Weekly") color = "success";
        if (val === "Biweekly") color = "warning";
        return <Tag color={color} style={{ borderRadius: 12, padding: "0 12px", fontWeight: 600 }}>{val}</Tag>;
      },
    },
    {
      title: "Valid Entries",
      key: "validEntries",
      width: 120,
      render: (_: unknown, record: ScreenerRow) => {
        const skipped = record.weeksAnalyzed - record.reboundConsistency;
        return (
          <div>
            <Text strong>{record.reboundConsistency} / {record.weeksAnalyzed}</Text>
            <div style={{ fontSize: 12, color: "#8c8c8c" }}>{skipped} Skipped</div>
          </div>
        );
      },
      sorter: (a: ScreenerRow, b: ScreenerRow) => a.reboundConsistency - b.reboundConsistency,
    },
    {
      title: "Today Setup",
      key: "todaySetup",
      width: 230,
      render: (_: unknown, record: ScreenerRow) => {
        const signal = getTradeSignal(record);
        const winPercent = record.reboundConsistency > 0
          ? Math.round((record.swingConsistency / record.reboundConsistency) * 100)
          : 0;
        return (
          <div>
            <Text strong>{signal.isRecommendedToday ? "Today is buy day" : `Buy ${record.buyDay}`}</Text>
            <Tag color="blue" style={{ marginLeft: 4 }}>+{record.entryReboundPct}% rebound</Tag>
            <div style={{ fontSize: 12, color: "#8c8c8c" }}>
              Today low {formatPrice(signal.todayLow)} · Entry {formatPrice(signal.entryPrice)}
            </div>
            <div style={{ fontSize: 12, color: "#8c8c8c", marginTop: 2 }}>
              Rebound hit {signal.reboundHit === null ? "-" : signal.reboundHit ? "Yes" : "No"} · Hit chance {winPercent}%
            </div>
            <div style={{ fontSize: 12, color: "#8c8c8c", marginTop: 2 }}>
              Target {record.targetRecommendation?.recommendedTargetPct ?? 5}% · Stop {record.stopLossPct}%
            </div>
          </div>
        );
      },
    },
    {
      title: "Ideal Potential",
      key: "idealPotential",
      width: 130,
      render: (_: unknown, record: ScreenerRow) => {
        const captureRatio = record.avgPotentialPct > 0
          ? Math.round((record.swingAvgPct / record.avgPotentialPct) * 100)
          : 0;
        return (
          <div>
            <Text strong style={{ color: "#0958d9" }}>{record.avgPotentialPct}%</Text>
            <div style={{ fontSize: 11, color: "#8c8c8c" }}>
              Capturing {captureRatio}% of swing
            </div>
          </div>
        );
      },
      sorter: (a: ScreenerRow, b: ScreenerRow) => a.avgPotentialPct - b.avgPotentialPct,
    },
    {
      title: "Market State",
      key: "marketState",
      width: 145,
      render: (_: unknown, record: ScreenerRow) => {
        if (!record.currentRsiStatus) return <Text type="secondary">-</Text>;
        const { isOverbought, isOversold, percentile, currentRsi } = record.currentRsiStatus;

        if (isOverbought) return <Tag color="error" style={{ fontWeight: 600 }}>Overbought</Tag>;
        if (isOversold) return <Tag color="success" style={{ fontWeight: 600 }}>Oversold</Tag>;

        return (
          <div style={{ display: "flex", flexDirection: "column" }}>
            <Text style={{ fontSize: 13, color: "#8c8c8c" }}>Within range ({percentile}%)</Text>
            <Text type="secondary" style={{ fontSize: 11 }}>RSI: {currentRsi}</Text>
          </div>
        );
      },
    },
    {
      title: "Score",
      dataIndex: "compositeScore",
      key: "score",
      fixed: "right",
      width: 90,
      render: (val: number) => {
        const color = val > 70 ? "#389e0d" : (val > 40 ? "#fa8c16" : "#8c8c8c");
        return <Text strong style={{ color, fontSize: 16 }}>{val}</Text>;
      },
    },
  ];

  return (
    <div style={{ padding: "12px 16px", height: "calc(100vh - 48px)" }}>
      <div
        style={{
          background: "#fff",
          borderRadius: 12,
          padding: 20,
          boxShadow: "0 4px 12px rgba(0,0,0,0.05)",
          height: "100%",
          display: "flex",
          flexDirection: "column",
        }}
      >
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 24 }}>
          <div>
            <Title level={4} style={{ margin: 0, padding: 0 }}>Pattern screener</Title>
            <Text type="secondary" style={{ fontSize: 13 }}>
              Last run: {data ? new Date(data.runAt).toLocaleString() : "Never"} • {lookbackWeeks}-week lookback
            </Text>
          </div>
          <Button
            type="default"
            icon={<ReloadOutlined />}
            onClick={handleSync}
            loading={syncing}
            style={{ borderRadius: 6, fontWeight: 500 }}
          >
            Refresh patterns
          </Button>
        </div>

        <div style={{ marginBottom: 16, display: "flex", flexWrap: "wrap", gap: 12 }}>
          <Segmented
            options={["All stocks", "Weekly pattern", "Strong (score > 70)", "No pattern"]}
            value={patternFilter}
            onChange={(val) => setPatternFilter(val as PatternFilter)}
            style={{ padding: 4 }}
          />
          <Segmented
            options={["All", "Inside zone", "Near (<=5% above min)", "Below zone"]}
            value={buyZoneFilter}
            onChange={(val) => setBuyZoneFilter(val as BuyZoneFilter)}
            style={{ padding: 4 }}
          />
        </div>

        <Table<ScreenerRow>
          dataSource={tableRows}
          columns={columns}
          rowKey="symbol"
          pagination={false}
          scroll={{ x: 2550, y: "calc(100vh - 290px)" }}
          loading={loading}
          onRow={(record) => ({
            onClick: () => onSelectSymbol(record.symbol),
            style: { cursor: "pointer" },
          })}
          rowClassName={(record) => todayIst === record.buyDay ? "screener-row screener-row-recommended" : "screener-row"}
          size="small"
          style={{ flex: 1 }}
          sticky
        />
      </div>

      <style>{`
        .screener-row:hover > td {
          background-color: #fafafa !important;
        }

        .screener-row-recommended > td {
          background-color: #fcfff5;
        }
      `}</style>
    </div>
  );
}
