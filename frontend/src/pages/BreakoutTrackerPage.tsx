import { CopyOutlined, DeleteOutlined, DownloadOutlined, EditOutlined, ExportOutlined, SaveOutlined } from "@ant-design/icons";
import { Alert, Button, Card, DatePicker, Empty, Input, InputNumber, Popconfirm, Space, Table, Typography, message } from "antd";
import type { ColumnsType } from "antd/es/table";
import dayjs, { type Dayjs } from "dayjs";
import { useMemo, useState } from "react";
import { InstrumentSearch } from "../components/InstrumentSearch";
import { useBreakoutTracker } from "../hooks/useBreakoutTracker";
import { useInstrumentSearch } from "../hooks/useInstrumentSearch";
import { useStockQuotes } from "../hooks/useStockQuotes";
import type { BreakoutTrackerEntry, InstrumentSearchResult } from "../types";
import { buildBreakoutTrackerCsv } from "../utils/breakoutTrackerCsv";

const { Text, Title } = Typography;

interface EntryDraft {
  instrument: InstrumentSearchResult | null;
  breakoutDate: Dayjs | null;
  breakoutPrice: number | null;
  notes: string;
}

const emptyDraft = (): EntryDraft => ({ instrument: null, breakoutDate: null, breakoutPrice: null, notes: "" });

