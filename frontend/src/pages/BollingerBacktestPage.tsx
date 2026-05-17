import { Alert, Button, Card, Col, Empty, Input, Modal, Row, Select, Space, Spin, Statistic, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useMemo, useState } from "react";
import { useBollingerBacktest } from "../hooks/useBollingerBacktest";
import { getJson } from "../utils/api";
import type { BollingerBacktestConfig, BollingerBacktestDebugRow, BollingerBacktestRequest, BollingerBacktestResponse, BollingerBacktestTrade } from "../types";

const DEFAULT_CONFIG: BollingerBacktestConfig = {
  capital: 200000,
  maxOpenPositions: 1,
  fromDate: "2026-01-01",
  toDate: "2026-05-16",
  setupWindowDays: 5,
  tightSqueezeTolerancePct: 12,
  volumeMultiplier: 2,
  breakEvenProfitPct: 2,
  maxHoldDays: 999,
};

interface UniverseOption {
  label: string;
  value: string;
  count: number;
}

interface UniverseOptionsResponse {
  options: UniverseOption[];
}

interface StockListItem {
  symbol: string;
  company_name?: string;
  companyName?: string;
}

const tradeColumns: ColumnsType<BollingerBacktestTrade> = [
  { title: "Symbol", dataIndex: "symbol", key: "symbol", width: 110, fixed: "left" },
  { title: "Entry", dataIndex: "entryDate", key: "entryDate", width: 110 },
  { title: "Exit", dataIndex: "exitDate", key: "exitDate", width: 110 },
  { title: "Days", dataIndex: "holdingDays", key: "holdingDays", width: 80 },
  { title: "Qty", dataIndex: "quantity", key: "quantity", width: 80 },
  { title: "Invested", dataIndex: "investedAmount", key: "investedAmount", width: 120, render: (v: number) => `₹${v.toFixed(2)}` },
  { title: "Entry Px", dataIndex: "entryPrice", key: "entryPrice", width: 100, render: (v: number) => v.toFixed(2) },
  { title: "Exit Px", dataIndex: "exitPrice", key: "exitPrice", width: 100, render: (v: number) => v.toFixed(2) },
  {
    title: "Exit Reason",
    dataIndex: "exitReason",
    key: "exitReason",
    width: 130,
    render: (reason: string) => {
      const color = reason.includes("STOP") ? "red" : reason.includes("PROFIT") ? "green" : "blue";
      return <Tag color={color}>{reason}</Tag>;
    },
  },
  { title: "Net P&L", dataIndex: "netPnlInr", key: "netPnlInr", width: 120, render: (v: number) => `₹${v.toFixed(2)}` },
  { title: "Net %", dataIndex: "netReturnPct", key: "netReturnPct", width: 90, render: (v: number) => `${v.toFixed(2)}%` },
];

const debugColumns: ColumnsType<BollingerBacktestDebugRow> = [
  { title: "Date", dataIndex: "date", key: "date", width: 110, fixed: "left" },
  { title: "LTP", dataIndex: "ltp", key: "ltp", width: 90, render: (v: number) => v.toFixed(2) },
  { title: "%B", dataIndex: "percentB", key: "percentB", width: 80, render: (v: number) => v.toFixed(2) },
  { title: "RSI", dataIndex: "rsi14", key: "rsi14", width: 80, render: (v: number | null) => (v == null ? "-" : v.toFixed(2)) },
  { title: "Bandwidth %", dataIndex: "bandwidthPct", key: "bandwidthPct", width: 110, render: (v: number) => `${v.toFixed(2)}%` },
  { title: "Vol Ratio", dataIndex: "volumeRatio20", key: "volumeRatio20", width: 100, render: (v: number) => `${v.toFixed(2)}x` },
  {
    title: "SMA200",
    dataIndex: "closeAboveSma200",
    key: "closeAboveSma200",
    width: 100,
    render: (v: boolean | null) => (v == null ? "-" : v ? "Above" : "Below"),
  },
  { title: "BB Upper", dataIndex: "bbUpper", key: "bbUpper", width: 100, render: (v: number) => v.toFixed(2) },
  { title: "BB Mid", dataIndex: "bbMiddle", key: "bbMiddle", width: 100, render: (v: number) => v.toFixed(2) },
  { title: "BB Lower", dataIndex: "bbLower", key: "bbLower", width: 100, render: (v: number) => v.toFixed(2) },
  { title: "Signal", dataIndex: "signal", key: "signal", width: 110 },
];

