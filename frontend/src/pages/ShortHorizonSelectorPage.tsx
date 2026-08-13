import { ArrowDownOutlined, ArrowUpOutlined, MinusOutlined } from "@ant-design/icons";
import { Alert, Button, Card, Empty, Modal, Select, Space, Spin, Table, Tabs, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useMemo, useState, type ReactNode } from "react";
import type { UniverseOptionsResponse, WeeklyPriceWatchlistScannerResponse } from "../types";
import { ShortHorizonTabOneGuide } from "../components/ShortHorizonTabOneGuide";
import { getJson } from "../utils/api";
import {
  buildShortHorizonCoreRows,
  buildShortHorizonRows,
  buildShortHorizonShortlistRows,
  calculateShortHorizonShortlistSize,
  filterShortHorizonRowsByShortlistGuards,
  getShortHorizonShortlistRuleDescription,
  isShortHorizonMoveExtended,
  SHORT_HORIZON_MOVE_ACCELERATION_TOLERANCE_PCT,
  SHORT_HORIZON_OVEREXTENDED_TWENTY_DAY_MOVE_PCT,
  type ClosePositionBucket,
  type ExitPressure,
  type MoveQuality,
  type PriceDirection,
  type ShortHorizonDailyEvidence,
  type ShortHorizonStockRow,
} from "../utils/shortHorizonSelector";
import "./shortHorizonSelector.css";

const { Text, Title } = Typography;

