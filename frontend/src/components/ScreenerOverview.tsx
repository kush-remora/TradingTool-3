import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  ReloadOutlined,
  SaveOutlined,
  SettingOutlined,
  SlidersOutlined,
} from "@ant-design/icons";
import {
  Alert,
  Button,
  Drawer,
  Input,
  InputNumber,
  Segmented,
  Select,
  Slider,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  Timeline,
  Typography,
  message,
} from "antd";
import type { TableColumnsType } from "antd";
import type { FilterValue, SorterResult, TablePaginationConfig } from "antd/es/table/interface";
import { useEffect, useMemo, useState } from "react";
import { useStockQuotes } from "../hooks/useStockQuotes";
import type {
  TechnicalContext,
  WeeklyPatternDetail,
  WeeklyPatternListResponse,
  WeeklyPatternResult,
} from "../types";
import { clearCache, getJson, postJson } from "../utils/api";
import {
  compareByNearestBuyZone,
  computeBuyZoneMetrics,
  matchesBuyZoneFilter,
  type BuyZoneFilter,
  type BuyZoneMetrics,
  type BuyZoneStatus,
} from "../utils/screenerBuyZone";
import {
  computeCustomRankScores,
  DEFAULT_RANK_WEIGHTS,
  type RankMetricWeights,
} from "../utils/weeklyScreenerRanking";
import {
  moveColumn,
  parseJsonSafely,
  updateColumnPin,
  updateColumnVisibility,
  type ColumnPin,
  type WeeklyScreenerColumnConfig,
  type WeeklyScreenerSortState,
} from "../utils/weeklyScreenerTableState";
import { StockBadge } from "./StockBadge";

const { CheckableTag } = Tag;
const { Text, Title, Paragraph } = Typography;

type PatternFilter = "All stocks" | "Weekly pattern" | "Strong (score > 70)" | "No pattern";
type RankMode = "system" | "custom";
type PlannerTargetProfile = "safe" | "recommended" | "aggressive";

const STORAGE_KEYS = {
  columns: "weekly_screener_columns_v1",
  filters: "weekly_screener_filters_v1",
  sort: "weekly_screener_sort_v1",
  rankPresets: "weekly_screener_rank_presets_v1",
  tradePlannerDefaults: "weekly_screener_trade_planner_defaults_v1",
} as const;

interface PlannerChecklistState {
  buyDayValid: boolean;
  reboundTriggered: boolean;
  overboughtOverrideAcknowledged: boolean;
  liquidityChecked: boolean;
}

interface PlannerSymbolState {
  entryOverride: number | null;
  checklist: PlannerChecklistState;
}

interface TradePlannerState {
  capital: number;
  riskPct: number;
  targetProfile: PlannerTargetProfile;
  symbolState: Record<string, PlannerSymbolState>;
}

interface FilterPreset {
  id: string;
  name: string;
  patternFilter: PatternFilter;
  buyZoneFilter: BuyZoneFilter;
  quickChips: QuickChipState;
  columnFilters: Record<string, string[]>;
}

interface RankPreset {
  id: string;
  name: string;
  weights: RankMetricWeights;
}

interface StoredFilterState {
  presets: FilterPreset[];
  activePresetId: string | null;
  patternFilter: PatternFilter;
  buyZoneFilter: BuyZoneFilter;
  quickChips: QuickChipState;
  tableFilters: Record<string, string[]>;
}

interface StoredRankState {
  presets: RankPreset[];
  activePresetId: string | null;
  mode: RankMode;
  weights: RankMetricWeights;
}

interface ScreenerRow extends WeeklyPatternResult {
  buyZoneMetrics: BuyZoneMetrics;
  bucketLabel: string;
  customRankScore: number;
  setupScoreValue: number;
  strikeRateValue: number | null;
  expectedSwingValue: number | null;
  baselineDistanceValue: number | null;
  reboundConsistencyPct: number;
}

interface QuickChipState {
  insideZone: boolean;
  nearZone: boolean;
  strongScore: boolean;
  buyDayToday: boolean;
}

interface DetailCacheEntry {
  detail: WeeklyPatternDetail | null;
  technicalContext: TechnicalContext | null;
  loading: boolean;
  error: string | null;
}

type ColumnKey =
  | "symbol"
  | "company"
  | "bucket"
  | "buyDay"
  | "cycleType"
  | "zoneStatus"
  | "ltpVsMin"
  | "baselineDistance"
  | "zonePosition"
  | "expectedSwing"
  | "vcpTightness"
  | "volumeSignature"
  | "strikeRate"
  | "reboundConsistency"
  | "setupScore"
  | "systemScore"
  | "customScore";

interface ColumnMeta {
  key: ColumnKey;
  title: string;
  width: number;
  defaultVisible: boolean;
  defaultPin: ColumnPin;
  getFilterValue: (row: ScreenerRow) => string;
  compare: (a: ScreenerRow, b: ScreenerRow) => number;
  render?: (row: ScreenerRow) => React.ReactNode;
}

