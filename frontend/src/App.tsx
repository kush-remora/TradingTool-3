import {
  ApartmentOutlined,
  BarChartOutlined,
  BookOutlined,
  DownloadOutlined,
  FundOutlined,
  ThunderboltOutlined,
  UnorderedListOutlined,
} from "@ant-design/icons";
import { ConfigProvider, Layout, Menu } from "antd";
import type { MenuProps } from "antd";
import { useState } from "react";
import { ConsoleV2GrowwWatchlistImportPage } from "./pages/ConsoleV2GrowwWatchlistImportPage";
import { CorporateResultsPage } from "./pages/CorporateResultsPage";
import { DrawdownScannerPage } from "./pages/DrawdownScannerPage";
import { EarningsDashboardPage } from "./pages/EarningsDashboardPage";
import { GraphPage } from "./pages/GraphPage";
import { MomentumDataPrepPage } from "./pages/MomentumDataPrepPage";
import { RemoraPage } from "./pages/RemoraPage";
import { RemoraRsiFloorPage } from "./pages/RemoraRsiFloorPage";
import { RsiMomentumBasePage } from "./pages/RsiMomentumBasePage";
import { RsiMomentumLeadersDrawdownPage } from "./pages/RsiMomentumLeadersDrawdownPage";
import { RsiMomentumPage } from "./pages/RsiMomentumPage";
import RsiMomentumSafePage from "./pages/RsiMomentumSafePage";
import { RsiRankDriftBacktestPage } from "./pages/RsiRankDriftBacktestPage";
import { S4VolumeSpikePage } from "./pages/S4VolumeSpikePage";
import { ScreenerPage } from "./pages/ScreenerPage";
import { SimpleBacktestPage } from "./pages/SimpleBacktestPage";
import { SwingAnalysisPage } from "./pages/SwingAnalysisPage";
import { TradePage } from "./pages/TradePage";
import { TradeReadyPage } from "./pages/TradeReadyPage";
import { V2DashboardPage } from "./pages/V2DashboardPage";
import { VolumeSpikeBacktestPage } from "./pages/VolumeSpikeBacktestPage";
import { WatchlistPage } from "./pages/WatchlistPage";
import { WeeklyCycleSuccessPage } from "./pages/WeeklyCycleSuccessPage";
import { WeeklySwingPage } from "./pages/WeeklySwingPage";

type V1PageKey =
  | "watchlist"
  | "graph"
  | "swing-analysis"
  | "trade"
  | "trade-ready"
  | "remora"
  | "remora-rsi-floor"
  | "screener"
  | "rsi-momentum"
  | "rsi-momentum-base"
  | "rsi-momentum-safe"
  | "rsi-momentum-drawdown"
  | "drawdown-scanner"
  | "simple-backtest"
  | "rsi-rank-drift"
  | "momentum-data-prep"
  | "weekly-swing"
  | "weekly-cycle-success"
  | "s4-volume-spike"
  | "volume-spike-backtest"
  | "corporate-results"
  | "earnings-dashboard"
  | "v2-dashboard";

type V2PageKey = "watchlist-import";

type AppRoute =
  | { mode: "console-v2"; page: V2PageKey }
  | { mode: "console-v1"; page: V1PageKey };

const menuItems: MenuProps["items"] = [
  {
    key: "console-v2",
    label: "Console V2",
    icon: <FundOutlined />,
    children: [
      { key: "console-v2/watchlist-import", label: "Watchlist Import" },
    ],
  },
  {
    key: "console-v1",
    label: "Console V1",
    icon: <FundOutlined />,
    children: [
      { key: "console-v1/watchlist", label: "Watchlist", icon: <UnorderedListOutlined /> },
      { key: "console-v1/graph", label: "Graph", icon: <ApartmentOutlined /> },
      { key: "console-v1/swing-analysis", label: "Swing Visualizer", icon: <BarChartOutlined /> },
      { key: "console-v1/trade", label: "Trade Journal", icon: <BookOutlined /> },
      { key: "console-v1/trade-ready", label: "Trade Ready", icon: <ThunderboltOutlined /> },
      { key: "console-v1/remora", label: "Remora", icon: <FundOutlined /> },
      { key: "console-v1/remora-rsi-floor", label: "Remora RSI Floor", icon: <FundOutlined /> },
      { key: "console-v1/rsi-momentum", label: "RSI Momentum", icon: <FundOutlined /> },
      { key: "console-v1/rsi-momentum-base", label: "RSI Momentum Base", icon: <FundOutlined /> },
      { key: "console-v1/rsi-momentum-safe", label: "RSI Safe", icon: <ThunderboltOutlined /> },
      { key: "console-v1/rsi-momentum-drawdown", label: "RSI Drawdown", icon: <FundOutlined /> },
      { key: "console-v1/drawdown-scanner", label: "Drawdown Scanner", icon: <FundOutlined /> },
      { key: "console-v1/simple-backtest", label: "Simple Backtest", icon: <FundOutlined /> },
      { key: "console-v1/rsi-rank-drift", label: "RSI Rank Drift", icon: <FundOutlined /> },
      { key: "console-v1/momentum-data-prep", label: "Momentum Data Prep", icon: <FundOutlined /> },
      { key: "console-v1/weekly-swing", label: "Weekly Swing", icon: <BarChartOutlined /> },
      { key: "console-v1/weekly-cycle-success", label: "Weekly Cycle Success", icon: <BarChartOutlined /> },
      { key: "console-v1/s4-volume-spike", label: "S4 Volume Spike", icon: <FundOutlined /> },
      { key: "console-v1/volume-spike-backtest", label: "Volume Backtest", icon: <FundOutlined /> },
      { key: "console-v1/screener", label: "Weekly Screener", icon: <BarChartOutlined /> },
      { key: "console-v1/corporate-results", label: "Results Export", icon: <DownloadOutlined /> },
      { key: "console-v1/earnings-dashboard", label: "Earnings Dashboard", icon: <FundOutlined /> },
      { key: "console-v1/v2-dashboard", label: "V2 Dashboard", icon: <FundOutlined /> },
    ],
  },
];

