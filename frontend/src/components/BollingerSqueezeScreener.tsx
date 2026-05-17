import {
  DownloadOutlined,
  ReloadOutlined,
  SearchOutlined,
} from "@ant-design/icons";
import {
  Button,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
  Card,
  Input,
} from "antd";
import type { TableColumnsType, TableProps } from "antd";
import { useEffect, useMemo, useState } from "react";
import { getJson } from "../utils/api";
import { StockBadge } from "./StockBadge";
import type {
  BollingerSqueezeScanResponse,
  BollingerSqueezeScanResult,
  UniverseOption,
  UniverseOptionsResponse,
} from "../types";

const { Text, Title } = Typography;

function escapeCsvValue(value: string | number | null): string {
  if (value == null) return "";
  const asString = String(value);
  if (asString.includes(",") || asString.includes('"') || asString.includes("\n")) {
    return `"${asString.replace(/"/g, '""')}"`;
  }
  return asString;
}

function compareNullableDate(left: string | null, right: string | null): number {
  if (left == null && right == null) return 0;
  if (left == null) return -1;
  if (right == null) return 1;
  return left.localeCompare(right);
}

function toDateFilterOptions(values: Array<string | null>): Array<{ text: string; value: string }> {
  return values
    .filter((value): value is string => value != null && value.trim().length > 0)
    .filter((value, index, all) => all.indexOf(value) === index)
    .sort((left, right) => right.localeCompare(left))
    .map((value) => ({ text: value, value }));
}

