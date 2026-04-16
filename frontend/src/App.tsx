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
import { RsiMomentumPage } from "./pages/RsiMomentumPage";
import { RsiMomentumSafePage } from "./pages/RsiMomentumSafePage";
import { WeeklySwingPage } from "./pages/WeeklySwingPage";
import { S4VolumeSpikePage } from "./pages/S4VolumeSpikePage";

type PageKey = "watchlist" | "graph" | "trade" | "trade-ready" | "remora" | "screener" | "rsi-momentum" | "rsi-momentum-safe" | "weekly-swing" | "s4-volume-spike";

const menuItems: MenuProps["items"] = [
  { key: "watchlist", label: "Watchlist", icon: <UnorderedListOutlined /> },
  { key: "graph", label: "Graph", icon: <ApartmentOutlined /> },
  { key: "trade", label: "Trade Journal", icon: <BookOutlined /> },
  { key: "trade-ready", label: "Trade Ready", icon: <ThunderboltOutlined /> },
  { key: "remora", label: "Remora", icon: <FundOutlined /> },
  { key: "rsi-momentum", label: "RSI Momentum", icon: <FundOutlined /> },
  { key: "rsi-momentum-safe", label: "RSI Safe", icon: <ThunderboltOutlined /> },
  { key: "weekly-swing", label: "Weekly Swing", icon: <BarChartOutlined /> },
  { key: "s4-volume-spike", label: "S4 Volume Spike", icon: <FundOutlined /> },
  { key: "screener", label: "Weekly Screener", icon: <BarChartOutlined /> },
];

export default function App() {
  const getInitialPage = (): PageKey => {
    const path = window.location.pathname;
    const baseUrl = import.meta.env.BASE_URL;
    // Remove baseUrl from path to find the internal route
    const internalPath = path.startsWith(baseUrl) ? path.slice(baseUrl.length) : path;
    const cleanPath = internalPath.replace(/^\//, "");
    
    const validPages: PageKey[] = ["watchlist", "graph", "trade", "trade-ready", "remora", "screener", "rsi-momentum", "rsi-momentum-safe", "weekly-swing", "s4-volume-spike"];
    if (validPages.includes(cleanPath as PageKey)) {
        return cleanPath as PageKey;
    }
    return "watchlist";
  };

  const [page, setPage] = useState<PageKey>(getInitialPage());

  const handleMenuClick: MenuProps["onClick"] = (e) => {
    const key = e.key as PageKey;
    setPage(key);
    
    // Update URL without full reload to keep query params if needed, 
    // but here we usually want to clear them when switching main tabs
    const baseUrl = import.meta.env.BASE_URL;
    const newPath = `${baseUrl}${key}`;
    window.history.pushState({}, "", newPath);
  };

  return (
    <ConfigProvider>
      <Layout style={{ minHeight: "100vh" }}>
        <Layout.Header style={{ display: "flex", alignItems: "center", padding: "0 20px", background: "#fff", borderBottom: "1px solid #f0f0f0" }}>
          <div style={{ color: "#000", fontSize: "1.2rem", fontWeight: "bold", marginRight: "40px" }}>
            TradingTool
          </div>
          <Menu
            theme="light"
            mode="horizontal"
            selectedKeys={[page]}
            items={menuItems}
            onClick={handleMenuClick}
            style={{ flex: 1, minWidth: 0 }}
          />
        </Layout.Header>
        <Layout.Content>
          {page === "watchlist" && <WatchlistPage />}
          {page === "graph" && <GraphPage />}
          { page === "trade" && <TradePage /> }
          { page === "trade-ready" && <TradeReadyPage /> }
          { page === "remora" && <RemoraPage /> }
          { page === "rsi-momentum" && <RsiMomentumPage /> }
          { page === "rsi-momentum-safe" && <RsiMomentumSafePage /> }
          { page === "weekly-swing" && <WeeklySwingPage /> }
          { page === "s4-volume-spike" && <S4VolumeSpikePage /> }
          { page === "screener" && <ScreenerPage /> }
        </Layout.Content>
      </Layout>
    </ConfigProvider>
  );
}
