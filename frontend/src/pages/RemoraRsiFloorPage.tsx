import { Alert, Button, Card, Col, Row, Select, Space, Statistic, Table, Tag, Typography, message } from "antd";
import type { TableColumnsType } from "antd";
import { DeleteOutlined, DownloadOutlined, SearchOutlined } from "@ant-design/icons";
import { useMemo, useState } from "react";
import { postJson } from "../utils/api";
import type {
  DeliverySurgeConfirmationRow,
  RemoraRsiFloorChainedResult,
  RsiFloorScannerRequest,
  RsiFloorScannerResult,
  RsiFloorScannerRow,
} from "../types";

const { Title, Text } = Typography;

type UniverseOption = {
  value: string;
  label: string;
};

const UNIVERSE_OPTIONS: UniverseOption[] = [
  { value: "WATCHLIST", label: "Watchlist" },
  { value: "ALL_NSE", label: "ALL NSE (EQ)" },
  { value: "ALL_CUSTOM_UNIVERSE", label: "ALL Custom Universe" },
  { value: "NIFTY_100", label: "Nifty 100" },
  { value: "NIFTY_LARGEMIDCAP_250", label: "Nifty LargeMidcap 250" },
  { value: "NIFTY_MIDCAP_250", label: "Nifty Midcap 250" },
  { value: "NIFTY_SMALLCAP_250", label: "Nifty Smallcap 250" },
  { value: "NIFTY_500", label: "Nifty 500" },
  { value: "NIFTY_50", label: "Nifty 50" },
];

function formatNumber(value: number | null, fractionDigits = 2): string {
  if (value === null) return "-";
  return value.toLocaleString("en-IN", {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  });
}

function resolveMatchType(row: RsiFloorScannerRow): string {
  if (row.matchedByYearLow && row.matchedByHardLimit) return "BOTH";
  if (row.matchedByYearLow) return "YEAR_LOW";
  if (row.matchedByHardLimit) return "RSI_20";
  return "NEAR_52W_LOW";
}

