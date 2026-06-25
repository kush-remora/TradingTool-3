import type { Key } from "react";
import { useEffect, useMemo, useState } from "react";
import { Alert, Button, Card, Col, Empty, Row, Select, Space, Spin, Statistic, Table, Typography, message, Switch } from "antd";
import type { ColumnsType } from "antd/es/table";
import type { FilterValue, TableCurrentDataSource, TablePaginationConfig, TableProps } from "antd/es/table/interface";
import { getJson } from "../utils/api";
import { useWyckoffPhase1Scanner } from "../hooks/useWyckoffPhase1Scanner";
import { useStockQuotes } from "../hooks/useStockQuotes";
import type {
  UniverseOptionsResponse,
  WyckoffPhase1Config,
  WyckoffPhase1Row,
  WyckoffPhase1RunRequest,
  WyckoffPhase1TableColumnsConfig,
} from "../types";
import { renderLiveMarketCell, resolveMarketChangePercent } from "../components/liveMarketCell";

const FILTERS_STORAGE_KEY = "wyckoff-phase1-filters-v1";

type StoredFilters = {
  universeKeys: string[];
  strictBaseFilter: boolean;
};

type PrefilledPhase1Request = {
  asOfDate: string | null;
  symbols: string[];
};

function loadStoredFilters(): StoredFilters | null {
  const raw = window.localStorage.getItem(FILTERS_STORAGE_KEY);
  if (!raw) {
    return null;
  }
  try {
    const parsed = JSON.parse(raw) as Partial<StoredFilters>;
    return {
      universeKeys: Array.isArray(parsed.universeKeys) ? parsed.universeKeys.filter((value): value is string => typeof value === "string") : [],
      strictBaseFilter: typeof parsed.strictBaseFilter === "boolean" ? parsed.strictBaseFilter : false,
    };
  } catch {
    return null;
  }
}

function loadPrefilledPhase1Request(): PrefilledPhase1Request {
  const params = new URLSearchParams(window.location.search);
  const rawSymbols = params.get("symbols")?.trim() ?? "";
  const symbols = (rawSymbols ? rawSymbols.split(",") : [])
    .map((symbol) => symbol.trim().toUpperCase())
    .filter((symbol, index, items) => symbol.length > 0 && items.indexOf(symbol) === index);

  return {
    asOfDate: params.get("asOfDate"),
    symbols,
  };
}

const COLUMN_LABELS: Record<string, string> = {
  symbol: "Symbol",
  signal_date: "Signal Date",
  days_ago: "Days Ago",
  index_key: "Index",
  delivery_pct: "Delivery %",
  delivery_threshold_pct: "Delivery Threshold %",
  delivery_pass: "Delivery Pass",
  density_breach_count_15d: "Density Count (15D)",
  density_pass: "Density Pass",
  delivery_volume_zscore_60d: "Delivery Vol Z-Score (60D)",
  zscore_pass: "Z-Score Pass",
  lvq_dq_pass: "LVQ_DQ Pass",
  lvq_hit_count_15d: "LVQ Hits (15D)",
  spread_pct: "Spread %",
  avg_spread_pct_20d: "Avg Spread % (20D)",
  absorption_pass: "Absorption Pass",
  roc20_pct: "ROC 20D %",
  roc20_range_pass: "ROC Range Pass",
  sma200_distance_pct: "Dist vs SMA200 %",
  sma_window_used: "SMA Window Used",
  dma200_range_pass: "DMA200 Range Pass",
  low_volume_high_delivery_info: "Low Vol + High Deliv Info",
  volume_vs_50d_ratio: "Volume vs 50D Ratio",
  passed_count: "Passed Count",
  accumulation_run_length_days: "Accumulation Run (Days)",
};

const PERCENT_KEYS = new Set([
  "delivery_pct",
  "delivery_threshold_pct",
  "spread_pct",
  "avg_spread_pct_20d",
  "roc20_pct",
  "sma200_distance_pct",
]);

function formatValue(key: string, value: unknown): string {
  if (value == null) {
    return "-";
  }
  if (typeof value === "number") {
    if (PERCENT_KEYS.has(key)) {
      return `${value.toFixed(2)}%`;
    }
    if (Number.isInteger(value)) {
      return String(value);
    }
    return value.toFixed(2);
  }
  return String(value);
}

function valueForSort(value: unknown): string | number {
  if (value == null) {
    return "";
  }
  if (typeof value === "number") {
    return value;
  }
  return String(value);
}

