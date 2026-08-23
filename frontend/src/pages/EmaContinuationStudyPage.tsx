import { ReloadOutlined } from "@ant-design/icons";
import { Alert, Button, Card, Col, Empty, Row, Select, Space, Spin, Statistic, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useMemo, useState } from "react";
import { EmaContinuationChart } from "./EmaContinuationChart";
import { useEmaContinuationStudy } from "../hooks/useEmaContinuationStudy";
import { useEmaContinuationWatchlistStudy } from "../hooks/useEmaContinuationWatchlistStudy";
import type {
  EmaContinuationCurrentResponse,
  EmaContinuationSignal,
  EmaContinuationTargetPerformance,
  EmaContinuationWatchlistRow,
  InstrumentSearchResult,
  UniverseOptionsResponse,
} from "../types";
import { getJson } from "../utils/api";
import "./emaContinuationStudy.css";

const { Text, Title } = Typography;
const WATCHLISTS_PATH = "/api/strategy/ema-continuation/watchlists";

function formatPrice(value: number | null): string {
  return value == null ? "—" : `₹${value.toLocaleString("en-IN", { maximumFractionDigits: 2 })}`;
}

function formatPercent(value: number | null): string {
  return value == null ? "—" : `${value >= 0 ? "+" : ""}${value.toFixed(2)}%`;
}

function formatRate(value: number | null): string {
  return value == null ? "—" : `${value.toFixed(1)}%`;
}

function formatAtrDistance(value: number | null): string {
  return value == null ? "—" : `${value >= 0 ? "+" : ""}${value.toFixed(2)} ATR`;
}

function formatDays(value: number | null): string {
  return value == null ? "Not reached" : `${value} ${value === 1 ? "session" : "sessions"}`;
}

function formatDate(value: string | null): string {
  if (!value) return "—";
  return new Intl.DateTimeFormat("en-IN", { day: "2-digit", month: "short", year: "numeric", timeZone: "UTC" })
    .format(new Date(`${value}T00:00:00Z`));
}

function signalLabel(type: EmaContinuationSignal["type"]): string {
  return type === "EMA_CROSS" ? "EMA cross" : "Base breakout";
}

function watchlistStatusLabel(status: EmaContinuationWatchlistRow["status"]): string {
  return status === "ANALYZED" ? "Analyzed" : "Error";
}

type FilterableValue = string | number | null;

const NULL_FILTER_VALUE = "__NULL__";

function filterKey(value: FilterableValue): string {
  return value == null ? NULL_FILTER_VALUE : String(value);
}

function buildExactFilters<T>(
  rows: T[],
  getValue: (row: T) => FilterableValue,
  formatValue: (value: FilterableValue) => string = (value) => value == null ? "—" : String(value),
): Array<{ text: string; value: string }> {
  const values = new Map<string, FilterableValue>();
  rows.forEach((row) => {
    const value = getValue(row);
    values.set(filterKey(value), value);
  });
  return Array.from(values.entries())
    .sort(([left], [right]) => left.localeCompare(right, undefined, { numeric: true }))
    .map(([key, value]) => ({ text: formatValue(value), value: key }));
}

function matchesExactFilter<T>(selectedValue: unknown, row: T, getValue: (row: T) => FilterableValue): boolean {
  return filterKey(getValue(row)) === String(selectedValue);
}

function compareNullableNumbers(left: number | null, right: number | null): number {
  if (left == null && right == null) return 0;
  if (left == null) return 1;
  if (right == null) return -1;
  return left - right;
}

function compareNullableStrings(left: string | null, right: string | null): number {
  return (left ?? "").localeCompare(right ?? "");
}