const COLUMN_META: ColumnMeta[] = [
  {
    key: "symbol",
    title: "Stock",
    width: 220,
    defaultVisible: true,
    defaultPin: "left",
    getFilterValue: (row) => row.symbol,
    compare: (a, b) => a.symbol.localeCompare(b.symbol),
    render: (row) => (
      <div style={{ display: "flex", flexDirection: "column" }}>
        <StockBadge symbol={row.symbol} instrumentToken={row.instrumentToken} companyName={row.companyName} fontSize={14} />
        <Text type="secondary" style={{ fontSize: 11 }}>{row.companyName}</Text>
      </div>
    ),
  },
  {
    key: "company",
    title: "Company",
    width: 180,
    defaultVisible: false,
    defaultPin: "none",
    getFilterValue: (row) => row.companyName,
    compare: (a, b) => a.companyName.localeCompare(b.companyName),
    render: (row) => row.companyName,
  },
  {
    key: "bucket",
    title: "Universe Bucket",
    width: 220,
    defaultVisible: true,
    defaultPin: "none",
    getFilterValue: (row) => row.bucketLabel,
    compare: (a, b) => a.bucketLabel.localeCompare(b.bucketLabel),
    render: (row) => <Text style={{ fontSize: 12 }}>{row.bucketLabel}</Text>,
  },
  {
    key: "buyDay",
    title: "Buy Day",
    width: 120,
    defaultVisible: true,
    defaultPin: "none",
    getFilterValue: (row) => row.buyDay,
    compare: (a, b) => a.buyDay.localeCompare(b.buyDay),
  },
  {
    key: "cycleType",
    title: "Cycle",
    width: 100,
    defaultVisible: true,
    defaultPin: "none",
    getFilterValue: (row) => row.cycleType,
    compare: (a, b) => a.cycleType.localeCompare(b.cycleType),
    render: (row) => {
      const color = row.cycleType === "Weekly" ? "success" : row.cycleType === "Biweekly" ? "warning" : "default";
      return <Tag color={color}>{row.cycleType}</Tag>;
    },
  },
  {
    key: "zoneStatus",
    title: "Zone Status",
    width: 130,
    defaultVisible: true,
    defaultPin: "none",
    getFilterValue: (row) => buyZoneStatusLabel(row.buyZoneMetrics.status),
    compare: (a, b) => zoneSortValue(a.buyZoneMetrics.status) - zoneSortValue(b.buyZoneMetrics.status),
    render: (row) => <Tag color={buyZoneStatusColor(row.buyZoneMetrics.status)}>{buyZoneStatusLabel(row.buyZoneMetrics.status)}</Tag>,
  },
  {
    key: "ltpVsMin",
    title: "LTP vs Min Low %",
    width: 140,
    defaultVisible: true,
    defaultPin: "none",
    getFilterValue: (row) => toFilterValue(row.buyZoneMetrics.ltpVsMinLowPct),
    compare: (a, b) => compareNullableNumber(a.buyZoneMetrics.ltpVsMinLowPct, b.buyZoneMetrics.ltpVsMinLowPct),
    render: (row) => {
      const value = row.buyZoneMetrics.ltpVsMinLowPct;
      if (value === null) return <Text type="secondary">-</Text>;
      const color = value <= 5 ? "#389e0d" : value <= 15 ? "#fa8c16" : "#8c8c8c";
      return <Text strong style={{ color }}>{formatSignedPercent(value)}</Text>;
    },
  },
  {
    key: "baselineDistance",
    title: "Baseline Distance %",
    width: 150,
    defaultVisible: true,
    defaultPin: "none",
    getFilterValue: (row) => toFilterValue(row.baselineDistanceValue),
    compare: (a, b) => compareNullableNumber(a.baselineDistanceValue, b.baselineDistanceValue),
    render: (row) => renderPct(row.baselineDistanceValue),
  },
  {
    key: "zonePosition",
    title: "Zone Position",
    width: 150,
    defaultVisible: true,
    defaultPin: "none",
    getFilterValue: (row) => toFilterValue(row.buyZoneMetrics.zonePositionPct),
    compare: (a, b) => compareNullableNumber(a.buyZoneMetrics.zonePositionPct, b.buyZoneMetrics.zonePositionPct),
    render: (row) => {
      const raw = row.buyZoneMetrics.zonePositionPct;
      const clamped = row.buyZoneMetrics.zonePositionPctClamped;
      if (raw === null || clamped === null) return <Text type="secondary">-</Text>;
      return (
        <div style={{ display: "flex", flexDirection: "column", gap: 4 }}>
          <div style={{ width: 90, height: 8, borderRadius: 999, background: "#f0f0f0", overflow: "hidden" }}>
            <div
              style={{
                width: `${clamped}%`,
                height: "100%",
                background: row.buyZoneMetrics.status === "below" ? "#52c41a" : row.buyZoneMetrics.status === "inside" ? "#1677ff" : "#faad14",
              }}
            />
          </div>
          <Text type="secondary" style={{ fontSize: 11 }}>{raw < 0 ? "<0%" : raw > 100 ? ">100%" : `${Math.round(raw)}%`}</Text>
        </div>
      );
    },
  },
  {
    key: "expectedSwing",
    title: "Expected Swing %",
    width: 145,
    defaultVisible: true,
    defaultPin: "none",
    getFilterValue: (row) => toFilterValue(row.expectedSwingValue),
    compare: (a, b) => compareNullableNumber(a.expectedSwingValue, b.expectedSwingValue),
    render: (row) => renderPct(row.expectedSwingValue),
  },
  {
    key: "vcpTightness",
    title: "VCP Tightness (lower better)",
    width: 130,
    defaultVisible: true,
    defaultPin: "none",
    getFilterValue: (row) => toFilterValue(row.vcpTightnessPct),
    compare: (a, b) => compareNullableNumber(a.vcpTightnessPct, b.vcpTightnessPct),
    render: (row) => renderPct(row.vcpTightnessPct),
  },
  {
    key: "volumeSignature",
    title: "Volume Signature",
    width: 135,
    defaultVisible: true,
    defaultPin: "none",
    getFilterValue: (row) => toFilterValue(row.volumeSignatureRatio),
    compare: (a, b) => compareNullableNumber(a.volumeSignatureRatio, b.volumeSignatureRatio),
    render: (row) => renderNumeric(row.volumeSignatureRatio),
  },
  {
    key: "strikeRate",
    title: "Strike Rate %",
    width: 120,
    defaultVisible: true,
    defaultPin: "none",
    getFilterValue: (row) => toFilterValue(row.strikeRateValue),
    compare: (a, b) => compareNullableNumber(a.strikeRateValue, b.strikeRateValue),
    render: (row) => renderPct(row.strikeRateValue),
  },
  {
    key: "reboundConsistency",
    title: "Rebound Consistency %",
    width: 170,
    defaultVisible: true,
    defaultPin: "none",
    getFilterValue: (row) => toFilterValue(row.reboundConsistencyPct),
    compare: (a, b) => compareNullableNumber(a.reboundConsistencyPct, b.reboundConsistencyPct),
    render: (row) => (
      <Text>{row.reboundConsistency} / {row.weeksAnalyzed} ({row.reboundConsistencyPct.toFixed(0)}%)</Text>
    ),
  },
  {
    key: "setupScore",
    title: "Setup Score",
    width: 115,
    defaultVisible: true,
    defaultPin: "none",
    getFilterValue: (row) => toFilterValue(row.setupScoreValue),
    compare: (a, b) => compareNullableNumber(a.setupScoreValue, b.setupScoreValue),
    render: (row) => renderScore(row.setupScoreValue),
  },
  {
    key: "systemScore",
    title: "System Score",
    width: 115,
    defaultVisible: true,
    defaultPin: "none",
    getFilterValue: (row) => toFilterValue(row.compositeScore),
    compare: (a, b) => a.compositeScore - b.compositeScore,
    render: (row) => renderScore(row.compositeScore),
  },
  {
    key: "customScore",
    title: "Custom Score",
    width: 115,
    defaultVisible: true,
    defaultPin: "right",
    getFilterValue: (row) => toFilterValue(row.customRankScore),
    compare: (a, b) => a.customRankScore - b.customRankScore,
    render: (row) => <Text strong style={{ color: "#0958d9", fontSize: 15 }}>{row.customRankScore.toFixed(2)}</Text>,
  },
];

function defaultColumnConfig(): WeeklyScreenerColumnConfig[] {
  return COLUMN_META.map((column) => ({
    key: column.key,
    visible: column.defaultVisible,
    pin: column.defaultPin,
  }));
}

const DEFAULT_QUICK_CHIPS: QuickChipState = {
  insideZone: false,
  nearZone: false,
  strongScore: false,
  buyDayToday: false,
};

const DEFAULT_SORT_STATE: WeeklyScreenerSortState = {
  columnKey: null,
  order: null,
};

const DEFAULT_TRADE_PLANNER: TradePlannerState = {
  capital: 500_000,
  riskPct: 1,
  targetProfile: "recommended",
  symbolState: {},
};

interface ScreenerOverviewProps {
  onSelectSymbol?: (symbol: string) => void;
}

