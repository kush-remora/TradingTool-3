import { BarChartOutlined, BookOutlined, DownloadOutlined, FundOutlined, ThunderboltOutlined, UnorderedListOutlined, LineChartOutlined, AreaChartOutlined } from "@ant-design/icons";
import { ConfigProvider, Layout, Menu } from "antd";
import type { MenuProps } from "antd";
import { useState } from "react";
import { BaseSwingPage } from "./pages/BaseSwingPage";
import { BollingerSqueezePage } from "./pages/BollingerSqueezePage";
import { ConsoleV2GrowwWatchlistImportPage } from "./pages/ConsoleV2GrowwWatchlistImportPage";
import { CorporateResultsPage } from "./pages/CorporateResultsPage";
import { DrawdownScannerPage } from "./pages/DrawdownScannerPage";
import { DeliveryThresholdBacktestPage } from "./pages/DeliveryThresholdBacktestPage";
import { WyckoffPhase1Page } from "./pages/WyckoffPhase1Page";
import { EarningsDashboardPage } from "./pages/EarningsDashboardPage";
import { FiftyTwoWeekHighBacktestPage } from "./pages/FiftyTwoWeekHighBacktestPage";
import { FiftyTwoWeekHighLivePage } from "./pages/FiftyTwoWeekHighLivePage";
import { HotSmaScannerPage } from "./pages/HotSmaScannerPage";
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
import { IntradayShockBacktestPage } from "./pages/IntradayShockBacktestPage";
import { WatchlistPage } from "./pages/WatchlistPage";
import { WeeklyCycleSuccessPage } from "./pages/WeeklyCycleSuccessPage";
import { WeeklySwingPage } from "./pages/WeeklySwingPage";

type V1PageKey =
  | "watchlist"
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
  | "base-swing"
  | "weekly-cycle-success"
  | "s4-volume-spike"
  | "volume-spike-backtest"
  | "corporate-results"
  | "earnings-dashboard"
  | "v2-dashboard"
  | "bollinger-squeeze"
  | "delivery-threshold-backtest"
  | "wyckoff-phase1"
  | "fiftytwo-week-high-backtest"
  | "fiftytwo-week-high-live"
  | "hot-sma-scanner"
  | "intraday-shock-backtest";

type PageKey = V1PageKey | "watchlist-import";

const menuItems: MenuProps["items"] = [
  { key: "watchlist-import", label: "Watchlist Import", icon: <FundOutlined /> },
  { key: "watchlist", label: "Watchlist", icon: <UnorderedListOutlined /> },
  { key: "bollinger-squeeze", label: "Bollinger Squeeze", icon: <AreaChartOutlined /> },
  { key: "delivery-threshold-backtest", label: "Delivery Threshold BT", icon: <BarChartOutlined /> },
  { key: "wyckoff-phase1", label: "Wyckoff Phase-1", icon: <BarChartOutlined /> },
  { key: "fiftytwo-week-high-backtest", label: "52W High BT", icon: <BarChartOutlined /> },
  { key: "fiftytwo-week-high-live", label: "104W Live", icon: <BarChartOutlined /> },
  { key: "hot-sma-scanner", label: "Hot SMA Scanner", icon: <BarChartOutlined /> },
  {
    key: "unused",
    label: "Unused",
    icon: <BarChartOutlined />,
    children: [
      { key: "swing-analysis", label: "Swing Visualizer", icon: <BarChartOutlined /> },
    ],
  },
  { key: "trade", label: "Trade Journal", icon: <BookOutlined /> },
  { key: "trade-ready", label: "Trade Ready", icon: <ThunderboltOutlined /> },
  { key: "remora", label: "Remora", icon: <FundOutlined /> },
  { key: "remora-rsi-floor", label: "Remora RSI Floor", icon: <FundOutlined /> },
  { key: "rsi-momentum", label: "RSI Momentum", icon: <FundOutlined /> },
  { key: "rsi-momentum-base", label: "RSI Momentum Base", icon: <FundOutlined /> },
  { key: "rsi-momentum-safe", label: "RSI Safe", icon: <ThunderboltOutlined /> },
  { key: "rsi-momentum-drawdown", label: "RSI Drawdown", icon: <FundOutlined /> },
  { key: "drawdown-scanner", label: "Drawdown Scanner", icon: <FundOutlined /> },
  { key: "simple-backtest", label: "Simple Backtest", icon: <FundOutlined /> },
  { key: "rsi-rank-drift", label: "RSI Rank Drift", icon: <FundOutlined /> },
  { key: "momentum-data-prep", label: "Momentum Data Prep", icon: <FundOutlined /> },
  { key: "base-swing", label: "Base-Swing Profiler", icon: <LineChartOutlined /> },
  { key: "weekly-swing", label: "Weekly Swing", icon: <BarChartOutlined /> },
  { key: "weekly-cycle-success", label: "Weekly Cycle Success", icon: <BarChartOutlined /> },
  { key: "s4-volume-spike", label: "S4 Volume Spike", icon: <FundOutlined /> },
  { key: "volume-spike-backtest", label: "Volume Backtest", icon: <FundOutlined /> },
  { key: "intraday-shock-backtest", label: "Intraday Volume Shock", icon: <FundOutlined /> },
  { key: "screener", label: "Weekly Screener", icon: <BarChartOutlined /> },
  { key: "corporate-results", label: "Results Export", icon: <DownloadOutlined /> },
  { key: "earnings-dashboard", label: "Earnings Dashboard", icon: <FundOutlined /> },
  { key: "v2-dashboard", label: "V2 Dashboard", icon: <FundOutlined /> },
];

