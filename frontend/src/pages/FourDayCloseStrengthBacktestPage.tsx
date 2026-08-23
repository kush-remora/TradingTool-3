import { BarChartOutlined, EyeOutlined } from "@ant-design/icons";
import { Alert, Button, Card, Col, Empty, InputNumber, Modal, Radio, Row, Select, Space, Statistic, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useMemo, useState, type ReactElement } from "react";
import { InstrumentSearch } from "../components/InstrumentSearch";
import { useInstrumentSearch } from "../hooks/useInstrumentSearch";
import { useFourDayCloseStrengthBacktest } from "../hooks/useFourDayCloseStrengthBacktest";
import { getJson } from "../utils/api";
import type {
  FourDayCloseStrengthBacktestResponse,
  FourDayCloseStrengthCandle,
  FourDayCloseStrengthSignal,
  InstrumentSearchResult,
  UniverseOptionsResponse,
} from "../types";

const { Text, Title } = Typography;
const WATCHLISTS_PATH = "/api/strategy/adaptive-breakout/watchlists";
const TEST_WINDOW_MONTHS = 60;

type RunScope = "STOCK" | "WATCHLIST";

function formatPrice(value: number): string {
  return `₹${value.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function formatPercent(value: number | null): string {
  return value == null ? "—" : `${value >= 0 ? "+" : ""}${value.toFixed(2)}%`;
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat("en-IN", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    timeZone: "UTC",
  }).format(new Date(`${value}T00:00:00Z`));
}

function buildKiteChartUrl(symbol: string, instrumentToken: number): string {
  return `https://kite.zerodha.com/chart/web/tvc/NSE/${encodeURIComponent(symbol)}/${instrumentToken}`;
}

function closePositionsLabel(values: number[]): string {
  return values.map((value) => `${value.toFixed(1)}%`).join(" · ");
}

