import { DeleteOutlined, LeftOutlined, RightOutlined, StockOutlined } from "@ant-design/icons";
import { Button, Select, Spin, Tooltip, Typography } from "antd";
import { InstrumentSearch } from "../../components/InstrumentSearch";
import type { FreshBreakoutDates, InstrumentSearchResult, LiveMarketUpdate, Roc9, Rsi14Range, StockDetailResponse, UniverseOption } from "../../types";
import type { CompactDailyRow } from "./compactStockReview";
import { buildCompactDeliveryContext, buildCompactThreeWeekFlow, formatPrice, formatQuantity, formatSignedPrice, type CompactDeliveryContext, type CompactThreeWeekFlow } from "./compactStockReview";

export interface CompactPaperPosition {
  symbol: string;
  entryDate: string;
  entryPrice: number;
  pnlPct: number | null;
  pnlAmount: number | null;
  isProfit: boolean | null;
  holdingDays: number;
}

const { Text } = Typography;

interface CompactReviewHeaderProps {
  instrument: InstrumentSearchResult | null;
  instruments: InstrumentSearchResult[];
  instrumentsLoading: boolean;
  instrumentsError: string | null;
  data: StockDetailResponse | null;
  liveData: LiveMarketUpdate | null;
  dailyRows: CompactDailyRow[];
  latestDay: CompactDailyRow | null;
  onSelect: (instrument: InstrumentSearchResult | null) => void;
  watchlistOptions: UniverseOption[];
  selectedWatchlist: string | null;
  watchlistMembers: InstrumentSearchResult[];
  watchlistLoading: boolean;
  watchlistError: string | null;
  onSelectWatchlist: (watchlist: string | null) => void;
  onNavigateWatchlist: (direction: -1 | 1) => void;
  paperPosition: CompactPaperPosition | null;
  onDeletePaperTrade: () => void;
}

const pos = (v: number | null) => v == null || v === 0 ? "" : v > 0 ? "crh-up" : "crh-dn";

const distPct = (price: number | null, ref: number | null): number | null =>
  price == null || ref == null || ref === 0 ? null : ((price - ref) / ref) * 100;

const fmtPct = (v: number | null, digits = 1): string =>
  v == null ? "—" : `${v > 0 ? "+" : ""}${v.toFixed(digits)}%`;

const buildKiteChartUrl = (symbol: string, instrumentToken: number): string =>
  `https://kite.zerodha.com/chart/web/tvc/NSE/${encodeURIComponent(symbol)}/${instrumentToken}`;

const priceMoveFromSessionsAgo = (
  currentPrice: number | null,
  days: CompactDailyRow[],
  lookback: number,
): number | null => {
  const priorDay = days.at(-(lookback + 1));
  if (currentPrice == null || priorDay == null || priorDay.close === 0) return null;
  return ((currentPrice - priorDay.close) / priorDay.close) * 100;
};

