import { Alert, Button, Card, Empty, Space, Spin, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useMemo, useState, type ReactNode } from "react";
import { InstrumentSearch } from "../components/InstrumentSearch";
import { LiveMarketWidget } from "../components/LiveMarketWidget";
import { BuySellChangeCalculator } from "../components/BuySellChangeCalculator";
import { FloatingInstrumentNotes } from "../components/FloatingInstrumentNotes";
import { WeeklyStructureIndicator } from "../components/WeeklyStructureIndicator";
import { useInstrumentSearch } from "../hooks/useInstrumentSearch";
import { useInstrumentNotes } from "../hooks/useInstrumentNotes";
import { useStockDetail } from "../hooks/useStockDetail";
import type { DeliveryDayDetail, InstrumentSearchResult } from "../types";
import {
  buildWeeklyPriceSummaries,
  type WeeklyPriceSummary,
  buildWeeklyPriceTimelines,
  type WeeklyPriceTimelineDay,
} from "../utils/threeWeekStockReview";

const { Text, Title } = Typography;
const COMPACT_HISTORY_DAYS = 30;
const THREE_MONTH_HISTORY_DAYS = 70;
const WEEKS_TO_DISPLAY = 4;
const THREE_MONTH_WEEKS_TO_DISPLAY = 14;

interface DailyPriceRow extends WeeklyPriceTimelineDay {
  key: string;
  day: string;
  deliveryPct: number | null;
  lowFromOpenPct: number | null;
  highFromOpenPct: number | null;
  lowToHighPct: number | null;
}

function formatDay(date: string): string {
  return new Intl.DateTimeFormat("en-IN", { weekday: "short", timeZone: "UTC" }).format(
    new Date(`${date}T00:00:00Z`),
  );
}

