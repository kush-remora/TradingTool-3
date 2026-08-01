import { Alert, Card, Empty, Select, Space, Spin, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useMemo, useState } from "react";
import type { UniverseOptionsResponse, WeeklyPriceWatchlistScannerResponse } from "../types";
import { buildBaseConsolidationObservations, type BaseConsolidationObservation } from "../utils/baseConsolidation";
import { getJson } from "../utils/api";

const { Link, Text, Title } = Typography;
const TOP_STOCK_COUNT = 10;

interface WeeklyPriceWatchlistScannerPageProps {
  onOpenStockReview: (symbol: string) => void;
}

interface BaseConsolidationTableRow extends BaseConsolidationObservation {
  symbol: string;
  isStrongestRow: boolean;
}

interface BaseConsolidationCard {
  symbol: string;
  companyName: string;
  instrumentToken: number;
  observations: BaseConsolidationObservation[];
  strongestHitCount: number | null;
  isFocusStock: boolean;
}

function formatPrice(value: number): string {
  return `₹${value.toLocaleString("en-IN", { maximumFractionDigits: 2, minimumFractionDigits: 2 })}`;
}

function buildKiteChartUrl(symbol: string, instrumentToken: number): string {
  return `https://kite.zerodha.com/chart/web/tvc/NSE/${encodeURIComponent(symbol)}/${instrumentToken}`;
}

function buildCards(response: WeeklyPriceWatchlistScannerResponse | null): BaseConsolidationCard[] {
  if (!response) return [];

  const cards = response.rows.map((row) => {
    const observations = buildBaseConsolidationObservations(row.days);
    const hitCounts = observations
      .map((observation) => observation.hitCount)
      .filter((hitCount): hitCount is number => hitCount != null);

    return {
      symbol: row.symbol,
      companyName: row.companyName,
      instrumentToken: row.instrumentToken,
      observations,
      strongestHitCount: hitCounts.length > 0 ? Math.max(...hitCounts) : null,
      isFocusStock: false,
    };
  });

  const sortedCards = [...cards]
    .sort((left, right) => {
      const leftHits = left.strongestHitCount ?? -1;
      const rightHits = right.strongestHitCount ?? -1;
      return rightHits - leftHits || left.symbol.localeCompare(right.symbol);
    });
  let focusStockCount = 0;
  return sortedCards.map((card) => {
    const isFocusStock = card.strongestHitCount != null && focusStockCount < TOP_STOCK_COUNT;
    if (isFocusStock) focusStockCount += 1;
    return { ...card, isFocusStock };
  });
}

export function WeeklyPriceWatchlistScannerPage({ onOpenStockReview }: WeeklyPriceWatchlistScannerPageProps) {
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

  const cards = useMemo(() => buildCards(data), [data]);
  const columns = useMemo<ColumnsType<BaseConsolidationTableRow>>(() => [
    {
      title: "Stock",
      dataIndex: "symbol",
      key: "symbol",
      width: 120,
      render: (symbol: string) => (
        <Link
          href="#"
          onClick={(event) => {
            event.preventDefault();
            onOpenStockReview(symbol);
          }}
        >
          {symbol}
        </Link>
      ),
    },
    { title: "Reference date", dataIndex: "date", key: "date", width: 130 },
    { title: "Reference low", dataIndex: "low", key: "low", width: 130, render: formatPrice },
    {
      title: "Hits in previous 20 sessions",
      dataIndex: "hitCount",
      key: "hitCount",
      width: 190,
      render: (hitCount: number | null) => hitCount ?? "—",
    },
  ], [onOpenStockReview]);

  return (
    <div style={{ padding: "24px 24px 160px" }}>
      <Space orientation="vertical" size={16} style={{ width: "100%" }}>
        <Card>
          <Space orientation="vertical" size={8} style={{ width: "100%" }}>
            <Title level={3} style={{ margin: 0 }}>Base Consolidation Low-Hit Scanner</Title>
            <Text type="secondary">
              Raw validation view: each stock shows its latest 10 daily lows and the number of hits in the previous 20 sessions within ±1%.
            </Text>
            <Text type="secondary" style={{ fontSize: 12 }}>
              Yellow rows are the strongest repeated lows for that stock. Yellow stock cards are the top 10 by strongest hit count.
            </Text>
            <Select
              aria-label="Watchlist"
              loading={loadingOptions}
              value={selectedWatchlist}
              onChange={setSelectedWatchlist}
              placeholder="Select a watchlist"
              style={{ width: 360, maxWidth: "100%" }}
              options={options.map((option) => ({ value: option.value, label: `${option.label} (${option.count})` }))}
            />
          </Space>
        </Card>

        {error && <Alert type="error" message={error} showIcon />}
        {!selectedWatchlist && !loadingOptions && <Empty description="Select a watchlist to inspect repeated daily lows." />}
        {loadingScan && <Spin />}
        {data && !loadingScan && cards.length === 0 && <Empty description="No stocks are available in this watchlist." />}
        {cards.map((card) => {
          const strongestHitCount = card.strongestHitCount;
          const tableRows: BaseConsolidationTableRow[] = card.observations.map((observation) => ({
            ...observation,
            symbol: card.symbol,
            isStrongestRow: strongestHitCount != null && observation.hitCount === strongestHitCount,
          }));

          return (
            <Card
              key={card.symbol}
              size="small"
              data-testid={`base-stock-card-${card.symbol}`}
              className={card.isFocusStock ? "base-consolidation-focus-stock" : undefined}
              title={(
                <Space size={8}>
                  <a
                    aria-label={`Open ${card.symbol} in Kite`}
                    href={buildKiteChartUrl(card.symbol, card.instrumentToken)}
                    target="_blank"
                    rel="noreferrer"
                  >
                    <Text strong>{card.symbol}</Text>
                    <Text type="secondary"> · {card.companyName}</Text>
                  </a>
                </Space>
              )}
            >
              {tableRows.length === 0 ? (
                <Text type="secondary">No recent daily history.</Text>
              ) : (
                <Table<BaseConsolidationTableRow>
                  size="small"
                  pagination={false}
                  scroll={{ x: true }}
                  columns={columns}
                  dataSource={tableRows}
                  rowClassName={(row) => row.isStrongestRow ? "base-consolidation-focus-row" : ""}
                />
              )}
            </Card>
          );
        })}
      </Space>
    </div>
  );
}
