import { ReloadOutlined } from "@ant-design/icons";
import { useEffect, useMemo, useState } from "react";
import { Alert, Button, Card, Col, DatePicker, Empty, Input, Row, Select, Space, Spin, Statistic, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import dayjs from "dayjs";

import { useChartinkFiftyTwoWeekHighReport } from "../hooks/useChartinkFiftyTwoWeekHighReport";
import type {
  ChartinkFiftyTwoWeekHighBacktestReport,
  ChartinkFiftyTwoWeekHighTradeRow,
} from "../types";

type TradeGroupRow = {
  key: string;
  symbol: string;
  marketCapBucket: string;
  sector: string;
  appearanceCount: number;
  successCount: number;
  failureCount: number;
  openCount: number;
  winRatePct: number | null;
  avgHoldingDays: number | null;
  latestSignalDate: string;
  earliestSignalDate: string;
  trades: ChartinkFiftyTwoWeekHighTradeRow[];
};

type DateRangeValue = [dayjs.Dayjs | null, dayjs.Dayjs | null] | null;

function formatNumber(value: number | null | undefined, fractionDigits: number = 2): string {
  if (value == null) {
    return "-";
  }

  return value.toLocaleString("en-IN", {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  });
}

function formatPercent(value: number | null | undefined): string {
  if (value == null) {
    return "-";
  }
  return `${formatNumber(value, 2)}%`;
}

function formatDate(value: string | null | undefined): string {
  if (!value) {
    return "-";
  }
  return dayjs(value).format("DD MMM YYYY");
}

function toOutcomeColor(outcome: string): string | undefined {
  switch (outcome) {
    case "TARGET_HIT":
      return "green";
    case "STOP_LOSS":
      return "red";
    case "EXIT_AT_END":
      return "blue";
    case "NO_PRICE_DATA":
    case "NO_NEXT_TRADING_DAY":
      return "default";
    default:
      return "gold";
  }
}

function summarizeBucket(trades: ChartinkFiftyTwoWeekHighTradeRow[], bucket: string) {
  const bucketTrades = trades.filter((trade) => trade.marketCapBucket === bucket);
  const enteredTrades = bucketTrades.filter((trade) => trade.entryDate !== null);
  const successCount = enteredTrades.filter((trade) => trade.success).length;

  return {
    totalSignals: bucketTrades.length,
    enteredTrades: enteredTrades.length,
    successRatePct: enteredTrades.length === 0 ? null : (successCount / enteredTrades.length) * 100,
  };
}

function buildGroupRows(trades: ChartinkFiftyTwoWeekHighTradeRow[]): TradeGroupRow[] {
  const groups = new Map<string, ChartinkFiftyTwoWeekHighTradeRow[]>();

  trades.forEach((trade) => {
    const current = groups.get(trade.symbol) ?? [];
    current.push(trade);
    groups.set(trade.symbol, current);
  });

  return Array.from(groups.entries())
    .map(([symbol, rows]) => {
      const orderedRows = [...rows].sort((left, right) => right.signalDate.localeCompare(left.signalDate));
      const enteredRows = orderedRows.filter((row) => row.entryDate !== null);
      const successCount = enteredRows.filter((row) => row.success).length;
      const failureCount = enteredRows.filter((row) => !row.success).length;
      const totalHoldingDays = enteredRows.reduce((sum, row) => sum + (row.holdingTradingDays ?? 0), 0);

      return {
        key: symbol,
        symbol,
        marketCapBucket: orderedRows[0]?.marketCapBucket ?? "Unknown",
        sector: orderedRows[0]?.sector ?? "-",
        appearanceCount: orderedRows.length,
        successCount,
        failureCount,
        openCount: orderedRows.length - enteredRows.length,
        winRatePct: enteredRows.length === 0 ? null : (successCount / enteredRows.length) * 100,
        avgHoldingDays: enteredRows.length === 0 ? null : totalHoldingDays / enteredRows.length,
        latestSignalDate: orderedRows[0]?.signalDate ?? "",
        earliestSignalDate: orderedRows[orderedRows.length - 1]?.signalDate ?? "",
        trades: orderedRows,
      };
    })
    .sort((left, right) => {
      if (right.appearanceCount !== left.appearanceCount) {
        return right.appearanceCount - left.appearanceCount;
      }
      return right.latestSignalDate.localeCompare(left.latestSignalDate);
    });
}

function getDefaultStrategy(report: ChartinkFiftyTwoWeekHighBacktestReport | null): string | null {
  if (!report || report.strategies.length === 0) {
    return null;
  }
  return report.strategies[0].name;
}

export function ChartinkFiftyTwoWeekHighPage() {
  const { data, loading, error, loadReport } = useChartinkFiftyTwoWeekHighReport();
  const [selectedStrategy, setSelectedStrategy] = useState<string | null>(null);
  const [symbolQuery, setSymbolQuery] = useState("");
  const [selectedBucket, setSelectedBucket] = useState<string | undefined>(undefined);
  const [selectedSector, setSelectedSector] = useState<string | undefined>(undefined);
  const [selectedOutcome, setSelectedOutcome] = useState<string | undefined>(undefined);
  const [signalDateRange, setSignalDateRange] = useState<DateRangeValue>(null);

  useEffect(() => {
    void loadReport()
      .then((report) => {
        setSelectedStrategy((current) => current ?? getDefaultStrategy(report));
      })
      .catch(() => undefined);
  }, [loadReport]);

  const strategyOptions = useMemo(() => {
    return (data?.strategies ?? []).map((strategy) => ({
      label: `${strategy.name} (${strategy.profitTargetPct}% / ${strategy.stopLossPct}% stop)`,
      value: strategy.name,
    }));
  }, [data?.strategies]);

  const strategyTrades = useMemo(() => {
    if (!data || !selectedStrategy) {
      return [];
    }
    return data.trades.filter((trade) => trade.strategyName === selectedStrategy);
  }, [data, selectedStrategy]);

  const bucketOptions = useMemo(() => {
    return Array.from(new Set(strategyTrades.map((trade) => trade.marketCapBucket)))
      .sort()
      .map((bucket) => ({ label: bucket, value: bucket }));
  }, [strategyTrades]);

  const sectorOptions = useMemo(() => {
    return Array.from(new Set(strategyTrades.map((trade) => trade.sector)))
      .sort()
      .map((sector) => ({ label: sector, value: sector }));
  }, [strategyTrades]);

  const outcomeOptions = useMemo(() => {
    return Array.from(new Set(strategyTrades.map((trade) => trade.outcome)))
      .sort()
      .map((outcome) => ({ label: outcome, value: outcome }));
  }, [strategyTrades]);

  const filteredTrades = useMemo(() => {
    const normalizedQuery = symbolQuery.trim().toUpperCase();
    const rangeStart = signalDateRange?.[0]?.format("YYYY-MM-DD") ?? null;
    const rangeEnd = signalDateRange?.[1]?.format("YYYY-MM-DD") ?? null;

    return strategyTrades.filter((trade) => {
      if (normalizedQuery.length > 0 && !trade.symbol.includes(normalizedQuery)) {
        return false;
      }
      if (selectedBucket && trade.marketCapBucket !== selectedBucket) {
        return false;
      }
      if (selectedSector && trade.sector !== selectedSector) {
        return false;
      }
      if (selectedOutcome && trade.outcome !== selectedOutcome) {
        return false;
      }
      if (rangeStart && trade.signalDate < rangeStart) {
        return false;
      }
      if (rangeEnd && trade.signalDate > rangeEnd) {
        return false;
      }
      return true;
    });
  }, [selectedBucket, selectedOutcome, selectedSector, signalDateRange, strategyTrades, symbolQuery]);

  const groupedRows = useMemo(() => buildGroupRows(filteredTrades), [filteredTrades]);

  const filteredEnteredTrades = useMemo(
    () => filteredTrades.filter((trade) => trade.entryDate !== null),
    [filteredTrades],
  );

  const successCount = useMemo(
    () => filteredEnteredTrades.filter((trade) => trade.success).length,
    [filteredEnteredTrades],
  );

  const avgHoldingDays = useMemo(() => {
    if (filteredEnteredTrades.length === 0) {
      return null;
    }
    const total = filteredEnteredTrades.reduce((sum, trade) => sum + (trade.holdingTradingDays ?? 0), 0);
    return total / filteredEnteredTrades.length;
  }, [filteredEnteredTrades]);

  const baselineSummary = useMemo(() => {
    if (!data || !selectedStrategy) {
      return null;
    }
    return data.summaries.find((summary) => summary.strategyName === selectedStrategy) ?? null;
  }, [data, selectedStrategy]);

  const columns = useMemo<ColumnsType<TradeGroupRow>>(() => {
    return [
      {
        title: "Symbol",
        dataIndex: "symbol",
        key: "symbol",
        sorter: (left, right) => left.symbol.localeCompare(right.symbol),
        render: (value: string, row: TradeGroupRow) => (
          <Space direction="vertical" size={0}>
            <Typography.Text strong>{value}</Typography.Text>
            <Space size={6} wrap>
              <Tag color="blue">{row.marketCapBucket}</Tag>
              <Tag>{row.sector}</Tag>
            </Space>
          </Space>
        ),
      },
      {
        title: "Appearances",
        dataIndex: "appearanceCount",
        key: "appearanceCount",
        sorter: (left, right) => left.appearanceCount - right.appearanceCount,
      },
      {
        title: "Signal Window",
        key: "signalWindow",
        sorter: (left, right) => left.latestSignalDate.localeCompare(right.latestSignalDate),
        render: (_value: unknown, row: TradeGroupRow) => (
          <Space direction="vertical" size={0}>
            <Typography.Text>{formatDate(row.latestSignalDate)}</Typography.Text>
            <Typography.Text type="secondary">{formatDate(row.earliestSignalDate)}</Typography.Text>
          </Space>
        ),
      },
      {
        title: "Wins / Losses",
        key: "resultCounts",
        render: (_value: unknown, row: TradeGroupRow) => (
          <Space wrap>
            <Tag color="green">Win {row.successCount}</Tag>
            <Tag color="red">Loss {row.failureCount}</Tag>
            {row.openCount > 0 ? <Tag>Open/No Entry {row.openCount}</Tag> : null}
          </Space>
        ),
      },
      {
        title: "Win Rate",
        dataIndex: "winRatePct",
        key: "winRatePct",
        sorter: (left, right) => (left.winRatePct ?? -1) - (right.winRatePct ?? -1),
        render: (value: number | null) => <Typography.Text strong>{formatPercent(value)}</Typography.Text>,
      },
      {
        title: "Avg Hold",
        dataIndex: "avgHoldingDays",
        key: "avgHoldingDays",
        sorter: (left, right) => (left.avgHoldingDays ?? -1) - (right.avgHoldingDays ?? -1),
        render: (value: number | null) => (value == null ? "-" : `${formatNumber(value, 1)} days`),
      },
    ];
  }, []);

  const expandedColumns = useMemo<ColumnsType<ChartinkFiftyTwoWeekHighTradeRow>>(() => {
    return [
      {
        title: "Signal Date",
        dataIndex: "signalDate",
        key: "signalDate",
        render: (value: string) => formatDate(value),
      },
      {
        title: "Entry",
        key: "entry",
        render: (_value: unknown, row: ChartinkFiftyTwoWeekHighTradeRow) => (
          <Space direction="vertical" size={0}>
            <Typography.Text>{formatDate(row.entryDate)}</Typography.Text>
            <Typography.Text type="secondary">₹{formatNumber(row.entryPrice)}</Typography.Text>
          </Space>
        ),
      },
      {
        title: "Exit",
        key: "exit",
        render: (_value: unknown, row: ChartinkFiftyTwoWeekHighTradeRow) => (
          <Space direction="vertical" size={0}>
            <Typography.Text>{formatDate(row.exitDate)}</Typography.Text>
            <Typography.Text type="secondary">₹{formatNumber(row.exitPrice)}</Typography.Text>
          </Space>
        ),
      },
      {
        title: "Outcome",
        dataIndex: "outcome",
        key: "outcome",
        render: (value: string, row: ChartinkFiftyTwoWeekHighTradeRow) => (
          <Space wrap>
            <Tag color={toOutcomeColor(value)}>{value}</Tag>
            {row.exitWasAmbiguous ? <Tag color="orange">AMBIGUOUS BAR</Tag> : null}
          </Space>
        ),
      },
      {
        title: "Return",
        dataIndex: "returnPct",
        key: "returnPct",
        render: (value: number | null) => formatPercent(value),
      },
      {
        title: "Hold Days",
        dataIndex: "holdingTradingDays",
        key: "holdingTradingDays",
        render: (value: number | null) => (value == null ? "-" : `${value}`),
      },
      {
        title: "Forward 20D",
        dataIndex: "forward20dReturnPct",
        key: "forward20dReturnPct",
        render: (value: number | null) => formatPercent(value),
      },
      {
        title: "MFE / MAE",
        key: "excursions",
        render: (_value: unknown, row: ChartinkFiftyTwoWeekHighTradeRow) => (
          <Space direction="vertical" size={0}>
            <Typography.Text>MFE {formatPercent(row.maxFavorableExcursionPct)}</Typography.Text>
            <Typography.Text type="secondary">MAE {formatPercent(row.maxAdverseExcursionPct)}</Typography.Text>
          </Space>
        ),
      },
    ];
  }, []);

  const largecapSummary = useMemo(() => summarizeBucket(filteredTrades, "Largecap"), [filteredTrades]);
  const midcapSummary = useMemo(() => summarizeBucket(filteredTrades, "Midcap"), [filteredTrades]);
  const smallcapSummary = useMemo(() => summarizeBucket(filteredTrades, "Smallcap"), [filteredTrades]);

  const resetFilters = (): void => {
    setSymbolQuery("");
    setSelectedBucket(undefined);
    setSelectedSector(undefined);
    setSelectedOutcome(undefined);
    setSignalDateRange(null);
  };

  return (
    <div style={{ padding: 24 }}>
      <Card
        title="Chartink 52-Week High Backtest"
        extra={(
          <Button icon={<ReloadOutlined />} onClick={() => void loadReport().catch(() => undefined)} loading={loading}>
            Reload
          </Button>
        )}
      >
        <Space direction="vertical" size={16} style={{ width: "100%" }}>
          <Typography.Text type="secondary">
            Explore next-day-open entries from the generated Chartink report, then filter and group repeated symbols to study which patterns behave safely.
          </Typography.Text>

          {error ? <Alert type="error" showIcon message={error} /> : null}

          {loading && !data ? (
            <div style={{ display: "flex", justifyContent: "center", padding: "48px 0" }}>
              <Spin size="large" />
            </div>
          ) : null}

          {!loading && !data && !error ? <Empty description="No report loaded yet." /> : null}

          {data ? (
            <Space direction="vertical" size={16} style={{ width: "100%" }}>
              <Space wrap>
                <Tag color="blue">Signals {data.signalCount}</Tag>
                <Tag color="purple">Unique Symbols {data.uniqueSymbolCount}</Tag>
                <Tag>Generated {formatDate(data.generatedAt)}</Tag>
                <Tag>Price Data Through {formatDate(data.priceDataToDate)}</Tag>
                <Tag>{data.inputFile}</Tag>
              </Space>

              <Card size="small">
                <Space wrap size={12}>
                  <Select
                    style={{ minWidth: 280 }}
                    placeholder="Strategy"
                    value={selectedStrategy ?? undefined}
                    options={strategyOptions}
                    onChange={setSelectedStrategy}
                  />
                  <Input
                    style={{ width: 220 }}
                    placeholder="Filter by symbol"
                    value={symbolQuery}
                    onChange={(event) => setSymbolQuery(event.target.value.toUpperCase())}
                  />
                  <DatePicker.RangePicker
                    value={signalDateRange}
                    onChange={(value) => setSignalDateRange(value)}
                    allowClear
                  />
                  <Select
                    style={{ minWidth: 140 }}
                    placeholder="Cap bucket"
                    value={selectedBucket}
                    options={bucketOptions}
                    allowClear
                    onChange={(value) => setSelectedBucket(value)}
                  />
                  <Select
                    style={{ minWidth: 180 }}
                    placeholder="Sector"
                    value={selectedSector}
                    options={sectorOptions}
                    allowClear
                    showSearch
                    onChange={(value) => setSelectedSector(value)}
                  />
                  <Select
                    style={{ minWidth: 180 }}
                    placeholder="Outcome"
                    value={selectedOutcome}
                    options={outcomeOptions}
                    allowClear
                    onChange={(value) => setSelectedOutcome(value)}
                  />
                  <Button onClick={resetFilters}>Reset Filters</Button>
                </Space>
              </Card>

              <Row gutter={[16, 16]}>
                <Col xs={24} md={12} xl={6}>
                  <Card size="small">
                    <Statistic title="Filtered Success Rate" value={filteredEnteredTrades.length === 0 ? 0 : (successCount / filteredEnteredTrades.length) * 100} precision={2} suffix="%" />
                    <Typography.Text type="secondary">
                      {successCount} wins out of {filteredEnteredTrades.length} entered trades
                    </Typography.Text>
                  </Card>
                </Col>
                <Col xs={24} md={12} xl={6}>
                  <Card size="small">
                    <Statistic title="Grouped Symbols" value={groupedRows.length} />
                    <Typography.Text type="secondary">
                      {filteredTrades.length} filtered appearances
                    </Typography.Text>
                  </Card>
                </Col>
                <Col xs={24} md={12} xl={6}>
                  <Card size="small">
                    <Statistic title="Average Hold" value={avgHoldingDays ?? 0} precision={1} suffix="days" />
                    <Typography.Text type="secondary">
                      Entered trades only
                    </Typography.Text>
                  </Card>
                </Col>
                <Col xs={24} md={12} xl={6}>
                  <Card size="small">
                    <Statistic title="Baseline Strategy Win Rate" value={baselineSummary?.successRatePct ?? 0} precision={2} suffix="%" />
                    <Typography.Text type="secondary">
                      Full unfiltered report
                    </Typography.Text>
                  </Card>
                </Col>
              </Row>

              <Row gutter={[16, 16]}>
                <Col xs={24} md={8}>
                  <Card size="small" title="Largecap">
                    <Statistic value={largecapSummary.successRatePct ?? 0} precision={2} suffix="%" />
                    <Typography.Text type="secondary">
                      {largecapSummary.enteredTrades} entered / {largecapSummary.totalSignals} signals
                    </Typography.Text>
                  </Card>
                </Col>
                <Col xs={24} md={8}>
                  <Card size="small" title="Midcap">
                    <Statistic value={midcapSummary.successRatePct ?? 0} precision={2} suffix="%" />
                    <Typography.Text type="secondary">
                      {midcapSummary.enteredTrades} entered / {midcapSummary.totalSignals} signals
                    </Typography.Text>
                  </Card>
                </Col>
                <Col xs={24} md={8}>
                  <Card size="small" title="Smallcap">
                    <Statistic value={smallcapSummary.successRatePct ?? 0} precision={2} suffix="%" />
                    <Typography.Text type="secondary">
                      {smallcapSummary.enteredTrades} entered / {smallcapSummary.totalSignals} signals
                    </Typography.Text>
                  </Card>
                </Col>
              </Row>

              <Table<TradeGroupRow>
                rowKey="key"
                columns={columns}
                dataSource={groupedRows}
                pagination={{ pageSize: 20, showSizeChanger: true }}
                expandable={{
                  expandedRowRender: (row) => (
                    <Table<ChartinkFiftyTwoWeekHighTradeRow>
                      rowKey={(trade) => `${trade.strategyName}-${trade.symbol}-${trade.signalDate}`}
                      columns={expandedColumns}
                      dataSource={row.trades}
                      pagination={false}
                      size="small"
                    />
                  ),
                }}
              />
            </Space>
          ) : null}
        </Space>
      </Card>
    </div>
  );
}
