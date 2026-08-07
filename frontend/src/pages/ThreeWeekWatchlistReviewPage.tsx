import { Alert, Button, Card, Collapse, Empty, Segmented, Select, Space, Spin, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useMemo, useState, type ReactNode } from "react";
import type { MomentumWeeklyRoc, UniverseOptionsResponse, WeeklyPriceWatchlistScannerResponse } from "../types";
import { WeeklyStructureIndicator } from "../components/WeeklyStructureIndicator";
import { MomentumEvidenceSummary, MomentumParticipationTable, MomentumRocSummary } from "../components/MomentumEvidencePanel";
import { useStockQuotes } from "../hooks/useStockQuotes";
import { getJson } from "../utils/api";
import { buildWeeklyPriceSummaries, type WeeklyPriceDay, type WeeklyPriceSummary, type WeeklyStructure } from "../utils/threeWeekStockReview";

const { Text, Title } = Typography;
const NEAR_52_WEEK_HIGH_PCT = -5;
const STRONG_WEEKLY_MOMENTUM_PCT = 5;
const SIGNIFICANT_30D_LOW_MOVE_PCT = 10;

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
  instrumentToken: number;
  summaries: WeeklyPriceSummary[];
  dayByDate: Map<string, WeeklyPriceWatchlistScannerResponse["rows"][number]["days"][number]>;
  momentumEvidence: WeeklyPriceWatchlistScannerResponse["rows"][number]["momentum_evidence"];
}

interface WatchlistMarketSnapshot {
  price: number | null;
  priceLabel: "LTP" | "Latest close";
  deliveryPercentage: number | null;
  volume: number | null;
  volumeChangePct: number | null;
}

interface VolumeAnomalySummary {
  count: number;
  maxRatio: number | null;
  latestDate: string | null;
  latestDeliveryPercentage: number | null;
}

interface WeeklyMomentumSummary {
  latestReturnPct: number | null;
  strongWeekCount: number;
  positiveWeekCount: number;
  weeksConsidered: number;
  strongPriority: number;
  risePriority: number;
}

interface WatchlistSummaryRow {
  key: string;
  card: WatchlistReviewCard;
  currentPrice: number | null;
  distanceFromHighPct: number | null;
  thirtyDayLow: number | null;
  distanceFromThirtyDayLowPct: number | null;
  volumeAnomaly: VolumeAnomalySummary;
  weeklyMomentum: WeeklyMomentumSummary;
  weeklyRoc: MomentumWeeklyRoc | null;
  latestStructure: WeeklyStructure | null;
}

function formatPrice(value: number): string {
  return `₹${value.toLocaleString("en-IN", { maximumFractionDigits: 2, minimumFractionDigits: 2 })}`;
}

