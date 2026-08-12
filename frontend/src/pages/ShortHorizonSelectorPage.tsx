import { ArrowDownOutlined, ArrowUpOutlined, MinusOutlined } from "@ant-design/icons";
import { Alert, Button, Card, Empty, Modal, Select, Space, Spin, Table, Tabs, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useMemo, useState, type ReactNode } from "react";
import type { UniverseOptionsResponse, WeeklyPriceWatchlistScannerResponse } from "../types";
import { getJson } from "../utils/api";
import {
  buildShortHorizonCoreRows,
  buildShortHorizonRows,
  buildShortHorizonShortlistRows,
  calculateShortHorizonShortlistSize,
  filterShortHorizonRowsByShortlistGuards,
  getShortHorizonShortlistRuleDescription,
  type ClosePositionBucket,
  type PriceDirection,
  type ShortHorizonStockRow,
  type ShortHorizonSuccessDay,
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

function renderBucketTag(bucket: ClosePositionBucket, count: number): ReactNode {
  const color = bucket === "HIGH" ? "green" : bucket === "LOW" ? "red" : "gold";
  const label = bucket === "MIDDLE" ? "MID" : bucket;
  return <Tag color={color}>{label} {count}</Tag>;
}

function SuccessDetails({ row }: { row: ShortHorizonStockRow }) {
  const successColumns: ColumnsType<ShortHorizonSuccessDay> = [
    { title: "Starting day", dataIndex: "date", key: "date", width: 120, render: formatDate },
    { title: "Start close", dataIndex: "startClose", key: "startClose", width: 105, render: formatPrice },
    { title: "Next 5D high", dataIndex: "forwardHigh", key: "forwardHigh", width: 110, render: formatPrice },
    { title: "Move", dataIndex: "movePct", key: "movePct", width: 80, render: (value: number) => `+${value.toFixed(1)}%` },
    {
      title: "Starting day ended",
      key: "closePosition",
      width: 150,
      render: (_, day) => <ClosePositionBar positionPct={day.closePositionPct} bucket={day.closePositionBucket} direction={day.direction} />,
    },
  ];

  return (
    <div className="short-horizon-details">
      <div className="short-horizon-details-summary">
        <div>
          <span className="short-horizon-details-number">{row.successfulDayCount}</span>
          <span>successful starting days out of {row.eligibleDayCount}</span>
        </div>
        <div className="short-horizon-details-rate">{formatPercent(row.successRatePct)}</div>
      </div>
      <Text type="secondary">On those successful days, the close finished here:</Text>
      <div className="short-horizon-bucket-summary">
        {renderBucketTag("HIGH", row.successCloseBuckets.HIGH)}
        {renderBucketTag("MIDDLE", row.successCloseBuckets.MIDDLE)}
        {renderBucketTag("LOW", row.successCloseBuckets.LOW)}
      </div>
      {row.successfulDays.length > 0 ? (
        <Table<ShortHorizonSuccessDay>
          className="short-horizon-details-table"
          size="small"
          rowKey="key"
          pagination={false}
          columns={successColumns}
          dataSource={row.successfulDays}
          scroll={{ x: true }}
        />
      ) : (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No successful examples in this window." />
      )}
    </div>
  );
}

function buildStockColumns(
  showRecentEvidence: boolean,
  showCurrentMoveEvidence: boolean,
  showFiftyTwoWeekEvidence: boolean,
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
      width: 120,
      render: (_, row) => (
        <Space orientation="vertical" size={0}>
          <Text>{formatPrice(row.latestClose)}</Text>
          <Text type="secondary" className="short-horizon-date">{formatDate(row.latestDate)}</Text>
        </Space>
      ),
    },
    {
      title: "Target reached (+5%)",
      key: "successfulDays",
      width: 155,
      sorter: (left, right) => left.successfulDayCount - right.successfulDayCount,
      render: (_, row) => (
        <Space orientation="vertical" size={0}>
          <Text strong>20D {row.successfulDayCount} / {row.eligibleDayCount}</Text>
          <Text type="secondary" className="short-horizon-date">6D {row.recentSuccessfulDayCount} / {row.recentEligibleDayCount}</Text>
        </Space>
      ),
    },
  ];

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
      title: "Largest 5D volume",
      key: "recentVolumeMultiple",
      width: 170,
      sorter: (left, right) => (left.recentVolumeMultiple ?? -1) - (right.recentVolumeMultiple ?? -1),
      render: (_, row) => {
        const direction = row.recentVolumeDirection?.toLowerCase() ?? "unknown";
        return (
          <Space orientation="vertical" size={0}>
            <span className={`short-horizon-volume-value short-horizon-volume-${direction}`}>
              {getDirectionIcon(row.recentVolumeDirection)} <strong>{formatMultiple(row.recentVolumeMultiple)}</strong>
            </span>
            <Text type="secondary" className="short-horizon-date">{formatDate(row.recentVolumeDate)} · prior 20D avg</Text>
          </Space>
        );
      },
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

  const contextColumns: ColumnsType<ShortHorizonStockRow> = [
    {
      title: "Latest finish",
      key: "latestFinish",
      width: 175,
      render: (_, row) => <ClosePositionBar positionPct={row.latestClosePositionPct} bucket={row.latestClosePositionBucket} direction={row.latestDirection} />,
    },
    {
      title: "Action",
      key: "action",
      width: 145,
      render: (_, row) => (
        <Space size={4}>
          <Button size="small" onClick={() => onDetails(row)}>Details</Button>
          <Button size="small" type="primary" onClick={() => onReview(row.symbol)}>Review</Button>
        </Space>
      ),
    },
  ];

  return [numberColumn, ...identityColumns, ...currentMoveColumns, ...recentEvidenceColumns, ...fiftyTwoWeekEvidenceColumns, ...contextColumns];
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
          <Select
            aria-label="Watchlist"
            loading={loadingOptions}
            value={selectedWatchlist}
            onChange={setSelectedWatchlist}
            placeholder="Select a watchlist"
            style={{ width: 280, maxWidth: "100%" }}
            options={options.map((option) => ({ value: option.value, label: `${option.label} (${option.count})` }))}
          />
        </div>
        {data && <Text className="short-horizon-method-note" type="secondary">Past +5%: last 20 usable starting days · future window: next 5 trading sessions · latest finish uses the latest available daily candle.</Text>}
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
                    columns={buildStockColumns(false, false, false, setSelectedDetails, onOpenCompactStockReview)}
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
                      columns={buildStockColumns(true, false, false, setSelectedDetails, onOpenCompactStockReview)}
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
                    <Text type="secondary" className="short-horizon-tab-note">Selected by both rankings · sorted nearest to the 52-week high · current moves shown for context.</Text>
                    <Text type="secondary" className="short-horizon-rule-note"><strong>Core:</strong> A stock must be in the top {shortlistSizePerRule} by both 20-day success count and recent 6-day success count. The 52-week-high distance only orders this list; it does not reject a stock.</Text>
                    <Table<ShortHorizonStockRow>
                      data-testid="short-horizon-core-table"
                      rowKey="key"
                      size="small"
                      pagination={false}
                      scroll={{ x: true }}
                      columns={buildStockColumns(true, true, true, setSelectedDetails, onOpenCompactStockReview)}
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
        title={selectedDetails ? `${selectedDetails.symbol} · Past +5% details` : "Past +5% details"}
        open={selectedDetails != null}
        onCancel={() => setSelectedDetails(null)}
        footer={null}
        width={760}
      >
        {selectedDetails && <SuccessDetails row={selectedDetails} />}
      </Modal>
    </div>
  );
}
