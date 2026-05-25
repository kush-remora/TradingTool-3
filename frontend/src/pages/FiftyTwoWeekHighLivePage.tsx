import { useEffect, useMemo, useState } from "react";
import { Alert, Button, Card, Empty, Input, Select, Space, Spin, Statistic, Table, Typography, message } from "antd";
import type { ColumnsType } from "antd/es/table";
import { DownloadOutlined, FilterFilled } from "@ant-design/icons";
import { getJson } from "../utils/api";
import { use52WeekHighLive } from "../hooks/use52WeekHighLive";
import type { FiftyTwoWeekHighLiveRequest, FiftyTwoWeekHighLiveResponse, FiftyTwoWeekHighLiveRow, UniverseOptionsResponse } from "../types";

function parseSymbols(input: string): string[] {
  return input
    .split(/[\n,\s]+/)
    .map((value) => value.trim().toUpperCase())
    .filter((value) => value.length > 0);
}

function containsIgnoreCase(source: string, query: string): boolean {
  return source.toLowerCase().includes(query.toLowerCase());
}

function escapeCsv(value: string): string {
  return `"${value.replace(/"/g, "\"\"")}"`;
}

function toCsv(data: FiftyTwoWeekHighLiveResponse): string {
  const header = [
    "bucket",
    "symbol",
    "indexBucket",
    "latestDate",
    "breakoutLevel",
    "latestHigh",
    "latestClose",
    "gapToBreakoutPct",
    "lastHitDate",
    "cooldownActive",
  ];

  const rows: Array<{ bucket: string; row: FiftyTwoWeekHighLiveRow }> = [
    ...data.nearBreakout.map((row) => ({ bucket: "NEAR_BREAKOUT", row })),
    ...data.hitInLast2Weeks.map((row) => ({ bucket: "HIT_LAST_2_WEEKS", row })),
    ...data.hitToday.map((row) => ({ bucket: "HIT_TODAY", row })),
  ];

  const lines = rows.map(({ bucket, row }) => [
    bucket,
    row.symbol,
    row.indexBucket,
    row.latestDate,
    row.breakoutLevel.toFixed(2),
    row.latestHigh.toFixed(2),
    row.latestClose.toFixed(2),
    row.gapToBreakoutPct.toFixed(2),
    row.lastHitDate ?? "",
    row.cooldownActive ? "YES" : "NO",
  ].map((value) => escapeCsv(String(value))).join(","));

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

function withTextFilter(
  key: string,
  pickValue: (row: FiftyTwoWeekHighLiveRow) => string,
): Partial<ColumnsType<FiftyTwoWeekHighLiveRow>[number]> {
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
          <Button size="small" onClick={() => { clearFilters?.(); confirm(); }}>Reset</Button>
        </Space>
      </div>
    ),
    filterIcon: (filtered) => <FilterFilled style={{ color: filtered ? "#1677ff" : undefined }} />,
    onFilter: (value, record) => containsIgnoreCase(pickValue(record), String(value)),
  };
}

