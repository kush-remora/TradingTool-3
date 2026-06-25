import type { Key } from "react";
import { useEffect, useMemo, useState } from "react";
import { Alert, Button, Card, Empty, Select, Space, Spin, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import type { FilterValue, TableCurrentDataSource, TablePaginationConfig, TableProps } from "antd/es/table/interface";
import { useHotSmaScanner } from "../hooks/useHotSmaScanner";
import { useStockQuotes } from "../hooks/useStockQuotes";
import type { HotSmaRow, HotSmaUniverseOption, HotSmaZoneStatus } from "../types";
import { getJson } from "../utils/api";
import { renderLiveMarketCell, resolveMarketChangePercent } from "../components/liveMarketCell";
import { WakeUpVolumeCell, resolveDisplayedWakeUpSortRatio } from "../components/WakeUpVolumeCell";

const UNIVERSES_PATH = "/api/strategy/hot-sma/universes";
const STORAGE_KEY = "hot-sma-filters-v1";
const DEFAULT_PAGE_SIZE = 110;

type PersistedFilters = {
  indexKeys?: string[];
  indexKey?: string;
};

const ZONE_STATUS_COLORS: Record<HotSmaZoneStatus, string> = {
  BUY_ZONE: "green",
  ABOVE_BUY_ZONE: "orange",
  NO_SMA200: "default",
};

const COLUMN_TITLES: Record<keyof HotSmaRow, string> = {
  symbol: "Symbol",
  companyName: "Company Name",
  indexKey: "Universe",
  instrumentToken: "Instrument Token",
  latestDate: "Latest Date",
  currentPrice: "LTP",
  sma50: "SMA50",
  sma100: "SMA100",
  sma200: "SMA200",
  pctToSma50: "% From SMA50",
  pctToSma100: "% From SMA100",
  pctToSma200: "% From SMA200",
  distanceToSma200AbsPct: "Abs % To SMA200",
  rsi14: "RSI 14",
  drawdownFromHigh20Pct: "DD From 20D High %",
  drawdownFromHigh60Pct: "DD From 60D High %",
  consecutiveRedDays: "Consecutive Red Days",
  move3dPct: "3D Move %",
  sma100TouchedInLast5d: "SMA100 Touch 5D",
  sma100TouchDate: "SMA100 Touch Date",
  sma200TouchedInLast5d: "SMA200 Touch 5D",
  sma200TouchDate: "SMA200 Touch Date",
  signalTag: "Signal Tag",
  zoneStatus: "Status",
};

const DISPLAY_COLUMNS: Array<keyof HotSmaRow> = [
  "symbol",
  "companyName",
  "currentPrice",
  "sma100",
  "pctToSma100",
  "sma200",
  "pctToSma200",
  "distanceToSma200AbsPct",
  "rsi14",
  "drawdownFromHigh20Pct",
  "drawdownFromHigh60Pct",
  "consecutiveRedDays",
  "move3dPct",
  "zoneStatus",
];

function formatNumber(value: number | null | undefined, fractionDigits: number = 2): string {
  if (value == null) {
    return "-";
  }

  return value.toLocaleString("en-IN", {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  });
}

function formatCellValue(row: HotSmaRow, key: keyof HotSmaRow): string {
  const value = row[key];
  if (value == null) {
    return "-";
  }
  if (typeof value === "number") {
    const zeroDigitKeys = new Set<keyof HotSmaRow>(["instrumentToken", "consecutiveRedDays"]);
    return formatNumber(value, zeroDigitKeys.has(key) ? 0 : 2);
  }
  if (typeof value === "boolean") {
    return value ? "Yes" : "No";
  }
  return String(value);
}

function loadPersistedFilters(): PersistedFilters {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw) as PersistedFilters;
    const storedIndexKeys = Array.isArray(parsed.indexKeys)
      ? parsed.indexKeys
      : (typeof parsed.indexKey === "string" && parsed.indexKey.trim().length > 0 ? [parsed.indexKey] : []);
    return {
      indexKeys: storedIndexKeys.filter(
        (value): value is string => typeof value === "string" && value.trim().length > 0,
      ),
    };
  } catch {
    return {};
  }
}

