import {
  AimOutlined,
  BarChartOutlined,
  CheckCircleFilled,
  EyeOutlined,
  FileSearchOutlined,
  HistoryOutlined,
  LineChartOutlined,
  MinusCircleOutlined,
  RadarChartOutlined,
  ReloadOutlined,
  RiseOutlined,
  ThunderboltFilled,
  VerticalAlignTopOutlined,
} from "@ant-design/icons";
import { Alert, Button, Card, Drawer, Empty, Modal, Select, Space, Spin, Table, Tag, Typography } from "antd";
import type { TableColumnsType } from "antd";
import { useEffect, useMemo, useState, type ReactNode } from "react";
import { useStockQuotes } from "../hooks/useStockQuotes";
import { RecentDailyEvidenceTable, type RecentDailyEvidenceRow } from "../components/RecentDailyEvidenceTable";
import type {
  AdaptiveBreakoutRawStep,
  AdaptiveBreakoutScanResponse,
  AdaptiveBreakoutScanRow,
  AdaptiveBreakoutStatus,
  StockQuoteSnapshot,
  UniverseOptionsResponse,
} from "../types";
import { getJson } from "../utils/api";
import { isIndianEquityMarketOpen } from "../utils/marketHours";
import type { ClosePositionBucket, PriceDirection } from "../utils/shortHorizonSelector";
import "./adaptiveBreakoutScanner.css";

const { Text, Title } = Typography;

type DisplayStatus = AdaptiveBreakoutStatus | "LIVE_CANDIDATE";
type StatusFilter = DisplayStatus | "ALL";

interface ConfirmationEvidence {
  closePositionPct: number | null;
  volumeVsTenDayAverage: number | null;
  distanceFromFiftyTwoWeekHighPct: number | null;
  sourceLabel: string;
}

const STATUS_ORDER: DisplayStatus[] = [
  "LIVE_CANDIDATE",
  "FRESH_BREAKOUT",
  "TESTING_CEILING",
  "STRONG_REBOUND",
  "BELOW_CEILING",
  "NO_CEILING",
  "BREAKOUT_CONTINUATION",
];

function formatPrice(value: number | null): string {
  return value == null
    ? "—"
    : `₹${value.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function buildCompactReviewUrl(symbol: string): string {
  return `${import.meta.env.BASE_URL}console/compact-stock-review?symbol=${encodeURIComponent(symbol)}`;
}

function buildKiteChartUrl(symbol: string, instrumentToken: number): string {
  return `https://kite.zerodha.com/chart/web/tvc/NSE/${encodeURIComponent(symbol)}/${instrumentToken}`;
}

function formatPercent(value: number | null, digits = 1): string {
  if (value == null) return "—";
  return `${value >= 0 ? "+" : ""}${value.toFixed(digits)}%`;
}

function formatMultiple(value: number | null): string {
  return value == null ? "—" : `${value.toFixed(2)}×`;
}

function formatInteger(value: number): string {
  return value.toLocaleString("en-IN");
}

function formatDate(value: string | null): string {
  if (!value) return "—";
  return new Intl.DateTimeFormat("en-IN", {
    day: "2-digit",
    month: "short",
    timeZone: "UTC",
  }).format(new Date(`${value}T00:00:00Z`));
}

function statusLabel(status: DisplayStatus): string {
  if (status === "LIVE_CANDIDATE") return "Live candidate";
  if (status === "FRESH_BREAKOUT") return "Fresh breakout";
  if (status === "TESTING_CEILING") return "At ceiling";
  if (status === "STRONG_REBOUND") return "Strong rebound";
  if (status === "BELOW_CEILING") return "Below ceiling";
  if (status === "BREAKOUT_CONTINUATION") return "Already broken";
  return "Building structure";
}

function statusIcon(status: DisplayStatus): ReactNode {
  if (status === "LIVE_CANDIDATE") return <ThunderboltFilled />;
  if (status === "FRESH_BREAKOUT") return <CheckCircleFilled />;
  if (status === "TESTING_CEILING") return <AimOutlined />;
  if (status === "STRONG_REBOUND") return <RiseOutlined />;
  if (status === "BELOW_CEILING") return <RiseOutlined />;
  if (status === "BREAKOUT_CONTINUATION") return <RadarChartOutlined />;
  return <MinusCircleOutlined />;
}

