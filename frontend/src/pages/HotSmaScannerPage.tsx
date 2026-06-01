import { useEffect, useMemo, useState } from "react";
import { Alert, Button, Card, Col, Empty, Radio, Row, Select, Space, Spin, Statistic, Table, Tag, Typography, message } from "antd";
import type { ColumnsType } from "antd/es/table";
import { DownloadOutlined, SearchOutlined } from "@ant-design/icons";
import { getJson } from "../utils/api";
import { useHotSmaScanner } from "../hooks/useHotSmaScanner";
import type { HotSmaRow, HotSmaSignalTag, UniverseOptionsResponse } from "../types";

const { Title, Text } = Typography;

type SignalFilter = "ALL" | HotSmaSignalTag;
type SortMode = "SIGNAL_PRIORITY" | "NEAREST_SMA100" | "NEAREST_SMA200" | "RSI_ASC";

const SIGNAL_RANK: Record<HotSmaSignalTag, number> = {
  AGGRESSIVE_BUY: 0,
  STANDARD_BUY: 1,
  WATCH_ZONE: 2,
};

const SIGNAL_LABEL: Record<HotSmaSignalTag, string> = {
  AGGRESSIVE_BUY: "Aggressive Buy",
  STANDARD_BUY: "Standard Buy",
  WATCH_ZONE: "Watch Zone",
};

const SIGNAL_COLOR: Record<HotSmaSignalTag, string> = {
  AGGRESSIVE_BUY: "red",
  STANDARD_BUY: "green",
  WATCH_ZONE: "gold",
};

function formatNumber(value: number | null | undefined, fractionDigits = 2): string {
  if (value == null) return "-";
  return value.toLocaleString("en-IN", {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  });
}

function csvEscape(value: string): string {
  return `"${value.replace(/"/g, '""')}"`;
}

function toCsv(rows: HotSmaRow[], indexKey: string): string {
  const header = [
    "scan_time",
    "index_key",
    "symbol",
    "signal_tag",
    "current_price",
    "sma50",
    "sma100",
    "sma200",
    "pct_to_sma50",
    "pct_to_sma100",
    "pct_to_sma200",
    "rsi14",
    "latest_date",
    "sma100_touch_date",
    "sma200_touch_date",
  ];

  const scanTime = new Date().toISOString();
  const lines = rows.map((row) => [
    scanTime,
    indexKey,
    row.symbol,
    row.signalTag,
    row.currentPrice.toFixed(2),
    row.sma50 == null ? "" : row.sma50.toFixed(2),
    row.sma100 == null ? "" : row.sma100.toFixed(2),
    row.sma200 == null ? "" : row.sma200.toFixed(2),
    row.pctToSma50 == null ? "" : row.pctToSma50.toFixed(2),
    row.pctToSma100 == null ? "" : row.pctToSma100.toFixed(2),
    row.pctToSma200 == null ? "" : row.pctToSma200.toFixed(2),
    row.rsi14 == null ? "" : row.rsi14.toFixed(2),
    row.latestDate,
    row.sma100TouchDate ?? "",
    row.sma200TouchDate ?? "",
  ].map((value) => csvEscape(String(value))).join(","));

  return [header.join(","), ...lines].join("\n");
}

