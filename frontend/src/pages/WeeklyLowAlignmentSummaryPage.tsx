import { Alert, Button, Card, Empty, Select, Space, Spin, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useMemo, useState, type ReactNode } from "react";
import type { UniverseOptionsResponse, WeeklyPriceWatchlistScannerResponse } from "../types";
import { getJson } from "../utils/api";
import { findCurrentWeekLowAlignment, type CurrentWeekLowAlignment, type WeeklyPriceDay } from "../utils/threeWeekStockReview";

const { Text, Title } = Typography;
const WEEKLY_LOW_ALIGNMENT_MAX_DIFFERENCE_PCT = 1;

interface WeeklyLowAlignmentSummaryPageProps {
  onOpenStockReview: (symbol: string) => void;
}

interface AlignmentCandidate {
  key: string;
  symbol: string;
  companyName: string;
  alignment: CurrentWeekLowAlignment;
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

function formatSignedPercentage(value: number): string {
  return `${value >= 0 ? "+" : ""}${value.toFixed(2)}%`;
}

function buildStockReviewUrl(symbol: string): string {
  return `${import.meta.env.BASE_URL}console/three-week-stock-review?symbol=${encodeURIComponent(symbol)}`;
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

function buildAlignmentCandidates(response: WeeklyPriceWatchlistScannerResponse | null): AlignmentCandidate[] {
  if (!response) return [];

  return response.rows
    .flatMap((row) => {
      const alignment = findCurrentWeekLowAlignment(toDayDetails(row.days), WEEKLY_LOW_ALIGNMENT_MAX_DIFFERENCE_PCT);
      return alignment ? [{ key: row.symbol, symbol: row.symbol, companyName: row.companyName, alignment }] : [];
    })
    .sort((left, right) => left.alignment.currentWeekDifferencePct - right.alignment.currentWeekDifferencePct
      || left.symbol.localeCompare(right.symbol));
}

function renderLowCell(price: number, date: string, prefix: string): ReactNode {
  return (
    <Space orientation="vertical" size={0}>
      <Text strong>{formatPrice(price)}</Text>
      <Text type="secondary">{prefix} {formatDateWithDay(date)}</Text>
    </Space>
  );
}

function renderWeekComparison(value: number, includeAlignmentTag: boolean = false): ReactNode {
  const color = value > 0 ? "#389e0d" : value < 0 ? "#cf1322" : undefined;
  return (
    <Space orientation="vertical" size={0}>
      {includeAlignmentTag && <Tag color="gold" style={{ width: "fit-content", marginInlineEnd: 0 }}>Within 1%</Tag>}
      <Text style={{ color, fontWeight: 600 }}>{formatSignedPercentage(value)}</Text>
    </Space>
  );
}

export function WeeklyLowAlignmentSummaryPage({ onOpenStockReview }: WeeklyLowAlignmentSummaryPageProps) {
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

  const candidates = useMemo(() => buildAlignmentCandidates(data), [data]);
  const columns = useMemo<ColumnsType<AlignmentCandidate>>(() => [
    {
      title: "Stock",
      key: "stock",
      width: 180,
      render: (_, row) => (
        <Space orientation="vertical" size={0}>
          <Typography.Link
            href={buildStockReviewUrl(row.symbol)}
            target="_blank"
            rel="noopener noreferrer"
            aria-label={`Open ${row.symbol} in Three-Week Stock Review`}
            onClick={(event) => {
              event.preventDefault();
              onOpenStockReview(row.symbol);
            }}
          >
            <Text strong>{row.symbol}</Text>
          </Typography.Link>
          <Text type="secondary" style={{ fontSize: 11 }}>{row.companyName}</Text>
        </Space>
      ),
    },
    {
      title: "Current week low",
      key: "currentWeekLow",
      width: 150,
      render: (_, row) => renderLowCell(row.alignment.currentWeekLow, row.alignment.currentWeekLowDate, "Low on"),
    },
    {
      title: "W-1 low",
      key: "previousWeekLow",
      width: 155,
      render: (_, row) => renderLowCell(row.alignment.previousWeekLow, row.alignment.previousWeekLowDate, "Low on"),
    },
    {
      title: "W-2 low",
      key: "earlierWeekLow",
      width: 175,
      render: (_, row) => renderLowCell(row.alignment.earlierWeekLow, row.alignment.earlierWeekLowDate, "Low on"),
    },
    {
      title: "Current vs last week",
      key: "currentWeekDifferencePct",
      width: 155,
      render: (_, row) => renderWeekComparison(row.alignment.currentVsPreviousWeekPct, true),
    },
    {
      title: "Last week vs last-to-last week",
      key: "previousVsEarlierWeekPct",
      width: 205,
      render: (_, row) => renderWeekComparison(row.alignment.previousVsEarlierWeekPct),
    },
    {
      title: "Review",
      key: "review",
      width: 110,
      render: (_, row) => <Button size="small" onClick={() => onOpenStockReview(row.symbol)}>Open review</Button>,
    },
  ], [onOpenStockReview]);

  return (
    <div style={{ padding: "24px 24px 160px" }}>
      <Space orientation="vertical" size={16} style={{ width: "100%" }}>
        <Card>
          <Space orientation="vertical" size={8} style={{ width: "100%" }}>
            <Title level={3} style={{ margin: 0 }}>Weekly Low Alignment Summary</Title>
            <Text type="secondary">
              Daily floor watch: find stocks where the current week is within 1% of last week&apos;s low, with W-2 shown for the original three-week context.
            </Text>
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
              {data && <Tag color="gold">{candidates.length} floor candidate{candidates.length === 1 ? "" : "s"}</Tag>}
            </Space>
            {data && <Text type="secondary" style={{ fontSize: 12 }}>
              Each low includes its weekday and date. Current-week alignment remains the only automatic filter; manually review whether the current week has enough sessions.
            </Text>}
          </Space>
        </Card>

        <Alert
          type="info"
          showIcon
          message="Use this as a discovery queue"
          description="A 1% low alignment is a repeatable price-floor observation, not a guaranteed buy or a no-downside claim. Open the stock review to check the full weekly and daily evidence before acting."
        />
        {error && <Alert type="error" message={error} showIcon />}
        {!selectedWatchlist && !loadingOptions && <Empty description="Select a watchlist to find aligned weekly lows." />}
        {loadingScan && <Spin />}
        {data && !loadingScan && candidates.length === 0 && (
          <Empty description="No current-week low is within 1% of the previous week&apos;s low." />
        )}
        {data && !loadingScan && candidates.length > 0 && (
          <Card title="Floor candidates">
            <Table<AlignmentCandidate>
              data-testid="weekly-low-alignment-summary-table"
              rowKey="key"
              size="small"
              pagination={false}
              scroll={{ x: true }}
              columns={columns}
              dataSource={candidates}
            />
          </Card>
        )}
      </Space>
    </div>
  );
}
