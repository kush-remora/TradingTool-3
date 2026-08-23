import { ReloadOutlined } from "@ant-design/icons";
import { Alert, Button, Card, Empty, Input, List, Select, Space, Table, Tag, Typography } from "antd";
import type { TableColumnsType } from "antd";
import { useEffect, useMemo, useState } from "react";
import { useWyckoffRawObservation } from "../hooks/useWyckoffRawObservation";
import type {
  UniverseOption,
  UniverseOptionsResponse,
  WyckoffRawObservationPhase,
  WyckoffRawObservationRow,
  WyckoffRawObservationStock,
} from "../types";
import { getJson } from "../utils/api";

const { Text, Title } = Typography;

function formatPrice(value: number | null): string {
  return value == null ? "—" : `₹${value.toLocaleString("en-IN", { maximumFractionDigits: 2 })}`;
}

function formatNumber(value: number | null, digits = 2): string {
  return value == null ? "—" : value.toFixed(digits);
}

function formatPercent(value: number | null): string {
  return value == null ? "—" : `${value >= 0 ? "+" : ""}${value.toFixed(2)}%`;
}

function formatMultiple(value: number | null): string {
  return value == null ? "—" : `${value.toFixed(2)}×`;
}

function formatVolume(value: number): string {
  return value.toLocaleString("en-IN", { maximumFractionDigits: 0 });
}

const phaseLabels: Record<WyckoffRawObservationPhase, string> = {
  PRIOR_DECLINE: "Prior decline",
  CANDIDATE_ACCUMULATION: "Candidate accumulation",
  ORDERLY_ADVANCE: "Orderly advance",
  POSSIBLE_PHASE_D: "Possible Phase D",
  UNCLASSIFIED: "Unclassified",
};

const phaseColors: Record<WyckoffRawObservationPhase, string> = {
  PRIOR_DECLINE: "red",
  CANDIDATE_ACCUMULATION: "gold",
  ORDERLY_ADVANCE: "green",
  POSSIBLE_PHASE_D: "blue",
  UNCLASSIFIED: "default",
};

const observationColumns: TableColumnsType<WyckoffRawObservationRow> = [
  { title: "Date", dataIndex: "date", key: "date", fixed: "left", width: 110 },
  { title: "Close", dataIndex: "close", key: "close", render: (value: number) => formatPrice(value), width: 100 },
  { title: "Day %", dataIndex: "closeChangePct", key: "closeChangePct", render: formatPercent, width: 90 },
  { title: "Range %", dataIndex: "rangePct", key: "rangePct", render: formatPercent, width: 95 },
  { title: "True range", dataIndex: "trueRange", key: "trueRange", render: (value: number | null) => formatNumber(value), width: 100 },
  { title: "ATR 14", dataIndex: "atr14", key: "atr14", render: (value: number | null) => formatNumber(value), width: 90 },
  { title: "ATR %", dataIndex: "atrPct", key: "atrPct", render: formatPercent, width: 90 },
  { title: "Range / ATR", dataIndex: "movementVsAtr", key: "movementVsAtr", render: formatMultiple, width: 105 },
  { title: "Volume", dataIndex: "volume", key: "volume", render: formatVolume, width: 120 },
  { title: "Vol / 20D", dataIndex: "volumeVs20dAverage", key: "volumeVs20dAverage", render: formatMultiple, width: 105 },
  { title: "20D price %", dataIndex: "priceChange20dPct", key: "priceChange20dPct", render: formatPercent, width: 115 },
  { title: "20D range %", dataIndex: "twentyDayRangePct", key: "twentyDayRangePct", render: formatPercent, width: 110 },
  { title: "Prior 20D high", dataIndex: "priorTwentyDayHigh", key: "priorTwentyDayHigh", render: formatPrice, width: 120 },
  {
    title: "Observation",
    key: "observation",
    render: (_, row) => (
      <Space orientation="vertical" size={0}>
        <Tag color={phaseColors[row.phase]}>{phaseLabels[row.phase]}</Tag>
        {row.annotation && <Text type="secondary">{row.annotation}</Text>}
      </Space>
    ),
    width: 360,
  },
];

const summaryColumns: TableColumnsType<WyckoffRawObservationStock> = [
  { title: "Stock", dataIndex: "symbol", key: "symbol", fixed: "left", width: 130, render: (value: string, row) => <Space orientation="vertical" size={0}><Text strong>{value}</Text><Text type="secondary">{row.companyName}</Text></Space> },
  { title: "Period", key: "period", width: 180, render: (_, row) => `${row.startDate} → ${row.endDate}` },
  { title: "First close", dataIndex: "firstClose", key: "firstClose", render: formatPrice, width: 110 },
  { title: "Latest close", dataIndex: "latestClose", key: "latestClose", render: formatPrice, width: 115 },
  { title: "Period move", dataIndex: "periodReturnPct", key: "periodReturnPct", render: formatPercent, width: 110 },
  { title: "Period high", dataIndex: "periodHigh", key: "periodHigh", render: formatPrice, width: 110 },
  { title: "Period low", dataIndex: "periodLow", key: "periodLow", render: formatPrice, width: 110 },
  { title: "Avg ATR %", dataIndex: "averageAtrPct", key: "averageAtrPct", render: formatPercent, width: 100 },
  { title: "Lowest ATR %", dataIndex: "lowestAtrPct", key: "lowestAtrPct", render: formatPercent, width: 110 },
  { title: "Avg volume", dataIndex: "averageVolume", key: "averageVolume", render: (value: number | null) => value == null ? "—" : formatVolume(value), width: 120 },
];