function compareNullableNumber(a: number | null | undefined, b: number | null | undefined): number {
  const left = a ?? Number.POSITIVE_INFINITY;
  const right = b ?? Number.POSITIVE_INFINITY;
  return left - right;
}

function toFilterValue(value: number | string | null | undefined): string {
  if (value === null || value === undefined) return "-";
  if (typeof value === "number") return value.toFixed(2);
  return value;
}

function renderPct(value: number | null | undefined): React.ReactNode {
  if (value === null || value === undefined) return <Text type="secondary">-</Text>;
  return <Text>{value.toFixed(2)}%</Text>;
}

function renderNumeric(value: number | null | undefined): React.ReactNode {
  if (value === null || value === undefined) return <Text type="secondary">-</Text>;
  return <Text>{value.toFixed(2)}</Text>;
}

function renderScore(value: number): React.ReactNode {
  const color = value > 70 ? "#389e0d" : value > 40 ? "#fa8c16" : "#8c8c8c";
  return <Text strong style={{ color, fontSize: 15 }}>{value.toFixed(0)}</Text>;
}

function roundPrice(value: number): number {
  return Math.round(value * 100) / 100;
}

function formatSignedPercent(value: number | null): string {
  if (value === null) return "-";
  return `${value > 0 ? "+" : ""}${value.toFixed(2)}%`;
}