function buildKiteChartUrl(symbol: string, instrumentToken: number): string {
  return `https://kite.zerodha.com/chart/web/tvc/NSE/${encodeURIComponent(symbol)}/${instrumentToken}`;
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

function formatSignedPercentage(value: number | null): string {
  if (value == null) return "—";
  return `${value >= 0 ? "+" : ""}${value.toFixed(2)}%`;
}

function formatShortDate(date: string | null): string {
  if (!date) return "—";
  return new Intl.DateTimeFormat("en-IN", { day: "2-digit", month: "short", timeZone: "UTC" }).format(new Date(`${date}T00:00:00Z`));
}

function calculateVolumeChangePct(currentVolume: number | null, previousVolume: number | null): number | null {
  if (currentVolume == null || previousVolume == null || previousVolume <= 0) return null;
  return ((currentVolume - previousVolume) / previousVolume) * 100;
}

function calculateDistanceFromHigh(currentPrice: number | null, high: number | null): number | null {
  if (currentPrice == null || high == null || high <= 0) return null;
  return ((currentPrice - high) / high) * 100;
}

function calculateDistanceFromLow(currentPrice: number | null, low: number | null): number | null {
  if (currentPrice == null || low == null || low <= 0) return null;
  return ((currentPrice - low) / low) * 100;
}

function findThirtyDayLow(card: WatchlistReviewCard): number | null {
  const evidenceLow = card.momentumEvidence?.thirty_day_low;
  if (evidenceLow != null) return evidenceLow;

  return Array.from(card.dayByDate.values())
    .sort((left, right) => left.date.localeCompare(right.date))
    .slice(-30)
    .map((day) => day.low)
    .filter((low) => low > 0 && Number.isFinite(low))
    .reduce<number | null>((lowest, low) => lowest == null ? low : Math.min(lowest, low), null);
}

function buildRecentVolumeAnomalySummary(card: WatchlistReviewCard): VolumeAnomalySummary {
  const recentDates = new Set(
    Array.from(card.dayByDate.values())
      .sort((left, right) => left.date.localeCompare(right.date))
      .slice(-10)
      .map((day) => day.date),
  );
  const events = (card.momentumEvidence?.participation_events ?? [])
    .filter((event) => recentDates.has(event.event_date))
    .sort((left, right) => left.event_date.localeCompare(right.event_date));
  const latestEvent = events[events.length - 1];

  return {
    count: events.length,
    maxRatio: events.length > 0 ? Math.max(...events.map((event) => event.volume_ratio)) : null,
    latestDate: latestEvent?.event_date ?? null,
    latestDeliveryPercentage: latestEvent?.delivery_percentage ?? null,
  };
}

function buildWeeklyMomentumSummary(card: WatchlistReviewCard): WeeklyMomentumSummary {
  const recentReturns = [...(card.momentumEvidence?.weekly_returns ?? [])].reverse().slice(0, 3);
  const strongWeekCount = recentReturns.filter((weeklyReturn) => weeklyReturn.return_pct >= STRONG_WEEKLY_MOMENTUM_PCT).length;
  const positiveWeekCount = recentReturns.filter((weeklyReturn) => weeklyReturn.return_pct > 0).length;

  return {
    latestReturnPct: recentReturns[0]?.return_pct ?? null,
    strongWeekCount,
    positiveWeekCount,
    weeksConsidered: recentReturns.length,
    strongPriority: recentReturns.length === 3 ? strongWeekCount : -1,
    risePriority: recentReturns.length === 3 ? positiveWeekCount : -1,
  };
}

function renderVolumeAnomalySummary(summary: VolumeAnomalySummary): ReactNode {
  if (summary.count === 0) return <Text type="secondary">None</Text>;

  return (
    <Space orientation="vertical" size={0}>
      <Text strong>{summary.count} day{summary.count === 1 ? "" : "s"} · max {summary.maxRatio?.toFixed(1)}×</Text>
      <Text type="secondary">Latest {formatShortDate(summary.latestDate)} · Del {formatDeliveryPercentage(summary.latestDeliveryPercentage)}</Text>
    </Space>
  );
}

function renderWeeklyMomentumSummary(summary: WeeklyMomentumSummary): ReactNode {
  if (summary.weeksConsidered === 0) return <Text type="secondary">—</Text>;

  return (
    <Space orientation="vertical" size={0}>
      <Tag color={summary.positiveWeekCount === summary.weeksConsidered ? "green" : summary.positiveWeekCount >= 2 ? "gold" : "default"} style={{ width: "fit-content", marginInlineEnd: 0 }}>
        {summary.positiveWeekCount}/{summary.weeksConsidered} rising
      </Tag>
      <Text>Latest {formatSignedPercentage(summary.latestReturnPct)}</Text>
      <Text type="secondary">≥5% weeks {summary.strongWeekCount}/{summary.weeksConsidered} · Up weeks {summary.positiveWeekCount}/{summary.weeksConsidered}</Text>
    </Space>
  );
}

function buildMarketSnapshot(
  card: WatchlistReviewCard,
  liveLtp: number | null,
): WatchlistMarketSnapshot {
  const days = Array.from(card.dayByDate.values()).sort((left, right) => left.date.localeCompare(right.date));
  const latestDay = days[days.length - 1];
  const previousDay = days[days.length - 2];

  return {
    price: liveLtp ?? latestDay?.close ?? null,
    priceLabel: liveLtp != null ? "LTP" : "Latest close",
    deliveryPercentage: latestDay?.deliveryPercentage ?? null,
    volume: latestDay?.volume ?? null,
    volumeChangePct: calculateVolumeChangePct(latestDay?.volume ?? null, previousDay?.volume ?? null),
  };
}

function WatchlistMarketSnapshotView({ snapshot }: { snapshot: WatchlistMarketSnapshot }) {
  const volumeChangeColor = snapshot.volumeChangePct == null
    ? undefined
    : snapshot.volumeChangePct >= 0 ? "#389e0d" : "#cf1322";

  return (
    <Space size={6} wrap aria-label="Latest market snapshot" style={{ fontSize: 12 }}>
      <Text strong>{snapshot.priceLabel} {snapshot.price == null ? "—" : formatPrice(snapshot.price)}</Text>
      <Text type="secondary">Delivery {formatDeliveryPercentage(snapshot.deliveryPercentage)}</Text>
      <Text type="secondary">Vol {formatCompactQuantity(snapshot.volume)}</Text>
      <Text style={{ color: volumeChangeColor }}>Vol vs prev {formatSignedPercentage(snapshot.volumeChangePct)}</Text>
    </Space>
  );
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
    instrumentToken: row.instrumentToken,
    summaries: [...buildWeeklyPriceSummaries(toDayDetails(row.days), 4)].reverse(),
    dayByDate: new Map(row.days.map((day) => [day.date, day])),
    momentumEvidence: row.momentum_evidence,
  }));
}

