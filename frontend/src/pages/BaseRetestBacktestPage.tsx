import { Alert, Button, Card, Col, InputNumber, Modal, Radio, Row, Select, Space, Spin, Statistic, Table, Tabs, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useMemo, useState, type ReactElement } from "react";
import { useBaseRetestBacktest } from "../hooks/useBaseRetestBacktest";
import { useStockDetail } from "../hooks/useStockDetail";
import type { BaseRetestBacktestRequest, BaseRetestObservation, InstrumentSearchResult, UniverseOptionsResponse } from "../types";
import { getJson } from "../utils/api";
import { buildCompactDailyRows, type CompactDailyRow } from "./compactStockReview/compactStockReview";
import "./baseRetestBacktest.css";

const { Text, Title } = Typography;
const WATCHLISTS_PATH = "/api/strategy/weekly-price-review/watchlists";
type RunScope = "WATCHLIST" | "STOCK";

function formatPrice(value: number | null | undefined): string {
  return value == null ? "-" : `₹${value.toLocaleString("en-IN", { maximumFractionDigits: 2 })}`;
}

function formatPercent(value: number | null | undefined): string {
  return value == null ? "-" : `${value.toFixed(2)}%`;
}

function formatSignedPercent(value: number | null | undefined): string {
  return value == null ? "-" : `${value >= 0 ? "+" : ""}${value.toFixed(2)}%`;
}

function formatShortDate(value: string | null | undefined): string {
  if (!value) return "-";
  const [year, month, day] = value.split("-").map(Number);
  return new Date(year, month - 1, day).toLocaleDateString("en-IN", { weekday: "short", day: "2-digit", month: "short" });
}

function outcomeLabel(outcome: string): string {
  if (outcome === "TARGET_HIT") return "Target hit";
  if (outcome === "STOP_LOSS") return "Stop loss";
  if (outcome === "BASE_INVALIDATED") return "Base invalidated";
  if (outcome === "END_OF_DATA_EXIT") return "End-date close";
  return "No fill";
}

