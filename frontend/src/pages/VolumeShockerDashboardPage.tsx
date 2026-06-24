import type { Key } from "react";
import { useEffect, useMemo, useState } from "react";
import { Alert, Button, Card, Empty, Select, Space, Spin, Switch, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import type { FilterValue, TableCurrentDataSource, TablePaginationConfig, TableProps } from "antd/es/table/interface";
import { useVolumeShockerDashboard } from "../hooks/useVolumeShockerDashboard";
import { LiveMarketWidget } from "../components/LiveMarketWidget";
import type {
  VolumeShockerDashboardRow,
  VolumeShockerDetailResponse,
  VolumeShockerDetailDay,
} from "../types";

type DetailState = {
  loading: boolean;
  data: VolumeShockerDetailResponse | null;
  error: string | null;
};

const PERCENT_KEYS = new Set([
  "delivery_pct",
  "distance_from_sma200_pct",
  "daily_change_pct",
]);

const RATIO_KEYS = new Set([
  "delivery_volume_vs_max_10d_before_event_ratio",
]);

function formatNumber(value: number | null | undefined, fractionDigits: number = 2): string {
  if (value == null) {
    return "-";
  }
  return value.toLocaleString("en-IN", {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  });
}

function formatValue(key: string, value: unknown): string {
  if (value == null) {
    return "-";
  }
  if (typeof value === "boolean") {
    return value ? "Yes" : "No";
  }
  if (typeof value === "number") {
    if (PERCENT_KEYS.has(key)) {
      return `${formatNumber(value, 2)}%`;
    }
    if (RATIO_KEYS.has(key)) {
      return `${formatNumber(value, 2)}x`;
    }
    const digits = Number.isInteger(value) ? 0 : 2;
    return formatNumber(value, digits);
  }
  return String(value);
}

function buildPhase1Url(symbols: string[], asOfDate: string): string {
  const baseUrl = import.meta.env.BASE_URL;
  const params = new URLSearchParams({
    symbols: symbols.join(","),
    asOfDate,
  });
  return `${baseUrl}console/wyckoff-phase1?${params.toString()}`;
}

export function VolumeShockerDashboardPage() {
  const {
    dates,
    data,
    loadingDates,
    loadingData,
    error,
    loadDates,
    loadDashboard,
    loadDetail,
  } = useVolumeShockerDashboard();

  const [selectedDate, setSelectedDate] = useState<string | null>(null);
  const [showAccumulationOnly, setShowAccumulationOnly] = useState(false);
  const [showRepeatOnly, setShowRepeatOnly] = useState(false);
  const [filteredInfo, setFilteredInfo] = useState<Record<string, FilterValue | null>>({});
  const [filteredRows, setFilteredRows] = useState<VolumeShockerDashboardRow[]>([]);
  const [expandedRowKeys, setExpandedRowKeys] = useState<Key[]>([]);
  const [detailBySymbol, setDetailBySymbol] = useState<Record<string, DetailState>>({});

  useEffect(() => {
    let mounted = true;

    void loadDates()
      .then((result) => {
        if (!mounted || !result.default_date) {
          return;
        }
        setSelectedDate(result.default_date);
        return loadDashboard(result.default_date);
      })
      .catch(() => undefined);

    return () => {
      mounted = false;
    };
  }, [loadDashboard, loadDates]);

  const quickFilteredRows = useMemo(() => {
    const rows = data?.rows ?? [];
    return rows.filter((row) => {
      if (showAccumulationOnly && !row.pre_event_accumulation_hint) {
        return false;
      }
      if (showRepeatOnly && row.appearance_count_10d <= 1) {
        return false;
      }
      return true;
    });
  }, [data?.rows, showAccumulationOnly, showRepeatOnly]);

  useEffect(() => {
    setFilteredRows(quickFilteredRows);
    setFilteredInfo({});
  }, [quickFilteredRows]);

  const openPhase1 = (symbols: string[]): void => {
    if (!selectedDate || symbols.length === 0) {
      return;
    }
    window.open(buildPhase1Url(symbols, selectedDate), "_blank", "noopener,noreferrer");
  };

  const handleDateChange = async (tradeDate: string): Promise<void> => {
    setSelectedDate(tradeDate);
    setExpandedRowKeys([]);
    setDetailBySymbol({});
    await loadDashboard(tradeDate);
  };

  const ensureDetailLoaded = async (symbol: string): Promise<void> => {
    if (!selectedDate) {
      return;
    }
    const existing = detailBySymbol[symbol];
    if (existing?.loading || existing?.data) {
      return;
    }

    setDetailBySymbol((current) => ({
      ...current,
      [symbol]: { loading: true, data: null, error: null },
    }));

    try {
      const result = await loadDetail(selectedDate, symbol);
      setDetailBySymbol((current) => ({
        ...current,
        [symbol]: { loading: false, data: result, error: null },
      }));
    } catch (err) {
      setDetailBySymbol((current) => ({
        ...current,
        [symbol]: {
          loading: false,
          data: null,
          error: err instanceof Error ? err.message : "Failed to load detail",
        },
      }));
    }
  };

  const handleTableChange: TableProps<VolumeShockerDashboardRow>["onChange"] = (
    _pagination: TablePaginationConfig,
    filters: Record<string, FilterValue | null>,
    _sorter,
    extra: TableCurrentDataSource<VolumeShockerDashboardRow>,
  ) => {
    setFilteredInfo(filters);
    setFilteredRows(extra.currentDataSource);
  };

  const columns = useMemo<ColumnsType<VolumeShockerDashboardRow>>(() => {
    const columnDefs: Array<{ key: keyof VolumeShockerDashboardRow; title: string }> = [
      { key: "source_rank", title: "Rank" },
      { key: "symbol", title: "Symbol" },
      { key: "company_name", title: "Company Name" },
      { key: "ltp", title: "LTP" },
      { key: "volume", title: "Volume" },
      { key: "delivery_volume", title: "Delivery Volume" },
      { key: "delivery_pct", title: "Delivery %" },
      { key: "max_delivery_volume_10d_before_event", title: "10D Max Delivery Before Event" },
      { key: "delivery_volume_vs_max_10d_before_event_ratio", title: "Today Delivery vs 10D Max" },
      { key: "appearance_count_10d", title: "10D Appearance Count" },
      { key: "streak_length_10d", title: "Streak Length" },
      { key: "sma200_price", title: "200 SMA Price" },
      { key: "distance_from_sma200_pct", title: "% Away From 200 SMA" },
      { key: "pre_event_accumulation_hint", title: "Pre-Event Accumulation Hint" },
      { key: "tag", title: "Tag" },
    ];

    const tableColumns = columnDefs.map((definition) => {
      const key = definition.key;
      const uniqueValues = Array.from(
        new Set(quickFilteredRows.map((row) => row[key]).filter((value) => value != null)),
      ).map((value) => ({
        text: formatValue(String(key), value),
        value: String(value),
      }));

      return {
        title: definition.title,
        dataIndex: key,
        key,
        filteredValue: filteredInfo[String(key)] ?? null,
        filters: uniqueValues,
        filterSearch: true,
        onFilter: (filterValue: boolean | Key, row: VolumeShockerDashboardRow) => String(row[key]) === String(filterValue),
        sorter: (left: VolumeShockerDashboardRow, right: VolumeShockerDashboardRow) => {
          const leftValue = left[key];
          const rightValue = right[key];
          if (typeof leftValue === "number" && typeof rightValue === "number") {
            return leftValue - rightValue;
          }
          return String(leftValue ?? "").localeCompare(String(rightValue ?? ""));
        },
        defaultSortOrder: key === "source_rank" ? ("ascend" as const) : undefined,
        render: (value: unknown, row: VolumeShockerDashboardRow) => {
          if (key === "ltp") {
            return (
              <LiveMarketWidget
                symbol={`NSE:${row.symbol}`}
                fallbackLtp={row.ltp}
                showDetails={true}
              />
            );
          }
          if (key === "pre_event_accumulation_hint") {
            return row.pre_event_accumulation_hint ? <Tag color="green">YES</Tag> : <Tag>NO</Tag>;
          }
          if (key === "tag") {
            return row.tag === "WYCKOFF_PHASE_D" ? <Tag color="blue">{row.tag}</Tag> : <Tag>{row.tag}</Tag>;
          }
          return formatValue(String(key), value);
        },
      };
    });

    tableColumns.push({
      title: "Actions",
      key: "actions",
      fixed: "right",
      render: (_: unknown, row: VolumeShockerDashboardRow) => (
        <Space>
          <Button size="small" onClick={() => openPhase1([row.symbol])}>
            Phase 1
          </Button>
        </Space>
      ),
    });

    return tableColumns;
  }, [filteredInfo, quickFilteredRows]);

  const detailColumns: ColumnsType<VolumeShockerDetailDay> = [
    {
      title: "Date",
      dataIndex: "date",
      key: "date",
      render: (value: string, row: VolumeShockerDetailDay) => row.is_event_day ? <Tag color="gold">{value}</Tag> : value,
    },
    { title: "Open", dataIndex: "open", key: "open", render: (value: number) => formatValue("open", value) },
    { title: "Close", dataIndex: "close", key: "close", render: (value: number) => formatValue("close", value) },
    { title: "Daily %", dataIndex: "daily_change_pct", key: "daily_change_pct", render: (value: number | null) => formatValue("daily_change_pct", value) },
    { title: "Volume", dataIndex: "volume", key: "volume", render: (value: number) => formatValue("volume", value) },
    { title: "Delivery Volume", dataIndex: "delivery_volume", key: "delivery_volume", render: (value: number | null) => formatValue("delivery_volume", value) },
    { title: "Delivery %", dataIndex: "delivery_pct", key: "delivery_pct", render: (value: number | null) => formatValue("delivery_pct", value) },
  ];

  return (
    <div style={{ padding: 24, background: "#f5f7fa", minHeight: "calc(100vh - 48px)" }}>
      <Space direction="vertical" size={16} style={{ width: "100%" }}>
        <div>
          <Typography.Title level={4} style={{ margin: 0 }}>
            Volume Shocker Dashboard
          </Typography.Title>
          <Typography.Text type="secondary">
            Review one event date, spot repeat names, and study delivery build-up before and after the shock day.
          </Typography.Text>
        </div>

        <Card size="small" title="Controls">
          <Space wrap size={16}>
            <div>
              <Typography.Text strong>Select Event Date</Typography.Text>
              <div>
                <Select
                  style={{ width: 220 }}
                  loading={loadingDates}
                  value={selectedDate ?? undefined}
                  options={(dates?.available_dates ?? []).map((value) => ({ label: value, value }))}
                  placeholder="Select an ingested date"
                  onChange={(value) => void handleDateChange(value)}
                />
              </div>
            </div>

            <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
              <Switch checked={showAccumulationOnly} onChange={setShowAccumulationOnly} />
              <Typography.Text strong>Show only pre-event accumulation hint = yes</Typography.Text>
            </div>

            <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
              <Switch checked={showRepeatOnly} onChange={setShowRepeatOnly} />
              <Typography.Text strong>Show only repeat names</Typography.Text>
            </div>

            <Button
              type="primary"
              disabled={quickFilteredRows.length === 0 || !selectedDate}
              onClick={() => openPhase1(quickFilteredRows.map((row) => row.symbol))}
            >
              Open Phase 1 For These Stocks
            </Button>
          </Space>
        </Card>

        {error && <Alert type="error" showIcon title={error} />}

        {(loadingDates || loadingData) && (
          <div style={{ textAlign: "center", padding: 48 }}>
            <Spin size="large" />
          </div>
        )}

        {!loadingDates && !loadingData && data && (
          <Card
            size="small"
            title={`Result Table (${data.trade_date})`}
            extra={
              <Space>
                <Typography.Text type="secondary">
                  Loaded: {data.rows.length} rows
                </Typography.Text>
                <Typography.Text type="secondary">
                  Filtered: {filteredRows.length}
                </Typography.Text>
              </Space>
            }
          >
            <Typography.Text type="secondary" style={{ display: "block", marginBottom: 12 }}>
              Use each column header filter menu for column-level filtering. Expand rows to inspect the 15-day event-study window.
            </Typography.Text>
            <Table
              rowKey={(row) => row.symbol}
              columns={columns}
              dataSource={quickFilteredRows}
              size="small"
              scroll={{ x: 2200 }}
              pagination={{ pageSize: 25, showSizeChanger: true }}
              onChange={handleTableChange}
              expandable={{
                expandedRowKeys,
                onExpand: (expanded, row) => {
                  const nextKeys = expanded
                    ? [...expandedRowKeys, row.symbol]
                    : expandedRowKeys.filter((key) => key !== row.symbol);
                  setExpandedRowKeys(nextKeys);
                  if (expanded) {
                    void ensureDetailLoaded(row.symbol);
                  }
                },
                expandedRowRender: (row) => {
                  const detail = detailBySymbol[row.symbol];
                  if (!detail || detail.loading) {
                    return <Spin />;
                  }
                  if (detail.error) {
                    return <Alert type="error" showIcon title={detail.error} />;
                  }
                  if (!detail.data) {
                    return <Empty description="No detail loaded." />;
                  }

                  return (
                    <Space direction="vertical" size={12} style={{ width: "100%" }}>
                      <Space wrap size={16}>
                        <Typography.Text strong>
                          10D Appearance Count: {detail.data.summary.appearance_count_10d}
                        </Typography.Text>
                        <Typography.Text strong>
                          Streak Length: {detail.data.summary.streak_length_10d}
                        </Typography.Text>
                        <Typography.Text strong>
                          Max Delivery Before Event: {formatValue("delivery_volume", detail.data.summary.max_delivery_volume_10d_before_event)}
                        </Typography.Text>
                        <Typography.Text strong>
                          Event Delivery vs Max Before: {formatValue("delivery_volume_vs_max_10d_before_event_ratio", detail.data.summary.delivery_volume_vs_max_10d_before_event_ratio)}
                        </Typography.Text>
                      </Space>

                      <Table
                        rowKey={(day) => day.date}
                        columns={detailColumns}
                        dataSource={detail.data.days}
                        size="small"
                        pagination={false}
                      />
                    </Space>
                  );
                },
              }}
            />
          </Card>
        )}

        {!loadingDates && !loadingData && !data && !error && (
          <Card>
            <Empty description="Select an ingested date to view the volume-shocker dashboard." />
          </Card>
        )}
      </Space>
    </div>
  );
}
