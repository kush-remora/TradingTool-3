import { Alert, Button, Card, Col, InputNumber, Modal, Radio, Row, Select, Space, Spin, Statistic, Table, Tabs, Tooltip, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useMemo, useState, type ReactElement } from "react";
import { useStockDetail } from "../hooks/useStockDetail";
import { useWeeklyLowRetestBacktest } from "../hooks/useWeeklyLowRetestBacktest";
import { buildCompactDailyRows, type CompactDailyRow } from "./compactStockReview/compactStockReview";
import type { InstrumentSearchResult, UniverseOptionsResponse, WeeklyLowRetestBacktestRequest, WeeklyLowRetestObservation } from "../types";
import { getJson } from "../utils/api";
import "./weeklyLowRetestBacktest.css";

const { Text, Title } = Typography;
const WATCHLISTS_PATH = "/api/strategy/weekly-price-review/watchlists";
const CLOSE_POSITION_LOW_THRESHOLD = 30;
const CLOSE_POSITION_HIGH_THRESHOLD = 70;
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

function formatDate(value: string): string {
  const [year, month, day] = value.split("-").map(Number);
  const date = new Date(year, month - 1, day);
  return `${date.toLocaleDateString("en-IN", { weekday: "short" })}, ${value}`;
}

function formatShortDate(value: string | null | undefined): string {
  if (!value) return "-";
  const [year, month, day] = value.split("-").map(Number);
  const date = new Date(year, month - 1, day);
  return date.toLocaleDateString("en-IN", { weekday: "short", day: "2-digit", month: "short" });
}

function closePositionBand(value: number): "HIGH" | "MID" | "LOW" {
  if (value >= CLOSE_POSITION_HIGH_THRESHOLD) return "HIGH";
  if (value <= CLOSE_POSITION_LOW_THRESHOLD) return "LOW";
  return "MID";
}

function ClosePositionCell({ day }: { day: CompactDailyRow }): ReactElement {
  const value = day.closePositionPct;
  if (value == null) return <span>-</span>;

  const band = closePositionBand(value);
  const direction = day.close > day.open ? "UP" : day.close < day.open ? "DOWN" : "FLAT";
  const boundedValue = Math.max(0, Math.min(100, value));
  const directionIcon = direction === "UP" ? "↑" : direction === "DOWN" ? "↓" : "→";

  return (
    <div className={`weekly-low-retest-close-position weekly-low-retest-close-position-${band.toLowerCase()} weekly-low-retest-close-position-${direction.toLowerCase()}`}>
      <div className="weekly-low-retest-close-position-heading">
        <span className="weekly-low-retest-close-position-icon" aria-hidden="true">{directionIcon}</span>
        <span className="weekly-low-retest-close-position-band">{band}</span>
        <span className="weekly-low-retest-close-position-value">{formatPercent(value)}</span>
      </div>
      <div className="weekly-low-retest-close-position-scale" aria-label={`Close ${formatPercent(value)} between low and high`}>
        <span className="weekly-low-retest-close-position-end">L</span>
        <span className="weekly-low-retest-close-position-track">
          <span className="weekly-low-retest-close-position-marker" style={{ left: `${boundedValue}%` }} />
        </span>
        <span className="weekly-low-retest-close-position-end">H</span>
      </div>
    </div>
  );
}

function outcomeLabel(row: WeeklyLowRetestObservation, targetPct: number): string {
  if (row.outcome === "NO_FILL") return "No fill";
  if (row.outcome === "TARGET_HIT") return `${formatPercent(targetPct)} target`;
  return "4th trading-session close";
}

function outcomeColor(row: WeeklyLowRetestObservation): string {
  if (row.outcome === "NO_FILL") return "#d48806";
  return row.realizedReturnPct != null && row.realizedReturnPct < 0 ? "#cf1322" : "#389e0d";
}

