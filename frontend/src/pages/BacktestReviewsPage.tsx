import { useEffect, useState } from "react";
import { Card, Table, Typography, message, Tag, Space, Button } from "antd";
import { ReloadOutlined } from "@ant-design/icons";
import { BacktestTradeReview, BacktestTradeReviewApiResponse } from "../types";

const { Text } = Typography;

const formatNumber = (num: number | null | undefined, decimals = 2) => {
  if (num === null || num === undefined) return "-";
  return num.toFixed(decimals);
};

export function BacktestReviewsPage() {
  const [loading, setLoading] = useState(false);
  const [reviews, setReviews] = useState<BacktestTradeReview[]>([]);

  const fetchReviews = async () => {
    setLoading(true);
    try {
      const res = await fetch("/api/strategy/csv-backtest/reviews");
      if (!res.ok) throw new Error("Failed to fetch reviews");
      const data: BacktestTradeReviewApiResponse = await res.json();
      setReviews(data.reviews);
    } catch (err: any) {
      message.error(err.message || "Error fetching reviews");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchReviews();
  }, []);

  const columns = [
    { title: "Symbol", dataIndex: "symbol", key: "symbol", render: (val: string) => <Text strong>{val}</Text>, sorter: (a: any, b: any) => a.symbol.localeCompare(b.symbol) },
    { title: "Status", dataIndex: "isPass", key: "isPass", render: (val: boolean | null) => {
      if (val === null) return "-";
      return val ? <Tag color="success">PASS</Tag> : <Tag color="error">REJECT</Tag>;
    }},
    { title: "Signal Date", dataIndex: "signalDate", key: "signalDate", sorter: (a: any, b: any) => a.signalDate.localeCompare(b.signalDate) },
    { title: "Entry", key: "entry", render: (_: any, record: any) => record.entryDate ? `${record.entryDate} @ ₹${formatNumber(record.entryPrice)}` : "-" },
    { title: "Exit", key: "exit", render: (_: any, record: any) => record.exitDate ? `${record.exitDate} @ ₹${formatNumber(record.exitPrice)}` : "-" },
    { 
      title: "P&L %", 
      dataIndex: "pnlPct", 
      key: "pnlPct", 
      sorter: (a: any, b: any) => (a.pnlPct || 0) - (b.pnlPct || 0),
      render: (val: number | null) => {
        if (val === null) return "-";
        return <Text type={val >= 0 ? "success" : "danger"}>{val > 0 ? "+" : ""}{val.toFixed(2)}%</Text>;
      }
    },
    { 
      title: "Reasons", 
      dataIndex: "reasonTags", 
      key: "reasonTags", 
      render: (val: string | null) => {
        if (!val) return "-";
        const tags = val.split(",");
        return (
          <Space size={[0, 4]} wrap>
            {tags.map((t, idx) => <Tag key={idx}>{t}</Tag>)}
          </Space>
        );
      }
    },
    { title: "Notes", dataIndex: "notes", key: "notes", render: (val: string | null) => val || "-" },
    { title: "Reviewed At", dataIndex: "updatedAt", key: "updatedAt", render: (val: string | null) => val ? new Date(val).toLocaleString() : "-" }
  ];

  return (
    <div style={{ padding: 24, maxWidth: 1400, margin: '0 auto' }}>
      <Card 
        title="Saved Trade Reviews"
        extra={<Button icon={<ReloadOutlined />} onClick={fetchReviews} loading={loading}>Refresh</Button>}
      >
        <Table 
          dataSource={reviews} 
          columns={columns} 
          rowKey="id" 
          loading={loading}
          pagination={{ pageSize: 50 }}
          size="small"
        />
      </Card>
    </div>
  );
}