export function FourDayCloseStrengthBacktestPage(): ReactElement {
  const [scope, setScope] = useState<RunScope>("STOCK");
  const [selectedInstrument, setSelectedInstrument] = useState<InstrumentSearchResult | null>(null);
  const [watchlists, setWatchlists] = useState<UniverseOptionsResponse["options"]>([]);
  const [watchlistKey, setWatchlistKey] = useState<string>();
  const [watchlistError, setWatchlistError] = useState<string | null>(null);
  const [closePositionThresholdPct, setClosePositionThresholdPct] = useState(80);
  const [selectedSignal, setSelectedSignal] = useState<FourDayCloseStrengthSignal | null>(null);
  const { allInstruments, loading: instrumentsLoading, error: instrumentsError } = useInstrumentSearch();
  const { data, loading, error, run } = useFourDayCloseStrengthBacktest();
  const nseEquities = useMemo(
    () => allInstruments.filter((instrument) => instrument.exchange === "NSE" && instrument.instrument_type === "EQ"),
    [allInstruments],
  );

  useEffect(() => {
    if (scope !== "WATCHLIST" || watchlists.length > 0 || watchlistError != null) return;
    void getJson<UniverseOptionsResponse>(WATCHLISTS_PATH)
      .then((response) => setWatchlists(response.options))
      .catch((cause) => setWatchlistError(cause instanceof Error ? cause.message : "Unable to load watchlists."));
  }, [scope, watchlistError, watchlists.length]);

  const runBacktest = (): void => {
    if (scope === "WATCHLIST") {
      if (watchlistKey == null) return;
      void run({ watchlistKey, closePositionThresholdPct });
      return;
    }
    if (selectedInstrument == null) return;
    void run({
      symbol: selectedInstrument.trading_symbol,
      instrumentToken: selectedInstrument.instrument_token,
      closePositionThresholdPct,
    });
  };

  const canRun = scope === "WATCHLIST" ? watchlistKey != null : selectedInstrument != null;

  return (
    <div style={{ padding: 24 }}>
      <Space orientation="vertical" size={16} style={{ width: "100%" }}>
        <Card>
          <Space orientation="vertical" size={12} style={{ width: "100%" }}>
            <Space size={8} align="center">
              <BarChartOutlined />
              <Title level={3} style={{ margin: 0 }}>Four-Day Close-Strength Backtest</Title>
            </Space>
            <Text type="secondary">
              Scan the latest {TEST_WINDOW_MONTHS} months for four consecutive green candles, each closing at or above the configured percentage of its daily high-low range.
            </Text>
            <Text type="secondary">Each signal opens a 20-session price and volume audit. This screen does not simulate entries or exits.</Text>
            <Space wrap>
              <Radio.Group
                aria-label="Run scope"
                buttonStyle="solid"
                value={scope}
                onChange={(event) => setScope(event.target.value as RunScope)}
                options={[{ label: "Single stock", value: "STOCK" }, { label: "Watchlist", value: "WATCHLIST" }]}
              />
              {scope === "STOCK" ? (
                <div>
                  {instrumentsLoading ? <Text type="secondary">Loading instruments…</Text> : (
                    <InstrumentSearch
                      instruments={nseEquities}
                      value={selectedInstrument}
                      onSelect={setSelectedInstrument}
                      placeholder="Select an NSE stock"
                    />
                  )}
                  {instrumentsError && <Text type="danger">{instrumentsError}</Text>}
                </div>
              ) : (
                <div>
                  <Select
                    aria-label="Watchlist"
                    value={watchlistKey}
                    onChange={setWatchlistKey}
                    placeholder="Select a watchlist"
                    options={watchlists.map((watchlist) => ({ value: watchlist.value, label: `${watchlist.label} (${watchlist.count})` }))}
                    loading={watchlists.length === 0 && watchlistError == null}
                    style={{ width: 280 }}
                  />
                  {watchlistError && <Text type="danger">{watchlistError}</Text>}
                </div>
              )}
              <label>
                <Text strong>Close near high</Text>
                <InputNumber
                  aria-label="Close near high threshold"
                  min={0}
                  max={100}
                  step={5}
                  precision={2}
                  value={closePositionThresholdPct}
                  onChange={(value) => value != null && setClosePositionThresholdPct(value)}
                  style={{ width: 105, marginLeft: 8 }}
                />
                <Text type="secondary">%</Text>
              </label>
              <Button type="primary" onClick={runBacktest} loading={loading} disabled={!canRun}>
                Run {scope === "WATCHLIST" ? watchlistKey ?? "watchlist" : selectedInstrument?.trading_symbol ?? "stock"} test
              </Button>
            </Space>
          </Space>
        </Card>

        {error && <Alert type="error" message={error} showIcon />}
        {!data && !loading && !error && <Empty description="Select a stock or watchlist to scan the 60-month history." />}
        {data && <BacktestResults data={data} onOpenSignal={setSelectedSignal} />}
        <Modal
          title={selectedSignal == null ? undefined : `${selectedSignal.symbol} · 20 trading days ending ${formatDate(selectedSignal.signalDate)}`}
          open={selectedSignal != null}
          onCancel={() => setSelectedSignal(null)}
          footer={null}
          width={1100}
        >
          {selectedSignal && <SignalDetail signal={selectedSignal} thresholdPct={data?.closePositionThresholdPct ?? closePositionThresholdPct} />}
        </Modal>
      </Space>
    </div>
  );
}

