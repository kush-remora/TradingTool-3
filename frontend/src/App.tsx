import { ApartmentOutlined, BookOutlined, FundOutlined, ThunderboltOutlined, UnorderedListOutlined, DownloadOutlined } from "@ant-design/icons";
import { ConfigProvider, Layout, Menu } from "antd";
import type { MenuProps } from "antd";
import { useState } from "react";
import { GraphPage } from "./pages/GraphPage";
import { SwingAnalysisPage } from "./pages/SwingAnalysisPage";
import { RemoraPage } from "./pages/RemoraPage";
import { TradePage } from "./pages/TradePage";
import { WatchlistPage } from "./pages/WatchlistPage";
import { ScreenerPage } from "./pages/ScreenerPage";
import { TradeReadyPage } from "./pages/TradeReadyPage";
import { BarChartOutlined } from "@ant-design/icons";
import { RsiMomentumPage } from "./pages/RsiMomentumPage";
import RsiMomentumSafePage from "./pages/RsiMomentumSafePage";
import { RsiMomentumBasePage } from "./pages/RsiMomentumBasePage";
import { WeeklySwingPage } from "./pages/WeeklySwingPage";
import { S4VolumeSpikePage } from "./pages/S4VolumeSpikePage";
import { MomentumDataPrepPage } from "./pages/MomentumDataPrepPage";
import { SimpleBacktestPage } from "./pages/SimpleBacktestPage";
import { RsiMomentumLeadersDrawdownPage } from "./pages/RsiMomentumLeadersDrawdownPage";
import { DrawdownScannerPage } from "./pages/DrawdownScannerPage";
import { CorporateResultsPage } from "./pages/CorporateResultsPage";
import { RsiRankDriftBacktestPage } from "./pages/RsiRankDriftBacktestPage";
import { WeeklyCycleSuccessPage } from "./pages/WeeklyCycleSuccessPage";
import { V2DashboardPage } from "./pages/V2DashboardPage";

type PageKey = "watchlist" | "graph" | "swing-analysis" | "trade" | "trade-ready" | "remora" | "screener" | "rsi-momentum" | "rsi-momentum-base" | "rsi-momentum-safe" | "weekly-swing" | "weekly-cycle-success" | "s4-volume-spike" | "momentum-data-prep" | "simple-backtest" | "rsi-momentum-drawdown" | "drawdown-scanner" | "corporate-results" | "rsi-rank-drift" | "v2-dashboard";

const menuItems: MenuProps["items"] = [
  { key: "watchlist", label: "Watchlist", icon: <UnorderedListOutlined /> },
  { key: "graph", label: "Graph", icon: <ApartmentOutlined /> },
  { key: "swing-analysis", label: "Swing Visualizer", icon: <BarChartOutlined /> },
  { key: "trade", label: "Trade Journal", icon: <BookOutlined /> },
  { key: "trade-ready", label: "Trade Ready", icon: <ThunderboltOutlined /> },
  { key: "remora", label: "Remora", icon: <FundOutlined /> },
  { key: "rsi-momentum", label: "RSI Momentum", icon: <FundOutlined /> },
  { key: "rsi-momentum-base", label: "RSI Momentum Base", icon: <FundOutlined /> },
  { key: "rsi-momentum-safe", label: "RSI Safe", icon: <ThunderboltOutlined /> },
  { key: "rsi-momentum-drawdown", label: "RSI Drawdown", icon: <FundOutlined /> },
  { key: "drawdown-scanner", label: "Drawdown Scanner", icon: <FundOutlined /> },
  { key: "simple-backtest", label: "Simple Backtest", icon: <FundOutlined /> },
  { key: "rsi-rank-drift", label: "RSI Rank Drift", icon: <FundOutlined /> },
  { key: "momentum-data-prep", label: "Momentum Data Prep", icon: <FundOutlined /> },
  { key: "weekly-swing", label: "Weekly Swing", icon: <BarChartOutlined /> },
  { key: "weekly-cycle-success", label: "Weekly Cycle Success", icon: <BarChartOutlined /> },
  { key: "s4-volume-spike", label: "S4 Volume Spike", icon: <FundOutlined /> },
  { key: "screener", label: "Weekly Screener", icon: <BarChartOutlined /> },
  { key: "corporate-results", label: "Results Export", icon: <DownloadOutlined /> },
  { key: "v2-dashboard", label: "V2 Dashboard", icon: <FundOutlined /> },
];

export default function App() {
  const getInitialPage = (): PageKey => {
    const path = window.location.pathname;
    const baseUrl = import.meta.env.BASE_URL;
    // Remove baseUrl from path to find the internal route
    const internalPath = path.startsWith(baseUrl) ? path.slice(baseUrl.length) : path;
    const cleanPath = internalPath.replace(/^\//, "");
    
    const validPages: PageKey[] = ["watchlist", "graph", "swing-analysis", "trade", "trade-ready", "remora", "screener", "rsi-momentum", "rsi-momentum-base", "rsi-momentum-safe", "weekly-swing", "weekly-cycle-success", "s4-volume-spike", "momentum-data-prep", "simple-backtest", "rsi-momentum-drawdown", "drawdown-scanner", "corporate-results", "rsi-rank-drift", "v2-dashboard"];
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
          {page === "swing-analysis" && <SwingAnalysisPage />}
          { page === "trade" && <TradePage /> }
          { page === "trade-ready" && <TradeReadyPage /> }
          { page === "remora" && <RemoraPage /> }
          { page === "rsi-momentum" && <RsiMomentumPage /> }
          { page === "rsi-momentum-base" && <RsiMomentumBasePage /> }
          { page === "rsi-momentum-safe" && <RsiMomentumSafePage /> }
          { page === "rsi-momentum-drawdown" && <RsiMomentumLeadersDrawdownPage /> }
          { page === "drawdown-scanner" && <DrawdownScannerPage /> }
          { page === "simple-backtest" && <SimpleBacktestPage /> }
          { page === "rsi-rank-drift" && <RsiRankDriftBacktestPage /> }
          { page === "momentum-data-prep" && <MomentumDataPrepPage /> }
          { page === "weekly-swing" && <WeeklySwingPage /> }
          { page === "weekly-cycle-success" && <WeeklyCycleSuccessPage /> }
          { page === "s4-volume-spike" && <S4VolumeSpikePage /> }
          { page === "screener" && <ScreenerPage /> }
          { page === "corporate-results" && <CorporateResultsPage /> }
          { page === "v2-dashboard" && <V2DashboardPage /> }
        </Layout.Content>
      </Layout>
    </ConfigProvider>
  );
}
