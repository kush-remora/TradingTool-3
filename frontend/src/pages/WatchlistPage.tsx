import {
  CheckOutlined,
  CloseOutlined,
  FileTextOutlined,
  MinusOutlined,
  PlusOutlined,
  StarFilled,
} from "@ant-design/icons";
import { Button, Collapse, Input, Spin, Tooltip, Typography, message } from "antd";
import { useEffect, useMemo, useState } from "react";
import { InstrumentSearch } from "../components/InstrumentSearch";
import { StockNotesPanel } from "../components/StockNotesPanel";
import { TelegramChatWidget } from "../components/TelegramChatWidget";
import { useLayout } from "../hooks/useLayout";
import { useWatchlistData } from "../hooks/useWatchlistData";
import type { LayoutData, Stock, Watchlist } from "../types";

const PRIORITY_COLORS = ["", "#bfbfbf", "#52c41a", "#1677ff", "#faad14", "#f5222d"];

export function WatchlistPage() {
  const data = useWatchlistData();
  const { layout, saveLayout } = useLayout();
  const [selectedStock, setSelectedStock] = useState<Stock | null>(null);
  const [notesOpen, setNotesOpen] = useState(false);
  const [editingWatchlistId, setEditingWatchlistId] = useState<number | null>(null);
  const [editName, setEditName] = useState("");
  const [createWlOpen, setCreateWlOpen] = useState(false);
  const [createWlName, setCreateWlName] = useState("");
  const [createWlDesc, setCreateWlDesc] = useState("");
  const [messageApi, contextHolder] = message.useMessage();

  // Build stockId → Tags map
  const stockTagMap = useMemo(() => {
    const tagById = new Map(data.allTags.map((t) => [t.id, t]));
    const map = new Map<number, typeof data.allTags>();
    data.stockTags.forEach((st) => {
      const tag = tagById.get(st.tag_id);
      if (!tag) return;
      const existing = map.get(st.stock_id) ?? [];
      map.set(st.stock_id, [...existing, tag]);
    });
    return map;
  }, [data.stockTags, data.allTags]);

  // Build stockId Map for quick lookup
  const stockById = useMemo(
    () => new Map(data.stocks.map((s) => [s.id, s])),
    [data.stocks],
  );

  // Order watchlists according to saved layout
  const orderedWatchlists = useMemo(() => {
    const orderMap = new Map(layout.watchlistOrder.map((id, i) => [id, i]));
    return [...data.watchlists].sort((a, b) => {
      const ai = orderMap.get(a.id) ?? 9999;
      const bi = orderMap.get(b.id) ?? 9999;
      return ai - bi;
    });
  }, [data.watchlists, layout.watchlistOrder]);

  // Close notes when stock is deselected
  useEffect(() => {
    if (!selectedStock) setNotesOpen(false);
  }, [selectedStock]);

  const stocksForWatchlist = (watchlist: Watchlist): Stock[] => {
    const stockOrder = layout.stockOrder[String(watchlist.id)] ?? [];
    const orderMap = new Map(stockOrder.map((id, i) => [id, i]));
    const ids = new Set(
      data.watchlistStocks
        .filter((ws) => ws.watchlist_id === watchlist.id)
        .map((ws) => ws.stock_id),
    );
    const stocks = [...ids]
      .map((id) => stockById.get(id))
      .filter((s): s is Stock => s !== undefined);
    return stocks.sort((a, b) => {
      const ai = orderMap.get(a.id) ?? 9999;
      const bi = orderMap.get(b.id) ?? 9999;
      return ai - bi;
    });
  };

  const existingStockIdsForWatchlist = (watchlistId: number): Set<number> =>
    new Set(
      data.watchlistStocks
        .filter((ws) => ws.watchlist_id === watchlistId)
        .map((ws) => ws.stock_id),
    );

  const existingTokensForWatchlist = (watchlistId: number): Set<number> => {
    const stockIds = existingStockIdsForWatchlist(watchlistId);
    return new Set(
      data.stocks
        .filter((s) => stockIds.has(s.id))
        .map((s) => s.instrument_token),
    );
  };

  const handleCreateWatchlist = async () => {
    if (!createWlName.trim()) return;
    try {
      const wl = await data.createWatchlist(createWlName.trim(), createWlDesc.trim() || undefined);
      saveLayout({ ...layout, watchlistOrder: [...layout.watchlistOrder, wl.id] });
      setCreateWlOpen(false);
      setCreateWlName("");
      setCreateWlDesc("");
    } catch (e) {
      messageApi.error(e instanceof Error ? e.message : "Failed to create watchlist");
    }
  };

  const handleStockAdded = (watchlistId: number, stock: Stock) => {
    const currentOrder = layout.stockOrder[String(watchlistId)] ?? [];
    const next: LayoutData = {
      ...layout,
      stockOrder: { ...layout.stockOrder, [String(watchlistId)]: [...currentOrder, stock.id] },
    };
    saveLayout(next);
  };

  const handleUpdateDescription = async (description: string) => {
    if (!selectedStock) return;
    await data.updateStock(selectedStock.id, { description });
    setSelectedStock((prev) => (prev ? { ...prev, description } : prev));
  };

  const handleUpdatePriority = async (priority: number) => {
    if (!selectedStock) return;
    await data.updateStock(selectedStock.id, { priority });
    setSelectedStock((prev) => (prev ? { ...prev, priority } : prev));
  };

  const handleRenameWatchlist = async (watchlistId: number) => {
    if (!editName.trim()) {
      setEditingWatchlistId(null);
      return;
    }
    try {
      await data.updateWatchlist(watchlistId, { name: editName.trim() });
      setEditingWatchlistId(null);
      setEditName("");
    } catch (e) {
      messageApi.error(e instanceof Error ? e.message : "Failed to rename watchlist");
    }
  };

  if (data.loading) {
    return (
      <div style={{ display: "flex", justifyContent: "center", alignItems: "center", height: "calc(100vh - 48px)" }}>
        <Spin size="large" />
      </div>
    );
  }

  const collapseItems = orderedWatchlists.map((watchlist) => {
    const stocks = stocksForWatchlist(watchlist);
    const existingIds = existingStockIdsForWatchlist(watchlist.id);
    const existingTokens = existingTokensForWatchlist(watchlist.id);

    return {
      key: String(watchlist.id),
      label: (
        <div style={{ display: "flex", alignItems: "center", gap: 8, flex: 1 }}>
          {editingWatchlistId === watchlist.id ? (
            <Input
              size="small"
              value={editName}
              onChange={(e) => setEditName(e.target.value)}
              onPressEnter={() => void handleRenameWatchlist(watchlist.id)}
              onBlur={() => void handleRenameWatchlist(watchlist.id)}
              autoFocus
              onClick={(e) => e.stopPropagation()}
              style={{ width: 150 }}
              suffix={
                <span style={{ display: "flex", gap: 4 }}>
                  <CheckOutlined style={{ color: "#52c41a", cursor: "pointer", fontSize: 12 }} />
                  <CloseOutlined
                    style={{ color: "#ff4d4f", cursor: "pointer", fontSize: 12 }}
                    onClick={() => setEditingWatchlistId(null)}
                  />
                </span>
              }
            />
          ) : (
            <>
              <Typography.Text
                strong
                style={{ fontSize: 13, cursor: "pointer", flex: 1 }}
                onClick={(e) => {
                  e.stopPropagation();
                  setEditingWatchlistId(watchlist.id);
                  setEditName(watchlist.name);
                }}
              >
                {watchlist.name}
              </Typography.Text>
              <Typography.Text type="secondary" style={{ fontSize: 11 }}>
                ({stocks.length})
              </Typography.Text>
            </>
          )}
        </div>
      ),
      children: (
        <div>
          {/* Search bar to add stocks to this watchlist */}
          <div style={{ marginBottom: 12 }}>
            <InstrumentSearch
              watchlistId={watchlist.id}
              existingStockIds={existingIds}
              existingStockTokens={existingTokens}
              onStockAdded={(stock) => handleStockAdded(watchlist.id, stock)}
            />
          </div>

          {/* Stock rows */}
          {stocks.length === 0 ? (
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              Search above to add stocks
            </Typography.Text>
          ) : (
            stocks.map((stock) => (
              <StockRow
                key={stock.id}
                stock={stock}
                tags={stockTagMap.get(stock.id) ?? []}
                isSelected={selectedStock?.id === stock.id}
                onSelect={() => {
                  const next = selectedStock?.id === stock.id ? null : stock;
                  setSelectedStock(next);
                  if (next) setNotesOpen(true);
                }}
                onRemove={() => void data.removeStockFromWatchlist(watchlist.id, stock.id)}
              />
            ))
          )}
        </div>
      ),
    };
  });


  return (
    <>
      {contextHolder}

      {/* ─── Main layout: sidebar + content ─── */}
      <div style={{ display: "flex", height: "calc(100vh - 48px)" }}>

        {/* ── Left sidebar ────────────────────── */}
        <div
          style={{
            width: 280,
            flexShrink: 0,
            background: "#fff",
            borderRight: "1px solid #e8e8e8",
            display: "flex",
            flexDirection: "column",
            overflowY: "auto",
          }}
        >
          {/* Header + create button */}
          <div style={{ padding: "12px", borderBottom: "1px solid #f0f0f0" }}>
            <Button
              block
              type="primary"
              size="small"
              icon={<PlusOutlined />}
              onClick={() => setCreateWlOpen(true)}
            >
              New Watchlist
            </Button>
          </div>

          {/* Error message */}
          {data.error && (
            <div style={{ padding: "8px 12px" }}>
              <Typography.Text type="danger" style={{ fontSize: 11 }}>
                {data.error}
              </Typography.Text>
            </div>
          )}

          {/* Collapse: all watchlists */}
          <Collapse
            items={collapseItems}
            defaultActiveKey={orderedWatchlists.map((w) => String(w.id))}
            style={{ background: "transparent", border: "none", flex: 1 }}
          />
        </div>

        {/* ── Right content area ──────────────── */}
        <div style={{ flex: 1, background: "#f5f7fa" }} />
      </div>

      {/* ─── Notes FAB ─────────────────────── */}
      <div style={{ position: "fixed", bottom: 24, right: 84, zIndex: 1001 }}>
        <Tooltip
          title={selectedStock ? `Notes: ${selectedStock.symbol}` : "Select a stock to open notes"}
          placement="top"
        >
          <Button
            type={notesOpen ? "primary" : "default"}
            shape="circle"
            size="large"
            disabled={!selectedStock}
            icon={<FileTextOutlined />}
            onClick={() => setNotesOpen((v) => !v)}
            style={{ width: 48, height: 48, boxShadow: "0 4px 12px rgba(0,0,0,0.15)" }}
          />
        </Tooltip>
      </div>

      {/* ─── Notes panel ───────────────────── */}
      {notesOpen && selectedStock && (
        <StockNotesPanel
          stock={selectedStock}
          tags={stockTagMap.get(selectedStock.id) ?? []}
          onClose={() => setNotesOpen(false)}
          onUpdateDescription={handleUpdateDescription}
          onUpdatePriority={handleUpdatePriority}
        />
      )}

      {/* ─── Telegram FAB ──────────────────── */}
      <TelegramChatWidget />

      {/* ─── Create watchlist modal ────────── */}
      {/* (same as before) */}
    </>
  );
}