export function CompactReviewHeader({
  instrument,
  instruments,
  instrumentsLoading,
  instrumentsError,
  data,
  liveData,
  dailyRows,
  latestDay,
  onSelect,
  watchlistOptions,
  selectedWatchlist,
  watchlistMembers,
  watchlistLoading,
  watchlistError,
  onSelectWatchlist,
  onNavigateWatchlist,
  paperPosition,
  onDeletePaperTrade,
}: CompactReviewHeaderProps) {
  const f = data?.fundamentals ?? null;
  const currentPrice = liveData?.ltp ?? latestDay?.close ?? f?.currentPrice ?? null;
  const dataDate = data?.days.at(-1)?.date ?? null;
  const deliveryDate = data?.delivery_days.find((day) => day.delivery_percentage != null)?.date ?? null;
  const rsi14Range: Rsi14Range | null = data?.rsi14_range ?? null;
  const roc9: Roc9 | null = data?.roc9 ?? null;
  const breakoutDates: FreshBreakoutDates | null = data?.breakout_dates ?? null;
  const threeWeekFlow: CompactThreeWeekFlow | null = buildCompactThreeWeekFlow(dailyRows);
  const deliveryContext: CompactDeliveryContext = buildCompactDeliveryContext(dailyRows);
  const periodMoves = [5, 20, 40, 60].map((lookback) => ({
    lookback,
    movePct: priceMoveFromSessionsAgo(currentPrice, dailyRows, lookback),
  }));

  const sessionOpen = liveData?.open ?? latestDay?.open ?? null;
  const sessionLow = liveData?.low ?? latestDay?.low ?? null;
  const sessionOpenToLowPct = distPct(sessionLow, sessionOpen);
  const rangeSpreadPct  = latestDay?.spreadPct;
  const openClosePct    = latestDay?.openToClosePct;
  const volVs10d        = latestDay?.volumeVsPrior10dPct;
  const delivery        = latestDay?.deliveryPct;
  const closePos        = latestDay?.closePositionPct;   // 0–100%, position in day's range
  const vs200           = distPct(currentPrice, f?.sma200 ?? null);
  const vs100           = distPct(currentPrice, f?.sma100 ?? null);
  const vsLow           = distPct(currentPrice, f?.fiftyTwoWeekLow ?? null);
  const vsHigh          = distPct(currentPrice, f?.fiftyTwoWeekHigh ?? null);

  const volAnomaly      = volVs10d != null && volVs10d >= 150;
  const deliveryAnomaly = delivery != null && delivery >= 70;
  const rangeAnomaly    = rangeSpreadPct != null && Math.abs(rangeSpreadPct) >= 5;

  return (
    <header className="crh-header">
      <div className="crh-wrap crh-primary-wrap">

      {/* ── identity ── */}
      <div className="crh-identity">
        <div className="crh-identity-main" aria-hidden="true">
          <div className="crh-search">
            {instrumentsLoading
              ? <Spin size="small" />
              : <InstrumentSearch
                  instruments={instruments}
                  value={instrument}
                  onSelect={onSelect}
                  placeholder="Search NSE stock"
                  maxOptions={20}
                />}
          </div>
          <div className="crh-name">
            <strong>{instrument?.company_name ?? "Select a stock"}</strong>
            <span>{instrument ? `NSE · ${instrument.trading_symbol}` : "Compact Stock Review"}</span>
            <span className="crh-inline-ltp"><span className="crh-inline-ltp-label">LTP</span> <strong>{formatPrice(currentPrice)}</strong></span>
            {dataDate && <span className="crh-data-date" title="Latest completed daily candle">Close {formatHeaderDate(dataDate)}</span>}
          </div>
          {instrument && (
            <a
              className="crh-kite-link"
              aria-label={`Open ${instrument.trading_symbol} in Kite`}
              title={`Open ${instrument.trading_symbol} in Kite`}
              href={buildKiteChartUrl(instrument.trading_symbol, instrument.instrument_token)}
              target="_blank"
              rel="noopener noreferrer"
            >
              <StockOutlined />
            </a>
          )}
          {instrumentsError && <Text type="danger" className="crh-err">{instrumentsError}</Text>}
        </div>
        <WatchlistReviewNav
          options={watchlistOptions}
          selectedWatchlist={selectedWatchlist}
          instrument={instrument}
          members={watchlistMembers}
          loading={watchlistLoading}
          error={watchlistError}
          onSelectWatchlist={onSelectWatchlist}
          onNavigate={onNavigateWatchlist}
        />
        <div className="crh-primary-stock-search">
          <InstrumentSearch
            instruments={instruments}
            value={instrument}
            onSelect={onSelect}
            placeholder="Search stock"
            maxOptions={20}
          />
        </div>
      </div>

      {latestDay ? (<>

        {/* Range: Low → High + spread % */}
        <div className={`crh-col${rangeAnomaly ? " crh-col-alert" : ""}`}
             title={rangeAnomaly ? "Wide intraday range (≥5%)" : undefined}>
          <span className="crh-col-label">Range</span>
          <span className="crh-col-main">
            <span className="crh-muted">{formatPrice(latestDay.low)}</span>
            <span className="crh-arrow">→</span>
            <span className="crh-muted">{formatPrice(latestDay.high)}</span>
          </span>
          <span className={`crh-col-pct ${pos(rangeSpreadPct ?? null)}`}>
            {rangeSpreadPct == null ? "—" : `${Math.abs(rangeSpreadPct).toFixed(1)}%`}
          </span>
        </div>

        {/* O → C + directional % */}
        <div className={`crh-col${openClosePct != null && Math.abs(openClosePct) >= 3 ? " crh-col-alert" : ""}`}>
          <span className="crh-col-label">O → C</span>
          <span className="crh-col-main">
            <span className="crh-muted">{formatPrice(latestDay.open)}</span>
            <span className="crh-arrow">→</span>
            <span className="crh-muted">{formatPrice(latestDay.close)}</span>
          </span>
          <span className={`crh-col-pct ${pos(openClosePct ?? null)}`}>
            {fmtPct(openClosePct ?? null)}
          </span>
        </div>

        {/* Maximum downside from the session open to the session low */}
        <div className="crh-col crh-open-low-col" title="Maximum downside from today's open to today's low">
          <span className="crh-col-label">Dip O → L</span>
          <span className="crh-col-main">
            <span className="crh-muted">{formatPrice(sessionOpen)}</span>
            <span className="crh-arrow">→</span>
            <span className="crh-muted">{formatPrice(sessionLow)}</span>
          </span>
          <span className={`crh-col-pct ${pos(sessionOpenToLowPct)}`}>
            {formatSignedPrice(sessionLow != null && sessionOpen != null ? sessionLow - sessionOpen : null)} · {fmtPct(sessionOpenToLowPct)}
          </span>
        </div>

        {/* Close position in range — mini progress bar */}
        <div className="crh-col" title="Where in today's range did price close? 0% = at low, 100% = at high">
          <span className="crh-col-label">Close pos</span>
          <ClosePositionBar pct={closePos ?? null} />
          <span className="crh-muted" style={{ fontSize: 10 }}>
            {closePos == null ? "—" : `${closePos.toFixed(0)}%`}
          </span>
        </div>

        {/* Volume */}
        <div className={`crh-col${volAnomaly ? " crh-col-signal" : ""}`}
             title={volAnomaly ? "Volume ≥ 150% of 10-day avg" : undefined}>
          <span className="crh-col-label">Volume</span>
          <span className="crh-col-main">
            <strong className={volAnomaly ? "crh-signal-val" : ""}>{formatQuantity(latestDay.volume)}</strong>
          </span>
          <span className="crh-col-sub">
            <span className={`crh-vs10d${volAnomaly ? " crh-signal-tag" : ""}`}>
              {volVs10d == null ? "—" : `${volVs10d.toFixed(0)}%`} vs 10D
            </span>
            {data?.avg_volume_20d != null && (
              <span className="crh-muted">avg {formatQuantity(data.avg_volume_20d)}</span>
            )}
          </span>
        </div>

        {/* Delivery */}
        <div className={`crh-col${deliveryAnomaly || deliveryContext.state === "ERRATIC" ? " crh-col-signal" : ""}`}
             title={deliveryContext.state === "ERRATIC" ? "Erratic when prior 10-session delivery variation is at least 30% of its average" : deliveryAnomaly ? "Delivery ≥ 70%" : ""}>
          <span className="crh-col-label">Delivery</span>
          <span className="crh-col-main">
            <strong className={deliveryAnomaly ? "crh-signal-val" : "crh-muted"}>
              {delivery == null ? "—" : `${delivery.toFixed(1)}%`}
            </strong>
          </span>
          {deliveryContext.ratio != null && deliveryContext.averagePct != null && (
            <span className="crh-delivery-context">
              {deliveryContext.ratio.toFixed(2)}× · 10D avg {deliveryContext.averagePct.toFixed(1)}%
            </span>
          )}
          {deliveryContext.state && (
            <span className={`crh-delivery-state ${deliveryContext.state === "ERRATIC" ? "crh-dn" : "crh-muted"}`}>
              {deliveryContext.state === "ERRATIC" ? "Erratic" : "Stable"}
            </span>
          )}
          <span className="crh-delivery-date">as of {formatCompactDate(deliveryDate)}</span>
        </div>

        {/* RSI14: today + 60-day range */}
        <div className="crh-col" title="RSI14 today vs 60-session high/low — shows where current RSI sits in its own recent range">
          <span className="crh-col-label">RSI 14</span>
          <Rsi14RangeCol rsi={rsi14Range} />
        </div>

        {/* ROC-9: today + 3-day direction */}
        <div className="crh-col" title="Rate of Change (9 sessions) — positive = price higher than 9 days ago, direction arrow = improving or fading">
          <span className="crh-col-label">ROC 9</span>
          <Roc9Col roc={roc9} />
        </div>

        {/* DMA: 200D + 100D */}
        <div className="crh-col">
          <span className="crh-col-label">DMA</span>
          <span className="crh-col-main crh-col-stack">
            <span className="crh-dma-row">
              <span className="crh-muted">200D</span>
              <span className="crh-muted">{f?.sma200 != null ? formatPrice(f.sma200) : "—"}</span>
              <span className={`crh-col-pct ${pos(vs200)}`}>{fmtPct(vs200)}</span>
            </span>
            <span className="crh-dma-row">
              <span className="crh-muted">100D</span>
              <span className="crh-muted">{f?.sma100 != null ? formatPrice(f.sma100) : "—"}</span>
              <span className={`crh-col-pct ${pos(vs100)}`}>{fmtPct(vs100)}</span>
            </span>
          </span>
        </div>

        {/* 52-week: low + high */}
        <div className="crh-col">
          <span className="crh-col-label">52-week</span>
          <span className="crh-col-main crh-col-stack">
            <span className="crh-dma-row">
              <span className="crh-muted">↓</span>
              <span className="crh-muted">{f?.fiftyTwoWeekLow != null ? formatPrice(f.fiftyTwoWeekLow) : "—"}</span>
              <span className={`crh-col-pct ${pos(vsLow)}`}>{fmtPct(vsLow)}</span>
            </span>
            <span className="crh-dma-row">
              <span className="crh-muted">↑</span>
              <span className="crh-muted">{f?.fiftyTwoWeekHigh != null ? formatPrice(f.fiftyTwoWeekHigh) : "—"}</span>
              <span className={`crh-col-pct ${pos(vsHigh)}`}>{fmtPct(vsHigh)}</span>
            </span>
          </span>
        </div>

      </>) : null}
      </div>

      {latestDay && (
        <div className="crh-wrap crh-secondary-wrap">
          <div className="crh-secondary-stock">
            <div className="crh-identity-main">
              <div className="crh-search">
                {instrumentsLoading
                  ? <Spin size="small" />
                  : <InstrumentSearch
                      instruments={instruments}
                      value={instrument}
                      onSelect={onSelect}
                      placeholder="Search NSE stock"
                      maxOptions={20}
                    />}
              </div>
              <div className="crh-name">
                <strong>{instrument?.company_name ?? "Select a stock"}</strong>
                <span>{instrument ? "NSE · " + instrument.trading_symbol : "Compact Stock Review"}</span>
                <span className="crh-inline-ltp"><span className="crh-inline-ltp-label">LTP</span> <strong>{formatPrice(currentPrice)}</strong></span>
                {dataDate && <span className="crh-data-date" title="Latest completed daily candle">Close {formatHeaderDate(dataDate)}</span>}
              </div>
              {instrument && (
                <a
                  className="crh-kite-link"
                  aria-label={"Open " + instrument.trading_symbol + " in Kite"}
                  title={"Open " + instrument.trading_symbol + " in Kite"}
                  href={buildKiteChartUrl(instrument.trading_symbol, instrument.instrument_token)}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  <StockOutlined />
                </a>
              )}
              {instrumentsError && <Text type="danger" className="crh-err">{instrumentsError}</Text>}
            </div>
          </div>
          {/* Reserved secondary row for momentum and future compact data points */}
          <div className="crh-col crh-move-col" title="Price change from the close N trading sessions ago to the current price">
            <span className="crh-col-label">Move</span>
            <span className="crh-col-main crh-col-stack">
              {periodMoves.map(({ lookback, movePct }) => (
                <span className="crh-dma-row" key={lookback}>
                  <span className="crh-muted">{lookback}D</span>
                  <span className={`crh-col-pct ${pos(movePct)}`}>{fmtPct(movePct)}</span>
                </span>
              ))}
            </span>
          </div>
          <div
            className="crh-col crh-breakout-col"
            title="Latest fresh close-confirmed breakout above the prior N-session high"
          >
            <span className="crh-col-label">Last fresh breakout</span>
            <BreakoutDatesRow dates={breakoutDates} currentPrice={currentPrice} />
          </div>
          {/* Three-week flow: weekly low alignment + higher/lower low and high sequence */}
          <div className="crh-col crh-flow-col" title="W−3, W−2, and W−1 are completed weeks; WTD is the current week. Floor aligned means adjacent weekly lows are within 1%.">
            <span className="crh-col-label">4W flow</span>
            {threeWeekFlow ? <CompactFlowCol flow={threeWeekFlow} currentPrice={currentPrice} /> : <span className="crh-muted">—</span>}
          </div>
          {paperPosition && (
            <div className="crh-col crh-paper-position-col" role="region" aria-label="Open paper trade">
              <span className="crh-col-label">Paper position · {paperPosition.symbol}</span>
              <span className="crh-paper-position-line">
                <strong>₹{paperPosition.entryPrice.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</strong>
                <span className="crh-muted">·</span>
                <strong className={paperPosition.isProfit == null ? "crh-muted" : paperPosition.isProfit ? "crh-up" : "crh-dn"}>
                  {paperPosition.pnlPct == null ? "Waiting…" : (paperPosition.pnlPct >= 0 ? "+" : "") + paperPosition.pnlPct.toFixed(2) + "%"}
                </strong>
                {paperPosition.pnlAmount != null && (
                  <span className="crh-paper-position-amount">
                    ({paperPosition.pnlAmount >= 0 ? "+" : "−"}₹{Math.abs(paperPosition.pnlAmount).toLocaleString("en-IN", { maximumFractionDigits: 2 })})
                  </span>
                )}
              </span>
              <span className="crh-paper-position-meta">
                Entered {paperPosition.entryDate} · {paperPosition.holdingDays}d old
              </span>
              <Tooltip title="Delete paper trade">
                <Button
                  type="text"
                  size="small"
                  danger
                  className="crh-paper-position-delete"
                  aria-label={"Delete paper trade for " + paperPosition.symbol}
                  icon={<DeleteOutlined />}
                  onClick={onDeletePaperTrade}
                />
              </Tooltip>
            </div>
          )}
        </div>
      )}

    </header>
  );
}

function formatHeaderDate(date: string): string {
  return new Intl.DateTimeFormat("en-IN", {
    weekday: "short",
    day: "2-digit",
    month: "short",
    timeZone: "UTC",
  }).format(new Date(`${date}T00:00:00Z`)) + ` ${date.slice(0, 4)}`;
}

function formatCompactDate(date: string | null): string {
  if (!date) return "—";
  return new Intl.DateTimeFormat("en-IN", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    timeZone: "UTC",
  }).format(new Date(`${date}T00:00:00Z`));
}

