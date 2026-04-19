import { ReloadOutlined } from "@ant-design/icons";
import { Alert, Button, Card, Empty, InputNumber, Select, Space, Spin, Statistic, Table, Tag, Typography, message } from "antd";
import type { TableColumnsType } from "antd";
import { useCallback, useEffect, useMemo, useState } from "react";
import { StockBadge } from "../components/StockBadge";
import type { WeeklyCycleSuccessResponse, WeeklyCycleSuccessRow } from "../types";
import { clearCache, getJson } from "../utils/api";

const { Text, Title } = Typography;

type UniverseOption = "MIDCAP_250" | "SMALLCAP_250" | "BOTH";

const UNIVERSE_OPTIONS: Array<{ value: UniverseOption; label: string }> = [
  { value: "BOTH", label: "Midcap + Smallcap" },
  { value: "MIDCAP_250", label: "Nifty Midcap 250" },
  { value: "SMALLCAP_250", label: "Nifty Smallcap 250" },
];

export function WeeklyCycleSuccessPage() {
  const [data, setData] = useState<WeeklyCycleSuccessResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [universe, setUniverse] = useState<UniverseOption>("BOTH");
  const [weeks, setWeeks] = useState<number>(8);
  const [highLowPct, setHighLowPct] = useState<number>(10);
  const [rocPct, setRocPct] = useState<number>(2);
  const [messageApi, contextHolder] = message.useMessage();

  const buildPath = useCallback((prepareMissingDaily: boolean) => {
    const params = new URLSearchParams({
      universe,
      weeks: String(weeks),
      highLowPct: String(highLowPct),
      rocPct: String(rocPct),
    });
    if (prepareMissingDaily) {
      params.set("prepare", "true");
    }
    return `/api/screener/weekly-cycle-success?${params.toString()}`;
  }, [universe, weeks, highLowPct, rocPct]);

  const fetchScan = useCallback(async (forceRefresh = false) => {
    const path = buildPath(forceRefresh);
    setLoading(true);
    setError(null);
    try {
      if (forceRefresh) clearCache(path);
      const json = await getJson<WeeklyCycleSuccessResponse>(path, { useCache: !forceRefresh });
      setData(json);
    } catch (err: unknown) {
      const messageText = err instanceof Error ? err.message : "Failed to fetch weekly cycle success data";
      setError(messageText);
      messageApi.error(messageText);
    } finally {
      setLoading(false);
    }
  }, [buildPath, messageApi]);

  useEffect(() => {
    void fetchScan();
  }, [fetchScan]);

  const rows = data?.results ?? [];
  const avgSuccessRate = useMemo(() => {
    if (rows.length === 0) return 0;
    return rows.reduce((sum, row) => sum + row.successRatePct, 0) / rows.length;
  }, [rows]);
  const topPerformers = useMemo(() => rows.filter((row) => row.successRatePct >= 60).length, [rows]);

  const columns: TableColumnsType<WeeklyCycleSuccessRow> = [
    {
      title: "Stock",
      dataIndex: "symbol",
      key: "symbol",
      fixed: "left",
      width: 220,
      sorter: (a, b) => a.symbol.localeCompare(b.symbol),
      render: (symbol: string, row) => (
        <StockBadge symbol={symbol} instrumentToken={row.instrumentToken} companyName={row.companyName} fontSize={14} />
      ),
    },
    {
      title: "Universe",
      dataIndex: "universeBuckets",
      key: "universeBuckets",
      width: 220,
      render: (buckets: string[]) => (
        <Space size={4} wrap>
          {buckets.length === 0 ? <Text type="secondary">-</Text> : buckets.map((bucket) => <Tag key={bucket}>{bucket}</Tag>)}
        </Space>
      ),
    },
    {
      title: "Success",
      dataIndex: "successCount",
      key: "successCount",
      width: 110,
      sorter: (a, b) => a.successCount - b.successCount,
      render: (value: number, row) => (row.cycleCount === 0 ? "No Data" : `${value}/${row.cycleCount}`),
    },
    {
      title: "Success Rate",
      dataIndex: "successRatePct",
      key: "successRatePct",
      width: 140,
      defaultSortOrder: "descend",
      sorter: (a, b) => a.successRatePct - b.successRatePct,
      render: (value: number) => {
        const color = value >= 70 ? "success" : value >= 50 ? "warning" : "default";
        return <Tag color={color}>{value.toFixed(2)}%</Tag>;
      },
    },
    {
      title: "Last Cycle (HL / ROC)",
      key: "lastCycleMetrics",
      width: 210,
      render: (_, row) => {
        const last = row.lastCycleMetrics;
        if (!last) return <Text type="secondary">No cycle</Text>;
        return (
          <Space size={4} orientation="vertical">
            <Text>{last.weekLabel} ({last.startDay}→{last.endDay})</Text>
            <Text strong>{last.highLowPct.toFixed(2)}% / {last.rocPct.toFixed(2)}%</Text>
          </Space>
        );
      },
    },
    {
      title: "Failed Start Weeks",
      dataIndex: "failedStartWeeks",
      key: "failedStartWeeks",
      width: 220,
      render: (failed: string[]) => (failed.length === 0 ? "-" : failed.join(", ")),
    },
  ];

  return (
    <div style={{ padding: 24, background: "#f5f7fa", minHeight: "calc(100vh - 48px)" }}>
      {contextHolder}
      <Space orientation="vertical" size={16} style={{ width: "100%" }}>
        <div>
          <Title level={4} style={{ margin: 0 }}>Weekly Cycle Success Scanner</Title>
          <Text type="secondary">Scan Midcap 250 and Smallcap 250 by Mon/Tue low to week high and ROC cycle rules.</Text>
        </div>

        <Card size="small">
          <Space wrap>
            <div>
              <Text type="secondary">Universe</Text>
              <br />
              <Select value={universe} style={{ width: 220 }} onChange={(value) => setUniverse(value)} options={UNIVERSE_OPTIONS} />
            </div>
            <div>
              <Text type="secondary">Weeks</Text>
              <br />
              <InputNumber data-testid="weeks-input" min={1} max={52} value={weeks} onChange={(value) => setWeeks(value ?? 8)} />
            </div>
            <div>
              <Text type="secondary">High-Low %</Text>
              <br />
              <InputNumber data-testid="high-low-input" min={0} value={highLowPct} onChange={(value) => setHighLowPct(value ?? 10)} />
            </div>
            <div>
              <Text type="secondary">ROC %</Text>
              <br />
              <InputNumber data-testid="roc-input" min={0} value={rocPct} onChange={(value) => setRocPct(value ?? 2)} />
            </div>
            <Button type="primary" icon={<ReloadOutlined />} onClick={() => void fetchScan(true)} loading={loading}>
              Run Scan
            </Button>
          </Space>
        </Card>

        {error ? <Alert type="error" title="Scan failed" description={error} showIcon /> : null}

        <Card size="small">
          <Space size={24} wrap>
            <Statistic title="Stocks Scanned" value={rows.length} />
            <Statistic title="Avg Success Rate" value={avgSuccessRate} precision={2} suffix="%" />
            <Statistic title="Top Performers (>=60%)" value={topPerformers} />
            <Statistic title="Weeks Evaluated" value={data?.weeksEvaluated ?? 0} suffix={`/ ${data?.weeksRequested ?? weeks}`} />
          </Space>
        </Card>

        {loading ? (
          <div style={{ textAlign: "center", padding: 64 }}>
            <Spin size="large" />
          </div>
        ) : rows.length === 0 ? (
          <Card>
            <Empty description="No stocks matched this cycle scan." />
          </Card>
        ) : (
          <Card size="small" style={{ borderRadius: 12 }}>
            <Table
              rowKey="symbol"
              columns={columns}
              dataSource={rows}
              pagination={{ pageSize: 30, showSizeChanger: true }}
              size="small"
              scroll={{ x: 1200 }}
            />
          </Card>
        )}
      </Space>
    </div>
  );
}
