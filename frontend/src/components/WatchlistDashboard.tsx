import { Table, Typography, Tag, Space, Progress, Button, Tooltip, message } from "antd";
import { SyncOutlined } from "@ant-design/icons";
import { useState } from "react";
import { useWatchlist } from "../hooks/useWatchlist";
import { StockDetailDrawer } from "./StockDetailDrawer";
import type { WatchlistRow } from "../types";
import { StockBadge } from "./StockBadge";
import { LiveMarketWidget } from "./LiveMarketWidget";
import { TradeMarketHistoryPanel } from "./TradeMarketHistoryPanel";

const { Text } = Typography;

interface WatchlistDashboardProps {
  tag?: string;
  onAddClick?: () => void;
  onRowClick?: (symbol: string) => void;
}

export function WatchlistDashboard({ tag = "", onAddClick, onRowClick }: WatchlistDashboardProps) {
  const { rows, loading, refreshing, refreshIndicators } = useWatchlist(tag);
  const [detailSymbol, setDetailSymbol] = useState<string | null>(null);
  const [expandedRows, setExpandedRows] = useState<string[]>([]);
  const [messageApi, contextHolder] = message.useMessage();

  const formatMoney = (val: number | null) => 
    val !== null ? `₹${val.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}` : "-";

  const formatRsi = (val: number | null): string => (val !== null ? val.toFixed(2) : "-");
  const formatPercent = (val: number | null): string => (val !== null ? `${val.toFixed(2)}%` : "-");

  const formatVolume = (val: number | null): string => {
    if (val === null) return "-";
    const abs = Math.abs(val);
    if (abs >= 1_000_000_000) return `${(val / 1_000_000_000).toFixed(2)}B`;
    if (abs >= 1_000_000) return `${(val / 1_000_000).toFixed(2)}M`;
    if (abs >= 1_000) return `${(val / 1_000).toFixed(2)}K`;
    return val.toFixed(0);
  };

  const compareNullableNumbers = (a: number | null, b: number | null): number => {
    if (a === null && b === null) return 0;
    if (a === null) return 1;
    if (b === null) return -1;
    return a - b;
  };

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

  const trendStateMeta: Record<string, { label: string; color: string }> = {
    ABOVE_BOTH: { label: "Above Both", color: "success" },
    ABOVE_50_ONLY: { label: "Above 50 Only", color: "processing" },
    ABOVE_200_ONLY: { label: "Above 200 Only", color: "warning" },
    BELOW_BOTH: { label: "Below Both", color: "error" },
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
          <StockBadge symbol={v} instrumentToken={record.instrumentToken} companyName={record.companyName} fontSize={14} />
          <Text type="secondary" style={{ fontSize: 11 }}>{record.exchange}</Text>
        </div>
      )
    },
    {
      title: "LIVE MARKET",
      dataIndex: "ltp",
      key: "ltp",
      width: 170,
      render: (v: number | null, record: WatchlistRow) => {
        return (
          <LiveMarketWidget
            symbol={`${record.exchange}:${record.symbol}`}
            fallbackLtp={v}
            fallbackChangePercent={record.changePercent}
          />
        );
      }
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
      title: "TREND STATE",
      dataIndex: "trendState",
      key: "trendState",
      width: 130,
      render: (v: string | null) => {
        if (!v) return <Text type="secondary">-</Text>;
        const meta = trendStateMeta[v] ?? { label: v, color: "default" };
        return (
          <Tag color={meta.color} style={{ width: "100%", textAlign: "center", margin: 0 }}>
            <span style={{ fontWeight: 600 }}>{meta.label}</span>
          </Tag>
        );
      }
    },
    {
      title: "RANGE POSITION (60D)",
      dataIndex: "rangePosition60dPct",
      key: "rangePosition60dPct",
      width: 160,
      sorter: (a: WatchlistRow, b: WatchlistRow) => compareNullableNumbers(a.rangePosition60dPct, b.rangePosition60dPct),
      render: (v: number | null, record: WatchlistRow) => {
        if (v === null) return <Text type="secondary">-</Text>;
        const clamped = Math.max(0, Math.min(100, v));
        const tooltip = (
          <div style={{ minWidth: 220, color: "#ffffff", fontSize: 12 }}>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 4 }}>
              <span>60D High</span>
              <span>{formatMoney(record.high60d)}</span>
            </div>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 4 }}>
              <span>RSI at High</span>
              <span>{formatRsi(record.rsiAtHigh60d)}</span>
            </div>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 8 }}>
              <span>Volume at High</span>
              <span>{formatVolume(record.volumeAtHigh60d)}</span>
            </div>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 4 }}>
              <span>60D Low</span>
              <span>{formatMoney(record.low60d)}</span>
            </div>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 4 }}>
              <span>RSI at Low</span>
              <span>{formatRsi(record.rsiAtLow60d)}</span>
            </div>
            <div style={{ display: "flex", justifyContent: "space-between" }}>
              <span>Volume at Low</span>
              <span>{formatVolume(record.volumeAtLow60d)}</span>
            </div>
          </div>
        );

        return (
          <Tooltip title={tooltip} placement="right">
            <div style={{ display: "flex", flexDirection: "column", gap: 2 }}>
              <Text style={{ fontSize: 11, fontWeight: 500 }}>{clamped.toFixed(0)}%</Text>
              <Progress percent={clamped} showInfo={false} size="small" />
            </div>
          </Tooltip>
        );
      }
    },
    {
      title: "RSI (14)",
      dataIndex: "rsi14",
      key: "rsi14",
      width: 130,
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
      title: "ATR (14)",
      dataIndex: "atr14Pct",
      key: "atr14Pct",
      width: 130,
      render: (_: number | null, record: WatchlistRow) => {
        if (record.atr14Pct === null || record.atr14 === null) return <Text type="secondary">-</Text>;
        const atrColor = record.atr14Pct >= 4 ? "#faad14" : record.atr14Pct >= 2 ? "#1677ff" : "#8c8c8c";
        return (
          <Text style={{ color: atrColor, fontWeight: 500 }}>
            {record.atr14Pct.toFixed(2)}% ({formatMoney(record.atr14)})
          </Text>
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
      title: "GAP TO 3M LOW %",
      dataIndex: "gapTo3mLowPct",
      key: "gapTo3mLowPct",
      width: 140,
      sorter: (a: WatchlistRow, b: WatchlistRow) => compareNullableNumbers(a.gapTo3mLowPct, b.gapTo3mLowPct),
      render: (v: number | null) => renderColorValue(v, "", "%")
    },
    {
      title: "GAP TO 3M HIGH %",
      dataIndex: "gapTo3mHighPct",
      key: "gapTo3mHighPct",
      width: 145,
      sorter: (a: WatchlistRow, b: WatchlistRow) => compareNullableNumbers(a.gapTo3mHighPct, b.gapTo3mHighPct),
      render: (v: number | null) => {
        if (v === null) return <Text type="secondary">-</Text>;
        const color = v > 0 ? "#8c8c8c" : "#52c41a";
        return <Text style={{ color, fontWeight: 500 }}>{formatPercent(v)}</Text>;
      }
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
    },
    {
      title: "",
      key: "action",
      width: 120,
      fixed: "right" as const,
      render: (_: unknown, record: WatchlistRow) => (
        <Button
          size="small"
          type={expandedRows.includes(record.symbol) ? "default" : "text"}
          onClick={(event) => {
            event.stopPropagation();
            setExpandedRows((prev) =>
              prev.includes(record.symbol)
                ? prev.filter((symbol) => symbol !== record.symbol)
                : [...prev, record.symbol],
            );
          }}
        >
          10D Context
        </Button>
      ),
    }
  ];

  const handleRefreshIndicators = async () => {
    const key = "watchlist-refresh";
    messageApi.open({
      key,
      type: "loading",
      content: "Refreshing stocks...",
      duration: 0,
    });
    try {
      const completionMessage = await refreshIndicators();
      messageApi.open({
        key,
        type: "success",
        content: completionMessage,
        duration: 3,
      });
    } catch (err) {
      const content = err instanceof Error ? err.message : "Refresh failed";
      messageApi.open({
        key,
        type: "error",
        content,
        duration: 4,
      });
    }
  };

  return (
    <div style={{ padding: "16px 24px", height: "100%", display: "flex", flexDirection: "column" }}>
      {contextHolder}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
        <div>
          <Text strong style={{ fontSize: 18, letterSpacing: 0.5 }}>WATCHLIST {tag ? `: ${tag.toUpperCase()}` : ""}</Text>
        </div>
        <Space>
          <Tooltip title="Refresh all stock indicators and wait for completion">
            <Button type="text" icon={<SyncOutlined />} onClick={handleRefreshIndicators} loading={refreshing}>
              Refresh Engine
            </Button>
          </Tooltip>
          {onAddClick && (
            <Button type="primary" onClick={onAddClick}>
              + Add Stock
            </Button>
          )}
        </Space>
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
          onClick: () => {
            if (onRowClick) {
              onRowClick(record.symbol);
            } else {
              setDetailSymbol(record.symbol);
            }
          },
          style: { cursor: "pointer" },
        })}
        expandable={{
          expandedRowKeys: expandedRows,
          showExpandColumn: false,
          expandedRowRender: (record) => (
            <TradeMarketHistoryPanel
              symbol={record.symbol}
              days={10}
              defaultExpanded
              showToggle={false}
              title="10D Context"
            />
          ),
        }}
      />

      <StockDetailDrawer
        symbol={detailSymbol}
        onClose={() => setDetailSymbol(null)}
      />
    </div>
  );
}