function matchesColumnFilter(row: WyckoffPhase1Row, key: string, filterValue: string): boolean {
  const rowValue = row[key as keyof WyckoffPhase1Row];
  if (rowValue == null) {
    return false;
  }
  return String(rowValue) === filterValue;
}

export function WyckoffPhase1Page() {
  const [messageApi, contextHolder] = message.useMessage();
  const { data, loading, error, run } = useWyckoffPhase1Scanner();
  const [prefilledRequest] = useState<PrefilledPhase1Request>(() => loadPrefilledPhase1Request());

  const [universeLoading, setUniverseLoading] = useState(false);
  const [indexOptions, setIndexOptions] = useState<Array<{ label: string; value: string }>>([]);
  const [selectedUniverseKeys, setSelectedUniverseKeys] = useState<string[]>([]);
  const [strictBaseFilter, setStrictBaseFilter] = useState(false);
  const [filtersHydrated, setFiltersHydrated] = useState(false);
  const [filteredInfo, setFilteredInfo] = useState<Record<string, FilterValue | null>>({});
  const [filteredRows, setFilteredRows] = useState<WyckoffPhase1Row[]>([]);

  const [scannerConfig, setScannerConfig] = useState<WyckoffPhase1Config | null>(null);
  const [columnsConfig, setColumnsConfig] = useState<WyckoffPhase1TableColumnsConfig | null>(null);
  const quoteSymbols = useMemo(() => (data?.rows ?? []).map((row) => row.symbol), [data?.rows]);
  const { quotesBySymbol } = useStockQuotes(quoteSymbols);

  useEffect(() => {
    let mounted = true;
    setUniverseLoading(true);
    void getJson<UniverseOptionsResponse>("/api/strategy/wyckoff/phase1/universes")
      .then((response) => {
        if (!mounted) return;
        const options = response.options.map((option) => ({
          label: `${option.value} (${option.count})`,
          value: option.value,
        }));
        setIndexOptions(options);
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
    const stored = loadStoredFilters();
    if (stored) {
      setSelectedUniverseKeys(stored.universeKeys);
      setStrictBaseFilter(stored.strictBaseFilter);
    }
    setFiltersHydrated(true);
  }, []);

  useEffect(() => {
    if (!filtersHydrated) {
      return;
    }
    window.localStorage.setItem(
      FILTERS_STORAGE_KEY,
      JSON.stringify({
        universeKeys: selectedUniverseKeys,
        strictBaseFilter,
      } satisfies StoredFilters),
    );
  }, [filtersHydrated, selectedUniverseKeys, strictBaseFilter]);

  useEffect(() => {
    let mounted = true;

    void getJson<WyckoffPhase1Config>("/api/strategy/wyckoff/phase1/config")
      .then((response) => {
        if (!mounted) return;
        setScannerConfig(response);
      })
      .catch(() => {
        if (!mounted) return;
        setScannerConfig(null);
      });

    void getJson<WyckoffPhase1TableColumnsConfig>("/api/strategy/wyckoff/phase1/columns")
      .then((response) => {
        if (!mounted) return;
        setColumnsConfig(response);
      })
      .catch(() => {
        if (!mounted) return;
        setColumnsConfig(null);
      });

    return () => {
      mounted = false;
    };
  }, []);

  const columns = useMemo<ColumnsType<WyckoffPhase1Row>>(() => {
    const enabledKeys = (columnsConfig?.columns ?? [])
      .filter((column) => column.enabled)
      .map((column) => column.key);

    let keys = enabledKeys.length > 0 ? enabledKeys : ["symbol", "index_key", "signal_date", "delivery_pct", "delivery_pass", "passed_count"];

    const symbolIndex = keys.indexOf("symbol");
    if (symbolIndex !== -1 && !keys.includes("liveMarket")) {
      const newKeys = [...keys];
      newKeys.splice(symbolIndex + 1, 0, "liveMarket");
      keys = newKeys;
    }

    const defaultSort = columnsConfig?.defaultSort?.[0];

    return keys.map((key) => {
      const uniqueValues = key === "liveMarket" ? [] : Array.from(
        new Set((data?.rows ?? []).map((row) => row[key as keyof WyckoffPhase1Row]).filter((value) => value != null)),
      )
        .sort((left, right) => {
          const leftSortable = valueForSort(left);
          const rightSortable = valueForSort(right);
          if (typeof leftSortable === "number" && typeof rightSortable === "number") {
            return leftSortable - rightSortable;
          }
          return String(leftSortable).localeCompare(String(rightSortable));
        })
        .map((value) => ({
          text: formatValue(key, value),
          value: String(value),
        }));

      const column = {
        title: key === "liveMarket" ? "Live Market" : (COLUMN_LABELS[key] ?? key),
        dataIndex: key,
        key,
        filteredValue: filteredInfo[key] ?? null,
        filters: uniqueValues,
        filterSearch: key !== "liveMarket",
        onFilter: key === "liveMarket" ? undefined : ((filterValue: boolean | Key, row: WyckoffPhase1Row) =>
          matchesColumnFilter(row, key, String(filterValue))),
        sorter: key === "liveMarket" ? ((a: WyckoffPhase1Row, b: WyckoffPhase1Row) => {
          return (
            resolveMarketChangePercent(a.symbol, quotesBySymbol[a.symbol.toUpperCase()]) ?? Number.NEGATIVE_INFINITY
          ) - (
            resolveMarketChangePercent(b.symbol, quotesBySymbol[b.symbol.toUpperCase()]) ?? Number.NEGATIVE_INFINITY
          );
        }) : ((a: WyckoffPhase1Row, b: WyckoffPhase1Row) => {
          const left = valueForSort(a[key as keyof WyckoffPhase1Row]);
          const right = valueForSort(b[key as keyof WyckoffPhase1Row]);
          if (typeof left === "number" && typeof right === "number") {
            return left - right;
          }
          return String(left).localeCompare(String(right));
        }),
        render: (value: unknown, row: WyckoffPhase1Row) => {
          if (key === "liveMarket") {
            return renderLiveMarketCell({
              symbol: row.symbol,
              snapshot: quotesBySymbol[row.symbol.toUpperCase()],
            });
          }
          return formatValue(key, value);
        },
      };

      if (defaultSort?.key === key && defaultSort.direction === "desc") {
        return { ...column, defaultSortOrder: "descend" as const };
      }
      if (defaultSort?.key === key && defaultSort.direction === "asc") {
        return { ...column, defaultSortOrder: "ascend" as const };
      }
      return column;
    });
  }, [columnsConfig, data?.rows, filteredInfo, quotesBySymbol]);

  useEffect(() => {
    setFilteredRows(data?.rows ?? []);
    setFilteredInfo({});
  }, [data]);

  const handleTableChange: TableProps<WyckoffPhase1Row>["onChange"] = (
    _pagination: TablePaginationConfig,
    filters: Record<string, FilterValue | null>,
    _sorter,
    extra: TableCurrentDataSource<WyckoffPhase1Row>,
  ) => {
    setFilteredInfo(filters);
    setFilteredRows(extra.currentDataSource);
  };

  const clearColumnFilters = (): void => {
    setFilteredInfo({});
    setFilteredRows(data?.rows ?? []);
  };

  const runScanner = async (): Promise<void> => {
    if (selectedUniverseKeys.length === 0 && prefilledRequest.symbols.length === 0) {
      messageApi.warning("Select at least one universe key or use prefilled symbols.");
      return;
    }

    const request: WyckoffPhase1RunRequest = {
      universeKeys: prefilledRequest.symbols.length > 0 ? [] : selectedUniverseKeys,
      symbols: prefilledRequest.symbols.length > 0 ? prefilledRequest.symbols : undefined,
      asOfDate: prefilledRequest.asOfDate ?? undefined,
      applyStrictBaseFilter: strictBaseFilter,
    };

    await run(request);
  };

  return (
    <div style={{ padding: 24, background: "#f5f7fa", minHeight: "calc(100vh - 48px)" }}>
      {contextHolder}
      <Space orientation="vertical" size={16} style={{ width: "100%" }}>
        <div>
          <Typography.Title level={4} style={{ margin: 0 }}>Wyckoff Phase-1 Scanner</Typography.Title>
          <Typography.Text type="secondary">
            Latest matched delivery or z-score signal in the configured lookback window with accumulation diagnostics.
          </Typography.Text>
        </div>

        <Card size="small" title="Controls">
          <Space orientation="vertical" size={12} style={{ width: "100%" }}>
            {prefilledRequest.symbols.length > 0 && (
              <Alert
                type="info"
                showIcon
                title={`Prefilled from Volume Shocker: ${prefilledRequest.symbols.length} symbol(s) as of ${prefilledRequest.asOfDate ?? "latest available date"}.`}
              />
            )}
            <Row gutter={12}>
              <Col xs={24} md={24}>
                <Typography.Text strong>Universe Keys</Typography.Text>
                <Select
                  mode="multiple"
                  style={{ width: "100%" }}
                  value={selectedUniverseKeys}
                  disabled={prefilledRequest.symbols.length > 0}
                  loading={universeLoading}
                  options={indexOptions}
                  onChange={(value) => setSelectedUniverseKeys(value)}
                  placeholder={
                    prefilledRequest.symbols.length > 0
                      ? "Using prefilled symbols from Volume Shocker"
                      : "Select one or more index keys or WATCHLIST"
                  }
                />
                {prefilledRequest.symbols.length > 0 && (
                  <Typography.Text type="secondary">
                    Universe keys are disabled because this run is using prefilled symbols from the Volume Shocker dashboard.
                  </Typography.Text>
                )}
              </Col>
            </Row>

            <Space>
              <Button
                type="primary"
                loading={loading}
                disabled={selectedUniverseKeys.length === 0 && prefilledRequest.symbols.length === 0}
                onClick={() => void runScanner()}
              >
                Run Scan
              </Button>

              <div style={{ marginLeft: 24, display: "inline-flex", alignItems: "center" }}>
                <Switch 
                  checked={strictBaseFilter} 
                  onChange={(checked) => setStrictBaseFilter(checked)} 
                />
                <Typography.Text style={{ marginLeft: 8 }} strong>
                  Strict Phase 2 Filter (Dead Base Only)
                </Typography.Text>
              </div>
            </Space>
          </Space>
        </Card>

        <Card size="small" title="Loaded Config (Read-only)">
          <Row gutter={12}>
            <Col><Statistic title="Signal Lookback" value={scannerConfig?.signalLookbackDays ?? "-"} /></Col>
            <Col><Statistic title="Density Lookback" value={scannerConfig?.trackA.rollingDensity.lookbackDays ?? "-"} /></Col>
            <Col><Statistic title="Density Min Breach" value={scannerConfig?.trackA.rollingDensity.minThresholdBreaches ?? "-"} /></Col>
            <Col><Statistic title="Z Baseline" value={scannerConfig?.trackA.deliveryVolumeZScore.baselineDays ?? "-"} /></Col>
            <Col><Statistic title="Min Z-Score" value={scannerConfig?.trackA.deliveryVolumeZScore.minZScore ?? "-"} /></Col>
            <Col><Statistic title="LVQ Near %" value={scannerConfig?.trackA.lvqDq.nearMinPctOfRollingMin ?? "-"} suffix="%" /></Col>
          </Row>
        </Card>

        {error && <Alert type="error" showIcon message={error} />}

        {loading && (
          <div style={{ textAlign: "center", padding: 48 }}>
            <Spin size="large" />
          </div>
        )}

        {!loading && data && (
          <Space orientation="vertical" size={16} style={{ width: "100%" }}>
            <Row gutter={12}>
              <Col><Card size="small"><Statistic title="Universe" value={data.meta.universe_count} /></Card></Col>
              <Col><Card size="small"><Statistic title="Matched" value={data.meta.matched_count} /></Card></Col>
              <Col><Card size="small"><Statistic title="Filtered" value={filteredRows.length} /></Card></Col>
              <Col><Card size="small"><Statistic title="As Of" value={data.meta.as_of_date} /></Card></Col>
            </Row>

            <Card 
              size="small" 
              title="Result Table"
              extra={
                <Space>
                  <Button
                    size="small"
                    disabled={Object.values(filteredInfo).every((value) => !value || value.length === 0)}
                    onClick={clearColumnFilters}
                  >
                    Clear Filters
                  </Button>
                  <Button
                    size="small"
                    disabled={filteredRows.length === 0}
                    onClick={() => {
                      if (filteredRows.length === 0) return;
                      const blob = new Blob([JSON.stringify(filteredRows, null, 2)], { type: "application/json" });
                      const url = URL.createObjectURL(blob);
                      const a = document.createElement("a");
                      a.href = url;
                      a.download = `wyckoff_phase1_${new Date().toISOString().split("T")[0]}.json`;
                      document.body.appendChild(a);
                      a.click();
                      document.body.removeChild(a);
                      URL.revokeObjectURL(url);
                    }}
                  >
                    Export JSON
                  </Button>
                </Space>
              }
            >
              <Typography.Text type="secondary" style={{ display: "block", marginBottom: 12 }}>
                Use each column header filter menu to select values from the result set. Export uses the filtered rows.
              </Typography.Text>
              <Table
                rowKey={(row) => row.symbol}
                columns={columns}
                dataSource={filteredRows}
                size="small"
                pagination={{ pageSize: 25, showSizeChanger: true }}
                scroll={{ x: 1800 }}
                onChange={handleTableChange}
              />
            </Card>
          </Space>
        )}

        {!loading && !data && !error && (
          <Card>
            <Empty description="Run scan to view Phase-1 results." />
          </Card>
        )}
      </Space>
    </div>
  );
}