export function BollingerBacktestPage() {
  const { data, error, loading, run } = useBollingerBacktest();
  const [universe, setUniverse] = useState<string>("WATCHLIST");
  const [universeOptions, setUniverseOptions] = useState<UniverseOption[]>([]);
  const [stockOptions, setStockOptions] = useState<Array<{ label: string; value: string }>>([]);
  const [configText, setConfigText] = useState<string>(JSON.stringify(DEFAULT_CONFIG, null, 2));
  const [parseError, setParseError] = useState<string | null>(null);
  const [selectedSymbols, setSelectedSymbols] = useState<string[]>([]);

  useEffect(() => {
    const fetchUniverses = async () => {
      try {
        const response = await getJson<UniverseOptionsResponse>("/api/screener/universes");
        setUniverseOptions(response.options);
      } catch {
        setUniverseOptions([{ label: "Watchlist", value: "WATCHLIST", count: 0 }]);
      }
    };
    const fetchStocks = async () => {
      try {
        const stocks = await getJson<StockListItem[]>("/api/stocks");
        setStockOptions(
          stocks
            .map((stock) => ({
              value: stock.symbol.trim().toUpperCase(),
              label: `${stock.symbol.trim().toUpperCase()} - ${stock.company_name ?? stock.companyName ?? stock.symbol.trim().toUpperCase()}`,
            }))
            .sort((a, b) => a.value.localeCompare(b.value)),
        );
      } catch {
        setStockOptions([]);
      }
    };
    void fetchUniverses();
    void fetchStocks();
  }, []);

  const runBacktest = () => {
    setParseError(null);
    try {
      const config = JSON.parse(configText) as BollingerBacktestConfig;
      const request: BollingerBacktestRequest = {
        universe,
        symbols: selectedSymbols.length > 0 ? selectedSymbols : undefined,
        config,
      };
      void run(request);
    } catch {
      setParseError("Invalid JSON config. Please check formatting.");
    }
  };

  const result = useMemo(() => data, [data]);

  return (
    <div style={{ padding: 24, background: "#f5f7fa", minHeight: "calc(100vh - 48px)" }}>
      <Space direction="vertical" size={16} style={{ width: "100%" }}>
        <Card size="small" title="Bollinger Squeeze Backtest Controls">
          <Space direction="vertical" style={{ width: "100%" }}>
            <div>
              <Typography.Text strong>Universe</Typography.Text>
              <Select
                style={{ width: 320, display: "block", marginTop: 8 }}
                value={universe}
                onChange={setUniverse}
                options={universeOptions.map((option) => ({ label: `${option.label} (${option.count})`, value: option.value }))}
              />
            </div>
            <div>
              <Typography.Text strong>Selected Stocks (optional, max 5)</Typography.Text>
              <Select
                mode="multiple"
                allowClear
                maxCount={5}
                style={{ width: "100%", display: "block", marginTop: 8 }}
                value={selectedSymbols}
                onChange={(values) => setSelectedSymbols(values.slice(0, 5))}
                placeholder="Pick 1-5 stocks to run only on those symbols"
                options={stockOptions}
              />
              <Typography.Text type="secondary">
                If symbols are selected, backtest ignores universe and runs only on chosen stocks.
              </Typography.Text>
            </div>
            <div>
              <Typography.Text strong>Backtest Config JSON</Typography.Text>
              <Input.TextArea rows={14} value={configText} onChange={(event) => setConfigText(event.target.value)} style={{ marginTop: 8, fontFamily: "monospace" }} />
            </div>
            <Space>
              <Button type="primary" onClick={runBacktest} loading={loading}>Run Bollinger Squeeze Backtest</Button>
              <Typography.Text type="secondary">Universe from UI; lookback window from config.</Typography.Text>
            </Space>
          </Space>
        </Card>

        {parseError && <Alert type="error" showIcon message={parseError} />}
        {error && <Alert type="error" showIcon message={error} />}

        {loading && (
          <div style={{ textAlign: "center", padding: 40 }}>
            <Spin size="large" />
          </div>
        )}

        {!loading && result && <BollingerBacktestResultView result={result} />}

        {!loading && !result && !error && !parseError && (
          <Card>
            <Empty description="Run backtest to view summary and trade breakdown." />
          </Card>
        )}
      </Space>
    </div>
  );
}

