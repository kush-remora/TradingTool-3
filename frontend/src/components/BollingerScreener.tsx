import {
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
import type { TableColumnsType } from "antd";
import { useEffect, useState } from "react";
import { getJson } from "../utils/api";
import { StockBadge } from "./StockBadge";

const { Text, Title } = Typography;

interface BollingerScanResult {
  symbol: string;
  companyName: string;
  instrumentToken: number;
  ltp: number;
  bbUpper: number;
  bbLower: number;
  bbMiddle: number;
  percentB: number;
  bandwidth: number;
  isSqueeze: boolean;
  rsi14: number | null;
  signal: string;
  setupScore: number;
  reasoning: string;
}

interface BollingerScanResponse {
  runAt: string;
  universe: string;
  results: BollingerScanResult[];
}

interface UniverseOption {
  label: string;
  value: string;
  count: number;
}

interface UniverseOptionsResponse {
  options: UniverseOption[];
}

export function BollingerScreener() {
  const [data, setData] = useState<BollingerScanResponse | null>(null);
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
      const json = await getJson<BollingerScanResponse>(`/api/screener/bollinger?universe=${targetUniverse}`);
      setData(json);
      message.success(`Scan completed for ${targetUniverse}`);
    } catch (err) {
      console.error(err);
      message.error("Failed to fetch Bollinger data");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void fetchUniverses();
    void fetchData(universe);
  }, [universe]);

  const filteredResults = data?.results.filter(row => 
    row.symbol.toLowerCase().includes(searchText.toLowerCase()) ||
    row.companyName.toLowerCase().includes(searchText.toLowerCase())
  ) ?? [];

  const columns: TableColumnsType<BollingerScanResult> = [
    {
      title: "Stock",
      key: "symbol",
      width: 220,
      fixed: "left",
      render: (_, row) => (
        <div style={{ display: "flex", flexDirection: "column" }}>
          <StockBadge symbol={row.symbol} instrumentToken={row.instrumentToken as any} companyName={row.companyName} fontSize={14} />
          <Text type="secondary" style={{ fontSize: 11 }}>{row.companyName}</Text>
        </div>
      ),
      sorter: (a, b) => a.symbol.localeCompare(b.symbol),
    },
    {
      title: "Signal",
      key: "signal",
      width: 130,
      render: (_, row) => {
        let color = "default";
        if (row.signal === "SQUEEZE") color = "purple";
        if (row.signal === "OVERSOLD") color = "success";
        if (row.signal === "OVERBOUGHT") color = "error";
        return <Tag color={color}>{row.signal}</Tag>;
      },
      filters: [
        { text: "SQUEEZE", value: "SQUEEZE" },
        { text: "OVERSOLD", value: "OVERSOLD" },
        { text: "OVERBOUGHT", value: "OVERBOUGHT" },
        { text: "NORMAL", value: "NORMAL" },
      ],
      onFilter: (value, record) => record.signal === value,
    },
    {
      title: "Score",
      dataIndex: "setupScore",
      width: 100,
      sorter: (a, b) => a.setupScore - b.setupScore,
      render: (score) => {
        const color = score > 70 ? "#389e0d" : score > 40 ? "#fa8c16" : "#8c8c8c";
        return <Text strong style={{ color }}>{score}</Text>;
      }
    },
    {
      title: "LTP",
      dataIndex: "ltp",
      width: 120,
      render: (val) => `₹${val.toLocaleString()}`,
      sorter: (a, b) => a.ltp - b.ltp,
    },
    {
      title: "%B",
      dataIndex: "percentB",
      width: 100,
      render: (val) => {
        const color = val <= 0.05 ? "#389e0d" : val >= 0.95 ? "#cf1322" : undefined;
        return <Text style={{ color, fontWeight: color ? "bold" : "normal" }}>{val.toFixed(2)}</Text>;
      },
      sorter: (a, b) => a.percentB - b.percentB,
    },
    {
      title: "Bandwidth %",
      dataIndex: "bandwidth",
      width: 120,
      render: (val) => `${val.toFixed(2)}%`,
      sorter: (a, b) => a.bandwidth - b.bandwidth,
    },
    {
      title: "RSI (14)",
      dataIndex: "rsi14",
      width: 100,
      render: (val) => val ? val.toFixed(2) : "-",
      sorter: (a, b) => (a.rsi14 || 0) - (b.rsi14 || 0),
    },
    {
      title: "Reasoning",
      dataIndex: "reasoning",
      width: 300,
      render: (text) => <Text type="secondary" style={{ fontSize: 12 }}>{text}</Text>,
    },
  ];

  return (
    <div style={{ height: "100%", display: "flex", flexDirection: "column", gap: 16 }}>
      <Card size="small" style={{ borderRadius: 8 }}>
        <Space size="large" wrap>
          <div>
            <Text type="secondary" style={{ display: "block", marginBottom: 4 }}>Select Index / Universe</Text>
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
              Run Scan
            </Button>
          </div>
        </Space>
      </Card>

      <div style={{ background: "#fff", borderRadius: 12, padding: "0px", flex: 1, display: "flex", flexDirection: "column" }}>
        <div style={{ padding: "16px 20px" }}>
          <Title level={5} style={{ margin: 0 }}>Bollinger Band Analysis</Title>
          <Text type="secondary" style={{ fontSize: 12 }}>
            Last run: {data ? new Date(data.runAt).toLocaleString() : "Never"} • BB (20, 2)
          </Text>
        </div>
        <Table<BollingerScanResult>
          dataSource={filteredResults}
          columns={columns}
          rowKey="symbol"
          pagination={{ pageSize: 50, showSizeChanger: true }}
          scroll={{ x: 1200, y: "calc(100vh - 400px)" }}
          loading={loading}
          size="small"
          sticky
        />
      </div>
    </div>
  );
}
