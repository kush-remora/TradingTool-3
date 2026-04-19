import { Table, Typography, InputNumber, Card, Space, Statistic, Tag, Progress, Tooltip, Row, Col, Button, message, Alert, Select } from "antd";
import { SyncOutlined, WarningOutlined, FilterOutlined } from "@ant-design/icons";
import { useState, useMemo, useEffect, useCallback } from "react";
import { getJson } from "../utils/api";
import { StockBadge } from "../components/StockBadge";
import { LiveMarketWidget } from "../components/LiveMarketWidget";
import { StockDetailDrawer } from "../components/StockDetailDrawer";
import type { WatchlistRow } from "../types";

const { Text, Title } = Typography;
const { Option } = Select;

interface DrawdownScannerResponse {
    universe: string;
    count: number;
    results: WatchlistRow[];
    computedAt: number;
}

export function DrawdownScannerPage() {
  const [rows, setRows] = useState<WatchlistRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [universe, setUniverse] = useState<string>("NIFTY_500");
  const [threshold, setThreshold] = useState<number>(50);
  const [detailSymbol, setDetailSymbol] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [messageApi, contextHolder] = message.useMessage();

  const fetchScannerData = useCallback(async (isRefresh = false) => {
    setLoading(true);
    setError(null);
    const key = "scanner-fetch";
    if (isRefresh) {
        messageApi.open({ key, type: "loading", content: `Scanning ${universe}...`, duration: 0 });
    }

    try {
      const data = await getJson<DrawdownScannerResponse>(`/api/screener/drawdown?universe=${universe}`, { useCache: !isRefresh });
      setRows(data.results || []);
      if (isRefresh) {
        messageApi.open({ key, type: "success", content: `Scan complete for ${universe}. Found ${data.results.length} stocks.`, duration: 3 });
      }
    } catch (err: any) {
      const msg = err.message || "Failed to load scanner data";
      setError(msg);
      if (isRefresh) {
        messageApi.open({ key, type: "error", content: msg, duration: 4 });
      }
    } finally {
      setLoading(false);
    }
  }, [universe, messageApi]);

  useEffect(() => {
    fetchScannerData();
  }, [fetchScannerData]);

  const filteredRows = useMemo(() => {
    return rows.filter(row => row.drawdownPct !== null && row.drawdownPct <= -threshold);
  }, [rows, threshold]);

  const formatMoney = (val: number | null) => 
    val !== null ? `₹${val.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}` : "-";

  const renderColorValue = (val: number | null, prefix = "", suffix = "") => {
    if (val === null) return <Text type="secondary">-</Text>;
    const color = val > 0 ? "#52c41a" : val < 0 ? "#ff4d4f" : "#bfbfbf";
    const sign = val > 0 ? "+" : "";
    return (
      <Text style={{ color, fontWeight: 500 }}>
        {sign}{prefix}{val.toFixed(2)}{suffix}
      </Text>
    );
  };

  const columns = [
    {
      title: "SYMBOL",
      dataIndex: "symbol",
      key: "symbol",
      width: 180,
      fixed: "left" as const,
      sorter: (a: WatchlistRow, b: WatchlistRow) => a.symbol.localeCompare(b.symbol),
      render: (v: string, record: WatchlistRow) => (
        <div style={{ display: "flex", flexDirection: "column" }}>
          <StockBadge symbol={v} instrumentToken={record.instrumentToken} companyName={record.companyName} fontSize={14} />
          <Text type="secondary" style={{ fontSize: 11 }}>{record.exchange} · {record.companyName}</Text>
        </div>
      )
    },
    {
      title: "LIVE PRICE",
      dataIndex: "ltp",
      key: "ltp",
      width: 170,
      render: (v: number | null, record: WatchlistRow) => (
        <LiveMarketWidget
          symbol={`${record.exchange}:${record.symbol}`}
          fallbackLtp={v}
          fallbackChangePercent={record.changePercent}
        />
      )
    },
    {
      title: "DRAWDOWN %",
      dataIndex: "drawdownPct",
      key: "drawdownPct",
      width: 150,
      defaultSortOrder: "ascend" as const,
      sorter: (a: WatchlistRow, b: WatchlistRow) => (a.drawdownPct || 0) - (b.drawdownPct || 0),
      render: (v: number | null) => renderColorValue(v, "", "%")
    },
    {
        title: "TREND",
        dataIndex: "trendState",
        key: "trendState",
        width: 130,
        render: (v: string | null) => {
          if (!v) return <Text type="secondary">-</Text>;
          const trendStateMeta: Record<string, { label: string; color: string }> = {
            ABOVE_BOTH: { label: "Above Both", color: "success" },
            ABOVE_50_ONLY: { label: "Above 50 Only", color: "processing" },
            ABOVE_200_ONLY: { label: "Above 200 Only", color: "warning" },
            BELOW_BOTH: { label: "Below Both", color: "error" },
          };
          const meta = trendStateMeta[v] ?? { label: v, color: "default" };
          return <Tag color={meta.color}>{meta.label}</Tag>;
        }
    },
    {
      title: "RSI (14)",
      dataIndex: "rsi14",
      key: "rsi14",
      width: 130,
      sorter: (a: WatchlistRow, b: WatchlistRow) => (a.rsi14 || 0) - (b.rsi14 || 0),
      render: (v: number | null) => {
        if (v === null) return <Text type="secondary">-</Text>;
        return (
          <div style={{ display: "flex", flexDirection: "column", gap: 2 }}>
            <Text style={{ fontSize: 12, fontWeight: 500 }}>{v.toFixed(1)}</Text>
            <Progress percent={v} showInfo={false} size="small" style={{ margin: 0 }} />
          </div>
        );
      }
    },
    {
        title: "VOL VS AVG",
        dataIndex: "volumeVsAvg",
        key: "volumeVsAvg",
        width: 120,
        sorter: (a: WatchlistRow, b: WatchlistRow) => (a.volumeVsAvg || 0) - (b.volumeVsAvg || 0),
        render: (v: number | null) => {
          if (v === null) return <Text type="secondary">-</Text>;
          const ratioColor = v > 2.0 ? "#faad14" : v > 1.25 ? "#52c41a" : "#8c8c8c";
          return <Text style={{ color: ratioColor, fontWeight: 500 }}>{v.toFixed(2)}x</Text>;
        }
    }
  ];

  return (
    <div style={{ padding: "24px", minHeight: "calc(100vh - 64px)", background: "#f5f7fa" }}>
      {contextHolder}
      <Row gutter={[24, 24]}>
        {error && (
          <Col span={24}>
            <Alert message="Error loading data" description={error} type="error" showIcon />
          </Col>
        )}
        
        <Col span={24}>
          <Card bordered={false} style={{ borderRadius: 12, boxShadow: "0 2px 8px rgba(0,0,0,0.05)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 16 }}>
              <div>
                <Title level={3} style={{ margin: 0 }}>Global Drawdown Scanner</Title>
                <Text type="secondary">Identify stocks trading significantly below their 1-year high across universes</Text>
              </div>
              <Space size="large">
                <div style={{ background: "#f0f2f5", padding: "8px 16px", borderRadius: 8 }}>
                   <Space size={12}>
                        <Text strong>Universe:</Text>
                        <Select 
                            value={universe} 
                            onChange={setUniverse} 
                            style={{ width: 180 }}
                            disabled={loading}
                        >
                            <Option value="WATCHLIST">My Watchlist</Option>
                            <Option value="NIFTY_500">Nifty 500</Option>
                            <Option value="LARGEMIDCAP_250">LargeMidcap 250</Option>
                            <Option value="SMALLCAP_250">Smallcap 250</Option>
                            <Option value="NIFTY_50">Nifty 50</Option>
                        </Select>
                   </Space>
                </div>

                <div style={{ background: "#f0f2f5", padding: "8px 16px", borderRadius: 8 }}>
                   <Space size={12}>
                        <Text strong>Min Drawdown %:</Text>
                        <InputNumber
                            min={0}
                            max={100}
                            value={threshold}
                            onChange={(val) => setThreshold(val || 0)}
                            formatter={value => `${value}%`}
                            parser={value => value!.replace('%', '')}
                            style={{ width: 90 }}
                        />
                   </Space>
                </div>

                <Button 
                    icon={<SyncOutlined />} 
                    loading={loading} 
                    onClick={() => fetchScannerData(true)}
                    type="primary"
                >
                    Run Scan
                </Button>
                <Statistic title="Matched" value={filteredRows.length} suffix={`/ ${rows.length}`} />
              </Space>
            </div>
          </Card>
        </Col>

        <Col span={24}>
          <Table
            dataSource={filteredRows}
            columns={columns}
            rowKey="symbol"
            loading={loading}
            pagination={{ pageSize: 50, showSizeChanger: true }}
            scroll={{ x: 'max-content' }}
            size="middle"
            onRow={(record) => ({
                onClick: () => setDetailSymbol(record.symbol),
                style: { cursor: "pointer" }
            })}
            style={{ 
                borderRadius: 12, 
                overflow: "hidden", 
                boxShadow: "0 2px 8px rgba(0,0,0,0.05)",
                background: "#fff" 
            }}
          />
        </Col>
      </Row>

      <StockDetailDrawer
        symbol={detailSymbol}
        onClose={() => setDetailSymbol(null)}
      />
    </div>
  );
}