const validPages: PageKey[] = [
  "watchlist-import",
  "watchlist",
  "swing-analysis",
  "trade",
  "trade-ready",
  "remora",
  "remora-rsi-floor",
  "rsi-momentum",
  "rsi-momentum-base",
  "rsi-momentum-safe",
  "rsi-momentum-drawdown",
  "drawdown-scanner",
  "simple-backtest",
  "rsi-rank-drift",
  "momentum-data-prep",
  "base-swing",
  "weekly-swing",
  "weekly-cycle-success",
  "s4-volume-spike",
  "volume-spike-backtest",
  "intraday-shock-backtest",
  "screener",
  "corporate-results",
  "earnings-dashboard",
  "v2-dashboard",
  "bollinger-squeeze",
  "delivery-threshold-backtest",
  "wyckoff-phase1",
  "fiftytwo-week-high-backtest",
  "fiftytwo-week-high-live",
  "hot-sma-scanner",
];

export default function App() {
  const getInitialRoute = (): PageKey => {
    const path = window.location.pathname;
    const baseUrl = import.meta.env.BASE_URL;
    const internalPath = path.startsWith(baseUrl) ? path.slice(baseUrl.length) : path;

    const cleanPath = internalPath.replace(/^\//, "").replace(/\/+$/, "");
    if (cleanPath === "" || cleanPath === "console-v1" || cleanPath === "console") {
      return "watchlist";
    }

    if (cleanPath.startsWith("console-v1/")) {
      const page = cleanPath.slice("console-v1/".length) as V1PageKey;
      if (validPages.includes(page)) {
        return page;
      }
    }

    if (cleanPath === "console-v2") {
      return "watchlist-import";
    }

    if (cleanPath.startsWith("console-v2/")) {
      const page = cleanPath.slice("console-v2/".length) as PageKey;
      if (validPages.includes(page)) {
        return page;
      }
    }

    if (cleanPath.startsWith("console/")) {
      const page = cleanPath.slice("console/".length) as PageKey;
      if (validPages.includes(page)) {
        return page;
      }
    }

    return "watchlist";
  };

  const [route, setRoute] = useState<PageKey>(getInitialRoute());

  const handleMenuClick: MenuProps["onClick"] = (e) => {
    const page = String(e.key) as PageKey;
    const baseUrl = import.meta.env.BASE_URL;
    if (validPages.includes(page)) {
      setRoute(page);
      window.history.pushState({}, "", `${baseUrl}console/${page}`);
    }
  };

  const selectedKeys = [route];

  return (
    <ConfigProvider>
      <Layout style={{ minHeight: "100vh" }}>
        <Layout.Header style={{ display: "flex", alignItems: "center", padding: "0 20px", background: "#fff", borderBottom: "1px solid #f0f0f0" }}>
          <div style={{ color: "#000", fontSize: "1.2rem", fontWeight: "bold", marginRight: "40px" }}>
            TradingTool
          </div>
        </Layout.Header>
        <Layout>
          <Layout.Sider
            width={260}
            theme="light"
            style={{
              borderRight: "1px solid #f0f0f0",
              height: "calc(100vh - 64px)",
              overflow: "auto",
              position: "sticky",
              top: 64,
              left: 0,
            }}
          >
            <Menu
              mode="inline"
              selectedKeys={selectedKeys}
              items={menuItems}
              onClick={handleMenuClick}
              style={{ borderRight: 0 }}
            />
          </Layout.Sider>
          <Layout.Content>
            {route === "watchlist-import" && <ConsoleV2GrowwWatchlistImportPage />}
            {route === "watchlist" && <WatchlistPage />}
            {route === "swing-analysis" && <SwingAnalysisPage />}
            {route === "trade" && <TradePage />}
            {route === "trade-ready" && <TradeReadyPage />}
            {route === "remora" && <RemoraPage />}
            {route === "remora-rsi-floor" && <RemoraRsiFloorPage />}
            {route === "rsi-momentum" && <RsiMomentumPage />}
            {route === "rsi-momentum-base" && <RsiMomentumBasePage />}
            {route === "rsi-momentum-safe" && <RsiMomentumSafePage />}
            {route === "rsi-momentum-drawdown" && <RsiMomentumLeadersDrawdownPage />}
            {route === "drawdown-scanner" && <DrawdownScannerPage />}
            {route === "simple-backtest" && <SimpleBacktestPage />}
            {route === "rsi-rank-drift" && <RsiRankDriftBacktestPage />}
            {route === "momentum-data-prep" && <MomentumDataPrepPage />}
            {route === "base-swing" && <BaseSwingPage />}
            {route === "weekly-swing" && <WeeklySwingPage />}
            {route === "weekly-cycle-success" && <WeeklyCycleSuccessPage />}
            {route === "s4-volume-spike" && <S4VolumeSpikePage />}
            {route === "volume-spike-backtest" && <VolumeSpikeBacktestPage />}
            {route === "intraday-shock-backtest" && <IntradayShockBacktestPage />}
            {route === "screener" && <ScreenerPage />}
            {route === "corporate-results" && <CorporateResultsPage />}
            {route === "earnings-dashboard" && <EarningsDashboardPage />}
            {route === "v2-dashboard" && <V2DashboardPage />}
            {route === "bollinger-squeeze" && <BollingerSqueezePage />}
            {route === "delivery-threshold-backtest" && <DeliveryThresholdBacktestPage />}
            {route === "wyckoff-phase1" && <WyckoffPhase1Page />}
            {route === "fiftytwo-week-high-backtest" && <FiftyTwoWeekHighBacktestPage />}
            {route === "fiftytwo-week-high-live" && <FiftyTwoWeekHighLivePage />}
            {route === "hot-sma-scanner" && <HotSmaScannerPage />}
          </Layout.Content>
        </Layout>
      </Layout>
    </ConfigProvider>
  );
}