interface WatchlistReviewNavProps {
  options: UniverseOption[];
  selectedWatchlist: string | null;
  instrument: InstrumentSearchResult | null;
  members: InstrumentSearchResult[];
  loading: boolean;
  error: string | null;
  onSelectWatchlist: (watchlist: string | null) => void;
  onNavigate: (direction: -1 | 1) => void;
}

function WatchlistReviewNav({
  options,
  selectedWatchlist,
  instrument,
  members,
  loading,
  error,
  onSelectWatchlist,
  onNavigate,
}: WatchlistReviewNavProps) {
  const currentIndex = instrument
    ? members.findIndex((member) => member.trading_symbol === instrument.trading_symbol)
    : -1;
  const canNavigate = selectedWatchlist != null && members.length > 0 && !loading;
  const position = currentIndex >= 0
    ? `${currentIndex + 1}/${members.length}`
    : selectedWatchlist ? "—" : "Independent";

  return (
    <div className="crh-review-nav" aria-label="Stock review navigation">
      <Select
        size="small"
        aria-label="Review watchlist"
        value={selectedWatchlist ?? undefined}
        placeholder="Watchlist"
        allowClear
        loading={loading && options.length === 0}
        options={options.map((option) => ({
          value: option.value,
          label: `${option.label} (${option.count})`,
        }))}
        onChange={(value: string | undefined) => onSelectWatchlist(value ?? null)}
      />
      <Button
        size="small"
        aria-label="Previous watchlist stock"
        icon={<LeftOutlined />}
        disabled={!canNavigate}
        onClick={() => onNavigate(-1)}
      />
      <span className="crh-review-position" title={error ?? undefined}>{position}</span>
      <Button
        size="small"
        aria-label="Next watchlist stock"
        icon={<RightOutlined />}
        disabled={!canNavigate}
        onClick={() => onNavigate(1)}
      />
    </div>
  );
}