export function BaseRetestBacktestPage() {
  const [watchlists, setWatchlists] = useState<UniverseOptionsResponse["options"]>([]);
  const [watchlistKey, setWatchlistKey] = useState<string>();
  const [scope, setScope] = useState<RunScope>("WATCHLIST");
  const [selectedSymbol, setSelectedSymbol] = useState<string>();
  const [watchlistMembers, setWatchlistMembers] = useState<InstrumentSearchResult[]>([]);
  const [watchlistLoading, setWatchlistLoading] = useState(false);
  const [watchlistError, setWatchlistError] = useState<string | null>(null);
  const [targetPct, setTargetPct] = useState(5);
  const [stopLossPct, setStopLossPct] = useState(5);
  const [selectedTrade, setSelectedTrade] = useState<BaseRetestObservation | null>(null);
  const { data, loading, error, run } = useBaseRetestBacktest();
  const { data: stockDetail, loading: detailLoading, error: detailError } = useStockDetail(selectedTrade?.symbol ?? null, 250);

  useEffect(() => {
    void getJson<UniverseOptionsResponse>(WATCHLISTS_PATH)
      .then((response) => {
        setWatchlists(response.options);
        setWatchlistError(null);
      })
      .catch((cause) => setWatchlistError(cause instanceof Error ? cause.message : "Unable to load watchlists."));
  }, []);

  useEffect(() => {
    if (!watchlistKey) {
      setWatchlistMembers([]);
      setSelectedSymbol(undefined);
      return;
    }
    setWatchlistLoading(true);
    void getJson<InstrumentSearchResult[]>(`/api/stocks/watchlists/${encodeURIComponent(watchlistKey)}/members`, { useCache: false })
      .then((members) => {
        setWatchlistMembers(members);
        setSelectedSymbol((current) => members.some((member) => member.trading_symbol === current) ? current : undefined);
        setWatchlistError(null);
      })
      .catch((cause) => {
        setWatchlistMembers([]);
        setSelectedSymbol(undefined);
        setWatchlistError(cause instanceof Error ? cause.message : "Unable to load stocks for this watchlist.");
      })
      .finally(() => setWatchlistLoading(false));
  }, [watchlistKey]);

  const detailRows = useMemo(() => {
    if (!selectedTrade || !stockDetail) return [];
    const rows = buildCompactDailyRows(stockDetail.days, stockDetail.delivery_days);
    const setupIndex = rows.findIndex((day) => day.date === selectedTrade.firstLowDate);
    const endIndex = rows.findIndex((day) => day.date === (selectedTrade.exitDate ?? selectedTrade.orderEndDate));
    if (setupIndex < 0 || endIndex < 0) return [];
    return rows.slice(Math.max(0, setupIndex - 5), Math.min(rows.length, endIndex + 6));
  }, [selectedTrade, stockDetail]);

  const stockFilters = [...new Set(data?.observations.map((row) => row.symbol) ?? [])]
    .sort()
    .map((symbol) => ({ text: symbol, value: symbol }));

  const columns: ColumnsType<BaseRetestObservation> = [
    { title: "#", key: "number", width: 45, render: (_, __, index) => index + 1 },
    {
      title: "Stock",
      dataIndex: "symbol",
      key: "symbol",
      width: 90,
      fixed: "left",
      filters: stockFilters,
      filterSearch: true,
      onFilter: (value, row) => row.symbol === String(value),
      render: (value: string) => <Text strong>{value}</Text>,
    },
    {
      title: "Base formation",
      key: "base",
      width: 310,
      render: (_, row) => <div><Text strong>{formatPrice(row.firstLow)} → +{formatPercent(row.firstReboundMovePct)} → {formatPrice(row.secondLow)}</Text><div><Text type="secondary">{formatShortDate(row.firstLowDate)} · rebound {formatShortDate(row.firstReboundDate)} · second low {formatShortDate(row.secondLowDate)} · difference {formatPercent(row.lowDifferencePct)}</Text></div></div>,
    },
    {
      title: "Confirmation / order",
      key: "order",
      width: 285,
      render: (_, row) => <div><Text strong>{formatPercent(row.confirmationMovePct)} confirmed · limit {formatPrice(row.limitPrice)}</Text><div><Text type="secondary">Confirmed {formatShortDate(row.confirmationDate)} · active {formatShortDate(row.orderActiveDate)} · invalid below close {formatPrice(row.invalidationClosePrice)}</Text></div></div>,
    },
    {
      title: "Entry / exit",
      key: "trade",
      width: 220,
      render: (_, row) => <div><Text strong>{outcomeLabel(row.outcome)}</Text><div><Text type="secondary">{row.fillDate ? `${formatPrice(row.fillPrice)} ${formatShortDate(row.fillDate)} → ${formatPrice(row.exitPrice)} ${formatShortDate(row.exitDate)}` : `Order ended ${formatShortDate(row.orderEndDate)}`}</Text></div></div>,
    },
    {
      title: "P/L",
      dataIndex: "pnlPct",
      key: "pnlPct",
      width: 90,
      sorter: (left, right) => (left.pnlPct ?? -Infinity) - (right.pnlPct ?? -Infinity),
      render: (value: number | null) => <Text strong className={value != null && value < 0 ? "base-retest-negative" : "base-retest-positive"}>{formatSignedPercent(value)}</Text>,
    },
    { title: "Holding days", dataIndex: "holdingSessions", key: "holdingSessions", width: 110, render: (value: number | null) => value ?? "-" },
    { title: "Daily", key: "daily", width: 75, render: (_, row) => <Button size="small" onClick={() => setSelectedTrade(row)}>View</Button> },
  ];

  const detailColumns: ColumnsType<CompactDailyRow> = [
    {
      title: "Date",
      dataIndex: "date",
      key: "date",
      width: 145,
      render: (value: string) => <div>{formatShortDate(value)}{value === selectedTrade?.fillDate && <span className="base-retest-day-marker">Entry</span>}{value === selectedTrade?.exitDate && <span className="base-retest-day-marker">Exit</span>}</div>,
    },
    { title: "Open", dataIndex: "open", key: "open", render: formatPrice },
    { title: "High", dataIndex: "high", key: "high", render: formatPrice },
    { title: "Low", dataIndex: "low", key: "low", render: formatPrice },
    { title: "Close", dataIndex: "close", key: "close", render: formatPrice },
    { title: "Change %", dataIndex: "daily_change_pct", key: "change", render: formatSignedPercent },
    { title: "Volume", dataIndex: "volume", key: "volume", render: (value: number) => value.toLocaleString("en-IN") },
    { title: "Delivery %", dataIndex: "deliveryPct", key: "delivery", render: formatPercent },
  ];

  const canRun = watchlistKey != null && (scope === "WATCHLIST" || selectedSymbol != null);
  const runBacktest = (): void => {
    if (!watchlistKey || !canRun) return;
    const request: BaseRetestBacktestRequest = {
      watchlistKey,
      targetPct,
      stopLossPct,
      ...(scope === "STOCK" && selectedSymbol ? { symbol: selectedSymbol } : {}),
    };
    void run(request);
  };

  const renderTable = (rows: BaseRetestObservation[]): ReactElement => <Table rowKey={(row) => `${row.symbol}-${row.confirmationDate}`} columns={columns} dataSource={rows} pagination={{ pageSize: 30 }} size="small" scroll={{ x: 1225 }} expandable={{ expandedRowRender: (row) => <div style={{ padding: "4px 12px" }}>Base {formatPrice(row.basePrice)} from lows {formatPrice(row.firstLow)} and {formatPrice(row.secondLow)} ({formatPercent(row.lowDifferencePct)} apart). Confirmation {formatPrice(row.confirmationHigh)}; target {formatPrice(row.targetPrice)}; stop {formatPrice(row.stopLossPrice)}; order end {formatShortDate(row.orderEndDate)}.</div> }} />;

  return <div style={{ padding: 24 }}>
    <Space orientation="vertical" size={16} style={{ width: "100%" }}>
      <Card>
        <Space orientation="vertical" size={12} style={{ width: "100%" }}>
          <Title level={3} style={{ margin: 0 }}>Three-Touch Base Backtest</Title>
          <Text type="secondary">Two lows must be within 1%. Price must rebound at least 5% after each low. The confirmed base places a buy limit 1% above the lower low for the third visit, active from the next trading session.</Text>
          <Text type="secondary">An unfilled order remains active until the data ends or the base closes more than 1% below support. Filled trades exit at the configured target, stop loss, or the final available close.</Text>
          <Row gutter={[12, 12]}>
            <Col xs={24} sm={12} lg={5}><Text strong>Run scope</Text><Radio.Group size="small" aria-label="Run scope" value={scope} onChange={(event) => { setScope(event.target.value); setSelectedSymbol(undefined); }} options={[{ label: "Watchlist", value: "WATCHLIST" }, { label: "Single stock", value: "STOCK" }]} /></Col>
            <Col xs={24} sm={12} lg={5}><Text strong>Watchlist</Text><Select size="small" aria-label="Watchlist" value={watchlistKey} onChange={setWatchlistKey} placeholder="Select a watchlist" style={{ width: "100%" }} options={watchlists.map((watchlist) => ({ value: watchlist.value, label: `${watchlist.label} (${watchlist.count})` }))} /></Col>
            {scope === "STOCK" && <Col xs={24} sm={12} lg={5}><Text strong>Stock</Text><Select size="small" aria-label="Stock" showSearch optionFilterProp="label" value={selectedSymbol} onChange={setSelectedSymbol} placeholder="Select a stock" style={{ width: "100%" }} loading={watchlistLoading} disabled={!watchlistKey} options={watchlistMembers.map((member) => ({ value: member.trading_symbol, label: `${member.trading_symbol} · ${member.company_name ?? ""}` }))} /></Col>}
            <Col xs={12} sm={6} lg={3}><Text strong>Target (%)</Text><InputNumber size="small" aria-label="Target percent" min={0.1} max={100} step={0.5} precision={2} value={targetPct} onChange={(value) => value != null && setTargetPct(value)} style={{ width: "100%" }} /></Col>
            <Col xs={12} sm={6} lg={3}><Text strong>Stop loss (%)</Text><InputNumber size="small" aria-label="Stop loss percent" min={0.1} max={99.9} step={0.5} precision={2} value={stopLossPct} onChange={(value) => value != null && setStopLossPct(value)} style={{ width: "100%" }} /></Col>
          </Row>
          {watchlistError && <Text type="danger">{watchlistError}</Text>}
          <Button size="small" type="primary" onClick={runBacktest} loading={loading} disabled={!canRun}>Run six-month backtest</Button>
        </Space>
      </Card>
      {error && <Alert type="error" message={error} showIcon />}
      {data && <>
        <Card title={`${data.selectedSymbol ?? data.watchlistKey} · ${data.testedFromDate} to ${data.testedToDate}`}><Row gutter={[12, 12]}>
          <Metric title="Bases" value={data.summary.setupCount} /><Metric title="Filled" value={data.summary.filledTradeCount} /><Metric title="Targets" value={data.summary.targetHitCount} /><Metric title="Stops" value={data.summary.stopLossCount} /><Metric title="Invalid / no fill" value={data.summary.baseInvalidatedCount + data.summary.noFillCount} /><Metric title="Win rate" value={formatPercent(data.summary.winRatePct)} /><Metric title="Average P/L" value={formatSignedPercent(data.summary.averagePnlPct)} /><Metric title="Worst P/L" value={formatSignedPercent(data.summary.worstPnlPct)} /><Metric title="Total P/L (sum)" value={formatSignedPercent(data.summary.totalPnlPct)} /><Metric title="Holding sessions" value={data.summary.totalHoldingSessions} />
        </Row></Card>
        <Card title="Base and trade audit trail"><Tabs items={[{ key: "all", label: `All bases (${data.observations.length})`, children: renderTable(data.observations) }, { key: "filled", label: `Filled trades (${data.observations.filter((row) => row.fillDate != null).length})`, children: renderTable(data.observations.filter((row) => row.fillDate != null)) }]} /></Card>
      </>}
    </Space>
    <Modal open={selectedTrade != null} mask={false} onCancel={() => setSelectedTrade(null)} footer={null} width={1100} title={selectedTrade ? `${selectedTrade.symbol} · base and trade detail` : "Base and trade detail"}>
      {detailLoading && <Spin size="small" />}{detailError && <Alert type="error" message={detailError} showIcon />}{!detailLoading && !detailError && <Table<CompactDailyRow> size="small" rowKey="date" pagination={false} columns={detailColumns} dataSource={detailRows} scroll={{ x: 850 }} rowClassName={(day) => day.date === selectedTrade?.fillDate ? "base-retest-entry-row" : day.date === selectedTrade?.exitDate ? "base-retest-exit-row" : ""} />}
    </Modal>
  </div>;
}

function Metric({ title, value }: { title: string; value: number | string }): ReactElement {
  return <Col xs={12} sm={8} lg={4}><Statistic title={title} value={value} /></Col>;
}
