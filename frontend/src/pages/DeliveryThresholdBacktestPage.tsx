import { useEffect, useMemo, useState } from "react";
import { Alert, Button, Card, Col, Empty, Input, Row, Select, Space, Spin, Statistic, Table, Tag, Typography, message } from "antd";
import type { ColumnsType } from "antd/es/table";
import { DownloadOutlined, FilterFilled } from "@ant-design/icons";
import { getJson } from "../utils/api";
import { useDeliveryThresholdBacktest } from "../hooks/useDeliveryThresholdBacktest";
import type {
  DeliveryThresholdBacktestRequest,
  DeliveryThresholdBacktestResponse,
  DeliveryThresholdBacktestRow,
  UniverseOptionsResponse,
} from "../types";

const EMPTY_CONFIG_JSON = JSON.stringify({ thresholds: {}, profitPct: 10 }, null, 2);

const rowColumns: ColumnsType<DeliveryThresholdBacktestRow> = [
  { title: "Symbol", dataIndex: "symbol", key: "symbol", fixed: "left", width: 110, sorter: (a, b) => a.symbol.localeCompare(b.symbol) },
  { title: "Index", dataIndex: "index", key: "index", width: 180, sorter: (a, b) => a.index.localeCompare(b.index) },
  {
    title: "Status",
    dataIndex: "status",
    key: "status",
    width: 95,
    sorter: (a, b) => a.status.localeCompare(b.status),
    render: (status: string) => <Tag color={status === "HIT" ? "green" : "gold"}>{status}</Tag>,
  },
  { title: "Entry Date", dataIndex: "entryDate", key: "entryDate", width: 120, sorter: (a, b) => a.entryDate.localeCompare(b.entryDate) },
  { title: "Entry Price", dataIndex: "entryPrice", key: "entryPrice", width: 110, sorter: (a, b) => a.entryPrice - b.entryPrice, render: (value: number) => value.toFixed(2) },
  { title: "Entry Delivery %", dataIndex: "entryDeliveryPct", key: "entryDeliveryPct", width: 140, sorter: (a, b) => a.entryDeliveryPct - b.entryDeliveryPct, render: (value: number) => `${value.toFixed(2)}%` },
  {
    title: "Total Volume",
    dataIndex: "totalVolumeCount",
    key: "totalVolumeCount",
    width: 130,
    render: (value: number | null) => (value == null ? "-" : value.toLocaleString("en-IN")),
  },
  {
    title: "Avg 20D Vol",
    dataIndex: "avg20dVolumeAtSignal",
    key: "avg20dVolumeAtSignal",
    width: 130,
    sorter: (a, b) => (a.avg20dVolumeAtSignal ?? 0) - (b.avg20dVolumeAtSignal ?? 0),
    render: (value: number | null) => (value == null ? "-" : value.toLocaleString("en-IN", { maximumFractionDigits: 0 })),
  },
  {
    title: "Signal Vol vs 20D",
    dataIndex: "signalVolumeVs20dPct",
    key: "signalVolumeVs20dPct",
    width: 140,
    sorter: (a, b) => (a.signalVolumeVs20dPct ?? 0) - (b.signalVolumeVs20dPct ?? 0),
    render: (value: number | null) => (value == null ? "-" : `${value.toFixed(2)}%`),
  },
  { title: "Target", dataIndex: "targetPrice", key: "targetPrice", width: 100, sorter: (a, b) => a.targetPrice - b.targetPrice, render: (value: number) => value.toFixed(2) },
  {
    title: "% From 52W High",
    dataIndex: "pctFrom52WeekHighAtBuy",
    key: "pctFrom52WeekHighAtBuy",
    width: 130,
    sorter: (a, b) => (a.pctFrom52WeekHighAtBuy ?? 0) - (b.pctFrom52WeekHighAtBuy ?? 0),
    render: (value: number | null) => (value == null ? "-" : `${value.toFixed(2)}%`),
  },
  {
    title: "% From 52W Low",
    dataIndex: "pctFrom52WeekLowAtBuy",
    key: "pctFrom52WeekLowAtBuy",
    width: 130,
    sorter: (a, b) => (a.pctFrom52WeekLowAtBuy ?? 0) - (b.pctFrom52WeekLowAtBuy ?? 0),
    render: (value: number | null) => (value == null ? "-" : `${value.toFixed(2)}%`),
  },
  { title: "Buy Day", dataIndex: "buyDayOfWeek", key: "buyDayOfWeek", width: 95, sorter: (a, b) => a.buyDayOfWeek.localeCompare(b.buyDayOfWeek) },
  { title: "Exit Date", dataIndex: "exitDate", key: "exitDate", width: 120, render: (value: string | null) => value ?? "-" },
  { title: "Exit Price", dataIndex: "exitPrice", key: "exitPrice", width: 110, render: (value: number | null) => (value == null ? "-" : value.toFixed(2)) },
  { title: "Holding Days", dataIndex: "holdingDays", key: "holdingDays", width: 120, sorter: (a, b) => a.holdingDays - b.holdingDays },
  { title: "RSI Buy", dataIndex: "rsiBuy", key: "rsiBuy", width: 100, render: (value: number | null) => (value == null ? "-" : value.toFixed(2)) },
  { title: "RSI Sell", dataIndex: "rsiSell", key: "rsiSell", width: 100, render: (value: number | null) => (value == null ? "-" : value.toFixed(2)) },
  {
    title: "Max DD @ Buy %",
    dataIndex: "maxDrawdownAtBuyPct",
    key: "maxDrawdownAtBuyPct",
    width: 130,
    sorter: (a, b) => (a.maxDrawdownAtBuyPct ?? 0) - (b.maxDrawdownAtBuyPct ?? 0),
    render: (value: number | null) => (value == null ? "-" : `${value.toFixed(2)}%`),
  },
  { title: "Current Price", dataIndex: "currentPrice", key: "currentPrice", width: 120, sorter: (a, b) => a.currentPrice - b.currentPrice, render: (value: number) => value.toFixed(2) },
  {
    title: "Floating PnL %",
    dataIndex: "floatingPnlPct",
    key: "floatingPnlPct",
    width: 130,
    render: (value: number | null) => (value == null ? "-" : `${value.toFixed(2)}%`),
  },
  { title: "Threshold Used", dataIndex: "thresholdUsed", key: "thresholdUsed", width: 130, render: (value: number) => value.toFixed(2) },
];

