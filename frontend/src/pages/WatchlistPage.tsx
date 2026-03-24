import { DeleteOutlined, EditOutlined } from "@ant-design/icons";
import { Button, Popconfirm, Space, Spin, Tooltip, Typography, message } from "antd";
import { useEffect, useState } from "react";
import { InstrumentSearch } from "../components/InstrumentSearch";
import { StockDetailPanel } from "../components/StockDetailPanel";
import { TelegramChatWidget } from "../components/TelegramChatWidget";
import { WatchlistDashboard } from "../components/WatchlistDashboard";
import { useStocks, type CreateStockInput } from "../hooks/useStocks";
import { useLivePrices } from "../hooks/useLivePrices";
import type { InstrumentSearchResult, Stock, TickSnapshot } from "../types";

const PRIORITY_COLORS = ["", "#bfbfbf", "#52c41a", "#1677ff", "#faad14", "#f5222d"];

export function WatchlistPage() {
  const { stocks, allTags, loading, error, createStock, updateStock, deleteStock } = useStocks();
  const { getTick } = useLivePrices();

  // Panel state
  const [selectedStock, setSelectedStock] = useState<Stock | null>(null);
  const [panelMode, setPanelMode] = useState<"create" | "view" | "edit" | null>(null);

  // Create mode: selected instrument
  const [selectedInstrument, setSelectedInstrument] = useState<InstrumentSearchResult | null>(null);

  const [messageApi, contextHolder] = message.useMessage();

  // Sync selectedStock with latest data
  useEffect(() => {
    if (!selectedStock) return;
    const updated = stocks.find((s) => s.id === selectedStock.id);
    if (updated) setSelectedStock(updated);
  }, [stocks]); // eslint-disable-line react-hooks/exhaustive-deps

  const handleCreate = async (payload: CreateStockInput) => {
    try {
      const stock = await createStock(payload);
      setSelectedStock(stock);
      setPanelMode("view");
      setSelectedInstrument(null);
    } catch (e) {
      messageApi.error(e instanceof Error ? e.message : "Failed to create stock");
    }
  };

  const handleUpdate = async (payload: any) => {
    if (!selectedStock) return;
    try {
      const updated = await updateStock(selectedStock.id, payload);
      setSelectedStock(updated);
      setPanelMode("view");
    } catch (e) {
      messageApi.error(e instanceof Error ? e.message : "Failed to update stock");
    }
  };

  const handleDelete = async (stockId: number) => {
    try {
      await deleteStock(stockId);
      if (selectedStock?.id === stockId) {
        setSelectedStock(null);
        setPanelMode(null);
      }
    } catch (e) {
      messageApi.error(e instanceof Error ? e.message : "Failed to delete stock");
    }
  };

  if (loading) {
    return (
      <div style={{ display: "flex", justifyContent: "center", alignItems: "center", height: "calc(100vh - 48px)" }}>
        <Spin size="large" />
      </div>
    );
  }

  return (
    <>
      {contextHolder}

      <div style={{ display: "flex", flexDirection: "column", height: "calc(100vh - 48px)" }}>
        {/* Main content area: sidebar + empty space */}
        <div style={{ display: "flex", flex: 1, overflow: "hidden" }}>
          {/* Left sidebar (300px) */}
          <div
            style={{
              width: 300,
              flexShrink: 0,
              background: "#fff",
              borderRight: "1px solid #e8e8e8",
              display: "flex",
              flexDirection: "column",
              padding: "12px",
              overflowY: "auto",
            }}
          >
            {/* Search box + Add button */}
            <div style={{ marginBottom: 12 }}>
              <Space.Compact style={{ width: "100%" }}>
                <InstrumentSearch
                  existingStockTokens={new Set(stocks.map((s) => s.instrument_token))}
                  onSelect={setSelectedInstrument}
                  value={selectedInstrument}
                />
                <Tooltip title="Add selected stock">
                  <Button
                    type="primary"
                    size="small"
                    disabled={!selectedInstrument}
                    onClick={() => {
                      setPanelMode("create");
                    }}
                  >
                    + Add
                  </Button>
                </Tooltip>
              </Space.Compact>
            </div>

            {/* Error state */}
            {error && (
              <Typography.Text type="danger" style={{ fontSize: 11, marginBottom: 8 }}>
                {error}
              </Typography.Text>
            )}

            {/* Stock list */}
            {stocks.length === 0 ? (
              <Typography.Text type="secondary" style={{ fontSize: 11 }}>
                No stocks yet — search above to add one.
              </Typography.Text>
            ) : (
              <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                {stocks.map((stock) => (
                  <StockSidebarRow
                    key={stock.id}
                    stock={stock}
                    tick={getTick(stock.instrument_token)}
                    isSelected={selectedStock?.id === stock.id}
                    onSelect={() => {
                      setSelectedStock(stock);
                      setPanelMode("view");
                    }}
                    onEdit={() => {
                      setSelectedStock(stock);
                      setPanelMode("edit");
                    }}
                    onDelete={() => void handleDelete(stock.id)}
                  />
                ))}
              </div>
            )}
          </div>

          {/* Main area (empty) */}
          <div
            style={{
              flex: 1,
              background: "#141414", // dark mode background for the dashboard
              overflow: "hidden",
            }}
          >
            <WatchlistDashboard />
          </div>
        </div>

        {/* Bottom panel */}
        {panelMode && (
          <StockDetailPanel
            mode={panelMode}
            stock={selectedStock}
            allTags={allTags}
            existingStockTokens={new Set(stocks.map((s) => s.instrument_token))}
            onCreate={async (payload) => {
              await handleCreate(payload);
            }}
            onUpdate={async (payload) => {
              await handleUpdate(payload);
            }}
            onClose={() => {
              setPanelMode(null);
              setSelectedStock(null);
              setSelectedInstrument(null);
            }}
            onEnterEdit={() => {
              setPanelMode("edit");
            }}
          />
        )}

        {/* Telegram widget */}
        <TelegramChatWidget />
      </div>
    </>
  );
}

