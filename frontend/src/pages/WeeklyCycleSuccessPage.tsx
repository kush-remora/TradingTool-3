import { ReloadOutlined } from "@ant-design/icons";
import { Alert, Button, Card, Empty, InputNumber, Select, Space, Spin, Statistic, Switch, Table, Tag, Tooltip, Typography, message } from "antd";
import type { TableColumnsType } from "antd";
import { useCallback, useEffect, useMemo, useState } from "react";
import { StockBadge } from "../components/StockBadge";
import type { WeeklyCycleSuccessResponse, WeeklyCycleSuccessRow } from "../types";
import { clearCache, getJson } from "../utils/api";

const { Text, Title } = Typography;

type UniverseOption = "ALL" | "MIDCAP_250" | "SMALLCAP_250" | "BOTH" | "NIFTY_50" | "WATCHLIST" | "NIFTY_150";

const UNIVERSE_OPTIONS: Array<{ value: UniverseOption; label: string }> = [
  { value: "ALL", label: "All (50+100+Mid250+Small250+Watchlist)" },
  { value: "BOTH", label: "Midcap + Smallcap" },
  { value: "NIFTY_50", label: "Nifty 50" },
  { value: "NIFTY_150", label: "Nifty 150" },
  { value: "WATCHLIST", label: "Watchlist" },
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
  const [stableBaseDriftPct, setStableBaseDriftPct] = useState<number>(4);
  const [stableBaseOnly, setStableBaseOnly] = useState<boolean>(true);
  const [executionMode, setExecutionMode] = useState<boolean>(true);
  const [messageApi, contextHolder] = message.useMessage();

  const buildPath = useCallback((prepareMissingDaily: boolean) => {
    const params = new URLSearchParams({
      universe,
      weeks: String(weeks),
      highLowPct: String(highLowPct),
      rocPct: String(rocPct),
      stableBaseDriftPct: String(stableBaseDriftPct),
    });
    if (prepareMissingDaily) {
      params.set("prepare", "true");
    }
    return `/api/screener/weekly-cycle-success?${params.toString()}`;
  }, [universe, weeks, highLowPct, rocPct, stableBaseDriftPct]);

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

  const allRows = data?.results ?? [];
  const rows = useMemo(
    () => (stableBaseOnly ? allRows.filter((row) => row.stableBasePass) : allRows),
    [allRows, stableBaseOnly],
  );
  const avgSuccessRate = useMemo(() => {
    if (rows.length === 0) return 0;
    return rows.reduce((sum, row) => sum + row.successRatePct, 0) / rows.length;
  }, [rows]);
  const topPerformers = useMemo(() => rows.filter((row) => row.successRatePct >= 60).length, [rows]);
  const stableBasePassCount = useMemo(() => allRows.filter((row) => row.stableBasePass).length, [allRows]);

  const stockColumn: TableColumnsType<WeeklyCycleSuccessRow>[number] = {
      title: "Stock",
      dataIndex: "symbol",
      key: "symbol",
      fixed: "left",
      width: 220,
      sorter: (a, b) => a.symbol.localeCompare(b.symbol),
      render: (symbol: string, row) => (
        <StockBadge symbol={symbol} instrumentToken={row.instrumentToken} companyName={row.companyName} fontSize={14} />
      ),
    };

  const universeColumn: TableColumnsType<WeeklyCycleSuccessRow>[number] = {
      title: "Universe",
      dataIndex: "universeBuckets",
      key: "universeBuckets",
      width: 220,
      render: (buckets: string[]) => (
        <Space size={4} wrap>
          {buckets.length === 0 ? <Text type="secondary">-</Text> : buckets.map((bucket) => <Tag key={bucket}>{bucket}</Tag>)}
        </Space>
      ),
    };

  const successColumn: TableColumnsType<WeeklyCycleSuccessRow>[number] = {
      title: "Success (x/y | %)",
      key: "successComposite",
      width: 190,
      defaultSortOrder: "descend",
      sorter: (a, b) => a.successRatePct - b.successRatePct,
      render: (_, row) => {
        if (row.cycleCount === 0) {
          return <Text type="secondary">No Data</Text>;
        }
        const color = row.successRatePct >= 70 ? "success" : row.successRatePct >= 50 ? "warning" : "default";
        return (
          <Space size={6}>
            <Text strong>{row.successCount}/{row.cycleCount}</Text>
            <Tag color={color}>{row.successRatePct.toFixed(2)}%</Tag>
          </Space>
        );
      },
    };

  const stableBaseColumn: TableColumnsType<WeeklyCycleSuccessRow>[number] = {
      title: "Stable Base",
      key: "stableBase",
      width: 190,
      sorter: (a, b) => {
        const left = a.stableBaseDriftPct ?? Number.POSITIVE_INFINITY;
        const right = b.stableBaseDriftPct ?? Number.POSITIVE_INFINITY;
        return left - right;
      },
      render: (_, row) => {
        if (row.stableBasePass) {
          return <Tag color="success">PASS ({(row.stableBaseDriftPct ?? 0).toFixed(2)}%)</Tag>;
        }
        return <Tag color="error">{row.stableBaseReason ?? "FAIL"}</Tag>;
      },
    };

  const baseRangeColumn: TableColumnsType<WeeklyCycleSuccessRow>[number] = {
      title: "Base Range",
      key: "baseRange",
      width: 160,
      render: (_, row) => {
        if (row.stableBaseLowMin == null || row.stableBaseLowMax == null) {
          return <Text type="secondary">-</Text>;
        }
        return <Text strong>{row.stableBaseLowMin.toFixed(2)} - {row.stableBaseLowMax.toFixed(2)}</Text>;
      },
    };

  const lastCycleColumn: TableColumnsType<WeeklyCycleSuccessRow>[number] = {
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
    };

  const lastMondayDipColumn: TableColumnsType<WeeklyCycleSuccessRow>[number] = {
      title: <Tooltip title="Uses Monday open-low dip. If Monday is missing, Tuesday is used as fallback.">Mon Dip (Last Wk %)</Tooltip>,
      key: "lastWeekMondayDipPct",
      width: 170,
      sorter: (a, b) => (a.lastWeekMondayDipPct ?? Number.NEGATIVE_INFINITY) - (b.lastWeekMondayDipPct ?? Number.NEGATIVE_INFINITY),
      render: (_, row) => {
        if (row.lastWeekMondayDipPct == null) return <Text type="secondary">-</Text>;
        return <Text strong>{row.lastWeekMondayDipPct.toFixed(2)}%</Text>;
      },
    };

  const avg8wMondayDipColumn: TableColumnsType<WeeklyCycleSuccessRow>[number] = {
      title: <Tooltip title="Average of up to last 8 completed weeks. Monday preferred, Tuesday fallback if Monday is missing.">Mon Dip (8W Avg %)</Tooltip>,
      key: "avg8wMondayDipPct",
      width: 170,
      sorter: (a, b) => (a.avg8wMondayDipPct ?? Number.NEGATIVE_INFINITY) - (b.avg8wMondayDipPct ?? Number.NEGATIVE_INFINITY),
      render: (_, row) => {
        if (row.avg8wMondayDipPct == null) return <Text type="secondary">-</Text>;
        const samples = row.mondayDipSamples8w ?? 0;
        return <Text strong>{row.avg8wMondayDipPct.toFixed(2)}% ({samples})</Text>;
      },
    };

  const failedStartWeeksColumn: TableColumnsType<WeeklyCycleSuccessRow>[number] = {
      title: "Failed Start Weeks",
      dataIndex: "failedStartWeeks",
      key: "failedStartWeeks",
      width: 220,
      render: (failed: string[]) => (failed.length === 0 ? "-" : failed.join(", ")),
    };

  const columns = useMemo<TableColumnsType<WeeklyCycleSuccessRow>>(
    () => {
      if (executionMode) {
        return [stockColumn, successColumn, stableBaseColumn, lastMondayDipColumn, avg8wMondayDipColumn, baseRangeColumn, lastCycleColumn];
      }
      return [stockColumn, universeColumn, successColumn, stableBaseColumn, lastMondayDipColumn, avg8wMondayDipColumn, baseRangeColumn, lastCycleColumn, failedStartWeeksColumn];
    },
    [executionMode],
  );

  return (
    <div style={{ padding: 24, background: "#f5f7fa", minHeight: "calc(100vh - 48px)" }}>
      {contextHolder}
      <Space orientation="vertical" size={16} style={{ width: "100%" }}>
        <div>
          <Title level={4} style={{ margin: 0 }}>Weekly Cycle Success Scanner</Title>
          <Text type="secondary">Scan selected universe by Mon/Tue low to week high and ROC cycle rules.</Text>
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
            <div>
              <Text type="secondary">Base Shift % (Anchor→Latest)</Text>
              <br />
              <InputNumber data-testid="stable-base-drift-input" min={0} value={stableBaseDriftPct} onChange={(value) => setStableBaseDriftPct(value ?? 4)} />
            </div>
            <div>
              <Text type="secondary">Stable Base Only</Text>
              <br />
              <Switch data-testid="stable-base-only-switch" checked={stableBaseOnly} onChange={setStableBaseOnly} />
            </div>
            <div>
              <Text type="secondary">Execution Mode</Text>
              <br />
              <Switch data-testid="execution-mode-switch" checked={executionMode} onChange={setExecutionMode} />
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
            <Statistic title="Stable Base Pass" value={stableBasePassCount} suffix={`/ ${allRows.length}`} />
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
