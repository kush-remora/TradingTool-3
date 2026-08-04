import dayjs, { type Dayjs } from "dayjs";
import { DownloadOutlined } from "@ant-design/icons";
import { Alert, Button, Card, DatePicker, Empty, InputNumber, Select, Space, Spin, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useMemo, useState } from "react";
import { useDeliveryBreakoutScanner } from "../hooks/useDeliveryBreakoutScanner";
import { useStockQuotes } from "../hooks/useStockQuotes";
import { resolveMarketChangePercent } from "../components/liveMarketCell";
import type { DeliveryBreakoutDashboardRow } from "../types";
import { buildDeliveryBreakoutCsv } from "../utils/deliveryBreakoutCsv";

function formatNumber(value: number | null | undefined, fractionDigits: number = 2): string {
  if (value == null) return "-";
  return value.toLocaleString("en-IN", {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  });
}

function formatInteger(value: number | null | undefined): string {
  if (value == null) return "-";
  return value.toLocaleString("en-IN", { maximumFractionDigits: 0 });
}

function formatPercent(value: number | null | undefined): string {
  return value == null ? "-" : `${formatNumber(value)}%`;
}

function formatRatio(value: number | null | undefined): string {
  return value == null ? "-" : `${formatNumber(value)}x`;
}

function eventColor(eventType: string): string {
  if (eventType === "BOTH") return "green";
  if (eventType === "DELIVERY_ONLY") return "blue";
  return "orange";
}

function buildReviewUrl(symbol: string): string {
  return `${import.meta.env.BASE_URL}console/three-week-stock-review?symbol=${encodeURIComponent(symbol)}`;
}

function buildKiteChartUrl(symbol: string, instrumentToken: number): string {
  return `https://kite.zerodha.com/chart/web/tvc/NSE/${encodeURIComponent(symbol)}/${instrumentToken}`;
}