export function BollingerSqueezeScreener() {
  const [data, setData] = useState<BollingerSqueezeScanResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [universe, setUniverse] = useState<string>("WATCHLIST");
  const [universeOptions, setUniverseOptions] = useState<UniverseOption[]>([]);
  const [searchText, setSearchText] = useState("");
  const [tableViewRows, setTableViewRows] = useState<BollingerSqueezeScanResult[]>([]);

  const fetchUniverses = async () => {
    try {
      const json = await getJson<UniverseOptionsResponse>("/api/screener/universes");
      setUniverseOptions(json.options);
    } catch (err) {
      console.error("Failed to fetch universes", err);
    }
  };

  const fetchData = async (overrideUniverse?: string) => {
    setLoading(true);
    const targetUniverse = overrideUniverse || universe;
    try {
      const json = await getJson<BollingerSqueezeScanResponse>(
        `/api/screener/bollinger-squeeze?universe=${encodeURIComponent(targetUniverse)}`
      );
      setData(json);
      message.success(`Bollinger Squeeze scan completed for ${targetUniverse}`);
    } catch (err) {
      console.error(err);
      message.error("Failed to fetch Bollinger Squeeze data");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void fetchUniverses();
  }, []);

  useEffect(() => {
    void fetchData(universe);
  }, [universe]);

  const baseRows = useMemo(() => {
    const rows = data?.results ?? [];
    const search = searchText.trim().toLowerCase();
    if (search.length === 0) return rows;
    return rows.filter((row) =>
      row.symbol.toLowerCase().includes(search) ||
      row.companyName.toLowerCase().includes(search)
    );
  }, [data, searchText]);

  useEffect(() => {
    const defaultSortedRows = [...baseRows].sort((left, right) =>
      compareNullableDate(right.filter1LatestDate, left.filter1LatestDate)
    );
    setTableViewRows(defaultSortedRows);
  }, [baseRows]);

  const stockColumnFilters = useMemo(() => {
    return baseRows
      .map((row) => row.symbol)
      .filter((value, index, all) => all.indexOf(value) === index)
      .sort((left, right) => left.localeCompare(right))
      .map((symbol) => ({ text: symbol, value: symbol }));
  }, [baseRows]);

  const filter1OriginDateFilters = useMemo(() => toDateFilterOptions(baseRows.map((row) => row.filter1OriginDate)), [baseRows]);
  const filter1LatestDateFilters = useMemo(() => toDateFilterOptions(baseRows.map((row) => row.filter1LatestDate)), [baseRows]);
  const filter2OriginDateFilters = useMemo(() => toDateFilterOptions(baseRows.map((row) => row.filter2OriginDate)), [baseRows]);
  const filter2LatestDateFilters = useMemo(() => toDateFilterOptions(baseRows.map((row) => row.filter2LatestDate)), [baseRows]);

  const filter2TypeFilters = useMemo(() => {
    return baseRows
      .map((row) => row.filter2Type ?? "NONE")
      .filter((value, index, all) => all.indexOf(value) === index)
      .sort((left, right) => left.localeCompare(right))
      .map((value) => ({ text: value.replace(/_/g, " "), value }));
  }, [baseRows]);

  const trendOverallFilters = useMemo(() => {
    return baseRows
      .map((row) => row.trendOverallFromFilter1 ?? "UNKNOWN")
      .filter((value, index, all) => all.indexOf(value) === index)
      .sort((left, right) => left.localeCompare(right))
      .map((value) => ({ text: value, value }));
  }, [baseRows]);

  const handleTableChange: TableProps<BollingerSqueezeScanResult>["onChange"] = (
    _pagination,
    _filters,
    _sorter,
    extra
  ) => {
    setTableViewRows(extra.currentDataSource);
  };

  const downloadFilteredCsv = (): void => {
    if (tableViewRows.length === 0) {
      message.warning("No rows to export.");
      return;
    }

    const headers = [
      "Symbol",
      "Company Name",
      "LTP",
      "Filter 1 Origin Date",
      "Filter 1 Latest Date",
      "Filter 2 Origin Date",
      "Filter 2 Latest Date",
      "Filter 2 Type",
      "Trend Pattern (Since Filter1 Origin)",
      "Trend Overall (Since Filter1 Origin)",
      "Trend Net Move % (Since Filter1 Origin)",
      "Above 200 SMA",
      "Max Drawdown %",
      "Current RSI",
      "Trigger RSI",
      "52W Max RSI",
      "BB Upper",
      "BB Middle",
      "BB Lower",
    ];

    const lines = tableViewRows.map((row) => [
      row.symbol,
      row.companyName,
      row.ltp.toFixed(2),
      row.filter1OriginDate,
      row.filter1LatestDate,
      row.filter2OriginDate,
      row.filter2LatestDate,
      row.filter2Type ?? "NONE",
      row.trendPatternFromFilter1 ?? "NA",
      row.trendOverallFromFilter1 ?? "UNKNOWN",
      row.trendNetMovePctFromFilter1 != null ? row.trendNetMovePctFromFilter1.toFixed(2) : null,
      row.above200Sma ? "Above" : "Below",
      row.maxDrawdownPct.toFixed(2),
      row.currentRsi != null ? row.currentRsi.toFixed(2) : null,
      row.triggerRsi != null ? row.triggerRsi.toFixed(2) : null,
      row.maxRsi52w != null ? row.maxRsi52w.toFixed(2) : null,
      row.bbUpper.toFixed(2),
      row.bbMiddle.toFixed(2),
      row.bbLower.toFixed(2),
    ]);

    const csvContent = [headers, ...lines]
      .map((line) => line.map((value) => escapeCsvValue(value)).join(","))
      .join("\n");

    const blob = new Blob([`\uFEFF${csvContent}`], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    const runAt = data?.runAt ? new Date(data.runAt).toISOString().slice(0, 10) : "latest";
    link.href = url;
    link.download = `bollinger_squeeze_raw_${universe.toLowerCase()}_${runAt}.csv`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
    message.success(`Exported ${tableViewRows.length} rows.`);
  };

  const columns: TableColumnsType<BollingerSqueezeScanResult> = [
    {
      title: "Stock",
      key: "symbol",
      width: 230,
      fixed: "left",
      render: (_, row) => (
        <div style={{ display: "flex", flexDirection: "column" }}>
          <StockBadge symbol={row.symbol} instrumentToken={row.instrumentToken} companyName={row.companyName} fontSize={14} />
          <Text type="secondary" style={{ fontSize: 11 }}>{row.companyName}</Text>
        </div>
      ),
      sorter: (a, b) => a.symbol.localeCompare(b.symbol),
      filters: stockColumnFilters,
      filterSearch: true,
      onFilter: (value, record) => record.symbol === String(value),
    },
    {
      title: "LTP",
      dataIndex: "ltp",
      width: 110,
      render: (value: number) => <Text strong>₹{value.toLocaleString()}</Text>,
      sorter: (a, b) => a.ltp - b.ltp,
    },
    {
      title: "Filter1 Origin",
      dataIndex: "filter1OriginDate",
      width: 130,
      render: (value: string | null) => value ?? "-",
      sorter: (a, b) => compareNullableDate(a.filter1OriginDate, b.filter1OriginDate),
      filters: filter1OriginDateFilters,
      filterSearch: true,
      onFilter: (value, record) => record.filter1OriginDate === String(value),
    },
    {
      title: "Filter1 Latest",
      dataIndex: "filter1LatestDate",
      width: 130,
      render: (value: string | null) => value ?? "-",
      sorter: (a, b) => compareNullableDate(a.filter1LatestDate, b.filter1LatestDate),
      defaultSortOrder: "descend",
      filters: filter1LatestDateFilters,
      filterSearch: true,
      onFilter: (value, record) => record.filter1LatestDate === String(value),
    },
    {
      title: "Filter2 Origin",
      dataIndex: "filter2OriginDate",
      width: 130,
      render: (value: string | null) => value ?? "-",
      sorter: (a, b) => compareNullableDate(a.filter2OriginDate, b.filter2OriginDate),
      filters: filter2OriginDateFilters,
      filterSearch: true,
      onFilter: (value, record) => record.filter2OriginDate === String(value),
    },
    {
      title: "Filter2 Latest",
      dataIndex: "filter2LatestDate",
      width: 130,
      render: (value: string | null) => value ?? "-",
      sorter: (a, b) => compareNullableDate(a.filter2LatestDate, b.filter2LatestDate),
      filters: filter2LatestDateFilters,
      filterSearch: true,
      onFilter: (value, record) => record.filter2LatestDate === String(value),
    },
    {
      title: "Filter2 Type",
      dataIndex: "filter2Type",
      width: 130,
      render: (value: string | null) => value?.replace(/_/g, " ") ?? "NONE",
      sorter: (a, b) => (a.filter2Type ?? "NONE").localeCompare(b.filter2Type ?? "NONE"),
      filters: filter2TypeFilters,
      filterSearch: true,
      onFilter: (value, record) => (record.filter2Type ?? "NONE") === String(value),
    },
    {
      title: "Trend Since F1",
      key: "trendSinceF1",
      width: 250,
      render: (_, row) => (
        <Space orientation="vertical" size={0}>
          <Text style={{ fontSize: 12 }}>
            Pattern: <Text strong>{row.trendPatternFromFilter1 ?? "NA"}</Text>
          </Text>
          <Text style={{ fontSize: 12 }}>
            Overall: <Text type="secondary">{row.trendOverallFromFilter1 ?? "UNKNOWN"}</Text>
          </Text>
          <Text style={{ fontSize: 12 }}>
            Net: <Text type="secondary">{row.trendNetMovePctFromFilter1 != null ? `${row.trendNetMovePctFromFilter1.toFixed(2)}%` : "-"}</Text>
          </Text>
        </Space>
      ),
      sorter: (a, b) => (a.trendNetMovePctFromFilter1 ?? Number.NEGATIVE_INFINITY) - (b.trendNetMovePctFromFilter1 ?? Number.NEGATIVE_INFINITY),
      filters: trendOverallFilters,
      onFilter: (value, record) => (record.trendOverallFromFilter1 ?? "UNKNOWN") === String(value),
    },
    {
      title: "200 SMA",
      dataIndex: "above200Sma",
      width: 100,
      render: (above: boolean) => <Tag color={above ? "success" : "error"}>{above ? "Above" : "Below"}</Tag>,
      sorter: (a, b) => Number(a.above200Sma) - Number(b.above200Sma),
      filters: [
        { text: "Above", value: "ABOVE" },
        { text: "Below", value: "BELOW" },
      ],
      onFilter: (value, record) => (value === "ABOVE" ? record.above200Sma : !record.above200Sma),
    },
    {
      title: "Max DD",
      dataIndex: "maxDrawdownPct",
      width: 110,
      render: (value: number) => <Text type="danger">{value.toFixed(2)}%</Text>,
      sorter: (a, b) => a.maxDrawdownPct - b.maxDrawdownPct,
    },
    {
      title: "RSI Context",
      key: "rsi",
      width: 220,
      render: (_, row) => (
        <Space orientation="vertical" size={0}>
          <Text style={{ fontSize: 12 }}>Current: <Text strong>{row.currentRsi?.toFixed(2) || "-"}</Text></Text>
          <Text style={{ fontSize: 12 }}>Trigger: <Text type="secondary">{row.triggerRsi?.toFixed(2) || "-"}</Text></Text>
          <Text style={{ fontSize: 12 }}>52W Max: <Text type="secondary">{row.maxRsi52w?.toFixed(2) || "-"}</Text></Text>
        </Space>
      ),
      sorter: (a, b) => (a.currentRsi ?? Number.NEGATIVE_INFINITY) - (b.currentRsi ?? Number.NEGATIVE_INFINITY),
    },
  ];

  return (
    <div style={{ height: "100%", display: "flex", flexDirection: "column", gap: 16 }}>
      <Card size="small" style={{ borderRadius: 8 }}>
        <Space size="large" wrap>
          <div>
            <Text type="secondary" style={{ display: "block", marginBottom: 4 }}>Select Universe</Text>
            <Select
              style={{ width: 300 }}
              options={universeOptions.map((option) => ({
                label: `${option.label} (${option.count} stocks)`,
                value: option.value,
              }))}
              value={universe}
              onChange={setUniverse}
              placeholder="Select universe"
            />
          </div>
          <div>
            <Text type="secondary" style={{ display: "block", marginBottom: 4 }}>Search Symbol</Text>
            <Input
              prefix={<SearchOutlined />}
              placeholder="Search..."
              value={searchText}
              onChange={(event) => setSearchText(event.target.value)}
              style={{ width: 220 }}
              allowClear
            />
          </div>
          <div style={{ alignSelf: "flex-end" }}>
            <Space>
              <Button
                type="primary"
                icon={<ReloadOutlined />}
                onClick={() => fetchData()}
                loading={loading}
              >
                Run Squeeze Scan
              </Button>
              <Button
                icon={<DownloadOutlined />}
                onClick={downloadFilteredCsv}
                disabled={tableViewRows.length === 0}
              >
                Download Raw CSV
              </Button>
            </Space>
          </div>
        </Space>
      </Card>

      <div style={{ background: "#fff", borderRadius: 12, padding: "0px", flex: 1, display: "flex", flexDirection: "column" }}>
        <div style={{ padding: "16px 20px" }}>
          <Title level={5} style={{ margin: 0 }}>Bollinger Squeeze Screener (Raw View)</Title>
          <Text type="secondary" style={{ fontSize: 12 }}>
            Last run: {data ? new Date(data.runAt).toLocaleString() : "Never"} • Sort/filter by raw Filter1/Filter2 dates
          </Text>
          <br />
          <Text type="secondary" style={{ fontSize: 12 }}>
            Showing {tableViewRows.length} / {baseRows.length} rows
          </Text>
        </div>
        <Table<BollingerSqueezeScanResult>
          dataSource={baseRows}
          columns={columns}
          rowKey="symbol"
          onChange={handleTableChange}
          pagination={{ pageSize: 50, showSizeChanger: true }}
          scroll={{ x: 1650, y: "calc(100vh - 400px)" }}
          loading={loading}
          size="small"
          sticky
        />
      </div>
    </div>
  );
}
