import { Alert, Button, Card, Empty, Select, Space, Spin, Typography } from "antd";
import { useEffect, useMemo, useState } from "react";
import { BuySellChangeCalculator } from "../components/BuySellChangeCalculator";
import type { DayDetail, UniverseOptionsResponse, WeeklyPriceWatchlistScannerResponse } from "../types";
import { getJson } from "../utils/api";
import { buildWeeklyPriceSummaries } from "../utils/threeWeekStockReview";

const { Text, Title } = Typography;

interface WeeklyPriceWatchlistScannerPageProps {
  onOpenStockReview: (symbol: string) => void;
}

function formatPrice(value: number): string {
  return `₹${value.toLocaleString("en-IN", { maximumFractionDigits: 2, minimumFractionDigits: 2 })}`;
}

function formatDateWithDay(date: string): string {
  const day = new Intl.DateTimeFormat("en-IN", { weekday: "short", timeZone: "UTC" }).format(new Date(`${date}T00:00:00Z`));
  return `${date} (${day})`;
}

function toDayDetails(days: WeeklyPriceWatchlistScannerResponse["rows"][number]["days"]): DayDetail[] {
  return days.map((day) => ({
    ...day,
    daily_change_pct: null,
    rsi14: null,
    vol_ratio: null,
  }));
}

export function WeeklyPriceWatchlistScannerPage({ onOpenStockReview }: WeeklyPriceWatchlistScannerPageProps) {
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
        if (!active) return;
        setOptions(response.options);
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
    void getJson<WeeklyPriceWatchlistScannerResponse>(`/api/strategy/weekly-price-review/scan?watchlist=${encodeURIComponent(selectedWatchlist)}`, { useCache: false })
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

  const cards = useMemo(() => data?.rows.map((row) => ({
    ...row,
    summaries: buildWeeklyPriceSummaries(toDayDetails(row.days), 4),
  })) ?? [], [data]);

  return (
    <div style={{ padding: 24 }}>
      <Space orientation="vertical" size={16} style={{ width: "100%" }}>
        <Card>
          <Space orientation="vertical" size={8} style={{ width: "100%" }}>
            <Title level={3} style={{ margin: 0 }}>Weekly Price Watchlist Scanner</Title>
            <Text type="secondary">Compare weekly high, low, and range across one watchlist. Open a stock only when its price structure deserves deeper review.</Text>
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
        {!selectedWatchlist && !loadingOptions && <Empty description="Select a watchlist to compare its weekly price structure." />}
        {loadingScan && <Spin />}
        {data && !loadingScan && cards.length === 0 && <Empty description="No stocks are available in this watchlist." />}
        {cards.map((card) => (
          <Card
            key={card.symbol}
            size="small"
            title={<Space size={8}><Text strong>{card.symbol}</Text><Text type="secondary">{card.companyName}</Text></Space>}
            extra={<Button size="small" onClick={() => onOpenStockReview(card.symbol)}>Open review</Button>}
          >
            {card.summaries.length === 0 ? <Text type="secondary">No recent daily history.</Text> : (
              <div style={{ overflowX: "auto" }}>
                <div style={{ display: "grid", gridTemplateColumns: "130px 115px 150px 115px 150px 90px", gap: 8, minWidth: 750, fontSize: 12, alignItems: "center" }}>
                  {card.summaries.map((summary) => <div key={summary.weekLabel} style={{ display: "contents" }}>
                    <Text type="secondary">{summary.weekLabel.replace("Week of ", "")}</Text>
                    <Text>Low {formatPrice(summary.low)}</Text>
                    <Text type="secondary">{formatDateWithDay(summary.lowDate)}</Text>
                    <Text>High {formatPrice(summary.high)}</Text>
                    <Text type="secondary">{formatDateWithDay(summary.highDate)}</Text>
                    <Text>Range {summary.rangePct.toFixed(2)}%</Text>
                  </div>)}
                </div>
              </div>
            )}
          </Card>
        ))}
      </Space>
      <div
        data-testid="floating-change-calculator"
        style={{ position: "fixed", right: 24, bottom: 24, zIndex: 1000, maxWidth: "calc(100vw - 32px)" }}
      >
        <Card size="small">
          <Space orientation="vertical" size={4}>
            <Text type="secondary" style={{ fontSize: 11, fontWeight: 700, letterSpacing: 0.5 }}>BUY / SELL CALCULATOR</Text>
            <BuySellChangeCalculator />
          </Space>
        </Card>
      </div>
    </div>
  );
}
