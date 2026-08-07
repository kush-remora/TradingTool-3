import { Alert, Button, Card, Empty, Select, Space, Spin, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useMemo, useState } from "react";
import type { UniverseOptionsResponse, WeeklyPriceWatchlistScannerResponse } from "../types";
import { WeeklyStructureIndicator } from "../components/WeeklyStructureIndicator";
import { getJson } from "../utils/api";
import { buildWeeklyPriceSummaries, type WeeklyPriceDay, type WeeklyPriceSummary, type WeeklyStructure } from "../utils/threeWeekStockReview";

const { Text, Title } = Typography;

interface ThreeWeekWatchlistReviewPageProps {
  onOpenStockReview: (symbol: string) => void;
}

interface WatchlistWeeklyRow {
  key: string;
  week: string;
  low: number;
  lowDay: string;
  high: number;
  highDay: string;
  range: string;
  weekOnWeekStructure: WeeklyStructure | null;
  hasLowDayAccumulationCue: boolean;
}

interface WatchlistReviewCard {
  symbol: string;
  companyName: string;
  summaries: WeeklyPriceSummary[];
  dayByDate: Map<string, WeeklyPriceWatchlistScannerResponse["rows"][number]["days"][number]>;
}

function formatPrice(value: number): string {
  return `₹${value.toLocaleString("en-IN", { maximumFractionDigits: 2, minimumFractionDigits: 2 })}`;
}

function formatDateWithDay(date: string): string {
  const day = new Intl.DateTimeFormat("en-IN", { weekday: "short", timeZone: "UTC" }).format(new Date(`${date}T00:00:00Z`));
  return `${date} (${day})`;
}

function formatDeliveryPercentage(value: number | null): string {
  return value == null ? "—" : `${value.toFixed(2)}%`;
}

function formatCompactQuantity(value: number | null): string {
  if (value == null) return "—";
  if (value >= 100_000) return `${(value / 100_000).toFixed(2)} L`;
  return `${(value / 1_000).toFixed(1)} K`;
}

function toDayDetails(days: WeeklyPriceWatchlistScannerResponse["rows"][number]["days"]): WeeklyPriceDay[] {
  return days.map((day) => ({
    ...day,
    daily_change_pct: null,
    rsi14: null,
    vol_ratio: null,
    deliveryPercentage: day.deliveryPercentage,
  }));
}

function buildCards(response: WeeklyPriceWatchlistScannerResponse | null): WatchlistReviewCard[] {
  if (!response) return [];

  return response.rows.map((row) => ({
    symbol: row.symbol,
    companyName: row.companyName,
    summaries: [...buildWeeklyPriceSummaries(toDayDetails(row.days), 4)].reverse(),
    dayByDate: new Map(row.days.map((day) => [day.date, day])),
  }));
}

