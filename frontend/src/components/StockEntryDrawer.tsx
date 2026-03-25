import { Drawer, Button, Input, Rate, Select, Space, Spin, Table, Tag, Typography, message, Form } from "antd";
import { useEffect, useState } from "react";
import { InstrumentSearch } from "./InstrumentSearch";
import { getJson } from "../utils/api";
import type { CreateStockInput, UpdateStockInput } from "../hooks/useStocks";
import type { InstrumentSearchResult, Stock, StockTag, Trade } from "../types";

interface Props {
  open: boolean;
  mode: "create" | "edit";
  stock?: Stock | null;
  allTags: StockTag[];
  existingStockTokens: Set<number>;
  onCreate: (payload: CreateStockInput) => Promise<void>;
  onUpdate: (payload: UpdateStockInput) => Promise<void>;
  onClose: () => void;
}

const TAG_COLORS = ["blue", "green", "gold", "red", "purple", "cyan"];

export function StockEntryDrawer({
  open,
  mode,
  stock,
  allTags,
  existingStockTokens,
  onCreate,
  onUpdate,
  onClose,
}: Props) {
  const [selectedInstrument, setSelectedInstrument] = useState<InstrumentSearchResult | null>(null);
  const [priority, setPriority] = useState(stock?.priority ?? 0);
  const [notesDraft, setNotesDraft] = useState(stock?.notes ?? "");
  const [tagsDraft, setTagsDraft] = useState<StockTag[]>(stock?.tags ?? []);
  
  const [newTagName, setNewTagName] = useState("");
  const [newTagColor, setNewTagColor] = useState<string>("blue");

  const [trades, setTrades] = useState<Trade[]>([]);
  const [loadingTrades, setLoadingTrades] = useState(false);
  const [saving, setSaving] = useState(false);
  const [messageApi, contextHolder] = message.useMessage();

  useEffect(() => {
    if (open) {
      if (mode === "edit" && stock) {
        setPriority(stock.priority ?? 0);
        setNotesDraft(stock.notes ?? "");
        setTagsDraft(stock.tags ?? []);
        setLoadingTrades(true);
        getJson<Trade[]>(`/api/stocks/${stock.id}/trades`)
          .then(setTrades)
          .catch(() => setTrades([]))
          .finally(() => setLoadingTrades(false));
      } else if (mode === "create") {
        setSelectedInstrument(null);
        setPriority(0);
        setNotesDraft("");
        setTagsDraft([]);
        setTrades([]);
      }
    }
  }, [stock, mode, open]);

  const handleAddTag = () => {
    if (!newTagName.trim()) return;
    const newTag: StockTag = { name: newTagName.trim(), color: newTagColor };
    if (!tagsDraft.some((t) => t.name === newTag.name)) {
      setTagsDraft([...tagsDraft, newTag]);
      setNewTagName("");
      setNewTagColor("blue");
    }
  };

  const handleRemoveTag = (tagName: string) => {
    setTagsDraft(tagsDraft.filter((t) => t.name !== tagName));
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      if (mode === "create") {
        if (!selectedInstrument) throw new Error("Please select an instrument");
        await onCreate({
          symbol: selectedInstrument.trading_symbol,
          instrument_token: selectedInstrument.instrument_token,
          company_name: selectedInstrument.company_name,
          exchange: selectedInstrument.exchange,
          priority: priority > 0 ? priority : undefined,
          notes: notesDraft.trim().length > 0 ? notesDraft : undefined,
          tags: tagsDraft.length > 0 ? tagsDraft : undefined,
        });
        messageApi.success("Stock added to watchlist");
      } else if (mode === "edit" && stock) {
        await onUpdate({
          priority: priority > 0 ? priority : undefined,
          notes: notesDraft.trim().length > 0 ? notesDraft : undefined,
          tags: tagsDraft.length > 0 ? tagsDraft : undefined,
        });
        messageApi.success("Stock updated");
      }
      onClose();
    } catch (e) {
      messageApi.error(e instanceof Error ? e.message : "Failed to save stock");
    } finally {
      setSaving(false);
    }
  };

  const tradeColumns = [
    { title: "Qty", dataIndex: "quantity", key: "qty", width: 50 },
    { title: "Avg Price", dataIndex: "avg_buy_price", key: "avg", width: 80 },
    { title: "SL %", dataIndex: "stop_loss_percent", key: "sl", width: 60, render: (v: string) => `${v}%` },
    { title: "Date", dataIndex: "trade_date", key: "date", width: 90 },
  ];

  return (
    <Drawer
      title={mode === "create" ? "Add to Watchlist" : `Edit ${stock?.symbol}`}
      placement="right"
      width={400}
      onClose={onClose}
      open={open}
      styles={{
        body: { paddingBottom: 80, display: "flex", flexDirection: "column", gap: 16 },
      }}
      extra={
        <Space>
          <Button onClick={onClose} size="small">Cancel</Button>
          <Button type="primary" onClick={handleSave} loading={saving} size="small" disabled={mode === "create" && !selectedInstrument}>
            {mode === "create" ? "Add Stock" : "Save Changes"}
          </Button>
        </Space>
      }
    >
      {contextHolder}
      <Form layout="vertical" size="small">
        {mode === "create" && (
          <Form.Item label="Search Instrument">
            <InstrumentSearch
              existingStockTokens={existingStockTokens}
              onSelect={setSelectedInstrument}
              value={selectedInstrument}
            />
          </Form.Item>
        )}

        {mode === "edit" && stock && (
          <div style={{ marginBottom: 16 }}>
            <Typography.Text strong style={{ fontSize: 16 }}>{stock.symbol}</Typography.Text>
            <br />
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>{stock.exchange} • {stock.company_name}</Typography.Text>
          </div>
        )}

        <Form.Item label="Priority">
          <Rate count={5} value={priority} onChange={setPriority} style={{ fontSize: 16 }} />
        </Form.Item>

        <Form.Item label="Notes">
          <Input.TextArea
            value={notesDraft}
            onChange={(e) => setNotesDraft(e.target.value)}
            rows={4}
            placeholder="Write thesis, moat analysis, etc..."
          />
        </Form.Item>

        <Form.Item label="Tags">
          <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
            {tagsDraft.length > 0 && (
              <div style={{ display: "flex", flexWrap: "wrap", gap: 4 }}>
                {tagsDraft.map((tag) => (
                  <Tag
                    key={tag.name}
                    color={tag.color}
                    closable
                    onClose={() => handleRemoveTag(tag.name)}
                    style={{ margin: 0, padding: "2px 8px", fontSize: 11 }}
                  >
                    {tag.name}
                  </Tag>
                ))}
              </div>
            )}
            <Space.Compact style={{ width: "100%" }}>
              <Input
                size="small"
                placeholder="New tag..."
                value={newTagName}
                onChange={(e) => setNewTagName(e.target.value)}
                onPressEnter={handleAddTag}
              />
              <Select
                value={newTagColor}
                onChange={setNewTagColor}
                options={TAG_COLORS.map((c) => ({ label: c, value: c }))}
                size="small"
                style={{ width: 80 }}
              />
              <Button type="primary" size="small" onClick={handleAddTag}>Add</Button>
            </Space.Compact>
          </div>
        </Form.Item>

        {mode === "edit" && stock && (
          <div style={{ marginTop: 16, borderTop: "1px solid #f0f0f0", paddingTop: 16 }}>
            <Typography.Text strong style={{ fontSize: 13, display: "block", marginBottom: 8 }}>Open Positions</Typography.Text>
            {loadingTrades ? (
              <Spin size="small" />
            ) : trades.length === 0 ? (
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>No active open positions.</Typography.Text>
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
        )}
      </Form>
    </Drawer>
  );
}