function formatPrice(price: number | null | undefined): string {
  return price == null ? "—" : `₹${price.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function performance(currentPrice: number | null | undefined, breakoutPrice: number): number | null {
  if (currentPrice == null || breakoutPrice <= 0) return null;
  return ((currentPrice - breakoutPrice) / breakoutPrice) * 100;
}

function formatPerformance(value: number | null): React.ReactNode {
  if (value == null) return "—";
  const color = value > 0 ? "#389e0d" : value < 0 ? "#cf1322" : undefined;
  return <span style={{ color, fontWeight: 600 }}>{`${value > 0 ? "+" : ""}${value.toFixed(2)}%`}</span>;
}

interface BreakoutTrackerPageProps {
  onOpenStockReview: (symbol: string) => void;
}

export function BreakoutTrackerPage({ onOpenStockReview }: BreakoutTrackerPageProps) {
  const [draft, setDraft] = useState<EntryDraft>(emptyDraft);
  const [editingId, setEditingId] = useState<number | null>(null);
  const { allInstruments, loading: instrumentsLoading, error: instrumentsError } = useInstrumentSearch();
  const { entries, loading, error, saveEntry, removeEntry } = useBreakoutTracker();
  const { quotesBySymbol, loading: quotesLoading, error: quotesError } = useStockQuotes(entries.map((entry) => entry.symbol));
  const nseEquities = useMemo(
    () => allInstruments.filter((instrument) => instrument.exchange === "NSE" && instrument.instrument_type === "EQ"),
    [allInstruments],
  );

  const resetDraft = (): void => {
    setDraft(emptyDraft());
    setEditingId(null);
  };

  const save = async (): Promise<void> => {
    if (!draft.instrument || !draft.breakoutDate || !draft.breakoutPrice || draft.breakoutPrice <= 0) {
      message.error("Select a symbol, breakout date, and positive breakout price.");
      return;
    }

    const saved = await saveEntry({
      instrumentToken: draft.instrument.instrument_token,
      symbol: draft.instrument.trading_symbol,
      companyName: draft.instrument.company_name,
      breakoutDate: draft.breakoutDate.format("YYYY-MM-DD"),
      breakoutPrice: draft.breakoutPrice,
      notes: draft.notes,
    });
    if (saved) {
      message.success(editingId == null ? "Breakout candidate added." : "Breakout candidate updated.");
      resetDraft();
    }
  };

  const edit = (entry: BreakoutTrackerEntry): void => {
    const instrument = nseEquities.find((candidate) => candidate.instrument_token === entry.instrumentToken) ?? {
      instrument_token: entry.instrumentToken,
      trading_symbol: entry.symbol,
      company_name: entry.companyName,
      exchange: "NSE",
      instrument_type: "EQ",
    };
    setDraft({ instrument, breakoutDate: dayjs(entry.breakoutDate), breakoutPrice: entry.breakoutPrice, notes: entry.notes });
    setEditingId(entry.id);
  };

  const copyNotes = async (entry: BreakoutTrackerEntry): Promise<void> => {
    const details = `${entry.symbol}\nBreakout date: ${entry.breakoutDate}\nBreakout price: ${formatPrice(entry.breakoutPrice)}\n\n${entry.notes}`;
    try {
      await navigator.clipboard.writeText(details);
      message.success("Breakout details copied.");
    } catch {
      message.error("Could not copy details. Copy them manually from the notes.");
    }
  };

  const downloadCsv = (): void => {
    if (entries.length === 0) return;

    const blob = new Blob(["\uFEFF", buildBreakoutTrackerCsv(entries, quotesBySymbol)], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `breakout_tracker_${new Date().toISOString().slice(0, 10)}.csv`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  };

  const columns: ColumnsType<BreakoutTrackerEntry> = [
    { title: "Symbol", key: "symbol", width: 180, render: (_, entry) => <><Text strong>{entry.symbol}</Text><br /><Text type="secondary" style={{ fontSize: 11 }}>{entry.companyName}</Text></> },
    { title: "Breakout", key: "breakout", width: 160, render: (_, entry) => <>{entry.breakoutDate}<br /><Text>{formatPrice(entry.breakoutPrice)}</Text></> },
    { title: "Last price", key: "lastPrice", width: 110, render: (_, entry) => formatPrice(quotesBySymbol[entry.symbol]?.ltp) },
    { title: "Since breakout", key: "performance", width: 120, render: (_, entry) => formatPerformance(performance(quotesBySymbol[entry.symbol]?.ltp, entry.breakoutPrice)) },
    { title: "Notes", dataIndex: "notes", key: "notes", render: (notes: string, entry) => <Space orientation="vertical" size={2}><Text style={{ whiteSpace: "pre-wrap", fontSize: 12 }}>{notes || "—"}</Text><Button aria-label={`Copy ${entry.symbol} details`} type="link" size="small" icon={<CopyOutlined />} onClick={() => void copyNotes(entry)} style={{ alignSelf: "flex-start", padding: 0 }}>Copy details</Button></Space> },
    { title: "", key: "actions", width: 128, render: (_, entry) => <Space size={2}><Button aria-label={`Open ${entry.symbol} three-week review`} type="link" size="small" icon={<ExportOutlined />} onClick={() => onOpenStockReview(entry.symbol)}>Review</Button><Button aria-label={`Edit ${entry.symbol}`} type="text" size="small" icon={<EditOutlined />} onClick={() => edit(entry)} /><Popconfirm title="Remove this breakout candidate?" onConfirm={() => void removeEntry(entry.id)}><Button aria-label={`Remove ${entry.symbol}`} type="text" danger size="small" icon={<DeleteOutlined />} /></Popconfirm></Space> },
  ];

  return (
    <div style={{ padding: 24, maxWidth: 1440, margin: "0 auto" }}>
      <Space orientation="vertical" size={16} style={{ width: "100%" }}>
        <Card size="small">
          <Title level={3} style={{ margin: 0 }}>Breakout Tracker</Title>
          <Text type="secondary">Keep quiet accumulation candidates visible until their price and volume confirm the next Wyckoff phase.</Text>
          <div style={{ display: "grid", gridTemplateColumns: "minmax(220px, 1fr) 150px 150px", gap: 8, marginTop: 12 }}>
            <InstrumentSearch instruments={nseEquities} value={draft.instrument} onSelect={(instrument) => setDraft((current) => ({ ...current, instrument }))} placeholder="Select NSE symbol" />
            <DatePicker aria-label="Breakout date" value={draft.breakoutDate} onChange={(date) => setDraft((current) => ({ ...current, breakoutDate: date }))} size="small" style={{ width: "100%" }} />
            <InputNumber aria-label="Breakout price" value={draft.breakoutPrice} onChange={(price) => setDraft((current) => ({ ...current, breakoutPrice: price }))} min={0.01} precision={2} prefix="₹" placeholder="Breakout price" size="small" style={{ width: "100%" }} />
          </div>
          <div style={{ marginTop: 8 }}>
            <Input.TextArea aria-label="Accumulation notes" value={draft.notes} onChange={(event) => setDraft((current) => ({ ...current, notes: event.target.value }))} placeholder="Paste accumulation evidence here: breakout volume, five-day delivery %, base structure, and chart observations." autoSize={{ minRows: 3, maxRows: 6 }} />
          </div>
          <Space style={{ marginTop: 8 }}>
            <Button type="primary" size="small" icon={<SaveOutlined />} onClick={() => void save()}>{editingId == null ? "Add candidate" : "Save changes"}</Button>
            {editingId != null && <Button size="small" onClick={resetDraft}>Cancel</Button>}
          </Space>
          {(instrumentsLoading || instrumentsError) && <div style={{ marginTop: 8 }}><Text type={instrumentsError ? "danger" : "secondary"}>{instrumentsError ?? "Loading symbols…"}</Text></div>}
        </Card>
        {error && <Alert type="error" showIcon message={error} />}
        {quotesError && <Alert type="warning" showIcon message={`Current prices unavailable: ${quotesError}`} />}
        <Card
          size="small"
          title="Tracked candidates"
          extra={
            <Space size={8}>
              {quotesLoading && <Text type="secondary">Refreshing prices…</Text>}
              <Button aria-label="Download all entries as CSV" size="small" icon={<DownloadOutlined />} disabled={entries.length === 0} onClick={downloadCsv}>
                Download CSV
              </Button>
            </Space>
          }
        >
          {entries.length === 0 && !loading ? <Empty description="Add an accumulation candidate to start tracking it." /> : <Table columns={columns} dataSource={entries} rowKey="id" loading={loading} size="small" pagination={false} scroll={{ x: 900 }} />}
        </Card>
      </Space>
    </div>
  );
}
