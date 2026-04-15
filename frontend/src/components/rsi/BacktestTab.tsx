import { Alert, Button, Card, Col, DatePicker, Row, Select, Space, Spin, Statistic, Table, Tag, Typography, Switch, InputNumber, Checkbox, Input } from "antd";
import type { ColumnsType } from "antd/es/table";
import dayjs from "dayjs";
import { useState } from "react";
import { postJson } from "../../utils/api";
import { useRsiMomentumBacktest } from "../../hooks/useRsiMomentumBacktest";
import type { BacktestResult, StockTrade, StatefulBacktestConfig } from "../../types";

const { RangePicker } = DatePicker;

const fmt2 = (v: number | null | undefined) =>
  typeof v === "number" ? v.toFixed(2) : "—";

const fmtPct = (v: number | null | undefined) => {
  if (typeof v !== "number") return <span>—</span>;
  return (
    <Typography.Text style={{ color: v >= 0 ? "#3f8600" : "#cf1322" }}>
      {v >= 0 ? "+" : ""}{v.toFixed(2)}%
    </Typography.Text>
  );
};

const fmtPrice = (v: number | null | undefined) =>
  typeof v === "number" ? `₹${v.toFixed(2)}` : "—";

const columns: ColumnsType<StockTrade> = [
  {
    title: "Symbol",
    dataIndex: "symbol",
    key: "symbol",
    width: 130,
    fixed: "left",
    render: (sym: string, row: StockTrade) => (
      <Space direction="vertical" size={0}>
        <Typography.Text strong>{sym}</Typography.Text>
        <Typography.Text type="secondary" style={{ fontSize: 11 }}>
          {row.companyName}
        </Typography.Text>
      </Space>
    ),
  },
  {
    title: "Status",
    dataIndex: "status",
    key: "status",
    width: 80,
    filters: [
      { text: "Closed", value: "CLOSED" },
      { text: "Open", value: "OPEN" },
    ],
    onFilter: (value, row) => row.status === value,
    render: (status: string) => (
      <Tag color={status === "OPEN" ? "blue" : "default"}>{status}</Tag>
    ),
  },
  {
    title: "Entry Date",
    dataIndex: "entryDate",
    key: "entryDate",
    width: 105,
    sorter: (a, b) => a.entryDate.localeCompare(b.entryDate),
  },
  {
    title: "Entry Price",
    dataIndex: "entryPrice",
    key: "entryPrice",
    width: 105,
    render: fmtPrice,
  },
  {
    title: "Entry Rank",
    dataIndex: "entryRank",
    key: "entryRank",
    width: 90,
    sorter: (a, b) => a.entryRank - b.entryRank,
    render: (r: number) => `#${r}`,
  },
  {
    title: "Entry RSI",
    dataIndex: "entryAvgRsi",
    key: "entryAvgRsi",
    width: 90,
    render: fmt2,
  },
  {
    title: "Exit Date",
    dataIndex: "exitDate",
    key: "exitDate",
    width: 105,
    render: (v: string | null) => v ?? <Typography.Text type="secondary">still held</Typography.Text>,
  },
  {
    title: "Exit Price",
    dataIndex: "exitPrice",
    key: "exitPrice",
    width: 105,
    render: fmtPrice,
  },
  {
    title: "Exit Rank",
    dataIndex: "exitRank",
    key: "exitRank",
    width: 85,
    render: (r: number | null) => (r != null ? `#${r}` : "—"),
  },
  {
    title: "Exit RSI",
    dataIndex: "exitAvgRsi",
    key: "exitAvgRsi",
    width: 85,
    render: fmt2,
  },
  {
    title: "Days Held",
    dataIndex: "daysHeld",
    key: "daysHeld",
    width: 90,
    sorter: (a, b) => a.daysHeld - b.daysHeld,
  },
  {
    title: "Return",
    dataIndex: "returnPct",
    key: "returnPct",
    width: 100,
    sorter: (a, b) => (a.returnPct ?? 0) - (b.returnPct ?? 0),
    render: fmtPct,
  },
];

const TOP_N_OPTIONS = [
  { value: 0, label: "All holdings (Top 10)" },
  { value: 5, label: "Top 5 only" },
  { value: 3, label: "Top 3 only" },
];

