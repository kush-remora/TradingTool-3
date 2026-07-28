import { Alert, Button, Card, Empty, Input, Space, Spin, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useMemo, useState, type ReactNode } from "react";
import { InstrumentSearch } from "../components/InstrumentSearch";
import { LiveMarketWidget } from "../components/LiveMarketWidget";
import { BuySellChangeCalculator } from "../components/BuySellChangeCalculator";
import { useInstrumentSearch } from "../hooks/useInstrumentSearch";
import { useStockDetail } from "../hooks/useStockDetail";
import type { DeliveryDayDetail, InstrumentSearchResult, StockNote } from "../types";
import { deleteJson, getJson, postJson } from "../utils/api";
import {
  buildWeeklyPriceSummaries,
  type WeeklyPriceSummary,
  buildWeeklyPriceTimelines,
  type WeeklyPriceTimelineDay,
} from "../utils/threeWeekStockReview";

const { Text, Title } = Typography;
const { TextArea } = Input;
const COMPACT_HISTORY_DAYS = 30;
const THREE_MONTH_HISTORY_DAYS = 70;
const WEEKS_TO_DISPLAY = 4;
const THREE_MONTH_WEEKS_TO_DISPLAY = 14;

interface DailyPriceRow extends WeeklyPriceTimelineDay {
  key: string;
  day: string;
}

function formatDay(date: string): string {
  return new Intl.DateTimeFormat("en-IN", { weekday: "short", timeZone: "UTC" }).format(
    new Date(`${date}T00:00:00Z`),
  );
}