function formatPrice(value: number | null | undefined): string {
  if (value === null || value === undefined) return "-";
  return `₹${value.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function buyZoneStatusLabel(status: BuyZoneStatus): string {
  if (status === "below") return "Below zone";
  if (status === "inside") return "Inside zone";
  if (status === "above") return "Above zone";
  return "-";
}

function buyZoneStatusColor(status: BuyZoneStatus): "success" | "warning" | "processing" | "default" {
  if (status === "below") return "success";
  if (status === "inside") return "processing";
  if (status === "above") return "warning";
  return "default";
}

function zoneSortValue(status: BuyZoneStatus): number {
  if (status === "below") return 0;
  if (status === "inside") return 1;
  if (status === "above") return 2;
  return 3;
}

function sparklinePath(values: number[], width: number, height: number): string {
  if (values.length === 0) return "";
  const min = Math.min(...values);
  const max = Math.max(...values);
  const yScale = (value: number): number => {
    if (max === min) return height / 2;
    return height - ((value - min) / (max - min)) * height;
  };
  return values
    .map((value, index) => {
      const x = (index / Math.max(values.length - 1, 1)) * width;
      const y = yScale(value);
      return `${index === 0 ? "M" : "L"}${x.toFixed(2)},${y.toFixed(2)}`;
    })
    .join(" ");
}

function createId(prefix: string): string {
  return `${prefix}_${Date.now()}_${Math.floor(Math.random() * 1000)}`;
}

function getTargetPct(row: ScreenerRow, profile: PlannerTargetProfile): number {
  const swingSetup = row.swingSetup;
  if (profile === "safe") return swingSetup?.safeTargetPct ?? 4;
  if (profile === "aggressive") return swingSetup?.aggressiveTargetPct ?? 8;
  return swingSetup?.recommendedTargetPct ?? row.targetRecommendation?.recommendedTargetPct ?? 6;
}

function getDefaultEntry(row: ScreenerRow, ltp: number | null): number | null {
  const zoneMin = row.swingSetup?.buyZoneMin ?? row.buyDayLowMin;
  const zoneMax = row.swingSetup?.buyZoneMax ?? row.buyDayLowMax;
  if (ltp === null) return roundPrice((zoneMin + zoneMax) / 2);
  if (ltp < zoneMin) return zoneMin;
  if (ltp > zoneMax) return zoneMax;
  return ltp;
}

function getPlannerStateForSymbol(state: TradePlannerState, symbol: string): PlannerSymbolState {
  return (
    state.symbolState[symbol] ?? {
      entryOverride: null,
      checklist: {
        buyDayValid: false,
        reboundTriggered: false,
        overboughtOverrideAcknowledged: false,
        liquidityChecked: false,
      },
    }
  );
}

export function ScreenerOverview({ onSelectSymbol }: ScreenerOverviewProps) {
  const [data, setData] = useState<WeeklyPatternListResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [patternFilter, setPatternFilter] = useState<PatternFilter>("All stocks");
  const [buyZoneFilter, setBuyZoneFilter] = useState<BuyZoneFilter>("All");
  const [quickChips, setQuickChips] = useState<QuickChipState>(DEFAULT_QUICK_CHIPS);
  const [columnConfigs, setColumnConfigs] = useState<WeeklyScreenerColumnConfig[]>(defaultColumnConfig);
  const [tableFilters, setTableFilters] = useState<Record<string, string[]>>({});
  const [sortState, setSortState] = useState<WeeklyScreenerSortState>(DEFAULT_SORT_STATE);
  const [columnsDrawerOpen, setColumnsDrawerOpen] = useState(false);
  const [rankingDrawerOpen, setRankingDrawerOpen] = useState(false);
  const [rankMode, setRankMode] = useState<RankMode>("system");
  const [rankWeights, setRankWeights] = useState<RankMetricWeights>(DEFAULT_RANK_WEIGHTS);
  const [rankPresets, setRankPresets] = useState<RankPreset[]>([]);
  const [activeRankPresetId, setActiveRankPresetId] = useState<string | null>(null);
  const [filterPresets, setFilterPresets] = useState<FilterPreset[]>([]);
  const [activeFilterPresetId, setActiveFilterPresetId] = useState<string | null>(null);
  const [newFilterPresetName, setNewFilterPresetName] = useState("");
  const [newRankPresetName, setNewRankPresetName] = useState("");
  const [savingFilterPreset, setSavingFilterPreset] = useState(false);
  const [savingRankPreset, setSavingRankPreset] = useState(false);
  const [tradePlannerState, setTradePlannerState] = useState<TradePlannerState>(DEFAULT_TRADE_PLANNER);
  const [selectedSymbol, setSelectedSymbol] = useState<string | null>(null);
  const [detailTab, setDetailTab] = useState("setup");
  const [detailCache, setDetailCache] = useState<Record<string, DetailCacheEntry>>({});

  const fetchData = async (forceRefresh = false) => {
    setLoading(true);
    try {
      const path = "/api/screener/weekly-pattern";
      if (forceRefresh) clearCache(path);
      const json = await getJson<WeeklyPatternListResponse>(path);
      setData(json);
    } catch (err) {
      console.error(err);
      message.error("Failed to fetch pattern data");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void fetchData();

    const storedColumns = parseJsonSafely<WeeklyScreenerColumnConfig[]>(localStorage.getItem(STORAGE_KEYS.columns));
    const storedFilters = parseJsonSafely<StoredFilterState>(localStorage.getItem(STORAGE_KEYS.filters));
    const storedSort = parseJsonSafely<WeeklyScreenerSortState>(localStorage.getItem(STORAGE_KEYS.sort));
    const storedRankState = parseJsonSafely<StoredRankState>(localStorage.getItem(STORAGE_KEYS.rankPresets));
    const storedPlanner = parseJsonSafely<TradePlannerState>(localStorage.getItem(STORAGE_KEYS.tradePlannerDefaults));

    if (storedColumns && storedColumns.length > 0) {
      const validKeys = new Set(COLUMN_META.map((column) => column.key));
      const sanitized = storedColumns.filter((column) => validKeys.has(column.key as ColumnKey));
      const missing = COLUMN_META.filter((column) => !sanitized.some((stored) => stored.key === column.key)).map((column) => ({
        key: column.key,
        visible: column.defaultVisible,
        pin: column.defaultPin,
      }));
      if (sanitized.length > 0) setColumnConfigs([...sanitized, ...missing]);
    }
    if (storedFilters) {
      setFilterPresets(storedFilters.presets);
      setActiveFilterPresetId(storedFilters.activePresetId);
      setPatternFilter(storedFilters.patternFilter);
      setBuyZoneFilter(storedFilters.buyZoneFilter);
      setQuickChips(storedFilters.quickChips);
      setTableFilters(storedFilters.tableFilters);
    }
    if (storedSort) setSortState(storedSort);
    if (storedRankState) {
      setRankPresets(storedRankState.presets);
      setActiveRankPresetId(storedRankState.activePresetId);
      setRankMode(storedRankState.mode);
      setRankWeights(storedRankState.weights);
    }
    if (storedPlanner) setTradePlannerState(storedPlanner);
  }, []);

  useEffect(() => {
    localStorage.setItem(STORAGE_KEYS.columns, JSON.stringify(columnConfigs));
  }, [columnConfigs]);

  useEffect(() => {
    const payload: StoredFilterState = {
      presets: filterPresets,
      activePresetId: activeFilterPresetId,
      patternFilter,
      buyZoneFilter,
      quickChips,
      tableFilters,
    };
    localStorage.setItem(STORAGE_KEYS.filters, JSON.stringify(payload));
  }, [activeFilterPresetId, buyZoneFilter, filterPresets, patternFilter, quickChips, tableFilters]);

  useEffect(() => {
    localStorage.setItem(STORAGE_KEYS.sort, JSON.stringify(sortState));
  }, [sortState]);

  useEffect(() => {
    const payload: StoredRankState = {
      presets: rankPresets,
      activePresetId: activeRankPresetId,
      mode: rankMode,
      weights: rankWeights,
    };
    localStorage.setItem(STORAGE_KEYS.rankPresets, JSON.stringify(payload));
  }, [activeRankPresetId, rankMode, rankPresets, rankWeights]);

  useEffect(() => {
    localStorage.setItem(STORAGE_KEYS.tradePlannerDefaults, JSON.stringify(tradePlannerState));
  }, [tradePlannerState]);

  const handleSync = async () => {
    setSyncing(true);
    try {
      await postJson("/api/screener/sync", {});
      message.success("Sync complete");
      await fetchData(true);
    } catch {
      message.error("Sync failed");
    } finally {
      setSyncing(false);
    }
  };

  const patternFilteredResults = useMemo(() => {
    const results = data?.results ?? [];
    return results.filter((row) => {
      if (patternFilter === "Weekly pattern") return row.patternConfirmed;
      if (patternFilter === "Strong (score > 70)") return row.compositeScore > 70;
      if (patternFilter === "No pattern") return !row.patternConfirmed;
      return true;
    });
  }, [data, patternFilter]);

  const { quotesBySymbol } = useStockQuotes(patternFilteredResults.map((row) => row.symbol));
  const todayIst = new Intl.DateTimeFormat("en-US", { weekday: "short", timeZone: "Asia/Kolkata" }).format(new Date());

  const rowsWithBuyZone = useMemo<ScreenerRow[]>(() => {
    const baseRows = patternFilteredResults.map((row) => {
      const quote = quotesBySymbol[row.symbol.toUpperCase()];
      const metrics = computeBuyZoneMetrics({
        ltp: quote?.ltp ?? null,
        buyDayLowMin: row.buyDayLowMin,
        buyDayLowMax: row.buyDayLowMax,
      });
      const strikeRate = row.mondayStrikeRatePct ?? (row.reboundConsistency > 0 ? (row.swingConsistency / row.reboundConsistency) * 100 : null);
      const expectedSwing = row.expectedSwingPct ?? row.swingSetup?.expectedSwingPct ?? row.targetRecommendation?.expectedSwingPct ?? row.swingAvgPct;
      const baselineDistance = row.baselineDistancePct ?? metrics.ltpVsMinLowPct;
      const setupScore = row.setupQualityScore ?? row.compositeScore;
      const reboundConsistencyPct = row.weeksAnalyzed > 0 ? (row.reboundConsistency / row.weeksAnalyzed) * 100 : 0;

      return {
        ...row,
        buyZoneMetrics: metrics,
        bucketLabel: row.sourceBuckets && row.sourceBuckets.length > 0 ? row.sourceBuckets.join(", ") : "Watchlist",
        customRankScore: 0,
        strikeRateValue: strikeRate,
        expectedSwingValue: expectedSwing,
        baselineDistanceValue: baselineDistance,
        setupScoreValue: setupScore,
        reboundConsistencyPct,
      };
    });

    const scores = computeCustomRankScores(
      baseRows.map((row) => ({
        symbol: row.symbol,
        vcpTightness: row.vcpTightnessPct,
        volumeSignature: row.volumeSignatureRatio,
        strikeRate: row.strikeRateValue,
        baselineDistance: row.baselineDistanceValue,
        reboundConsistency: row.reboundConsistencyPct,
        expectedSwing: row.expectedSwingValue,
      })),
      rankWeights,
    );

    const scoreMap = new Map(scores.map((score) => [score.symbol, score.score]));
    return baseRows.map((row) => ({
      ...row,
      customRankScore: scoreMap.get(row.symbol) ?? 50,
    }));
  }, [patternFilteredResults, quotesBySymbol, rankWeights]);

  const chipFilteredRows = useMemo(() => {
    return rowsWithBuyZone.filter((row) => {
      if (!matchesBuyZoneFilter(row.buyZoneMetrics, buyZoneFilter)) return false;
      if (quickChips.insideZone && row.buyZoneMetrics.status !== "inside") return false;
      if (quickChips.nearZone && !matchesBuyZoneFilter(row.buyZoneMetrics, "Near (<=5% above min)")) return false;
      if (quickChips.strongScore && row.setupScoreValue < 70) return false;
      if (quickChips.buyDayToday && row.buyDay !== todayIst) return false;
      return true;
    });
  }, [rowsWithBuyZone, buyZoneFilter, quickChips, todayIst]);

  const tableRows = useMemo(() => {
    if (sortState.columnKey !== null || sortState.order !== null) return chipFilteredRows;
    if (rankMode === "custom") {
      return [...chipFilteredRows].sort((a, b) => b.customRankScore - a.customRankScore);
    }
    return [...chipFilteredRows].sort((a, b) => compareByNearestBuyZone(a.buyZoneMetrics, b.buyZoneMetrics));
  }, [chipFilteredRows, rankMode, sortState]);

  const allRowsForFilterValues = useMemo(() => chipFilteredRows, [chipFilteredRows]);

  const metaByKey = useMemo(() => {
    const byKey = new Map<ColumnKey, ColumnMeta>();
    COLUMN_META.forEach((meta) => {
      byKey.set(meta.key, meta);
    });
    return byKey;
  }, []);

  const columns: TableColumnsType<ScreenerRow> = useMemo(() => {
    const ordered = columnConfigs
      .map((config) => {
        const meta = metaByKey.get(config.key as ColumnKey);
        if (!meta || !config.visible) return null;

        const options = Array.from(new Set(allRowsForFilterValues.map((row) => meta.getFilterValue(row)))).sort();
        const filteredValue = tableFilters[meta.key] ?? null;

        return {
          title: meta.title,
          key: meta.key,
          dataIndex: meta.key,
          width: meta.width,
          fixed: config.pin === "none" ? undefined : config.pin,
          sorter: (a: ScreenerRow, b: ScreenerRow) => meta.compare(a, b),
          sortOrder: sortState.columnKey === meta.key ? sortState.order : null,
          filters: options.map((option) => ({ text: option, value: option })),
          filterMultiple: true,
          filterSearch: true,
          filteredValue,
          onFilter: (value: boolean | React.Key, record: ScreenerRow) => meta.getFilterValue(record) === String(value),
          render: (_: unknown, record: ScreenerRow) => (meta.render ? meta.render(record) : meta.getFilterValue(record)),
        };
      })
      .filter((column): column is NonNullable<typeof column> => column !== null);

    return ordered;
  }, [allRowsForFilterValues, columnConfigs, metaByKey, sortState, tableFilters]);

  const selectedRow = useMemo(() => {
    if (!selectedSymbol) return null;
    return rowsWithBuyZone.find((row) => row.symbol === selectedSymbol) ?? null;
  }, [rowsWithBuyZone, selectedSymbol]);

  const selectedDetail = selectedSymbol ? detailCache[selectedSymbol] : undefined;

  const getTradeSignal = (record: ScreenerRow) => {
    const quote = quotesBySymbol[record.symbol.toUpperCase()];
    const todayLow = quote?.day_low ?? null;
    const todayHigh = quote?.day_high ?? null;
    const entryPrice = todayLow !== null ? roundPrice(todayLow * (1 + record.entryReboundPct / 100)) : null;
    const reboundHit = entryPrice !== null && todayHigh !== null ? todayHigh >= entryPrice : null;

    return {
      isRecommendedToday: todayIst === record.buyDay,
      todayLow,
      todayHigh,
      entryPrice,
      reboundHit,
      ltp: quote?.ltp ?? null,
      volume: quote?.volume ?? null,
    };
  };

  const updatePlannerSymbol = (symbol: string, updater: (prev: PlannerSymbolState) => PlannerSymbolState) => {
    setTradePlannerState((prev) => {
      const current = getPlannerStateForSymbol(prev, symbol);
      const next = updater(current);
      return {
        ...prev,
        symbolState: {
          ...prev.symbolState,
          [symbol]: next,
        },
      };
    });
  };

  const renderTradePlanner = (row: ScreenerRow) => {
    const signal = getTradeSignal(row);
    const symbolState = getPlannerStateForSymbol(tradePlannerState, row.symbol);
    const entry = symbolState.entryOverride ?? getDefaultEntry(row, signal.ltp);
    const stopLossPct = row.swingSetup?.hardStopLossPct ?? row.stopLossPct;
    const targetPct = getTargetPct(row, tradePlannerState.targetProfile);

    if (entry === null) {
      return <Alert type="info" showIcon message="Live price and buy zone are unavailable for planner calculations." />;
    }

    const stopPrice = roundPrice(entry * (1 - stopLossPct / 100));
    const targetPrice = roundPrice(entry * (1 + targetPct / 100));
    const riskPerShare = Math.max(0, roundPrice(entry - stopPrice));
    const rewardPerShare = Math.max(0, roundPrice(targetPrice - entry));
    const maxRisk = roundPrice((tradePlannerState.capital * tradePlannerState.riskPct) / 100);
    const qtyByRisk = riskPerShare > 0 ? Math.floor(maxRisk / riskPerShare) : 0;
    const qtyByCapital = entry > 0 ? Math.floor(tradePlannerState.capital / entry) : 0;
    const quantity = Math.max(0, Math.min(qtyByRisk, qtyByCapital));
    const totalRisk = roundPrice(quantity * riskPerShare);
    const rewardAmount = roundPrice(quantity * rewardPerShare);
    const rr = riskPerShare > 0 ? roundPrice(rewardPerShare / riskPerShare) : 0;

    return (
      <div style={{ border: "1px solid #f0f0f0", borderRadius: 8, padding: 12, background: "#fafafa" }}>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(4, minmax(150px, 1fr))", gap: 10, marginBottom: 12 }}>
          <div>
            <Text type="secondary" style={{ fontSize: 11 }}>Capital</Text>
            <InputNumber
              style={{ width: "100%" }}
              min={10_000}
              step={10_000}
              value={tradePlannerState.capital}
              onChange={(value) => setTradePlannerState((prev) => ({ ...prev, capital: Number(value ?? prev.capital) }))}
            />
          </div>
          <div>
            <Text type="secondary" style={{ fontSize: 11 }}>Risk % / trade</Text>
            <InputNumber
              style={{ width: "100%" }}
              min={0.1}
              max={10}
              step={0.1}
              value={tradePlannerState.riskPct}
              onChange={(value) => setTradePlannerState((prev) => ({ ...prev, riskPct: Number(value ?? prev.riskPct) }))}
            />
          </div>
          <div>
            <Text type="secondary" style={{ fontSize: 11 }}>Target profile</Text>
            <Select
              style={{ width: "100%" }}
              value={tradePlannerState.targetProfile}
              onChange={(value: PlannerTargetProfile) => setTradePlannerState((prev) => ({ ...prev, targetProfile: value }))}
              options={[
                { value: "safe", label: "Safe" },
                { value: "recommended", label: "Recommended" },
                { value: "aggressive", label: "Aggressive" },
              ]}
            />
          </div>
          <div>
            <Text type="secondary" style={{ fontSize: 11 }}>Entry override</Text>
            <InputNumber
              style={{ width: "100%" }}
              value={symbolState.entryOverride ?? undefined}
              placeholder="Auto"
              onChange={(value) => {
                updatePlannerSymbol(row.symbol, (prev) => ({
                  ...prev,
                  entryOverride: value === null ? null : Number(value),
                }));
              }}
            />
          </div>
        </div>

        <div style={{ display: "grid", gridTemplateColumns: "repeat(4, minmax(140px, 1fr))", gap: 10, marginBottom: 10 }}>
          <Stat label="Entry" value={formatPrice(entry)} />
          <Stat label="Stop" value={formatPrice(stopPrice)} />
          <Stat label="Target" value={formatPrice(targetPrice)} />
          <Stat label="Risk/Share" value={formatPrice(riskPerShare)} />
          <Stat label="Qty" value={quantity.toString()} />
          <Stat label="Total Risk ₹" value={formatPrice(totalRisk)} />
          <Stat label="Reward ₹" value={formatPrice(rewardAmount)} />
          <Stat label="R:R" value={rr.toFixed(2)} />
        </div>

        <Space size={[10, 8]} wrap>
          <CheckableTag
            checked={symbolState.checklist.buyDayValid}
            onChange={(checked) =>
              updatePlannerSymbol(row.symbol, (prev) => ({
                ...prev,
                checklist: { ...prev.checklist, buyDayValid: checked },
              }))
            }
          >
            Buy-day valid
          </CheckableTag>
          <CheckableTag
            checked={symbolState.checklist.reboundTriggered}
            onChange={(checked) =>
              updatePlannerSymbol(row.symbol, (prev) => ({
                ...prev,
                checklist: { ...prev.checklist, reboundTriggered: checked },
              }))
            }
          >
            Rebound triggered
          </CheckableTag>
          <CheckableTag
            checked={symbolState.checklist.overboughtOverrideAcknowledged}
            onChange={(checked) =>
              updatePlannerSymbol(row.symbol, (prev) => ({
                ...prev,
                checklist: { ...prev.checklist, overboughtOverrideAcknowledged: checked },
              }))
            }
          >
            Overbought override
          </CheckableTag>
          <CheckableTag
            checked={symbolState.checklist.liquidityChecked}
            onChange={(checked) =>
              updatePlannerSymbol(row.symbol, (prev) => ({
                ...prev,
                checklist: { ...prev.checklist, liquidityChecked: checked },
              }))
            }
          >
            Liquidity checked
          </CheckableTag>
        </Space>
      </div>
    );
  };

  const openDetails = async (symbol: string) => {
    setSelectedSymbol(symbol);
    setDetailTab("setup");
    onSelectSymbol?.(symbol);

    const existing = detailCache[symbol];
    if (existing && (existing.detail || existing.error || existing.loading)) return;

    setDetailCache((prev) => ({
      ...prev,
      [symbol]: {
        detail: null,
        technicalContext: null,
        loading: true,
        error: null,
      },
    }));

    try {
      const [detail, technicalContext] = await Promise.all([
        getJson<WeeklyPatternDetail>(`/api/screener/weekly-pattern/${symbol}`),
        getJson<TechnicalContext>(`/api/stock/${symbol}/technical-context`),
      ]);

      setDetailCache((prev) => ({
        ...prev,
        [symbol]: {
          detail,
          technicalContext,
          loading: false,
          error: null,
        },
      }));
    } catch (err) {
      setDetailCache((prev) => ({
        ...prev,
        [symbol]: {
          detail: null,
          technicalContext: null,
          loading: false,
          error: err instanceof Error ? err.message : "Failed to load details",
        },
      }));
    }
  };

  const handleTableChange = (
    _pagination: TablePaginationConfig,
    filters: Record<string, FilterValue | null>,
    sorter: SorterResult<ScreenerRow> | SorterResult<ScreenerRow>[],
  ) => {
    const nextFilters: Record<string, string[]> = {};
    Object.entries(filters).forEach(([key, value]) => {
      if (!value || value.length === 0) return;
      nextFilters[key] = value.map((entry) => String(entry));
    });
    setTableFilters(nextFilters);

    const chosenSorter = Array.isArray(sorter) ? sorter[0] : sorter;
    if (chosenSorter && chosenSorter.columnKey) {
      setSortState({
        columnKey: String(chosenSorter.columnKey),
        order: chosenSorter.order ?? null,
      });
    } else {
      setSortState(DEFAULT_SORT_STATE);
    }
  };

  const saveFilterPreset = () => {
    const name = newFilterPresetName.trim();
    if (!name) {
      message.warning("Enter a filter preset name");
      return;
    }

    const preset: FilterPreset = {
      id: createId("filter"),
      name,
      patternFilter,
      buyZoneFilter,
      quickChips,
      columnFilters: tableFilters,
    };
    setFilterPresets((prev) => [...prev, preset]);
    setNewFilterPresetName("");
    setSavingFilterPreset(false);
    message.success("Filter preset saved");
  };

  const applyFilterPreset = (id: string) => {
    const preset = filterPresets.find((candidate) => candidate.id === id);
    if (!preset) return;
    setPatternFilter(preset.patternFilter);
    setBuyZoneFilter(preset.buyZoneFilter);
    setQuickChips(preset.quickChips);
    setTableFilters(preset.columnFilters);
    setActiveFilterPresetId(id);
  };

  const deleteFilterPreset = (id: string) => {
    setFilterPresets((prev) => prev.filter((preset) => preset.id !== id));
    if (activeFilterPresetId === id) setActiveFilterPresetId(null);
  };

  const saveRankPreset = () => {
    const name = newRankPresetName.trim();
    if (!name) {
      message.warning("Enter a ranking preset name");
      return;
    }
    const preset: RankPreset = {
      id: createId("rank"),
      name,
      weights: rankWeights,
    };
    setRankPresets((prev) => [...prev, preset]);
    setActiveRankPresetId(preset.id);
    setNewRankPresetName("");
    setSavingRankPreset(false);
    message.success("Ranking preset saved");
  };

  const applyRankPreset = (id: string) => {
    const preset = rankPresets.find((candidate) => candidate.id === id);
    if (!preset) return;
    setRankWeights(preset.weights);
    setActiveRankPresetId(id);
  };

  const deleteRankPreset = (id: string) => {
    setRankPresets((prev) => prev.filter((preset) => preset.id !== id));
    if (activeRankPresetId === id) setActiveRankPresetId(null);
  };

  const lookbackWeeks = data?.lookbackWeeks ?? 8;

  return (
    <div style={{ padding: "12px 16px", height: "calc(100vh - 48px)" }}>
      <div
        style={{
          background: "#fff",
          borderRadius: 12,
          padding: 20,
          boxShadow: "0 4px 12px rgba(0,0,0,0.05)",
          height: "100%",
          display: "flex",
          flexDirection: "column",
        }}
      >
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
          <div>
            <Title level={4} style={{ margin: 0 }}>Pattern screener</Title>
            <Text type="secondary" style={{ fontSize: 13 }}>
              Last run: {data ? new Date(data.runAt).toLocaleString() : "Never"} • {lookbackWeeks}-week lookback
            </Text>
            {data?.universeSourceTags && data.universeSourceTags.length > 0 && (
              <div style={{ marginTop: 4 }}>
                <Text type="secondary" style={{ fontSize: 12 }}>Universe: {data.universeSourceTags.join(", ")}</Text>
              </div>
            )}
          </div>
          <Space>
            <Button icon={<SettingOutlined />} onClick={() => setColumnsDrawerOpen(true)}>Columns</Button>
            <Button icon={<SlidersOutlined />} onClick={() => setRankingDrawerOpen(true)}>Ranking</Button>
            <Button
              type="default"
              icon={<ReloadOutlined />}
              onClick={handleSync}
              loading={syncing}
              style={{ borderRadius: 6, fontWeight: 500 }}
            >
              Refresh patterns
            </Button>
          </Space>
        </div>

        <div style={{ marginBottom: 12, display: "flex", flexWrap: "wrap", gap: 12 }}>
          <Segmented
            options={["All stocks", "Weekly pattern", "Strong (score > 70)", "No pattern"]}
            value={patternFilter}
            onChange={(value) => setPatternFilter(value as PatternFilter)}
          />
          <Segmented
            options={["All", "Inside zone", "Near (<=5% above min)", "Below zone"]}
            value={buyZoneFilter}
            onChange={(value) => setBuyZoneFilter(value as BuyZoneFilter)}
          />
          <Segmented
            options={[
              { label: "System Score", value: "system" },
              { label: "Custom Weighted Score", value: "custom" },
            ]}
            value={rankMode}
            onChange={(value) => setRankMode(value as RankMode)}
          />
        </div>

        <div style={{ marginBottom: 12, display: "flex", flexWrap: "wrap", gap: 8, alignItems: "center" }}>
          <Text type="secondary" style={{ fontSize: 12 }}>Quick filters:</Text>
          <CheckableTag checked={quickChips.insideZone} onChange={(checked) => setQuickChips((prev) => ({ ...prev, insideZone: checked }))}>Inside zone</CheckableTag>
          <CheckableTag checked={quickChips.nearZone} onChange={(checked) => setQuickChips((prev) => ({ ...prev, nearZone: checked }))}>Near</CheckableTag>
          <CheckableTag checked={quickChips.strongScore} onChange={(checked) => setQuickChips((prev) => ({ ...prev, strongScore: checked }))}>Strong score</CheckableTag>
          <CheckableTag checked={quickChips.buyDayToday} onChange={(checked) => setQuickChips((prev) => ({ ...prev, buyDayToday: checked }))}>Buy day today</CheckableTag>

          <Select
            allowClear
            placeholder="Load filter preset"
            style={{ width: 220 }}
            value={activeFilterPresetId ?? undefined}
            onChange={(value) => {
              if (!value) {
                setActiveFilterPresetId(null);
                return;
              }
              applyFilterPreset(value);
            }}
            options={filterPresets.map((preset) => ({ value: preset.id, label: preset.name }))}
          />
          <Button icon={<SaveOutlined />} onClick={() => setSavingFilterPreset(true)}>Save Filter Preset</Button>
          {activeFilterPresetId && (
            <Button danger onClick={() => deleteFilterPreset(activeFilterPresetId)}>Delete Preset</Button>
          )}
        </div>

        {savingFilterPreset && (
          <div style={{ marginBottom: 10, display: "flex", gap: 8, alignItems: "center" }}>
            <Input
              style={{ width: 260 }}
              value={newFilterPresetName}
              onChange={(event) => setNewFilterPresetName(event.target.value)}
              placeholder="Preset name"
            />
            <Button type="primary" onClick={saveFilterPreset}>Save</Button>
            <Button onClick={() => setSavingFilterPreset(false)}>Cancel</Button>
          </div>
        )}

        <Table<ScreenerRow>
          dataSource={tableRows}
          columns={columns}
          rowKey="symbol"
          pagination={false}
          scroll={{ x: 2900, y: "calc(100vh - 350px)" }}
          loading={loading}
          onChange={handleTableChange}
          onRow={(record) => ({
            onClick: () => {
              void openDetails(record.symbol);
            },
            style: { cursor: "pointer" },
          })}
          expandable={{
            expandedRowRender: (record) => renderTradePlanner(record),
            rowExpandable: () => true,
          }}
          rowClassName={(record) => todayIst === record.buyDay ? "screener-row screener-row-recommended" : "screener-row"}
          size="small"
          style={{ flex: 1 }}
          sticky
        />
      </div>

      <Drawer
        title="Columns"
        placement="right"
        width={420}
        open={columnsDrawerOpen}
        onClose={() => setColumnsDrawerOpen(false)}
      >
        <Space direction="vertical" size={10} style={{ width: "100%" }}>
          {columnConfigs.map((config, index) => {
            const meta = metaByKey.get(config.key as ColumnKey);
            if (!meta) return null;
            return (
              <div key={config.key} style={{ display: "grid", gridTemplateColumns: "1fr auto auto auto", gap: 8, alignItems: "center" }}>
                <Space>
                  <Switch checked={config.visible} onChange={(checked) => setColumnConfigs((prev) => updateColumnVisibility(prev, config.key, checked))} />
                  <Text>{meta.title}</Text>
                </Space>
                <Select
                  style={{ width: 86 }}
                  value={config.pin}
                  onChange={(pin: ColumnPin) => setColumnConfigs((prev) => updateColumnPin(prev, config.key, pin))}
                  options={[
                    { label: "None", value: "none" },
                    { label: "Left", value: "left" },
                    { label: "Right", value: "right" },
                  ]}
                />
                <Button
                  icon={<ArrowUpOutlined />}
                  disabled={index === 0}
                  onClick={() => setColumnConfigs((prev) => moveColumn(prev, config.key, "up"))}
                />
                <Button
                  icon={<ArrowDownOutlined />}
                  disabled={index === columnConfigs.length - 1}
                  onClick={() => setColumnConfigs((prev) => moveColumn(prev, config.key, "down"))}
                />
              </div>
            );
          })}

          <Button onClick={() => setColumnConfigs(defaultColumnConfig())}>Reset to default layout</Button>
        </Space>
      </Drawer>

      <Drawer
        title="Ranking Builder"
        placement="right"
        width={440}
        open={rankingDrawerOpen}
        onClose={() => setRankingDrawerOpen(false)}
      >
        <Space direction="vertical" size={14} style={{ width: "100%" }}>
          <Select
            allowClear
            placeholder="Load ranking preset"
            style={{ width: "100%" }}
            value={activeRankPresetId ?? undefined}
            onChange={(value) => {
              if (!value) {
                setActiveRankPresetId(null);
                return;
              }
              applyRankPreset(value);
            }}
            options={rankPresets.map((preset) => ({ value: preset.id, label: preset.name }))}
          />

          <WeightSlider
            label="VCP tightness (inverse)"
            value={rankWeights.vcpTightness}
            onChange={(value) => setRankWeights((prev) => ({ ...prev, vcpTightness: value }))}
          />
          <WeightSlider
            label="Volume signature"
            value={rankWeights.volumeSignature}
            onChange={(value) => setRankWeights((prev) => ({ ...prev, volumeSignature: value }))}
          />
          <WeightSlider
            label="Strike rate"
            value={rankWeights.strikeRate}
            onChange={(value) => setRankWeights((prev) => ({ ...prev, strikeRate: value }))}
          />
          <WeightSlider
            label="Baseline distance (inverse)"
            value={rankWeights.baselineDistance}
            onChange={(value) => setRankWeights((prev) => ({ ...prev, baselineDistance: value }))}
          />
          <WeightSlider
            label="Rebound consistency"
            value={rankWeights.reboundConsistency}
            onChange={(value) => setRankWeights((prev) => ({ ...prev, reboundConsistency: value }))}
          />
          <WeightSlider
            label="Expected swing"
            value={rankWeights.expectedSwing}
            onChange={(value) => setRankWeights((prev) => ({ ...prev, expectedSwing: value }))}
          />

          <Button onClick={() => setRankWeights(DEFAULT_RANK_WEIGHTS)}>Reset weights</Button>

          <Button icon={<SaveOutlined />} onClick={() => setSavingRankPreset(true)}>Save Ranking Preset</Button>
          {activeRankPresetId && <Button danger onClick={() => deleteRankPreset(activeRankPresetId)}>Delete Active Preset</Button>}

          {savingRankPreset && (
            <Space>
              <Input value={newRankPresetName} onChange={(event) => setNewRankPresetName(event.target.value)} placeholder="Ranking preset name" />
              <Button type="primary" onClick={saveRankPreset}>Save</Button>
              <Button onClick={() => setSavingRankPreset(false)}>Cancel</Button>
            </Space>
          )}
        </Space>
      </Drawer>

      <Drawer
        title={selectedSymbol ? `Weekly Setup • ${selectedSymbol}` : "Weekly Setup"}
        placement="right"
        width={660}
        open={selectedSymbol !== null}
        onClose={() => setSelectedSymbol(null)}
      >
        {!selectedSymbol || !selectedRow ? (
          <Text type="secondary">Select a row to view details.</Text>
        ) : selectedDetail?.loading ? (
          <Text>Loading details...</Text>
        ) : (
          <Tabs
            activeKey={detailTab}
            onChange={setDetailTab}
            items={[
              {
                key: "setup",
                label: "Setup",
                children: (
                  <div>
                    {selectedDetail?.error && (
                      <Alert
                        type="warning"
                        showIcon
                        style={{ marginBottom: 12 }}
                        message="Failed to load full details"
                        description={selectedDetail.error}
                      />
                    )}
                    <Paragraph style={{ marginBottom: 8 }}>
                      {selectedRow.swingSetup?.reasoning ?? selectedDetail?.detail?.patternSummary ?? selectedRow.reason ?? "Setup rationale not available."}
                    </Paragraph>
                    <Space direction="vertical" size={8} style={{ width: "100%" }}>
                      <Stat label="Buy zone" value={`${formatPrice(selectedRow.swingSetup?.buyZoneMin ?? selectedRow.buyDayLowMin)} - ${formatPrice(selectedRow.swingSetup?.buyZoneMax ?? selectedRow.buyDayLowMax)}`} />
                      <Stat label="Safe / Recommended / Aggressive" value={`${getTargetPct(selectedRow, "safe").toFixed(2)}% / ${getTargetPct(selectedRow, "recommended").toFixed(2)}% / ${getTargetPct(selectedRow, "aggressive").toFixed(2)}%`} />
                      <Stat label="Hard stop" value={`${(selectedRow.swingSetup?.hardStopLossPct ?? selectedRow.stopLossPct).toFixed(2)}%`} />
                      <Stat label="Invalidation" value={selectedRow.swingSetup?.invalidationCondition ?? "Break below hard stop after entry"} />
                      <Stat label="Confidence" value={selectedRow.swingSetup?.confidence ?? selectedRow.targetRecommendation?.confidence ?? "MEDIUM"} />
                    </Space>
                  </div>
                ),
              },
              {
                key: "miniChart",
                label: "Mini Chart",
                children: (
                  <MiniChartCard
                    sessions={selectedDetail?.technicalContext?.recentSessions ?? []}
                    error={selectedDetail?.error}
                  />
                ),
              },
              {
                key: "timeline",
                label: "Timeline",
                children: (
                  <div>
                    {selectedDetail?.detail?.weeklyHeatmap && selectedDetail.detail.weeklyHeatmap.length > 0 ? (
                      <Timeline
                        items={selectedDetail.detail.weeklyHeatmap.slice(0, 12).map((week) => ({
                          color: week.swingTargetHit ? "green" : week.entryTriggered ? "blue" : "gray",
                          children: (
                            <div>
                              <Text strong>{week.weekLabel}</Text>
                              <div style={{ fontSize: 12, color: "#8c8c8c" }}>
                                Entry {week.entryTriggered ? "hit" : "not hit"} • Target {week.swingTargetHit ? "hit" : "missed"} • {week.reasoning ?? "No note"}
                              </div>
                            </div>
                          ),
                        }))}
                      />
                    ) : (
                      <Text type="secondary">No timeline data available.</Text>
                    )}
                  </div>
                ),
              },
              {
                key: "planner",
                label: "Trade Planner",
                children: renderTradePlanner(selectedRow),
              },
            ]}
          />
        )}
      </Drawer>

      <style>{`
        .screener-row:hover > td {
          background-color: #fafafa !important;
        }

        .screener-row-recommended > td {
          background-color: #fcfff5;
        }
      `}</style>
    </div>
  );
}

interface StatProps {
  label: string;
  value: string;
}

function Stat({ label, value }: StatProps) {
  return (
    <div style={{ display: "flex", justifyContent: "space-between", gap: 8, border: "1px solid #f0f0f0", borderRadius: 6, padding: "8px 10px", background: "#fff" }}>
      <Text type="secondary" style={{ fontSize: 12 }}>{label}</Text>
      <Text strong>{value}</Text>
    </div>
  );
}

interface WeightSliderProps {
  label: string;
  value: number;
  onChange: (value: number) => void;
}

function WeightSlider({ label, value, onChange }: WeightSliderProps) {
  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 4 }}>
        <Text>{label}</Text>
        <Text strong>{value}</Text>
      </div>
      <Slider min={0} max={100} value={value} onChange={(next) => onChange(Number(next))} />
    </div>
  );
}

interface MiniChartCardProps {
  sessions: TechnicalContext["recentSessions"];
  error: string | null | undefined;
}

function MiniChartCard({ sessions, error }: MiniChartCardProps) {
  if (error) {
    return <Alert type="warning" showIcon message="Mini chart unavailable" description={error} />;
  }
  if (!sessions || sessions.length === 0) {
    return <Text type="secondary">No session data available for mini chart.</Text>;
  }

  const closes = sessions.map((session) => session.close);
  const path = sparklinePath(closes, 560, 170);
  const latest = closes[closes.length - 1];
  const first = closes[0];
  const changePct = first > 0 ? ((latest - first) / first) * 100 : 0;

  return (
    <div style={{ border: "1px solid #f0f0f0", borderRadius: 8, padding: 10 }}>
      <div style={{ marginBottom: 6, display: "flex", justifyContent: "space-between" }}>
        <Text strong>Recent sessions close trend</Text>
        <Text style={{ color: changePct >= 0 ? "#389e0d" : "#cf1322" }}>
          {changePct >= 0 ? "+" : ""}{changePct.toFixed(2)}%
        </Text>
      </div>
      <svg width="100%" viewBox="0 0 560 190" role="img" aria-label="Mini chart">
        <rect x="0" y="0" width="560" height="190" fill="#fafafa" />
        <path d={path} fill="none" stroke="#1677ff" strokeWidth="2.5" />
      </svg>
      <Text type="secondary" style={{ fontSize: 12 }}>
        {sessions[0]?.date} to {sessions[sessions.length - 1]?.date}
      </Text>
    </div>
  );
}
