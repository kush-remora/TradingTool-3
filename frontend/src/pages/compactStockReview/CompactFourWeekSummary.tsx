import { Alert, Button, Empty, Select, Space, Spin, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useMemo, useState, type ReactNode } from "react";
import type {
  UniverseOption,
  WeeklyPriceWatchlistRow,
  WeeklyPriceWatchlistScannerResponse,
} from "../../types";
import { getJson } from "../../utils/api";
import {
  findFiveSessionMaxMove,
  findLatestWeeklyLowAlignment,
  type FiveSessionMaxMove,
  type LatestWeeklyLowAlignment,
  type WeeklyPriceDay,
} from "../../utils/threeWeekStockReview";

const MAX_WEEKLY_LOW_DIFFERENCE_PCT = 1;
const FIVE_SESSION_WINDOW = 5;
const MAX_MOVE_FILTER_PCT = 5;

interface CompactFourWeekSummaryProps {
  watchlistOptions: UniverseOption[];
  watchlistOptionsLoading: boolean;
  watchlistOptionsError: string | null;
  showOnlyMaxMoveCandidates: boolean;
  onOpenStockReview: (symbol: string) => void;
}

interface FourWeekSummaryCandidate {
  symbol: string;
  companyName: string;
  alignment: LatestWeeklyLowAlignment;
  maxMove: FiveSessionMaxMove | null;
}

function formatPrice(value: number): string {
  return `₹${value.toLocaleString("en-IN", { maximumFractionDigits: 2, minimumFractionDigits: 2 })}`;
}

function formatDateWithDay(date: string): string {
  const value = new Date(`${date}T00:00:00Z`);
  const day = new Intl.DateTimeFormat("en-IN", { weekday: "short", timeZone: "UTC" }).format(value);
  const formattedDate = new Intl.DateTimeFormat("en-IN", { day: "2-digit", month: "short", timeZone: "UTC" }).format(value);
  return `${day}, ${formattedDate}`;
}

function toWeeklyPriceDays(days: WeeklyPriceWatchlistRow["days"]): WeeklyPriceDay[] {
  return days.map((day) => ({
    ...day,
    daily_change_pct: null,
    rsi14: null,
    vol_ratio: null,
  }));
}

function buildCandidates(
  responses: WeeklyPriceWatchlistScannerResponse[],
  showOnlyMaxMoveCandidates: boolean,
): FourWeekSummaryCandidate[] {
  const uniqueRows = new Map<string, WeeklyPriceWatchlistRow>();
  for (const response of responses) {
    for (const row of response.rows) {
      if (!uniqueRows.has(row.symbol)) uniqueRows.set(row.symbol, row);
    }
  }

  return [...uniqueRows.values()]
    .flatMap((row) => {
      const weeklyDays = toWeeklyPriceDays(row.days);
      const alignment = findLatestWeeklyLowAlignment(weeklyDays, MAX_WEEKLY_LOW_DIFFERENCE_PCT);
      if (!alignment) return [];

      const maxMove = findFiveSessionMaxMove(
        weeklyDays,
        alignment.previousWeekLowDate,
        alignment.previousWeekLow,
        FIVE_SESSION_WINDOW,
      );
      if (showOnlyMaxMoveCandidates && (maxMove == null || !maxMove.isComplete || maxMove.maxMovePct <= MAX_MOVE_FILTER_PCT)) {
        return [];
      }
      return [{ symbol: row.symbol, companyName: row.companyName, alignment, maxMove }];
    })
    .sort((left, right) => (showOnlyMaxMoveCandidates
      ? (right.maxMove?.maxMovePct ?? 0) - (left.maxMove?.maxMovePct ?? 0)
      : left.alignment.differencePct - right.alignment.differencePct)
      || left.symbol.localeCompare(right.symbol));
}

function buildReviewUrl(symbol: string): string {
  return `${import.meta.env.BASE_URL}console/compact-stock-review?symbol=${encodeURIComponent(symbol)}`;
}

function renderLowCell(price: number, date: string): ReactNode {
  return (
    <Space orientation="vertical" size={0}>
      <Typography.Text strong>{formatPrice(price)}</Typography.Text>
      <Typography.Text type="secondary">{formatDateWithDay(date)}</Typography.Text>
    </Space>
  );
}

