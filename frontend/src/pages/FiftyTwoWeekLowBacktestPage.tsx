import { useEffect, useMemo, useState } from "react";
import { Alert, Button, Card, Empty, Input, InputNumber, Select, Space, Spin, Statistic, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { FilterFilled, DownloadOutlined } from "@ant-design/icons";
import { getJson } from "../utils/api";
import { use52WeekLowBacktest } from "../hooks/use52WeekLowBacktest";
import type { FiftyTwoWeekLowBacktestRequest, FiftyTwoWeekLowBacktestResponse, FiftyTwoWeekLowBacktestRow, UniverseOptionsResponse } from "../types";

const baseColumns: ColumnsType<FiftyTwoWeekLowBacktestRow> = [
  { title: "Symbol", dataIndex: "symbol", key: "symbol", sorter: (a, b) => a.symbol.localeCompare(b.symbol) },
  { title: "Index", dataIndex: "indexBucket", key: "indexBucket", sorter: (a, b) => a.indexBucket.localeCompare(b.indexBucket) },
  { title: "Enter Trade", dataIndex: "enterTrade", key: "enterTrade", sorter: (a, b) => a.enterTrade.localeCompare(b.enterTrade) },
  { title: "Buy Price", dataIndex: "buyPrice", key: "buyPrice", sorter: (a, b) => a.buyPrice - b.buyPrice, render: (value: number) => value.toFixed(2) },
  { title: "Exit Trade", dataIndex: "exitTrade", key: "exitTrade", sorter: (a, b) => (a.exitTrade ?? "OPEN").localeCompare(b.exitTrade ?? "OPEN"), render: (value: string | null) => value ?? "OPEN" },
  { title: "Sell Price", dataIndex: "sellPrice", key: "sellPrice", sorter: (a, b) => (a.sellPrice ?? 0) - (b.sellPrice ?? 0), render: (value: number | null) => value != null ? value.toFixed(2) : "-" },
  { title: "LTP (Open)", dataIndex: "ltp", key: "ltp", sorter: (a, b) => (a.ltp ?? 0) - (b.ltp ?? 0), render: (value: number | null) => value != null ? value.toFixed(2) : "-" },
  { title: "Holding Days", dataIndex: "holdingDays", key: "holdingDays", sorter: (a, b) => a.holdingDays - b.holdingDays },
  { title: "Target Profit %", dataIndex: "profitPct", key: "profitPct", sorter: (a, b) => (a.profitPct ?? 0) - (b.profitPct ?? 0), render: (value: number | null) => value != null ? `${value.toFixed(2)}%` : "-" },
  { title: "Current Profit %", dataIndex: "currentProfitPct", key: "currentProfitPct", sorter: (a, b) => (a.currentProfitPct ?? 0) - (b.currentProfitPct ?? 0), render: (value: number | null, record) => record.status === "OPEN" && value != null ? <span style={{ color: value > 0 ? "green" : "red" }}>{value.toFixed(2)}%</span> : "-" },
  { title: "Status", dataIndex: "status", key: "status", sorter: (a, b) => a.status.localeCompare(b.status) },
];

function containsIgnoreCase(source: string, query: string): boolean {
  return source.toLowerCase().includes(query.toLowerCase());
}

function withTextFilter(
  key: string,
  pickValue: (row: FiftyTwoWeekLowBacktestRow) => string,
): Partial<ColumnsType<FiftyTwoWeekLowBacktestRow>[number]> {
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

function parseSymbols(input: string): string[] {
  return input
    .split(/[\n,\s]+/)
    .map((value) => value.trim().toUpperCase())
    .filter((value) => value.length > 0);
}

export function FiftyTwoWeekLowBacktestPage() {
  const { data, loading, error, run } = use52WeekLowBacktest();
  const [indexOptions, setIndexOptions] = useState<Array<{ label: string; value: string }>>([]);
  const [selectedIndexes, setSelectedIndexes] = useState<string[]>([]);
  const [symbolsText, setSymbolsText] = useState<string>("");
  const [universeLoading, setUniverseLoading] = useState<boolean>(false);
  const [targetProfitPct, setTargetProfitPct] = useState<number>(30);
  const [lookbackDays, setLookbackDays] = useState<number>(365);

  useEffect(() => {
    let mounted = true;
    setUniverseLoading(true);
    // Reuse universes from 52-week-high endpoint for simplicity
    void getJson<UniverseOptionsResponse>("/api/strategy/52-week-high/universes")
      .then((response) => {
        if (!mounted) return;
        const options = response.options.map((option) => ({
          label: `${option.value} (${option.count})`,
          value: option.value,
        }));
        setIndexOptions(options);
      })
      .finally(() => {
        if (mounted) setUniverseLoading(false);
      });

    return () => {
      mounted = false;
    };
  }, []);

  const parsedSymbols = useMemo(() => parseSymbols(symbolsText), [symbolsText]);

  const runBacktest = async (): Promise<void> => {
    if (selectedIndexes.length === 0) return;
    const request: FiftyTwoWeekLowBacktestRequest = {
      indexKeys: selectedIndexes,
      symbols: parsedSymbols,
      config: {
        profitPct: targetProfitPct,
        lookbackDays: lookbackDays,
      },
    };
    await run(request);
  };

  const exportToMd = () => {
    if (!data) return;
    let markdown = `# 52-Week Low Bounce Backtest Results\n\n`;
    markdown += `**Indexes:** ${data.config.indexKeys.join(", ")}\n`;
    markdown += `**Lookback Days:** ${data.config.lookbackDays}\n`;
    markdown += `**Target Profit %:** ${data.config.profitPct}\n`;
    markdown += `**Scan Start Date:** ${data.config.fromDate}\n\n`;

    markdown += `### Summary\n`;
    markdown += `- **Total Trades:** ${data.summary.totalTrades}\n`;
    markdown += `- **Closed Trades:** ${data.summary.closedTrades}\n`;
    markdown += `- **Open Trades:** ${data.summary.openTrades}\n`;
    markdown += `- **Avg Days Held (Closed):** ${data.summary.avgDaysHeldClosed?.toFixed(1) ?? "-"}\n\n`;

    markdown += `### Trades\n\n`;
    markdown += `| Symbol | Index | Enter Date | Buy Price | Exit Date | Sell Price | LTP | Holding Days | Target % | Current % | Status |\n`;
    markdown += `|---|---|---|---|---|---|---|---|---|---|---|\n`;
    
    data.rows.forEach(row => {
      markdown += `| ${row.symbol} | ${row.indexBucket} | ${row.enterTrade} | ${row.buyPrice.toFixed(2)} | ${row.exitTrade ?? "-"} | ${row.sellPrice ? row.sellPrice.toFixed(2) : "-"} | ${row.ltp ? row.ltp.toFixed(2) : "-"} | ${row.holdingDays} | ${row.profitPct ? row.profitPct.toFixed(2) + "%" : "-"} | ${row.currentProfitPct ? row.currentProfitPct.toFixed(2) + "%" : "-"} | ${row.status} |\n`;
    });

    const blob = new Blob([markdown], { type: "text/markdown;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `52_week_low_bounce_backtest_${new Date().toISOString().split("T")[0]}.md`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  return (
    <div style={{ padding: 24, background: "#f5f7fa", minHeight: "calc(100vh - 48px)" }}>
      <Space direction="vertical" size={16} style={{ width: "100%" }}>
        <div>
          <Typography.Title level={4} style={{ margin: 0 }}>52-Week Low Bounce Backtest</Typography.Title>
          <Typography.Text type="secondary">Evaluate buying stocks at their 52-week low and holding until they reach a set profit percentage.</Typography.Text>
        </div>

        <Card size="small" title="Controls">
          <Space direction="vertical" style={{ width: "100%" }}>
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
            <Typography.Text strong>Manual Symbols (optional)</Typography.Text>
            <Input.TextArea
              rows={3}
              value={symbolsText}
              onChange={(event) => setSymbolsText(event.target.value)}
              placeholder="INFY, TCS, RELIANCE"
            />
            <Space size="large">
              <Space direction="vertical">
                <Typography.Text strong>Lookback Days</Typography.Text>
                <InputNumber
                  min={1}
                  max={2000}
                  step={1}
                  value={lookbackDays}
                  onChange={(value) => setLookbackDays(value ?? 365)}
                />
              </Space>
              <Space direction="vertical">
                <Typography.Text strong>Target Profit %</Typography.Text>
                <InputNumber
                  min={1}
                  max={200}
                  step={0.5}
                  value={targetProfitPct}
                  onChange={(value) => setTargetProfitPct(value ?? 30)}
                />
              </Space>
            </Space>
            <Space>
              <Button type="primary" loading={loading} disabled={selectedIndexes.length === 0} onClick={() => void runBacktest()}>Run Backtest</Button>
              {data && <Button icon={<DownloadOutlined />} onClick={exportToMd}>Export to MD</Button>}
            </Space>
          </Space>
        </Card>

        {error && <Alert type="error" showIcon message={error} />}
        {loading && <div style={{ textAlign: "center", padding: 40 }}><Spin size="large" /></div>}
        {!loading && data && <ResultPanel result={data} />}
        {!loading && !data && !error && <Card><Empty description="Run backtest to view trades." /></Card>}
      </Space>
    </div>
  );
}

function ResultPanel({ result }: { result: FiftyTwoWeekLowBacktestResponse }) {
  const filteredColumns = useMemo<ColumnsType<FiftyTwoWeekLowBacktestRow>>(() => {
    return baseColumns.map((column) => {
      const key = String(column.key ?? "");
      const accessor = (row: FiftyTwoWeekLowBacktestRow): string => {
        if (key === "exitTrade") return row.exitTrade ?? "OPEN";
        const value = row[key as keyof FiftyTwoWeekLowBacktestRow];
        return value == null ? "" : String(value);
      };
      return { ...column, ...withTextFilter(key, accessor) };
    });
  }, []);

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <Space size={12} wrap>
        <Card size="small"><Statistic title="Total Trades" value={result.summary.totalTrades} /></Card>
        <Card size="small"><Statistic title="Closed" value={result.summary.closedTrades} /></Card>
        <Card size="small"><Statistic title="Open" value={result.summary.openTrades} /></Card>
        <Card size="small"><Statistic title="Avg Days Held (Closed)" value={result.summary.avgDaysHeldClosed != null ? result.summary.avgDaysHeldClosed.toFixed(1) : "-"} /></Card>
      </Space>
      <Card size="small" title="Trades">
        <Table
          rowKey={(row) => `${row.symbol}-${row.enterTrade}`}
          columns={filteredColumns}
          dataSource={result.rows}
          size="small"
          pagination={{ pageSize: 25, showSizeChanger: true }}
        />
      </Card>
    </Space>
  );
}
