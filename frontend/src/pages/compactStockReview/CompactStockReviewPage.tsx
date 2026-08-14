import { Alert, Button, Drawer, Modal, Spin, message } from "antd";
import { useEffect, useMemo, useState } from "react";
import { PaperTradeEntryForm } from "../../components/PaperTradeEntryForm";
import { useInstrumentNotes } from "../../hooks/useInstrumentNotes";
import { useInstrumentSearch } from "../../hooks/useInstrumentSearch";
import { useLiveMarketData } from "../../hooks/useLiveMarketData";
import { useStockDetail } from "../../hooks/useStockDetail";
import { useTradeData } from "../../hooks/useTradeData";
import { getJson } from "../../utils/api";
import type {
  CreateTradeInput,
  InstrumentSearchResult,
  UniverseOption,
  UniverseOptionsResponse,
} from "../../types";
import { calculatePnL } from "../../utils/pnlUtils";
import { CompactReviewHeader, type CompactPaperPosition } from "./CompactReviewHeader";
import { CompactReviewStory } from "./CompactReviewStory";
import { CompactReviewTables } from "./CompactReviewTables";
import { CompactStockChart } from "./CompactStockChart";
import { CompactFourWeekSummary } from "./CompactFourWeekSummary";
import {
  buildCompactDailyRows,
  buildCompactWeeklyRows,
  participationEventDates,
} from "./compactStockReview";
import { downloadCompactReviewMarkdown } from "./compactReviewExport";
import { formatTradeDate, getDaysSinceTrade } from "../paperTradeBook/paperTradeBookUtils";
import "./compactStockReview.css";

const HISTORY_DAYS = 150;
const CHART_DAYS = 150;
const RECENT_TAPE_DAYS = 10;
type CompactReviewTab = "review" | "summary";