function formatPrice(value: number): string {
  return `₹${value.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function formatQuantity(value: number | null): string {
  return value == null ? "—" : value.toLocaleString("en-IN", { maximumFractionDigits: 0 });
}

function formatDeliveryPercentage(value: number | null): string {
  return value == null ? "—" : `${value.toFixed(2)}%`;
}

function formatDateWithDay(date: string): string {
  return `${date} (${formatDay(date)})`;
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
  const [notes, setNotes] = useState<StockNote[]>([]);
  const [noteText, setNoteText] = useState("");
  const [notesError, setNotesError] = useState<string | null>(null);
  const { allInstruments, loading: instrumentsLoading, error: instrumentsError } = useInstrumentSearch();
  const historyDays = showThreeMonths ? THREE_MONTH_HISTORY_DAYS : COMPACT_HISTORY_DAYS;
  const weeksToDisplay = showThreeMonths ? THREE_MONTH_WEEKS_TO_DISPLAY : WEEKS_TO_DISPLAY;
  const { data, loading, error } = useStockDetail(selectedInstrument?.trading_symbol ?? null, historyDays);
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
    const token = selectedInstrument?.instrument_token;
    if (!token) { setNotes([]); return; }
    void getJson<StockNote[]>(`/api/stocks/notes/${token}`, { useCache: false })
      .then(setNotes)
      .catch((requestError: unknown) => setNotesError(requestError instanceof Error ? requestError.message : "Failed to load notes"));
  }, [selectedInstrument?.instrument_token]);
  const addNote = async (): Promise<void> => {
    if (!selectedInstrument || !noteText.trim()) return;
    try {
      const note = await postJson<StockNote>("/api/stocks/notes", { instrumentToken: selectedInstrument.instrument_token, notes: noteText });
      setNotes((currentNotes) => [note, ...currentNotes]);
      setNoteText("");
    } catch (requestError) { setNotesError(requestError instanceof Error ? requestError.message : "Failed to save note"); }
  };
  const removeNote = async (id: number): Promise<void> => {
    try { await deleteJson(`/api/stocks/notes/${id}`); setNotes((currentNotes) => currentNotes.filter((note) => note.id !== id)); }
    catch (requestError) { setNotesError(requestError instanceof Error ? requestError.message : "Failed to delete note"); }
  };
  const weeklySummaries = useMemo(
    () => buildWeeklyPriceSummaries(data?.days ?? [], WEEKS_TO_DISPLAY),
    [data?.days],
  );
  const weeklyTimelines = useMemo(
    () => buildWeeklyPriceTimelines(data?.days ?? [], weeksToDisplay),
    [data?.days, weeksToDisplay],
  );
  const dailyRows = useMemo<DailyPriceRow[]>(() => weeklyTimelines
    .flatMap((timeline) => timeline.days)
    .sort((left, right) => right.date.localeCompare(left.date))
    .map((day) => ({ ...day, key: day.date, day: formatDay(day.date) })), [weeklyTimelines]);

  const dailyColumns: ColumnsType<DailyPriceRow> = [
    { title: "Date", dataIndex: "date", key: "date", width: 110 },
    { title: "Day", dataIndex: "day", key: "day", width: 75 },
    { title: "Open", dataIndex: "open", key: "open", render: formatPrice },
    { title: "Low", dataIndex: "low", key: "low", render: formatPrice },
    { title: "Close", dataIndex: "close", key: "close", render: formatPrice },
    { title: "High", dataIndex: "high", key: "high", render: formatPrice },
    { title: "Daily %", dataIndex: "dailyMovePct", key: "dailyMovePct", render: formatPercent },
    { title: "Week %", dataIndex: "accumulatedWeeklyPct", key: "accumulatedWeeklyPct", render: formatPercent },
  ];

  const weeklyColumns: ColumnsType<WeeklyPriceSummary> = [
    { title: "Week", dataIndex: "weekLabel", key: "weekLabel" },
    { title: "Weekly low", key: "low", render: (_, row) => formatPrice(row.low) },
    { title: "Low date / day", key: "lowDate", render: (_, row) => formatDateWithDay(row.lowDate) },
    { title: "Weekly high", key: "high", render: (_, row) => formatPrice(row.high) },
    { title: "High date / day", key: "highDate", render: (_, row) => formatDateWithDay(row.highDate) },
    { title: "Range", key: "rangePct", render: (_, row) => `${row.rangePct.toFixed(2)}%` },
  ];
  const deliveryColumns: ColumnsType<DeliveryDayDetail> = [
    { title: "Date", dataIndex: "date", key: "date", width: 94 },
    { title: "Delivery %", dataIndex: "delivery_percentage", key: "delivery_percentage", align: "right", render: formatDeliveryPercentage },
    { title: "Delivered qty", dataIndex: "delivered_quantity", key: "delivered_quantity", align: "right", render: formatQuantity },
    { title: "Traded qty", dataIndex: "traded_quantity", key: "traded_quantity", align: "right", render: formatQuantity },
  ];

  return (
    <div style={{ padding: 24 }}>
      <Space orientation="vertical" size={16} style={{ width: "100%" }}>
        <Card>
          <Space orientation="vertical" size={8} style={{ width: "100%" }}>
            <Title level={3} style={{ margin: 0 }}>Three-Week Stock Review + Current Week</Title>
            <Text type="secondary">Review the previous three completed weeks and the latest/current week of daily OHLC data.</Text>
            <div style={{ maxWidth: 420 }}>
              {instrumentsLoading ? <Spin size="small" /> : <InstrumentSearch instruments={nseEquities} value={selectedInstrument} onSelect={setSelectedInstrument} placeholder="Search any NSE stock" />}
              {instrumentsError && <Text type="danger">{instrumentsError}</Text>}
            </div>
          </Space>
        </Card>

        {selectedInstrument && (
          <Card size="small">
            <div style={{ display: "flex", flexWrap: "wrap", gap: 24, alignItems: "flex-start" }}>
              <Space orientation="vertical" size={2} style={{ minWidth: 180 }}>
                <Text type="secondary" style={{ fontSize: 11, fontWeight: 700, letterSpacing: 0.5 }}>LIVE MARKET</Text>
                <LiveMarketWidget symbol={`NSE:${selectedInstrument.trading_symbol}`} mode="wide" />
              </Space>
              <div style={{ flex: "1 1 460px", minWidth: 0 }}>
                <Text type="secondary" style={{ fontSize: 11, fontWeight: 700, letterSpacing: 0.5 }}>LAST 5 DELIVERY SESSIONS</Text>
                {data ? (
                  <Table
                    data-testid="delivery-history-table"
                    columns={deliveryColumns}
                    dataSource={data.delivery_days}
                    rowKey="date"
                    pagination={false}
                    size="small"
                    locale={{ emptyText: "No delivery data available." }}
                    style={{ marginTop: 4 }}
                  />
                ) : <Text type="secondary" style={{ display: "block", marginTop: 8, fontSize: 12 }}>Loading delivery data…</Text>}
              </div>
            </div>
          </Card>
        )}
        {selectedInstrument && <Card size="small" title="Notes">
          <Space orientation="vertical" size={8} style={{ width: "100%" }}>
            <TextArea aria-label="New note" value={noteText} onChange={(event) => setNoteText(event.target.value)} placeholder="Add a research note" autoSize={{ minRows: 2, maxRows: 4 }} />
            <Button size="small" type="primary" onClick={() => void addNote()} disabled={!noteText.trim()}>Add note</Button>
            {notesError && <Text type="danger">{notesError}</Text>}
            {notes.map((note) => <Card key={note.id} size="small"><Space direction="vertical" size={2}><Text>{note.notes}</Text><Text type="secondary" style={{ fontSize: 11 }}>{new Date(note.createdAt).toLocaleString("en-IN")}</Text><Button size="small" danger type="link" onClick={() => void removeNote(note.id)}>Delete</Button></Space></Card>)}
          </Space>
        </Card>}

        {error && <Alert type="error" message={error} showIcon />}
        {!selectedInstrument && <Empty description="Select a stock to start the three-week review." />}
        {loading && <Spin />}
        {data && !loading && <>
          <Card title={`${data.symbol}: weekly high, low, and range`}>
            <Table rowKey="weekLabel" columns={weeklyColumns} dataSource={weeklySummaries} pagination={false} scroll={{ x: true }} size="small" />
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
      <div data-testid="floating-change-calculator" style={{ position: "fixed", right: 24, bottom: 24, zIndex: 1000, maxWidth: "calc(100vw - 32px)" }}>
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