function persistFilters(filters: PersistedFilters): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(filters));
}

export function HotSmaPage() {
  const { data, loading, error, run } = useHotSmaScanner();
  const [universeOptions, setUniverseOptions] = useState<HotSmaUniverseOption[]>([]);
  const [loadingUniverses, setLoadingUniverses] = useState<boolean>(true);
  const [universeError, setUniverseError] = useState<string | null>(null);
  const [selectedUniverseKeys, setSelectedUniverseKeys] = useState<string[]>([]);
  const [filteredInfo, setFilteredInfo] = useState<Record<string, FilterValue | null>>({});
  const [filteredRows, setFilteredRows] = useState<HotSmaRow[]>([]);
  const quoteSymbols = useMemo(() => (data?.rows ?? []).map((row) => row.symbol), [data?.rows]);
  const { quotesBySymbol } = useStockQuotes(quoteSymbols);

  useEffect(() => {
    let mounted = true;

    const loadUniverses = async (): Promise<void> => {
      setLoadingUniverses(true);
      setUniverseError(null);
      try {
        const result = await getJson<HotSmaUniverseOption[]>(UNIVERSES_PATH, { useCache: false });
        if (!mounted) return;

        setUniverseOptions(result);
        const validUniverseKeys = new Set(result.map((option) => option.value));
        const persistedKeys = loadPersistedFilters().indexKeys?.filter((value) => validUniverseKeys.has(value)) ?? [];
        const fallbackUniverseKeys = persistedKeys.length > 0
          ? persistedKeys
          : (result[0]?.value ? [result[0].value] : []);
        setSelectedUniverseKeys(fallbackUniverseKeys);
      } catch (err) {
        if (!mounted) return;
        setUniverseError(err instanceof Error ? err.message : "Failed to load universes");
      } finally {
        if (mounted) {
          setLoadingUniverses(false);
        }
      }
    };

    void loadUniverses();

    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    setFilteredRows(data?.rows ?? []);
    setFilteredInfo({});
  }, [data?.rows]);

  useEffect(() => {
    persistFilters({ indexKeys: selectedUniverseKeys });
  }, [selectedUniverseKeys]);

  const handleRun = async (): Promise<void> => {
    if (selectedUniverseKeys.length === 0) {
      return;
    }
    await run({ indexKeys: selectedUniverseKeys });
  };

  const handleTableChange: TableProps<HotSmaRow>["onChange"] = (
    _pagination: TablePaginationConfig,
    filters: Record<string, FilterValue | null>,
    _sorter,
    extra: TableCurrentDataSource<HotSmaRow>,
  ) => {
    setFilteredInfo(filters);
    setFilteredRows(extra.currentDataSource);
  };

  const columns = useMemo<ColumnsType<HotSmaRow>>(() => {
    const baseColumns = DISPLAY_COLUMNS.map((key) => {
      const uniqueValues = Array.from(
        new Set((data?.rows ?? []).map((row) => formatCellValue(row, key)).filter((value) => value !== "-")),
      ).map((value) => ({
        text: value,
        value,
      }));

      return {
        title: key === "currentPrice" ? "Live Market" : COLUMN_TITLES[key],
        dataIndex: key,
        key: String(key),
        filteredValue: filteredInfo[String(key)] ?? null,
        filters: uniqueValues,
        filterSearch: true,
        onFilter: (filterValue: boolean | Key, row: HotSmaRow) => formatCellValue(row, key) === String(filterValue),
        sorter: (left: HotSmaRow, right: HotSmaRow) => {
          if (key === "currentPrice") {
            return (
              resolveMarketChangePercent(left.symbol, quotesBySymbol[left.symbol.toUpperCase()], left.previousCloseChangePct) ??
              Number.NEGATIVE_INFINITY
            ) - (
              resolveMarketChangePercent(right.symbol, quotesBySymbol[right.symbol.toUpperCase()], right.previousCloseChangePct) ??
              Number.NEGATIVE_INFINITY
            );
          }
          const leftValue = left[key];
          const rightValue = right[key];
          if (typeof leftValue === "number" && typeof rightValue === "number") {
            return leftValue - rightValue;
          }
          return String(leftValue ?? "").localeCompare(String(rightValue ?? ""));
        },
        defaultSortOrder: key === "distanceToSma200AbsPct" ? ("ascend" as const) : undefined,
        render: (_value: unknown, row: HotSmaRow) => {
          if (key === "zoneStatus") {
            return <Tag color={ZONE_STATUS_COLORS[row.zoneStatus]}>{row.zoneStatus}</Tag>;
          }
          if (key === "currentPrice") {
            return renderLiveMarketCell({
              symbol: row.symbol,
              snapshot: quotesBySymbol[row.symbol.toUpperCase()],
              fallbackLtp: row.currentPrice,
              fallbackChangePercent: row.previousCloseChangePct,
            });
          }
          return formatCellValue(row, key);
        },
      };
    });

    baseColumns.splice(3, 0, {
      title: "Wake-Up",
      key: "wakeUp",
      sorter: (left: HotSmaRow, right: HotSmaRow) =>
        (resolveDisplayedWakeUpSortRatio(left.symbol, quotesBySymbol[left.symbol.toUpperCase()]) ?? Number.NEGATIVE_INFINITY) -
        (resolveDisplayedWakeUpSortRatio(right.symbol, quotesBySymbol[right.symbol.toUpperCase()]) ?? Number.NEGATIVE_INFINITY),
      render: (_value: unknown, row: HotSmaRow) => (
        <WakeUpVolumeCell
          symbol={row.symbol}
          snapshot={quotesBySymbol[row.symbol.toUpperCase()]}
          fallbackChangePercent={row.previousCloseChangePct}
        />
      ),
    });

    return baseColumns;
  }, [data?.rows, filteredInfo, quotesBySymbol]);

  return (
    <div style={{ padding: 24 }}>
      <Space orientation="vertical" size={16} style={{ width: "100%" }}>
        <Card>
          <Space orientation="vertical" size={12} style={{ width: "100%" }}>
            <Typography.Title level={3} style={{ margin: 0 }}>SMA Buy Zone Screener</Typography.Title>
            <Typography.Text type="secondary">
              Run one or more universes and sort the full table by proximity to SMA200.
            </Typography.Text>
            {universeError ? <Alert type="error" message={universeError} /> : null}
            {error ? <Alert type="error" message={error} /> : null}
            <Space wrap>
              <Select
                mode="multiple"
                showSearch
                placeholder="Select one or more universes"
                style={{ minWidth: 360 }}
                loading={loadingUniverses}
                value={selectedUniverseKeys}
                onChange={setSelectedUniverseKeys}
                options={universeOptions.map((option) => ({
                  label: `${option.value} (${option.count})`,
                  value: option.value,
                }))}
              />
              <Button type="primary" onClick={() => void handleRun()} loading={loading} disabled={selectedUniverseKeys.length === 0}>
                Run Scanner
              </Button>
            </Space>
            {data ? (
              <Typography.Text type="secondary">
                Stocks: {data.summary.totalStocks} | Buy zone: {data.summary.buyZoneCount} | Above zone: {data.summary.aboveBuyZoneCount}
              </Typography.Text>
            ) : null}
          </Space>
        </Card>

        <Card>
          {loading && !data ? (
            <Spin />
          ) : data && data.rows.length > 0 ? (
            <Table<HotSmaRow>
              rowKey={(row) => row.symbol}
              columns={columns}
              dataSource={filteredRows}
              onChange={handleTableChange}
              pagination={{ pageSize: DEFAULT_PAGE_SIZE, showSizeChanger: false }}
              scroll={{ x: 1800 }}
            />
          ) : (
            <Empty description="Run the scanner to load stocks for one or more universes." />
          )}
        </Card>
      </Space>
    </div>
  );
}