export function WyckoffRawObservationPage(): JSX.Element {
  const { data, loading, error, run } = useWyckoffRawObservation();
  const [watchlists, setWatchlists] = useState<UniverseOption[]>([]);
  const [selectedWatchlist, setSelectedWatchlist] = useState<string>();
  const [symbol, setSymbol] = useState("");
  const [mode, setMode] = useState<"watchlist" | "symbol">("watchlist");
  const [days, setDays] = useState(180);
  const [loadingWatchlists, setLoadingWatchlists] = useState(true);

  useEffect(() => {
    let active = true;
    void getJson<UniverseOptionsResponse>("/api/strategy/wyckoff/observation/watchlists")
      .then((response) => {
        if (active) {
          setWatchlists(response.options);
          setSelectedWatchlist(response.options[0]?.value);
        }
      })
      .catch(() => {
        if (active) setWatchlists([]);
      })
      .finally(() => {
        if (active) setLoadingWatchlists(false);
      });
    return () => { active = false; };
  }, []);

  const runObservation = (): void => {
    if (mode === "watchlist" && selectedWatchlist) {
      void run({ watchlist: selectedWatchlist, days });
      return;
    }
    const normalizedSymbol = symbol.trim().toUpperCase();
    if (normalizedSymbol) void run({ symbol: normalizedSymbol, days });
  };

  const summaryRows = useMemo(
    () => (data?.stocks ?? []).map((stock) => ({ ...stock, key: stock.symbol })),
    [data?.stocks],
  );

  return (
    <div style={{ padding: 24, background: "#f5f7fa", minHeight: "calc(100vh - 48px)" }}>
      <Space orientation="vertical" size={16} style={{ width: "100%" }}>
        <div>
          <Title level={4} style={{ margin: 0 }}>Wyckoff Raw Observation V1</Title>
          <Text type="secondary">Raw daily price, ATR, and volume data for human chart review. There is no score, pass/fail result, or accumulation verdict.</Text>
        </div>

        <Card size="small" title="Controls" extra={<Button icon={<ReloadOutlined />} onClick={runObservation} loading={loading}>Run</Button>}>
          <Space wrap>
            <Select
              value={mode}
              options={[{ label: "Watchlist", value: "watchlist" }, { label: "One stock", value: "symbol" }]}
              onChange={(value: "watchlist" | "symbol") => setMode(value)}
              style={{ width: 130 }}
            />
            {mode === "watchlist" ? (
              <Select
                aria-label="Watchlist"
                loading={loadingWatchlists}
                value={selectedWatchlist}
                options={watchlists.map((option) => ({ label: `${option.label} (${option.count})`, value: option.value }))}
                onChange={setSelectedWatchlist}
                placeholder="Select watchlist"
                style={{ width: 260 }}
              />
            ) : (
              <Input aria-label="Stock symbol" value={symbol} onChange={(event) => setSymbol(event.target.value)} onPressEnter={runObservation} placeholder="NSE symbol, e.g. INFY" style={{ width: 220 }} />
            )}
            <Select
              aria-label="Lookback period"
              value={days}
              options={[{ label: "6 months", value: 180 }, { label: "3 months", value: 90 }, { label: "12 months", value: 365 }]}
              onChange={setDays}
              style={{ width: 140 }}
            />
          </Space>
        </Card>

        {error && <Alert type="error" showIcon message={error} />}

        {data && (
          <Card size="small" title={`Raw observations · ${data.stocks.length} stock(s) · as of ${data.asOfDate}`}>
            {data.stocks.length === 0 ? (
              <Empty description="No candle data was returned for this request." />
            ) : (
              <Table<WyckoffRawObservationStock>
                rowKey="symbol"
                columns={summaryColumns}
                dataSource={summaryRows}
                loading={loading}
                scroll={{ x: 1250 }}
                expandable={{
                  expandedRowRender: (row) => (
                    <Space orientation="vertical" size={12} style={{ width: "100%" }}>
                      <Card size="small" title="Candidate timeline">
                        {row.periods.length === 0 ? (
                          <Text type="secondary">No simple candidate period was identified. Review the raw daily data.</Text>
                        ) : (
                          <List
                            size="small"
                            dataSource={row.periods}
                            renderItem={(period) => (
                              <List.Item>
                                <List.Item.Meta
                                  title={<Space><Tag color={phaseColors[period.phase]}>{phaseLabels[period.phase]}</Tag><Text>{period.startDate} → {period.endDate}</Text><Text type="secondary">({period.observationCount} sessions)</Text></Space>}
                                  description={period.explanation}
                                />
                              </List.Item>
                            )}
                          />
                        )}
                      </Card>
                      <Table<WyckoffRawObservationRow>
                        rowKey="date"
                        columns={observationColumns}
                        dataSource={row.observations}
                        pagination={{ pageSize: 20, showSizeChanger: false }}
                        size="small"
                        scroll={{ x: 1650 }}
                      />
                    </Space>
                  ),
                }}
              />
            )}
          </Card>
        )}
      </Space>
    </div>
  );
}
