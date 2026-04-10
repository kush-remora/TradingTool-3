import { ApartmentOutlined, BookOutlined, FundOutlined, ThunderboltOutlined, UnorderedListOutlined } from "@ant-design/icons";
import { ConfigProvider, Layout, Menu, theme } from "antd";
import type { MenuProps } from "antd";
import { useState } from "react";
import { GraphPage } from "./pages/GraphPage";
import { RemoraPage } from "./pages/RemoraPage";
import { TradePage } from "./pages/TradePage";
import { WatchlistPage } from "./pages/WatchlistPage";
import { ScreenerPage } from "./pages/ScreenerPage";
import { TradeReadyPage } from "./pages/TradeReadyPage";
import { BarChartOutlined } from "@ant-design/icons";

type PageKey = "watchlist" | "graph" | "trade" | "trade-ready" | "remora" | "screener";

const menuItems: MenuProps["items"] = [
  { key: "watchlist", label: "Watchlist", icon: <UnorderedListOutlined /> },
  { key: "graph", label: "Graph", icon: <ApartmentOutlined /> },
  { key: "trade", label: "Trade Journal", icon: <BookOutlined /> },
  { key: "trade-ready", label: "Trade Ready", icon: <ThunderboltOutlined /> },
  { key: "remora", label: "Remora", icon: <FundOutlined /> },
  { key: "screener", label: "Weekly Screener", icon: <BarChartOutlined /> },
];

export default function App() {
  const [page, setPage] = useState<PageKey>("watchlist");

  return (
    <ConfigProvider
      theme={{
        algorithm: theme.defaultAlgorithm,
        token: {
          colorPrimary: "#1677ff",
          borderRadius: 8,
          fontSize: 14,
        },
      }}
    >
      <Layout style={{ minHeight: "100vh", background: "#f5f7fa" }}>
        {/* Top nav */}
        <Layout.Header
          style={{
            background: "#fff",
            borderBottom: "1px solid #e8e8e8",
            padding: "0 24px",
            display: "flex",
            alignItems: "center",
            height: 48,
            lineHeight: "48px",
          }}
        >
          <span style={{ fontWeight: 700, fontSize: 15, marginRight: 32, color: "#1677ff" }}>
            TradingTool
          </span>
          <Menu
            mode="horizontal"
            selectedKeys={[page]}
            items={menuItems}
            onClick={(e) => setPage(e.key as PageKey)}
            style={{ border: "none", flex: 1, lineHeight: "46px", background: "transparent" }}
          />
        </Layout.Header>

        <Layout.Content>
          {page === "watchlist" && <WatchlistPage />}
          {page === "graph" && <GraphPage />}
          { page === "trade" && <TradePage /> }
          { page === "trade-ready" && <TradeReadyPage /> }
          { page === "remora" && <RemoraPage /> }
          { page === "screener" && <ScreenerPage /> }
        </Layout.Content>
      </Layout>
    </ConfigProvider>
  );
}
