import dayjs, { type Dayjs } from "dayjs";
import { DownloadOutlined } from "@ant-design/icons";
import { Alert, Button, Card, DatePicker, Empty, InputNumber, Select, Space, Spin, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useMemo, useState, type ReactNode } from "react";
import { useDeliveryBreakoutScanner } from "../hooks/useDeliveryBreakoutScanner";
import { useStockQuotes } from "../hooks/useStockQuotes";
import type { DeliveryBreakoutDashboardMeta, DeliveryBreakoutDashboardRow } from "../types";
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

function formatPrice(value: number | null | undefined): string {
  return value == null ? "-" : `₹${formatNumber(value)}`;
}

function formatSignedPercent(value: number | null | undefined): string {
  if (value == null) return "-";
  return `${value > 0 ? "+" : ""}${formatNumber(value)}%`;
}

function formatRatio(value: number | null | undefined): string {
  return value == null ? "-" : `${formatNumber(value)}x`;
}

function calculatePercentDifference(value: number | null, reference: number | null): number | null {
  if (value == null || reference == null || reference === 0) return null;
  return ((value - reference) / reference) * 100;
}

function calculateLowToHighPercent(low: number | null, high: number | null): number | null {
  if (low == null || high == null || low <= 0) return null;
  return ((high - low) / low) * 100;
}

function calculateCloseNearHighPercent(low: number | null, high: number | null, close: number | null): number | null {
  if (low == null || high == null || close == null || high <= low) return null;
  return Math.max(0, Math.min(100, ((close - low) / (high - low)) * 100));
}

function signedMetricColor(value: number | null): string {
  if (value == null || value === 0) return "#8c8c8c";
  return value > 0 ? "#237804" : "#cf1322";
}

function closeNearHighColor(value: number | null): string {
  if (value == null) return "#8c8c8c";
  if (value >= 70) return "#237804";
  if (value <= 30) return "#cf1322";
  return "#ad6800";
}

