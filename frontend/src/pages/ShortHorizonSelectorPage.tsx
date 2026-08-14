import { ArrowDownOutlined, ArrowUpOutlined, MinusOutlined } from "@ant-design/icons";
import { Alert, Button, Card, Empty, Modal, Select, Space, Spin, Table, Tabs, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useMemo, useState, type ReactNode } from "react";
import type { UniverseOptionsResponse, WeeklyPriceWatchlistScannerResponse } from "../types";
import { AccumulationHeatmap } from "../components/AccumulationHeatmap";
import { ShortHorizonTabOneGuide } from "../components/ShortHorizonTabOneGuide";
import { getJson } from "../utils/api";
import {
  buildAccumulationRows,
  type AccumulationStockRow,
} from "../utils/accumulationScanner";
import {
  buildShortHorizonBestAlignedRows,
  buildShortHorizonFirstSeenPerformance,
  buildShortHorizonFreshTodayRows,
  buildShortHorizonLatestTwoFinishRows,
  buildShortHorizonRows,
  buildShortHorizonTabTwoShortlistRows,
  getShortHorizonMoveAccelerationState,
  getShortHorizonMoveStage,
  type ClosePositionBucket,
  type MoveAccelerationState,
  type MoveQuality,
  type PriceDirection,
  type ShortHorizonDailyEvidence,
  type ShortHorizonFirstSeenPerformance,
  type ShortHorizonFirstSeenPerformanceByTab,
  type ShortHorizonMoveStage,
  type ShortHorizonStockRow,
  type ShortHorizonTabTwoFilters,
  type VolumeActivity,
} from "../utils/shortHorizonSelector";
import "./shortHorizonSelector.css";

const { Text, Title } = Typography;
const SHORT_HORIZON_STRONG_FIRST_SEEN_RETURN_PCT = 5;

function getFirstSeenReturnClassName(value: number | null): string {
  const direction = value == null || value === 0 ? "neutral" : value > 0 ? "positive" : "negative";
  const weight = value != null && value >= SHORT_HORIZON_STRONG_FIRST_SEEN_RETURN_PCT ? " strong" : "";
  return "short-horizon-first-seen-return-" + direction + weight;
}

function FirstSeenReturnMetric({ label, value }: { label: string; value: number | null }): ReactNode {
  return (
    <span>
      {label} <span className={getFirstSeenReturnClassName(value)}>{formatSignedPercent(value)}</span>
    </span>
  );
}

