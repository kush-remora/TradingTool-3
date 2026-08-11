import { Alert, Button, Card, Empty, Select, Space, Spin, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useMemo, useState } from "react";
import { useRsiOversoldScanner } from "../hooks/useRsiOversoldScanner";
import { useStockQuotes } from "../hooks/useStockQuotes";
import type { RsiOversoldRow, StockQuoteSnapshot, UniverseOptionsResponse } from "../types";
import { getJson } from "../utils/api";

const WATCHLISTS_PATH = "/api/strategy/rsi-oversold/watchlists";
const { Text, Title } = Typography;

interface DisplayRow extends RsiOversoldRow {
  currentLtp: number | null;
  currentVolume: number | null;
  changePct: number | null;
}

function formatPrice(value: number | null): string {
  return value == null ? "-" : `₹${value.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function formatVolume(value: number | null): string {
  return value == null ? "-" : value.toLocaleString("en-IN");
}

function formatPercent(value: number | null): string {
  return value == null ? "-" : `${value >= 0 ? "+" : ""}${value.toFixed(2)}%`;
}

function buildDisplayRows(rows: RsiOversoldRow[], quotesBySymbol: Record<string, StockQuoteSnapshot>): DisplayRow[] {
  return rows.map((row) => {
    const quote = quotesBySymbol[row.symbol.toUpperCase()];
    const currentLtp = quote?.ltp ?? row.latestClose;
    const currentVolume = quote?.volume ?? row.latestVolume;
    const changePct = row.signalPrice > 0 ? ((currentLtp - row.signalPrice) / row.signalPrice) * 100 : null;
    return { ...row, currentLtp, currentVolume, changePct };
  });
}

function buildColumns(): ColumnsType<DisplayRow> {
  return [
    { title: "Symbol", dataIndex: "symbol", key: "symbol", fixed: "left", width: 110 },
    { title: "Signal date", dataIndex: "signalDate", key: "signalDate", width: 115 },
    { title: "Signal RSI-14", dataIndex: "signalRsi", key: "signalRsi", width: 105, sorter: (left, right) => left.signalRsi - right.signalRsi },
    { title: "Signal price", dataIndex: "signalPrice", key: "signalPrice", width: 110, render: formatPrice },
    { title: "Signal volume", dataIndex: "signalVolume", key: "signalVolume", width: 115, render: formatVolume },
    { title: "200D RSI low", dataIndex: "baselineRsiLow", key: "baselineRsiLow", width: 105 },
    { title: "Current LTP", dataIndex: "currentLtp", key: "currentLtp", width: 115, render: formatPrice },
    { title: "Current volume", dataIndex: "currentVolume", key: "currentVolume", width: 125, render: formatVolume },
    { title: "% change", dataIndex: "changePct", key: "changePct", width: 100, render: (value: number | null) => <Tag color={value != null && value >= 0 ? "green" : "red"}>{formatPercent(value)}</Tag> },
    { title: "Watchlists", dataIndex: "watchlistKeys", key: "watchlistKeys", width: 220, render: (values: string[]) => values.join(", ") },
  ];
}

export function RsiOversoldPage() {
  const { data, loading, error, run } = useRsiOversoldScanner();
  const [watchlists, setWatchlists] = useState<UniverseOptionsResponse["options"]>([]);
  const [selectedWatchlists, setSelectedWatchlists] = useState<string[]>([]);
  const [loadingWatchlists, setLoadingWatchlists] = useState(true);
  const [watchlistError, setWatchlistError] = useState<string | null>(null);
  const quoteSymbols = useMemo(() => data?.rows.map((row) => row.symbol) ?? [], [data?.rows]);
  const { quotesBySymbol, loading: quotesLoading, error: quotesError } = useStockQuotes(quoteSymbols);
  const rows = useMemo(() => buildDisplayRows(data?.rows ?? [], quotesBySymbol), [data?.rows, quotesBySymbol]);
  const columns = useMemo(() => buildColumns(), []);

  useEffect(() => {
    let active = true;
    void getJson<UniverseOptionsResponse>(WATCHLISTS_PATH, { useCache: false })
      .then((response) => {
        if (!active) return;
        setWatchlists(response.options);
        setSelectedWatchlists(response.options[0] ? [response.options[0].value] : []);
      })
      .catch((cause) => {
        if (active) setWatchlistError(cause instanceof Error ? cause.message : "Failed to load watchlists.");
      })
      .finally(() => {
        if (active) setLoadingWatchlists(false);
      });

    return () => {
      active = false;
    };
  }, []);

  const handleRun = (): void => {
    if (selectedWatchlists.length > 0) void run({ indexKeys: selectedWatchlists });
  };

  return (
    <div style={{ padding: "24px 24px 160px" }}>
      <Space orientation="vertical" size={16} style={{ width: "100%" }}>
        <Card>
          <Space orientation="vertical" size={10} style={{ width: "100%" }}>
            <Title level={3} style={{ margin: 0 }}>RSI Low Scanner</Title>
            <Text type="secondary">
              Finds stocks where a day in the latest 20 sessions had RSI-14 + 1 equal to the minimum RSI-14 from the prior 200 sessions.
            </Text>
            {watchlistError && <Alert type="error" message={watchlistError} showIcon />}
            {error && <Alert type="error" message={error} showIcon />}
            <Space wrap>
              <Select
                aria-label="Watchlists"
                mode="multiple"
                showSearch
                loading={loadingWatchlists}
                value={selectedWatchlists}
                onChange={setSelectedWatchlists}
                placeholder="Select one or more watchlists"
                style={{ minWidth: 360, maxWidth: "100%" }}
                options={watchlists.map((watchlist) => ({ value: watchlist.value, label: `${watchlist.label} (${watchlist.count})` }))}
              />
              <Button type="primary" onClick={handleRun} disabled={selectedWatchlists.length === 0 || loading} loading={loading}>
                Run Scanner
              </Button>
            </Space>
          </Space>
        </Card>

        {data && (
          <Card>
            <Space wrap>
              <Tag color="blue">{data.resultCount} stocks</Tag>
              <Tag>{data.config.baselineSessions}D baseline · {data.config.signalWindowSessions}D scan</Tag>
              <Tag>RSI + {data.config.signalOffset}</Tag>
              <Tag>{data.config.asOfDate}</Tag>
              {quotesLoading && <Text type="secondary">Refreshing live quotes…</Text>}
            </Space>
            {data.insufficientDataSymbols.length > 0 && <Alert type="error" showIcon message={`${data.insufficientDataSymbols.length} stock(s) could not be evaluated because fewer than ${data.config.baselineSessions + data.config.signalWindowSessions} recent trading sessions were available.`} style={{ marginTop: 12 }} />}
            {quotesError && <Alert type="warning" showIcon message={`Live quote refresh failed: ${quotesError}`} style={{ marginTop: 12 }} />}
          </Card>
        )}

        <Card title="RSI low signals">
          {loading && !data ? <Spin /> : rows.length > 0 ? (
            <Table<DisplayRow> rowKey="symbol" columns={columns} dataSource={rows} pagination={{ pageSize: 50, showSizeChanger: false }} scroll={{ x: 1150 }} size="small" />
          ) : (
            <Empty description={data ? "No qualifying RSI signals were found." : "Select watchlists and run the scanner."} />
          )}
        </Card>
      </Space>
    </div>
  );
}