function formatPrice(value: number): string {
  return `₹${value.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function formatCompactQuantity(value: number | null): string {
  if (value == null) return "—";
  if (value >= 10_000_000) return `${(value / 10_000_000).toFixed(2)} Cr`;
  if (value >= 100_000) return `${(value / 100_000).toFixed(2)} L`;
  if (value >= 1_000) return `${(value / 1_000).toFixed(1)} K`;
  return value.toLocaleString("en-IN");
}

function formatDeliveryPercentage(value: number | null): string {
  return value == null ? "—" : `${value.toFixed(2)}%`;
}

function formatDistance(currentPrice: number, referencePrice: number | null): string {
  return referencePrice == null || referencePrice === 0 ? "—" : `${(((currentPrice - referencePrice) / referencePrice) * 100).toFixed(1)}%`;
}

function formatDateWithDay(date: string): string {
  return `${date} (${formatDay(date)})`;
}

function formatCreatedDate(createdAt: string): string {
  return new Intl.DateTimeFormat("en-IN", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    timeZone: "Asia/Kolkata",
  }).format(new Date(createdAt));
}

function calculatePercentChange(value: number, referenceValue: number): number | null {
  if (referenceValue === 0) return null;
  return ((value - referenceValue) / referenceValue) * 100;
}

function calculateLowToHighPercent(low: number, high: number): number | null {
  return low === 0 ? null : ((high - low) / low) * 100;
}

function formatPercent(value: number | null): ReactNode {
  if (value === null) {
    return "—";
  }

  const color = value > 0 ? "#389e0d" : value < 0 ? "#cf1322" : undefined;
  return <span style={{ color, fontWeight: 600 }}>{`${value > 0 ? "+" : ""}${value.toFixed(2)}%`}</span>;
}

function getWeeklyExtremeStyle(row: DailyPriceRow): { backgroundColor: string } | undefined {
  if (row.isWeekLow && row.isWeekHigh) {
    return { backgroundColor: "#fffbe6" };
  }
  if (row.isWeekLow) {
    return { backgroundColor: "#fff1f0" };
  }
  if (row.isWeekHigh) {
    return { backgroundColor: "#f6ffed" };
  }
  return undefined;
}

export function ThreeWeekStockReviewPage() {
  const [selectedInstrument, setSelectedInstrument] = useState<InstrumentSearchResult | null>(null);
  const [showThreeMonths, setShowThreeMonths] = useState(false);
  const { allInstruments, loading: instrumentsLoading, error: instrumentsError } = useInstrumentSearch();
  const historyDays = showThreeMonths ? THREE_MONTH_HISTORY_DAYS : COMPACT_HISTORY_DAYS;
  const weeksToDisplay = showThreeMonths ? THREE_MONTH_WEEKS_TO_DISPLAY : WEEKS_TO_DISPLAY;
  const { data, loading, error } = useStockDetail(selectedInstrument?.trading_symbol ?? null, historyDays);
  const instrumentNotes = useInstrumentNotes(selectedInstrument?.instrument_token ?? null);
  const nseEquities = useMemo(
    () => allInstruments.filter((instrument) => instrument.exchange === "NSE" && instrument.instrument_type === "EQ"),
    [allInstruments],
  );
  useEffect(() => {
    const requestedSymbol = new URLSearchParams(window.location.search).get("symbol")?.trim().toUpperCase();
    if (!requestedSymbol || selectedInstrument) return;
    const instrument = nseEquities.find((candidate) => candidate.trading_symbol === requestedSymbol)
      ?? nseEquities.find((candidate) => candidate.trading_symbol.split("-")[0] === requestedSymbol);
    if (instrument) setSelectedInstrument(instrument);
  }, [nseEquities, selectedInstrument]);
  useEffect(() => {
    const syncFromUrl = (): void => setSelectedInstrument(null);
    window.addEventListener("popstate", syncFromUrl);
    return () => window.removeEventListener("popstate", syncFromUrl);
  }, []);
  const selectInstrument = (instrument: InstrumentSearchResult | null): void => {
    setSelectedInstrument(instrument);
    const url = new URL(window.location.href);
    if (instrument) url.searchParams.set("symbol", instrument.trading_symbol);
    else url.searchParams.delete("symbol");
    window.history.replaceState({}, "", `${url.pathname}${url.search}`);
  };
  const deliveryByDate = useMemo(
    () => new Map(data?.delivery_days?.map((delivery) => [delivery.date, delivery.delivery_percentage]) ?? []),
    [data?.delivery_days],
  );
  const volumeByDate = useMemo(
    () => new Map(data?.days.map((day) => [day.date, day.volume]) ?? []),
    [data?.days],
  );
  const weeklySummaries = useMemo(
    () => [...buildWeeklyPriceSummaries(
      data?.days.map((day) => ({
        ...day,
        deliveryPercentage: deliveryByDate.get(day.date) ?? null,
      })) ?? [],
      WEEKS_TO_DISPLAY,
    )].reverse(),
    [data?.days, deliveryByDate],
  );
  const weeklyTimelines = useMemo(
    () => buildWeeklyPriceTimelines(data?.days ?? [], weeksToDisplay),
    [data?.days, weeksToDisplay],
  );
  const dailyRows = useMemo<DailyPriceRow[]>(() => weeklyTimelines
    .flatMap((timeline) => timeline.days)
    .sort((left, right) => right.date.localeCompare(left.date))
    .map((day) => ({
      ...day,
      key: day.date,
      day: formatDay(day.date),
      deliveryPct: deliveryByDate.get(day.date) ?? null,
      lowFromOpenPct: calculatePercentChange(day.low, day.open),
      highFromOpenPct: calculatePercentChange(day.high, day.open),
      lowToHighPct: calculateLowToHighPercent(day.low, day.high),
    })), [deliveryByDate, weeklyTimelines]);

  const dailyColumns: ColumnsType<DailyPriceRow> = [
    { title: "Date", dataIndex: "date", key: "date", width: 92 },
    { title: "Day", dataIndex: "day", key: "day", width: 48 },
    { title: "Open", dataIndex: "open", key: "open", width: 90, render: formatPrice },
    { title: "Low", dataIndex: "low", key: "low", width: 90, render: formatPrice },
    { title: "Low %", dataIndex: "lowFromOpenPct", key: "lowFromOpenPct", width: 70, render: formatPercent },
    { title: "Close", dataIndex: "close", key: "close", width: 90, render: formatPrice },
    { title: "High", dataIndex: "high", key: "high", width: 90, render: formatPrice },
    { title: "Open → High %", dataIndex: "highFromOpenPct", key: "highFromOpenPct", width: 105, render: formatPercent },
    { title: "High %", dataIndex: "lowToHighPct", key: "lowToHighPct", width: 70, render: formatPercent },
    { title: "Vol", dataIndex: "volume", key: "volume", width: 72, render: formatCompactQuantity },
    { title: "Del %", dataIndex: "deliveryPct", key: "deliveryPct", width: 65, render: formatDeliveryPercentage },
    { title: "Daily %", dataIndex: "dailyMovePct", key: "dailyMovePct", width: 70, render: formatPercent },
    { title: "Week %", dataIndex: "accumulatedWeeklyPct", key: "accumulatedWeeklyPct", width: 70, render: formatPercent },
  ];

  const weeklyColumns: ColumnsType<WeeklyPriceSummary> = [
    { title: "Week", dataIndex: "weekLabel", key: "weekLabel", width: 125 },
    { title: "Low", key: "low", width: 85, render: (_, row) => formatPrice(row.low) },
    { title: "Low day · Del / Vol", key: "lowDate", width: 180, render: (_, row) => <>{formatDateWithDay(row.lowDate)} · {formatDeliveryPercentage(deliveryByDate.get(row.lowDate) ?? null)} / {formatCompactQuantity(volumeByDate.get(row.lowDate) ?? null)}</> },
    { title: "High", key: "high", width: 85, render: (_, row) => formatPrice(row.high) },
    { title: "High day · Del / Vol", key: "highDate", width: 180, render: (_, row) => <>{formatDateWithDay(row.highDate)} · {formatDeliveryPercentage(deliveryByDate.get(row.highDate) ?? null)} / {formatCompactQuantity(volumeByDate.get(row.highDate) ?? null)}</> },
    { title: "Range", key: "rangePct", width: 65, render: (_, row) => `${row.rangePct.toFixed(2)}%` },
    { title: "Structure", key: "structure", width: 105, render: (_, row) => <WeeklyStructureIndicator structure={row.weekOnWeekStructure} /> },
    { title: "Cue", key: "cue", width: 150, render: (_, row) => row.lowDayHasHigherVolumeAndDelivery ? <Text type="success" strong>Low-day D/V higher</Text> : "—" },
  ];
  const deliveryColumns: ColumnsType<DeliveryDayDetail> = [
    { title: "Date", dataIndex: "date", key: "date", width: 72, render: (date: string) => date.slice(5) },
    { title: "D%", dataIndex: "delivery_percentage", key: "delivery_percentage", width: 48, align: "right", render: formatDeliveryPercentage },
    {
      title: "Dlv / Trd",
      key: "quantities",
      width: 108,
      align: "right",
      render: (_, row) => `${formatCompactQuantity(row.delivered_quantity)} / ${formatCompactQuantity(row.traded_quantity)}`,
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <Space orientation="vertical" size={16} style={{ width: "100%" }}>
        <Card>
          <Space orientation="vertical" size={8} style={{ width: "100%" }}>
            <Title level={3} style={{ margin: 0 }}>Three-Week Stock Review + Current Week</Title>
            <Text type="secondary">Review the previous three completed weeks and the latest/current week of daily OHLC data.</Text>
            <div style={{ maxWidth: 420 }}>
              {instrumentsLoading ? <Spin size="small" /> : <InstrumentSearch instruments={nseEquities} value={selectedInstrument} onSelect={selectInstrument} placeholder="Search any NSE stock" />}
              {instrumentsError && <Text type="danger">{instrumentsError}</Text>}
            </div>
          </Space>
        </Card>

        {selectedInstrument && (
          <Card size="small">
            <div style={{ display: "flex", flexWrap: "wrap", gap: 16, alignItems: "flex-start" }}>
              <Space orientation="vertical" size={2} style={{ minWidth: 240 }}>
                <Text type="secondary" style={{ fontSize: 11, fontWeight: 700, letterSpacing: 0.5 }}>LIVE MARKET</Text>
                <LiveMarketWidget symbol={`NSE:${selectedInstrument.trading_symbol}`} mode="wide" />
              </Space>
              <div style={{ flex: "0 1 250px", minWidth: 220 }}>
                <Text type="secondary" style={{ fontSize: 11, fontWeight: 700, letterSpacing: 0.5 }}>DELIVERY · 5D</Text>
                {data ? (
                  <Table
                    data-testid="delivery-history-table"
                    columns={deliveryColumns}
                    dataSource={data.delivery_days?.slice(0, 5) ?? []}
                    rowKey="date"
                    pagination={false}
                    size="small"
                    locale={{ emptyText: "No delivery data available." }}
                    style={{ marginTop: 4, fontSize: 10 }}
                  />
                ) : <Text type="secondary" style={{ display: "block", marginTop: 8, fontSize: 12 }}>Loading delivery data…</Text>}
              </div>
              <div style={{ flex: "0 1 280px", minWidth: 250 }}>
                <Text type="secondary" style={{ fontSize: 11, fontWeight: 700, letterSpacing: 0.5 }}>FUNDAMENTALS</Text>
                {data?.fundamentals && <Table
                  size="small"
                  pagination={false}
                  showHeader={false}
                  style={{ marginTop: 4, fontSize: 12 }}
                  columns={[
                    { dataIndex: "label", key: "label", width: 92 },
                    { dataIndex: "value", key: "value", width: 88, align: "right" },
                    { dataIndex: "position", key: "position", width: 86, align: "right" },
                  ]}
                  dataSource={[
                    { key: "low", label: "52W low", value: data.fundamentals.fiftyTwoWeekLow == null ? "—" : formatPrice(data.fundamentals.fiftyTwoWeekLow), position: data.fundamentals.fiftyTwoWeekLow == null ? "—" : `+${formatDistance(data.fundamentals.currentPrice, data.fundamentals.fiftyTwoWeekLow)}` },
                    { key: "high", label: "52W high", value: formatPrice(data.fundamentals.fiftyTwoWeekHigh ?? 0), position: formatDistance(data.fundamentals.currentPrice, data.fundamentals.fiftyTwoWeekHigh) },
                    { key: "volume", label: "Avg vol 20D", value: formatCompactQuantity(data.avg_volume_20d), position: "" },
                    { key: "sma", label: "Fair · 200 DMA", value: data.fundamentals.sma200 == null ? "—" : formatPrice(data.fundamentals.sma200), position: "" },
                  ]}
                />}
              </div>
              <div style={{ flex: "0 1 300px", minWidth: 250 }}>
                <Text type="secondary" style={{ fontSize: 11, fontWeight: 700, letterSpacing: 0.5 }}>EXISTING NOTES</Text>
                <div data-testid="existing-stock-notes" style={{ marginTop: 4, maxHeight: 112, overflowY: "auto" }}>
                  {instrumentNotes.loading && <Text type="secondary" style={{ fontSize: 11 }}>Loading notes…</Text>}
                  {instrumentNotes.error && <Text type="danger" style={{ fontSize: 11 }}>{instrumentNotes.error}</Text>}
                  {!instrumentNotes.loading && !instrumentNotes.error && instrumentNotes.notes.length === 0 && <Text type="secondary" style={{ fontSize: 11 }}>No saved notes.</Text>}
                  {instrumentNotes.notes.map((note, index) => (
                    <div key={note.id} style={{ display: "flex", gap: 5, fontSize: 11, lineHeight: 1.35, marginBottom: 3 }}>
                      <Text type="secondary" style={{ fontSize: 11, flex: "0 0 auto" }}>{index + 1}.</Text>
                      <Text style={{ fontSize: 11, flex: 1 }}>{note.notes}</Text>
                      <Text type="secondary" style={{ fontSize: 10, flex: "0 0 auto" }}>{formatCreatedDate(note.createdAt)}</Text>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </Card>
        )}
        {error && <Alert type="error" message={error} showIcon />}
        {!selectedInstrument && <Empty description="Select a stock to start the three-week review." />}
        {loading && <Spin />}
        {data && !loading && <>
          <Card title={`${data.symbol}: weekly high, low, and range`}>
            <Text type="secondary" style={{ display: "block", marginBottom: 8, fontSize: 12 }}>Structure compares each week with the preceding week: ↑ higher high + higher low, ↓ lower high + lower low, → mixed or unchanged.</Text>
            <Table
              rowKey="weekLabel"
              columns={weeklyColumns}
              dataSource={weeklySummaries}
              pagination={false}
              scroll={{ x: true }}
              size="small"
              onRow={(row) => ({ style: row.lowDayHasHigherVolumeAndDelivery ? { backgroundColor: "#f6ffed" } : undefined })}
            />
          </Card>
          <Card title={`${data.symbol}: ${showThreeMonths ? "last three months" : "daily data for three completed weeks + current week"}`}>
            <Table columns={dailyColumns} dataSource={dailyRows} pagination={false} scroll={{ x: true }} size="small" sticky onRow={(row) => ({ style: getWeeklyExtremeStyle(row) })} />
            <Button type="link" size="small" onClick={() => setShowThreeMonths((visible) => !visible)}>
              {showThreeMonths ? "Show 4 weeks" : "Show 3 months"}
            </Button>
          </Card>
          <Alert type="info" showIcon message="Use the raw price structure first." description="Compare weekly highs, lows, and range yourself; confirm any accumulation idea with volume and delivery evidence." />
        </>}
      </Space>
      {selectedInstrument && <FloatingInstrumentNotes
        notes={instrumentNotes.notes}
        loading={instrumentNotes.loading}
        error={instrumentNotes.error}
        onAddNote={instrumentNotes.addNote}
        onRemoveNote={instrumentNotes.removeNote}
      />}
      <div data-testid="floating-change-calculator" style={{ position: "fixed", right: 20, bottom: 20, zIndex: 1000, maxWidth: "calc(100vw - 32px)", background: "#fff", border: "1px solid #f0f0f0", borderRadius: 8, boxShadow: "0 2px 8px rgba(0, 0, 0, 0.08)", padding: 6 }}>
        <BuySellChangeCalculator />
      </div>
    </div>
  );
}
