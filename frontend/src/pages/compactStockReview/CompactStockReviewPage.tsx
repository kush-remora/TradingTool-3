import { Alert, Button, Spin } from "antd";
import { useEffect, useMemo, useState } from "react";
import { useInstrumentNotes } from "../../hooks/useInstrumentNotes";
import { useInstrumentSearch } from "../../hooks/useInstrumentSearch";
import { useLiveMarketData } from "../../hooks/useLiveMarketData";
import { useStockDetail } from "../../hooks/useStockDetail";
import { getJson } from "../../utils/api";
import type { InstrumentSearchResult, UniverseOption, UniverseOptionsResponse } from "../../types";
import { CompactReviewHeader } from "./CompactReviewHeader";
import { CompactReviewStory } from "./CompactReviewStory";
import { CompactReviewTables } from "./CompactReviewTables";
import { CompactStockChart } from "./CompactStockChart";
import {
  buildCompactDailyRows,
  buildCompactWeeklyRows,
  participationEventDates,
} from "./compactStockReview";
import { downloadCompactReviewMarkdown } from "./compactReviewExport";
import "./compactStockReview.css";

const HISTORY_DAYS = 150;
const CHART_DAYS = 150;
const RECENT_TAPE_DAYS = 10;

export function CompactStockReviewPage() {
  const [selectedInstrument, setSelectedInstrument] = useState<InstrumentSearchResult | null>(null);
  const [watchlistOptions, setWatchlistOptions] = useState<UniverseOption[]>([]);
  const [selectedWatchlist, setSelectedWatchlist] = useState<string | null>(() => (
    new URLSearchParams(window.location.search).get("watchlist")
  ));
  const [watchlistMembers, setWatchlistMembers] = useState<InstrumentSearchResult[]>([]);
  const [watchlistLoading, setWatchlistLoading] = useState(false);
  const [watchlistError, setWatchlistError] = useState<string | null>(null);
  const { allInstruments, loading: instrumentsLoading, error: instrumentsError } = useInstrumentSearch();
  const nseEquities = useMemo(
    () => allInstruments.filter((instrument) => instrument.exchange === "NSE" && instrument.instrument_type === "EQ"),
    [allInstruments],
  );
  const { data, loading, error } = useStockDetail(selectedInstrument?.trading_symbol ?? null, HISTORY_DAYS);
  const liveData = useLiveMarketData(selectedInstrument ? `NSE:${selectedInstrument.trading_symbol}` : "");
  const instrumentNotes = useInstrumentNotes(selectedInstrument?.instrument_token ?? null);

  useEffect(() => {
    let active = true;
    void getJson<UniverseOptionsResponse>("/api/strategy/weekly-price-review/watchlists")
      .then((response) => {
        if (active) setWatchlistOptions(response.options);
      })
      .catch((requestError: unknown) => {
        if (active) setWatchlistError(requestError instanceof Error ? requestError.message : "Failed to load watchlists");
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

  return (
    <div className="compact-review-page" data-testid="compact-stock-review-page">
      <div className="compact-review-shell">
        <div className="compact-review-toolbar">
          <Button
            size="small"
            aria-label="Export compact review as Markdown"
            disabled={!selectedInstrument || !data || loading}
            onClick={exportMarkdown}
          >
            Export .md
          </Button>
        </div>
        <CompactReviewHeader
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
        />

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
      </div>
    </div>
  );
}