function displayStatus(
  row: AdaptiveBreakoutScanRow,
  quote: StockQuoteSnapshot | undefined,
  marketOpen: boolean,
): DisplayStatus {
  const ceiling = row.ceiling;
  if (ceiling == null) return row.status;
  if (
    ceiling.breakoutDate == null &&
    quote?.ltp != null &&
    row.latestClose <= ceiling.upperBoundary &&
    quote.ltp > ceiling.upperBoundary
  ) {
    if (marketOpen) return "LIVE_CANDIDATE";
    return canUseQuoteAsCompletedClose(row, quote) ? "FRESH_BREAKOUT" : row.status;
  }
  return row.status;
}

function currentPrice(row: AdaptiveBreakoutScanRow, quote: StockQuoteSnapshot | undefined): number {
  return quote?.ltp ?? row.latestClose;
}

function gapFromCeilingPct(row: AdaptiveBreakoutScanRow, quote: StockQuoteSnapshot | undefined): number | null {
  if (!row.ceiling) return null;
  const price = currentPrice(row, quote);
  return price > 0 ? ((price - row.ceiling.upperBoundary) / row.ceiling.upperBoundary) * 100 : null;
}

function latestFloor(row: AdaptiveBreakoutScanRow): number | null {
  return row.rawSteps[row.rawSteps.length - 1]?.candidateFloor ?? null;
}

function quoteSessionDate(quote: StockQuoteSnapshot | undefined): string | null {
  if (!quote?.updated_at) return null;
  const value = new Date(quote.updated_at);
  if (Number.isNaN(value.getTime())) return null;
  const parts = new Intl.DateTimeFormat("en-CA", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    timeZone: "Asia/Kolkata",
  }).formatToParts(value);
  const year = parts.find((part) => part.type === "year")?.value;
  const month = parts.find((part) => part.type === "month")?.value;
  const day = parts.find((part) => part.type === "day")?.value;
  return year && month && day ? `${year}-${month}-${day}` : null;
}

function canUseQuoteAsCompletedClose(
  row: AdaptiveBreakoutScanRow,
  quote: StockQuoteSnapshot | undefined,
): boolean {
  const quoteDate = quoteSessionDate(quote);
  return quote?.ltp != null && quoteDate != null && quoteDate >= row.latestDate;
}

function calculateClosePosition(close: number, high: number, low: number): number | null {
  const range = high - low;
  return range > 0 ? ((close - low) / range) * 100 : null;
}

function calculatePercentageFrom(reference: number | null, value: number): number | null {
  return reference != null && reference > 0 ? ((value - reference) / reference) * 100 : null;
}

function evidenceFromRawStep(
  row: AdaptiveBreakoutScanRow,
  targetDate: string,
  sourceLabel: string,
): ConfirmationEvidence | null {
  const targetIndex = row.rawSteps.findIndex((step) => step.date === targetDate);
  if (targetIndex < 0) return null;
  const target = row.rawSteps[targetIndex];
  const previousTen = row.rawSteps.slice(Math.max(0, targetIndex - 10), targetIndex);
  const averageVolume = previousTen.length === 10
    ? previousTen.reduce((total, step) => total + step.volume, 0) / previousTen.length
    : null;
  const fiftyTwoWeekHigh = Math.max(
    ...row.rawSteps.slice(Math.max(0, targetIndex - 251), targetIndex + 1).map((step) => step.high),
  );
  return {
    closePositionPct: calculateClosePosition(target.close, target.high, target.low),
    volumeVsTenDayAverage: averageVolume != null && averageVolume > 0 ? target.volume / averageVolume : null,
    distanceFromFiftyTwoWeekHighPct: calculatePercentageFrom(fiftyTwoWeekHigh, target.close),
    sourceLabel,
  };
}