export function FiftyTwoWeekHighLivePage() {
  const { data, loading, error, run, sendTelegramForRow } = use52WeekHighLive();
  const [universeLoading, setUniverseLoading] = useState<boolean>(false);
  const [universeOptions, setUniverseOptions] = useState<Array<{ label: string; value: string }>>([]);
  const [selectedUniverses, setSelectedUniverses] = useState<string[]>([]);
  const [symbolsText, setSymbolsText] = useState<string>("");
  const [sendingKey, setSendingKey] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;
    setUniverseLoading(true);
    void getJson<UniverseOptionsResponse>("/api/strategy/52-week-high/live/universes")
      .then((response) => {
        if (!mounted) return;
        setUniverseOptions(response.options.map((option) => ({ label: `${option.value} (${option.count})`, value: option.value })));
      })
      .finally(() => {
        if (mounted) setUniverseLoading(false);
      });

    return () => {
      mounted = false;
    };
  }, []);

  const parsedSymbols = useMemo(() => parseSymbols(symbolsText), [symbolsText]);

  const runScan = async (): Promise<void> => {
    if (selectedUniverses.length === 0) return;
    const request: FiftyTwoWeekHighLiveRequest = {
      universeKeys: selectedUniverses,
      symbols: parsedSymbols,
    };
    await run(request);
  };

  const handleSend = async (bucket: string, row: FiftyTwoWeekHighLiveRow): Promise<void> => {
    const key = `${bucket}-${row.symbol}`;
    setSendingKey(key);
    try {
      await sendTelegramForRow(bucket, row);
      message.success(`Sent ${row.symbol} to Telegram`);
    } catch (err) {
      message.error(err instanceof Error ? err.message : "Telegram send failed");
    } finally {
      setSendingKey(null);
    }
  };

  return (
    <div style={{ padding: 24, background: "#f5f7fa", minHeight: "calc(100vh - 48px)" }}>
      <Space direction="vertical" size={16} style={{ width: "100%" }}>
        <div>
          <Typography.Title level={4} style={{ margin: 0 }}>104-Week Live Strategy</Typography.Title>
          <Typography.Text type="secondary">Daily-candle breakout scanner with 5% near zone, 10-session hit window, T-1/T hit bucket, and 180-trading-day cooldown.</Typography.Text>
        </div>

        <Card size="small" title="Controls">
          <Space direction="vertical" style={{ width: "100%" }}>
            <Typography.Text strong>Universe Keys</Typography.Text>
            <Select
              mode="multiple"
              style={{ width: "100%" }}
              loading={universeLoading}
              value={selectedUniverses}
              options={universeOptions}
              onChange={(value) => setSelectedUniverses(value)}
              placeholder="Select WATCHLIST and/or INDEX:* buckets"
            />
            <Typography.Text strong>Manual Symbols (optional)</Typography.Text>
            <Input.TextArea
              rows={3}
              value={symbolsText}
              onChange={(event) => setSymbolsText(event.target.value)}
              placeholder="INFY, TCS, RELIANCE"
            />
            <Space>
              <Button type="primary" loading={loading} disabled={selectedUniverses.length === 0} onClick={() => void runScan()}>Run Live Scan</Button>
              <Typography.Text type="secondary">Manual symbols parsed: {parsedSymbols.length}</Typography.Text>
            </Space>
          </Space>
        </Card>

        {error && <Alert type="error" showIcon message={error} />}
        {loading && <div style={{ textAlign: "center", padding: 40 }}><Spin size="large" /></div>}
        {!loading && data && <ResultPanel data={data} sendingKey={sendingKey} onSend={handleSend} />}
        {!loading && !data && !error && <Card><Empty description="Run live scan to view results." /></Card>}
      </Space>
    </div>
  );
}

