import { Alert, Button, Card, Col, DatePicker, Input, InputNumber, Row, Select, Space, Spin, Statistic, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import dayjs from "dayjs";
import { useMemo, useState } from "react";
import { useIntradayShockBacktest } from "../hooks/useIntradayShockBacktest";
import type { IntradayShockBacktestRequest, IntradayShockBacktestTrade } from "../types";

const { RangePicker } = DatePicker;

const tradeColumns: ColumnsType<IntradayShockBacktestTrade> = [
  { title: "Symbol", dataIndex: "symbol", key: "symbol", width: 100, fixed: "left" },
  { title: "Entry Time", dataIndex: "entryTime", key: "entryTime", width: 165 },
  { title: "Exit Time", dataIndex: "exitTime", key: "exitTime", width: 165 },
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
  { title: "Gap Up %", dataIndex: "gapUpPct", key: "gapUpPct", width: 90, render: (value: number) => `${value.toFixed(2)}%` },
  { title: "9:15-9:30 Vol", dataIndex: "morningVolume", key: "morningVolume", width: 120, render: (value: number) => value.toLocaleString("en-IN", { maximumFractionDigits: 0 }) },
  { title: "Max Daily 60d", dataIndex: "maxDailyVolume60d", key: "maxDailyVolume60d", width: 120, render: (value: number) => value.toLocaleString("en-IN", { maximumFractionDigits: 0 }) },
  { title: "Net P&L", dataIndex: "netPnlInr", key: "netPnlInr", width: 120, render: (value: number) => `₹${value.toFixed(2)}` },
  { title: "Net %", dataIndex: "netReturnPct", key: "netReturnPct", width: 90, render: (value: number) => `${value.toFixed(2)}%` },
];

const universeOptions = [
  { label: "NIFTY 50", value: "NIFTY 50" },
  { label: "NIFTY NEXT 50", value: "NIFTY NEXT 50" },
  { label: "NIFTY 100", value: "NIFTY 100" },
  { label: "NIFTY MIDCAP 50", value: "NIFTY MIDCAP 50" },
  { label: "NIFTY MIDCAP 150", value: "NIFTY MIDCAP 150" },
  { label: "NIFTY SMALLCAP 50", value: "NIFTY SMALLCAP 50" },
  { label: "NIFTY SMALLCAP 250", value: "NIFTY SMALLCAP 250" },
  { label: "NIFTY MICROCAP 250", value: "NIFTY MICROCAP 250" },
  { label: "NIFTY 500", value: "NIFTY 500" },
  { label: "ALL NSE", value: "" },
];

export function IntradayShockBacktestPage() {
  const { data, loading, error, run } = useIntradayShockBacktest();

  const [dateRange, setDateRange] = useState<[dayjs.Dayjs, dayjs.Dayjs]>([
    dayjs().subtract(1, "month"),
    dayjs(),
  ]);
  const [universe, setUniverse] = useState<string>("NIFTY 50");
  const [manualSymbolsText, setManualSymbolsText] = useState<string>("");
  const [scanEndMinutes, setScanEndMinutes] = useState<number>(15);
  const [entryDelayMinutes, setEntryDelayMinutes] = useState<number>(30);
  const [gapUpTolerancePct, setGapUpTolerancePct] = useState<number>(5.0);
  const [targetPct, setTargetPct] = useState<number>(5.0);
  const [hardStopPct, setHardStopPct] = useState<number>(3.0);
  const [minTurnover, setMinTurnover] = useState<number>(5000000);
  const [minVolumeSma, setMinVolumeSma] = useState<number>(100000);
  const [positionSizeInr, setPositionSizeInr] = useState<number>(50000);
  const [feePerTradeInr, setFeePerTradeInr] = useState<number>(40);

  const manualSymbols = useMemo(() => {
    return manualSymbolsText
      .split(/[\n,\s]+/)
      .map((value) => value.trim().toUpperCase())
      .filter((value) => value.length > 0);
  }, [manualSymbolsText]);

  const runBacktest = (): void => {
    const request: IntradayShockBacktestRequest = {
      fromDate: dateRange[0].format("YYYY-MM-DD"),
      toDate: dateRange[1].format("YYYY-MM-DD"),
      universe: universe,
      manualSymbols: manualSymbols.length > 0 ? manualSymbols : undefined,
      scanEndMinutes,
      entryDelayMinutes,
      gapUpTolerancePct,
      targetPct,
      hardStopPct,
      minTurnover,
      minVolumeSma,
      positionSizeInr,
      feePerTradeInr,
    };

    void run(request);
  };

  return (
    <div style={{ padding: 24, background: "#f5f7fa", minHeight: "calc(100vh - 48px)" }}>
      <Space orientation="vertical" size={16} style={{ width: "100%" }}>
        <div>
          <Typography.Title level={4} style={{ margin: 0 }}>Intraday Volume Shock Backtest</Typography.Title>
          <Typography.Text type="secondary">
            Scans morning minutes (e.g. 9:15-9:30) for extreme volume that eclipses historical daily maximums. 
            Trades are executed after a slight delay with target and stop logic.
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
              <Col xs={24} md={7}>
                <Typography.Text strong>Universe (Index)</Typography.Text>
                <Select
                  style={{ width: "100%" }}
                  value={universe}
                  options={universeOptions}
                  onChange={(value) => setUniverse(value)}
                  allowClear
                />
              </Col>
              <Col xs={24} md={7}>
                <Typography.Text strong>Manual Symbols (Overrides Universe)</Typography.Text>
                <Input.TextArea
                  rows={1}
                  placeholder="INFY, TCS, RELIANCE"
                  value={manualSymbolsText}
                  onChange={(event) => setManualSymbolsText(event.target.value)}
                />
              </Col>
            </Row>

            <Row gutter={12}>
              <Col xs={24} md={6}>
                <Typography.Text strong>Scan End Mins (from 9:15)</Typography.Text>
                <InputNumber min={5} step={5} style={{ width: "100%" }} value={scanEndMinutes} onChange={(value) => setScanEndMinutes(value ?? 15)} />
              </Col>
              <Col xs={24} md={6}>
                <Typography.Text strong>Entry Delay Mins (from 9:15)</Typography.Text>
                <InputNumber min={scanEndMinutes} step={5} style={{ width: "100%" }} value={entryDelayMinutes} onChange={(value) => setEntryDelayMinutes(value ?? 30)} />
              </Col>
              <Col xs={24} md={6}>
                <Typography.Text strong>Max Gap Up %</Typography.Text>
                <InputNumber min={0.1} step={0.5} style={{ width: "100%" }} value={gapUpTolerancePct} onChange={(value) => setGapUpTolerancePct(value ?? 5.0)} />
              </Col>
              <Col xs={24} md={6}>
                <Typography.Text strong>Min Volume SMA 60d</Typography.Text>
                <InputNumber min={10000} step={50000} style={{ width: "100%" }} value={minVolumeSma} onChange={(value) => setMinVolumeSma(value ?? 100000)} />
              </Col>
            </Row>

            <Row gutter={12}>
              <Col xs={24} md={6}>
                <Typography.Text strong>Target %</Typography.Text>
                <InputNumber min={0.1} step={0.5} style={{ width: "100%" }} value={targetPct} onChange={(value) => setTargetPct(value ?? 5.0)} />
              </Col>
              <Col xs={24} md={6}>
                <Typography.Text strong>Hard Stop %</Typography.Text>
                <InputNumber min={0.1} step={0.5} style={{ width: "100%" }} value={hardStopPct} onChange={(value) => setHardStopPct(value ?? 3.0)} />
              </Col>
              <Col xs={24} md={6}>
                <Typography.Text strong>Position Size (INR)</Typography.Text>
                <InputNumber min={1000} step={10000} style={{ width: "100%" }} value={positionSizeInr} onChange={(value) => setPositionSizeInr(value ?? 50000)} />
              </Col>
              <Col xs={24} md={6}>
                <Typography.Text strong>Fee Per Trade (INR)</Typography.Text>
                <InputNumber min={0} step={10} style={{ width: "100%" }} value={feePerTradeInr} onChange={(value) => setFeePerTradeInr(value ?? 40)} />
              </Col>
            </Row>

            <Button type="primary" onClick={runBacktest} loading={loading} disabled={loading} style={{ marginTop: 8 }}>
              Run Backtest
            </Button>
          </Space>
        </Card>

        {error && <Alert type="error" message={error.message} showIcon />}

        {loading ? (
          <div style={{ textAlign: "center", padding: 48 }}>
            <Spin size="large" />
            <div style={{ marginTop: 16 }}>Running backtest across the requested universe... This may take a while.</div>
          </div>
        ) : data ? (
          <Space orientation="vertical" size={16} style={{ width: "100%" }}>
            <Row gutter={16}>
              <Col span={8}>
                <Card size="small">
                  <Statistic title="Total Signals" value={data.trades.length} />
                </Card>
              </Col>
              <Col span={8}>
                <Card size="small">
                  <Statistic
                    title="Targets Hit"
                    value={data.trades.filter((t) => t.exitReason === "TARGET_HIT").length}
                    valueStyle={{ color: "#3f8600" }}
                  />
                </Card>
              </Col>
              <Col span={8}>
                <Card size="small">
                  <Statistic
                    title="Stops Hit"
                    value={data.trades.filter((t) => t.exitReason === "STOP_HIT").length}
                    valueStyle={{ color: "#cf1322" }}
                  />
                </Card>
              </Col>
            </Row>
            
            <Card size="small" bodyStyle={{ padding: 0 }}>
              <Table
                dataSource={data.trades}
                columns={tradeColumns}
                rowKey={(record) => `${record.symbol}-${record.entryTime}`}
                size="small"
                pagination={false}
                scroll={{ x: 1300, y: 600 }}
              />
            </Card>
          </Space>
        ) : null}
      </Space>
    </div>
  );
}
