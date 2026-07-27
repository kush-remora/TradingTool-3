import { useEffect, useMemo, useState } from "react";
import { Alert, Button, Card, Empty, Select, Space, Spin, Table, Tabs, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useAbsoluteDeliveryBacktest } from "../hooks/useAbsoluteDeliveryBacktest";
import type {
  AbsoluteDeliveryBacktestRow,
  AbsoluteDeliveryDataStatus,
  AbsoluteDeliveryTrendDataStatus,
} from "../types";

function formatInteger(value: number | null | undefined): string {
  return value == null ? "-" : value.toLocaleString("en-IN", { maximumFractionDigits: 0 });
}

function formatPercentage(value: number | null | undefined): string {
  return value == null ? "-" : `${value.toFixed(2)}%`;
}

function formatPrice(value: number | null | undefined): string {
  return value == null ? "-" : value.toFixed(2);
}

function renderPass(passed: boolean): React.ReactNode {
  return <Tag color={passed ? "green" : "default"}>{passed ? "Pass" : "Fail"}</Tag>;
}

function renderDataStatus(status: AbsoluteDeliveryDataStatus): React.ReactNode {
  if (status === "AVAILABLE") {
    return <Tag>Available</Tag>;
  }
  const labels: Record<Exclude<AbsoluteDeliveryDataStatus, "AVAILABLE">, string> = {
    MISSING_FROM_SOURCE: "Missing from source",
    INCOMPLETE: "Incomplete",
    NO_RECORD: "No record",
  };
  return <Tag color="orange">{labels[status]}</Tag>;
}

function renderTrendDataStatus(status: AbsoluteDeliveryTrendDataStatus): React.ReactNode {
  if (status === "AVAILABLE") {
    return <Tag>Available</Tag>;
  }
  return (
    <Tag color="orange">
      {status === "NO_CANDLE" ? "No candle" : "Insufficient history"}
    </Tag>
  );
}

