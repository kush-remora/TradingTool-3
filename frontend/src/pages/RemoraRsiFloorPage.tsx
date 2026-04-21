import { Alert, Button, Card, Col, Row, Select, Space, Statistic, Table, Tag, Typography, message } from "antd";
import type { TableColumnsType } from "antd";
import { DeleteOutlined, DownloadOutlined, SearchOutlined } from "@ant-design/icons";
import { useMemo, useState } from "react";
import { postJson } from "../utils/api";
import type { RsiFloorScannerRequest, RsiFloorScannerResult, RsiFloorScannerRow } from "../types";

const { Title, Text } = Typography;

type UniverseOption = {
  value: string;
  label: string;
};

const UNIVERSE_OPTIONS: UniverseOption[] = [
  { value: "ALL_NSE", label: "ALL NSE (EQ)" },
  { value: "GROWW_WATCHLIST", label: "Groww Watchlist" },
  { value: "ALL_CUSTOM_UNIVERSE", label: "ALL Custom Universe" },
  { value: "NIFTY_100", label: "Nifty 100" },
  { value: "NIFTY_LARGEMIDCAP_250", label: "Nifty LargeMidcap 250" },
  { value: "NIFTY_MIDCAP_250", label: "Nifty Midcap 250" },
  { value: "NIFTY_SMALLCAP_250", label: "Nifty Smallcap 250" },
  { value: "NIFTY_500", label: "Nifty 500" },
  { value: "NIFTY_50", label: "Nifty 50" },
  { value: "WATCHLIST", label: "Watchlist" },
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
  return "UNKNOWN";
}

export function RemoraRsiFloorPage() {
  const [messageApi, contextHolder] = message.useMessage();
  const [universe, setUniverse] = useState<string>("ALL_CUSTOM_UNIVERSE");
  const [loading, setLoading] = useState<boolean>(false);
  const [result, setResult] = useState<RsiFloorScannerResult | null>(null);
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
                  Matches if any of last 14 trading sessions met RSI floor condition.
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
      </Space>
    </div>
  );
}