// ─── Close position bar ───────────────────────────────────────────────────────
// pct: 0 = closed at day low, 100 = closed at day high

function ClosePositionBar({ pct }: { pct: number | null }) {
  if (pct == null) {
    return <div className="crh-pos-bar crh-pos-bar-empty" />;
  }

  const clamped = Math.max(0, Math.min(100, pct));
  // tone: top 70%+ = demand present, bottom 30%- = supply present
  const dotClass = clamped >= 70 ? "crh-pos-dot crh-up"
    : clamped <= 30 ? "crh-pos-dot crh-dn"
    : "crh-pos-dot";

  return (
    <div className="crh-pos-bar" aria-label={`Close position ${clamped.toFixed(0)}% of day's range`}>
      <div className="crh-pos-track">
        <div className={dotClass} style={{ left: `${clamped}%` }} />
      </div>
    </div>
  );
}

function CompactFlowCol({ flow, currentPrice }: { flow: CompactThreeWeekFlow; currentPrice: number | null }) {
  const allPrices = flow.weeks.flatMap((week) => [week.low, week.high]);
  const minPrice = Math.min(...allPrices);
  const maxPrice = Math.max(...allPrices);
  const spread = maxPrice - minPrice;
  const floorGapPct = currentPrice != null && flow.floorHigh !== 0
    ? ((currentPrice - flow.floorHigh) / flow.floorHigh) * 100
    : null;

  return (
    <span className="crh-flow">
      {flow.weeks.map((week) => {
        const left = spread > 0 ? ((week.low - minPrice) / spread) * 100 : 0;
        const width = spread > 0 ? ((week.high - week.low) / spread) * 100 : 0;
        return (
          <span className="crh-flow-row" key={week.weekStart} title={`${week.label}: low ${formatPrice(week.low)}, high ${formatPrice(week.high)}`}>
            <span className="crh-flow-label">{week.label}</span>
            <span className="crh-flow-low-date">{formatFlowLowDate(week.lowDate)}</span>
            <span className="crh-flow-track">
              <span className="crh-flow-range" style={{ left: `${left}%`, width: `${Math.max(width, 1)}%` }} />
              <span className="crh-flow-low-dot" style={{ left: `${left}%` }} />
            </span>
            <span className="crh-muted">{formatPrice(week.low)}</span>
          </span>
        );
      })}
      <span className="crh-flow-summary">
        <span className={flow.floorAligned ? "crh-up" : "crh-muted"}>
          {flow.floorAligned ? "Floor" : "Low band"} {formatPrice(flow.floorLow)}–{formatPrice(flow.floorHigh)}
        </span>
        <span className="crh-muted">·</span>
        {floorGapPct != null && <>
          <span className="crh-floor-gap" title="Current price distance from the upper edge of the low band">Gap {fmtPct(floorGapPct)}</span>
          <span className="crh-muted">·</span>
        </>}
        <strong title={`${flow.lowStructure} + ${flow.highStructure}`}>
          {formatFlowStructure(flow.lowStructure)}+{formatFlowStructure(flow.highStructure)}
        </strong>
      </span>
    </span>
  );
}