// ─── Stock row component ────────────────────────────────────────────────────

interface StockRowProps {
  stock: Stock;
  tags: ReturnType<typeof useWatchlistData>["allTags"];
  isSelected: boolean;
  onSelect: () => void;
  onRemove: () => void;
}

function StockRow({ stock, tags, isSelected, onSelect, onRemove }: StockRowProps) {
  const [hovered, setHovered] = useState(false);

  return (
    <div
      onClick={onSelect}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      style={{
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        padding: "7px 14px 7px 11px",
        cursor: "pointer",
        background: isSelected ? "#e6f4ff" : hovered ? "#f5f5f5" : "transparent",
        borderLeft: isSelected ? "3px solid #1677ff" : "3px solid transparent",
        transition: "background 0.1s",
      }}
    >
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
          <Typography.Text
            strong
            style={{ fontSize: 13, color: isSelected ? "#1677ff" : "#1f1f1f" }}
          >
            {stock.symbol}
          </Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 10 }}>
            {stock.exchange}
          </Typography.Text>
        </div>
        {/* Company name visible on hover */}
        {hovered && (
          <Typography.Text
            type="secondary"
            ellipsis
            style={{ fontSize: 10, display: "block", maxWidth: 180 }}
          >
            {stock.company_name}
          </Typography.Text>
        )}
      </div>

      {/* Right side: priority star (hidden on hover) + remove button */}
      <div style={{ display: "flex", alignItems: "center", gap: 4, flexShrink: 0 }}>
        {stock.priority != null && stock.priority > 0 && !hovered && (
          <StarFilled style={{ fontSize: 8, color: PRIORITY_COLORS[stock.priority] ?? "#bfbfbf" }} />
        )}
        {hovered && (
          <Tooltip title="Remove from watchlist">
            <Button
              type="text"
              size="small"
              icon={<MinusOutlined style={{ fontSize: 10, color: "#ff4d4f" }} />}
              onClick={(e) => { e.stopPropagation(); onRemove(); }}
              style={{ padding: "0 3px", height: 18 }}
            />
          </Tooltip>
        )}
      </div>
    </div>
  );
}
