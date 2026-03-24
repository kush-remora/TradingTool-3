import { Table, Typography, Tag, Space, Progress, Button, Tooltip } from "antd";
import { SyncOutlined } from "@ant-design/icons";
import { useState } from "react";
import { useWatchlist } from "../hooks/useWatchlist";
import { StockDetailDrawer } from "./StockDetailDrawer";
import type { WatchlistRow } from "../types";

const { Text } = Typography;

export function WatchlistDashboard({ tag = "" }: { tag?: string }) {
  const { rows, loading, refreshIndicators } = useWatchlist(tag);
  const [detailSymbol, setDetailSymbol] = useState<string | null>(null);

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
      width: 140,
      fixed: "left" as const,
      render: (v: string, record: WatchlistRow) => (
        <div style={{ display: "flex", flexDirection: "column" }}>
          <Space>
            <Text strong style={{ fontSize: 14 }}>{v}</Text>
          </Space>
          <Text type="secondary" style={{ fontSize: 11 }}>{record.exchange}</Text>
        </div>
      )
    },
    {
      title: "PRICE (LTP)",
      dataIndex: "ltp",
      key: "ltp",
      width: 120,
      render: (v: number | null) => <Text strong>{formatMoney(v)}</Text>
    },
    {
      title: "CHANGE %",
      dataIndex: "changePercent",
      key: "changePercent",
      width: 100,
      render: (v: number | null) => renderColorValue(v, "", "%")
    },
    {
      title: "SMA 50",
      dataIndex: "sma50",
      key: "sma50",
      width: 100,
      render: (v: number | null) => <Text>{formatMoney(v)}</Text>
    },
    {
      title: "SMA 200",
      dataIndex: "sma200",
      key: "sma200",
      width: 100,
      render: (v: number | null) => <Text>{formatMoney(v)}</Text>
    },
    {
      title: "RSI (14)",
      dataIndex: "rsi14",
      key: "rsi14",
      width: 160,
      render: (v: number | null) => {
        if (v === null) return <Text type="secondary">-</Text>;
        let strokeColor = "#bfbfbf";
        let statusText = "Neutral";
        if (v < 30) { strokeColor = "#52c41a"; statusText = "Oversold"; }
        else if (v > 70) { strokeColor = "#ff4d4f"; statusText = "Overbought"; }
        
        return (
          <div style={{ display: "flex", flexDirection: "column", gap: 2 }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <Text style={{ fontSize: 12, fontWeight: 500 }}>{v.toFixed(1)}</Text>
              <Text style={{ fontSize: 10, color: strokeColor }}>{statusText}</Text>
            </div>
            <Progress percent={v} showInfo={false} strokeColor={strokeColor} size="small" style={{ margin: 0 }} />
          </div>
        );
      }
    },
    {
      title: "MACD",
      dataIndex: "macdSignal",
      key: "macdSignal",
      width: 110,
      render: (v: string | null) => {
        if (!v) return <Text type="secondary">-</Text>;
        const isBull = v.toUpperCase() === "BULLISH";
        const color = isBull ? "#52c41a" : "#ff4d4f";
        return (
          <Tag color={isBull ? "success" : "error"} style={{ width: "100%", textAlign: "center", margin: 0 }}>
            <span style={{ fontWeight: 600 }}>{v.toUpperCase()}</span>
          </Tag>
        );
      }
    },
    {
      title: "DRAWDOWN %",
      dataIndex: "drawdownPct",
      key: "drawdownPct",
      width: 130,
      render: (v: number | null) => renderColorValue(v, "", "%")
    },
    {
      title: "VOL VS AVG (20D)",
      dataIndex: "volumeVsAvg",
      key: "volumeVsAvg",
      width: 140,
      render: (v: number | null) => {
        if (v === null) return <Text type="secondary">-</Text>;
        // Represents ratio e.g. 1.5x
        const ratioColor = v > 2.0 ? "#faad14" : v > 1.25 ? "#52c41a" : "#8c8c8c";
        return <Text style={{ color: ratioColor, fontWeight: 500 }}>{v.toFixed(2)}x</Text>;
      }
    }
  ];

  return (
    <div style={{ padding: "16px 24px", height: "100%", display: "flex", flexDirection: "column" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
        <div>
          <Text strong style={{ fontSize: 18, letterSpacing: 0.5 }}>WATCHLIST {tag ? `: ${tag.toUpperCase()}` : ""}</Text>
        </div>
        <Tooltip title="Trigger Cache Refresh (Background Job)">
          <Button type="text" icon={<SyncOutlined />} onClick={refreshIndicators}>
            Refresh Engine
          </Button>
        </Tooltip>
      </div>
      
      <Table
        dataSource={rows}
        columns={columns}
        rowKey="symbol"
        loading={loading}
        pagination={false}
        scroll={{ y: 'calc(100vh - 180px)', x: 'max-content' }}
        size="small"
        style={{ borderRadius: 8, overflow: "hidden", border: "1px solid #f0f0f0" }}
        onRow={(record) => ({
          onClick: () => setDetailSymbol(record.symbol),
          style: { cursor: "pointer" },
        })}
      />

      <StockDetailDrawer
        symbol={detailSymbol}
        onClose={() => setDetailSymbol(null)}
      />
    </div>
  );
}