export function WeeklyLowRetestBacktestPage() {
  const [watchlists, setWatchlists] = useState<UniverseOptionsResponse["options"]>([]);
  const [watchlistKey, setWatchlistKey] = useState<string>();
  const [scope, setScope] = useState<RunScope>("WATCHLIST");
  const [selectedSymbol, setSelectedSymbol] = useState<string>();
  const [watchlistMembers, setWatchlistMembers] = useState<InstrumentSearchResult[]>([]);
  const [watchlistLoading, setWatchlistLoading] = useState(false);
  const [watchlistError, setWatchlistError] = useState<string | null>(null);
  const [limitOffsetPct, setLimitOffsetPct] = useState(0.5);
  const [targetPct, setTargetPct] = useState(5);
  const [selectedTrade, setSelectedTrade] = useState<WeeklyLowRetestObservation | null>(null);
  const { data, loading, error, run } = useWeeklyLowRetestBacktest();
  const { data: stockDetail, loading: stockDetailLoading, error: stockDetailError } = useStockDetail(selectedTrade?.symbol ?? null, 200);

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
      })
      .catch((cause) => {
        setWatchlistMembers([]);
        setSelectedSymbol(undefined);
        setWatchlistError(cause instanceof Error ? cause.message : "Unable to load stocks for this watchlist.");
      })
      .finally(() => setWatchlistLoading(false));
  }, [watchlistKey]);

  const stockFilters = [...new Set(data?.observations.map((observation) => observation.symbol) ?? [])]
    .sort()
    .map((symbol) => ({ text: symbol, value: symbol }));
  const targetFilterLabel = data?.targetPct == null ? "Target hit" : `${formatPercent(data.targetPct)} target`;

  const selectedTradeDailyRows = useMemo(() => {
    if (!selectedTrade || !stockDetail || !selectedTrade.fillDate || !selectedTrade.exitDate) return [];
    const dailyRows = buildCompactDailyRows(stockDetail.days, stockDetail.delivery_days);
    const cycleStartIndex = dailyRows.findIndex((day) => day.date === selectedTrade.lookbackStartDate);
    const exitIndex = dailyRows.findIndex((day) => day.date === selectedTrade.exitDate);
    if (cycleStartIndex < 0 || exitIndex < 0) return [];
    const windowStartIndex = Math.max(0, cycleStartIndex - 5);
    const windowEndIndex = Math.min(dailyRows.length - 1, exitIndex + 5);
    return dailyRows.slice(windowStartIndex, windowEndIndex + 1);
  }, [selectedTrade, stockDetail]);

  const dailyDetailColumns: ColumnsType<CompactDailyRow> = [
    {
      title: "Date",
      dataIndex: "date",
      key: "date",
      width: 150,
      render: (value: string) => <div>
        <span>{formatShortDate(value)}</span>
        {value === selectedTrade?.limitOrderDate && <span className="weekly-low-retest-day-marker weekly-low-retest-order-marker">Order</span>}
        {value === selectedTrade?.fillDate && <span className="weekly-low-retest-day-marker weekly-low-retest-entry-marker">Entry</span>}
        {value === selectedTrade?.exitDate && <span className="weekly-low-retest-day-marker weekly-low-retest-exit-marker">Exit</span>}
      </div>,
    },
    { title: "Open", dataIndex: "open", key: "open", width: 95, render: formatPrice },
    { title: "High", dataIndex: "high", key: "high", width: 95, render: formatPrice },
    { title: "Low", dataIndex: "low", key: "low", width: 95, render: formatPrice },
    { title: "Close", dataIndex: "close", key: "close", width: 95, render: formatPrice },
    {
      title: "Vol vs 10D avg",
      dataIndex: "volumeVsPrior10dPct",
      key: "volumeVsPrior10dPct",
      width: 125,
      sorter: (left, right) => (left.volumeVsPrior10dPct ?? -Infinity) - (right.volumeVsPrior10dPct ?? -Infinity),
      render: (value: number | null) => value == null ? "-" : `${formatPercent(value)} of avg`,
    },
    {
      title: "Delivery %",
      dataIndex: "deliveryPct",
      key: "deliveryPct",
      width: 100,
      sorter: (left, right) => (left.deliveryPct ?? -Infinity) - (right.deliveryPct ?? -Infinity),
      render: (value: number | null) => formatPercent(value),
    },
    {
      title: "Change %",
      dataIndex: "daily_change_pct",
      key: "daily_change_pct",
      width: 95,
      sorter: (left, right) => (left.daily_change_pct ?? -Infinity) - (right.daily_change_pct ?? -Infinity),
      render: (value: number | null) => <span className={value != null && value < 0 ? "weekly-low-retest-negative" : "weekly-low-retest-positive"}>{formatSignedPercent(value)}</span>,
    },
    {
      title: "Close position",
      dataIndex: "closePositionPct",
      key: "closePositionPct",
      width: 145,
      sorter: (left, right) => (left.closePositionPct ?? -Infinity) - (right.closePositionPct ?? -Infinity),
      render: (_, day) => <ClosePositionCell day={day} />,
    },
    {
      title: "Low → high %",
      key: "lowHighPct",
      width: 105,
      sorter: (left, right) => (left.spreadPct ?? -Infinity) - (right.spreadPct ?? -Infinity),
      render: (_, day) => <span className="weekly-low-retest-range">{formatPercent(day.spreadPct)}</span>,
    },
  ];

  const columns: ColumnsType<WeeklyLowRetestObservation> = [
    { title: "#", key: "rowNumber", width: 45, render: (_, __, index) => index + 1 },
    {
      title: "Stock",
      dataIndex: "symbol",
      key: "symbol",
      fixed: "left",
      width: 90,
      sorter: (left, right) => left.symbol.localeCompare(right.symbol),
      filters: stockFilters,
      filterSearch: true,
      onFilter: (value, row) => row.symbol === String(value),
      render: (value: string) => <Text strong>{value}</Text>,
    },
    {
      title: "Cycle",
      key: "cycle",
      width: 245,
      sorter: (left, right) => left.triggerMovePct - right.triggerMovePct,
      render: (_, row) => <div style={{ lineHeight: 1.35 }}><Text strong>Ref {formatPrice(row.anchorLow)} · {formatPrice(row.recentCycleLow)} → {formatPrice(row.triggerHigh)} · {formatPercent(row.triggerMovePct)}</Text><div><Text type="secondary">Ref {formatShortDate(row.anchorDate)} · latest {formatShortDate(row.recentCycleLowDate)} → {formatShortDate(row.triggerDate)}</Text></div></div>,
    },
    {
      title: "Entry",
      key: "entry",
      width: 205,
      sorter: (left, right) => left.limitOrderDate.localeCompare(right.limitOrderDate),
      render: (_, row) => <div style={{ lineHeight: 1.35 }}><Text strong>{formatPrice(row.limitPrice)}</Text><div><Text type="secondary">{formatShortDate(row.limitOrderDate)} · expires {formatShortDate(row.limitOrderExpiryDate)}</Text></div></div>,
    },
    {
      title: "Peak / hypothetical",
      key: "peak",
      width: 225,
      sorter: (left, right) => (left.peakReturnPct ?? -Infinity) - (right.peakReturnPct ?? -Infinity),
      render: (_, row) => <div style={{ lineHeight: 1.35 }}><Text strong style={{ color: row.targetReachedInOrderWindow ? "#389e0d" : undefined }}>{formatPrice(row.peakHigh)} · {formatPercent(row.peakReturnPct)}</Text><div><Text type="secondary">{formatShortDate(row.peakHighDate)} · target {formatPrice(row.targetPrice)} {row.targetReachedInOrderWindow ? "reached" : "not reached"}</Text></div></div>,
    },
    {
      title: "Result",
      key: "result",
      width: 220,
      filters: [
        { text: "No fill", value: "NO_FILL" },
        { text: targetFilterLabel, value: "TARGET_HIT" },
        { text: "4th trading-session close", value: "FOURTH_SESSION_EXIT" },
      ],
      onFilter: (value, row) => row.outcome === String(value),
      render: (_, row) => <Tooltip title={row.exitDate == null ? `No position filled · hypothetical 4th trading-session close P/L ${formatPercent(row.noFillFourthSessionPnlPct)}` : `Target ${formatPrice(row.targetPrice)} · exit ${formatDate(row.exitDate)} · ${formatPrice(row.exitPrice)}`}><div style={{ lineHeight: 1.35 }}><Text strong style={{ color: outcomeColor(row) }}>{outcomeLabel(row, data?.targetPct ?? 5)} · {formatPercent(row.realizedReturnPct)}</Text>{row.outcome === "NO_FILL" && <div><Text type="secondary">Hypothetical 4th trading-session close {formatPrice(row.fourthSessionClose)} · {formatPercent(row.noFillFourthSessionPnlPct)}</Text></div>}</div></Tooltip>,
    },
    {
      title: "Days",
      key: "days",
      width: 75,
      render: (_, row) => row.fillDate != null && row.exitDate != null
        ? <Button size="small" onClick={() => setSelectedTrade(row)}>View</Button>
        : <Text type="secondary">-</Text>,
    },
  ];

  const renderDebugRow = (row: WeeklyLowRetestObservation): ReactElement => (
    <div style={{ padding: "4px 12px", fontSize: 12, lineHeight: 1.45 }}>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(240px, 1fr))", gap: "4px 18px" }}>
        <div><Text strong>Lookback:</Text> {formatShortDate(row.lookbackStartDate)} → {formatShortDate(row.lookbackEndDate)} · two 5-session cycles</div>
        <div><Text strong>Reference low:</Text> {formatPrice(row.anchorLow)} · {formatShortDate(row.anchorDate)} · Vol {formatPercent(row.anchorVolumeVs10DayAveragePct)} of 10D avg · Close/high {formatPercent(row.anchorCloseNearHighPct)}</div>
        <div><Text strong>Latest cycle:</Text> low {formatPrice(row.recentCycleLow)} on {formatShortDate(row.recentCycleLowDate)} → high {formatPrice(row.triggerHigh)} on {formatShortDate(row.triggerDate)} · Move {formatPercent(row.triggerMovePct)} · {row.cycleSequence}</div>
        <div><Text strong>Order window low:</Text> {formatPrice(row.orderWindowLow)} · {formatShortDate(row.orderWindowLowDate)} · Vol {formatPercent(row.orderWindowLowVolumeVs10DayAveragePct)} of 10D avg · Close/high {formatPercent(row.orderWindowLowCloseNearHighPct)}</div>
        <div><Text strong>Fill:</Text> {row.fillDate == null ? "No fill" : `${formatPrice(row.fillPrice)} · ${formatShortDate(row.fillDate)} · market low ${formatPrice(row.fillLow)} · target ${formatPrice(row.targetPrice)} (low +${formatPercent(data?.targetPct ?? 5)}) · Vol ${formatPercent(row.fillVolumeVs10DayAveragePct)} of 10D avg · Close/high ${formatPercent(row.fillCloseNearHighPct)}`}</div>
        <div><Text strong>Exit:</Text> {row.exitDate == null ? "No actual exit" : `${formatPrice(row.exitPrice)} · ${formatShortDate(row.exitDate)}`} · Fourth trading-session close {formatPrice(row.fourthSessionClose)} · Peak {formatPrice(row.peakHigh)} on {formatShortDate(row.peakHighDate)} from {row.fillDate == null ? "limit" : "fill"}</div>
      </div>
    </div>
  );

  const renderAuditTable = (observations: WeeklyLowRetestObservation[]): ReactElement => (
    <Table
      rowKey={(row) => `${row.symbol}-${row.limitOrderDate}`}
      columns={columns}
      dataSource={observations}
      pagination={{ pageSize: 30 }}
      expandable={{
        expandedRowRender: renderDebugRow,
        expandIcon: ({ expanded, onExpand, record }) => <button type="button" aria-label={expanded ? "Hide debug details" : "Show debug details"} onClick={(event) => onExpand(record, event)} style={{ border: 0, background: "transparent", cursor: "pointer", fontSize: 16, lineHeight: 1 }}>{expanded ? "−" : "+"}</button>,
      }}
      scroll={{ x: 1030 }}
      size="small"
    />
  );

  const canRun = watchlistKey != null && (scope === "WATCHLIST" || selectedSymbol != null);
  const runBacktest = (): void => {
    if (!watchlistKey || !canRun) return;
    const request: WeeklyLowRetestBacktestRequest = {
      watchlistKey,
      limitOffsetPct,
      targetPct,
      ...(scope === "STOCK" && selectedSymbol ? { symbol: selectedSymbol } : {}),
    };
    void run(request);
  };

  return (
    <div style={{ padding: 24 }}>
      <Space orientation="vertical" size={16} style={{ width: "100%" }}>
        <Card>
          <Space orientation="vertical" size={12} style={{ width: "100%" }}>
            <Title level={3} style={{ margin: 0 }}>Daily Low Trigger Backtest</Title>
            <Text type="secondary">Each day checks the previous 10 completed sessions as two five-session cycles. The latest cycle must reach +5% low-to-high; the limit reference is the lower low from both cycles plus the configured buffer.</Text>
            <Text type="secondary">Every day is independent, so consecutive days can create separate orders. Orders remain active for 4 trading sessions; filled trades target +{targetPct.toFixed(2)}% from the buying day&apos;s low, otherwise exit on the fourth trading-session close.</Text>
            <Row gutter={[12, 12]}>
              <Col xs={24} sm={10} lg={6}>
                <Text strong>Run scope</Text>
                <Radio.Group aria-label="Run scope" value={scope} onChange={(event) => { setScope(event.target.value); setSelectedSymbol(undefined); }} options={[{ label: "Watchlist", value: "WATCHLIST" }, { label: "Single stock", value: "STOCK" }]} />
              </Col>
              <Col xs={24} sm={10} lg={6}>
                <Text strong>Watchlist</Text>
                <Select aria-label="Watchlist" value={watchlistKey} onChange={setWatchlistKey} placeholder="Select a watchlist" style={{ width: "100%" }} options={watchlists.map((watchlist) => ({ value: watchlist.value, label: `${watchlist.label} (${watchlist.count})` }))} loading={watchlists.length === 0 && watchlistError == null} />
              </Col>
              {scope === "STOCK" && <Col xs={24} sm={10} lg={6}>
                <Text strong>Stock</Text>
                <Select aria-label="Stock" showSearch optionFilterProp="label" value={selectedSymbol} onChange={setSelectedSymbol} placeholder="Select a stock" style={{ width: "100%" }} loading={watchlistLoading} disabled={!watchlistKey} options={watchlistMembers.map((member) => ({ value: member.trading_symbol, label: `${member.trading_symbol} · ${member.company_name ?? ""}` }))} />
              </Col>}
              <Col xs={24} sm={10} lg={4}>
                <Text strong>Limit offset (%)</Text>
                <InputNumber aria-label="Limit offset percent" min={0.5} max={1} step={0.1} precision={2} value={limitOffsetPct} onChange={(value) => value != null && setLimitOffsetPct(value)} style={{ width: "100%" }} />
              </Col>
              <Col xs={24} sm={10} lg={4}>
                <Text strong>Target (%)</Text>
                <InputNumber aria-label="Target percent" min={0.1} max={100} step={0.5} precision={2} value={targetPct} onChange={(value) => value != null && setTargetPct(value)} style={{ width: "100%" }} />
              </Col>
            </Row>
            {watchlistError && <Text type="danger">{watchlistError}</Text>}
            <Button type="primary" onClick={runBacktest} loading={loading} disabled={!canRun}>Run six-month backtest</Button>
          </Space>
        </Card>

        {error && <Alert type="error" message={error} showIcon />}

        {data && <>
          <Card title={`${data.selectedSymbol ?? data.watchlistKey} · ${formatDate(data.testedFromDate)} to ${formatDate(data.testedToDate)}`}>
            <Row gutter={[12, 12]}>
              <Metric title="Signals" value={data.summary.signalCount} />
              <Metric title="No fills" value={data.summary.noFillCount} />
              <Metric title="Filled" value={data.summary.filledTradeCount} />
              <Metric title="Targets" value={data.summary.targetHitCount} />
              <Metric title="4th trading-session exits" value={data.summary.fourthSessionExitCount} />
              <Metric title="Profitable" value={data.summary.profitableExitCount} />
              <Metric title="Losses" value={data.summary.lossExitCount} />
              <Metric title="Average" value={formatPercent(data.summary.averageRealizedReturnPct)} />
              <Metric title="Median" value={formatPercent(data.summary.medianRealizedReturnPct)} />
              <Metric title="Worst" value={formatPercent(data.summary.worstRealizedReturnPct)} />
              {data.selectedSymbol != null && <>
                <Metric title="Total P/L (sum)" value={formatSignedPercent(data.summary.totalRealizedReturnPct)} />
                <Metric title="Total hold (sessions)" value={data.summary.totalHoldingSessions} />
              </>}
            </Row>
          </Card>
          <Card title="Daily trigger audit trail" extra={<Text type="secondary">Bold = decision data · + = raw debug</Text>}>
            <Tabs
              destroyOnHidden
              items={[
                { key: "all", label: `All signals (${data.observations.length})`, children: renderAuditTable(data.observations) },
                { key: "entered", label: `Actually entered (${data.observations.filter((observation) => observation.fillDate != null).length})`, children: renderAuditTable(data.observations.filter((observation) => observation.fillDate != null)) },
              ]}
            />
          </Card>
        </>}
      </Space>
      <Modal
        open={selectedTrade != null}
        mask={false}
        onCancel={() => setSelectedTrade(null)}
        footer={null}
        width={1280}
        title={selectedTrade ? `${selectedTrade.symbol} · trade daily detail` : "Trade daily detail"}
      >
        {selectedTrade && <Space orientation="vertical" size={8} style={{ width: "100%" }}>
          <Text type="secondary">
            {formatShortDate(selectedTradeDailyRows[0]?.date ?? selectedTrade.lookbackStartDate)} → {formatShortDate(selectedTradeDailyRows.at(-1)?.date ?? selectedTrade.exitDate)} · 5 sessions before cycle start + 5 after exit · entry {formatShortDate(selectedTrade.fillDate)} · exit {formatShortDate(selectedTrade.exitDate)}
          </Text>
          <Space size={16}>
            <Text className="weekly-low-retest-detail-entry-legend">Entry row</Text>
            <Text className="weekly-low-retest-detail-exit-legend">Exit row</Text>
          </Space>
          {stockDetailLoading && <Spin size="small" />}
          {stockDetailError && <Alert type="error" message={stockDetailError} showIcon />}
          {!stockDetailLoading && !stockDetailError && <Table<CompactDailyRow>
            className="weekly-low-retest-detail-table"
            size="small"
            rowKey="date"
            pagination={false}
            columns={dailyDetailColumns}
            dataSource={selectedTradeDailyRows}
            scroll={{ x: 900 }}
            rowClassName={(day) => [
              day.date === selectedTrade.limitOrderDate ? "weekly-low-retest-order-row" : "",
              day.date === selectedTrade.fillDate ? "weekly-low-retest-entry-row" : "",
              day.date === selectedTrade.exitDate ? "weekly-low-retest-exit-row" : "",
              day.date === selectedTrade.fillDate && day.date === selectedTrade.exitDate ? "weekly-low-retest-entry-exit-row" : "",
            ].filter(Boolean).join(" ")}
          />}
        </Space>}
      </Modal>
    </div>
  );
}

function Metric({ title, value }: { title: string; value: number | string }): ReactElement {
  return <Col xs={12} sm={8} lg={4}><Statistic title={title} value={value} /></Col>;
}
