import { useEffect, useMemo, useState } from "react";
import { Alert, Button, Card, Empty, Input, InputNumber, Select, Space, Spin, Statistic, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { FilterFilled } from "@ant-design/icons";
import { getJson } from "../utils/api";
import { use52WeekHighBacktest } from "../hooks/use52WeekHighBacktest";
import type { FiftyTwoWeekHighBacktestRequest, FiftyTwoWeekHighBacktestResponse, FiftyTwoWeekHighBacktestRow, UniverseOptionsResponse } from "../types";

const baseColumns: ColumnsType<FiftyTwoWeekHighBacktestRow> = [
  { title: "Symbol", dataIndex: "symbol", key: "symbol", sorter: (a, b) => a.symbol.localeCompare(b.symbol) },
  { title: "Index", dataIndex: "indexBucket", key: "indexBucket", sorter: (a, b) => a.indexBucket.localeCompare(b.indexBucket) },
  { title: "Enter Trade", dataIndex: "enterTrade", key: "enterTrade", sorter: (a, b) => a.enterTrade.localeCompare(b.enterTrade) },
  { title: "Exit Trade", dataIndex: "exitTrade", key: "exitTrade", sorter: (a, b) => (a.exitTrade ?? "OPEN").localeCompare(b.exitTrade ?? "OPEN"), render: (value: string | null) => value ?? "OPEN" },
  { title: "Holding Days", dataIndex: "holdingDays", key: "holdingDays", sorter: (a, b) => a.holdingDays - b.holdingDays },
  { title: "Status", dataIndex: "status", key: "status", sorter: (a, b) => a.status.localeCompare(b.status) },
];

function containsIgnoreCase(source: string, query: string): boolean {
  return source.toLowerCase().includes(query.toLowerCase());
}

function withTextFilter(
  key: string,
  pickValue: (row: FiftyTwoWeekHighBacktestRow) => string,
): Partial<ColumnsType<FiftyTwoWeekHighBacktestRow>[number]> {
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

export function FiftyTwoWeekHighBacktestPage() {
  const { data, loading, error, run } = use52WeekHighBacktest();
  const [indexOptions, setIndexOptions] = useState<Array<{ label: string; value: string }>>([]);
  const [selectedIndexes, setSelectedIndexes] = useState<string[]>([]);
  const [symbolsText, setSymbolsText] = useState<string>("");
  const [universeLoading, setUniverseLoading] = useState<boolean>(false);
  const [targetProfitPct, setTargetProfitPct] = useState<number>(20);

  useEffect(() => {
    let mounted = true;
    setUniverseLoading(true);
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
    const request: FiftyTwoWeekHighBacktestRequest = {
      indexKeys: selectedIndexes,
      symbols: parsedSymbols,
      config: {
        profitPct: targetProfitPct,
        historyDays: 1300,
        backtestDays: 365,
        cooldownDays: 180,
      },
    };
    await run(request);
  };

  return (
    <div style={{ padding: 24, background: "#f5f7fa", minHeight: "calc(100vh - 48px)" }}>
      <Space direction="vertical" size={16} style={{ width: "100%" }}>
        <div>
          <Typography.Title level={4} style={{ margin: 0 }}>104-Week High Backtest</Typography.Title>
          <Typography.Text type="secondary">~3-year data, trade in last 1 year, next-day open entry, configurable target exit %, 180-day cooldown, 104-week breakout signal.</Typography.Text>
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
            <Typography.Text strong>Target Profit %</Typography.Text>
            <InputNumber
              min={1}
              max={200}
              step={0.5}
              value={targetProfitPct}
              onChange={(value) => setTargetProfitPct(value ?? 20)}
            />
            <Space>
              <Button type="primary" loading={loading} disabled={selectedIndexes.length === 0} onClick={() => void runBacktest()}>Run Backtest</Button>
              <Typography.Text type="secondary">Manual symbols parsed: {parsedSymbols.length}</Typography.Text>
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

function ResultPanel({ result }: { result: FiftyTwoWeekHighBacktestResponse }) {
  const filteredColumns = useMemo<ColumnsType<FiftyTwoWeekHighBacktestRow>>(() => {
    return baseColumns.map((column) => {
      const key = String(column.key ?? "");
      const accessor = (row: FiftyTwoWeekHighBacktestRow): string => {
        if (key === "exitTrade") return row.exitTrade ?? "OPEN";
        const value = row[key as keyof FiftyTwoWeekHighBacktestRow];
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