function evidenceFromQuote(
  row: AdaptiveBreakoutScanRow,
  quote: StockQuoteSnapshot | undefined,
  sourceLabel: string,
): ConfirmationEvidence | null {
  if (quote?.ltp == null) return null;
  const quoteDate = quoteSessionDate(quote);
  const matchingIndex = quoteDate == null ? -1 : row.rawSteps.findIndex((step) => step.date === quoteDate);
  const previousTen = matchingIndex >= 0
    ? row.rawSteps.slice(Math.max(0, matchingIndex - 10), matchingIndex)
    : row.rawSteps.slice(-10);
  const averageVolume = previousTen.length === 10
    ? previousTen.reduce((total, step) => total + step.volume, 0) / previousTen.length
    : null;
  const historicalHigh = row.rawSteps.slice(-251).reduce(
    (highest, step) => Math.max(highest, step.high),
    row.fiftyTwoWeekHigh ?? 0,
  );
  const effectiveHigh = Math.max(historicalHigh, quote.day_high ?? quote.ltp);
  const quoteClosePosition = quote.day_high != null && quote.day_low != null
    ? calculateClosePosition(quote.ltp, quote.day_high, quote.day_low)
    : null;
  return {
    closePositionPct: quoteClosePosition ?? row.closePositionPct,
    volumeVsTenDayAverage: quote.volume != null && averageVolume != null && averageVolume > 0
      ? quote.volume / averageVolume
      : row.volumeVsTenDayAverage,
    distanceFromFiftyTwoWeekHighPct: calculatePercentageFrom(effectiveHigh, quote.ltp),
    sourceLabel,
  };
}

function confirmationEvidence(
  row: AdaptiveBreakoutScanRow,
  quote: StockQuoteSnapshot | undefined,
  status: DisplayStatus,
  marketOpen: boolean,
): ConfirmationEvidence {
  const breakoutDate = row.ceiling?.breakoutDate;
  if (breakoutDate && (status === "FRESH_BREAKOUT" || status === "BREAKOUT_CONTINUATION")) {
    if (row.breakoutEvidence?.date === breakoutDate) {
      return {
        closePositionPct: row.breakoutEvidence.closePositionPct,
        volumeVsTenDayAverage: row.breakoutEvidence.volumeVsTenDayAverage,
        distanceFromFiftyTwoWeekHighPct: row.breakoutEvidence.distanceFromFiftyTwoWeekHighPct,
        sourceLabel: `Breakout close · ${formatDate(breakoutDate)}`,
      };
    }
    const breakoutEvidence = evidenceFromRawStep(row, breakoutDate, `Breakout close · ${formatDate(breakoutDate)}`);
    if (breakoutEvidence) return breakoutEvidence;
    return {
      closePositionPct: null,
      volumeVsTenDayAverage: null,
      distanceFromFiftyTwoWeekHighPct: null,
      sourceLabel: `Breakout close · ${formatDate(breakoutDate)} · evidence unavailable`,
    };
  }
  if (status === "LIVE_CANDIDATE") {
    const liveEvidence = evidenceFromQuote(row, quote, "Live candle · unconfirmed");
    if (liveEvidence) return liveEvidence;
  }
  if (!marketOpen && canUseQuoteAsCompletedClose(row, quote)) {
    const closedEvidence = evidenceFromQuote(row, quote, `Market close · ${formatDate(quoteSessionDate(quote))}`);
    if (closedEvidence) return closedEvidence;
  }
  return {
    closePositionPct: row.closePositionPct,
    volumeVsTenDayAverage: row.volumeVsTenDayAverage,
    distanceFromFiftyTwoWeekHighPct: row.distanceFromFiftyTwoWeekHighPct,
    sourceLabel: `Latest close · ${formatDate(row.latestDate)}`,
  };
}

function recentDailyEvidence(row: AdaptiveBreakoutScanRow): RecentDailyEvidenceRow[] {
  const startIndex = Math.max(0, row.rawSteps.length - 20);
  return row.rawSteps
    .slice(startIndex)
    .map((step, index) => {
      const absoluteIndex = startIndex + index;
      const previousClose = row.rawSteps[absoluteIndex - 1]?.close ?? null;
      const previousTen = row.rawSteps.slice(Math.max(0, absoluteIndex - 10), absoluteIndex);
      const averageVolume = previousTen.length === 10
        ? previousTen.reduce((total, previousStep) => total + previousStep.volume, 0) / previousTen.length
        : null;
      const closePositionPct = calculateClosePosition(step.close, step.high, step.low);
      const closePositionBucket: ClosePositionBucket | null = closePositionPct == null
        ? null
        : closePositionPct >= 75
          ? "HIGH"
          : closePositionPct <= 25
            ? "LOW"
            : "MIDDLE";
      const direction: PriceDirection = step.close > step.open
        ? "UP"
        : step.close < step.open
          ? "DOWN"
          : "FLAT";
      return {
        key: step.date,
        date: step.date,
        open: step.open,
        high: step.high,
        low: step.low,
        close: step.close,
        volumeMultiple: averageVolume != null && averageVolume > 0 ? step.volume / averageVolume : null,
        deliveryPercentage: null,
        changePct: previousClose != null && previousClose > 0 ? ((step.close - previousClose) / previousClose) * 100 : null,
        closePositionPct,
        closePositionBucket,
        direction,
      };
    })
    .reverse();
}