export function DeliveryBreakoutScannerPage() {
  const {
    watchlists,
    data,
    loadingWatchlists,
    loading,
    error,
    loadWatchlists,
    loadDashboard,
  } = useDeliveryBreakoutScanner();
  const [selectedWatchlist, setSelectedWatchlist] = useState<string | null>(null);
  const [selectedTradeDate, setSelectedTradeDate] = useState<string | null>(null);
  const [eventType, setEventType] = useState<string | null>(null);
  const [minVolumeRatio, setMinVolumeRatio] = useState<number | null>(null);
  const [minDeliveryRatio, setMinDeliveryRatio] = useState<number | null>(null);
  const quoteSymbols = useMemo(() => (data?.rows ?? []).map((row) => row.symbol), [data?.rows]);
  const { quotesBySymbol } = useStockQuotes(quoteSymbols);

  useEffect(() => {
    let active = true;
    void loadWatchlists()
      .then((result) => {
        if (active && result.options.length > 0) {
          setSelectedWatchlist(result.options[0].value);
        }
      })
      .catch(() => undefined);
    return () => {
      active = false;
    };
  }, [loadWatchlists]);

  useEffect(() => {
    if (!selectedWatchlist) return;
    void loadDashboard(selectedWatchlist, selectedTradeDate ?? undefined).catch(() => undefined);
  }, [loadDashboard, selectedTradeDate, selectedWatchlist]);

  const filteredRows = useMemo(() => {
    return (data?.rows ?? []).filter((row) => {
      if (eventType && row.event_type !== eventType) return false;
      if (minVolumeRatio !== null && (row.volume_ratio ?? 0) < minVolumeRatio) return false;
      if (minDeliveryRatio !== null && (row.delivery_ratio ?? 0) < minDeliveryRatio) return false;
      return true;
    });
  }, [data?.rows, eventType, minDeliveryRatio, minVolumeRatio]);

  const columns = useMemo<ColumnsType<DeliveryBreakoutDashboardRow>>(() => [
    {
      title: "Symbol",
      dataIndex: "symbol",
      key: "symbol",
      fixed: "left",
      sorter: (left, right) => left.symbol.localeCompare(right.symbol),
      render: (value: string, row: DeliveryBreakoutDashboardRow) => (
        <Space orientation="vertical" size={2}>
          <Typography.Text strong>
            <a
              aria-label={`Open ${value} in Kite`}
              href={buildKiteChartUrl(value, row.instrument_token)}
              target="_blank"
              rel="noopener noreferrer"
            >
              {value}
            </a>
          </Typography.Text>
          <Space size={6}>
            <a
              aria-label={`Open ${value} three-week review`}
              href={buildReviewUrl(value)}
              target="_blank"
              rel="noopener noreferrer"
            >
              Review
            </a>
          </Space>
        </Space>
      ),
    },
    {
      title: "Event",
      key: "event",
      render: (_value: unknown, row: DeliveryBreakoutDashboardRow) => (
        <div>
          <Tag color={eventColor(row.event_type)}>{row.event_type}</Tag>
          <div style={{ fontSize: 11, color: "gray" }}>{row.event_date}</div>
        </div>
      ),
      filters: [
        { text: "Both", value: "BOTH" },
        { text: "Delivery only", value: "DELIVERY_ONLY" },
        { text: "Volume only", value: "VOLUME_ONLY" },
      ],
      onFilter: (value, row) => row.event_type === String(value),
    },
    {
      title: "Price Context",
      key: "price",
      render: (_value: unknown, row: DeliveryBreakoutDashboardRow) => {
        const quote = quotesBySymbol[row.symbol.toUpperCase()];
        const price = quote?.ltp ?? row.close;
        const change = quote?.change_percent ?? row.close_pct_change;
        return (
          <div>
            <Typography.Text strong>₹{formatNumber(price)}</Typography.Text>
            <div style={{ color: change == null ? "inherit" : change >= 0 ? "green" : "red", fontSize: 12 }}>
              Event day: {formatPercent(change)}
            </div>
            <div style={{ fontSize: 11, color: "gray" }}>Prev close: ₹{formatNumber(row.prev_close)}</div>
          </div>
        );
      },
      sorter: (left, right) =>
        (resolveMarketChangePercent(left.symbol, quotesBySymbol[left.symbol.toUpperCase()], left.close_pct_change) ?? Number.NEGATIVE_INFINITY) -
        (resolveMarketChangePercent(right.symbol, quotesBySymbol[right.symbol.toUpperCase()], right.close_pct_change) ?? Number.NEGATIVE_INFINITY),
    },
    {
      title: "Volume Evidence",
      key: "volume",
      render: (_value: unknown, row: DeliveryBreakoutDashboardRow) => (
        <div>
          <Typography.Text strong>{formatInteger(row.volume)} ({formatRatio(row.volume_ratio)})</Typography.Text>
          <div style={{ fontSize: 11, color: "gray" }}>Prior 10D avg: {formatInteger(row.average_volume_10d)}</div>
        </div>
      ),
      sorter: (left, right) => (left.volume_ratio ?? -1) - (right.volume_ratio ?? -1),
    },
    {
      title: "Delivery Evidence",
      key: "delivery",
      render: (_value: unknown, row: DeliveryBreakoutDashboardRow) => (
        <div>
          <Typography.Text strong>{formatInteger(row.delivery_quantity)} ({formatRatio(row.delivery_ratio)})</Typography.Text>
          <div style={{ fontSize: 11, color: "gray" }}>
            Prior 10D avg: {formatInteger(row.average_delivery_quantity_10d)} · {formatPercent(row.delivery_percentage)} Del
          </div>
        </div>
      ),
      sorter: (left, right) => (left.delivery_ratio ?? -1) - (right.delivery_ratio ?? -1),
    },
    {
      title: "52W Distance",
      key: "fiftyTwoWeekDistance",
      render: (_value: unknown, row: DeliveryBreakoutDashboardRow) => {
        const price = quotesBySymbol[row.symbol.toUpperCase()]?.ltp ?? row.close;
        const highDistance = price != null && row.fifty_two_week_high ? ((price - row.fifty_two_week_high) / row.fifty_two_week_high) * 100 : null;
        const lowDistance = price != null && row.fifty_two_week_low ? ((price - row.fifty_two_week_low) / row.fifty_two_week_low) * 100 : null;
        return <div style={{ fontSize: 12 }}>High: {formatPercent(highDistance)}<br />Low: {formatPercent(lowDistance)}</div>;
      },
    },
  ], [quotesBySymbol]);

  const handleWatchlistChange = (value: string): void => {
    setSelectedWatchlist(value);
    setSelectedTradeDate(null);
  };

  const handleDateChange = (value: Dayjs | null): void => {
    setSelectedTradeDate(value?.format("YYYY-MM-DD") ?? null);
  };

  const resetFilters = (): void => {
    setEventType(null);
    setMinVolumeRatio(null);
    setMinDeliveryRatio(null);
  };

  const downloadCsv = (): void => {
    if (!data || data.rows.length === 0) return;

    const blob = new Blob(["\uFEFF", buildDeliveryBreakoutCsv(data.rows)], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `delivery_breakout_${data.meta.watchlist_key}_${data.meta.trade_date}.csv`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  };

  return (
    <div style={{ padding: 24 }}>
      <Space orientation="vertical" size={16} style={{ width: "100%" }}>
        <Card
          title="Delivery Breakout Validation"
          extra={(
            <Space>
              <Button icon={<DownloadOutlined />} disabled={!data || data.rows.length === 0} onClick={downloadCsv}>
                Download CSV
              </Button>
              <Button onClick={() => selectedWatchlist && void loadDashboard(selectedWatchlist, selectedTradeDate ?? undefined)} loading={loading}>
                Reload
              </Button>
            </Space>
          )}
        >
          <Space orientation="vertical" size={12} style={{ width: "100%" }}>
            <Typography.Text type="secondary">
              Select a watchlist to find unusual volume, unusual delivery, or both across the last 10 trading sessions.
            </Typography.Text>
            {error && <Alert type="error" message={error} showIcon />}
            <Space wrap>
              <Select
                aria-label="Watchlist"
                showSearch
                loading={loadingWatchlists}
                value={selectedWatchlist ?? undefined}
                onChange={handleWatchlistChange}
                placeholder="Select a watchlist"
                style={{ width: 320 }}
                options={watchlists.map((option) => ({ value: option.value, label: `${option.label} (${option.count})` }))}
              />
              <DatePicker
                aria-label="End date"
                value={selectedTradeDate ? dayjs(selectedTradeDate) : data ? dayjs(data.meta.trade_date) : null}
                onChange={handleDateChange}
                allowClear={false}
              />
              <Button
                type="primary"
                disabled={!selectedWatchlist || loading}
                loading={loading}
                onClick={() => selectedWatchlist && void loadDashboard(selectedWatchlist, selectedTradeDate ?? undefined)}
              >
                Run Scan
              </Button>
            </Space>
          </Space>
        </Card>

        {loadingWatchlists && !data ? <Spin /> : null}
        {data ? (
          <Card size="small">
            <Space wrap>
              <Tag color="blue">{data.meta.watchlist_key}</Tag>
              <Tag>{data.meta.window_start_date} → {data.meta.window_end_date}</Tag>
              <Tag>Scanned {formatInteger(data.meta.scanned_count)}</Tag>
              <Tag>Data available {formatInteger(data.meta.data_available_count)}</Tag>
              <Tag color="green">Both {formatInteger(data.meta.both_count)}</Tag>
              <Tag color="blue">Delivery only {formatInteger(data.meta.delivery_only_count)}</Tag>
              <Tag color="orange">Volume only {formatInteger(data.meta.volume_only_count)}</Tag>
              <Tag>No event {formatInteger(data.meta.no_event_count)}</Tag>
            </Space>
          </Card>
        ) : null}

        {data ? (
          <Card size="small" title={`Event days (${filteredRows.length} of ${data.meta.event_count})`}>
            <Space wrap style={{ marginBottom: 12 }}>
              <Select
                aria-label="Event type"
                allowClear
                placeholder="All event types"
                value={eventType}
                onChange={setEventType}
                options={[
                  { value: "BOTH", label: "Both" },
                  { value: "DELIVERY_ONLY", label: "Delivery only" },
                  { value: "VOLUME_ONLY", label: "Volume only" },
                ]}
                style={{ width: 160 }}
              />
              <InputNumber aria-label="Minimum volume ratio" placeholder="Min volume x" min={0} step={0.5} value={minVolumeRatio} onChange={setMinVolumeRatio} />
              <InputNumber aria-label="Minimum delivery ratio" placeholder="Min delivery x" min={0} step={0.5} value={minDeliveryRatio} onChange={setMinDeliveryRatio} />
              <Button size="small" onClick={resetFilters}>Reset filters</Button>
            </Space>
            {filteredRows.length > 0 ? (
              <Table<DeliveryBreakoutDashboardRow>
                rowKey={(row) => `${row.symbol}-${row.event_date}`}
                columns={columns}
                dataSource={filteredRows}
                pagination={{ pageSize: 50, showSizeChanger: true }}
                scroll={{ x: 1150 }}
                size="small"
              />
            ) : (
              <Empty description="No unusual volume or delivery events matched this window." />
            )}
          </Card>
        ) : !loading && !loadingWatchlists ? (
          <Empty description="Select a watchlist to scan the last 10 trading sessions." />
        ) : null}
      </Space>
    </div>
  );
}