function BacktestResults({ data, onOpenSignal }: { data: FourDayCloseStrengthBacktestResponse; onOpenSignal: (signal: FourDayCloseStrengthSignal) => void }): ReactElement {
  const columns: ColumnsType<FourDayCloseStrengthSignal> = [
    {
      title: "Stock",
      dataIndex: "symbol",
      key: "symbol",
      fixed: "left",
      render: (value: string, row) => <a aria-label={`Open ${value} in Kite`} href={buildKiteChartUrl(value, row.instrumentToken)} target="_blank" rel="noopener noreferrer"><Text strong>{value}</Text></a>,
    },
    { title: "Signal date", dataIndex: "signalDate", key: "signalDate", render: formatDate },
    { title: "Pattern", key: "pattern", render: (_, row) => `${formatDate(row.patternStartDate)} → ${formatDate(row.patternEndDate)}` },
    { title: "Close locations", key: "closeLocations", render: (_, row) => <Tag color="green">{closePositionsLabel(row.patternClosePositionPct)}</Tag> },
    { title: "20D audit", key: "audit", render: (_, row) => <Button aria-label={`Open 20-day audit for ${row.symbol} on ${row.signalDate}`} icon={<EyeOutlined />} onClick={() => onOpenSignal(row)}>Open</Button> },
  ];

  return (
    <>
      <Card title={`${data.watchlistKey ?? data.symbol ?? "Stock"} · ${formatDate(data.testedFromDate)} to ${formatDate(data.testedToDate)}`}>
        <Row gutter={[12, 12]}>
          <Metric title="Stocks tested" value={data.symbols.length} />
          <Metric title="Signals" value={data.signalCount} />
          <Metric title="Pattern" value={`${data.patternSessions} days`} />
          <Metric title="Close threshold" value={`${data.closePositionThresholdPct}%`} />
        </Row>
      </Card>
      <Card title={`Signal dates (${data.signalCount})`}>
        {data.signals.length === 0 ? <Empty description="No four-day close-strength signals matched this threshold." /> : (
          <Table<FourDayCloseStrengthSignal>
            rowKey={(row) => `${row.symbol}-${row.signalDate}`}
            columns={columns}
            dataSource={data.signals}
            pagination={{ pageSize: 30 }}
            size="small"
            scroll={{ x: 850 }}
          />
        )}
      </Card>
    </>
  );
}

function SignalDetail({ signal, thresholdPct }: { signal: FourDayCloseStrengthSignal; thresholdPct: number }): ReactElement {
  const columns: ColumnsType<FourDayCloseStrengthCandle> = [
    { title: "Date", dataIndex: "date", key: "date", render: formatDate },
    { title: "Open", dataIndex: "open", key: "open", render: formatPrice },
    { title: "High", dataIndex: "high", key: "high", render: formatPrice },
    { title: "Low", dataIndex: "low", key: "low", render: formatPrice },
    { title: "Close", dataIndex: "close", key: "close", render: formatPrice },
    { title: "Volume", dataIndex: "volume", key: "volume", render: (value: number) => value.toLocaleString("en-IN") },
    { title: "Daily move", dataIndex: "dailyChangePct", key: "dailyChangePct", render: formatPercent },
    { title: "Close near high", dataIndex: "closeLocationPct", key: "closeLocationPct", render: (value: number | null) => <Text type={value != null && value >= thresholdPct ? "success" : undefined}>{formatPercent(value)}</Text> },
  ];

  return (
    <Space orientation="vertical" size={12} style={{ width: "100%" }}>
      <Space wrap>
        <Tag color="green">{signal.patternStartDate} → {signal.patternEndDate}</Tag>
        <Text type="secondary">Four green candles · close locations {closePositionsLabel(signal.patternClosePositionPct)}</Text>
        <a href={buildKiteChartUrl(signal.symbol, signal.instrumentToken)} target="_blank" rel="noopener noreferrer">Open in Kite</a>
      </Space>
      <Table<FourDayCloseStrengthCandle> rowKey="date" columns={columns} dataSource={signal.lastTwentyTradingDays} pagination={false} size="small" scroll={{ x: 900 }} />
    </Space>
  );
}

function Metric({ title, value }: { title: string; value: number | string }): ReactElement {
  return <Col xs={12} sm={8} lg={4}><Statistic title={title} value={value} /></Col>;
}