function formatPrice(value: number | null): string {
  return value == null ? "—" : `₹${value.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function formatDate(value: string | null): string {
  if (!value) return "—";
  return new Intl.DateTimeFormat("en-IN", { day: "2-digit", month: "short", timeZone: "UTC" }).format(new Date(`${value}T00:00:00Z`));
}

function formatSessionAge(value: number | null): string {
  if (value == null) return "age unavailable";
  return value === 0 ? "today" : `${value} sessions ago`;
}

function formatPercent(value: number | null): string {
  return value == null ? "—" : `${value.toFixed(0)}%`;
}

function formatDeliveryPercentage(value: number | null): string {
  return value == null ? "—" : `${value.toFixed(1)}%`;
}

function formatSignedPercent(value: number | null): string {
  if (value == null) return "—";
  return `${value >= 0 ? "+" : ""}${value.toFixed(1)}%`;
}

function formatAccumulationClose(value: number | null): string {
  return value == null
    ? "—"
    : `₹${value.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function addFirstSeenPerformance(
  rows: ShortHorizonStockRow[],
  firstSeenPerformance: Record<string, ShortHorizonFirstSeenPerformance>,
): ShortHorizonStockRow[] {
  return rows.map((row) => ({
    ...row,
    firstSeenDate: firstSeenPerformance[row.key]?.date ?? null,
    firstSeenCloseReturnPct: firstSeenPerformance[row.key]?.closeReturnPct ?? null,
    firstSeenHighReturnPct: firstSeenPerformance[row.key]?.highReturnPct ?? null,
    firstSeenHighDate: firstSeenPerformance[row.key]?.highDate ?? null,
  }));
}

function formatMultiple(value: number | null): string {
  return value == null ? "—" : `${value.toFixed(1)}×`;
}

function getMoveDirection(value: number | null): PriceDirection | null {
  if (value == null) return null;
  if (value > 0) return "UP";
  if (value < 0) return "DOWN";
  return "FLAT";
}

function getMoveQualityLabel(quality: MoveQuality | null): string {
  if (quality === "CLEAN") return "Clean";
  if (quality === "WILD") return "Wild";
  if (quality === "MIXED") return "Mixed";
  return "—";
}

function getVolumeActivityLabel(activity: VolumeActivity | null): string {
  if (activity === "QUIET") return "Quiet";
  if (activity === "WATCH") return "Watch";
  return "—";
}

function getVolumeActivitySortValue(activity: VolumeActivity | null): number {
  if (activity === "WATCH") return 1;
  if (activity === "QUIET") return 0;
  return -1;
}

function getStrongFinishDayLabel(index: number): string {
  return index === 0 ? "T" : `T-${index}`;
}

function getBucketLabel(bucket: ClosePositionBucket | null): string {
  if (bucket === "HIGH") return "HIGH";
  if (bucket === "LOW") return "LOW";
  if (bucket === "MIDDLE") return "MID";
  return "—";
}

function getDirectionIcon(direction: PriceDirection | null): ReactNode {
  if (direction === "UP") return <ArrowUpOutlined />;
  if (direction === "DOWN") return <ArrowDownOutlined />;
  if (direction === "FLAT") return <MinusOutlined />;
  return null;
}

function ClosePositionBar({ positionPct, bucket, direction }: {
  positionPct: number | null;
  bucket: ClosePositionBucket | null;
  direction: PriceDirection | null;
}) {
  const position = positionPct == null ? 50 : positionPct;
  const state = bucket?.toLowerCase() ?? "unknown";

  return (
    <div className="short-horizon-close-cell" aria-label={`Close ${getBucketLabel(bucket)}, ${formatPercent(positionPct)} of the way from low to high`}>
      <div className="short-horizon-close-label">
        <span className={`short-horizon-direction short-horizon-direction-${state}`}>{getDirectionIcon(direction)}</span>
        <strong>{getBucketLabel(bucket)}</strong>
        <span className="short-horizon-close-position">{formatPercent(positionPct)}</span>
      </div>
      <div className="short-horizon-range" aria-hidden="true">
        <span className="short-horizon-range-low">L</span>
        <span className="short-horizon-range-track">
          <span className={`short-horizon-range-dot short-horizon-range-dot-${state}`} style={{ left: `${position}%` }} />
        </span>
        <span className="short-horizon-range-high">H</span>
      </div>
    </div>
  );
}

function RecentDailyDetails({ row }: { row: ShortHorizonStockRow }) {
  const dailyColumns: ColumnsType<ShortHorizonDailyEvidence> = [
    { title: "Date", dataIndex: "date", key: "date", width: 100, render: formatDate },
    { title: "Open", dataIndex: "open", key: "open", width: 100, render: formatPrice },
    { title: "High", dataIndex: "high", key: "high", width: 100, render: formatPrice },
    { title: "Low", dataIndex: "low", key: "low", width: 100, render: formatPrice },
    { title: "Close", dataIndex: "close", key: "close", width: 100, render: formatPrice },
    {
      title: "Volume vs 10D avg",
      dataIndex: "volumeMultiple",
      key: "volumeMultiple",
      width: 130,
      sorter: (left, right) => (left.volumeMultiple ?? -Infinity) - (right.volumeMultiple ?? -Infinity),
      render: (value: number | null) => formatMultiple(value),
    },
    {
      title: "Delivery %",
      dataIndex: "deliveryPercentage",
      key: "deliveryPercentage",
      width: 105,
      sorter: (left, right) => (left.deliveryPercentage ?? -Infinity) - (right.deliveryPercentage ?? -Infinity),
      render: (value: number | null) => formatDeliveryPercentage(value),
    },
    {
      title: "Change %",
      dataIndex: "changePct",
      key: "changePct",
      width: 100,
      sorter: (left, right) => (left.changePct ?? -Infinity) - (right.changePct ?? -Infinity),
      render: (value: number | null) => <span className={`short-horizon-daily-change-${getMoveDirection(value)?.toLowerCase() ?? "unknown"}`}>{formatSignedPercent(value)}</span>,
    },
    {
      title: "Close position",
      key: "closePosition",
      width: 155,
      sorter: (left, right) => left.closePositionPct - right.closePositionPct,
      render: (_, day) => <ClosePositionBar positionPct={day.closePositionPct} bucket={day.closePositionBucket} direction={day.direction} />,
    },
    {
      title: "From high",
      dataIndex: "closeFromHighPct",
      key: "closeFromHighPct",
      width: 105,
      sorter: (left, right) => (left.closeFromHighPct ?? -Infinity) - (right.closeFromHighPct ?? -Infinity),
      render: (value: number | null) => <span className="short-horizon-daily-from-high">{formatSignedPercent(value)}</span>,
    },
  ];

  return (
    <div className="short-horizon-details">
      <div className="short-horizon-details-summary">
        <div>
          <span className="short-horizon-details-number">{row.recentDailyEvidence.length}</span>
          <span>recent completed sessions</span>
        </div>
        <div className="short-horizon-details-rate">Newest first</div>
      </div>
      <Text type="secondary">Change is close versus the previous close. Close position shows where the close finished between the day's low and high; From high shows the close's distance from that day's high; Volume vs 10D avg compares the session volume with the preceding ten-session average.</Text>
      {row.recentDailyEvidence.length > 0 ? (
        <Table<ShortHorizonDailyEvidence>
          className="short-horizon-details-table"
          size="small"
          rowKey="key"
          pagination={false}
          columns={dailyColumns}
          dataSource={row.recentDailyEvidence}
          scroll={{ x: true }}
        />
      ) : (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No recent daily evidence available." />
      )}
    </div>
  );
}

function StrongFinishDots({ row }: { row: ShortHorizonStockRow }): ReactNode {
  const latestDays = row.recentDailyEvidence.slice(0, 5);
  if (latestDays.length === 0) return null;

  return (
    <div className="short-horizon-strong-finish-dots" aria-label="Strong finish sequence, newest day first">
      {latestDays.map((day, index) => {
        const label = getStrongFinishDayLabel(index);
        return (
          <span className="short-horizon-strong-finish-day" key={day.key} title={`${label} · ${formatDate(day.date)} · ${day.isStrongFinish ? "strong finish" : "not strong"}`}>
            <span className={`short-horizon-strong-finish-dot ${day.isStrongFinish ? "short-horizon-strong-finish-dot-filled" : "short-horizon-strong-finish-dot-empty"}`} aria-hidden="true" />
          </span>
        );
      })}
    </div>
  );
}

function VolumeActivityCell({ row }: { row: ShortHorizonStockRow }): ReactNode {
  const state = row.volumeActivity?.toLowerCase() ?? "unknown";
  const detail = row.volumeActivityMultiple == null
    ? "No abnormal volume in latest 5 sessions"
    : `${formatMultiple(row.volumeActivityMultiple)} · event ${formatDate(row.volumeActivityDate)}`;

  return (
    <Space orientation="vertical" size={0}>
      <span className={`short-horizon-volume-activity short-horizon-volume-activity-${state}`}>
        <strong>{getVolumeActivityLabel(row.volumeActivity)}</strong>
      </span>
      <Text type="secondary" className="short-horizon-date">
        {detail}
      </Text>
    </Space>
  );
}

function LatestCloseCell({ row }: { row: ShortHorizonStockRow }): ReactNode {
  const stage = getShortHorizonMoveStage(row);
  const latestDayChangePct = row.recentDailyEvidence[0]?.changePct ?? null;
  const contextTitle = `Day ${formatSignedPercent(latestDayChangePct)} · 20D ${formatSignedPercent(row.currentTwentyDayMovePct)}${row.pullbackFromRecentHighPct == null ? "" : ` · ${formatSignedPercent(row.pullbackFromRecentHighPct)} from recent high`} · Stage ${getMoveStageLabel(stage)}`;

  return (
    <Space orientation="vertical" size={0}>
      <Space size={6}>
        <Text>{formatPrice(row.latestClose)}</Text>
        <span className={`short-horizon-current-move short-horizon-current-move-${getMoveDirection(latestDayChangePct)?.toLowerCase() ?? "unknown"}`}>
          Day {formatSignedPercent(latestDayChangePct)}
        </span>
      </Space>
      <Text type="secondary" className="short-horizon-date">{formatDate(row.latestDate)}</Text>
      <span className="short-horizon-latest-close-context" title={contextTitle} aria-label={contextTitle}>
        <span className={`short-horizon-current-move short-horizon-current-move-${getMoveDirection(row.currentTwentyDayMovePct)?.toLowerCase() ?? "unknown"}`}>
          20D {formatSignedPercent(row.currentTwentyDayMovePct)}
        </span>
        {row.pullbackFromRecentHighPct != null && (
          <span className="short-horizon-latest-close-from-high"> · {formatSignedPercent(row.pullbackFromRecentHighPct)} from high</span>
        )}
        <span className={`short-horizon-move-stage short-horizon-move-stage-${stage.toLowerCase()}`}> · {getMoveStageLabel(stage)}</span>
      </span>
    </Space>
  );
}

function getMovePaceLabel(state: MoveAccelerationState): string {
  if (state === "ACCELERATING") return "Accelerating";
  if (state === "RECOVERING") return "Recovering";
  if (state === "WEAKENING") return "Slowing";
  if (state === "STEADY") return "Steady";
  return "Unavailable";
}

const DEFAULT_TAB_TWO_FILTERS: ShortHorizonTabTwoFilters = {
  acceleration: "ACCELERATING",
  minimumStrongFinishCount: 0,
};

const TAB_TWO_ACCELERATION_OPTIONS = [
  { value: "ACCELERATING", label: "Accelerating" },
  { value: "ANY", label: "Any pace" },
  { value: "STEADY", label: "Steady" },
  { value: "RECOVERING", label: "Recovering" },
  { value: "WEAKENING", label: "Weakening" },
] as const;

const TAB_TWO_STRONG_FINISH_OPTIONS = [
  { value: 0, label: "Any strong finishes" },
  { value: 3, label: "At least 3 / 5" },
  { value: 4, label: "At least 4 / 5" },
  { value: 5, label: "5 / 5" },
] as const;

function TabTwoFilters({
  filters,
  onChange,
}: {
  filters: ShortHorizonTabTwoFilters;
  onChange: (filters: ShortHorizonTabTwoFilters) => void;
}): ReactNode {
  return (
    <div className="short-horizon-tab-filters" data-testid="short-horizon-tab-two-filters">
      <label>
        <span>Move now</span>
        <Select<ShortHorizonTabTwoFilters["acceleration"]>
          aria-label="Tab 2 move now filter"
          size="small"
          value={filters.acceleration}
          options={[...TAB_TWO_ACCELERATION_OPTIONS]}
          onChange={(acceleration) => onChange({ ...filters, acceleration })}
        />
      </label>
      <label>
        <span>Strong finishes</span>
        <Select<number>
          aria-label="Tab 2 strong finishes filter"
          size="small"
          value={filters.minimumStrongFinishCount}
          options={[...TAB_TWO_STRONG_FINISH_OPTIONS]}
          onChange={(minimumStrongFinishCount) => onChange({ ...filters, minimumStrongFinishCount })}
        />
      </label>
    </div>
  );
}

type FiveDayPathMarker = "UP" | "DOWN" | "FLAT" | "UNKNOWN";

interface FiveDayPathSummary {
  greenDays: number;
  averageDailyChangePct: number | null;
  markers: FiveDayPathMarker[];
}

function getFiveDayMoveBand(value: number | null): string {
  if (value == null) return "—";
  const absoluteMove = Math.abs(value);
  if (absoluteMove >= 10) return "10%+";
  if (absoluteMove >= 5) return "5–10%";
  if (absoluteMove >= 3) return "3–5%";
  return "<3%";
}

function buildFiveDayPathSummary(row: ShortHorizonStockRow): FiveDayPathSummary {
  const recentDays = row.recentDailyEvidence.slice(0, 5);
  const changes = recentDays
    .map((day) => day.changePct)
    .filter((changePct): changePct is number => changePct != null);

  return {
    greenDays: recentDays.filter((day) => day.changePct != null && day.changePct > 0).length,
    averageDailyChangePct: changes.length === 0
      ? null
      : changes.reduce((total, changePct) => total + changePct, 0) / changes.length,
    markers: recentDays.map((day) => getMoveDirection(day.changePct) ?? "UNKNOWN"),
  };
}

function getFiveDayPathMarkerLabel(marker: FiveDayPathMarker): string {
  if (marker === "UP") return "G";
  if (marker === "DOWN") return "R";
  if (marker === "FLAT") return "·";
  return "?";
}

function getMoveStageLabel(stage: ShortHorizonMoveStage): string {
  if (stage === "FRESH") return "Fresh";
  if (stage === "REVIEW") return "Review";
  if (stage === "EXTENDED") return "Extended";
  return "Stage unavailable";
}

function MoveNowCell({ row }: { row: ShortHorizonStockRow }): ReactNode {
  const path = buildFiveDayPathSummary(row);
  const direction = getMoveDirection(row.currentFiveDayMovePct);
  const pace = getShortHorizonMoveAccelerationState(row);
  const paceLabel = getMovePaceLabel(pace);
  const directionLabel = direction === "UP" ? "Up" : direction === "DOWN" ? "Down" : direction === "FLAT" ? "Flat" : "5D";
  const resultLabel = row.currentFiveDayMovePct == null
    ? "Now 5D unavailable"
    : "Now 5D " + directionLabel + " " + formatSignedPercent(row.currentFiveDayMovePct) + " · Prior 5D " + formatSignedPercent(row.currentPreviousFiveDayMovePct) + " · " + paceLabel;
  const availableDays = path.markers.length;
  const pathLabel = "5D path: " + path.greenDays + "/" + availableDays + " green · avg day " + formatSignedPercent(path.averageDailyChangePct) + " · " + path.markers.map(getFiveDayPathMarkerLabel).join(" ");
  const title = resultLabel + ". " + (availableDays === 0 ? "5D path unavailable." : pathLabel);

  return (
    <div className="short-horizon-five-day-summary" title={title} aria-label={title}>
      <div className={`short-horizon-five-day-result short-horizon-five-day-result-${direction?.toLowerCase() ?? "unknown"}`}>
        <span>{directionLabel}</span>
        <strong>{formatSignedPercent(row.currentFiveDayMovePct)}</strong>
        <span className="short-horizon-five-day-band">{getFiveDayMoveBand(row.currentFiveDayMovePct)}</span>
      </div>
      <div className={`short-horizon-five-day-pace short-horizon-five-day-pace-${pace.toLowerCase()}`}>
        <span>Prior 5D</span>
        <strong>{formatSignedPercent(row.currentPreviousFiveDayMovePct)}</strong>
        <span>{paceLabel}</span>
      </div>
      <div className="short-horizon-five-day-path">
        <span className="short-horizon-five-day-path-count">{path.greenDays}/{availableDays}</span>
        <span className="short-horizon-five-day-path-markers" aria-hidden="true">
          {path.markers.map((marker, index) => (
            <span className={`short-horizon-five-day-marker short-horizon-five-day-marker-${marker.toLowerCase()}`} key={marker + "-" + index}>
              {getFiveDayPathMarkerLabel(marker)}
            </span>
          ))}
        </span>
        <span className="short-horizon-five-day-average">avg {formatSignedPercent(path.averageDailyChangePct)}</span>
      </div>
    </div>
  );
}

function buildStockColumns(
  showRecentEvidence: boolean,
  showCurrentMoveEvidence: boolean,
  showFiftyTwoWeekEvidence: boolean,
  showStrongFinishColumn: boolean,
  showTenTwentyMoveSummary: boolean,
  showCurrentConditionEvidence: boolean,
  showTabOneFilters: boolean,
  showCurrentConditionFilters: boolean,
  onDetails: (row: ShortHorizonStockRow) => void,
  onReview: (symbol: string) => void,
): ColumnsType<ShortHorizonStockRow> {
  const numberColumn: ColumnsType<ShortHorizonStockRow>[number] = {
    title: "No.",
    key: "number",
    width: 52,
    align: "center",
    render: (_, _row, index) => (index ?? 0) + 1,
  };
  const identityColumns: ColumnsType<ShortHorizonStockRow> = [
    {
      title: "Stock",
      key: "stock",
      width: 170,
      render: (_, row) => (
        <Space orientation="vertical" size={0}>
          <Text type="secondary" className="short-horizon-first-seen">
            {row.firstSeenDate ? `First seen ${formatDate(row.firstSeenDate)}` : "Not seen in last 5 sessions"}
          </Text>
          {row.firstSeenDate && (
            <Text
              type="secondary"
              className="short-horizon-first-seen-performance"
              title={row.firstSeenHighDate ? `Highest post-signal high on ${formatDate(row.firstSeenHighDate)}` : "No later session high yet"}
            >
              <FirstSeenReturnMetric label="Close" value={row.firstSeenCloseReturnPct} />
              {" · "}
              <FirstSeenReturnMetric label="High" value={row.firstSeenHighReturnPct} />
            </Text>
          )}
          <Text strong>{row.symbol}</Text>
          <Text type="secondary" className="short-horizon-company">{row.companyName}</Text>
        </Space>
      ),
    },
    {
      title: "Latest close",
      key: "latestClose",
      width: 160,
      render: (_, row) => <LatestCloseCell row={row} />,
    },
    {
      title: "5D reach",
      key: "successfulDays",
      width: 155,
      sorter: (left, right) => left.successfulDayCount - right.successfulDayCount,
      render: (_, row) => (
        <Space orientation="vertical" size={0}>
          <Text strong>5D reach {row.successfulDayCount} / {row.eligibleDayCount}</Text>
          <Text type="secondary" className="short-horizon-date">Recent tested 6D {row.recentSuccessfulDayCount} / {row.recentEligibleDayCount}</Text>
        </Space>
      ),
    },
  ];

  const strongFinishColumns: ColumnsType<ShortHorizonStockRow> = showStrongFinishColumn ? [
    {
      title: "Strong finishes",
      key: "recentStrongFinishCount",
      width: 150,
      sorter: (left, right) => left.recentStrongFinishCount - right.recentStrongFinishCount,
      filters: showTabOneFilters ? [
        { text: "At least 3 / 5", value: "3" },
        { text: "At least 4 / 5", value: "4" },
        { text: "5 / 5", value: "5" },
      ] : undefined,
      filterMultiple: false,
      onFilter: showTabOneFilters
        ? (value, row) => row.recentStrongFinishSessionCount >= 5 && row.recentStrongFinishCount >= Number(value)
        : undefined,
      render: (_, row) => (
        <Space orientation="vertical" size={0}>
          <Text strong>{row.recentStrongFinishCount} / {row.recentStrongFinishSessionCount}</Text>
          <StrongFinishDots row={row} />
        </Space>
      ),
    },
  ] : [];

  const recentEvidenceColumns: ColumnsType<ShortHorizonStockRow> = showRecentEvidence ? [
    {
      title: "Recent high",
      key: "recentHigh",
      width: 125,
      render: (_, row) => (
        <Space orientation="vertical" size={0}>
          <Text>{formatPrice(row.recentHigh)}</Text>
          <Text type="secondary" className="short-horizon-date">{formatDate(row.recentHighDate)} · 20D</Text>
        </Space>
      ),
    },
    {
      title: "From high",
      key: "pullbackFromRecentHighPct",
      width: 105,
      sorter: (left, right) => (left.pullbackFromRecentHighPct ?? 0) - (right.pullbackFromRecentHighPct ?? 0),
      render: (_, row) => (
        <Text className={row.pullbackFromRecentHighPct != null && row.pullbackFromRecentHighPct < 0 ? "short-horizon-pullback-negative" : undefined}>
          {formatSignedPercent(row.pullbackFromRecentHighPct)}
        </Text>
      ),
    },
    {
      title: "Volume activity",
      key: "volumeActivity",
      width: 145,
      sorter: (left, right) => getVolumeActivitySortValue(left.volumeActivity) - getVolumeActivitySortValue(right.volumeActivity),
      render: (_, row) => <VolumeActivityCell row={row} />,
    },
  ] : [];

  const tenTwentyMoveSummaryColumns: ColumnsType<ShortHorizonStockRow> = showTenTwentyMoveSummary ? [
    {
      title: "Move now",
      key: "currentTenTwentyDayMove",
      width: 180,
      sorter: (left, right) =>
        (left.currentFiveDayMovePct ?? -Infinity) - (right.currentFiveDayMovePct ?? -Infinity)
        || (left.currentPreviousFiveDayMovePct ?? -Infinity) - (right.currentPreviousFiveDayMovePct ?? -Infinity),
      render: (_, row) => <MoveNowCell row={row} />,
    },
  ] : [];

  const currentConditionColumns: ColumnsType<ShortHorizonStockRow> = showCurrentConditionEvidence ? [
    {
      title: "Move quality",
      key: "recentMoveQuality",
      width: 125,
      sorter: (left, right) => (left.recentMoveQuality ?? "").localeCompare(right.recentMoveQuality ?? ""),
      filters: showCurrentConditionFilters ? [
        { text: "Clean", value: "CLEAN" },
        { text: "Mixed", value: "MIXED" },
        { text: "Wild", value: "WILD" },
        { text: "Unavailable", value: "UNKNOWN" },
      ] : undefined,
      filterMultiple: false,
      onFilter: showCurrentConditionFilters
        ? (value, row) => (row.recentMoveQuality ?? "UNKNOWN") === value
        : undefined,
      render: (_, row) => (
        <Space orientation="vertical" size={0}>
          <Text className={`short-horizon-move-quality short-horizon-move-quality-${row.recentMoveQuality?.toLowerCase() ?? "unknown"}`}>
            <strong>{getMoveQualityLabel(row.recentMoveQuality)}</strong>
          </Text>
          <Text type="secondary" className="short-horizon-date">latest 5D</Text>
        </Space>
      ),
    },
    {
      title: "Volume activity",
      key: "volumeActivity",
      width: 145,
      sorter: (left, right) => getVolumeActivitySortValue(left.volumeActivity) - getVolumeActivitySortValue(right.volumeActivity),
      filters: showCurrentConditionFilters ? [
        { text: "Quiet", value: "QUIET" },
        { text: "Watch", value: "WATCH" },
        { text: "Unavailable", value: "UNKNOWN" },
      ] : undefined,
      filterMultiple: false,
      onFilter: showCurrentConditionFilters
        ? (value, row) => (row.volumeActivity ?? "UNKNOWN") === value
        : undefined,
      render: (_, row) => <VolumeActivityCell row={row} />,
    },
  ] : [];

  const currentMoveColumns: ColumnsType<ShortHorizonStockRow> = showCurrentMoveEvidence ? [
    {
      title: "5D move",
      key: "currentFiveDayMovePct",
      width: 100,
      sorter: (left, right) => (left.currentFiveDayMovePct ?? -Infinity) - (right.currentFiveDayMovePct ?? -Infinity),
      render: (_, row) => (
        <span className={`short-horizon-current-move short-horizon-current-move-${getMoveDirection(row.currentFiveDayMovePct)?.toLowerCase() ?? "unknown"}`}>
          {getDirectionIcon(getMoveDirection(row.currentFiveDayMovePct))} <strong>{formatSignedPercent(row.currentFiveDayMovePct)}</strong>
        </span>
      ),
    },
    {
      title: "20D move",
      key: "currentTwentyDayMovePct",
      width: 105,
      sorter: (left, right) => (left.currentTwentyDayMovePct ?? -Infinity) - (right.currentTwentyDayMovePct ?? -Infinity),
      render: (_, row) => (
        <span className={`short-horizon-current-move short-horizon-current-move-${getMoveDirection(row.currentTwentyDayMovePct)?.toLowerCase() ?? "unknown"}`}>
          {getDirectionIcon(getMoveDirection(row.currentTwentyDayMovePct))} <strong>{formatSignedPercent(row.currentTwentyDayMovePct)}</strong>
        </span>
      ),
    },
    {
      title: "Strong finishes",
      key: "recentStrongFinishCount",
      width: 150,
      sorter: (left, right) => left.recentStrongFinishCount - right.recentStrongFinishCount,
      render: (_, row) => (
        <Space orientation="vertical" size={0}>
          <Text strong>{row.recentStrongFinishCount} / {row.recentStrongFinishSessionCount}</Text>
          <Text type="secondary" className="short-horizon-date">close &gt;60% of range · latest 5D</Text>
        </Space>
      ),
    },
    {
      title: "Move quality",
      key: "recentMoveQuality",
      sorter: (left, right) => (left.recentMoveQuality ?? "").localeCompare(right.recentMoveQuality ?? ""),
      render: (_, row) => (
        <Text className={`short-horizon-move-quality short-horizon-move-quality-${row.recentMoveQuality?.toLowerCase() ?? "unknown"}`}>
          {getMoveQualityLabel(row.recentMoveQuality)}
        </Text>
      ),
    },
  ] : [];

  const fiftyTwoWeekEvidenceColumns: ColumnsType<ShortHorizonStockRow> = showFiftyTwoWeekEvidence ? [
    {
      title: "52W high",
      key: "distanceFromFiftyTwoWeekHighPct",
      width: 125,
      sorter: (left, right) => (right.distanceFromFiftyTwoWeekHighPct ?? -Infinity) - (left.distanceFromFiftyTwoWeekHighPct ?? -Infinity),
      render: (_, row) => (
        <Space orientation="vertical" size={0}>
          <Text strong>{formatSignedPercent(row.distanceFromFiftyTwoWeekHighPct)}</Text>
          <Text type="secondary" className="short-horizon-date">{formatPrice(row.fiftyTwoWeekHigh)}</Text>
          <Text type="secondary" className="short-horizon-date">{formatSessionAge(row.fiftyTwoWeekHighSessionsAgo)} · {formatDate(row.fiftyTwoWeekHighDate)}</Text>
        </Space>
      ),
    },
  ] : [];

  const latestFinishColumn: ColumnsType<ShortHorizonStockRow>[number] = {
    title: "Latest finish",
    key: "latestFinish",
    width: 150,
    sorter: (left, right) => (left.latestClosePositionPct ?? -1) - (right.latestClosePositionPct ?? -1),
    filters: showTabOneFilters ? [
      { text: "HIGH", value: "HIGH" },
      { text: "MID", value: "MIDDLE" },
      { text: "LOW", value: "LOW" },
    ] : undefined,
    filterMultiple: false,
    onFilter: showTabOneFilters
      ? (value, row) => row.latestClosePositionBucket === value
      : undefined,
    render: (_, row) => <ClosePositionBar positionPct={row.latestClosePositionPct} bucket={row.latestClosePositionBucket} direction={row.latestDirection} />,
  };

  const actionColumn: ColumnsType<ShortHorizonStockRow>[number] = {
    title: "Action",
    key: "action",
    width: 145,
    render: (_, row) => (
      <Space size={4}>
        <Button size="small" onClick={() => onDetails(row)}>Details</Button>
        <Button size="small" type="primary" onClick={() => onReview(row.symbol)}>Review</Button>
      </Space>
    ),
  };

  const trailingContextColumns: ColumnsType<ShortHorizonStockRow> = showStrongFinishColumn
    ? [actionColumn]
    : [latestFinishColumn, actionColumn];

  return [
    numberColumn,
    identityColumns[0],
    ...tenTwentyMoveSummaryColumns,
    ...strongFinishColumns,
    ...(showStrongFinishColumn ? [latestFinishColumn] : []),
    ...currentConditionColumns,
    identityColumns[1],
    identityColumns[2],
    ...currentMoveColumns,
    ...recentEvidenceColumns,
    ...fiftyTwoWeekEvidenceColumns,
    ...trailingContextColumns,
  ];
}

function AccumulationCountCell({
  count,
  total,
  tone,
}: {
  count: number;
  total: number;
  tone: "buying" | "green" | "quiet" | "volume";
}): ReactNode {
  return (
    <div className={`accumulation-count-cell accumulation-count-cell-${tone}`}>
      <strong>{count} / {total}</strong>
      <span>sessions</span>
    </div>
  );
}

function buildAccumulationColumns(
  onReview: (symbol: string) => void,
): ColumnsType<AccumulationStockRow> {
  return [
    {
      title: "No.",
      key: "number",
      width: 52,
      align: "center",
      render: (_, _row, index) => (index ?? 0) + 1,
    },
    {
      title: "Stock",
      key: "stock",
      width: 170,
      render: (_, row) => (
        <Space orientation="vertical" size={0}>
          <Text strong>{row.symbol}</Text>
          <Text type="secondary" className="short-horizon-company">{row.companyName}</Text>
        </Space>
      ),
    },
    {
      title: "Latest close",
      key: "latestClose",
      width: 120,
      render: (_, row) => (
        <Space orientation="vertical" size={0}>
          <Text strong>{formatAccumulationClose(row.latestClose)}</Text>
          <Text type="secondary" className="short-horizon-date">{formatDate(row.latestDate)}</Text>
        </Space>
      ),
    },
    {
      title: "Buy-interest days",
      key: "buyingInterestCount",
      width: 135,
      sorter: (left, right) => left.buyingInterestCount - right.buyingInterestCount,
      defaultSortOrder: "descend",
      render: (_, row) => <AccumulationCountCell count={row.buyingInterestCount} total={row.countWindowSessions} tone="buying" />,
    },
    {
      title: "Green closes",
      key: "greenCloseCount",
      width: 120,
      sorter: (left, right) => left.greenCloseCount - right.greenCloseCount,
      render: (_, row) => <AccumulationCountCell count={row.greenCloseCount} total={row.countWindowSessions} tone="green" />,
    },
    {
      title: "Quiet <1%",
      key: "quietMoveCount",
      width: 110,
      sorter: (left, right) => left.quietMoveCount - right.quietMoveCount,
      render: (_, row) => <AccumulationCountCell count={row.quietMoveCount} total={row.countWindowSessions} tone="quiet" />,
    },
    {
      title: "Volume below 10D",
      key: "volumeDryUpCount",
      width: 140,
      sorter: (left, right) => left.volumeDryUpCount - right.volumeDryUpCount,
      render: (_, row) => <AccumulationCountCell count={row.volumeDryUpCount} total={row.volumeEligibleSessionCount} tone="volume" />,
    },
    {
      title: "20D heatmap",
      key: "heatmap",
      width: 300,
      render: (_, row) => <AccumulationHeatmap days={row.heatmap} />,
    },
    {
      title: "Action",
      key: "action",
      width: 92,
      render: (_, row) => <Button size="small" type="primary" onClick={() => onReview(row.symbol)}>Review</Button>,
    },
  ];
}

export function ShortHorizonSelectorPage({ onOpenCompactStockReview }: { onOpenCompactStockReview: (symbol: string) => void }) {
  const [options, setOptions] = useState<UniverseOptionsResponse["options"]>([]);
  const [selectedWatchlist, setSelectedWatchlist] = useState<string | null>(null);
  const [data, setData] = useState<WeeklyPriceWatchlistScannerResponse | null>(null);
  const [selectedDetails, setSelectedDetails] = useState<ShortHorizonStockRow | null>(null);
  const [activeTab, setActiveTab] = useState("all");
  const [tabTwoFilters, setTabTwoFilters] = useState<ShortHorizonTabTwoFilters>(DEFAULT_TAB_TWO_FILTERS);
  const [loadingOptions, setLoadingOptions] = useState(true);
  const [loadingScan, setLoadingScan] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    void getJson<UniverseOptionsResponse>("/api/strategy/weekly-price-review/watchlists")
      .then((response) => {
        if (active) setOptions(response.options);
      })
      .catch((requestError: unknown) => {
        if (active) setError(requestError instanceof Error ? requestError.message : "Failed to load watchlists");
      })
      .finally(() => {
        if (active) setLoadingOptions(false);
      });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    if (!selectedWatchlist) {
      setData(null);
      return;
    }

    let active = true;
    setLoadingScan(true);
    setError(null);
    void getJson<WeeklyPriceWatchlistScannerResponse>(
      `/api/strategy/weekly-price-review/scan?watchlist=${encodeURIComponent(selectedWatchlist)}`,
      { useCache: false },
    )
      .then((response) => {
        if (active) setData(response);
      })
      .catch((requestError: unknown) => {
        if (active) setError(requestError instanceof Error ? requestError.message : "Failed to scan watchlist");
      })
      .finally(() => {
        if (active) setLoadingScan(false);
      });
    return () => { active = false; };
  }, [selectedWatchlist]);

  const rows = useMemo(() => buildShortHorizonRows(data?.rows ?? []), [data?.rows]);
  const firstSeenPerformance = useMemo<ShortHorizonFirstSeenPerformanceByTab>(
    () => buildShortHorizonFirstSeenPerformance(data?.rows ?? [], tabTwoFilters),
    [data?.rows, tabTwoFilters],
  );
  const rowsWithFirstSeenDates = useMemo(
    () => addFirstSeenPerformance(rows, firstSeenPerformance.all),
    [rows, firstSeenPerformance.all],
  );
  const shortlistRows = useMemo(
    () => buildShortHorizonTabTwoShortlistRows(rows, tabTwoFilters),
    [rows, tabTwoFilters],
  );
  const shortlistRowsWithFirstSeenDates = useMemo(
    () => addFirstSeenPerformance(shortlistRows, firstSeenPerformance.shortlist),
    [shortlistRows, firstSeenPerformance.shortlist],
  );
  const bestAlignedRows = useMemo(() => buildShortHorizonBestAlignedRows(rows), [rows]);
  const bestAlignedRowsWithFirstSeenDates = useMemo(
    () => addFirstSeenPerformance(bestAlignedRows, firstSeenPerformance["best-aligned"]),
    [bestAlignedRows, firstSeenPerformance],
  );
  const latestTwoFinishRows = useMemo(() => buildShortHorizonLatestTwoFinishRows(rows), [rows]);
  const latestTwoFinishRowsWithFirstSeenDates = useMemo(
    () => addFirstSeenPerformance(latestTwoFinishRows, firstSeenPerformance["latest-two-finish"]),
    [latestTwoFinishRows, firstSeenPerformance],
  );
  const freshTodayRows = useMemo(
    () => buildShortHorizonFreshTodayRows(latestTwoFinishRowsWithFirstSeenDates),
    [latestTwoFinishRowsWithFirstSeenDates],
  );
  const accumulationRows = useMemo(
    () => buildAccumulationRows(data?.rows ?? []),
    [data?.rows],
  );

  return (
    <div className="short-horizon-selector-page">
      <Card className="short-horizon-selector-card">
        <div className="short-horizon-selector-header">
          <div>
            <Text className="short-horizon-eyebrow">SHORT HORIZON · DAILY CLOSES</Text>
            <Title level={3} style={{ margin: 0 }}>5-Day Stock Selector</Title>
            <Text type="secondary">Find watchlist stocks that have previously reached +5% within five sessions, then check how strongly they closed.</Text>
          </div>
          <Space align="end">
            <Select
              aria-label="Watchlist"
              loading={loadingOptions}
              value={selectedWatchlist}
              onChange={setSelectedWatchlist}
              placeholder="Select a watchlist"
              style={{ width: 280, maxWidth: "100%" }}
              options={options.map((option) => ({ value: option.value, label: `${option.label} (${option.count})` }))}
            />
            <ShortHorizonTabOneGuide />
          </Space>
        </div>
        {data && <Text className="short-horizon-method-note" type="secondary">5D reach: for each tested starting close, price touched +5% within the next 5 trading sessions · last 20 usable starting days · Recent tested 6D uses the latest 6 eligible starting days · latest finish uses the latest available daily candle.</Text>}
      </Card>

      {error && <Alert type="error" message={error} showIcon />}
      {!selectedWatchlist && !loadingOptions && <Empty description="Select a watchlist to find short-horizon candidates." />}
      {loadingScan && <div className="short-horizon-loading"><Spin /><span>Reading daily history…</span></div>}
      {data && !loadingScan && rows.length === 0 && <Empty description="No stocks are available in this watchlist." />}
      {data && !loadingScan && rows.length > 0 && (
        <Card className="short-horizon-results-card">
          <Tabs
            activeKey={activeTab}
            onChange={setActiveTab}
            items={[
              {
                key: "all",
                label: `All Stocks · ${rows.length}`,
                children: (
                  <Table<ShortHorizonStockRow>
                    data-testid="short-horizon-selector-table"
                    rowKey="key"
                    size="small"
                    pagination={false}
                    scroll={{ x: true }}
                    columns={buildStockColumns(false, false, false, true, true, true, true, true, setSelectedDetails, onOpenCompactStockReview)}
                    dataSource={rowsWithFirstSeenDates}
                  />
                ),
              },
              {
                key: "shortlist",
                label: `Shortlist · ${shortlistRows.length}`,
                children: (
                  <div>
                    <TabTwoFilters filters={tabTwoFilters} onChange={setTabTwoFilters} />
                    <div className="short-horizon-rule-note">
                      <strong>How Shortlist works:</strong>
                      <div className="short-horizon-rule-columns">
                        <div>
                          <strong>Sorting rules</strong>
                          <ol>
                            <li>Rank by 5D reach across the last 20 usable starting days.</li>
                            <li>Rank separately by Recent tested 6D reach.</li>
                            <li>Keep the best group from each ranking in the historical candidate pool.</li>
                          </ol>
                        </div>
                        <div>
                          <strong>Filtering rules</strong>
                          <ol>
                            <li>Move now keeps accelerating stocks by default.</li>
                            <li>Strong finishes stays visible and has an optional minimum filter.</li>
                            <li>Volume activity remains visible as neutral evidence; it does not decide entry or exit.</li>
                            <li>Reject structural weakness only when the last 3 closes fall in a row and today's close breaks below the previous 5-session low.</li>
                            <li>All other Tab 1 columns remain visible for review.</li>
                          </ol>
                        </div>
                      </div>
                    </div>
                    <Table<ShortHorizonStockRow>
                      data-testid="short-horizon-shortlist-table"
                      rowKey="key"
                      size="small"
                      pagination={false}
                      scroll={{ x: true }}
                      columns={buildStockColumns(false, false, true, true, true, true, false, false, setSelectedDetails, onOpenCompactStockReview)}
                      dataSource={shortlistRowsWithFirstSeenDates}
                    />
                  </div>
                ),
              },
              {
                key: "best-aligned",
                label: `Best aligned · ${bestAlignedRows.length}`,
                children: (
                  <div>
                    <Text type="secondary" className="short-horizon-tab-note">Evidence floor: at least 3 / 20 5D reach or 1 / 6 Recent tested 6D reach. Then require accelerating Move now and at least 2 / 5 Strong finishes. Move quality and Volume activity remain context.</Text>
                    <Table<ShortHorizonStockRow>
                      data-testid="short-horizon-best-aligned-table"
                      rowKey="key"
                      size="small"
                      pagination={false}
                      scroll={{ x: true }}
                      columns={buildStockColumns(false, false, true, true, true, true, false, false, setSelectedDetails, onOpenCompactStockReview)}
                      dataSource={bestAlignedRowsWithFirstSeenDates}
                    />
                  </div>
                ),
              },
              {
                key: "latest-two-finish",
                label: `Latest 2-day finish · ${latestTwoFinishRows.length}`,
                children: (
                  <div>
                    <Text type="secondary" className="short-horizon-tab-note">Best aligned rules plus recent proof and latest finish: Recent tested 6D must be at least 1 / 6, and at least one of the latest two completed candles must close at least 75% up its daily range.</Text>
                    <Table<ShortHorizonStockRow>
                      data-testid="short-horizon-latest-two-finish-table"
                      rowKey="key"
                      size="small"
                      pagination={false}
                      scroll={{ x: true }}
                      columns={buildStockColumns(false, false, true, true, true, true, false, true, setSelectedDetails, onOpenCompactStockReview)}
                      dataSource={latestTwoFinishRowsWithFirstSeenDates}
                    />
                  </div>
                ),
              },
              {
                key: "fresh-today",
                label: "Fresh today · " + freshTodayRows.length,
                children: (
                  <div>
                    <Text type="secondary" className="short-horizon-tab-note">Only stocks that entered Latest 2-day finish in the current completed session. Use this tab to separate fresh signals from stocks already visible in the recent five-session window.</Text>
                    <Table<ShortHorizonStockRow>
                      data-testid="short-horizon-fresh-today-table"
                      rowKey="key"
                      size="small"
                      pagination={false}
                      scroll={{ x: true }}
                      columns={buildStockColumns(false, false, true, true, true, true, false, true, setSelectedDetails, onOpenCompactStockReview)}
                      dataSource={freshTodayRows}
                    />
                  </div>
                ),
              },
              {
                key: "accumulation",
                label: `Accumulation · ${accumulationRows.length}`,
                children: (
                  <div>
                    <Text type="secondary" className="short-horizon-tab-note">
                      30-session context: Buy = close at least 70% up the daily range · Green = close above previous close · Quiet = absolute close-to-close move below 1% · Vol = volume below the prior 10-session average. Heatmap reads oldest → latest across the latest 20 sessions.
                    </Text>
                    <Table<AccumulationStockRow>
                      data-testid="short-horizon-accumulation-table"
                      rowKey="key"
                      size="small"
                      pagination={false}
                      scroll={{ x: true }}
                      columns={buildAccumulationColumns(onOpenCompactStockReview)}
                      dataSource={accumulationRows}
                    />
                  </div>
                ),
              },
            ]}
          />
        </Card>
      )}

      <Modal
        title={selectedDetails ? `${selectedDetails.symbol} · Recent 20D details` : "Recent 20D details"}
        open={selectedDetails != null}
        mask={false}
        onCancel={() => setSelectedDetails(null)}
        footer={null}
        width={760}
      >
        {selectedDetails && <RecentDailyDetails row={selectedDetails} />}
      </Modal>
    </div>
  );
}