function parseSymbols(input: string): string[] {
  return input
    .split(/[\n,\s]+/)
    .map((value) => value.trim().toUpperCase())
    .filter((value) => value.length > 0);
}

function normalizeIndexKey(value: string): string {
  return value
    .trim()
    .toUpperCase()
    .replace(/[^A-Z0-9]+/g, "_")
    .replace(/_+/g, "_")
    .replace(/^_+|_+$/g, "");
}

function containsIgnoreCase(source: string, query: string): boolean {
  return source.toLowerCase().includes(query.toLowerCase());
}

function parseDateRange(raw: string): { from: string; to: string } {
  const [from = "", to = ""] = raw.split("|");
  return { from, to };
}

function withTextFilter(
  key: string,
  pickValue: (row: DeliveryThresholdBacktestRow) => string,
): Partial<ColumnsType<DeliveryThresholdBacktestRow>[number]> {
  return {
    filterDropdown: ({ selectedKeys, setSelectedKeys, confirm, clearFilters }) => (
      <div style={{ padding: 8, width: 220 }}>
        <Input
          allowClear
          placeholder={`Filter ${key}`}
          value={(selectedKeys[0] as string) ?? ""}
          onChange={(event) => {
            const value = event.target.value;
            setSelectedKeys(value ? [value] : []);
          }}
          onPressEnter={() => confirm()}
        />
        <Space style={{ marginTop: 8 }}>
          <Button type="primary" size="small" onClick={() => confirm()}>Apply</Button>
          <Button
            size="small"
            onClick={() => {
              clearFilters?.();
              confirm();
            }}
          >
            Reset
          </Button>
        </Space>
      </div>
    ),
    filterIcon: (filtered) => <FilterFilled style={{ color: filtered ? "#1677ff" : undefined }} />,
    onFilter: (value, record) => containsIgnoreCase(pickValue(record), String(value)),
  };
}

