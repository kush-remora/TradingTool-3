import { useState } from "react";
import { ConfigProvider, theme } from "antd";
import { WatchlistSidebar } from "./components/WatchlistSidebar";
import { StockDataGrid } from "./components/StockDataGrid";
import { TelegramChatWidget } from "./components/TelegramChatWidget";
import { useWatchlists } from "./hooks/useWatchlists";
import { useWatchlistStocks } from "./hooks/useWatchlistStocks";

const BG = "#0a0a0a";
const BORDER = "#1f1f1f";

export default function App() {
  const [selectedId, setSelectedId] = useState<number | null>(null);

  const {
    watchlists,
    loading: wlLoading,
    createWatchlist,
    renameWatchlist,
    removeWatchlist,
  } = useWatchlists();

  const selectedWatchlist = watchlists.find((w) => w.id === selectedId);

  const { rows, loading: stockLoading, error } = useWatchlistStocks(selectedId);

  return (
    <ConfigProvider
      theme={{
        algorithm: theme.darkAlgorithm,
        token: {
          colorBgBase: BG,
          colorBgContainer: "#141414",
          colorBorder: BORDER,
          fontSize: 12,
          borderRadius: 4,
        },
      }}
    >
      <div
        style={{
          display: "flex",
          height: "100vh",
          width: "100vw",
          background: BG,
          overflow: "hidden",
          fontFamily: "'Roboto Mono', 'JetBrains Mono', monospace",
        }}
      >
        {/* Left sidebar */}
        <WatchlistSidebar
          watchlists={watchlists}
          selectedId={selectedId}
          loading={wlLoading}
          onSelect={setSelectedId}
          onCreate={async (name, description) => { await createWatchlist(name, description); }}
          onRename={async (id, name) => { await renameWatchlist(id, name); }}
          onDelete={async (id) => {
            await removeWatchlist(id);
            if (selectedId === id) setSelectedId(null);
          }}
        />

        {/* Main content */}
        <div style={{ flex: 1, display: "flex", flexDirection: "column", overflow: "hidden" }}>
          {/* Top bar */}
          <div
            style={{
              padding: "0 12px",
              height: 36,
              borderBottom: `1px solid ${BORDER}`,
              display: "flex",
              alignItems: "center",
              gap: 12,
              flexShrink: 0,
            }}
          >
            <span
              style={{
                fontSize: 12,
                fontWeight: 700,
                color: "#26a69a",
                letterSpacing: 2,
                fontFamily: "monospace",
              }}
            >
              TRADINGTOOL
            </span>
            <span style={{ fontSize: 10, color: "#333" }}>|</span>
            <span style={{ fontSize: 10, color: "#555" }}>
              {new Date().toLocaleDateString("en-IN", {
                day: "2-digit",
                month: "short",
                year: "numeric",
              })}
            </span>
          </div>

          {/* Data grid */}
          <div style={{ flex: 1, overflow: "hidden" }}>
            <StockDataGrid
              rows={rows}
              loading={stockLoading}
              error={error}
              watchlistName={selectedWatchlist?.name}
            />
          </div>
        </div>

        {/* Telegram FAB */}
        <TelegramChatWidget />
      </div>
    </ConfigProvider>
  );
}
