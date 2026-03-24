import { CloseOutlined } from "@ant-design/icons";
import { Button, Input, Rate, Spin, Table, Tag, Typography } from "antd";
import { useEffect, useState } from "react";
import { getJson } from "../utils/api";
import type { UpdateStockInput } from "../hooks/useStocks";
import type { Stock, Trade } from "../types";

interface Props {
  stock: Stock;
  onClose: () => void;
  onUpdate: (payload: UpdateStockInput) => Promise<void>;
}

export function StockNotesPanel({ stock, onClose, onUpdate }: Props) {
  const [editingNotes, setEditingNotes] = useState(false);
  const [notesDraft, setNotesDraft] = useState(stock.notes ?? "");
  const [savingNotes, setSavingNotes] = useState(false);

  const [trades, setTrades] = useState<Trade[]>([]);
  const [loadingTrades, setLoadingTrades] = useState(false);

  // Sync draft when stock changes (e.g. parent re-fetches)
  useEffect(() => {
    setNotesDraft(stock.notes ?? "");
  }, [stock.notes]);

  // Load trades for this stock
  useEffect(() => {
    setLoadingTrades(true);
    getJson<Trade[]>(`/api/stocks/${stock.id}/trades`)
      .then(setTrades)
      .catch(() => setTrades([]))
      .finally(() => setLoadingTrades(false));
  }, [stock.id]);

  const handleSaveNotes = async () => {
    setSavingNotes(true);
    try {
      await onUpdate({ notes: notesDraft });
      setEditingNotes(false);
    } finally {
      setSavingNotes(false);
    }
  };

  const tradeColumns = [
    { title: "Qty", dataIndex: "quantity", key: "qty", width: 50 },
    { title: "Avg Price", dataIndex: "avg_buy_price", key: "avg", width: 80 },
    { title: "SL %", dataIndex: "stop_loss_percent", key: "sl", width: 60, render: (v: string) => `${v}%` },
    { title: "Date", dataIndex: "trade_date", key: "date", width: 90 },
  ];

  return (
    <div
      style={{
        position: "fixed",
        bottom: 80,
        right: 24,
        width: 380,
        maxHeight: 560,
        background: "#fff",
        border: "1px solid #e8e8e8",
        borderRadius: 12,
        boxShadow: "0 8px 32px rgba(0,0,0,0.12)",
        display: "flex",
        flexDirection: "column",
        zIndex: 1000,
        overflowY: "auto",
      }}
    >
      {/* Header */}
      <div
        style={{
          padding: "12px 16px",
          borderBottom: "1px solid #f0f0f0",
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          background: "#fafafa",
          borderRadius: "12px 12px 0 0",
          position: "sticky",
          top: 0,
          zIndex: 1,
        }}
      >
        <div>
          <Typography.Text strong style={{ fontSize: 14 }}>
            {stock.symbol}
          </Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 11, marginLeft: 6 }}>
            {stock.exchange}
          </Typography.Text>
          <div>
            <Typography.Text type="secondary" style={{ fontSize: 11 }}>
              {stock.company_name}
            </Typography.Text>
          </div>
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <Rate
            count={5}
            value={stock.priority ?? 0}
            onChange={(val) => void onUpdate({ priority: val })}
            style={{ fontSize: 12 }}
          />
          <Button type="text" size="small" icon={<CloseOutlined />} onClick={onClose} />
        </div>
      </div>

      {/* Tags — colored chips from stock.tags */}
      {stock.tags.length > 0 && (
        <div style={{ padding: "8px 16px", borderBottom: "1px solid #f0f0f0", display: "flex", flexWrap: "wrap", gap: 4 }}>
          {stock.tags.map((tag) => (
            <Tag key={tag.name} color={tag.color} style={{ margin: 0, fontSize: 11 }}>
              {tag.name}
            </Tag>
          ))}
        </div>
      )}

      {/* MOAT / THESIS — single editable text field */}
      <div style={{ padding: "10px 16px", borderBottom: "1px solid #f0f0f0" }}>
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 4 }}>
          <Typography.Text style={{ fontSize: 11, color: "#888", fontWeight: 600 }}>MOAT / THESIS</Typography.Text>
          {!editingNotes && (
            <Button type="link" size="small" style={{ fontSize: 11, padding: 0, height: "auto" }} onClick={() => setEditingNotes(true)}>
              Edit
            </Button>
          )}
        </div>
        {editingNotes ? (
          <div>
            <Input.TextArea
              autoFocus
              rows={4}
              value={notesDraft}
              onChange={(e) => setNotesDraft(e.target.value)}
              style={{ fontSize: 12, resize: "none" }}
              placeholder="Write your moat analysis, thesis, research here..."
            />
            <div style={{ display: "flex", gap: 6, marginTop: 6 }}>
              <Button size="small" type="primary" loading={savingNotes} onClick={() => void handleSaveNotes()}>
                Save
              </Button>
              <Button
                size="small"
                onClick={() => {
                  setEditingNotes(false);
                  setNotesDraft(stock.notes ?? "");
                }}
              >
                Cancel
              </Button>
            </div>
          </div>
        ) : (
          <Typography.Text style={{ fontSize: 12, color: stock.notes ? "#333" : "#bbb", whiteSpace: "pre-wrap" }}>
            {stock.notes || "No thesis yet — click Edit to add one."}
          </Typography.Text>
        )}
      </div>

      {/* Trades */}
      <div style={{ padding: "10px 16px" }}>
        <Typography.Text style={{ fontSize: 11, color: "#888", fontWeight: 600, display: "block", marginBottom: 6 }}>
          TRADE POSITION
        </Typography.Text>
        {loadingTrades ? (
          <Spin size="small" />
        ) : trades.length === 0 ? (
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            No open position.
          </Typography.Text>
        ) : (
          <Table
            dataSource={trades}
            columns={tradeColumns}
            rowKey="id"
            size="small"
            pagination={false}
            style={{ fontSize: 12 }}
          />
        )}
      </div>
    </div>
  );
}
