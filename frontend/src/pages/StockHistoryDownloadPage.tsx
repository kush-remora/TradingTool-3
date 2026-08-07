import { DownloadOutlined } from "@ant-design/icons";
import { Alert, Button, Card, Empty, Select, Space, Spin, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useMemo, useState } from "react";
import { useInstrumentSearch } from "../hooks/useInstrumentSearch";
import { useStockDetail } from "../hooks/useStockDetail";
import type { InstrumentSearchResult } from "../types";
import { buildStockHistoryCsv, buildStockHistoryRows, type StockHistoryCsvRow } from "../utils/stockHistoryCsv";

const { Text, Title } = Typography;

const LOOKBACK_OPTIONS = [
  { label: "1 month", value: 1, days: 31 },
  { label: "3 months", value: 3, days: 93 },
  { label: "6 months", value: 6, days: 186 },
];

const formatNumber = (value: number | null): string => value == null ? "—" : value.toLocaleString("en-IN", { maximumFractionDigits: 2 });
const formatPercent = (value: number | null): string => value == null ? "—" : `${value.toFixed(2)}%`;

const columns: ColumnsType<StockHistoryCsvRow> = [
  { title: "Date", dataIndex: "date", key: "date", width: 96 },
  { title: "Day", dataIndex: "day", key: "day", width: 56 },
  { title: "Open", dataIndex: "open", key: "open", render: formatNumber },
  { title: "Close", dataIndex: "close", key: "close", render: formatNumber },
  { title: "Low", dataIndex: "low", key: "low", render: formatNumber },
  { title: "High", dataIndex: "high", key: "high", render: formatNumber },
  { title: "Open → High %", dataIndex: "openToHighPct", key: "openToHighPct", render: formatPercent },
  { title: "Open → Close %", dataIndex: "openToClosePct", key: "openToClosePct", render: formatPercent },
  { title: "Volume", dataIndex: "volume", key: "volume", render: formatNumber },
  { title: "Delivery volume", dataIndex: "deliveryVolume", key: "deliveryVolume", render: formatNumber },
  { title: "Delivery %", dataIndex: "deliveryPct", key: "deliveryPct", render: formatPercent },
];

export function StockHistoryDownloadPage(): JSX.Element {
  const [selectedSymbol, setSelectedSymbol] = useState<string | null>(null);
  const [lookbackMonths, setLookbackMonths] = useState(3);
  const { allInstruments, loading: instrumentsLoading, error: instrumentsError } = useInstrumentSearch();
  const nseEquities = useMemo(
    () => allInstruments.filter((instrument) => instrument.exchange === "NSE" && instrument.instrument_type === "EQ"),
    [allInstruments],
  );
  const selectedInstrument = useMemo<InstrumentSearchResult | null>(
    () => nseEquities.find((instrument) => instrument.trading_symbol === selectedSymbol) ?? null,
    [nseEquities, selectedSymbol],
  );
  const selectedLookback = LOOKBACK_OPTIONS.find((option) => option.value === lookbackMonths) ?? LOOKBACK_OPTIONS[1];
  const { data, loading, error } = useStockDetail(selectedInstrument?.trading_symbol ?? null, selectedLookback.days);
  const rows = useMemo(() => data ? buildStockHistoryRows(data) : [], [data]);
  const previewRows = useMemo(() => [...rows].reverse(), [rows]);

  const downloadCsv = (): void => {
    if (!selectedInstrument || rows.length === 0) return;
    const csv = buildStockHistoryCsv(rows);
    const blob = new Blob(["\uFEFF", csv], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `${selectedInstrument.trading_symbol}_history_${lookbackMonths}m.csv`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  };

  return (
    <div style={{ padding: 24, maxWidth: 1500, margin: "0 auto" }}>
      <Space orientation="vertical" size={16} style={{ width: "100%" }}>
        <Card size="small">
          <Space orientation="vertical" size={10} style={{ width: "100%" }}>
            <div>
              <Title level={3} style={{ margin: 0 }}>Stock History CSV</Title>
              <Text type="secondary">Download daily OHLCV and available delivery data for one NSE equity.</Text>
            </div>
            <Space wrap style={{ width: "100%" }}>
              <Select
                aria-label="Stock"
                showSearch
                allowClear
                optionFilterProp="label"
                placeholder={instrumentsLoading ? "Loading stocks…" : "Select NSE stock"}
                options={nseEquities.map((instrument) => ({
                  label: `${instrument.trading_symbol} — ${instrument.company_name}`,
                  value: instrument.trading_symbol,
                }))}
                value={selectedSymbol}
                onChange={(value: string | undefined) => setSelectedSymbol(value ?? null)}
                disabled={instrumentsLoading || nseEquities.length === 0}
                style={{ width: 320 }}
                size="small"
              />
              <Select
                aria-label="Period"
                value={lookbackMonths}
                options={LOOKBACK_OPTIONS.map(({ label, value }) => ({ label, value }))}
                onChange={setLookbackMonths}
                style={{ width: 120 }}
                size="small"
              />
              <Button
                aria-label="Download CSV"
                type="primary"
                size="small"
                icon={<DownloadOutlined />}
                onClick={downloadCsv}
                disabled={!selectedInstrument || rows.length === 0}
                loading={loading}
              >
                Download CSV
              </Button>
            </Space>
            {instrumentsError && <Text type="danger">{instrumentsError}</Text>}
          </Space>
        </Card>

        {error && <Alert type="error" showIcon message={error} />}
        {loading && <Card size="small"><Spin size="small" /> <Text type="secondary">Loading daily history…</Text></Card>}
        {!loading && selectedInstrument && data && (
          <Card
            size="small"
            title={`${data.symbol} · ${selectedLookback.label}`}
            extra={<Text type="secondary">{rows.length} trading days · latest first</Text>}
          >
            <Table<StockHistoryCsvRow>
              rowKey="key"
              columns={columns}
              dataSource={previewRows}
              size="small"
              pagination={{ pageSize: 25, showSizeChanger: false }}
              scroll={{ x: 1100 }}
            />
          </Card>
        )}
        {!loading && !selectedInstrument && <Card size="small"><Empty description="Select a stock to load its history." /></Card>}
      </Space>
    </div>
  );
}