function formatPrice(value: number | null): string {
  return value == null ? "—" : `₹${value.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function formatDate(value: string | null): string {
  if (!value) return "—";
  return new Intl.DateTimeFormat("en-IN", { day: "2-digit", month: "short", timeZone: "UTC" }).format(new Date(`${value}T00:00:00Z`));
}

function formatPercent(value: number | null): string {
  return value == null ? "—" : `${value.toFixed(0)}%`;
}

function formatSignedPercent(value: number | null): string {
  if (value == null) return "—";
  return `${value >= 0 ? "+" : ""}${value.toFixed(1)}%`;
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

function getExitPressureLabel(pressure: ExitPressure | null): string {
  if (pressure === "QUIET") return "Quiet";
  if (pressure === "WATCH") return "Watch";
  if (pressure === "CAUTION") return "Caution";
  return "—";
}

function getExitPressureSortValue(pressure: ExitPressure | null): number {
  if (pressure === "CAUTION") return 2;
  if (pressure === "WATCH") return 1;
  if (pressure === "QUIET") return 0;
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

function ExitPressureCell({ row }: { row: ShortHorizonStockRow }): ReactNode {
  const state = row.exitPressure?.toLowerCase() ?? "unknown";

  return (
    <Space orientation="vertical" size={0}>
      <span className={`short-horizon-exit-pressure short-horizon-exit-pressure-${state}`}>
        <strong>{getExitPressureLabel(row.exitPressure)}</strong>
      </span>
      <Text type="secondary" className="short-horizon-date">
        {formatMultiple(row.exitPressureVolumeMultiple)} · push {formatDate(row.exitPressureDate)}
      </Text>
    </Space>
  );
}

function LatestCloseCell({ row }: { row: ShortHorizonStockRow }): ReactNode {
  const isExtended = isShortHorizonMoveExtended(row.currentTwentyDayMovePct);
  const contextTitle = `20D ${formatSignedPercent(row.currentTwentyDayMovePct)}${row.pullbackFromRecentHighPct == null ? "" : ` · ${formatSignedPercent(row.pullbackFromRecentHighPct)} from recent high`}${isExtended ? ` · Extension watch above ${SHORT_HORIZON_OVEREXTENDED_TWENTY_DAY_MOVE_PCT}%` : ""}`;

  return (
    <Space orientation="vertical" size={0}>
      <Text>{formatPrice(row.latestClose)}</Text>
      <Text type="secondary" className="short-horizon-date">{formatDate(row.latestDate)}</Text>
      <span className="short-horizon-latest-close-context" title={contextTitle} aria-label={contextTitle}>
        <span className={`short-horizon-current-move short-horizon-current-move-${getMoveDirection(row.currentTwentyDayMovePct)?.toLowerCase() ?? "unknown"}`}>
          20D {formatSignedPercent(row.currentTwentyDayMovePct)}
        </span>
        {row.pullbackFromRecentHighPct != null && (
          <span className="short-horizon-latest-close-from-high"> · {formatSignedPercent(row.pullbackFromRecentHighPct)} from high</span>
        )}
        {isExtended && <span className="short-horizon-latest-close-warning" aria-label="Extension watch">⚠</span>}
      </span>
    </Space>
  );
}

type MoveAccelerationState = "ACCELERATING" | "SLOWING" | "STEADY" | "UNKNOWN";

function getMoveAccelerationState(row: ShortHorizonStockRow): MoveAccelerationState {
  if (row.currentFiveDayMovePct == null || row.currentPreviousFiveDayMovePct == null) return "UNKNOWN";
  if (row.currentFiveDayMovePct - row.currentPreviousFiveDayMovePct >= SHORT_HORIZON_MOVE_ACCELERATION_TOLERANCE_PCT) return "ACCELERATING";
  if (row.currentPreviousFiveDayMovePct - row.currentFiveDayMovePct >= SHORT_HORIZON_MOVE_ACCELERATION_TOLERANCE_PCT) return "SLOWING";
  return "STEADY";
}

function getMoveAccelerationLabel(state: MoveAccelerationState): string {
  if (state === "ACCELERATING") return "accelerating";
  if (state === "SLOWING") return "slowing";
  if (state === "STEADY") return "steady pace";
  return "pace unavailable";
}

function MoveNowCell({ row }: { row: ShortHorizonStockRow }): ReactNode {
  const accelerationState = getMoveAccelerationState(row);
  const accelerationLabel = getMoveAccelerationLabel(accelerationState);
  const isOverextended = isShortHorizonMoveExtended(row.currentTwentyDayMovePct);
  const explanation = row.currentPreviousFiveDayMovePct == null || row.currentPreviousTenDayMovePct == null
    ? `Now 5D ${formatSignedPercent(row.currentFiveDayMovePct)} · Prior 5D ${formatSignedPercent(row.currentPreviousFiveDayMovePct)} · Earlier 10D ${formatSignedPercent(row.currentPreviousTenDayMovePct)}. The 5D pace comparison is unavailable.`
    : `Now 5D ${formatSignedPercent(row.currentFiveDayMovePct)} · Prior 5D ${formatSignedPercent(row.currentPreviousFiveDayMovePct)} · Earlier 10D ${formatSignedPercent(row.currentPreviousTenDayMovePct)} · 20D total ${formatSignedPercent(row.currentTwentyDayMovePct)}. The latest 5D is ${accelerationLabel} versus the prior 5D.`;
  const title = isOverextended
    ? `${explanation} Extension watch: the 20D move is above ${SHORT_HORIZON_OVEREXTENDED_TWENTY_DAY_MOVE_PCT}%.`
    : explanation;

  return (
    <div className="short-horizon-move-now" title={title} aria-label={title}>
      <div className="short-horizon-move-now-line">
        <span className="short-horizon-move-period">Now 5D</span>
        <span className={`short-horizon-current-move short-horizon-current-move-${getMoveDirection(row.currentFiveDayMovePct)?.toLowerCase() ?? "unknown"}`}>
          {formatSignedPercent(row.currentFiveDayMovePct)}
        </span>
      </div>
      <div className="short-horizon-move-now-line">
        <span className="short-horizon-move-period">Prior 5D</span>
        <span className={`short-horizon-current-move short-horizon-current-move-${getMoveDirection(row.currentPreviousFiveDayMovePct)?.toLowerCase() ?? "unknown"}`}>
          {formatSignedPercent(row.currentPreviousFiveDayMovePct)}
        </span>
      </div>
      <div className="short-horizon-move-now-line">
        <span className="short-horizon-move-period">Earlier 10D</span>
        <span className={`short-horizon-current-move short-horizon-current-move-${getMoveDirection(row.currentPreviousTenDayMovePct)?.toLowerCase() ?? "unknown"}`}>
          {formatSignedPercent(row.currentPreviousTenDayMovePct)}
        </span>
        <span className={`short-horizon-move-acceleration short-horizon-move-acceleration-${accelerationState.toLowerCase()}`} aria-label={accelerationLabel}>
          {accelerationState === "ACCELERATING" ? "↗" : accelerationState === "SLOWING" ? "↘" : accelerationState === "STEADY" ? "→" : "·"}
        </span>
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
      title: "Exit pressure",
      key: "exitPressure",
      width: 145,
      sorter: (left, right) => getExitPressureSortValue(left.exitPressure) - getExitPressureSortValue(right.exitPressure),
      render: (_, row) => <ExitPressureCell row={row} />,
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
      title: "Exit pressure",
      key: "exitPressure",
      width: 145,
      sorter: (left, right) => getExitPressureSortValue(left.exitPressure) - getExitPressureSortValue(right.exitPressure),
      render: (_, row) => <ExitPressureCell row={row} />,
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
        </Space>
      ),
    },
  ] : [];

  const latestFinishColumn: ColumnsType<ShortHorizonStockRow>[number] = {
    title: "Latest finish",
    key: "latestFinish",
    width: 150,
    sorter: (left, right) => (left.latestClosePositionPct ?? -1) - (right.latestClosePositionPct ?? -1),
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

export function ShortHorizonSelectorPage({ onOpenCompactStockReview }: { onOpenCompactStockReview: (symbol: string) => void }) {
  const [options, setOptions] = useState<UniverseOptionsResponse["options"]>([]);
  const [selectedWatchlist, setSelectedWatchlist] = useState<string | null>(null);
  const [data, setData] = useState<WeeklyPriceWatchlistScannerResponse | null>(null);
  const [selectedDetails, setSelectedDetails] = useState<ShortHorizonStockRow | null>(null);
  const [activeTab, setActiveTab] = useState("all");
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
  const eligibleRows = useMemo(() => filterShortHorizonRowsByShortlistGuards(rows), [rows]);
  const shortlistRows = useMemo(() => buildShortHorizonShortlistRows(rows), [rows]);
  const coreRows = useMemo(() => buildShortHorizonCoreRows(rows), [rows]);
  const shortlistSizePerRule = calculateShortHorizonShortlistSize(rows.length);

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
                    columns={buildStockColumns(false, false, false, true, true, true, setSelectedDetails, onOpenCompactStockReview)}
                    dataSource={rows}
                  />
                ),
              },
              {
                key: "shortlist",
                label: `Shortlist · ${shortlistRows.length}`,
                children: (
                  <div>
                    <Text type="secondary" className="short-horizon-tab-note">Passed {eligibleRows.length} / {rows.length} · Best {shortlistSizePerRule} by each history.</Text>
                    <Text type="secondary" className="short-horizon-rule-note"><strong>Rules:</strong> {getShortHorizonShortlistRuleDescription()}</Text>
                    <Table<ShortHorizonStockRow>
                      data-testid="short-horizon-shortlist-table"
                      rowKey="key"
                      size="small"
                      pagination={false}
                      scroll={{ x: true }}
                      columns={buildStockColumns(true, false, false, false, false, false, setSelectedDetails, onOpenCompactStockReview)}
                      dataSource={shortlistRows}
                    />
                  </div>
                ),
              },
              {
                key: "core",
                label: `Core · ${coreRows.length}`,
                children: (
                  <div>
                    <Text type="secondary" className="short-horizon-tab-note">Selected by both rankings · sorted nearest to the 52-week high · current moves, strong finishes, move quality, and exit pressure shown for context.</Text>
                    <Text type="secondary" className="short-horizon-rule-note"><strong>Core:</strong> A stock must be in the top {shortlistSizePerRule} by both 5D reach count and Recent tested 6D reach count. The 52-week-high distance only orders this list; it does not reject a stock.</Text>
                    <Table<ShortHorizonStockRow>
                      data-testid="short-horizon-core-table"
                      rowKey="key"
                      size="small"
                      pagination={false}
                      scroll={{ x: true }}
                      columns={buildStockColumns(true, true, true, false, false, false, setSelectedDetails, onOpenCompactStockReview)}
                      dataSource={coreRows}
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