export default function App() {
  const getInitialRoute = (): AppRoute => {
    const path = window.location.pathname;
    const baseUrl = import.meta.env.BASE_URL;
    const internalPath = path.startsWith(baseUrl) ? path.slice(baseUrl.length) : path;

    const cleanPath = internalPath.replace(/^\//, "").replace(/\/+$/, "");
    if (cleanPath === "" || cleanPath === "console-v1") {
      return { mode: "console-v1", page: "watchlist" };
    }

    if (cleanPath === "console-v2") {
      return { mode: "console-v2", page: "watchlist-import" };
    }

    if (cleanPath.startsWith("console-v1/")) {
      const page = cleanPath.slice("console-v1/".length) as V1PageKey;
      const validPages: V1PageKey[] = [
        "watchlist",
        "graph",
        "swing-analysis",
        "trade",
        "trade-ready",
        "remora",
        "remora-rsi-floor",
        "screener",
        "rsi-momentum",
        "rsi-momentum-base",
        "rsi-momentum-safe",
        "rsi-momentum-drawdown",
        "drawdown-scanner",
        "simple-backtest",
        "rsi-rank-drift",
        "momentum-data-prep",
        "weekly-swing",
        "weekly-cycle-success",
        "s4-volume-spike",
        "volume-spike-backtest",
        "corporate-results",
        "earnings-dashboard",
        "v2-dashboard",
      ];

      if (validPages.includes(page)) {
        return { mode: "console-v1", page };
      }
    }

    if (cleanPath.startsWith("console-v2/")) {
      const page = cleanPath.slice("console-v2/".length) as V2PageKey;
      const validPages: V2PageKey[] = ["watchlist-import"];
      if (validPages.includes(page)) {
        return { mode: "console-v2", page };
      }
    }

    return { mode: "console-v1", page: "watchlist" };
  };

  const [route, setRoute] = useState<AppRoute>(getInitialRoute());

  const handleMenuClick: MenuProps["onClick"] = (e) => {
    const rawKey = String(e.key);
    const baseUrl = import.meta.env.BASE_URL;

    // V1 pages
    if (rawKey.startsWith("console-v1/")) {
      const page = rawKey.slice("console-v1/".length) as V1PageKey;
      setRoute({ mode: "console-v1", page });
      window.history.pushState({}, "", `${baseUrl}console-v1/${page}`);
      return;
    }

    // V2 pages
    if (rawKey.startsWith("console-v2/")) {
      const page = rawKey.slice("console-v2/".length) as V2PageKey;
      setRoute({ mode: "console-v2", page });
      window.history.pushState({}, "", `${baseUrl}console-v2/${page}`);
      return;
    }
  };

  const selectedKeys = (() => {
    if (route.mode === "console-v2") return [`console-v2/${route.page}`];
    return [`console-v1/${route.page}`];
  })();

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
            selectedKeys={selectedKeys}
            items={menuItems}
            onClick={handleMenuClick}
            triggerSubMenuAction="click"
            style={{ flex: 1, minWidth: 0 }}
          />
        </Layout.Header>
        <Layout.Content>
          {route.mode === "console-v2" && route.page === "watchlist-import" && <ConsoleV2GrowwWatchlistImportPage />}
          {route.mode === "console-v1" && (
            <>
              {route.page === "watchlist" && <WatchlistPage />}
              {route.page === "graph" && <GraphPage />}
              {route.page === "swing-analysis" && <SwingAnalysisPage />}
              {route.page === "trade" && <TradePage />}
              {route.page === "trade-ready" && <TradeReadyPage />}
              {route.page === "remora" && <RemoraPage />}
              {route.page === "remora-rsi-floor" && <RemoraRsiFloorPage />}
              {route.page === "rsi-momentum" && <RsiMomentumPage />}
              {route.page === "rsi-momentum-base" && <RsiMomentumBasePage />}
              {route.page === "rsi-momentum-safe" && <RsiMomentumSafePage />}
              {route.page === "rsi-momentum-drawdown" && <RsiMomentumLeadersDrawdownPage />}
              {route.page === "drawdown-scanner" && <DrawdownScannerPage />}
              {route.page === "simple-backtest" && <SimpleBacktestPage />}
              {route.page === "rsi-rank-drift" && <RsiRankDriftBacktestPage />}
              {route.page === "momentum-data-prep" && <MomentumDataPrepPage />}
              {route.page === "weekly-swing" && <WeeklySwingPage />}
              {route.page === "weekly-cycle-success" && <WeeklyCycleSuccessPage />}
              {route.page === "s4-volume-spike" && <S4VolumeSpikePage />}
              {route.page === "volume-spike-backtest" && <VolumeSpikeBacktestPage />}
              {route.page === "screener" && <ScreenerPage />}
              {route.page === "corporate-results" && <CorporateResultsPage />}
              {route.page === "earnings-dashboard" && <EarningsDashboardPage />}
              {route.page === "v2-dashboard" && <V2DashboardPage />}
            </>
          )}
        </Layout.Content>
      </Layout>
    </ConfigProvider>
  );
}