export function RemoraRsiFloorPage() {
  const [messageApi, contextHolder] = message.useMessage();
  const [universe, setUniverse] = useState<string>("WATCHLIST");
  const [loading, setLoading] = useState<boolean>(false);
  const [result, setResult] = useState<RsiFloorScannerResult | null>(null);
  const [remoraResult, setRemoraResult] = useState<RemoraRsiFloorChainedResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  const rows = result?.rows ?? [];

  const symbolFilters = useMemo(() => {
    return rows.slice(0, 500).map((row) => ({ text: row.symbol, value: row.symbol }));
  }, [rows]);

  const runScan = async (freshScan: boolean): Promise<void> => {
    setLoading(true);
    setError(null);
    const key = freshScan ? "remora-rsi-floor-fresh" : "remora-rsi-floor-scan";
    messageApi.open({
      key,
      type: "loading",
      content: freshScan ? "Deleting Redis cache and scanning..." : "Running Remora RSI floor scan...",
      duration: 0,
    });

    try {
      const payload: RsiFloorScannerRequest = {
        universe,
        freshScan,
      };
      const response = await postJson<RsiFloorScannerResult>("/api/screener/remora-rsi-floor/scan", payload);
      setResult(response);
      setRemoraResult(null);
      messageApi.open({
        key,
        type: "success",
        content: `Scan completed. Matched ${response.matchedCount} stocks.`,
        duration: 3,
      });
    } catch (scanError: unknown) {
      const errorMessage = scanError instanceof Error ? scanError.message : "Scan failed";
      setError(errorMessage);
      messageApi.open({
        key,
        type: "error",
        content: errorMessage,
        duration: 4,
      });
    } finally {
      setLoading(false);
    }
  };

  const runRemoraOnFiltered = async (): Promise<void> => {
    setLoading(true);
    setError(null);
    const key = "remora-rsi-floor-remora-confirm";
    messageApi.open({
      key,
      type: "loading",
      content: "Running RSI filter and delivery-surge confirmation...",
      duration: 0,
    });

    try {
      const payload: RsiFloorScannerRequest = {
        universe,
      };
      const response = await postJson<RemoraRsiFloorChainedResult>(
        "/api/screener/remora-rsi-floor/remora-confirm",
        payload,
      );
      setResult(response.rsiResult);
      setRemoraResult(response);
      messageApi.open({
        key,
        type: "success",
        content: `Delivery confirmed ${response.deliveryConfirmedCount} of ${response.deliveryRequestedSymbols} RSI-filtered stocks.`,
        duration: 4,
      });
    } catch (scanError: unknown) {
      const errorMessage = scanError instanceof Error ? scanError.message : "Remora confirmation failed";
      setError(errorMessage);
      messageApi.open({
        key,
        type: "error",
        content: errorMessage,
        duration: 4,
      });
    } finally {
      setLoading(false);
    }
  };

  const exportJson = (): void => {
    if (!result) return;
    const fileName = `remora-rsi-floor-${result.universe.toLowerCase()}-${result.runAt.slice(0, 10)}.json`;
    const blob = new Blob([JSON.stringify(result, null, 2)], { type: "application/json;charset=utf-8" });
    const objectUrl = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = objectUrl;
    link.download = fileName;
    link.click();
    URL.revokeObjectURL(objectUrl);
  };

  const columns: TableColumnsType<RsiFloorScannerRow> = [
    {
      title: "Symbol",
      dataIndex: "symbol",
      key: "symbol",
      width: 150,
      sorter: (a, b) => a.symbol.localeCompare(b.symbol),
      filters: symbolFilters,
      onFilter: (value, record) => record.symbol === value,
      filterSearch: true,
      fixed: "left",
      render: (_value, row) => (
        <Space direction="vertical" size={0}>
          <Text strong>{row.symbol}</Text>
          <Text type="secondary" style={{ fontSize: 11 }}>{row.companyName}</Text>
        </Space>
      ),
    },
    {
      title: "Cap Bucket",
      dataIndex: "capBucket",
      key: "capBucket",
      width: 120,
      filters: [
        { text: "Large", value: "LARGE" },
        { text: "Mid", value: "MID" },
        { text: "Small", value: "SMALL" },
        { text: "Unknown", value: "UNKNOWN" },
      ],
      onFilter: (value, row) => row.capBucket === value,
      render: (value) => <Tag>{value}</Tag>,
    },
    {
      title: "Match Type",
      key: "matchType",
      width: 120,
      filters: [
        { text: "Both", value: "BOTH" },
        { text: "Year Low", value: "YEAR_LOW" },
        { text: "RSI <= 20", value: "RSI_20" },
        { text: "Near 52W Low", value: "NEAR_52W_LOW" },
      ],
      onFilter: (value, row) => resolveMatchType(row) === value,
      render: (_value, row) => <Tag color="blue">{resolveMatchType(row)}</Tag>,
    },
    {
      title: "Match Date",
      dataIndex: "matchedDate",
      key: "matchedDate",
      width: 120,
      sorter: (a, b) => a.matchedDate.localeCompare(b.matchedDate),
    },
    {
      title: "RSI(14)",
      dataIndex: "currentRsi",
      key: "currentRsi",
      width: 100,
      defaultSortOrder: "ascend",
      sorter: (a, b) => a.currentRsi - b.currentRsi,
      render: (value) => formatNumber(value, 2),
    },
    {
      title: "1Y RSI Low",
      dataIndex: "yearLowRsiAtMatchedDay",
      key: "yearLowRsiAtMatchedDay",
      width: 110,
      sorter: (a, b) => a.yearLowRsiAtMatchedDay - b.yearLowRsiAtMatchedDay,
      render: (value) => formatNumber(value, 2),
    },
    {
      title: "LTP",
      dataIndex: "ltp",
      key: "ltp",
      width: 110,
      sorter: (a, b) => (a.ltp ?? -1) - (b.ltp ?? -1),
      render: (value) => (value == null ? "-" : `₹${formatNumber(value, 2)}`),
    },
    {
      title: "Drawdown %",
      dataIndex: "drawdownPct",
      key: "drawdownPct",
      width: 120,
      sorter: (a, b) => (a.drawdownPct ?? 0) - (b.drawdownPct ?? 0),
      render: (value) => (value == null ? "-" : `${formatNumber(value, 2)}%`),
    },
    {
      title: "52W High",
      dataIndex: "high52w",
      key: "high52w",
      width: 110,
      sorter: (a, b) => (a.high52w ?? 0) - (b.high52w ?? 0),
      render: (value) => (value == null ? "-" : `₹${formatNumber(value, 2)}`),
    },
    {
      title: "52W Low",
      dataIndex: "low52w",
      key: "low52w",
      width: 110,
      sorter: (a, b) => (a.low52w ?? 0) - (b.low52w ?? 0),
      render: (value) => (value == null ? "-" : `₹${formatNumber(value, 2)}`),
    },
    {
      title: "Market Cap (Cr)",
      dataIndex: "marketCapCr",
      key: "marketCapCr",
      width: 140,
      sorter: (a, b) => (a.marketCapCr ?? -1) - (b.marketCapCr ?? -1),
      render: (value) => (value == null ? "-" : formatNumber(value, 0)),
    },
  ];

  const remoraColumns: TableColumnsType<DeliverySurgeConfirmationRow> = [
    {
      title: "Symbol",
      dataIndex: "symbol",
      key: "symbol",
      width: 150,
      sorter: (a, b) => a.symbol.localeCompare(b.symbol),
      render: (_value, row) => (
        <Space direction="vertical" size={0}>
          <Text strong>{row.symbol}</Text>
          <Text type="secondary" style={{ fontSize: 11 }}>{row.companyName}</Text>
        </Space>
      ),
    },
    {
      title: "History",
      dataIndex: "insufficientHistory",
      key: "insufficientHistory",
      width: 120,
      filters: [
        { text: "Ready", value: "READY" },
        { text: "Insufficient", value: "INSUFFICIENT" },
      ],
      onFilter: (value, row) => {
        const status = row.insufficientHistory ? "INSUFFICIENT" : "READY";
        return status === value;
      },
      render: (_value, row) => (
        <Space direction="vertical" size={0}>
          <Tag color={row.insufficientHistory ? "orange" : "green"}>{row.insufficientHistory ? "INSUFFICIENT" : "READY"}</Tag>
          <Text type="secondary" style={{ fontSize: 11 }}>
            {`${row.recentDaysUsed}d recent / ${row.baselineDaysUsed}d base`}
          </Text>
        </Space>
      ),
    },
    {
      title: "Latest Delivery Date",
      dataIndex: "latestTradingDate",
      key: "latestTradingDate",
      width: 120,
      sorter: (a, b) => (a.latestTradingDate ?? "").localeCompare(b.latestTradingDate ?? ""),
    },
    {
      title: "Avg Delivered Qty (20d)",
      dataIndex: "avgDeliveredQty20d",
      key: "avgDeliveredQty20d",
      width: 170,
      sorter: (a, b) => (a.avgDeliveredQty20d ?? 0) - (b.avgDeliveredQty20d ?? 0),
      render: (value) => (value == null ? "-" : formatNumber(value, 0)),
    },
    {
      title: "Latest Delivery Surge %",
      dataIndex: "latestDeliverySurgePct",
      key: "latestDeliverySurgePct",
      width: 170,
      sorter: (a, b) => (a.latestDeliverySurgePct ?? -9999) - (b.latestDeliverySurgePct ?? -9999),
      render: (value) => (value == null ? "-" : `${formatNumber(value, 2)}%`),
    },
    {
      title: "Max Delivery Surge % (7d)",
      dataIndex: "maxDeliverySurgePct7d",
      key: "maxDeliverySurgePct7d",
      width: 180,
      sorter: (a, b) => (a.maxDeliverySurgePct7d ?? -9999) - (b.maxDeliverySurgePct7d ?? -9999),
      render: (value) => (value == null ? "-" : `${formatNumber(value, 2)}%`),
    },
    {
      title: "Surge Days (7d)",
      dataIndex: "surgeDays7d",
      key: "surgeDays7d",
      width: 130,
      sorter: (a, b) => a.surgeDays7d - b.surgeDays7d,
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      {contextHolder}
      <Space direction="vertical" size={16} style={{ width: "100%" }}>
        <Card bordered={false}>
          <Row gutter={[16, 16]} align="middle" justify="space-between">
            <Col>
              <Space direction="vertical" size={2}>
                <Title level={4} style={{ margin: 0 }}>Remora RSI Floor Scanner</Title>
                <Text type="secondary">
                  Matches if last 14 sessions include RSI floor or daily low within 3% of 52W/available-history low.
                </Text>
              </Space>
            </Col>
            <Col>
              <Space wrap>
                <Select
                  value={universe}
                  onChange={setUniverse}
                  style={{ width: 260 }}
                  options={UNIVERSE_OPTIONS}
                  disabled={loading}
                />
                <Button
                  type="primary"
                  icon={<SearchOutlined />}
                  loading={loading}
                  onClick={() => runScan(false)}
                >
                  Scan
                </Button>
                <Button
                  danger
                  icon={<DeleteOutlined />}
                  loading={loading}
                  onClick={() => runScan(true)}
                >
                  Delete Redis + Scan Fresh
                </Button>
                <Button
                  loading={loading}
                  onClick={runRemoraOnFiltered}
                >
                  Run Delivery Surge On Filtered
                </Button>
                <Button
                  icon={<DownloadOutlined />}
                  disabled={!result}
                  onClick={exportJson}
                >
                  Export JSON
                </Button>
              </Space>
            </Col>
          </Row>
        </Card>

        {error && <Alert type="error" showIcon message={error} />}

        {result && (
          <Row gutter={16}>
            <Col>
              <Statistic title="Matched" value={result.matchedCount} />
            </Col>
            <Col>
              <Statistic title="Scanned" value={result.scannedSymbols} />
            </Col>
            <Col>
              <Statistic title="Requested" value={result.requestedSymbols} />
            </Col>
            <Col>
              <Statistic title="Skipped (Insufficient)" value={result.skippedInsufficientHistory} />
            </Col>
            <Col>
              <Statistic title="Source" value={result.source} />
            </Col>
          </Row>
        )}

        <Table<RsiFloorScannerRow>
          rowKey={(row) => row.symbol}
          columns={columns}
          dataSource={rows}
          loading={loading}
          size="small"
          pagination={{ pageSize: 100, showSizeChanger: true }}
          scroll={{ x: "max-content" }}
          locale={{
            emptyText: "Run scan to view RSI floor matches.",
          }}
        />

        <Card bordered={false}>
          <Space direction="vertical" size={12} style={{ width: "100%" }}>
            <Space align="center">
              <Title level={5} style={{ margin: 0 }}>Delivery Surge Confirmations (from RSI filtered set)</Title>
              {remoraResult && (
                <Text type="secondary">
                  Confirmed {remoraResult.deliveryConfirmedCount} of {remoraResult.deliveryRequestedSymbols}
                </Text>
              )}
            </Space>
            <Table<DeliverySurgeConfirmationRow>
              rowKey={(row) => `${row.symbol}-${row.latestTradingDate ?? "na"}`}
              columns={remoraColumns}
              dataSource={remoraResult?.deliveryConfirmedRows ?? []}
              loading={loading}
              size="small"
              pagination={{ pageSize: 50, showSizeChanger: true }}
              scroll={{ x: "max-content" }}
              locale={{
                emptyText: "Run 'Delivery Surge On Filtered' to see delivery confirmation on RSI-filtered stocks.",
              }}
            />
          </Space>
        </Card>
      </Space>
    </div>
  );
}