function formatFlowStructure(structure: string): string {
  return structure === "MIXED" ? "MIX" : structure;
}

function formatFlowLowDate(date: string): string {
  return new Intl.DateTimeFormat("en-IN", {
    weekday: "short",
    day: "2-digit",
    month: "short",
    timeZone: "UTC",
  }).format(new Date(`${date}T00:00:00Z`));
}

// ─── Direction arrow helper ───────────────────────────────────────────────────

function DirArrow({ dir }: { dir: "UP" | "DOWN" | "FLAT" | null }) {
  if (!dir || dir === "FLAT") return <span className="crh-dir crh-dir-flat">→</span>;
  return dir === "UP"
    ? <span className="crh-dir crh-dir-up">↑</span>
    : <span className="crh-dir crh-dir-dn">↓</span>;
}

// ─── RSI14 range column ───────────────────────────────────────────────────────

function Rsi14RangeCol({ rsi }: { rsi: Rsi14Range | null }) {
  if (!rsi || rsi.current == null) {
    return <span className="crh-muted" style={{ fontSize: 11 }}>—</span>;
  }

  const range = (rsi.max_60d ?? 0) - (rsi.min_60d ?? 0);
  const posPct = range > 0 ? ((rsi.current - (rsi.min_60d ?? 0)) / range) * 100 : 50;
  const clamped = Math.max(0, Math.min(100, posPct));

  return (
    <span className="crh-rsi-col">
      <span className="crh-rsi-top">
        <span className="crh-rsi-current">{rsi.current.toFixed(1)}</span>
        <DirArrow dir={rsi.direction_3d} />
      </span>
      <span className="crh-rsi-range">
        <span className="crh-muted">{rsi.min_60d?.toFixed(0) ?? "—"}</span>
        <div className="crh-pos-track crh-rsi-track">
          <div className="crh-pos-dot" style={{ left: `${clamped}%` }} />
        </div>
        <span className="crh-muted">{rsi.max_60d?.toFixed(0) ?? "—"}</span>
      </span>
    </span>
  );
}

