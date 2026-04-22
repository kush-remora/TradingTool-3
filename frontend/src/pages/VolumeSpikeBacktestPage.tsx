import { Alert, Button, Card, Col, DatePicker, Empty, Input, InputNumber, Row, Select, Space, Spin, Statistic, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import dayjs from "dayjs";
import { useMemo, useState } from "react";
import { useVolumeSpikeBacktest } from "../hooks/useVolumeSpikeBacktest";
import type { EarningsFilterMode, VolumeSpikeBacktestRequest, VolumeSpikeBacktestResponse, VolumeSpikeBacktestTrade } from "../types";

const { RangePicker } = DatePicker;

const tradeColumns: ColumnsType<VolumeSpikeBacktestTrade> = [
  { title: "Symbol", dataIndex: "symbol", key: "symbol", width: 100, fixed: "left" },
  { title: "Signal", dataIndex: "signalTime", key: "signalTime", width: 165 },
  { title: "Entry", dataIndex: "entryTime", key: "entryTime", width: 165 },
  { title: "Exit", dataIndex: "exitTime", key: "exitTime", width: 165 },
  {
    title: "Exit Reason",
    dataIndex: "exitReason",
    key: "exitReason",
    width: 110,
    render: (reason: string) => {
      const color = reason === "TARGET_HIT" ? "green" : reason === "STOP_HIT" ? "red" : "default";
      return <Tag color={color}>{reason}</Tag>;
    },
  },
  { title: "Qty", dataIndex: "quantity", key: "quantity", width: 80 },
  { title: "Entry", dataIndex: "entryPrice", key: "entryPrice", width: 90, render: (value: number) => value.toFixed(2) },
  { title: "Exit", dataIndex: "exitPrice", key: "exitPrice", width: 90, render: (value: number) => value.toFixed(2) },
  { title: "RVOL", dataIndex: "rvolAtSignal", key: "rvolAtSignal", width: 90, render: (value: number) => `${value.toFixed(2)}x` },
  { title: "Net P&L", dataIndex: "netPnlInr", key: "netPnlInr", width: 120, render: (value: number) => `₹${value.toFixed(2)}` },
  { title: "Net %", dataIndex: "netReturnPct", key: "netReturnPct", width: 90, render: (value: number) => `${value.toFixed(2)}%` },
];

const earningsModeOptions: Array<{ label: string; value: EarningsFilterMode }> = [
  { label: "OFF", value: "OFF" },
  { label: "CUSTOM_WINDOW", value: "CUSTOM_WINDOW" },
  { label: "MANUAL_SYMBOL", value: "MANUAL_SYMBOL" },
];

const delayOptions = [
  { label: "5 minutes", value: 5 },
  { label: "10 minutes", value: 10 },
  { label: "15 minutes", value: 15 },
];

export function VolumeSpikeBacktestPage() {
  const { data, loading, error, run } = useVolumeSpikeBacktest();

  const [dateRange, setDateRange] = useState<[dayjs.Dayjs, dayjs.Dayjs]>([
    dayjs().subtract(1, "month"),
    dayjs(),
  ]);
  const [delayMinutes, setDelayMinutes] = useState<number>(5);
  const [manualSymbolsText, setManualSymbolsText] = useState<string>("");
  const [earningsFilterMode, setEarningsFilterMode] = useState<EarningsFilterMode>("OFF");
  const [earningsWindowStartOffsetDays, setEarningsWindowStartOffsetDays] = useState<number>(-10);
  const [earningsWindowEndOffsetDays, setEarningsWindowEndOffsetDays] = useState<number>(-1);
  const [rvolThreshold, setRvolThreshold] = useState<number>(2.0);
  const [targetPct, setTargetPct] = useState<number>(5.0);
  const [stopPct, setStopPct] = useState<number>(2.0);
  const [positionSizeInr, setPositionSizeInr] = useState<number>(200000);
  const [feePerTradeInr, setFeePerTradeInr] = useState<number>(500);

  const manualSymbols = useMemo(() => {
    return manualSymbolsText
      .split(/[\n,\s]+/)
      .map((value) => value.trim().toUpperCase())
      .filter((value) => value.length > 0);
  }, [manualSymbolsText]);

  const runBacktest = (): void => {
    const request: VolumeSpikeBacktestRequest = {
      fromDate: dateRange[0].format("YYYY-MM-DD"),
      toDate: dateRange[1].format("YYYY-MM-DD"),
      delayMinutes,
      manualSymbols,
      earningsFilterMode,
      earningsWindowStartOffsetDays: earningsFilterMode === "CUSTOM_WINDOW" ? earningsWindowStartOffsetDays : undefined,
      earningsWindowEndOffsetDays: earningsFilterMode === "CUSTOM_WINDOW" ? earningsWindowEndOffsetDays : undefined,
      rvolThreshold,
      targetPct,
      stopPct,
      positionSizeInr,
      feePerTradeInr,
    };

    void run(request);
  };

  return (
    <div style={{ padding: 24, background: "#f5f7fa", minHeight: "calc(100vh - 48px)" }}>
      <Space orientation="vertical" size={16} style={{ width: "100%" }}>
        <div>
          <Typography.Title level={4} style={{ margin: 0 }}>Volume Spike Backtest</Typography.Title>
          <Typography.Text type="secondary">
            5-minute RVOL breakout backtest with Redis cache, Kite fallback, and optional earnings-window filtering.
          </Typography.Text>
        </div>

        <Card size="small" title="Controls">
          <Space orientation="vertical" style={{ width: "100%" }} size={12}>
            <Row gutter={12}>
              <Col xs={24} md={10}>
                <Typography.Text strong>Date Range</Typography.Text>
                <div>
                  <RangePicker
                    allowClear={false}
                    value={dateRange}
                    onChange={(next) => {
                      if (next?.[0] && next?.[1]) {
                        setDateRange([next[0], next[1]]);
                      }
                    }}
                    format="YYYY-MM-DD"
                  />
                </div>
              </Col>
              <Col xs={24} md={6}>
                <Typography.Text strong>Delay Minutes</Typography.Text>
                <Select
                  style={{ width: "100%" }}
                  value={delayMinutes}
                  options={delayOptions}
                  onChange={(value) => setDelayMinutes(value)}
                />
              </Col>
              <Col xs={24} md={8}>
                <Typography.Text strong>Earnings Filter</Typography.Text>
                <Select
                  style={{ width: "100%" }}
                  value={earningsFilterMode}
                  options={earningsModeOptions}
                  onChange={(value) => setEarningsFilterMode(value)}
                />
              </Col>
            </Row>

            {earningsFilterMode === "CUSTOM_WINDOW" && (
              <Row gutter={12}>
                <Col xs={24} md={6}>
                  <Typography.Text strong>Window Start Offset</Typography.Text>
                  <InputNumber style={{ width: "100%" }} value={earningsWindowStartOffsetDays} onChange={(value) => setEarningsWindowStartOffsetDays(value ?? -10)} />
                </Col>
                <Col xs={24} md={6}>
                  <Typography.Text strong>Window End Offset</Typography.Text>
                  <InputNumber style={{ width: "100%" }} value={earningsWindowEndOffsetDays} onChange={(value) => setEarningsWindowEndOffsetDays(value ?? -1)} />
                </Col>
              </Row>
            )}

            <Row gutter={12}>
              <Col xs={24} md={6}>
                <Typography.Text strong>RVOL Threshold</Typography.Text>
                <InputNumber min={0.1} step={0.1} style={{ width: "100%" }} value={rvolThreshold} onChange={(value) => setRvolThreshold(value ?? 2.0)} />
              </Col>
              <Col xs={24} md={6}>
                <Typography.Text strong>Target %</Typography.Text>
                <InputNumber min={0.1} step={0.1} style={{ width: "100%" }} value={targetPct} onChange={(value) => setTargetPct(value ?? 5.0)} />
              </Col>
              <Col xs={24} md={6}>
                <Typography.Text strong>Stop %</Typography.Text>
                <InputNumber min={0.1} step={0.1} style={{ width: "100%" }} value={stopPct} onChange={(value) => setStopPct(value ?? 2.0)} />
              </Col>
              <Col xs={24} md={6}>
                <Typography.Text strong>Position Size (INR)</Typography.Text>
                <InputNumber min={1000} step={1000} style={{ width: "100%" }} value={positionSizeInr} onChange={(value) => setPositionSizeInr(value ?? 200000)} />
              </Col>
            </Row>

            <Row gutter={12}>
              <Col xs={24} md={8}>
                <Typography.Text strong>Fee Per Trade (INR)</Typography.Text>
                <InputNumber min={0} step={50} style={{ width: "100%" }} value={feePerTradeInr} onChange={(value) => setFeePerTradeInr(value ?? 500)} />
              </Col>
              <Col xs={24} md={16}>
                <Typography.Text strong>Manual Symbols (optional)</Typography.Text>
                <Input.TextArea
                  rows={2}
                  placeholder="INFY, TCS, RELIANCE"
                  value={manualSymbolsText}
                  onChange={(event) => setManualSymbolsText(event.target.value)}
                />
              </Col>
            </Row>

            <Space>
              <Button type="primary" onClick={runBacktest} loading={loading}>Run Backtest</Button>
              <Typography.Text type="secondary">Manual symbols parsed: {manualSymbols.length}</Typography.Text>
            </Space>
          </Space>
        </Card>

        {error && <Alert type="error" showIcon message={error} />}

        {loading && (
          <div style={{ textAlign: "center", padding: 48 }}>
            <Spin size="large" />
          </div>
        )}

        {!loading && data && <VolumeSpikeResult result={data} />}

        {!loading && !data && !error && (
          <Card>
            <Empty description="Run a backtest to see results." />
          </Card>
        )}
      </Space>
    </div>
  );
}

function VolumeSpikeResult({ result }: { result: VolumeSpikeBacktestResponse }) {
  return (
    <Space orientation="vertical" size={16} style={{ width: "100%" }}>
      <Row gutter={12}>
        <Col><Card size="small"><Statistic title="Trades" value={result.summary.totalTrades} /></Card></Col>
        <Col><Card size="small"><Statistic title="Win Rate" value={result.summary.winRatePct.toFixed(2)} suffix="%" /></Card></Col>
        <Col><Card size="small"><Statistic title="Net P&L" value={result.summary.netPnlInr.toFixed(2)} prefix="₹" /></Card></Col>
        <Col><Card size="small"><Statistic title="Total Fees" value={result.summary.totalFeesInr.toFixed(2)} prefix="₹" /></Card></Col>
        <Col><Card size="small"><Statistic title="Max DD" value={result.summary.maxDrawdownInr.toFixed(2)} prefix="₹" /></Card></Col>
      </Row>

      <Card size="small" title="Diagnostics">
        <Space wrap size={20}>
          <Typography.Text>Cache Hits: {result.diagnostics.cacheHits}</Typography.Text>
          <Typography.Text>Cache Misses: {result.diagnostics.cacheMisses}</Typography.Text>
          <Typography.Text>No Data: {result.diagnostics.symbolsWithNoIntradayData.length}</Typography.Text>
          <Typography.Text>No Trades: {result.diagnostics.symbolsWithNoTrades.length}</Typography.Text>
          <Typography.Text>Kite Failures: {result.diagnostics.kiteFetchFailures.length}</Typography.Text>
        </Space>
      </Card>

      <Card size="small" title="Trades">
        <Table
          rowKey={(row) => `${row.symbol}-${row.entryTime}`}
          columns={tradeColumns}
          dataSource={result.trades}
          size="small"
          pagination={{ pageSize: 25, showSizeChanger: true }}
          scroll={{ x: 1500 }}
        />
      </Card>
    </Space>
  );
}