// ─── Stock Sidebar Row ───────────────────────────────────────────

function StockSidebarRow({
  stock,
  tick,
  isSelected,
  onSelect,
  onEdit,
  onDelete,
}: {
  stock: Stock;
  tick: TickSnapshot | undefined;
  isSelected: boolean;
  onSelect: () => void;
  onEdit: () => void;
  onDelete: () => void;
}) {
  const [hovered, setHovered] = useState(false);

  const ltpColor = tick
    ? tick.changePercent >= 0
      ? "#52c41a"
      : "#f5222d"
    : "#bfbfbf";

  const ltpFormatted = tick
    ? `₹${tick.ltp.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
    : null;

  const changeFormatted = tick
    ? `${tick.changePercent >= 0 ? "▲" : "▼"}${Math.abs(tick.changePercent).toFixed(2)}%`
    : null;

  return (
    <div
      onClick={onSelect}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      style={{
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        padding: "8px 10px",
        cursor: "pointer",
        background: isSelected ? "#e6f4ff" : "#fff",
        borderRadius: 6,
        border: isSelected ? "1px solid #91caff" : "1px solid #f0f0f0",
        transition: "background 0.1s",
      }}
    >
      <div style={{ flex: 1, minWidth: 0 }}>
        <Typography.Text strong style={{ fontSize: 13, color: isSelected ? "#1677ff" : "#1f1f1f" }}>
          {stock.symbol}
        </Typography.Text>
        <Typography.Text type="secondary" style={{ fontSize: 10, marginLeft: 6 }}>
          {stock.exchange}
        </Typography.Text>
      </div>

      <div style={{ display: "flex", alignItems: "center", gap: 4, flexShrink: 0 }}>
        {!hovered && (
          <>
            {ltpFormatted && (
              <div style={{ textAlign: "right" }}>
                <Typography.Text style={{ fontSize: 11, color: ltpColor, fontVariantNumeric: "tabular-nums" }}>
                  {ltpFormatted}
                </Typography.Text>
                <Typography.Text style={{ fontSize: 9, color: ltpColor, marginLeft: 3 }}>
                  {changeFormatted}
                </Typography.Text>
              </div>
            )}
            {!ltpFormatted && stock.priority && stock.priority > 0 && (
              <Typography.Text style={{ fontSize: 10, color: PRIORITY_COLORS[stock.priority] ?? "#bfbfbf" }}>
                {"★".repeat(stock.priority)}
              </Typography.Text>
            )}
          </>
        )}

        {hovered && (
          <Space size={4}>
            <Tooltip title="Edit">
              <Button
                type="text"
                size="small"
                icon={<EditOutlined style={{ fontSize: 12, color: "#1677ff" }} />}
                onClick={(e) => {
                  e.stopPropagation();
                  onEdit();
                }}
                style={{ padding: "0 3px", height: 20 }}
              />
            </Tooltip>
            <Popconfirm
              title="Delete stock?"
              description="This stock will be removed from your watchlist."
              okText="Delete"
              okButtonProps={{ danger: true }}
              onConfirm={(e) => {
                e?.stopPropagation();
                onDelete();
              }}
            >
              <Tooltip title="Delete">
                <Button
                  type="text"
                  size="small"
                  danger
                  icon={<DeleteOutlined style={{ fontSize: 12 }} />}
                  onClick={(e) => e.stopPropagation()}
                  style={{ padding: "0 3px", height: 20 }}
                />
              </Tooltip>
            </Popconfirm>
          </Space>
        )}
      </div>
    </div>
  );
}