export function BacktestTab({ profileId }: { profileId: string }) {
  const { data, loading, error, run } = useRsiMomentumBacktest();
  const [dateRange, setDateRange] = useState<[dayjs.Dayjs, dayjs.Dayjs] | null>(null);
  const [topN, setTopN] = useState<number>(0);
  
  // Stateful config states
  const [useStateful, setUseStateful] = useState(false);
  const [useJsonMode, setUseJsonMode] = useState(false);
  const [entryRankMax, setEntryRankMax] = useState(20);
  const [takeProfitRank, setTakeProfitRank] = useState(1);
  const [exitOnPeakLoss, setExitOnPeakLoss] = useState(true);
  const [giveUpRankMin, setGiveUpRankMin] = useState(25);
  const [rawJson, setRawJson] = useState("");

  const [preparing, setPreparing] = useState(false);
  const [prepareResult, setPrepareResult] = useState<string | null>(null);
  const [prepareError, setPrepareError] = useState<string | null>(null);

  const fromDate = dateRange?.[0].format("YYYY-MM-DD");
  const toDate = dateRange?.[1].format("YYYY-MM-DD");

  const handlePrepare = async () => {
    setPreparing(true);
    setPrepareResult(null);
    setPrepareError(null);
    try {
      const result = await postJson<{ message: string; datesProcessed: number; datesSkipped: number; datesFailed: number }>(
        "/api/strategy/rsi-momentum/backfill",
        { profileId, fromDate, toDate, skipExisting: true },
      );
      setPrepareResult(`${result.message}`);
    } catch (err) {
      setPrepareError(err instanceof Error ? err.message : "Prepare failed");
    } finally {
      setPreparing(false);
    }
  };

  const handleRun = () => {
    let statefulConfig: StatefulBacktestConfig | undefined = undefined;

    if (useStateful) {
      if (useJsonMode) {
        try {
          statefulConfig = JSON.parse(rawJson);
        } catch (e) {
          return alert("Invalid JSON format in configuration.");
        }
      } else {
        statefulConfig = {
          enabled: true,
          entryRankMax,
          takeProfitRank,
          exitOnTakeProfitLeave: exitOnPeakLoss,
          giveUpRankMin
        };
      }
    }

    void run({
      profileId,
      fromDate,
      toDate,
      topN: !useStateful && topN > 0 ? topN : undefined,
      statefulConfig
    });
  };

  const syncJsonFromForm = () => {
    const cfg = {
      enabled: true,
      entryRankMax,
      takeProfitRank,
      exitOnTakeProfitLeave: exitOnPeakLoss,
      giveUpRankMin
    };
    setRawJson(JSON.stringify(cfg, null, 2));
    setUseJsonMode(true);
  };

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <Card size="small" title="Configure Backtest">
        <Space direction="vertical" size={12} style={{ width: "100%" }}>
          <Space wrap align="center">
            <Typography.Text strong>Window:</Typography.Text>
            <RangePicker
              value={dateRange}
              onChange={(v) => setDateRange(v as [dayjs.Dayjs, dayjs.Dayjs] | null)}
              placeholder={["From (default: -3 months)", "To (default: today)"]}
            />
            <div style={{ marginLeft: 16 }}>
              <Typography.Text strong>Mode: </Typography.Text>
              <Select 
                value={useStateful ? "stateful" : "standard"} 
                onChange={(v) => setUseStateful(v === "stateful")}
                options={[
                  { label: "Standard (Top-N)", value: "standard" },
                  { label: "Stateful (Rank-Based)", value: "stateful" }
                ]}
                style={{ width: 180 }}
              />
            </div>
          </Space>

          {!useStateful ? (
            <Space wrap align="center">
              <Typography.Text strong>Selection:</Typography.Text>
              <Select
                value={topN}
                onChange={setTopN}
                options={TOP_N_OPTIONS}
                style={{ width: 200 }}
              />
            </Space>
          ) : (
            <Card 
              size="small" 
              styles={{ body: { background: "#fafafa" } }} 
              title={
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                  <span>Rank-Based Strategy Rules</span>
                  <Space>
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>JSON Mode</Typography.Text>
                    <Switch size="small" checked={useJsonMode} onChange={setUseJsonMode} />
                  </Space>
                </div>
              }
            >
              {!useJsonMode ? (
                <Row gutter={[24, 12]}>
                  <Col>
                    <Space direction="vertical" size={4}>
                      <Typography.Text type="secondary">{"Entry Rank Limit (≤)"}</Typography.Text>
                      <InputNumber min={1} max={40} value={entryRankMax} onChange={v => setEntryRankMax(v || 20)} />
                    </Space>
                  </Col>
                  <Col>
                    <Space direction="vertical" size={4}>
                      <Typography.Text type="secondary">Peak Rank Goal</Typography.Text>
                      <InputNumber min={1} max={10} value={takeProfitRank} onChange={v => setTakeProfitRank(v || 1)} />
                    </Space>
                  </Col>
                  <Col>
                    <Space direction="vertical" size={4}>
                      <Typography.Text type="secondary">{"Give Up Rank Threshold (>)"}</Typography.Text>
                      <InputNumber min={5} max={100} value={giveUpRankMin} onChange={v => setGiveUpRankMin(v || 25)} />
                    </Space>
                  </Col>
                  <Col style={{ alignSelf: "end", marginBottom: 8 }}>
                    <Checkbox checked={exitOnPeakLoss} onChange={e => setExitOnPeakLoss(e.target.checked)}>
                      Exit ONLY when leaving Peak Rank
                    </Checkbox>
                  </Col>
                  <Col style={{ alignSelf: "end", marginBottom: 8 }}>
                    <Button size="small" onClick={syncJsonFromForm}>View as JSON</Button>
                  </Col>
                </Row>
              ) : (
                <Space direction="vertical" style={{ width: "100%" }}>
                  <Typography.Text type="secondary">Paste strategy configuration JSON here (from rsi_backtest_configs.json):</Typography.Text>
                  <Input.TextArea 
                    rows={6} 
                    value={rawJson} 
                    onChange={e => setRawJson(e.target.value)}
                    placeholder='{ "enabled": true, "entryRankMax": 20, ... }'
                  />
                  <Button size="small" onClick={() => setUseJsonMode(false)}>Back to Form</Button>
                </Space>
              )}
            </Card>
          )}

          <Space wrap>
            <Button
              onClick={() => void handlePrepare()}
              loading={preparing}
              title="Scans daily_candles and computes RSI rankings for each missing trading day in the window"
            >
              Prepare Data
            </Button>
            <Button type="primary" onClick={handleRun} loading={loading}>
              Run Backtest
            </Button>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              Run "Prepare Data" first if this is a historical window with no data yet.
            </Typography.Text>
          </Space>
        </Space>
      </Card>

      {prepareResult && (
        <Alert type="success" showIcon message="Data prepared" description={prepareResult} closable onClose={() => setPrepareResult(null)} />
      )}
      {prepareError && (
        <Alert type="error" showIcon message={prepareError} closable onClose={() => setPrepareError(null)} />
      )}
      {error && <Alert type="error" showIcon message={error} />}

      {loading && (
        <div style={{ textAlign: "center", padding: 32 }}>
          <Spin />
        </div>
      )}

      {data && !loading && <BacktestResults result={data} />}

      {!data && !loading && !error && (
        <Card size="small">
          <Typography.Text type="secondary">
            Select a strategy mode and date window, then run the backtest to see trade results.
          </Typography.Text>
        </Card>
      )}
    </Space>
  );
}

