import { Alert, Button, Card, Empty, Select, Space, Spin, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useMemo, useState } from "react";
import type { UniverseOptionsResponse, WeeklyPriceWatchlistScannerResponse } from "../types";
import { useStockQuotes } from "../hooks/useStockQuotes";
import { getJson } from "../utils/api";

const { Text, Title } = Typography;
const LOOKBACK_CALENDAR_DAYS = 60;
const MIN_VOLUME_RATIO = 2;
const TOP_EVENT_COUNT = 3;

type WatchlistScanRow = WeeklyPriceWatchlistScannerResponse["rows"][number];
type MomentumEvent = NonNullable<WatchlistScanRow["momentum_evidence"]>["participation_events"][number];

interface VolumeEventRow {
  key: string;
  rank: number;
  eventDate: string;
  ageDays: number;
  close: number;
  rsi14: number | null;
  volume: number;
  volumeRatio: number;
  deliveryPercentage: number | null;
  moveSinceEventPct: number | null;
}

interface VolumeEventStockRow {
  key: string;
  symbol: string;
  companyName: string;
  instrumentToken: number;
  currentPrice: number | null;
  eventCount: number;
  events: VolumeEventRow[];
}

interface StockQuote {
  ltp: number | null;
}

function buildKiteChartUrl(symbol: string, instrumentToken: number): string {
  return `https://kite.zerodha.com/chart/web/tvc/NSE/${encodeURIComponent(symbol)}/${instrumentToken}`;
}