function structureProgress(row: AdaptiveBreakoutScanRow, quote: StockQuoteSnapshot | undefined): number {
  const floor = latestFloor(row);
  const ceiling = row.ceiling?.upperBoundary;
  if (floor == null || ceiling == null || ceiling <= floor) return 0;
  return Math.max(0, Math.min(100, ((currentPrice(row, quote) - floor) / (ceiling - floor)) * 100));
}

function StatusBadge({ status }: { status: DisplayStatus }): ReactNode {
  return (
    <span className={`adaptive-breakout-status adaptive-breakout-status-${status.toLowerCase()}`}>
      {statusIcon(status)}
      <strong>{statusLabel(status)}</strong>
    </span>
  );
}

function StatusStrip({
  rows,
  quotesBySymbol,
  marketOpen,
  activeFilter,
  onFilter,
}: {
  rows: AdaptiveBreakoutScanRow[];
  quotesBySymbol: Record<string, StockQuoteSnapshot>;
  marketOpen: boolean;
  activeFilter: StatusFilter;
  onFilter: (filter: StatusFilter) => void;
}): ReactNode {
  const countByStatus = new Map<DisplayStatus, number>();
  rows.forEach((row) => {
    const status = displayStatus(row, quotesBySymbol[row.symbol], marketOpen);
    countByStatus.set(status, (countByStatus.get(status) ?? 0) + 1);
  });

  return (
    <div className="adaptive-breakout-status-strip" aria-label="Breakout status summary">
      <button aria-label={`All stocks ${rows.length}`} className={activeFilter === "ALL" ? "active" : ""} onClick={() => onFilter("ALL")} type="button">
        <span>All stocks</span>
        <strong>{rows.length}</strong>
      </button>
      {STATUS_ORDER.map((status) => {
        const count = countByStatus.get(status) ?? 0;
        return (
          <button
            aria-label={`${statusLabel(status)} ${count}`}
            className={`adaptive-breakout-summary-${status.toLowerCase()} ${activeFilter === status ? "active" : ""}`}
            key={status}
            onClick={() => onFilter(status)}
            type="button"
          >
            <span>{statusIcon(status)} {statusLabel(status)}</span>
            <strong>{count}</strong>
          </button>
        );
      })}
    </div>
  );
}

function PriceCell({
  row,
  quote,
  marketOpen,
}: {
  row: AdaptiveBreakoutScanRow;
  quote: StockQuoteSnapshot | undefined;
  marketOpen: boolean;
}): ReactNode {
  const quoteDate = quoteSessionDate(quote);
  const secondaryLabel = !marketOpen && canUseQuoteAsCompletedClose(row, quote)
    ? `Market close · ${formatDate(quoteDate)}`
    : `Last close ${formatPrice(row.latestClose)} · ${formatDate(row.latestDate)}`;
  return (
    <div className="adaptive-breakout-price-cell">
      <div>
        <strong>{formatPrice(quote?.ltp ?? row.latestClose)}</strong>
        {quote?.change_percent != null && (
          <span className={quote.change_percent >= 0 ? "positive" : "negative"}>{formatPercent(quote.change_percent)}</span>
        )}
      </div>
      <span>{secondaryLabel}</span>
    </div>
  );
}

function StructureCell({ row, quote }: { row: AdaptiveBreakoutScanRow; quote: StockQuoteSnapshot | undefined }): ReactNode {
  const floor = latestFloor(row);
  if (!row.ceiling) {
    const headline = row.status === "STRONG_REBOUND" ? "Strong rebound · 2 ATR+" : "Rebound in progress";
    const context = row.majorCeiling
      ? `Major overhead ${formatPrice(row.majorCeiling.upperBoundary)}`
      : "No rejected top yet";
    return (
      <div className="adaptive-breakout-no-ceiling">
        <span className="adaptive-breakout-rise-line"><i /><i /><i /><i /></span>
        <div><strong>{headline}</strong><span>{context}</span></div>
      </div>
    );
  }

  const progress = structureProgress(row, quote);
  return (
    <div className="adaptive-breakout-structure" aria-label={`Price is ${progress.toFixed(0)}% of the way from floor to ceiling`}>
      <div className="adaptive-breakout-structure-labels">
        <span>Floor {formatPrice(floor)}</span>
        <strong>Ceiling {formatPrice(row.ceiling.upperBoundary)}</strong>
      </div>
      <div className="adaptive-breakout-track">
        <span className="adaptive-breakout-track-fill" style={{ width: `${progress}%` }} />
        <span className="adaptive-breakout-price-marker" style={{ left: `${progress}%` }} />
      </div>
      <span className="adaptive-breakout-structure-age">
        {row.ceilingAgeSessions ?? "—"} sessions · formed {formatDate(row.ceiling.confirmedDate)}
        {row.majorCeiling ? ` · major ${formatPrice(row.majorCeiling.upperBoundary)}` : ""}
      </span>
    </div>
  );
}