function BacktestResults({ result }: { result: BacktestResult }) {
  const { summary } = result;
  const modeLabel = result.statefulConfig ? "Stateful (Rank-Based)" : (result.topN ? `Top ${result.topN}` : "All holdings");

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <Row gutter={[12, 12]}>
        <Col xs={12} md={8} xl={4}>
          <Card size="small">
            <Statistic title="Total Trades" value={summary.totalTrades} />
          </Card>
        </Col>
        <Col xs={12} md={8} xl={4}>
          <Card size="small">
            <Statistic title="Closed" value={summary.closedTrades} />
          </Card>
        </Col>
        <Col xs={12} md={8} xl={4}>
          <Card size="small">
            <Statistic title="Still Open" value={summary.openPositions} />
          </Card>
        </Col>
        <Col xs={12} md={8} xl={4}>
          <Card size="small">
            <Statistic
              title="Win Rate"
              value={summary.winRate != null ? summary.winRate.toFixed(1) : "—"}
              suffix={summary.winRate != null ? "%" : ""}
            />
          </Card>
        </Col>
        <Col xs={12} md={8} xl={4}>
          <Card size="small">
            <Statistic
              title="Avg Return"
              value={summary.avgReturnPct != null ? summary.avgReturnPct.toFixed(2) : "—"}
              suffix={summary.avgReturnPct != null ? "%" : ""}
              valueStyle={{ color: (summary.avgReturnPct ?? 0) >= 0 ? "#3f8600" : "#cf1322" }}
            />
          </Card>
        </Col>
        <Col xs={12} md={8} xl={4}>
          <Card size="small">
            <Statistic title="Avg Days Held" value={summary.avgDaysHeld.toFixed(1)} />
          </Card>
        </Col>
      </Row>

      <Card
        size="small"
        title={`Trade Journal — ${result.fromDate} to ${result.toDate} · Mode: ${modeLabel} · ${result.snapshotDaysUsed} snapshot days`}
      >
        <Table
          columns={columns}
          dataSource={result.trades}
          rowKey={(row) => `${row.symbol}-${row.entryDate}`}
          size="small"
          pagination={{ pageSize: 25, showSizeChanger: true }}
          scroll={{ x: 1200 }}
          rowClassName={(row) => row.status === "OPEN" ? "ant-table-row-selected" : ""}
        />
      </Card>
    </Space>
  );
}