export function ThreeWeekWatchlistReviewPage({ onOpenStockReview }: ThreeWeekWatchlistReviewPageProps) {
  const [options, setOptions] = useState<UniverseOptionsResponse["options"]>([]);
  const [selectedWatchlist, setSelectedWatchlist] = useState<string | null>(null);
  const [data, setData] = useState<WeeklyPriceWatchlistScannerResponse | null>(null);
  const [loadingOptions, setLoadingOptions] = useState(true);
  const [loadingScan, setLoadingScan] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    void getJson<UniverseOptionsResponse>("/api/strategy/weekly-price-review/watchlists")
      .then((response) => {
        if (active) setOptions(response.options);
      })
      .catch((requestError: unknown) => {
        if (active) setError(requestError instanceof Error ? requestError.message : "Failed to load watchlists");
      })
      .finally(() => {
        if (active) setLoadingOptions(false);
      });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    if (!selectedWatchlist) {
      setData(null);
      return;
    }

    let active = true;
    setLoadingScan(true);
    setError(null);
    void getJson<WeeklyPriceWatchlistScannerResponse>(
      `/api/strategy/weekly-price-review/scan?watchlist=${encodeURIComponent(selectedWatchlist)}`,
      { useCache: false },
    )
      .then((response) => {
        if (active) setData(response);
      })
      .catch((requestError: unknown) => {
        if (active) setError(requestError instanceof Error ? requestError.message : "Failed to scan watchlist");
      })
      .finally(() => {
        if (active) setLoadingScan(false);
      });
    return () => { active = false; };
  }, [selectedWatchlist]);

  const cards = useMemo(() => buildCards(data), [data]);
  const weeklyColumns: ColumnsType<WatchlistWeeklyRow> = [
    { title: "Week", dataIndex: "week", key: "week", width: 120 },
    { title: "Low", dataIndex: "low", key: "low", width: 85, render: formatPrice },
    { title: "Low day · Del / Vol", dataIndex: "lowDay", key: "lowDay", width: 190 },
    { title: "High", dataIndex: "high", key: "high", width: 85, render: formatPrice },
    { title: "High day · Del / Vol", dataIndex: "highDay", key: "highDay", width: 190 },
    { title: "Range", dataIndex: "range", key: "range", width: 65 },
    { title: "Structure", key: "structure", width: 105, render: (_, row) => <WeeklyStructureIndicator structure={row.weekOnWeekStructure} /> },
    { title: "Cue", key: "cue", width: 150, render: (_, row) => row.hasLowDayAccumulationCue ? <Text type="success" strong>Low-day D/V higher</Text> : "—" },
  ];

  return (
    <div style={{ padding: "24px 24px 160px" }}>
      <Space orientation="vertical" size={16} style={{ width: "100%" }}>
        <Card>
          <Space orientation="vertical" size={8} style={{ width: "100%" }}>
            <Title level={3} style={{ margin: 0 }}>Three-Week Stock Review + Current Week</Title>
            <Text type="secondary">Compare weekly highs, lows, ranges, delivery, and volume across one watchlist. Select a stock's review button for its daily detail.</Text>
            <Select
              aria-label="Watchlist"
              loading={loadingOptions}
              value={selectedWatchlist}
              onChange={setSelectedWatchlist}
              placeholder="Select a watchlist"
              style={{ width: 360, maxWidth: "100%" }}
              options={options.map((option) => ({ value: option.value, label: `${option.label} (${option.count})` }))}
            />
          </Space>
        </Card>

        {error && <Alert type="error" message={error} showIcon />}
        {!selectedWatchlist && !loadingOptions && <Empty description="Select a watchlist to compare its three-week price structure." />}
        {loadingScan && <Spin />}
        {data && !loadingScan && cards.length === 0 && <Empty description="No stocks are available in this watchlist." />}
        {cards.map((card) => (
          <Card
            key={card.symbol}
            size="small"
            title={<Space size={8}><Text strong>{card.symbol}</Text><Text type="secondary">{card.companyName}</Text></Space>}
            extra={<Button size="small" onClick={() => onOpenStockReview(card.symbol)}>Open review</Button>}
          >
            {card.summaries.length === 0 ? <Text type="secondary">No recent daily history.</Text> : <>
              <Text type="secondary" style={{ display: "block", marginBottom: 8, fontSize: 12 }}>Structure: ↑ higher high + higher low, ↓ lower high + lower low, → mixed or unchanged.</Text>
              <Table<WatchlistWeeklyRow>
                size="small"
                pagination={false}
                scroll={{ x: true }}
                columns={weeklyColumns}
                dataSource={card.summaries.map((summary) => ({
                  key: summary.weekLabel,
                  week: summary.weekLabel,
                  low: summary.low,
                  lowDay: `${formatDateWithDay(summary.lowDate)} · ${formatDeliveryPercentage(card.dayByDate.get(summary.lowDate)?.deliveryPercentage ?? null)} / ${formatCompactQuantity(card.dayByDate.get(summary.lowDate)?.volume ?? null)}`,
                  high: summary.high,
                  highDay: `${formatDateWithDay(summary.highDate)} · ${formatDeliveryPercentage(card.dayByDate.get(summary.highDate)?.deliveryPercentage ?? null)} / ${formatCompactQuantity(card.dayByDate.get(summary.highDate)?.volume ?? null)}`,
                  range: `${summary.rangePct.toFixed(2)}%`,
                  weekOnWeekStructure: summary.weekOnWeekStructure,
                  hasLowDayAccumulationCue: summary.lowDayHasHigherVolumeAndDelivery,
                }))}
                onRow={(row) => ({ style: row.hasLowDayAccumulationCue ? { backgroundColor: "#f6ffed" } : undefined })}
              />
            </>}
          </Card>
        ))}
      </Space>
    </div>
  );
}
