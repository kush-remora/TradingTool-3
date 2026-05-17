import {
  ReloadOutlined,
  SearchOutlined,
  InfoCircleOutlined,
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
  Tooltip,
} from "antd";
import type { TableColumnsType } from "antd";
import { useEffect, useState } from "react";
import { getJson } from "../utils/api";
import { StockBadge } from "./StockBadge";
import type {
  BollingerSqueezeScanResponse,
  BollingerSqueezeScanResult,
  UniverseOption,
  UniverseOptionsResponse,
} from "../types";

const { Text, Title } = Typography;

export function BollingerSqueezeScreener() {
  const [data, setData] = useState<BollingerSqueezeScanResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [universe, setUniverse] = useState<string>("WATCHLIST");
  const [universeOptions, setUniverseOptions] = useState<UniverseOption[]>([]);
  const [searchText, setSearchText] = useState("");

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

  const filteredResults = data?.results.filter(row => 
    row.symbol.toLowerCase().includes(searchText.toLowerCase()) ||
    row.companyName.toLowerCase().includes(searchText.toLowerCase())
  ) ?? [];

  const columns: TableColumnsType<BollingerSqueezeScanResult> = [
    {
      title: "Stock",
      key: "symbol",
      width: 220,
      fixed: "left",
      render: (_, row) => (
        <div style={{ display: "flex", flexDirection: "column" }}>
          <StockBadge symbol={row.symbol} instrumentToken={row.instrumentToken} companyName={row.companyName} fontSize={14} />
          <Text type="secondary" style={{ fontSize: 11 }}>{row.companyName}</Text>
        </div>
      ),
      sorter: (a, b) => a.symbol.localeCompare(b.symbol),
    },
    {
      title: "Alert Status",
      dataIndex: "alertStatus",
      key: "alertStatus",
      width: 160,
      render: (status) => {
        let color = "default";
        let text = status.replace(/_/g, " ");
        if (status === "TRIGGERED_TODAY") color = "green";
        if (status === "DAY_1_ALERT") color = "orange";
        if (status === "SQUEEZING_GREEN") color = "cyan";
        if (status === "ACTIVE_SQUEEZE") color = "blue";
        if (status === "STALE_USED") color = "purple";
        return <Tag color={color} style={{ fontWeight: "bold" }}>{text}</Tag>;
      },
      filters: [
        { text: "TRIGGERED TODAY", value: "TRIGGERED_TODAY" },
        { text: "DAY 1 ALERT", value: "DAY_1_ALERT" },
        { text: "SQUEEZING GREEN", value: "SQUEEZING_GREEN" },
        { text: "ACTIVE SQUEEZE", value: "ACTIVE_SQUEEZE" },
        { text: "STALE USED", value: "STALE_USED" },
      ],
      onFilter: (value, record) => record.alertStatus === value,
    },
    {
      title: "LTP",
      dataIndex: "ltp",
      width: 110,
      render: (val) => <Text strong>₹{val.toLocaleString()}</Text>,
      sorter: (a, b) => a.ltp - b.ltp,
    },
    {
      title: "200 SMA",
      dataIndex: "above200Sma",
      width: 100,
      render: (above) => <Tag color={above ? "success" : "error"}>{above ? "Above" : "Below"}</Tag>,
    },
    {
      title: "Max DD",
      dataIndex: "maxDrawdownPct",
      width: 100,
      render: (val) => <Text type="danger">{val.toFixed(2)}%</Text>,
      sorter: (a, b) => a.maxDrawdownPct - b.maxDrawdownPct,
    },
    {
      title: (
        <Space>
          Filter 1
          <Tooltip title="3 consecutive days of tight squeeze (lowest 60-day bandwidth + 12% buffer) in the last 60 days.">
            <InfoCircleOutlined style={{ color: "#8c8c8c" }} />
          </Tooltip>
        </Space>
      ),
      key: "filter1",
      width: 150,
      render: (_, row) => (
        <Space orientation="vertical" size={0}>
          <Tag color={row.filter1Passed ? "success" : "default"}>{row.filter1Passed ? "Passed" : "Failed"}</Tag>
          {row.filter1Date && <Text type="secondary" style={{ fontSize: 10 }}>{row.filter1Date}</Text>}
        </Space>
      ),
    },
    {
      title: (
        <Space>
          Filter 2
          <Tooltip title="Standard 2-Day: 2 consecutive green closes above upper band. Fast 1-Day: Close > Upper Band, +8% move, and 10x average volume.">
            <InfoCircleOutlined style={{ color: "#8c8c8c" }} />
          </Tooltip>
        </Space>
      ),
      key: "filter2",
      width: 180,
      render: (_, row) => (
        <Space orientation="vertical" size={0}>
          <Tag color={row.filter2Passed ? "success" : "default"}>{row.filter2Passed ? "Passed" : "Failed"}</Tag>
          {row.filter2Type && <Text type="secondary" style={{ fontSize: 10 }}>{row.filter2Type.replace(/_/g, " ")}</Text>}
          {row.filter2Date && <Text type="secondary" style={{ fontSize: 10 }}>{row.filter2Date}</Text>}
        </Space>
      ),
    },
    {
      title: "RSI Context",
      key: "rsi",
      width: 200,
      render: (_, row) => (
        <Space orientation="vertical" size={0}>
          <Text style={{ fontSize: 12 }}>Current: <Text strong>{row.currentRsi?.toFixed(2) || "-"}</Text></Text>
          <Text style={{ fontSize: 12 }}>Trigger: <Text type="secondary">{row.triggerRsi?.toFixed(2) || "-"}</Text></Text>
          <Text style={{ fontSize: 12 }}>52W Max: <Text type="secondary">{row.maxRsi52w?.toFixed(2) || "-"}</Text></Text>
        </Space>
      ),
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
              options={universeOptions.map(opt => ({
                label: `${opt.label} (${opt.count} stocks)`,
                value: opt.value
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
              onChange={e => setSearchText(e.target.value)}
              style={{ width: 200 }}
              allowClear
            />
          </div>
          <div style={{ alignSelf: "flex-end" }}>
            <Button 
              type="primary" 
              icon={<ReloadOutlined />} 
              onClick={() => fetchData()} 
              loading={loading}
            >
              Run Squeeze Scan
            </Button>
          </div>
        </Space>
      </Card>

      <div style={{ background: "#fff", borderRadius: 12, padding: "0px", flex: 1, display: "flex", flexDirection: "column" }}>
        <div style={{ padding: "16px 20px" }}>
          <Title level={5} style={{ margin: 0 }}>Bollinger Squeeze Screener</Title>
          <Text type="secondary" style={{ fontSize: 12 }}>
            Last run: {data ? new Date(data.runAt).toLocaleString() : "Never"} • Rule: 3-day Squeeze in 60-day window
          </Text>
        </div>
        <Table<BollingerSqueezeScanResult>
          dataSource={filteredResults}
          columns={columns}
          rowKey="symbol"
          pagination={{ pageSize: 50, showSizeChanger: true }}
          scroll={{ x: 1300, y: "calc(100vh - 400px)" }}
          loading={loading}
          size="small"
          sticky
        />
      </div>
    </div>
  );
}
