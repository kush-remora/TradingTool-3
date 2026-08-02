import dayjs, { type Dayjs } from "dayjs";
import { Alert, Button, Card, DatePicker, Empty, Select, Space, Spin, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useMemo, useState } from "react";
import { usePriceAcceptanceScanner } from "../hooks/usePriceAcceptanceScanner";
import type { PriceAcceptanceRow, PriceAcceptanceScanResponse, UniverseOptionsResponse } from "../types";
import { getJson } from "../utils/api";

const UNIVERSES_PATH = "/api/strategy/price-acceptance/universes";
const { Text, Title } = Typography;

function formatPrice(value: number): string {
  return `₹${value.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function formatHitRate(value: number): string {
  return `${value.toFixed(1)}%`;
}

function renderHitCount(count: number, rate: number): string {
  return `${count} (${formatHitRate(rate)})`;
}

function buildColumns(): ColumnsType<PriceAcceptanceRow> {
  return [
    { title: "Symbol", dataIndex: "symbol", key: "symbol", fixed: "left", width: 110 },
    { title: "Anchor date", dataIndex: "anchorDate", key: "anchorDate", width: 120 },
    {
      title: "Today's body",
      key: "body",
      width: 170,
      render: (_value: unknown, row: PriceAcceptanceRow) => `${formatPrice(row.bodyLow)} – ${formatPrice(row.bodyHigh)}`,
    },
    {
      title: "Body width",
      dataIndex: "bodyRangePct",
      key: "bodyRangePct",
      width: 100,
      sorter: (left, right) => left.bodyRangePct - right.bodyRangePct,
      render: formatHitRate,
    },
    { title: "Prior sessions", dataIndex: "priorSessionCount", key: "priorSessionCount", width: 110 },
    {
      title: "20D hits (rate)",
      key: "closeHits20",
      width: 130,
      sorter: (left, right) => left.closeHits20 - right.closeHits20,
      render: (_value: unknown, row: PriceAcceptanceRow) => renderHitCount(row.closeHits20, row.closeHitRate20Pct),
    },
    {
      title: "40D hits (rate)",
      key: "closeHits40",
      width: 130,
      sorter: (left, right) => left.closeHits40 - right.closeHits40,
      render: (_value: unknown, row: PriceAcceptanceRow) => renderHitCount(row.closeHits40, row.closeHitRate40Pct),
    },
    {
      title: "60D hits (rate)",
      key: "closeHits60",
      width: 130,
      sorter: (left, right) => left.closeHits60 - right.closeHits60,
      render: (_value: unknown, row: PriceAcceptanceRow) => renderHitCount(row.closeHits60, row.closeHitRate60Pct),
    },
    {
      title: "80D hits (rate)",
      key: "closeHits80",
      width: 130,
      sorter: (left, right) => left.closeHits80 - right.closeHits80,
      render: (_value: unknown, row: PriceAcceptanceRow) => renderHitCount(row.closeHits80, row.closeHitRate80Pct),
    },
    {
      title: "100D hits (rate)",
      key: "closeHits100",
      width: 140,
      defaultSortOrder: "descend",
      sorter: (left, right) => left.closeHits100 - right.closeHits100,
      render: (_value: unknown, row: PriceAcceptanceRow) => renderHitCount(row.closeHits100, row.closeHitRate100Pct),
    },
  ];
}

export function PriceAcceptanceScannerPage() {
  const { data, loading, error, run } = usePriceAcceptanceScanner();
  const [universeOptions, setUniverseOptions] = useState<UniverseOptionsResponse["options"]>([]);
  const [selectedIndexKey, setSelectedIndexKey] = useState<string>();
  const [asOfDate, setAsOfDate] = useState<string>(dayjs().format("YYYY-MM-DD"));
  const [loadingUniverses, setLoadingUniverses] = useState(true);
  const [universeError, setUniverseError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    void getJson<UniverseOptionsResponse>(UNIVERSES_PATH, { useCache: false })
      .then((response) => {
        if (!active) return;
        setUniverseOptions(response.options);
        setSelectedIndexKey(response.options[0]?.value);
      })
      .catch((cause: unknown) => {
        if (active) setUniverseError(cause instanceof Error ? cause.message : "Failed to load universes.");
      })
      .finally(() => {
        if (active) setLoadingUniverses(false);
      });

    return () => {
      active = false;
    };
  }, []);

  const columns = useMemo(() => buildColumns(), []);

  const handleDateChange = (value: Dayjs | null): void => {
    if (value) setAsOfDate(value.format("YYYY-MM-DD"));
  };

  const handleRun = (): void => {
    if (selectedIndexKey) {
      void run({ indexKey: selectedIndexKey, asOfDate });
    }
  };

  return (
    <div style={{ padding: "24px 24px 160px" }}>
      <Space orientation="vertical" size={16} style={{ width: "100%" }}>
        <Card>
          <Space orientation="vertical" size={10} style={{ width: "100%" }}>
            <Title level={3} style={{ margin: 0 }}>Price Acceptance Scanner</Title>
            <Text type="secondary">
              Uses the selected day&apos;s open-close body and counts prior closes inside it over 20, 40, 60, 80, and 100 sessions.
            </Text>
            <Text type="secondary" style={{ fontSize: 12 }}>
              This is a price-acceptance hint, not an accumulation verdict. Today&apos;s anchor candle is excluded from the counts.
            </Text>
            {universeError && <Alert type="error" message={universeError} showIcon />}
            {error && <Alert type="error" message={error} showIcon />}
            <Space wrap>
              <Select
                aria-label="Index or watchlist"
                showSearch
                loading={loadingUniverses}
                value={selectedIndexKey}
                onChange={setSelectedIndexKey}
                placeholder="Select an index or watchlist"
                style={{ width: 340, maxWidth: "100%" }}
                options={universeOptions.map((option) => ({
                  value: option.value,
                  label: `${option.label} (${option.count})`,
                }))}
              />
              <DatePicker
                aria-label="As of date"
                value={dayjs(asOfDate)}
                onChange={handleDateChange}
                allowClear={false}
              />
              <Button
                type="primary"
                onClick={handleRun}
                disabled={!selectedIndexKey || loading}
                loading={loading}
              >
                Run Scanner
              </Button>
            </Space>
          </Space>
        </Card>

        {data && <ScanSummary data={data} />}

        <Card>
          {loading && !data ? <Spin /> : data && data.rows.length > 0 ? (
            <Table<PriceAcceptanceRow>
              rowKey="symbol"
              columns={columns}
              dataSource={data.rows}
              pagination={{ pageSize: 50, showSizeChanger: false }}
              scroll={{ x: 1350 }}
              size="small"
            />
          ) : (
            <Empty description="Select a universe and run the scanner." />
          )}
        </Card>
      </Space>
    </div>
  );
}

function ScanSummary({ data }: { data: PriceAcceptanceScanResponse }) {
  return (
    <Card size="small">
      <Space wrap>
        <Text strong>{data.selectedIndexKey}</Text>
        <Text type="secondary">Requested as-of: {data.requestedAsOfDate}</Text>
        <Text type="secondary">Stocks scanned: {data.scannedStockCount}</Text>
        <Text type="secondary">With usable history: {data.resultCount}</Text>
      </Space>
    </Card>
  );
}