function ResultPanel({
  data,
  sendingKey,
  onSend,
}: {
  data: FiftyTwoWeekHighLiveResponse;
  sendingKey: string | null;
  onSend: (bucket: string, row: FiftyTwoWeekHighLiveRow) => Promise<void>;
}) {
  const baseColumns = useMemo<ColumnsType<FiftyTwoWeekHighLiveRow>>(() => [
    { title: "Symbol", dataIndex: "symbol", key: "symbol", sorter: (a, b) => a.symbol.localeCompare(b.symbol) },
    { title: "Index", dataIndex: "indexBucket", key: "indexBucket", sorter: (a, b) => a.indexBucket.localeCompare(b.indexBucket) },
    { title: "Latest Date", dataIndex: "latestDate", key: "latestDate", sorter: (a, b) => a.latestDate.localeCompare(b.latestDate) },
    { title: "Breakout", dataIndex: "breakoutLevel", key: "breakoutLevel", sorter: (a, b) => a.breakoutLevel - b.breakoutLevel, render: (v: number) => v.toFixed(2) },
    { title: "Latest High", dataIndex: "latestHigh", key: "latestHigh", sorter: (a, b) => a.latestHigh - b.latestHigh, render: (v: number) => v.toFixed(2) },
    { title: "Latest Close", dataIndex: "latestClose", key: "latestClose", sorter: (a, b) => a.latestClose - b.latestClose, render: (v: number) => v.toFixed(2) },
    { title: "Gap %", dataIndex: "gapToBreakoutPct", key: "gapToBreakoutPct", sorter: (a, b) => a.gapToBreakoutPct - b.gapToBreakoutPct, render: (v: number) => v.toFixed(2) },
    { title: "Last Hit", dataIndex: "lastHitDate", key: "lastHitDate", sorter: (a, b) => (a.lastHitDate ?? "").localeCompare(b.lastHitDate ?? ""), render: (value: string | null) => value ?? "-" },
    { title: "Cooldown", dataIndex: "cooldownActive", key: "cooldownActive", sorter: (a, b) => Number(a.cooldownActive) - Number(b.cooldownActive), render: (v: boolean) => (v ? "YES" : "NO") },
  ], []);

  const columnsWithFilter = useMemo<ColumnsType<FiftyTwoWeekHighLiveRow>>(() => {
    return baseColumns.map((column) => {
      const key = String(column.key ?? "");
      const accessor = (row: FiftyTwoWeekHighLiveRow): string => {
        const value = row[key as keyof FiftyTwoWeekHighLiveRow];
        return value == null ? "" : String(value);
      };
      return { ...column, ...withTextFilter(key, accessor) };
    });
  }, [baseColumns]);

  const buildColumns = (bucket: string): ColumnsType<FiftyTwoWeekHighLiveRow> => ([
    ...columnsWithFilter,
    {
      title: "Telegram",
      key: "telegram",
      render: (_, row) => {
        const key = `${bucket}-${row.symbol}`;
        return <Button size="small" loading={sendingKey === key} onClick={() => void onSend(bucket, row)}>Send</Button>;
      },
    },
  ]);

  const exportAllBuckets = (): void => {
    const csv = toCsv(data);
    const fileDate = new Date().toISOString().slice(0, 10);
    downloadCsv(`104w-live-${fileDate}.csv`, csv);
    message.success("CSV export started");
  };

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <Space size={12} wrap>
        <Card size="small"><Statistic title="Near Breakout" value={data.summary.nearBreakout} /></Card>
        <Card size="small"><Statistic title="Hit in Last 2 Weeks" value={data.summary.hitInLast2Weeks} /></Card>
        <Card size="small"><Statistic title="Hit Today" value={data.summary.hitToday} /></Card>
      </Space>
      <Button icon={<DownloadOutlined />} onClick={exportAllBuckets} style={{ width: "fit-content" }}>
        Export CSV
      </Button>

      <Card size="small" title="Near Breakout">
        <Table rowKey={(row) => `near-${row.symbol}`} columns={buildColumns("NEAR_BREAKOUT")} dataSource={data.nearBreakout} size="small" pagination={{ pageSize: 25, showSizeChanger: true }} />
      </Card>

      <Card size="small" title="Hit in Last 2 Weeks">
        <Table rowKey={(row) => `hit10-${row.symbol}`} columns={buildColumns("HIT_LAST_2_WEEKS")} dataSource={data.hitInLast2Weeks} size="small" pagination={{ pageSize: 25, showSizeChanger: true }} />
      </Card>

      <Card size="small" title="Hit Today (T-1 to T)">
        <Table rowKey={(row) => `hittoday-${row.symbol}`} columns={buildColumns("HIT_TODAY")} dataSource={data.hitToday} size="small" pagination={{ pageSize: 25, showSizeChanger: true }} />
      </Card>
    </Space>
  );
}