function GapCell({ row, quote }: { row: AdaptiveBreakoutScanRow; quote: StockQuoteSnapshot | undefined }): ReactNode {
  const gap = gapFromCeilingPct(row, quote);
  if (gap == null) return <span className="adaptive-breakout-gap neutral">Waiting<br /><small>for ceiling</small></span>;
  if (gap > 0) return <span className="adaptive-breakout-gap cleared">{formatPercent(gap)}<br /><small>cleared</small></span>;
  return <span className={`adaptive-breakout-gap ${gap >= -2 ? "near" : "far"}`}>{Math.abs(gap).toFixed(1)}%<br /><small>to ceiling</small></span>;
}

function EvidenceCell({ evidence }: { evidence: ConfirmationEvidence }): ReactNode {
  const finishTone = evidence.closePositionPct != null && evidence.closePositionPct >= 75 ? "strong" : "neutral";
  const volumeTone = evidence.volumeVsTenDayAverage != null && evidence.volumeVsTenDayAverage >= 1.2 ? "active" : "neutral";
  const highTone = evidence.distanceFromFiftyTwoWeekHighPct != null && evidence.distanceFromFiftyTwoWeekHighPct >= -15 ? "near" : "neutral";
  return (
    <div className="adaptive-breakout-evidence-wrap" aria-label={`Confirmation evidence from ${evidence.sourceLabel}; does not decide breakout`}>
      <span className="adaptive-breakout-evidence-source">{evidence.sourceLabel}</span>
      <div className="adaptive-breakout-evidence">
        <span className={finishTone} title="Close position within that candle's low-high range">
          <VerticalAlignTopOutlined /><strong>{evidence.closePositionPct == null ? "—" : `${evidence.closePositionPct.toFixed(0)}%`}</strong><small>finish</small>
        </span>
        <span className={volumeTone} title="Volume compared with that candle's preceding 10-session average">
          <BarChartOutlined /><strong>{formatMultiple(evidence.volumeVsTenDayAverage)}</strong><small>volume</small>
        </span>
        <span className={highTone} title="Close distance from the 52-week high on that date">
          <RadarChartOutlined /><strong>{formatPercent(evidence.distanceFromFiftyTwoWeekHighPct, 0)}</strong><small>52W high</small>
        </span>
      </div>
    </div>
  );
}

function rawDecisionSummary(step: AdaptiveBreakoutRawStep): string {
  const ceiling = formatPrice(step.ceilingUpperBoundary);
  return {
    BUILDING_STRUCTURE: "No reliable floor-and-ceiling story exists yet.",
    FLOOR_CONFIRMED: `The close moved meaningfully away from the ${formatPrice(step.candidateFloor)} floor.`,
    CEILING_CANDIDATE: `${formatPrice(step.candidatePeak)} may be resistance; wait for price to test this area again.`,
    CEILING_CONFIRMED: `${ceiling} is now the confirmed line that a future close must beat.`,
    BELOW_CEILING: `The ${formatPrice(step.close)} close remains below the ${ceiling} line.`,
    CEILING_TEST: `The ${formatPrice(step.close)} close is testing the ${ceiling} resistance area.`,
    STRONG_REBOUND: "Price has risen strongly from the floor, but no resistance is confirmed yet.",
    FRESH_BREAKOUT: `The ${formatPrice(step.close)} close crossed ${ceiling} for the first time.`,
    BREAKOUT_CONTINUATION: `The ${ceiling} line was broken earlier; this is no longer the first breakout day.`,
  }[step.decision];
}