function withDateRangeFilter(
  key: string,
  pickValue: (row: DeliveryThresholdBacktestRow) => string | null,
): Partial<ColumnsType<DeliveryThresholdBacktestRow>[number]> {
  return {
    filterDropdown: ({ selectedKeys, setSelectedKeys, confirm, clearFilters }) => {
      const current = String(selectedKeys[0] ?? "");
      const parsed = parseDateRange(current);
      return (
        <div style={{ padding: 8, width: 230 }}>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>From</Typography.Text>
          <Input
            type="date"
            value={parsed.from}
            onChange={(event) => {
              const next = `${event.target.value}|${parsed.to}`;
              setSelectedKeys(next === "|" ? [] : [next]);
            }}
          />
          <Typography.Text type="secondary" style={{ marginTop: 8, display: "block", fontSize: 12 }}>To</Typography.Text>
          <Input
            type="date"
            value={parsed.to}
            onChange={(event) => {
              const next = `${parsed.from}|${event.target.value}`;
              setSelectedKeys(next === "|" ? [] : [next]);
            }}
          />
          <Space style={{ marginTop: 8 }}>
            <Button type="primary" size="small" onClick={() => confirm()}>Apply</Button>
            <Button
              size="small"
              onClick={() => {
                clearFilters?.();
                confirm();
              }}
            >
              Reset
            </Button>
          </Space>
        </div>
      );
    },
    filterIcon: (filtered) => <FilterFilled style={{ color: filtered ? "#1677ff" : undefined }} />,
    onFilter: (value, record) => {
      const rawDate = pickValue(record);
      if (!rawDate) return false;
      const { from, to } = parseDateRange(String(value));
      if (from && rawDate < from) return false;
      if (to && rawDate > to) return false;
      return true;
    },
  };
}

function downloadCsv(result: DeliveryThresholdBacktestResponse): void {
  const header = [
    "symbol",
    "index",
    "entryDate",
    "entryPrice",
    "entryDeliveryPct",
    "totalVolumeCount",
    "avg20dVolumeAtSignal",
    "signalVolumeVs20dPct",
    "targetPrice",
    "pctFrom52WeekHighAtBuy",
    "pctFrom52WeekLowAtBuy",
    "buyDayOfWeek",
    "exitDate",
    "exitPrice",
    "holdingDays",
    "rsiBuy",
    "rsiSell",
    "maxDrawdownAtBuyPct",
    "status",
    "currentPrice",
    "floatingPnlPct",
    "thresholdUsed",
  ];

  const rows = result.rows.map((row) => [
    row.symbol,
    row.index,
    row.entryDate,
    row.entryPrice,
    row.entryDeliveryPct,
    row.totalVolumeCount ?? "",
    row.avg20dVolumeAtSignal ?? "",
    row.signalVolumeVs20dPct ?? "",
    row.targetPrice,
    row.pctFrom52WeekHighAtBuy ?? "",
    row.pctFrom52WeekLowAtBuy ?? "",
    row.buyDayOfWeek,
    row.exitDate ?? "",
    row.exitPrice ?? "",
    row.holdingDays,
    row.rsiBuy ?? "",
    row.rsiSell ?? "",
    row.maxDrawdownAtBuyPct ?? "",
    row.status,
    row.currentPrice,
    row.floatingPnlPct ?? "",
    row.thresholdUsed,
  ]);

  const csv = [header, ...rows]
    .map((line) => line.map((value) => `"${String(value).replace(/"/g, '""')}"`).join(","))
    .join("\n");

  const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `delivery-threshold-backtest-${result.config.fromDate}-to-${result.config.toDate}.csv`;
  link.click();
  URL.revokeObjectURL(url);
}