export function ThreeWeekWatchlistReviewPage({ onOpenStockReview }: ThreeWeekWatchlistReviewPageProps) {
  const [options, setOptions] = useState<UniverseOptionsResponse["options"]>([]);
  const [selectedWatchlist, setSelectedWatchlist] = useState<string | null>(null);
  const [data, setData] = useState<WeeklyPriceWatchlistScannerResponse | null>(null);
  const [loadingOptions, setLoadingOptions] = useState(true);
  const [loadingScan, setLoadingScan] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [momentumFilter, setMomentumFilter] = useState<"ALL" | "ABOVE_200_DMA">("ALL");

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
  const quoteSymbols = useMemo(() => cards.map((card) => card.symbol), [cards]);
  const { quotesBySymbol } = useStockQuotes(quoteSymbols);
  const visibleCards = useMemo(
    () => momentumFilter === "ABOVE_200_DMA"
      ? cards.filter((card) => card.momentumEvidence?.above_sma200 === true)
      : cards,
    [cards, momentumFilter],
  );
  const summaryRows = useMemo<WatchlistSummaryRow[]>(() => visibleCards.map((card) => {
    const currentPrice = quotesBySymbol[card.symbol]?.ltp ?? card.momentumEvidence?.current_close ?? null;
    const thirtyDayLow = findThirtyDayLow(card);
    return {
      key: card.symbol,
      card,
      currentPrice,
      distanceFromHighPct: calculateDistanceFromHigh(currentPrice, card.momentumEvidence?.fifty_two_week_high ?? null),
      thirtyDayLow,
      distanceFromThirtyDayLowPct: calculateDistanceFromLow(currentPrice, thirtyDayLow),
      volumeAnomaly: buildRecentVolumeAnomalySummary(card),
      weeklyMomentum: buildWeeklyMomentumSummary(card),
      weeklyRoc: card.momentumEvidence?.weekly_roc ?? null,
      latestStructure: card.summaries[0]?.weekOnWeekStructure ?? null,
    };
  }), [quotesBySymbol, visibleCards]);
  const summaryColumns: ColumnsType<WatchlistSummaryRow> = [
    {
      title: "Stock",
      key: "stock",
      width: 160,
      render: (_, row) => (
        <Space orientation="vertical" size={0}>
          <a aria-label={`Open ${row.card.symbol} in Kite`} href={buildKiteChartUrl(row.card.symbol, row.card.instrumentToken)} target="_blank" rel="noopener noreferrer">
            <Text strong>{row.card.symbol}</Text>
          </a>
          <Text type="secondary" style={{ fontSize: 11 }}>{row.card.companyName}</Text>
        </Space>
      ),
    },
    { title: "LTP", key: "currentPrice", width: 90, render: (_, row) => row.currentPrice == null ? "—" : formatPrice(row.currentPrice) },
    {
      title: "52W high",
      key: "distanceFromHighPct",
      width: 125,
      render: (_, row) => {
        const nearHigh = row.distanceFromHighPct != null && row.distanceFromHighPct >= NEAR_52_WEEK_HIGH_PCT;
        const breakout = row.distanceFromHighPct != null && row.distanceFromHighPct >= 0;
        return (
          <Space orientation="vertical" size={0}>
            {nearHigh && <Tag color={breakout ? "green" : "gold"} style={{ width: "fit-content", marginInlineEnd: 0 }}>{breakout ? "52W breakout" : "Near high"}</Tag>}
            <Text>{row.distanceFromHighPct == null ? "—" : `${formatSignedPercentage(row.distanceFromHighPct)} from high`}</Text>
          </Space>
        );
      },
    },
    {
      title: "Move from 30D low",
      key: "distanceFromThirtyDayLowPct",
      width: 155,
      sorter: (left, right) => (left.distanceFromThirtyDayLowPct ?? Number.NEGATIVE_INFINITY) - (right.distanceFromThirtyDayLowPct ?? Number.NEGATIVE_INFINITY),
      render: (_, row) => {
        const significantMove = row.distanceFromThirtyDayLowPct != null && row.distanceFromThirtyDayLowPct >= SIGNIFICANT_30D_LOW_MOVE_PCT;
        return (
          <Space orientation="vertical" size={0}>
            {significantMove && <Tag color="green" style={{ width: "fit-content", marginInlineEnd: 0 }}>≥10% move</Tag>}
            <Text style={{ color: significantMove ? "#389e0d" : undefined, fontWeight: significantMove ? 600 : undefined }}>
              {row.distanceFromThirtyDayLowPct == null ? "—" : `${formatSignedPercentage(row.distanceFromThirtyDayLowPct)} from low`}
            </Text>
            <Text type="secondary">Low {row.thirtyDayLow == null ? "—" : formatPrice(row.thirtyDayLow)}</Text>
          </Space>
        );
      },
    },
    {
      title: "10D volume anomaly",
      key: "volumeAnomaly",
      width: 200,
      sorter: (left, right) => (left.volumeAnomaly.maxRatio ?? Number.NEGATIVE_INFINITY) - (right.volumeAnomaly.maxRatio ?? Number.NEGATIVE_INFINITY)
        || left.volumeAnomaly.count - right.volumeAnomaly.count,
      render: (_, row) => renderVolumeAnomalySummary(row.volumeAnomaly),
    },
    {
      title: "Weekly momentum",
      key: "weeklyMomentum",
      width: 180,
      sorter: (left, right) => left.weeklyMomentum.strongPriority - right.weeklyMomentum.strongPriority
        || left.weeklyMomentum.risePriority - right.weeklyMomentum.risePriority
        || (left.distanceFromHighPct ?? Number.NEGATIVE_INFINITY) - (right.distanceFromHighPct ?? Number.NEGATIVE_INFINITY)
        || (left.volumeAnomaly.maxRatio ?? Number.NEGATIVE_INFINITY) - (right.volumeAnomaly.maxRatio ?? Number.NEGATIVE_INFINITY)
        || left.weeklyMomentum.strongWeekCount - right.weeklyMomentum.strongWeekCount
        || (left.weeklyMomentum.latestReturnPct ?? Number.NEGATIVE_INFINITY) - (right.weeklyMomentum.latestReturnPct ?? Number.NEGATIVE_INFINITY)
        || (left.weeklyRoc.change_pct_points ?? Number.NEGATIVE_INFINITY) - (right.weeklyRoc.change_pct_points ?? Number.NEGATIVE_INFINITY),
      defaultSortOrder: "descend",
      render: (_, row) => renderWeeklyMomentumSummary(row.weeklyMomentum),
    },
    {
      title: "Weekly ROC",
      key: "weeklyRoc",
      width: 190,
      sorter: (left, right) => (left.weeklyRoc.change_pct_points ?? Number.NEGATIVE_INFINITY) - (right.weeklyRoc.change_pct_points ?? Number.NEGATIVE_INFINITY),
      render: (_, row) => <MomentumRocSummary roc={row.weeklyRoc} />,
    },
    { title: "Weekly structure", key: "structure", width: 150, render: (_, row) => <WeeklyStructureIndicator structure={row.latestStructure} /> },
    { title: "Action", key: "action", width: 100, render: (_, row) => <Button size="small" onClick={() => onOpenStockReview(row.card.symbol)}>Open review</Button> },
  ];
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
            <Space wrap size={12}>
              <Select
                aria-label="Watchlist"
                loading={loadingOptions}
                value={selectedWatchlist}
                onChange={setSelectedWatchlist}
                placeholder="Select a watchlist"
                style={{ width: 360, maxWidth: "100%" }}
                options={options.map((option) => ({ value: option.value, label: `${option.label} (${option.count})` }))}
              />
              <Segmented
                aria-label="Momentum filter"
                value={momentumFilter}
                onChange={(value) => setMomentumFilter(value as "ALL" | "ABOVE_200_DMA")}
                options={[{ label: "All stocks", value: "ALL" }, { label: "Above 200 DMA", value: "ABOVE_200_DMA" }]}
              />
            </Space>
            {data && <Text type="secondary" style={{ fontSize: 12 }}>
              Showing {visibleCards.length} of {cards.length} stocks · momentum evidence is raw market data, not a recommendation.
            </Text>}
          </Space>
        </Card>

        {error && <Alert type="error" message={error} showIcon />}
        {!selectedWatchlist && !loadingOptions && <Empty description="Select a watchlist to compare its three-week price structure." />}
        {loadingScan && <Spin />}
        {data && !loadingScan && visibleCards.length === 0 && <Empty description={momentumFilter === "ABOVE_200_DMA" ? "No stocks are above the 200 DMA." : "No stocks are available in this watchlist."} />}
        {data && !loadingScan && summaryRows.length > 0 && (
          <Card title="Watchlist evidence summary">
            <Text type="secondary" style={{ display: "block", marginBottom: 8, fontSize: 12 }}>
              Each stock can match multiple signals. Volume anomaly means at least 2× the prior 10-trading-day average. ROC is momentum speed; Δ ROC is its week-over-week acceleration. Rising from negative means improving, not yet positive momentum.
            </Text>
            <Table<WatchlistSummaryRow>
              data-testid="watchlist-evidence-summary-table"
              rowKey="key"
              size="small"
              pagination={false}
              scroll={{ x: true }}
              columns={summaryColumns}
              dataSource={summaryRows}
            />
          </Card>
        )}
        {visibleCards.map((card) => {
          const marketSnapshot = buildMarketSnapshot(card, quotesBySymbol[card.symbol]?.ltp ?? null);

          return (
            <Card
              key={card.symbol}
              size="small"
              data-testid={`watchlist-stock-card-${card.symbol}`}
              title={<Space size={8} wrap><a aria-label={`Open ${card.symbol} in Kite`} href={buildKiteChartUrl(card.symbol, card.instrumentToken)} target="_blank" rel="noopener noreferrer"><Text strong>{card.symbol}</Text><Text type="secondary"> · {card.companyName}</Text></a><WatchlistMarketSnapshotView snapshot={marketSnapshot} /></Space>}
              extra={<Button size="small" onClick={() => onOpenStockReview(card.symbol)}>Open review</Button>}
            >
              <div style={{ marginBottom: 10, padding: "8px 10px", background: "#fafafa", borderRadius: 6 }}>
                <Text type="secondary" style={{ display: "block", fontSize: 11, marginBottom: 4 }}>MOMENTUM EVIDENCE</Text>
                <MomentumEvidenceSummary evidence={card.momentumEvidence} />
              </div>
              {card.momentumEvidence && card.momentumEvidence.participation_events.length > 0 && (
                <Collapse
                  size="small"
                  defaultActiveKey={["volume-events"]}
                  items={[{
                    key: "volume-events",
                    label: `Volume events · last ${card.momentumEvidence.participation_lookback_days} days (${card.momentumEvidence.participation_events.length})`,
                    children: <MomentumParticipationTable evidence={card.momentumEvidence} currentLtp={quotesBySymbol[card.symbol]?.ltp} />,
                  }]}
                  style={{ marginBottom: 10 }}
                />
              )}
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
          );
        })}
      </Space>
    </div>
  );
}