function isMarketHours(): boolean {
  const parts = new Intl.DateTimeFormat("en-IN", {
    timeZone: "Asia/Kolkata",
    weekday: "short",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).formatToParts(new Date());
  const weekday = parts.find((part) => part.type === "weekday")?.value;
  const hour = Number(parts.find((part) => part.type === "hour")?.value ?? -1);
  const minute = Number(parts.find((part) => part.type === "minute")?.value ?? -1);
  const minutes = hour * 60 + minute;
  return weekday !== "Sat" && weekday !== "Sun" && minutes >= 9 * 60 + 15 && minutes <= 15 * 60 + 35;
}

export function EmaContinuationStudyPage() {
  const [watchlists, setWatchlists] = useState<UniverseOptionsResponse["options"]>([]);
  const [watchlistKey, setWatchlistKey] = useState<string>();
  const [members, setMembers] = useState<InstrumentSearchResult[]>([]);
  const [selectedMember, setSelectedMember] = useState<InstrumentSearchResult>();
  const [watchlistLoading, setWatchlistLoading] = useState(true);
  const [memberLoading, setMemberLoading] = useState(false);
  const [pageError, setPageError] = useState<string | null>(null);
  const [current, setCurrent] = useState<EmaContinuationCurrentResponse | null>(null);
  const [currentLoading, setCurrentLoading] = useState(false);
  const { data, loading, error, run } = useEmaContinuationStudy();
  const {
    data: watchlistData,
    loading: watchlistStudyLoading,
    error: watchlistStudyError,
    run: runWatchlistStudy,
    clear: clearWatchlistStudy,
  } = useEmaContinuationWatchlistStudy();

  useEffect(() => {
    void getJson<UniverseOptionsResponse>(WATCHLISTS_PATH)
      .then((response) => setWatchlists(response.options))
      .catch((cause) => setPageError(cause instanceof Error ? cause.message : "Unable to load watchlists."))
      .finally(() => setWatchlistLoading(false));
  }, []);

  useEffect(() => {
    if (!watchlistKey) {
      setMembers([]);
      setSelectedMember(undefined);
      return;
    }
    clearWatchlistStudy();
    setMemberLoading(true);
    setSelectedMember(undefined);
    void getJson<InstrumentSearchResult[]>(`/api/stocks/watchlists/${encodeURIComponent(watchlistKey)}/members`, { useCache: false })
      .then((response) => setMembers(response))
      .catch((cause) => setPageError(cause instanceof Error ? cause.message : "Unable to load watchlist members."))
      .finally(() => setMemberLoading(false));
  }, [clearWatchlistStudy, watchlistKey]);

  const refreshCurrent = useCallback(async (): Promise<void> => {
    if (!data?.symbol) return;
    setCurrentLoading(true);
    try {
      setCurrent(await getJson<EmaContinuationCurrentResponse>(
        `/api/strategy/ema-continuation/current?symbol=${encodeURIComponent(data.symbol)}`,
        { useCache: false },
      ));
    } catch (cause) {
      setPageError(cause instanceof Error ? cause.message : "Unable to load current session data.");
    } finally {
      setCurrentLoading(false);
    }
  }, [data?.symbol]);

  useEffect(() => {
    if (!data?.symbol) {
      setCurrent(null);
      return undefined;
    }
    void refreshCurrent();
    const interval = window.setInterval(() => {
      if (isMarketHours()) void refreshCurrent();
    }, 5 * 60 * 1000);
    return () => window.clearInterval(interval);
  }, [data?.symbol, refreshCurrent]);

  const runStudy = (): void => {
    if (!watchlistKey || !selectedMember) return;
    setPageError(null);
    void run({
      watchlistKey,
      symbol: selectedMember.trading_symbol,
      instrumentToken: selectedMember.instrument_token,
    });
  };

  const runCompleteWatchlist = (): void => {
    if (!watchlistKey) return;
    setPageError(null);
    void runWatchlistStudy({ watchlistKey });
  };

  const signalRows = data?.signals ?? [];
  const targetRows = data?.targetPerformance ?? [];
  const watchlistRows = watchlistData?.rows ?? [];

  const signalColumns: ColumnsType<EmaContinuationSignal> = useMemo(() => [
    {
      title: "Entry type",
      key: "type",
      filters: buildExactFilters(signalRows, (row) => row.type, (value) => value === "EMA_CROSS" ? "EMA cross" : value === "BASE_BREAKOUT" ? "Base breakout" : "—"),
      onFilter: (value, row) => matchesExactFilter(value, row, (item) => item.type),
      sorter: (left, right) => left.type.localeCompare(right.type),
      filterSearch: true,
      render: (_, row) => <Tag color={row.type === "EMA_CROSS" ? "green" : "blue"}>{signalLabel(row.type)}</Tag>,
    },
    {
      title: "Signal date",
      dataIndex: "date",
      key: "date",
      filters: buildExactFilters(signalRows, (row) => row.date, (value) => formatDate(value as string | null)),
      onFilter: (value, row) => matchesExactFilter(value, row, (item) => item.date),
      sorter: (left, right) => left.date.localeCompare(right.date),
      filterSearch: true,
      render: formatDate,
    },
    {
      title: "Close",
      dataIndex: "close",
      key: "close",
      filters: buildExactFilters(signalRows, (row) => row.close, (value) => formatPrice(value as number | null)),
      onFilter: (value, row) => matchesExactFilter(value, row, (item) => item.close),
      sorter: (left, right) => left.close - right.close,
      filterSearch: true,
      render: formatPrice,
    },
    {
      title: "Setup date",
      dataIndex: "setupDate",
      key: "setupDate",
      filters: buildExactFilters(signalRows, (row) => row.setupDate, (value) => formatDate(value as string | null)),
      onFilter: (value, row) => matchesExactFilter(value, row, (item) => item.setupDate),
      sorter: (left, right) => left.setupDate.localeCompare(right.setupDate),
      filterSearch: true,
      render: formatDate,
    },
    {
      title: "Base period",
      key: "basePeriod",
      filters: buildExactFilters(signalRows, (row) => row.baseStartDate, (value) => formatDate(value as string | null)),
      onFilter: (value, row) => matchesExactFilter(value, row, (item) => item.baseStartDate),
      sorter: (left, right) => compareNullableStrings(left.baseEndDate, right.baseEndDate),
      filterSearch: true,
      render: (_, row) => row.baseStartDate && row.baseEndDate
        ? `${formatDate(row.baseStartDate)} – ${formatDate(row.baseEndDate)}`
        : "—",
    },
    {
      title: "Base high",
      dataIndex: "baseHigh",
      key: "baseHigh",
      filters: buildExactFilters(signalRows, (row) => row.baseHigh, (value) => formatPrice(value as number | null)),
      onFilter: (value, row) => matchesExactFilter(value, row, (item) => item.baseHigh),
      sorter: (left, right) => left.baseHigh - right.baseHigh,
      filterSearch: true,
      render: formatPrice,
    },
    {
      title: "Base low",
      dataIndex: "baseLow",
      key: "baseLow",
      filters: buildExactFilters(signalRows, (row) => row.baseLow, (value) => formatPrice(value as number | null)),
      onFilter: (value, row) => matchesExactFilter(value, row, (item) => item.baseLow),
      sorter: (left, right) => left.baseLow - right.baseLow,
      filterSearch: true,
      render: formatPrice,
    },
    {
      title: "Base width",
      dataIndex: "baseWidthPct",
      key: "baseWidthPct",
      filters: buildExactFilters(signalRows, (row) => row.baseWidthPct, (value) => value == null ? "—" : `${value.toFixed(1)}%`),
      onFilter: (value, row) => matchesExactFilter(value, row, (item) => item.baseWidthPct),
      sorter: (left, right) => compareNullableNumbers(left.baseWidthPct, right.baseWidthPct),
      filterSearch: true,
      render: (value: number | null) => value == null ? "—" : `${value.toFixed(1)}%`,
    },
    {
      title: "Close near high",
      dataIndex: "closeNearHighPct",
      key: "closeNearHighPct",
      filters: buildExactFilters(signalRows, (row) => row.closeNearHighPct, (value) => value == null ? "—" : `${value.toFixed(1)}%`),
      onFilter: (value, row) => matchesExactFilter(value, row, (item) => item.closeNearHighPct),
      sorter: (left, right) => compareNullableNumbers(left.closeNearHighPct, right.closeNearHighPct),
      filterSearch: true,
      render: (value: number | null) => value == null ? "—" : `${value.toFixed(1)}%`,
    },
    {
      title: "Close vs EMA20",
      dataIndex: "closeAboveEma20Atr",
      key: "closeAboveEma20Atr",
      filters: buildExactFilters(signalRows, (row) => row.closeAboveEma20Atr, formatAtrDistance),
      onFilter: (value, row) => matchesExactFilter(value, row, (item) => item.closeAboveEma20Atr),
      sorter: (left, right) => compareNullableNumbers(left.closeAboveEma20Atr, right.closeAboveEma20Atr),
      filterSearch: true,
      render: formatAtrDistance,
    },
    {
      title: "Volume / 20D avg",
      dataIndex: "volumeVs20SessionAverageRatio",
      key: "volumeVs20SessionAverageRatio",
      filters: buildExactFilters(signalRows, (row) => row.volumeVs20SessionAverageRatio, (value) => value == null ? "—" : `${value.toFixed(2)}×`),
      onFilter: (value, row) => matchesExactFilter(value, row, (item) => item.volumeVs20SessionAverageRatio),
      sorter: (left, right) => compareNullableNumbers(left.volumeVs20SessionAverageRatio, right.volumeVs20SessionAverageRatio),
      filterSearch: true,
      render: (value: number | null) => value == null ? "—" : `${value.toFixed(2)}×`,
    },
    {
      title: "Entry",
      key: "entry",
      filters: buildExactFilters(signalRows, (row) => row.entryDate, (value) => formatDate(value as string | null)),
      onFilter: (value, row) => matchesExactFilter(value, row, (item) => item.entryDate),
      sorter: (left, right) => compareNullableStrings(left.entryDate, right.entryDate),
      filterSearch: true,
      render: (_, row) => row.entryDate ? `${formatDate(row.entryDate)} · ${formatPrice(row.entryPrice)}` : "—",
    },
    {
      title: "5% target",
      dataIndex: "daysTo5PctTarget",
      key: "daysTo5PctTarget",
      filters: buildExactFilters(signalRows, (row) => row.daysTo5PctTarget, formatDays),
      onFilter: (value, row) => matchesExactFilter(value, row, (item) => item.daysTo5PctTarget),
      sorter: (left, right) => compareNullableNumbers(left.daysTo5PctTarget, right.daysTo5PctTarget),
      filterSearch: true,
      render: formatDays,
    },
    {
      title: "10% target",
      dataIndex: "daysTo10PctTarget",
      key: "daysTo10PctTarget",
      filters: buildExactFilters(signalRows, (row) => row.daysTo10PctTarget, formatDays),
      onFilter: (value, row) => matchesExactFilter(value, row, (item) => item.daysTo10PctTarget),
      sorter: (left, right) => compareNullableNumbers(left.daysTo10PctTarget, right.daysTo10PctTarget),
      filterSearch: true,
      render: formatDays,
    },
    {
      title: "20% target",
      dataIndex: "daysTo20PctTarget",
      key: "daysTo20PctTarget",
      filters: buildExactFilters(signalRows, (row) => row.daysTo20PctTarget, formatDays),
      onFilter: (value, row) => matchesExactFilter(value, row, (item) => item.daysTo20PctTarget),
      sorter: (left, right) => compareNullableNumbers(left.daysTo20PctTarget, right.daysTo20PctTarget),
      filterSearch: true,
      render: formatDays,
    },
  ], [signalRows]);

  const targetPerformanceColumns: ColumnsType<EmaContinuationTargetPerformance> = useMemo(() => [
    {
      title: "Target",
      dataIndex: "targetPct",
      key: "targetPct",
      filters: buildExactFilters(targetRows, (row) => row.targetPct, (value) => value == null ? "—" : `${value}%`),
      onFilter: (value, row) => matchesExactFilter(value, row, (item) => item.targetPct),
      sorter: (left, right) => left.targetPct - right.targetPct,
      filterSearch: true,
      render: (value: number) => `${value}%`,
    },
    {
      title: "Observations",
      dataIndex: "observations",
      key: "observations",
      filters: buildExactFilters(targetRows, (row) => row.observations),
      onFilter: (value, row) => matchesExactFilter(value, row, (item) => item.observations),
      sorter: (left, right) => left.observations - right.observations,
      filterSearch: true,
    },
    {
      title: "Reached",
      dataIndex: "hitCount",
      key: "hitCount",
      filters: buildExactFilters(targetRows, (row) => row.hitCount),
      onFilter: (value, row) => matchesExactFilter(value, row, (item) => item.hitCount),
      sorter: (left, right) => left.hitCount - right.hitCount,
      filterSearch: true,
    },
    {
      title: "Hit rate",
      dataIndex: "hitRatePct",
      key: "hitRatePct",
      filters: buildExactFilters(targetRows, (row) => row.hitRatePct, formatRate),
      onFilter: (value, row) => matchesExactFilter(value, row, (item) => item.hitRatePct),
      sorter: (left, right) => compareNullableNumbers(left.hitRatePct, right.hitRatePct),
      filterSearch: true,
      render: formatRate,
    },
    {
      title: "Average time",
      dataIndex: "averageDaysToHit",
      key: "averageDaysToHit",
      filters: buildExactFilters(targetRows, (row) => row.averageDaysToHit, (value) => value == null ? "—" : `${value.toFixed(1)} sessions`),
      onFilter: (value, row) => matchesExactFilter(value, row, (item) => item.averageDaysToHit),
      sorter: (left, right) => compareNullableNumbers(left.averageDaysToHit, right.averageDaysToHit),
      filterSearch: true,
      render: (value: number | null) => value == null ? "—" : `${value.toFixed(1)} sessions`,
    },
  ], [targetRows]);

  const watchlistColumns: ColumnsType<EmaContinuationWatchlistRow> = useMemo(() => [
    {
      title: "Status",
      dataIndex: "status",
      key: "status",
      filters: buildExactFilters(watchlistRows, (row) => row.status, (value) => watchlistStatusLabel(value as EmaContinuationWatchlistRow["status"])),
      onFilter: (value, row) => matchesExactFilter(value, row, (item) => item.status),
      sorter: (left, right) => left.status.localeCompare(right.status),
      filterSearch: true,
      render: (value: EmaContinuationWatchlistRow["status"]) => <Tag color={value === "ANALYZED" ? "green" : "red"}>{watchlistStatusLabel(value)}</Tag>,
    },
    {
      title: "Symbol",
      dataIndex: "symbol",
      key: "symbol",
      filters: buildExactFilters(watchlistRows, (row) => row.symbol),
      onFilter: (value, row) => matchesExactFilter(value, row, (item) => item.symbol),
      sorter: (left, right) => left.symbol.localeCompare(right.symbol),
      filterSearch: true,
    },
    {
      title: "Company",
      dataIndex: "companyName",
      key: "companyName",
      filters: buildExactFilters(watchlistRows, (row) => row.companyName),
      onFilter: (value, row) => matchesExactFilter(value, row, (item) => item.companyName),
      sorter: (left, right) => left.companyName.localeCompare(right.companyName),
      filterSearch: true,
    },
    {
      title: "EMA cross",
      dataIndex: "emaCrossCount",
      key: "emaCrossCount",
      filters: buildExactFilters(watchlistRows, (row) => row.emaCrossCount),
      onFilter: (value, row) => matchesExactFilter(value, row, (item) => item.emaCrossCount),
      sorter: (left, right) => left.emaCrossCount - right.emaCrossCount,
      filterSearch: true,
    },
    {
      title: "Base breakout",
      dataIndex: "baseBreakoutCount",
      key: "baseBreakoutCount",
      filters: buildExactFilters(watchlistRows, (row) => row.baseBreakoutCount),
      onFilter: (value, row) => matchesExactFilter(value, row, (item) => item.baseBreakoutCount),
      sorter: (left, right) => left.baseBreakoutCount - right.baseBreakoutCount,
      filterSearch: true,
    },
    {
      title: "Total entries",
      dataIndex: "totalEntryCount",
      key: "totalEntryCount",
      filters: buildExactFilters(watchlistRows, (row) => row.totalEntryCount),
      onFilter: (value, row) => matchesExactFilter(value, row, (item) => item.totalEntryCount),
      sorter: (left, right) => left.totalEntryCount - right.totalEntryCount,
      filterSearch: true,
    },
    {
      title: "Latest signal",
      dataIndex: "latestSignalDate",
      key: "latestSignalDate",
      filters: buildExactFilters(watchlistRows, (row) => row.latestSignalDate, (value) => formatDate(value as string | null)),
      onFilter: (value, row) => matchesExactFilter(value, row, (item) => item.latestSignalDate),
      sorter: (left, right) => compareNullableStrings(left.latestSignalDate, right.latestSignalDate),
      filterSearch: true,
      render: formatDate,
    },
    {
      title: "Latest type",
      dataIndex: "latestSignalType",
      key: "latestSignalType",
      filters: buildExactFilters(watchlistRows, (row) => row.latestSignalType, (value) => value == null ? "—" : signalLabel(value as EmaContinuationSignal["type"])),
      onFilter: (value, row) => matchesExactFilter(value, row, (item) => item.latestSignalType),
      sorter: (left, right) => compareNullableStrings(left.latestSignalType, right.latestSignalType),
      filterSearch: true,
      render: (value: EmaContinuationSignal["type"] | null) => value ? signalLabel(value) : "—",
    },
    {
      title: "Issue",
      dataIndex: "errorMessage",
      key: "errorMessage",
      filters: buildExactFilters(watchlistRows, (row) => row.errorMessage),
      onFilter: (value, row) => matchesExactFilter(value, row, (item) => item.errorMessage),
      sorter: (left, right) => compareNullableStrings(left.errorMessage, right.errorMessage),
      filterSearch: true,
      render: (value: string | null) => value ?? "—",
    },
  ], [watchlistRows]);

  const selectedWatchlist = watchlists.find((option) => option.value === watchlistKey);

  return (
    <div className="ema-continuation-page">
      <Card className="ema-continuation-controls">
        <Space orientation="vertical" size={10} style={{ width: "100%" }}>
          <div>
            <Title level={3} style={{ margin: 0 }}>EMA Cross + Base Breakout Study</Title>
            <Text type="secondary">Daily study from 1 Jan 2026 · buy the EMA cross, or buy the later consolidation-base breakout.</Text>
          </div>
          <Space wrap>
            <Select
              aria-label="Watchlist"
              placeholder="Select watchlist"
              value={watchlistKey}
              loading={watchlistLoading}
              onChange={setWatchlistKey}
              options={watchlists.map((option) => ({ value: option.value, label: `${option.label} (${option.count})` }))}
              style={{ width: 270 }}
            />
            <Select
              aria-label="Stock"
              placeholder="Select one stock"
              value={selectedMember?.trading_symbol}
              loading={memberLoading}
              disabled={!watchlistKey}
              showSearch
              optionFilterProp="label"
              onChange={(symbol) => setSelectedMember(members.find((member) => member.trading_symbol === symbol))}
              options={members.map((member) => ({ value: member.trading_symbol, label: `${member.trading_symbol} · ${member.company_name}` }))}
              style={{ width: 300 }}
            />
            <Button type="primary" loading={loading} disabled={!selectedMember || !watchlistKey} onClick={runStudy}>
              Run Study{selectedMember ? ` · ${selectedMember.trading_symbol}` : ""}
            </Button>
            <Button loading={watchlistStudyLoading} disabled={!watchlistKey} onClick={runCompleteWatchlist}>
              Run Complete Watchlist
            </Button>
          </Space>
          {selectedWatchlist && <Text className="ema-continuation-muted">Selected universe: {selectedWatchlist.label}</Text>}
        </Space>
      </Card>

      {(pageError || error || watchlistStudyError) && <Alert type="error" showIcon message={pageError ?? error ?? watchlistStudyError} closable onClose={() => { setPageError(null); }} style={{ marginBottom: 12 }} />}
      {watchlistData && (
        <Card
          title={`Watchlist results · ${watchlistData.analyzedCount}/${watchlistData.stockCount} analyzed`}
          extra={<Text type="secondary">{formatDate(watchlistData.testedFromDate)} → {formatDate(watchlistData.testedToDate)}</Text>}
          style={{ marginBottom: 12 }}
        >
          <Table
            rowKey="symbol"
            columns={watchlistColumns}
            dataSource={watchlistData.rows}
            size="small"
            pagination={{ pageSize: 25 }}
            scroll={{ x: 1250 }}
            onRow={(row) => ({
              onClick: () => setSelectedMember(members.find((member) => member.trading_symbol === row.symbol)),
              style: { cursor: "pointer" },
            })}
          />
          <Text type="secondary">Click a stock row to select it; run the stock study to load its detailed chart.</Text>
        </Card>
      )}
      {loading && <Card><Spin tip="Loading daily candles and calculating signals…" /></Card>}
      {!loading && !data && !watchlistData && <Card><Empty description="Select a watchlist and stock, or run the complete watchlist study." /></Card>}

      {data && (
        <Space orientation="vertical" size={12} style={{ width: "100%" }}>
          <Row gutter={[12, 12]}>
            <Col xs={12} md={6}><Card className="ema-continuation-metric"><Statistic title={`EMA cross buys ≥ ${data.minimumVolumeRatio}×`} value={data.summary.emaCrossCount} /></Card></Col>
            <Col xs={12} md={6}><Card className="ema-continuation-metric"><Statistic title="Base breakout buys" value={data.summary.baseBreakoutCount} /></Card></Col>
            <Col xs={12} md={6}><Card className="ema-continuation-metric"><Statistic title="5% target hit rate" value={data.targetPerformance.find((item) => item.targetPct === 5)?.hitRatePct ?? 0} suffix="%" precision={1} /></Card></Col>
            <Col xs={12} md={6}><Card className="ema-continuation-metric"><Statistic title="Latest breakout" value={formatDate(data.summary.latestSignalDate)} /></Card></Col>
          </Row>

          <Card title={`${data.symbol} · Daily candles`} extra={<Text type="secondary">{formatDate(data.testedFromDate)} → {formatDate(data.testedToDate)}</Text>}>
            <EmaContinuationChart candles={data.candles} signals={data.signals} />
            <Space wrap size={14} className="ema-continuation-muted">
              <span><Tag color="#2563eb">10 EMA</Tag></span><span><Tag color="#d97706">20 EMA</Tag></span><span><Tag color="#7c3aed">200 SMA</Tag></span><span>● EMA cross buy · ↑ base breakout buy</span>
            </Space>
          </Card>

          <Card title="Current session" extra={<Button icon={<ReloadOutlined />} loading={currentLoading} onClick={() => void refreshCurrent}>Refresh</Button>}>
            {current ? <div className="ema-continuation-current-grid">
              <Statistic title="Price" value={formatPrice(current.price)} suffix={current.isLiveQuote ? <Tag color="green">Live</Tag> : <Tag>Daily fallback</Tag>} />
              <Statistic title="State" value={current.state.replace(/_/g, " ")} />
              <Statistic title="Day range" value={`${formatPrice(current.low)} – ${formatPrice(current.high)}`} />
              <Statistic title="Change" value={formatPercent(current.changePct)} />
              <Statistic title="Distance to 10 EMA" value={formatPercent(current.distanceToEma10Pct)} />
              <Statistic title="Distance to 20 EMA" value={formatPercent(current.distanceToEma20Pct)} />
              <Statistic title="Completed close near high" value={current.closeNearHighPct == null ? "—" : `${current.closeNearHighPct.toFixed(1)}%`} />
              <Statistic title="Completed close vs EMA20" value={formatAtrDistance(current.closeAboveEma20Atr)} />
              <Statistic title="Distance to 200 SMA" value={formatPercent(current.distanceToSma200Pct)} />
              <Statistic title="Completed candle" value={formatDate(current.latestCompletedCandleDate)} />
            </div> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Current session unavailable" />}
          </Card>

          <Card title={`Signals (${data.signals.length})`}>
            <Table rowKey={(row) => `${row.type}-${row.date}`} columns={signalColumns} dataSource={data.signals} size="small" pagination={{ pageSize: 20 }} scroll={{ x: 1850 }} />
          </Card>

          <Card title="Target performance · both strategies">
            <Table
              rowKey="targetPct"
              size="small"
              pagination={false}
              dataSource={data.targetPerformance}
              columns={targetPerformanceColumns}
            />
            <Text type="secondary">{data.entryRule} Target checks use each subsequent daily high. {data.baseRule}</Text>
          </Card>
        </Space>
      )}
    </div>
  );
}