export function DeliveryThresholdBacktestPage() {
  const [messageApi, contextHolder] = message.useMessage();
  const { data, loading, error, run } = useDeliveryThresholdBacktest();

  const [universeLoading, setUniverseLoading] = useState<boolean>(false);
  const [indexOptions, setIndexOptions] = useState<Array<{ label: string; value: string }>>([]);
  const [selectedIndexes, setSelectedIndexes] = useState<string[]>([]);
  const [symbolsText, setSymbolsText] = useState<string>("");
  const [configText, setConfigText] = useState<string>(EMPTY_CONFIG_JSON);

  useEffect(() => {
    let mounted = true;
    setUniverseLoading(true);
    void getJson<UniverseOptionsResponse>("/api/screener/universes")
      .then((response) => {
        if (!mounted) return;
        const filtered = response.options
          .filter((option) => option.value !== "WATCHLIST")
          .map((option) => ({ label: `${option.value} (${option.count})`, value: option.value }));
        setIndexOptions(filtered);
        setSelectedIndexes(filtered.slice(0, 2).map((option) => option.value));
      })
      .catch(() => {
        if (!mounted) return;
        setIndexOptions([]);
      })
      .finally(() => {
        if (mounted) setUniverseLoading(false);
      });

    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    let mounted = true;
    void getJson<DeliveryThresholdBacktestRequest["config"]>("/api/strategy/delivery-threshold/config")
      .then((response) => {
        if (!mounted) return;
        setConfigText(JSON.stringify(response, null, 2));
      })
      .catch(() => {
        if (!mounted) return;
        setConfigText(EMPTY_CONFIG_JSON);
      });

    return () => {
      mounted = false;
    };
  }, []);

  const parsedSymbols = useMemo(() => parseSymbols(symbolsText), [symbolsText]);

  const runBacktest = async (): Promise<void> => {
    if (selectedIndexes.length === 0) {
      messageApi.warning("Select at least one index.");
      return;
    }

    let parsedConfig: DeliveryThresholdBacktestRequest["config"];
    try {
      parsedConfig = JSON.parse(configText) as DeliveryThresholdBacktestRequest["config"];
    } catch {
      messageApi.error("Config JSON is invalid.");
      return;
    }

    const thresholdMap = Object.entries(parsedConfig.thresholds ?? {}).reduce<Record<string, number>>(
      (accumulator, [key, value]) => {
        accumulator[normalizeIndexKey(key)] = value;
        return accumulator;
      },
      {},
    );
    const missingThresholdKeys = selectedIndexes
      .map((indexKey) => normalizeIndexKey(indexKey))
      .filter((indexKey) => thresholdMap[indexKey] == null);
    if (missingThresholdKeys.length > 0) {
      messageApi.error(`Missing thresholds for: ${missingThresholdKeys.join(", ")}`);
      return;
    }

    const request: DeliveryThresholdBacktestRequest = {
      indexKeys: selectedIndexes,
      symbols: parsedSymbols,
      config: parsedConfig,
    };

    await run(request);
  };

  return (
    <div style={{ padding: 24, background: "#f5f7fa", minHeight: "calc(100vh - 48px)" }}>
      {contextHolder}
      <Space orientation="vertical" size={16} style={{ width: "100%" }}>
        <div>
          <Typography.Title level={4} style={{ margin: 0 }}>Delivery Threshold 10% Backtest</Typography.Title>
          <Typography.Text type="secondary">
            Buy next-day open on delivery threshold hit; sell only at +profit target; keep non-hit trades open.
          </Typography.Text>
        </div>

        <Card size="small" title="Controls">
          <Space orientation="vertical" size={12} style={{ width: "100%" }}>
            <Row gutter={12}>
              <Col xs={24} md={14}>
                <Typography.Text strong>Index Keys</Typography.Text>
                <Select
                  mode="multiple"
                  style={{ width: "100%" }}
                  value={selectedIndexes}
                  loading={universeLoading}
                  options={indexOptions}
                  onChange={(value) => setSelectedIndexes(value)}
                  placeholder="Select one or more index keys"
                />
              </Col>
              <Col xs={24} md={10}>
                <Typography.Text strong>Manual Symbols (optional)</Typography.Text>
                <Input.TextArea
                  rows={2}
                  value={symbolsText}
                  onChange={(event) => setSymbolsText(event.target.value)}
                  placeholder="INFY, TCS, RELIANCE"
                />
              </Col>
            </Row>

            <div>
              <Typography.Text strong>Config JSON</Typography.Text>
              <Input.TextArea
                value={configText}
                rows={10}
                onChange={(event) => setConfigText(event.target.value)}
              />
            </div>

            <Space>
              <Button type="primary" loading={loading} onClick={() => void runBacktest()}>
                Run Scan
              </Button>
              <Typography.Text type="secondary">Manual symbols parsed: {parsedSymbols.length}</Typography.Text>
              <Button
                icon={<DownloadOutlined />}
                disabled={!data || data.rows.length === 0}
                onClick={() => data && downloadCsv(data)}
              >
                Export CSV
              </Button>
            </Space>
          </Space>
        </Card>

        {error && <Alert type="error" showIcon message={error} />}

        {loading && (
          <div style={{ textAlign: "center", padding: 48 }}>
            <Spin size="large" />
          </div>
        )}

        {!loading && data && <DeliveryThresholdResult result={data} />}

        {!loading && !data && !error && (
          <Card>
            <Empty description="Run backtest to view summary and trade rows." />
          </Card>
        )}
      </Space>
    </div>
  );
}