function formatPrice(value: number | null): string {
  return value == null ? "—" : `₹${value.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function formatVolume(value: number): string {
  if (value >= 10_000_000) return `${(value / 10_000_000).toFixed(2)} Cr`;
  if (value >= 100_000) return `${(value / 100_000).toFixed(2)} L`;
  if (value >= 1_000) return `${(value / 1_000).toFixed(1)} K`;
  return value.toLocaleString("en-IN");
}

function formatSignedPercent(value: number | null): string {
  if (value == null) return "—";
  return `${value >= 0 ? "+" : ""}${value.toFixed(2)}%`;
}

function formatRsi(value: number | null): string {
  return value == null ? "—" : value.toFixed(2);
}

function formatDelivery(value: number | null): string {
  return value == null ? "—" : `${value.toFixed(2)}%`;
}

function formatDate(date: string): string {
  return new Intl.DateTimeFormat("en-IN", { day: "2-digit", month: "short", timeZone: "UTC" }).format(new Date(`${date}T00:00:00Z`));
}

function calculateAgeInDays(asOfDate: string, eventDate: string): number {
  const asOf = Date.parse(`${asOfDate}T00:00:00Z`);
  const event = Date.parse(`${eventDate}T00:00:00Z`);
  return Math.max(0, Math.floor((asOf - event) / 86_400_000));
}

function getLatestClose(row: WatchlistScanRow): number | null {
  const sortedDays = [...row.days].sort((left, right) => left.date.localeCompare(right.date));
  return sortedDays[sortedDays.length - 1]?.close
    ?? row.momentum_evidence?.current_close
    ?? null;
}

function getEventCandidates(row: WatchlistScanRow): MomentumEvent[] {
  const evidence = row.momentum_evidence;
  if (!evidence) return [];

  return evidence.participation_events
    .filter((event) => calculateAgeInDays(evidence.as_of_date, event.event_date) <= LOOKBACK_CALENDAR_DAYS)
    .filter((event) => event.volume_ratio >= MIN_VOLUME_RATIO)
    .sort((left, right) => right.volume_ratio - left.volume_ratio || right.event_date.localeCompare(left.event_date));
}

function buildStockRows(
  response: WeeklyPriceWatchlistScannerResponse | null,
  quotesBySymbol: Record<string, StockQuote | undefined>,
): VolumeEventStockRow[] {
  if (!response) return [];

  return response.rows.map((row) => {
    const currentPrice = quotesBySymbol[row.symbol]?.ltp ?? getLatestClose(row);
    const evidence = row.momentum_evidence;
    const candidateEvents = getEventCandidates(row);
    const rankedEvents = candidateEvents.slice(0, TOP_EVENT_COUNT).map((event, index) => ({ event, rank: index + 1 }));
    const events = [...rankedEvents]
      .sort((left, right) => right.event.event_date.localeCompare(left.event.event_date))
      .map(({ event, rank }) => ({
        key: `${row.symbol}-${event.event_date}`,
        rank,
        eventDate: event.event_date,
        ageDays: evidence ? calculateAgeInDays(evidence.as_of_date, event.event_date) : 0,
        close: event.close,
        rsi14: event.rsi14 ?? null,
        volume: event.volume,
        volumeRatio: event.volume_ratio,
        deliveryPercentage: event.delivery_percentage,
        moveSinceEventPct: currentPrice == null || event.close <= 0 ? null : ((currentPrice - event.close) / event.close) * 100,
      }));

    return {
      key: row.symbol,
      symbol: row.symbol,
      companyName: row.companyName,
      instrumentToken: row.instrumentToken,
      currentPrice,
      eventCount: candidateEvents.length,
      events,
    };
  });
}

const eventColumns: ColumnsType<VolumeEventRow> = [
  { title: "Rank", dataIndex: "rank", key: "rank", width: 55 },
  { title: "Date", dataIndex: "eventDate", key: "eventDate", width: 90, render: formatDate },
  { title: "Age", dataIndex: "ageDays", key: "ageDays", width: 60, render: (value: number) => `${value}d` },
  { title: "Event close", dataIndex: "close", key: "close", width: 100, render: formatPrice },
  { title: "RSI 14", dataIndex: "rsi14", key: "rsi14", width: 75, render: formatRsi },
  { title: "Volume", dataIndex: "volume", key: "volume", width: 90, render: formatVolume },
  { title: "Volume / prior 5D avg", dataIndex: "volumeRatio", key: "volumeRatio", width: 150, render: (value: number) => `${value.toFixed(2)}×` },
  { title: "Delivery", dataIndex: "deliveryPercentage", key: "deliveryPercentage", width: 90, render: formatDelivery },
  { title: "Move since event", dataIndex: "moveSinceEventPct", key: "moveSinceEventPct", width: 120, render: formatSignedPercent },
];

export function VolumeEventReviewPage({ onOpenStockReview }: { onOpenStockReview: (symbol: string) => void }) {
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

  const quoteSymbols = useMemo(() => data?.rows.map((row) => row.symbol) ?? [], [data?.rows]);
  const { quotesBySymbol } = useStockQuotes(quoteSymbols);
  const rows = useMemo(() => buildStockRows(data, quotesBySymbol), [data, quotesBySymbol]);

  return (
    <div style={{ padding: "24px 24px 160px" }}>
      <Space orientation="vertical" size={16} style={{ width: "100%" }}>
        <Card>
          <Space orientation="vertical" size={8} style={{ width: "100%" }}>
            <Title level={3} style={{ margin: 0 }}>Volume Event Review</Title>
            <Text type="secondary">Find the three largest volume events per stock during the last 60 calendar days. This view shows raw evidence only; it does not classify accumulation or distribution.</Text>
            <Select
              aria-label="Watchlist"
              loading={loadingOptions}
              value={selectedWatchlist}
              onChange={setSelectedWatchlist}
              placeholder="Select a watchlist"
              style={{ width: 360, maxWidth: "100%" }}
              options={options.map((option) => ({ value: option.value, label: `${option.label} (${option.count})` }))}
            />
            {data && <Text type="secondary" style={{ fontSize: 12 }}>
              Lookback: {LOOKBACK_CALENDAR_DAYS} calendar days · minimum event: {MIN_VOLUME_RATIO.toFixed(1)}× prior 5-trading-day average · top {TOP_EVENT_COUNT} by multiplier.
            </Text>}
          </Space>
        </Card>

        {error && <Alert type="error" message={error} showIcon />}
        {!selectedWatchlist && !loadingOptions && <Empty description="Select a watchlist to review its largest volume events." />}
        {loadingScan && <Spin />}
        {data && !loadingScan && rows.length > 0 && (
          <Card title={`Stocks · ${rows.length}`}>
            <Table<VolumeEventStockRow>
              data-testid="volume-event-review-table"
              rowKey="key"
              size="small"
              pagination={false}
              scroll={{ x: true }}
              columns={[
                {
                  title: "Stock",
                  key: "stock",
                  width: 170,
                  render: (_, row) => (
                    <Space orientation="vertical" size={0}>
                      <a aria-label={`Open ${row.symbol} in Kite`} href={buildKiteChartUrl(row.symbol, row.instrumentToken)} target="_blank" rel="noopener noreferrer">
                        <Text strong>{row.symbol}</Text>
                      </a>
                      <Text type="secondary" style={{ fontSize: 11 }}>{row.companyName}</Text>
                    </Space>
                  ),
                },
                { title: "Current LTP", key: "currentPrice", width: 105, render: (_, row) => formatPrice(row.currentPrice) },
                { title: "Events found", dataIndex: "eventCount", key: "eventCount", width: 100 },
                {
                  title: "Strongest event",
                  key: "strongestEvent",
                  width: 180,
                  sorter: (left, right) => (left.events.find((event) => event.rank === 1)?.eventDate ?? "").localeCompare(right.events.find((event) => event.rank === 1)?.eventDate ?? ""),
                  render: (_, row) => {
                    const strongestEvent = row.events.find((event) => event.rank === 1);
                    return strongestEvent
                      ? `${strongestEvent.volumeRatio.toFixed(2)}× · ${formatDate(strongestEvent.eventDate)} · ${formatSignedPercent(strongestEvent.moveSinceEventPct)}`
                      : <Text type="secondary">No ≥2× event</Text>;
                  },
                },
                { title: "Action", key: "action", width: 105, render: (_, row) => <Button size="small" onClick={() => onOpenStockReview(row.symbol)}>Open review</Button> },
              ]}
              expandable={{
                rowExpandable: (row) => row.events.length > 0,
                expandedRowRender: (row) => <Table<VolumeEventRow> rowKey="key" size="small" pagination={false} columns={eventColumns} dataSource={row.events} />,
              }}
              dataSource={rows}
            />
          </Card>
        )}
        {data && !loadingScan && rows.length === 0 && <Empty description="No stocks are available in this watchlist." />}
      </Space>
    </div>
  );
}
