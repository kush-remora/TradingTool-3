import { ReloadOutlined, ThunderboltOutlined } from "@ant-design/icons";
import { Alert, Button, Card, Empty, Input, Space, Spin, Table, Tag, Tooltip, Typography, message } from "antd";
import type { TableColumnsType } from "antd";
import { useEffect, useMemo, useState } from "react";
import type { BaseSwingListResponse, BaseSwingResult } from "../types";
import { clearCache, getJson } from "../utils/api";
import { StockBadge } from "../components/StockBadge";

const { Text, Title } = Typography;

export function BaseSwingPage() {
  const [data, setData] = useState<BaseSwingListResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [stockQuery, setStockQuery] = useState("");
  const [universe, setUniverse] = useState("WATCHLIST,NIFTY 100,NIFTY MIDCAP 250");

  const fetchData = async (forceRefresh = false) => {
    setLoading(true);
    try {
      const path = `/api/screener/base-swing?universe=${universe}`;
      if (forceRefresh) clearCache(path);
      const json = await getJson<BaseSwingListResponse>(path);
      setData(json);
    } catch {
      message.error("Failed to fetch base-swing data");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void fetchData();
  }, []);

  const searchedResults = useMemo(() => {
    const q = stockQuery.trim().toLowerCase();
    const results = data?.results ?? [];
    if (!q) return results;
    return results.filter((row) => 
      row.symbol.toLowerCase().includes(q) || 
      row.companyName.toLowerCase().includes(q)
    );
  }, [data, stockQuery]);

  const columns: TableColumnsType<BaseSwingResult> = [
    {
      title: "Stock",
      dataIndex: "symbol",
      key: "symbol",
      fixed: "left",
      width: 220,
      sorter: (a, b) => a.symbol.localeCompare(b.symbol),
      render: (text: string, record: BaseSwingResult) => (
        <StockBadge symbol={text} instrumentToken={record.instrumentToken} companyName={record.companyName} fontSize={15} />
      ),
    },
    {
      title: "Price",
      dataIndex: "currentPrice",
      key: "currentPrice",
      width: 120,
      render: (val: number) => `₹${val.toLocaleString()}`
    },
    {
      title: <Tooltip title="Base Stability (30d): How much the price has moved since 30 days ago. +/- 3% is ideal for a horizontal base.">Base Stability</Tooltip>,
      dataIndex: "baseDriftPct",
      key: "baseDriftPct",
      width: 140,
      sorter: (a, b) => (a.baseDriftPct || 0) - (b.baseDriftPct || 0),
      render: (val: number | null) => {
        if (val == null) return "-";
        const color = Math.abs(val) <= 3.0 ? "success" : Math.abs(val) <= 5.0 ? "warning" : "default";
        return <Tag color={color} style={{ fontWeight: 600 }}>{val > 0 ? "+" : ""}{val.toFixed(2)}%</Tag>;
      }
    },
    {
      title: <Tooltip title="Internal Volatility (30d): The range between the 30-day High and Low. Higher means more 'breathing' room for a swing.">Internal Swing</Tooltip>,
      dataIndex: "internalVolPct",
      key: "internalVolPct",
      width: 140,
      sorter: (a, b) => a.internalVolPct - b.internalVolPct,
      render: (val: number) => {
        const color = val >= 10.0 ? "#52c41a" : val >= 7.0 ? "#faad14" : undefined;
        return <Text strong style={{ color }}>{val.toFixed(2)}%</Text>;
      }
    },
    {
      title: <Tooltip title="Distance from 52-Week High: Safety check. We want stocks established on a base, not hitting euphoria peaks.">Safety (52w High)</Tooltip>,
      dataIndex: "distFrom52wHighPct",
      key: "distFrom52wHighPct",
      width: 160,
      sorter: (a, b) => (a.distFrom52wHighPct || 0) - (b.distFrom52wHighPct || 0),
      render: (val: number | null) => {
        if (val == null) return "-";
        const color = val >= 12.0 ? "success" : val < 5.0 ? "error" : "warning";
        return <Tag color={color}>{val.toFixed(2)}% away</Tag>;
      }
    },
    {
      title: <Tooltip title="Weekly Pulse: High-Low range for each of the last 4 weeks. W1 is most recent. Hover for date range.">Weekly Pulse (H-L %)</Tooltip>,
      key: "weeklyPulses",
      width: 280,
      render: (_: any, record: BaseSwingResult) => (
        <Space size={4}>
          {record.weeklyPulses.map((pulse) => (
            <Tooltip 
                key={pulse.label} 
                title={`${pulse.label}: ${pulse.startDate} to ${pulse.endDate}`}
            >
              <Tag color={pulse.swingPct >= 7.0 ? "cyan" : "default"} style={{ margin: 0, cursor: 'help' }}>
                <span style={{ fontSize: 10, opacity: 0.8 }}>{pulse.label}:</span>
                <span style={{ fontWeight: 600, marginLeft: 4 }}>{pulse.swingPct.toFixed(1)}%</span>
              </Tag>
            </Tooltip>
          ))}
        </Space>
      )
    },
    {
      title: "Setup Score",
      dataIndex: "setupScore",
      key: "setupScore",
      fixed: "right",
      width: 120,
      sorter: (a, b) => a.setupScore - b.setupScore,
      render: (val: number) => {
        const color = val >= 80 ? "#389e0d" : (val >= 60 ? "#fa8c16" : "#8c8c8c");
        return <Text strong style={{ color, fontSize: 16 }}>{val}</Text>;
      },
    },
    {
      title: "Analysis",
      dataIndex: "reasoning",
      key: "reasoning",
      width: 300,
      render: (text: string) => <Text type="secondary" style={{ fontSize: 12 }}>{text}</Text>
    }
  ];

  return (
    <div style={{ padding: 24, background: "#f5f7fa", minHeight: "calc(100vh - 48px)" }}>
      <Space direction="vertical" size={16} style={{ width: "100%" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <div>
            <Title level={4} style={{ margin: 0 }}>
              <ThunderboltOutlined style={{ marginRight: 8, color: "#faad14" }} />
              Base-Swing Profiler (Simpler Swing)
            </Title>
            <Text type="secondary">
              Finding "Boring" stocks that are sleeping (flat monthly price) but breathing (high internal volatility).
            </Text>
          </div>
          <Button icon={<ReloadOutlined />} onClick={() => fetchData(true)} loading={loading}>
            Refresh
          </Button>
        </div>

        <Card size="small" style={{ borderRadius: 12 }}>
          <Space align="center" wrap style={{ width: "100%", justifyContent: "space-between" }}>
            <Input
              allowClear
              placeholder="Search stock (symbol/company)"
              value={stockQuery}
              onChange={(e) => setStockQuery(e.target.value)}
              style={{ width: 320 }}
            />
            <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                <Text type="secondary">Universe:</Text>
                <Input 
                    value={universe} 
                    onChange={e => setUniverse(e.target.value)} 
                    style={{ width: 350 }} 
                    placeholder="e.g. WATCHLIST,NIFTY 100"
                />
            </div>
          </Space>
        </Card>

        {loading ? (
          <div style={{ textAlign: "center", padding: 64 }}>
            <Spin size="large" tip="Analyzing boring stocks..." />
          </div>
        ) : searchedResults.length === 0 ? (
          <Card>
            <Empty description="No base-swing opportunities found." />
          </Card>
        ) : (
          <Card size="small" style={{ borderRadius: 12 }}>
            <Table
              columns={columns}
              dataSource={searchedResults}
              rowKey="symbol"
              pagination={{ pageSize: 50, showSizeChanger: true }}
              size="small"
              scroll={{ x: 1300 }}
            />
          </Card>
        )}

        <Alert
          type="info"
          showIcon
          message="How the Base-Swing Logic Works"
          description={
            <ul style={{ margin: 0, paddingLeft: 16 }}>
              <li><b>Sleeping Base:</b> We look for stocks where Current Price is almost the same as 30 days ago (+/- 3%). No breakout has happened yet.</li>
              <li><b>Breathing Room:</b> Within that 30-day base, the stock must have swung at least 7-10% (High vs Low). This is our "profit room".</li>
              <li><b>Safe Distance:</b> We avoid stocks near 52-week highs to minimize drawdown risk. Ideal distance is &gt; 12%.</li>
              <li><b>Goal:</b> Buy at the bottom of a boring base and sell as it reaches the top of its internal 30-day range.</li>
            </ul>
          }
        />
      </Space>
    </div>
  );
}
