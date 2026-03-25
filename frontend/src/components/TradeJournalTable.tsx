import { DeleteOutlined } from "@ant-design/icons";
import { Button, Modal, Space, Table, Tag, Tooltip, message } from "antd";
import type { ColumnType } from "antd/es/table";
import { useState } from "react";
import type { TradeWithTargets } from "../types";

interface TradeJournalTableProps {
  trades: TradeWithTargets[];
  onDelete: (tradeId: number) => Promise<void>;
  loading?: boolean;
}

export function TradeJournalTable({
  trades,
  onDelete,
  loading = false,
}: TradeJournalTableProps) {
  const [expandedNotes, setExpandedNotes] = useState<Set<number>>(new Set());

  const handleDelete = async (tradeId: number) => {
    Modal.confirm({
      title: "Delete Trade?",
      content: "This action cannot be undone.",
      okText: "Delete",
      okType: "danger",
      onOk: async () => {
        try {
          await onDelete(tradeId);
          message.success("Trade deleted");
        } catch (e) {
          message.error(e instanceof Error ? e.message : "Failed to delete trade");
        }
      },
    });
  };

  const columns: ColumnType<TradeWithTargets>[] = [
    {
      title: "Date",
      dataIndex: ["trade", "trade_date"],
      key: "trade_date",
      width: 90,
      sorter: (a, b) => a.trade.trade_date.localeCompare(b.trade.trade_date),
    },
    {
      title: "Symbol",
      dataIndex: ["trade", "nse_symbol"],
      key: "nse_symbol",
      width: 140,
      render: (symbol, record) => (
        <span style={{ whiteSpace: "nowrap" }}>
          <strong>{symbol}</strong> <span style={{ color: "#999", fontSize: "11px" }}>• ₹{record.total_invested}</span>
        </span>
      ),
    },
    {
      title: "Qty",
      dataIndex: ["trade", "quantity"],
      key: "quantity",
      width: 60,
      sorter: (a, b) => a.trade.quantity - b.trade.quantity,
    },
    {
      title: "Avg Price",
      dataIndex: ["trade", "avg_buy_price"],
      key: "avg_buy_price",
      width: 80,
      render: (price) => `₹${price}`,
    },
    {
      title: "Today's Low",
      dataIndex: ["trade", "today_low"],
      key: "today_low",
      width: 80,
      render: (low) => (low ? `₹${low}` : "—"),
    },
    {
      title: "Stop Loss",
      dataIndex: ["trade", "stop_loss_price"],
      key: "stop_loss_price",
      width: 110,
      render: (price, record) => (
        <span className="danger-text" style={{ whiteSpace: "nowrap" }}>
          ₹{price} (-{record.trade.stop_loss_percent}%)
        </span>
      ),
    },
    {
      title: "GTT Targets",
      dataIndex: "gtt_targets",
      key: "gtt_targets",
      width: 250,
      render: (targets: any[]) => (
        <Space wrap size={4}>
          {targets.map((target: any) => (
            <Tag
              key={target.percent}
              bordered={false}
              color="success"
              style={{ margin: 0, fontSize: "11px", padding: "0 4px" }}
            >
              +{target.percent}% ₹{target.price}
            </Tag>
          ))}
        </Space>
      ),
    },
    {
      title: "Notes",
      dataIndex: ["trade", "notes"],
      key: "notes",
      width: 150,
      render: (notes, record) => {
        if (!notes) return "—";
        const isExpanded = expandedNotes.has(record.trade.id);
        const displayText = isExpanded ? notes : notes.substring(0, 40);
        return (
          <Tooltip title={notes}>
            <span
              onClick={() => {
                const newExpanded = new Set(expandedNotes);
                if (isExpanded) {
                  newExpanded.delete(record.trade.id);
                } else {
                  newExpanded.add(record.trade.id);
                }
                setExpandedNotes(newExpanded);
              }}
              style={{ cursor: "pointer", color: "#1677ff" }}
            >
              {displayText}
              {notes.length > 40 && "..."}
            </span>
          </Tooltip>
        );
      },
    },
    {
      title: "Action",
      key: "action",
      width: 60,
      render: (_, record) => (
        <Button
          type="text"
          danger
          size="small"
          icon={<DeleteOutlined />}
          onClick={() => handleDelete(record.trade.id)}
          loading={loading}
        />
      ),
    },
  ];

  return (
    <Table<TradeWithTargets>
      className="trade-book-table"
      columns={columns}
      dataSource={trades}
      rowKey={(record) => record.trade.id}
      size="small"
      style={{ background: "white", borderRadius: "4px" }}
      scroll={{ x: 1000 }}
      pagination={{ pageSize: 20, showSizeChanger: true }}
    />
  );
}