function DeliveryThresholdResult({ result }: { result: DeliveryThresholdBacktestResponse }) {
  const filteredColumns = useMemo<ColumnsType<DeliveryThresholdBacktestRow>>(() => {
    return rowColumns.map((column) => {
      const key = String(column.key ?? "");
      if (key === "entryDate" || key === "exitDate") {
        const dateAccessor = (row: DeliveryThresholdBacktestRow): string | null =>
          key === "entryDate" ? row.entryDate : row.exitDate;
        return { ...column, ...withDateRangeFilter(key, dateAccessor) };
      }
      const accessor = (row: DeliveryThresholdBacktestRow): string => {
        const value = row[key as keyof DeliveryThresholdBacktestRow];
        return value == null ? "" : String(value);
      };
      return { ...column, ...withTextFilter(key, accessor) };
    });
  }, []);

  return (
    <Space orientation="vertical" size={16} style={{ width: "100%" }}>
      <Row gutter={12}>
        <Col><Card size="small"><Statistic title="Total Buys" value={result.summary.totalBuys} /></Card></Col>
        <Col><Card size="small"><Statistic title="Hit Count" value={result.summary.hitCount} /></Card></Col>
        <Col><Card size="small"><Statistic title="Hit Rate" value={result.summary.hitRatePct.toFixed(2)} suffix="%" /></Card></Col>
        <Col><Card size="small"><Statistic title="Open" value={result.summary.openCount} /></Card></Col>
        <Col><Card size="small"><Statistic title="Days Avg" value={result.summary.daysToHitAvg == null ? "-" : result.summary.daysToHitAvg.toFixed(2)} /></Card></Col>
        <Col><Card size="small"><Statistic title="Days Median" value={result.summary.daysToHitMedian == null ? "-" : result.summary.daysToHitMedian.toFixed(2)} /></Card></Col>
      </Row>

      <Card size="small" title="Result Table">
        <Table
          rowKey={(row) => `${row.symbol}-${row.entryDate}-${row.index}`}
          columns={filteredColumns}
          dataSource={result.rows}
          size="small"
          pagination={{ pageSize: 25, showSizeChanger: true }}
          scroll={{ x: 2200 }}
        />
      </Card>
    </Space>
  );
}