// ─── ROC-9 column ─────────────────────────────────────────────────────────────

function Roc9Col({ roc }: { roc: Roc9 | null }) {
  if (!roc || roc.current == null) {
    return <span className="crh-muted" style={{ fontSize: 11 }}>—</span>;
  }

  const tone = roc.current > 0 ? "crh-up" : roc.current < 0 ? "crh-dn" : "";

  return (
    <span className="crh-rsi-top">
      <span className={`crh-rsi-current ${tone}`}>{roc.current > 0 ? "+" : ""}{roc.current.toFixed(1)}%</span>
      <DirArrow dir={roc.direction_3d} />
    </span>
  );
}

const BREAKOUT_HORIZONS: Array<{
  label: string;
  dateKey: keyof FreshBreakoutDates;
  levelKey: keyof FreshBreakoutDates;
}> = [
  { label: "20D", dateKey: "breakout_20d", levelKey: "breakout_20d_level" },
  { label: "50D", dateKey: "breakout_50d", levelKey: "breakout_50d_level" },
  { label: "52D", dateKey: "breakout_52d", levelKey: "breakout_52d_level" },
  { label: "100D", dateKey: "breakout_100d", levelKey: "breakout_100d_level" },
];

function BreakoutDatesRow({ dates, currentPrice }: { dates: FreshBreakoutDates | null; currentPrice: number | null }) {
  return (
    <span className="crh-breakout-dates" data-testid="compact-breakout-dates">
      {BREAKOUT_HORIZONS.map(({ label, dateKey, levelKey }) => {
        const date = dates?.[dateKey] ?? null;
        const level = typeof dates?.[levelKey] === "number" ? dates[levelKey] as number : null;
        const distance = level != null && currentPrice != null && level !== 0
          ? ((currentPrice - level) / level) * 100
          : null;
        return (
          <span className="crh-breakout-item" key={dateKey} title={date ? `${label} fresh breakout on ${formatBreakoutDate(date)} at ${formatPrice(level)}; current price ${fmtPct(distance)} from level` : `${label} breakout unavailable`}>
            <span className="crh-breakout-horizon">{label}</span>
            <span className="crh-breakout-separator">·</span>
            <span className={date ? "crh-breakout-date" : "crh-muted"}>{formatBreakoutDate(date)}</span>
            {date && level != null && <>
              <span className="crh-breakout-separator">·</span>
              <span className="crh-muted">{formatPrice(level)}</span>
              <span className="crh-breakout-separator">·</span>
              <span className={pos(distance)}>{fmtPct(distance)}</span>
            </>}
          </span>
        );
      })}
    </span>
  );
}

function formatBreakoutDate(date: string | null): string {
  if (!date) return "—";
  return new Intl.DateTimeFormat("en-IN", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    timeZone: "UTC",
  }).format(new Date(`${date}T00:00:00Z`));
}