function downloadCsv(filename: string, csv: string): void {
  const blob = new Blob([csv], { type: "text/csv;charset=utf-8;" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

function sortRows(rows: HotSmaRow[], mode: SortMode): HotSmaRow[] {
  const copy = [...rows];

  const safeAbs = (value: number | null | undefined): number => (value == null ? Number.POSITIVE_INFINITY : Math.abs(value));

  if (mode === "NEAREST_SMA100") {
    return copy.sort((a, b) => safeAbs(a.pctToSma100) - safeAbs(b.pctToSma100));
  }

  if (mode === "NEAREST_SMA200") {
    return copy.sort((a, b) => safeAbs(a.pctToSma200) - safeAbs(b.pctToSma200));
  }

  if (mode === "RSI_ASC") {
    return copy.sort((a, b) => {
      const left = a.rsi14 ?? Number.POSITIVE_INFINITY;
      const right = b.rsi14 ?? Number.POSITIVE_INFINITY;
      return left - right;
    });
  }

  return copy.sort((a, b) => {
    const rankCompare = SIGNAL_RANK[a.signalTag] - SIGNAL_RANK[b.signalTag];
    if (rankCompare !== 0) return rankCompare;

    if (a.signalTag === "STANDARD_BUY") {
      return safeAbs(a.pctToSma100) - safeAbs(b.pctToSma100);
    }

    return safeAbs(a.pctToSma200) - safeAbs(b.pctToSma200);
  });
}

export function HotSmaScannerPage() {
  const [messageApi, contextHolder] = message.useMessage();
  const { data, loading, error, run, sendTelegramForRow } = useHotSmaScanner();

  const [universeLoading, setUniverseLoading] = useState<boolean>(false);
  const [universeOptions, setUniverseOptions] = useState<Array<{ label: string; value: string }>>([]);
  const [selectedIndexKey, setSelectedIndexKey] = useState<string>("");
  const [signalFilter, setSignalFilter] = useState<SignalFilter>("ALL");
  const [sortMode, setSortMode] = useState<SortMode>("SIGNAL_PRIORITY");
  const [sendingSymbol, setSendingSymbol] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;
    setUniverseLoading(true);

    void getJson<UniverseOptionsResponse>("/api/strategy/hot-sma/universes")
      .then((response) => {
        if (!mounted) return;
        const options = response.options.map((option) => ({
          label: `${option.value} (${option.count})`,
          value: option.value,
        }));
        setUniverseOptions(options);
        setSelectedIndexKey(options[0]?.value ?? "");
      })
      .finally(() => {
        if (mounted) setUniverseLoading(false);
      });

    return () => {
      mounted = false;
    };
  }, []);

  const visibleRows = useMemo(() => {
    const source = data?.rows ?? [];
    const filtered = signalFilter === "ALL" ? source : source.filter((row) => row.signalTag === signalFilter);
    return sortRows(filtered, sortMode);
  }, [data, signalFilter, sortMode]);

  const handleRun = async (): Promise<void> => {
    if (!selectedIndexKey) {
      messageApi.warning("Select an index key.");
      return;
    }
    await run({ indexKey: selectedIndexKey });
  };

  const handleSendTelegram = async (row: HotSmaRow): Promise<void> => {
    setSendingSymbol(row.symbol);
    try {
      await sendTelegramForRow(selectedIndexKey, row);
      messageApi.success(`Sent ${row.symbol} to Telegram`);
    } catch (err) {
      messageApi.error(err instanceof Error ? err.message : "Telegram send failed");
    } finally {
      setSendingSymbol(null);
    }
  };

  const exportCsv = (): void => {
    if (visibleRows.length === 0 || !selectedIndexKey) {
      messageApi.info("No rows to export.");
      return;
    }
    const csv = toCsv(visibleRows, selectedIndexKey);
    const fileDate = new Date().toISOString().slice(0, 10);
    downloadCsv(`hot-sma-${selectedIndexKey}-${fileDate}.csv`, csv);
    messageApi.success("CSV export started");
  };

  const columns: ColumnsType<HotSmaRow> = [
    {
      title: "Symbol",
      dataIndex: "symbol",
      key: "symbol",
      fixed: "left",
      width: 130,
      sorter: (a, b) => a.symbol.localeCompare(b.symbol),
      render: (_value, row) => (
        <Space orientation="vertical" size={0}>
          <Text strong>{row.symbol}</Text>
          <Text type="secondary" style={{ fontSize: 11 }}>{row.companyName}</Text>
        </Space>
      ),
    },
    {
      title: "Signal",
      dataIndex: "signalTag",
      key: "signalTag",
      width: 140,
      render: (value: HotSmaSignalTag) => <Tag color={SIGNAL_COLOR[value]}>{SIGNAL_LABEL[value]}</Tag>,
    },
    {
      title: "Price",
      dataIndex: "currentPrice",
      key: "currentPrice",
      width: 110,
      sorter: (a, b) => a.currentPrice - b.currentPrice,
      render: (value: number) => formatNumber(value),
    },
    {
      title: "SMA50",
      dataIndex: "sma50",
      key: "sma50",
      width: 110,
      render: (value: number | null) => formatNumber(value),
    },
    {
      title: "SMA100",
      dataIndex: "sma100",
      key: "sma100",
      width: 110,
      render: (value: number | null) => formatNumber(value),
    },
    {
      title: "SMA200",
      dataIndex: "sma200",
      key: "sma200",
      width: 110,
      render: (value: number | null) => formatNumber(value),
    },
    {
      title: "% to SMA50",
      dataIndex: "pctToSma50",
      key: "pctToSma50",
      width: 120,
      render: (value: number | null) => (value == null ? "-" : `${formatNumber(value)}%`),
    },
    {
      title: "% to SMA100",
      dataIndex: "pctToSma100",
      key: "pctToSma100",
      width: 120,
      render: (value: number | null) => (value == null ? "-" : `${formatNumber(value)}%`),
    },
    {
      title: "% to SMA200",
      dataIndex: "pctToSma200",
      key: "pctToSma200",
      width: 120,
      render: (value: number | null) => (value == null ? "-" : `${formatNumber(value)}%`),
    },
    {
      title: "RSI(14)",
      dataIndex: "rsi14",
      key: "rsi14",
      width: 100,
      render: (value: number | null) => formatNumber(value),
    },
    {
      title: "SMA100 Touch",
      dataIndex: "sma100TouchDate",
      key: "sma100TouchDate",
      width: 125,
      render: (value: string | null) => value ?? "-",
    },
    {
      title: "SMA200 Touch",
      dataIndex: "sma200TouchDate",
      key: "sma200TouchDate",
      width: 125,
      render: (value: string | null) => value ?? "-",
    },
    {
      title: "Telegram",
      key: "telegram",
      width: 110,
      render: (_value, row) => (
        <Button
          size="small"
          loading={sendingSymbol === row.symbol}
          onClick={() => {
            void handleSendTelegram(row);
          }}
        >
          Send
        </Button>
      ),
    },
  ];

  return (
    <div style={{ padding: 24, background: "#f5f7fa", minHeight: "calc(100vh - 48px)" }}>
      {contextHolder}
      <Space orientation="vertical" size={16} style={{ width: "100%" }}>
        <div>
          <Title level={4} style={{ margin: 0 }}>Hot SMA Pullback Scanner</Title>
          <Text type="secondary">Scan hot stocks by index key and highlight pullback signals around SMA100/SMA200.</Text>
        </div>

        <Card size="small" title="Controls">
          <Space orientation="vertical" style={{ width: "100%" }}>
            <Row gutter={12} align="middle">
              <Col xs={24} md={10}>
                <Text strong>Index Key</Text>
                <Select
                  style={{ width: "100%" }}
                  loading={universeLoading}
                  value={selectedIndexKey || undefined}
                  options={universeOptions}
                  onChange={(value) => setSelectedIndexKey(value)}
                  placeholder="Select index key"
                />
              </Col>
              <Col xs={24} md={8}>
                <Text strong>Sort</Text>
                <Select
                  style={{ width: "100%" }}
                  value={sortMode}
                  onChange={(value) => setSortMode(value)}
                  options={[
                    { label: "Signal Priority", value: "SIGNAL_PRIORITY" },
                    { label: "Nearest SMA100", value: "NEAREST_SMA100" },
                    { label: "Nearest SMA200", value: "NEAREST_SMA200" },
                    { label: "Lowest RSI First", value: "RSI_ASC" },
                  ]}
                />
              </Col>
              <Col xs={24} md={6}>
                <Space style={{ marginTop: 22 }}>
                  <Button type="primary" icon={<SearchOutlined />} loading={loading} onClick={() => { void handleRun(); }}>
                    Run Scan
                  </Button>
                  <Button icon={<DownloadOutlined />} onClick={exportCsv} disabled={visibleRows.length === 0}>
                    Export CSV
                  </Button>
                </Space>
              </Col>
            </Row>

            <Radio.Group
              value={signalFilter}
              optionType="button"
              buttonStyle="solid"
              onChange={(event) => setSignalFilter(event.target.value as SignalFilter)}
              options={[
                { label: "All", value: "ALL" },
                { label: "Aggressive", value: "AGGRESSIVE_BUY" },
                { label: "Standard", value: "STANDARD_BUY" },
                { label: "Watch Zone", value: "WATCH_ZONE" },
              ]}
            />
          </Space>
        </Card>

        {error && <Alert type="error" showIcon message={error} />}

        {data && (
          <Space size={12} wrap>
            <Card size="small"><Statistic title="Total Signals" value={data.summary.totalSignals} /></Card>
            <Card size="small"><Statistic title="Aggressive" value={data.summary.aggressiveCount} /></Card>
            <Card size="small"><Statistic title="Standard" value={data.summary.standardCount} /></Card>
            <Card size="small"><Statistic title="Watch Zone" value={data.summary.watchCount} /></Card>
          </Space>
        )}

        {loading && <div style={{ textAlign: "center", padding: 48 }}><Spin size="large" /></div>}

        {!loading && data && (
          <Card size="small" title="Signals">
            <Table
              rowKey={(row) => row.symbol}
              columns={columns}
              dataSource={visibleRows}
              size="small"
              pagination={{ pageSize: 25, showSizeChanger: true }}
              scroll={{ x: 1700 }}
              locale={{ emptyText: "No signals for selected filters." }}
            />
          </Card>
        )}

        {!loading && !data && !error && <Card><Empty description="Run scan to view hot SMA signals." /></Card>}
      </Space>
    </div>
  );
}