export function AbsoluteDeliveryBacktestPage() {
  const [selectedGrouping, setSelectedGrouping] = useState("groww_HIGH_QUALITY");
  const {
    data,
    groupings,
    loading,
    loadingGroupings,
    error,
    groupingError,
    loadGroupings,
    loadBacktest,
  } = useAbsoluteDeliveryBacktest();

  useEffect(() => {
    void loadGroupings().catch(() => undefined);
  }, [loadGroupings]);

  useEffect(() => {
    void loadBacktest(selectedGrouping).catch(() => undefined);
  }, [loadBacktest, selectedGrouping]);

  const groupingOptions = useMemo(
    () => groupings.map((grouping) => ({
      value: grouping.value,
      label: `${grouping.value} (${grouping.count})`,
    })),
    [groupings],
  );

  const symbolFilters = useMemo(
    () => Array.from(new Set(data?.allRows.map((row) => row.symbol) ?? []))
      .sort()
      .map((symbol) => ({ text: symbol, value: symbol })),
    [data?.allRows],
  );

  const baseColumns = useMemo<ColumnsType<AbsoluteDeliveryBacktestRow>>(
    () => [
      {
        title: "Symbol",
        dataIndex: "symbol",
        key: "symbol",
        fixed: "left",
        filters: symbolFilters,
        onFilter: (value, row) => row.symbol === value,
        sorter: (left, right) => left.symbol.localeCompare(right.symbol),
        render: (value: string) => <Typography.Text strong>{value}</Typography.Text>,
      },
      {
        title: "Company",
        dataIndex: "companyName",
        key: "companyName",
        render: (value: string) => value || "-",
      },
      {
        title: "Date",
        dataIndex: "tradingDate",
        key: "tradingDate",
        sorter: (left, right) => left.tradingDate.localeCompare(right.tradingDate),
      },
      {
        title: "Traded Qty",
        dataIndex: "tradedQuantity",
        key: "tradedQuantity",
        align: "right",
        sorter: (left, right) => (left.tradedQuantity ?? -1) - (right.tradedQuantity ?? -1),
        render: formatInteger,
      },
      {
        title: "Delivery Qty",
        dataIndex: "deliveryQuantity",
        key: "deliveryQuantity",
        align: "right",
        sorter: (left, right) => (left.deliveryQuantity ?? -1) - (right.deliveryQuantity ?? -1),
        render: formatInteger,
      },
      {
        title: "Delivery %",
        dataIndex: "deliveryPercentage",
        key: "deliveryPercentage",
        align: "right",
        sorter: (left, right) => (left.deliveryPercentage ?? -1) - (right.deliveryPercentage ?? -1),
        render: formatPercentage,
      },
      {
        title: "Close",
        dataIndex: "closePrice",
        key: "closePrice",
        align: "right",
        render: formatPrice,
      },
      {
        title: "SMA50",
        dataIndex: "sma50",
        key: "sma50",
        align: "right",
        render: formatPrice,
      },
      {
        title: "SMA200",
        dataIndex: "sma200",
        key: "sma200",
        align: "right",
        render: formatPrice,
      },
      {
        title: "SMA50 (-20)",
        dataIndex: "sma50TwentySessionsAgo",
        key: "sma50TwentySessionsAgo",
        align: "right",
        render: formatPrice,
      },
    ],
    [symbolFilters],
  );

  const auditColumns = useMemo<ColumnsType<AbsoluteDeliveryBacktestRow>>(
    () => [
      ...baseColumns,
      {
        title: "Volume Gate",
        dataIndex: "tradedQuantityPassed",
        key: "tradedQuantityPassed",
        align: "center",
        render: renderPass,
      },
      {
        title: "Delivery Gate",
        dataIndex: "deliveryQuantityPassed",
        key: "deliveryQuantityPassed",
        align: "center",
        render: renderPass,
      },
      {
        title: "% Gate",
        dataIndex: "deliveryPercentagePassed",
        key: "deliveryPercentagePassed",
        align: "center",
        render: renderPass,
      },
      {
        title: "Price > 50",
        dataIndex: "priceAboveSma50Passed",
        key: "priceAboveSma50Passed",
        align: "center",
        render: renderPass,
      },
      {
        title: "50 > 200",
        dataIndex: "sma50AboveSma200Passed",
        key: "sma50AboveSma200Passed",
        align: "center",
        render: renderPass,
      },
      {
        title: "50 Rising",
        dataIndex: "sma50RisingPassed",
        key: "sma50RisingPassed",
        align: "center",
        render: renderPass,
      },
      {
        title: "Trend",
        dataIndex: "uptrendMatched",
        key: "uptrendMatched",
        align: "center",
        render: (matched: boolean) => (
          <Tag color={matched ? "green" : "default"}>{matched ? "Uptrend" : "Not uptrend"}</Tag>
        ),
      },
      {
        title: "Trend Data",
        dataIndex: "trendDataStatus",
        key: "trendDataStatus",
        render: renderTrendDataStatus,
      },
      {
        title: "Result",
        dataIndex: "matched",
        key: "matched",
        align: "center",
        render: (matched: boolean) => (
          <Tag color={matched ? "green" : "default"}>{matched ? "Match" : "No match"}</Tag>
        ),
      },
      {
        title: "Data",
        dataIndex: "dataStatus",
        key: "dataStatus",
        render: renderDataStatus,
      },
    ],
    [baseColumns],
  );

  const matchedTable = data && data.matchedRows.length > 0 ? (
    <Table<AbsoluteDeliveryBacktestRow>
      rowKey={(row) => `${row.symbol}-${row.tradingDate}`}
      columns={baseColumns}
      dataSource={data.matchedRows}
      pagination={{ pageSize: 50, showSizeChanger: true }}
      scroll={{ x: 1250 }}
      size="small"
    />
  ) : (
    <Empty description="No events matched all three absolute delivery conditions." />
  );

  const fullTable = data && data.allRows.length > 0 ? (
    <Table<AbsoluteDeliveryBacktestRow>
      rowKey={(row) => `${row.symbol}-${row.tradingDate}`}
      columns={auditColumns}
      dataSource={data.allRows}
      pagination={{ pageSize: 100, showSizeChanger: true }}
      scroll={{ x: 2200 }}
      size="small"
    />
  ) : (
    <Empty description="No delivery rows are available for the selected grouping." />
  );

  return (
    <div style={{ padding: 20, background: "#f5f7fa", minHeight: "calc(100vh - 48px)" }}>
      <Card
        size="small"
        title="Absolute Delivery Backtest"
        extra={
          <Button
            size="small"
            loading={loading}
            onClick={() => void loadBacktest(selectedGrouping).catch(() => undefined)}
          >
            Reload
          </Button>
        }
      >
        <Space orientation="vertical" size={12} style={{ width: "100%" }}>
          <Typography.Text type="secondary">
            Six-month event audit of the selected institutional grouping. This is evidence for review, not a buy signal.
          </Typography.Text>

          <Space size={8} wrap>
            <Typography.Text style={{ fontSize: 12 }}>Grouping</Typography.Text>
            <Select
              aria-label="Institutional grouping"
              size="small"
              showSearch
              optionFilterProp="label"
              style={{ minWidth: 280 }}
              value={selectedGrouping}
              options={groupingOptions}
              loading={loadingGroupings}
              disabled={loading}
              onChange={setSelectedGrouping}
            />
          </Space>

          {groupingError && <Alert type="error" showIcon message={groupingError} />}
          {error && <Alert type="error" showIcon message={error} />}
          {loading && !data && <div style={{ padding: 40, textAlign: "center" }}><Spin /></div>}

          {data && (
            <>
              <Space wrap size={[6, 6]}>
                <Tag color="blue">{data.summary.fromDate} to {data.summary.toDate}</Tag>
                <Tag>Symbols {formatInteger(data.summary.watchlistSymbolCount)}</Tag>
                <Tag>Trading days {formatInteger(data.summary.tradingDateCount)}</Tag>
                <Tag>Rows {formatInteger(data.summary.expectedRowCount)}</Tag>
                <Tag>Evaluated {formatInteger(data.summary.evaluatedRowCount)}</Tag>
                <Tag color={data.summary.missingRowCount > 0 ? "orange" : "default"}>
                  Missing {formatInteger(data.summary.missingRowCount)}
                </Tag>
                <Tag color="green">Matches {formatInteger(data.summary.matchedRowCount)}</Tag>
              </Space>

              <Typography.Text style={{ fontSize: 12 }}>
                Formula: traded quantity ≥ {formatInteger(data.criteria.minimumTradedQuantityInclusive)},
                {" "}delivery quantity &gt; {formatInteger(data.criteria.minimumDeliveryQuantityExclusive)},
                {" "}delivery percentage &gt; {formatPercentage(data.criteria.minimumDeliveryPercentageExclusive)};
                {" "}close &gt; SMA{data.criteria.shortSmaPeriod},
                {" "}SMA{data.criteria.shortSmaPeriod} &gt; SMA{data.criteria.longSmaPeriod},
                {" "}and SMA{data.criteria.shortSmaPeriod} rising over
                {" "}{data.criteria.shortSmaSlopeLookbackSessions} sessions.
              </Typography.Text>

              <Tabs
                defaultActiveKey="matched"
                items={[
                  {
                    key: "matched",
                    label: `Matched Events (${data.summary.matchedRowCount})`,
                    children: matchedTable,
                  },
                  {
                    key: "all",
                    label: `Entire Grouping (${data.summary.expectedRowCount})`,
                    children: fullTable,
                  },
                ]}
              />
            </>
          )}
        </Space>
      </Card>
    </div>
  );
}