function sortNullableNumbers(left: number | null, right: number | null): number {
  if (left == null) return right == null ? 0 : 1;
  if (right == null) return -1;
  return left - right;
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

function PriceContextCell({ row, ltp }: { row: DeliveryBreakoutDashboardRow; ltp: number | null }): ReactNode {
  const ltpVsEvent = calculatePercentDifference(ltp, row.close);
  const eventRange = calculateLowToHighPercent(row.event_low, row.event_high);
  const closeNearHigh = calculateCloseNearHighPercent(row.event_low, row.event_high, row.close);

  return (
    <div
      title="LTP is the latest traded price. Close→High is the event-day close position from low (0%) to high (100%)."
      style={{ minWidth: 215, fontSize: 12, lineHeight: 1.45 }}
    >
      <div>
        <span style={{ color: "#8c8c8c" }}>LTP </span>
        <strong>{formatPrice(ltp)}</strong>
        <span style={{ color: signedMetricColor(ltpVsEvent), marginLeft: 8 }}>
          {formatSignedPercent(ltpVsEvent)} vs event
        </span>
      </div>
      <div>
        <span style={{ color: "#8c8c8c" }}>Event </span>
        <strong>{formatPrice(row.close)}</strong>
        <span style={{ color: "#8c8c8c", marginLeft: 8 }}>Prev→Event </span>
        <span style={{ color: signedMetricColor(row.close_pct_change), fontWeight: 600 }}>
          {formatSignedPercent(row.close_pct_change)}
        </span>
      </div>
      <div style={{ color: "#595959" }}>
        Low→High <span style={{ fontWeight: 600 }}>{formatPercent(eventRange)}</span>
        <span style={{ color: "#8c8c8c", marginLeft: 8 }}>Close→High </span>
        <span style={{ color: closeNearHighColor(closeNearHigh), fontWeight: 600 }}>
          {formatPercent(closeNearHigh)}
        </span>
      </div>
    </div>
  );
}

export function DeliveryBreakoutScannerPage() {
  const {
    watchlists,
    data,
    multiData,
    loadingWatchlists,
    loading,
    error,
    loadWatchlists,
    loadDashboard,
    loadDashboards,
    clear,
  } = useDeliveryBreakoutScanner();
  const [selectedWatchlists, setSelectedWatchlists] = useState<string[]>([]);
  const [selectedTradeDate, setSelectedTradeDate] = useState<string | null>(null);
  const [eventType, setEventType] = useState<string | null>(null);
  const [minVolumeRatio, setMinVolumeRatio] = useState<number | null>(null);
  const [minDeliveryRatio, setMinDeliveryRatio] = useState<number | null>(null);
  const reports = useMemo(
    () => multiData?.dashboards ?? (data ? [data] : []),
    [data, multiData],
  );
  const tableRows = useMemo(() => reports.flatMap((report) => report.rows.map((row) => ({
    ...row,
    watchlist_key: report.meta.watchlist_key,
  }))), [reports]);
  const summaryMeta = useMemo<DeliveryBreakoutDashboardMeta | null>(() => {
    if (reports.length === 0) return null;
    const first = reports[0].meta;
    return {
      watchlist_key: reports.map((report) => report.meta.watchlist_key).join(", "),
      trade_date: reports.map((report) => report.meta.trade_date).sort().at(-1) ?? first.trade_date,
      window_start_date: reports.map((report) => report.meta.window_start_date).sort().at(0) ?? first.window_start_date,
      window_end_date: reports.map((report) => report.meta.window_end_date).sort().at(-1) ?? first.window_end_date,
      scanned_count: reports.reduce((total, report) => total + report.meta.scanned_count, 0),
      data_available_count: reports.reduce((total, report) => total + report.meta.data_available_count, 0),
      event_count: reports.reduce((total, report) => total + report.meta.event_count, 0),
      both_count: reports.reduce((total, report) => total + report.meta.both_count, 0),
      delivery_only_count: reports.reduce((total, report) => total + report.meta.delivery_only_count, 0),
      volume_only_count: reports.reduce((total, report) => total + report.meta.volume_only_count, 0),
      no_event_count: reports.reduce((total, report) => total + report.meta.no_event_count, 0),
    };
  }, [reports]);
  const quoteSymbols = useMemo(() => tableRows.map((row) => row.symbol), [tableRows]);
  const { quotesBySymbol } = useStockQuotes(quoteSymbols);

  const runSelectedScan = useCallback((): void => {
    if (selectedWatchlists.length === 0) return;
    if (selectedWatchlists.length === 1) {
      void loadDashboard(selectedWatchlists[0], selectedTradeDate ?? undefined).catch(() => undefined);
      return;
    }
    void loadDashboards(selectedWatchlists, selectedTradeDate ?? undefined).catch(() => undefined);
  }, [loadDashboard, loadDashboards, selectedTradeDate, selectedWatchlists]);

  useEffect(() => {
    let active = true;
    void loadWatchlists()
      .then((result) => {
        if (active && result.options.length > 0) {
          setSelectedWatchlists([result.options[0].value]);
        }
      })
      .catch(() => undefined);
    return () => {
      active = false;
    };
  }, [loadWatchlists]);

  useEffect(() => {
    runSelectedScan();
  }, [runSelectedScan, selectedWatchlists.length]);

  const filteredRows = useMemo(() => {
    return tableRows.filter((row) => {
      if (eventType && row.event_type !== eventType) return false;
      if (minVolumeRatio !== null && (row.volume_ratio ?? 0) < minVolumeRatio) return false;
      if (minDeliveryRatio !== null && (row.delivery_ratio ?? 0) < minDeliveryRatio) return false;
      return true;
    });
  }, [eventType, minDeliveryRatio, minVolumeRatio, tableRows]);

  const columns = useMemo<ColumnsType<DeliveryBreakoutDashboardRow>>(() => [
    {
      title: "Watchlist",
      dataIndex: "watchlist_key",
      key: "watchlist_key",
      sorter: (left, right) => (left.watchlist_key ?? "").localeCompare(right.watchlist_key ?? ""),
      filters: Array.from(new Set(tableRows.map((row) => row.watchlist_key).filter((value): value is string => value != null)))
        .sort()
        .map((value) => ({ text: value, value })),
      onFilter: (value, row) => row.watchlist_key === String(value),
      render: (value: string | undefined) => value ?? "-",
    },
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
      title: <span title="Click to sort by previous close → event-day close percentage">Price Context</span>,
      key: "price",
      render: (_value: unknown, row: DeliveryBreakoutDashboardRow) => {
        const quote = quotesBySymbol[row.symbol.toUpperCase()];
        return <PriceContextCell row={row} ltp={quote?.ltp ?? null} />;
      },
      sorter: (left, right) => sortNullableNumbers(left.close_pct_change, right.close_pct_change),
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
  ], [quotesBySymbol, tableRows]);

  const handleWatchlistChange = (values: string[]): void => {
    setSelectedWatchlists(values);
    setSelectedTradeDate(null);
    clear();
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
    if (tableRows.length === 0 || !summaryMeta) return;

    const blob = new Blob(["\uFEFF", buildDeliveryBreakoutCsv(tableRows)], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `delivery_breakout_${summaryMeta.watchlist_key.replace(/[^a-zA-Z0-9_-]+/g, "_")}_${summaryMeta.trade_date}.csv`;
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
              <Button icon={<DownloadOutlined />} disabled={tableRows.length === 0} onClick={downloadCsv}>
                Download CSV
              </Button>
              <Button onClick={runSelectedScan} loading={loading} disabled={selectedWatchlists.length === 0}>
                Reload
              </Button>
            </Space>
          )}
        >
          <Space orientation="vertical" size={12} style={{ width: "100%" }}>
            <Typography.Text type="secondary">
              Select one or more watchlists to find unusual volume, unusual delivery, or both across the last 10 trading sessions.
            </Typography.Text>
            {(error || multiData?.errors.length) ? (
              <Alert
                type="error"
                message={error ?? multiData?.errors.map((item) => `${item.watchlist_key}: ${item.message}`).join(" · ")}
                showIcon
              />
            ) : null}
            <Space wrap>
              <Select
                aria-label="Watchlist"
                mode="multiple"
                showSearch
                loading={loadingWatchlists}
                value={selectedWatchlists}
                onChange={handleWatchlistChange}
                placeholder="Select one or more watchlists"
                maxTagCount="responsive"
                style={{ width: 420 }}
                options={watchlists.map((option) => ({ value: option.value, label: `${option.label} (${option.count})` }))}
              />
              <DatePicker
                aria-label="End date"
                value={selectedTradeDate ? dayjs(selectedTradeDate) : summaryMeta ? dayjs(summaryMeta.trade_date) : null}
                onChange={handleDateChange}
                allowClear={false}
              />
              <Button
                type="primary"
                disabled={selectedWatchlists.length === 0 || loading}
                loading={loading}
                onClick={runSelectedScan}
              >
                Run Scan
              </Button>
            </Space>
          </Space>
        </Card>

        {loadingWatchlists && !summaryMeta ? <Spin /> : null}
        {summaryMeta ? (
          <Card size="small">
            <Space wrap>
              <Tag color="blue">{summaryMeta.watchlist_key}</Tag>
              <Tag>{summaryMeta.window_start_date} → {summaryMeta.window_end_date}</Tag>
              <Tag>Scanned {formatInteger(summaryMeta.scanned_count)}</Tag>
              <Tag>Data available {formatInteger(summaryMeta.data_available_count)}</Tag>
              <Tag color="green">Both {formatInteger(summaryMeta.both_count)}</Tag>
              <Tag color="blue">Delivery only {formatInteger(summaryMeta.delivery_only_count)}</Tag>
              <Tag color="orange">Volume only {formatInteger(summaryMeta.volume_only_count)}</Tag>
              <Tag>No event {formatInteger(summaryMeta.no_event_count)}</Tag>
            </Space>
          </Card>
        ) : null}

        {summaryMeta ? (
          <Card size="small" title={`Event days (${filteredRows.length} of ${summaryMeta.event_count})`}>
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
                rowKey={(row) => `${row.watchlist_key ?? "single"}-${row.symbol}-${row.event_date}`}
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
          <Empty description="Select one or more watchlists to scan the last 10 trading sessions." />
        ) : null}
      </Space>
    </div>
  );
}