function RawReplayGuide(): ReactNode {
  return (
    <div className="adaptive-breakout-raw-guide" role="note" aria-label="How to read the raw decision replay">
      <div className="adaptive-breakout-raw-guide-intro">
        <strong>Read the story from the bottom ↑</strong>
        <span>Each colored row answers what changed that day. Start with Decision, then compare Close with Ceiling.</span>
      </div>
      <div className="adaptive-breakout-raw-flow" aria-label="Normal breakout sequence">
        <span className="floor">Floor found</span><b>→</b>
        <span className="candidate">Possible ceiling</span><b>→</b>
        <span className="confirmed">Ceiling confirmed</span><b>→</b>
        <span className="testing">Testing</span><b>→</b>
        <span className="breakout">Fresh breakout</span>
      </div>
      <div className="adaptive-breakout-raw-keys">
        <span><strong>Ceiling</strong> = line to beat</span>
        <span><strong>Major</strong> = later obstacle only</span>
        <span><strong>ATR</strong> = the stock's movement ruler</span>
      </div>
    </div>
  );
}

function RawDecisionTable({ steps }: { steps: AdaptiveBreakoutRawStep[] }): ReactNode {
  const columns: TableColumnsType<AdaptiveBreakoutRawStep> = [
    { title: "Date", dataIndex: "date", key: "date", width: 75, render: formatDate },
    { title: "O", dataIndex: "open", key: "open", width: 68, render: formatPrice },
    { title: "H", dataIndex: "high", key: "high", width: 68, render: formatPrice },
    { title: "L", dataIndex: "low", key: "low", width: 68, render: formatPrice },
    { title: "C", dataIndex: "close", key: "close", width: 68, render: formatPrice },
    { title: "Volume", dataIndex: "volume", key: "volume", width: 82, render: formatInteger },
    { title: "ATR", dataIndex: "atr", key: "atr", width: 66, render: formatPrice },
    { title: <span className="adaptive-breakout-raw-header">Floor<small>journey start</small></span>, dataIndex: "candidateFloor", key: "candidateFloor", width: 78, render: formatPrice },
    { title: <span className="adaptive-breakout-raw-header">Peak<small>highest seen</small></span>, dataIndex: "candidatePeak", key: "candidatePeak", width: 78, render: formatPrice },
    { title: <span className="adaptive-breakout-raw-header">Ceiling<small>line to beat</small></span>, dataIndex: "ceilingUpperBoundary", key: "ceilingUpperBoundary", width: 84, render: formatPrice },
    { title: <span className="adaptive-breakout-raw-header">Major<small>later obstacle</small></span>, dataIndex: "majorCeilingUpperBoundary", key: "majorCeilingUpperBoundary", width: 84, render: formatPrice },
    {
      title: "Decision",
      dataIndex: "decision",
      key: "decision",
      width: 145,
      render: (value: AdaptiveBreakoutRawStep["decision"]) => (
        <Tag className={`adaptive-breakout-decision-tag adaptive-breakout-decision-${value.toLowerCase()}`}>
          {value.replace(/_/g, " ")}
        </Tag>
      ),
    },
    {
      title: "What this means",
      key: "meaning",
      width: 255,
      render: (_, step) => (
        <div className="adaptive-breakout-raw-meaning">
          <strong>{rawDecisionSummary(step)}</strong>
          <span>{step.explanation}</span>
        </div>
      ),
    },
  ];
  return (
    <div className="adaptive-breakout-raw-replay">
      <RawReplayGuide />
      <Table<AdaptiveBreakoutRawStep>
        className="adaptive-breakout-raw-table"
        size="small"
        rowKey="date"
        columns={columns}
        dataSource={[...steps].reverse()}
        pagination={{ pageSize: 25, hideOnSinglePage: true }}
        scroll={{ x: 1220, y: "calc(100vh - 345px)" }}
        rowClassName={(step) => `adaptive-breakout-raw-row-${step.decision.toLowerCase()}`}
      />
    </div>
  );
}

