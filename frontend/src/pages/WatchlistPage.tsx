import { Spin, message } from "antd";
import { useEffect, useState } from "react";
import { StockEntryDrawer } from "../components/StockEntryDrawer";
import { TelegramChatWidget } from "../components/TelegramChatWidget";
import { WatchlistDashboard } from "../components/WatchlistDashboard";
import { useStocks, type CreateStockInput } from "../hooks/useStocks";
import type { Stock } from "../types";

export function WatchlistPage() {
  const { stocks, configTags, loading, error, createStock, updateStock } = useStocks();

  // Drawer state
  const [selectedStock, setSelectedStock] = useState<Stock | null>(null);
  const [drawerMode, setDrawerMode] = useState<"create" | "edit" | null>(null);

  const [messageApi, contextHolder] = message.useMessage();

  // Sync selectedStock with latest data
  useEffect(() => {
    if (!selectedStock) return;
    const updated = stocks.find((s) => s.id === selectedStock.id);
    if (updated) setSelectedStock(updated);
  }, [stocks]); // eslint-disable-line react-hooks/exhaustive-deps

  const handleCreate = async (payload: CreateStockInput) => {
    try {
      await createStock(payload);
    } catch (e) {
      messageApi.error(e instanceof Error ? e.message : "Failed to create stock");
      throw e; // re-throw so the Drawer handles the error layout/spinner
    }

    const key = "watchlist-auto-refresh";
    messageApi.open({
      key,
      type: "loading",
      content: "Refreshing all stocks...",
      duration: 0,
    });

    try {
      const refreshResponse = await fetch(`${import.meta.env.VITE_API_URL || ""}/api/watchlist/refresh`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ tags: [] })
      });

      if (!refreshResponse.ok) {
        throw new Error(`Refresh failed: ${refreshResponse.statusText}`);
      }

      const refreshPayload = await refreshResponse.json().catch(() => ({} as { message?: string }));
      messageApi.open({
        key,
        type: "success",
        content: refreshPayload.message ?? "All stocks refreshed successfully.",
        duration: 3,
      });
    } catch (e) {
      messageApi.open({
        key,
        type: "error",
        content: e instanceof Error ? e.message : "Refresh failed after stock creation",
        duration: 4,
      });
    }
  };

  const handleUpdate = async (payload: any) => {
    if (!selectedStock) return;
    try {
      await updateStock(selectedStock.id, payload);
    } catch (e) {
      messageApi.error(e instanceof Error ? e.message : "Failed to update stock");
      throw e;
    }
  };

  const handleRowClick = (symbol: string) => {
    const stockToEdit = stocks.find((s) => s.symbol === symbol);
    if (stockToEdit) {
      setSelectedStock(stockToEdit);
      setDrawerMode("edit");
    } else {
      messageApi.error("Stock details not found.");
    }
  };

  if (loading && stocks.length === 0) {
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
        {/* Main Dashboard Area (100% width) */}
        <div
          style={{
            flex: 1,
            overflow: "hidden",
          }}
        >
          <WatchlistDashboard 
            onAddClick={() => setDrawerMode("create")}
            onRowClick={handleRowClick}
          />
        </div>

        {/* Action Drawer */}
        <StockEntryDrawer
          open={drawerMode !== null}
          mode={drawerMode || "create"}
          stock={selectedStock}
          allTags={configTags}
          existingStockTokens={new Set(stocks.map((s) => s.instrument_token))}
          onCreate={async (payload) => {
            await handleCreate(payload);
          }}
          onUpdate={async (payload) => {
            await handleUpdate(payload);
          }}
          onClose={() => {
            setDrawerMode(null);
            setSelectedStock(null);
          }}
        />

        {/* Telegram widget */}
        <TelegramChatWidget />
      </div>
    </>
  );
}