export function CompactFourWeekSummary({
  watchlistOptions,
  watchlistOptionsLoading,
  watchlistOptionsError,
  showOnlyMaxMoveCandidates,
  onOpenStockReview,
}: CompactFourWeekSummaryProps) {
  const [selectedWatchlists, setSelectedWatchlists] = useState<string[]>([]);
  const [responses, setResponses] = useState<WeeklyPriceWatchlistScannerResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (watchlistOptionsLoading || selectedWatchlists.length === 0) {
      setResponses([]);
      setLoading(false);
      setError(null);
      return;
    }

    let current = true;
    setLoading(true);
    setError(null);

    void Promise.all(selectedWatchlists.map((watchlist) => getJson<WeeklyPriceWatchlistScannerResponse>(
      `/api/strategy/weekly-price-review/scan?watchlist=${encodeURIComponent(watchlist)}`,
      { useCache: false },
    )))
      .then((nextResponses) => {
        if (current) setResponses(nextResponses);
      })
      .catch((requestError: unknown) => {
        if (current) {
          setResponses([]);
          setError(requestError instanceof Error ? requestError.message : "Failed to scan watchlists");
        }
      })
      .finally(() => {
        if (current) setLoading(false);
      });

    return () => { current = false; };
  }, [selectedWatchlists, watchlistOptionsLoading]);

  const candidates = useMemo(
    () => buildCandidates(responses, showOnlyMaxMoveCandidates),
    [responses, showOnlyMaxMoveCandidates],
  );
  const selectedLabel = selectedWatchlists.length === 0
    ? "Select one or more watchlists"
    : `${selectedWatchlists.length} watchlist${selectedWatchlists.length === 1 ? "" : "s"} selected`;
  const columns = useMemo<ColumnsType<FourWeekSummaryCandidate>>(() => [
    {
      title: "Stock",
      key: "stock",
      width: 220,
      render: (_, row) => (
        <Space orientation="vertical" size={0}>
          <Typography.Link
            href={buildReviewUrl(row.symbol)}
            aria-label={`Open ${row.symbol} in compact review`}
            onClick={(event) => {
              event.preventDefault();
              onOpenStockReview(row.symbol);
            }}
          >
            <Typography.Text strong>{row.symbol}</Typography.Text>
          </Typography.Link>
          <Typography.Text type="secondary" style={{ fontSize: 11 }}>{row.companyName}</Typography.Text>
        </Space>
      ),
    },
    {
      title: "This week low",
      key: "currentWeekLow",
      width: 170,
      render: (_, row) => renderLowCell(row.alignment.currentWeekLow, row.alignment.currentWeekLowDate),
    },
    {
      title: "Last week low",
      key: "previousWeekLow",
      width: 170,
      render: (_, row) => renderLowCell(row.alignment.previousWeekLow, row.alignment.previousWeekLowDate),
    },
    {
      title: "Gap",
      key: "differencePct",
      width: 110,
      render: (_, row) => <Tag color="gold">{row.alignment.signedDifferencePct >= 0 ? "+" : ""}{row.alignment.signedDifferencePct.toFixed(2)}%</Tag>,
    },
    {
      title: "Max 5D move",
      key: "maxMove",
      width: 190,
      render: (_, row) => {
        if (!row.maxMove) return <Typography.Text type="secondary">—</Typography.Text>;
        return (
          <Space orientation="vertical" size={0}>
            <Typography.Text strong style={{ color: row.maxMove.maxMovePct > 0 ? "#16803b" : "#cf1322" }}>
              {row.maxMove.maxMovePct >= 0 ? "+" : ""}{row.maxMove.maxMovePct.toFixed(2)}%
            </Typography.Text>
            <Typography.Text type="secondary">
              {formatPrice(row.maxMove.highestHigh)} · {formatDateWithDay(row.maxMove.highestHighDate)} · {row.maxMove.observedSessions}/{FIVE_SESSION_WINDOW} sessions
            </Typography.Text>
          </Space>
        );
      },
    },
    {
      title: "Review",
      key: "review",
      width: 110,
      render: (_, row) => <Button size="small" onClick={() => onOpenStockReview(row.symbol)}>Open</Button>,
    },
  ], [onOpenStockReview]);

  return (
    <section className="compact-four-week-summary" data-testid="compact-four-week-summary">
      <div className="compact-four-week-summary-heading">
        <div>
          <Typography.Title level={4} style={{ margin: 0 }}>
            {showOnlyMaxMoveCandidates ? "5D max move above 5%" : "4W flow summary"}
          </Typography.Title>
          <Typography.Text type="secondary">
            {showOnlyMaxMoveCandidates
              ? "Aligned stocks whose next five completed sessions reached more than 5% above last week's low."
              : "Stocks where this week&apos;s low is within 1% of last week&apos;s low. Use the compact review to validate the full structure."}
          </Typography.Text>
        </div>
        <Tag color="gold">{candidates.length} candidate{candidates.length === 1 ? "" : "s"}</Tag>
      </div>

      <div className="compact-four-week-summary-controls">
        <Typography.Text strong>Watchlists</Typography.Text>
        <Select
          mode="multiple"
          aria-label="Summary watchlists"
          loading={watchlistOptionsLoading}
          value={selectedWatchlists}
          onChange={setSelectedWatchlists}
          placeholder="Select one or more watchlists"
          maxTagCount="responsive"
          options={watchlistOptions.map((option) => ({ value: option.value, label: `${option.label} (${option.count})` }))}
          style={{ minWidth: 360, maxWidth: "100%" }}
        />
        <Typography.Text type="secondary">{selectedLabel}</Typography.Text>
      </div>

      {watchlistOptionsError && <Alert type="error" showIcon message={watchlistOptionsError} />}
      {error && <Alert type="error" showIcon message={error} />}
      {watchlistOptionsLoading && <div className="compact-four-week-summary-state"><Spin /><span>Loading watchlists…</span></div>}
      {!watchlistOptionsLoading && !watchlistOptionsError && watchlistOptions.length === 0 && <Empty description="No watchlists are available." />}
      {!watchlistOptionsLoading && !watchlistOptionsError && watchlistOptions.length > 0 && selectedWatchlists.length === 0 && (
        <Empty description="Select one or more watchlists to start the scan." />
      )}
      {loading && <div className="compact-four-week-summary-state"><Spin /><span>Scanning selected watchlists…</span></div>}
      {!loading && !error && selectedWatchlists.length > 0 && candidates.length === 0 && (
        <Empty description={showOnlyMaxMoveCandidates
          ? "No aligned stocks exceeded 5% in the next five completed sessions."
          : "No stocks match the 1% current-week floor check."} />
      )}
      {!loading && candidates.length > 0 && (
        <Table<FourWeekSummaryCandidate>
          data-testid="compact-four-week-summary-table"
          rowKey="symbol"
          size="small"
          pagination={false}
          scroll={{ x: true }}
          columns={columns}
          dataSource={candidates}
        />
      )}
    </section>
  );
}