export function CompactStockReviewPage() {
  const [selectedInstrument, setSelectedInstrument] = useState<InstrumentSearchResult | null>(null);
  const [watchlistOptions, setWatchlistOptions] = useState<UniverseOption[]>([]);
  const [selectedWatchlist, setSelectedWatchlist] = useState<string | null>(() => (
    new URLSearchParams(window.location.search).get("watchlist")
  ));
  const [activeTab, setActiveTab] = useState<CompactReviewTab>(() => (
    new URLSearchParams(window.location.search).get("view") === "summary" ? "summary" : "review"
  ));
  const [watchlistMembers, setWatchlistMembers] = useState<InstrumentSearchResult[]>([]);
  const [watchlistOptionsLoading, setWatchlistOptionsLoading] = useState(true);
  const [watchlistOptionsError, setWatchlistOptionsError] = useState<string | null>(null);
  const [watchlistLoading, setWatchlistLoading] = useState(false);
  const [watchlistError, setWatchlistError] = useState<string | null>(null);
  const [paperTradeOpen, setPaperTradeOpen] = useState(false);
  const [paperTradeSubmitting, setPaperTradeSubmitting] = useState(false);
  const { allInstruments, loading: instrumentsLoading, error: instrumentsError } = useInstrumentSearch();
  const nseEquities = useMemo(
    () => allInstruments.filter((instrument) => instrument.exchange === "NSE" && instrument.instrument_type === "EQ"),
    [allInstruments],
  );
  const { data, loading, error } = useStockDetail(selectedInstrument?.trading_symbol ?? null, HISTORY_DAYS);
  const liveData = useLiveMarketData(selectedInstrument ? `NSE:${selectedInstrument.trading_symbol}` : "");
  const instrumentNotes = useInstrumentNotes(selectedInstrument?.instrument_token ?? null);
  const { trades, createTrade: createPaperTrade, deleteTrade } = useTradeData();

  useEffect(() => {
    let active = true;
    void getJson<UniverseOptionsResponse>("/api/strategy/weekly-price-review/watchlists")
      .then((response) => {
        if (active) {
          setWatchlistOptions(response.options);
          setWatchlistOptionsError(null);
        }
      })
      .catch((requestError: unknown) => {
        if (active) {
          const errorMessage = requestError instanceof Error ? requestError.message : "Failed to load watchlists";
          setWatchlistOptionsError(errorMessage);
          setWatchlistError(errorMessage);
        }
      })
      .finally(() => {
        if (active) setWatchlistOptionsLoading(false);
      });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    if (!selectedWatchlist) {
      setWatchlistMembers([]);
      setWatchlistLoading(false);
      return;
    }

    let active = true;
    setWatchlistLoading(true);
    setWatchlistError(null);
    void getJson<InstrumentSearchResult[]>(`/api/stocks/watchlists/${encodeURIComponent(selectedWatchlist)}/members`, { useCache: false })
      .then((members) => {
        if (active) setWatchlistMembers(members);
      })
      .catch((requestError: unknown) => {
        if (active) {
          setWatchlistMembers([]);
          setWatchlistError(requestError instanceof Error ? requestError.message : "Failed to load watchlist stocks");
        }
      })
      .finally(() => {
        if (active) setWatchlistLoading(false);
      });
    return () => { active = false; };
  }, [selectedWatchlist]);

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
    setPaperTradeOpen(false);
    const url = new URL(window.location.href);
    if (instrument) url.searchParams.set("symbol", instrument.trading_symbol);
    else url.searchParams.delete("symbol");
    window.history.replaceState({}, "", `${url.pathname}${url.search}`);
  };

  const selectWatchlist = (watchlist: string | null): void => {
    setSelectedWatchlist(watchlist);
    setWatchlistMembers([]);
    const url = new URL(window.location.href);
    if (watchlist) url.searchParams.set("watchlist", watchlist);
    else url.searchParams.delete("watchlist");
    window.history.replaceState({}, "", `${url.pathname}${url.search}`);
  };

  const selectTab = (tab: CompactReviewTab): void => {
    setActiveTab(tab);
    const url = new URL(window.location.href);
    if (tab === "summary") url.searchParams.set("view", "summary");
    else url.searchParams.delete("view");
    window.history.replaceState({}, "", `${url.pathname}${url.search}`);
  };

  const moveWithinWatchlist = (direction: -1 | 1): void => {
    if (watchlistMembers.length === 0) return;
    const currentIndex = selectedInstrument
      ? watchlistMembers.findIndex((member) => member.trading_symbol === selectedInstrument.trading_symbol)
      : -1;
    const nextIndex = currentIndex < 0
      ? direction > 0 ? 0 : watchlistMembers.length - 1
      : (currentIndex + direction + watchlistMembers.length) % watchlistMembers.length;
    selectInstrument(watchlistMembers[nextIndex]);
  };

  useEffect(() => {
    if (!selectedWatchlist || watchlistLoading || watchlistMembers.length === 0) return;
    const currentIndex = selectedInstrument
      ? watchlistMembers.findIndex((member) => member.trading_symbol === selectedInstrument.trading_symbol)
      : -1;
    if (currentIndex < 0) selectInstrument(watchlistMembers[0]);
  }, [selectedWatchlist, watchlistLoading, watchlistMembers]);

  const dailyRows = useMemo(
    () => buildCompactDailyRows(data?.days ?? [], data?.delivery_days ?? []),
    [data?.days, data?.delivery_days],
  );
  const weeklyRows = useMemo(() => buildCompactWeeklyRows(dailyRows), [dailyRows]);
  const eventDates = useMemo(
    () => participationEventDates(data?.momentum_evidence?.participation_events ?? []),
    [data?.momentum_evidence?.participation_events],
  );
  const paperTradePrice = liveData?.ltp ?? dailyRows.at(-1)?.close ?? data?.fundamentals.currentPrice ?? null;
  const activePaperTrade = selectedInstrument
    ? trades.find((row) =>
        row.trade.nse_symbol.toUpperCase() === selectedInstrument.trading_symbol.toUpperCase()
        && row.trade.close_price == null,
      ) ?? null
    : null;
  const activePaperPnl = activePaperTrade && paperTradePrice != null
    ? calculatePnL(activePaperTrade.trade.avg_buy_price, paperTradePrice, activePaperTrade.trade.quantity)
    : null;
  const compactPaperPosition: CompactPaperPosition | null = activePaperTrade
    ? {
        symbol: activePaperTrade.trade.nse_symbol,
        entryDate: formatTradeDate(activePaperTrade.trade.trade_date),
        entryPrice: Number.parseFloat(activePaperTrade.trade.avg_buy_price),
        pnlPct: activePaperPnl?.pnlPct ?? null,
        pnlAmount: activePaperPnl?.pnl ?? null,
        isProfit: activePaperPnl?.isProfit ?? null,
        holdingDays: getDaysSinceTrade(activePaperTrade.trade.trade_date),
      }
    : null;

  const exportMarkdown = (): void => {
    if (!selectedInstrument || !data || loading) return;
    downloadCompactReviewMarkdown({
      instrument: selectedInstrument,
      data,
      liveData,
      dailyRows,
      weeklyRows,
      notes: instrumentNotes.notes,
    });
  };

  const addPaperTrade = async (payload: CreateTradeInput): Promise<void> => {
    setPaperTradeSubmitting(true);
    try {
      await createPaperTrade(payload);
      setPaperTradeOpen(false);
    } catch (requestError) {
      message.error(requestError instanceof Error ? requestError.message : "Failed to add paper trade");
      throw requestError;
    } finally {
      setPaperTradeSubmitting(false);
    }
  };

  const deleteActivePaperTrade = (): void => {
    if (!activePaperTrade) return;
    Modal.confirm({
      title: "Delete paper trade?",
      content: activePaperTrade.trade.nse_symbol + " will be removed from the Trade Book.",
      okText: "Delete",
      okType: "danger",
      onOk: async () => {
        await deleteTrade(activePaperTrade.trade.id);
        message.success(activePaperTrade.trade.nse_symbol + " paper trade deleted");
      },
    });
  };

  return (
    <div className="compact-review-page" data-testid="compact-stock-review-page">
      <div className="compact-review-shell">
        <div className="compact-review-toolbar">
          <div className="compact-review-tabs" role="tablist" aria-label="Compact review views">
            <button
              type="button"
              className={`compact-review-tab ${activeTab === "review" ? "compact-review-tab-active" : ""}`}
              role="tab"
              aria-selected={activeTab === "review"}
              onClick={() => selectTab("review")}
            >
              Stock review
            </button>
            <button
              type="button"
              className={`compact-review-tab ${activeTab === "summary" ? "compact-review-tab-active" : ""}`}
              role="tab"
              aria-selected={activeTab === "summary"}
              onClick={() => selectTab("summary")}
            >
              4W Summary
            </button>
          </div>
          {activeTab === "review" && <Button
            size="small"
            aria-label="Export compact review as Markdown"
            disabled={!selectedInstrument || !data || loading}
            onClick={exportMarkdown}
          >
            Export .md
          </Button>}
        </div>
        {activeTab === "review" && <CompactReviewHeader
          instrument={selectedInstrument}
          instruments={nseEquities}
          instrumentsLoading={instrumentsLoading}
          instrumentsError={instrumentsError}
          data={data}
          liveData={liveData}
          dailyRows={dailyRows}
          latestDay={dailyRows.at(-1) ?? null}
          onSelect={selectInstrument}
          watchlistOptions={watchlistOptions}
          selectedWatchlist={selectedWatchlist}
          watchlistMembers={watchlistMembers}
          watchlistLoading={watchlistLoading}
          watchlistError={watchlistError}
          onSelectWatchlist={selectWatchlist}
          onNavigateWatchlist={moveWithinWatchlist}
          paperPosition={compactPaperPosition}
          onDeletePaperTrade={deleteActivePaperTrade}
        />}

        {activeTab === "summary" && <CompactFourWeekSummary
          watchlistOptions={watchlistOptions}
          watchlistOptionsLoading={watchlistOptionsLoading}
          watchlistOptionsError={watchlistOptionsError}
          onOpenStockReview={(symbol) => {
            const instrument = nseEquities.find((candidate) => candidate.trading_symbol === symbol);
            if (instrument) selectInstrument(instrument);
            selectTab("review");
          }}
        />}

        {activeTab === "review" && <>
          {error && <Alert className="compact-review-alert" type="error" title={error} showIcon />}
          {!selectedInstrument && <div className="compact-review-empty">Select a stock to open the compact review.</div>}
          {selectedInstrument && loading && <div className="compact-review-loading"><Spin /><span>Loading stock evidence…</span></div>}

          {data && !loading && <>
          <div className="compact-review-main-grid">
            <section className="compact-review-chart-panel">
              <div className="compact-review-section-heading">
                <strong>Price · Volume · 150 sessions</strong>
                <span className="compact-review-chart-legend"><i className="compact-review-event-dot" />Volume event · <i className="compact-review-dma-line compact-review-dma-line-200" />200 DMA · <i className="compact-review-dma-line compact-review-dma-line-100" />100 DMA</span>
              </div>
              <CompactStockChart
                days={dailyRows.slice(-CHART_DAYS)}
                eventDates={eventDates}
                sma100={data.fundamentals.sma100}
                sma200={data.fundamentals.sma200}
              />
            </section>
            <CompactReviewStory
              days={dailyRows}
              notes={instrumentNotes.notes}
              notesLoading={instrumentNotes.loading}
              notesError={instrumentNotes.error}
              onAddNote={instrumentNotes.addNote}
              onPaperTrade={() => setPaperTradeOpen(true)}
            />
          </div>
          <CompactReviewTables
            weeks={weeklyRows}
            recentDays={[...dailyRows].reverse().slice(0, RECENT_TAPE_DAYS)}
            allDays={dailyRows}
          />
          <footer className="compact-review-footer">
            <span>Neutral values remain visible; only exceptions are highlighted.</span>
            <span>Delivery: {dailyRows.at(-1)?.deliveryPct == null ? "latest session pending / unavailable" : `updated ${dailyRows.at(-1)!.date}`}</span>
          </footer>
          </>}
        </>}
      </div>
      {paperTradeOpen && selectedInstrument && paperTradePrice != null && (
        <Drawer
          title={<span className="paper-trade-drawer-title">Add paper trade</span>}
          placement="right"
          open
          onClose={() => setPaperTradeOpen(false)}
          size={390}
        >
          <PaperTradeEntryForm
            initialInstrument={selectedInstrument}
            initialEntryPrice={paperTradePrice.toFixed(2)}
            onSubmit={addPaperTrade}
            loading={paperTradeSubmitting}
          />
        </Drawer>
      )}
    </div>
  );
}