export function AdaptiveBreakoutScannerPage(): ReactNode {
  const [watchlists, setWatchlists] = useState<UniverseOptionsResponse["options"]>([]);
  const [selectedWatchlist, setSelectedWatchlist] = useState<string | null>(null);
  const [report, setReport] = useState<AdaptiveBreakoutScanResponse | null>(null);
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("ALL");
  const [auditRow, setAuditRow] = useState<AdaptiveBreakoutScanRow | null>(null);
  const [dailyDetailsRow, setDailyDetailsRow] = useState<AdaptiveBreakoutScanRow | null>(null);
  const [loadingOptions, setLoadingOptions] = useState(true);
  const [loadingScan, setLoadingScan] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const symbols = useMemo(() => report?.rows.map((row) => row.symbol) ?? [], [report?.rows]);
  const { quotesBySymbol, loading: loadingQuotes, error: quoteError } = useStockQuotes(symbols);
  const marketOpen = isIndianEquityMarketOpen();

  useEffect(() => {
    let active = true;
    void getJson<UniverseOptionsResponse>("/api/strategy/adaptive-breakout/watchlists")
      .then((response) => { if (active) setWatchlists(response.options); })
      .catch((requestError: unknown) => {
        if (active) setError(requestError instanceof Error ? requestError.message : "Unable to load watchlists.");
      })
      .finally(() => { if (active) setLoadingOptions(false); });
    return () => { active = false; };
  }, []);

  const runScan = (): void => {
    if (!selectedWatchlist) return;
    setLoadingScan(true);
    setError(null);
    setStatusFilter("ALL");
    void getJson<AdaptiveBreakoutScanResponse>(
      `/api/strategy/adaptive-breakout/scan?watchlist=${encodeURIComponent(selectedWatchlist)}`,
      { useCache: false },
    )
      .then(setReport)
      .catch((requestError: unknown) => {
        setError(requestError instanceof Error ? requestError.message : "Unable to run the breakout scan.");
      })
      .finally(() => setLoadingScan(false));
  };

  const displayedRows = useMemo(() => {
    const rows = report?.rows ?? [];
    return [...rows]
      .filter((row) => statusFilter === "ALL" || displayStatus(row, quotesBySymbol[row.symbol], marketOpen) === statusFilter)
      .sort((left, right) => {
        const statusDifference = STATUS_ORDER.indexOf(displayStatus(left, quotesBySymbol[left.symbol], marketOpen))
          - STATUS_ORDER.indexOf(displayStatus(right, quotesBySymbol[right.symbol], marketOpen));
        if (statusDifference !== 0) return statusDifference;
        return (gapFromCeilingPct(right, quotesBySymbol[right.symbol]) ?? -Infinity)
          - (gapFromCeilingPct(left, quotesBySymbol[left.symbol]) ?? -Infinity);
      });
  }, [marketOpen, quotesBySymbol, report?.rows, statusFilter]);

  const columns: TableColumnsType<AdaptiveBreakoutScanRow> = [
    {
      title: "Stock / signal",
      key: "stock",
      fixed: "left",
      width: 190,
      render: (_, row) => {
        const quote = quotesBySymbol[row.symbol];
        const status = displayStatus(row, quote, marketOpen);
        return (
          <div className="adaptive-breakout-stock">
            <div className="adaptive-breakout-stock-actions">
              <a
                aria-label={`Open ${row.symbol} in Kite`}
                className="adaptive-breakout-kite-link"
                href={buildKiteChartUrl(row.symbol, row.instrumentToken)}
                target="_blank"
                rel="noopener noreferrer"
                title={`Open ${row.symbol} chart in Kite`}
              >
                <strong>{row.symbol}</strong><LineChartOutlined />
              </a>
              <a
                aria-label={`Open ${row.symbol} stock detail`}
                className="adaptive-breakout-detail-link"
                href={buildCompactReviewUrl(row.symbol)}
                target="_blank"
                rel="noopener noreferrer"
                title={`Open ${row.symbol} Compact Stock Review`}
              >
                <FileSearchOutlined /> Stock detail
              </a>
              <button
                aria-label={`Open ${row.symbol} recent daily details`}
                className="adaptive-breakout-daily-link"
                onClick={() => setDailyDetailsRow(row)}
                title={`Open ${row.symbol} recent 20-session table`}
                type="button"
              >
                <HistoryOutlined /> 20D
              </button>
            </div>
            <span>{row.companyName}</span>
            <StatusBadge status={status} />
          </div>
        );
      },
    },
    { title: "Price now", key: "price", width: 145, render: (_, row) => <PriceCell row={row} quote={quotesBySymbol[row.symbol]} marketOpen={marketOpen} /> },
    { title: "Floor → ceiling", key: "structure", width: 270, render: (_, row) => <StructureCell row={row} quote={quotesBySymbol[row.symbol]} /> },
    { title: "Breakout gap", key: "gap", width: 110, render: (_, row) => <GapCell row={row} quote={quotesBySymbol[row.symbol]} /> },
    {
      title: "Confirmation snapshot",
      key: "evidence",
      width: 245,
      render: (_, row) => {
        const quote = quotesBySymbol[row.symbol];
        const status = displayStatus(row, quote, marketOpen);
        return <EvidenceCell evidence={confirmationEvidence(row, quote, status, marketOpen)} />;
      },
    },
    { title: "", key: "audit", width: 62, render: (_, row) => <Button aria-label={`Audit ${row.symbol}`} icon={<EyeOutlined />} onClick={() => setAuditRow(row)} size="small" type="text" /> },
  ];

  return (
    <div className="adaptive-breakout-page">
      <Card className="adaptive-breakout-control-card">
        <div className="adaptive-breakout-header">
          <div>
            <Space size={7} align="center"><RadarChartOutlined className="adaptive-breakout-title-icon" /><Title level={3}>Adaptive Breakout</Title></Space>
            <Text type="secondary">One glance: signal → distance to ceiling → confirmation evidence</Text>
          </div>
          <Space align="center" wrap>
            {report && <Text className="adaptive-breakout-session">Close {formatDate(report.latestCandleDate)} {loadingQuotes ? "· LTP refreshing" : "· LTP live"}</Text>}
            <Select
              aria-label="Watchlist"
              size="small"
              loading={loadingOptions}
              value={selectedWatchlist}
              onChange={setSelectedWatchlist}
              placeholder="Select watchlist"
              style={{ width: 240 }}
              options={watchlists.map((watchlist) => ({ value: watchlist.value, label: `${watchlist.label} (${watchlist.count})` }))}
            />
            <Button type="primary" size="small" icon={<ReloadOutlined />} disabled={!selectedWatchlist} loading={loadingScan} onClick={runScan}>Run scan</Button>
          </Space>
        </div>
      </Card>

      {error && <Alert type="error" showIcon message={error} />}
      {quoteError && <Alert type="warning" showIcon message={`LTP unavailable: ${quoteError}`} />}
      {loadingScan && <div className="adaptive-breakout-loading"><Spin /><Text type="secondary">Reading price structure…</Text></div>}
      {!report && !loadingScan && !loadingOptions && <Empty description="Select a watchlist and run the latest scan." />}
      {report && !loadingScan && (
        <Card className="adaptive-breakout-results-card">
          <StatusStrip rows={report.rows} quotesBySymbol={quotesBySymbol} marketOpen={marketOpen} activeFilter={statusFilter} onFilter={setStatusFilter} />
          <div className="adaptive-breakout-legend">
            <span><i className="finish" /> Close near high</span>
            <span><i className="volume" /> Volume vs 10D</span>
            <span><i className="high" /> Distance from 52W high</span>
            <em>Evidence only · completed close confirms breakout</em>
          </div>
          <Table<AdaptiveBreakoutScanRow>
            data-testid="adaptive-breakout-table"
            size="small"
            rowKey="symbol"
            columns={columns}
            dataSource={displayedRows}
            pagination={false}
            scroll={{ x: 1020, y: "calc(100vh - 345px)" }}
            sticky
            rowClassName={(row) => `adaptive-breakout-row-${displayStatus(row, quotesBySymbol[row.symbol], marketOpen).toLowerCase()}`}
            locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No stocks in this state." /> }}
          />
        </Card>
      )}

      <Drawer
        className="adaptive-breakout-audit-drawer"
        rootClassName="adaptive-breakout-audit-drawer-root"
        destroyOnHidden
        mask={false}
        open={auditRow != null}
        onClose={() => setAuditRow(null)}
        size="min(1580px, calc(100vw - 24px))"
        title={auditRow ? <Space><EyeOutlined /><strong>{auditRow.symbol}</strong><Text type="secondary">raw decision replay · newest first</Text></Space> : null}
      >
        {auditRow && <RawDecisionTable steps={auditRow.rawSteps} />}
      </Drawer>

      <Modal
        title={dailyDetailsRow ? `${dailyDetailsRow.symbol} · Recent 20D details` : "Recent 20D details"}
        open={dailyDetailsRow != null}
        mask={false}
        onCancel={() => setDailyDetailsRow(null)}
        footer={null}
        width="min(1280px, calc(100vw - 32px))"
      >
        {dailyDetailsRow && <RecentDailyEvidenceTable days={recentDailyEvidence(dailyDetailsRow)} />}
      </Modal>
    </div>
  );
}