function BollingerBacktestResultView({ result }: { result: BollingerBacktestResponse }) {
  const [debugTrade, setDebugTrade] = useState<BollingerBacktestTrade | null>(null);

  const columnsWithDebug: ColumnsType<BollingerBacktestTrade> = [
    ...tradeColumns,
    {
      title: "Debug",
      key: "debug",
      width: 100,
      fixed: "right",
      render: (_, trade) => (
        <Button size="small" onClick={() => setDebugTrade(trade)}>Debug</Button>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <Row gutter={12}>
        <Col><Card size="small"><Statistic title="Trades" value={result.summary.totalTrades} /></Card></Col>
        <Col><Card size="small"><Statistic title="Win Rate" value={result.summary.winRatePct.toFixed(2)} suffix="%" /></Card></Col>
        <Col><Card size="small"><Statistic title="Net P&L" value={result.summary.netPnlInr.toFixed(2)} prefix="₹" /></Card></Col>
        <Col><Card size="small"><Statistic title="Return" value={result.summary.totalReturnPct.toFixed(2)} suffix="%" /></Card></Col>
        <Col><Card size="small"><Statistic title="Max DD" value={result.summary.maxDrawdownInr.toFixed(2)} prefix="₹" /></Card></Col>
      </Row>

      <Card size="small" title="Diagnostics">
        <Space wrap size={20}>
          <Typography.Text>Symbols Considered: {result.diagnostics.symbolsConsidered}</Typography.Text>
          <Typography.Text>Insufficient Data: {result.diagnostics.symbolsWithInsufficientData.length}</Typography.Text>
          <Typography.Text>No Trades: {result.diagnostics.symbolsWithNoTrades.length}</Typography.Text>
        </Space>
      </Card>

      <Card size="small" title="Trades">
        <Table
          rowKey={(row) => `${row.symbol}-${row.entryDate}-${row.exitDate}`}
          columns={columnsWithDebug}
          dataSource={result.trades}
          size="small"
          pagination={{ pageSize: 25, showSizeChanger: true }}
          scroll={{ x: 1400 }}
          expandable={{
            expandedRowRender: (trade) => (
              <Row gutter={12}>
                <Col xs={24} md={12}>
                  <Card size="small" title="Entry Criteria">
                    <Typography.Text>%B: {trade.entryCriteria.percentB.toFixed(2)}</Typography.Text><br />
                    <Typography.Text>RSI: {trade.entryCriteria.rsi14?.toFixed(2) ?? "-"}</Typography.Text><br />
                    <Typography.Text>Bandwidth %: {trade.entryCriteria.bandwidthPct.toFixed(2)}%</Typography.Text><br />
                    <Typography.Text>Vol Ratio: {trade.entryCriteria.volumeRatio20.toFixed(2)}x</Typography.Text><br />
                    <Typography.Text>SMA200: {trade.entryCriteria.closeAboveSma200 == null ? "-" : trade.entryCriteria.closeAboveSma200 ? "Above" : "Below"}</Typography.Text><br />
                    <Typography.Text>Signal: {trade.entryCriteria.signal}</Typography.Text><br />
                    <Typography.Text type="secondary">{trade.entryCriteria.reasoning || "-"}</Typography.Text>
                  </Card>
                </Col>
                <Col xs={24} md={12}>
                  <Card size="small" title="Exit Criteria">
                    <Typography.Text>%B: {trade.exitCriteria.percentB.toFixed(2)}</Typography.Text><br />
                    <Typography.Text>RSI: {trade.exitCriteria.rsi14?.toFixed(2) ?? "-"}</Typography.Text><br />
                    <Typography.Text>Bandwidth %: {trade.exitCriteria.bandwidthPct.toFixed(2)}%</Typography.Text><br />
                    <Typography.Text>Vol Ratio: {trade.exitCriteria.volumeRatio20.toFixed(2)}x</Typography.Text><br />
                    <Typography.Text>SMA200: {trade.exitCriteria.closeAboveSma200 == null ? "-" : trade.exitCriteria.closeAboveSma200 ? "Above" : "Below"}</Typography.Text><br />
                    <Typography.Text>Signal: {trade.exitCriteria.signal}</Typography.Text><br />
                    <Typography.Text type="secondary">{trade.exitCriteria.reasoning || "-"}</Typography.Text>
                  </Card>
                </Col>
              </Row>
            ),
          }}
        />
      </Card>

      <Modal
        title={debugTrade ? `Debug: ${debugTrade.symbol} (${debugTrade.entryDate} → ${debugTrade.exitDate})` : "Debug"}
        open={debugTrade != null}
        onCancel={() => setDebugTrade(null)}
        footer={null}
        width={1200}
      >
        {debugTrade && (
          <Table
            rowKey={(row) => row.date}
            columns={debugColumns}
            dataSource={debugTrade.debugRows}
            size="small"
            pagination={false}
            scroll={{ x: 1250 }}
          />
        )}
      </Modal>
    </Space>
  );
}
