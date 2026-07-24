import { BarChartOutlined, BookOutlined, FundOutlined, HeatMapOutlined, LineChartOutlined } from "@ant-design/icons";
import { ConfigProvider, Layout, Menu } from "antd";
import type { MenuProps } from "antd";
import { useState } from "react";

import { VolumeShockerDashboardPage } from "./pages/VolumeShockerDashboardPage";
import { DeliveryBreakoutScannerPage } from "./pages/DeliveryBreakoutScannerPage";
import { HotSmaPage } from "./pages/HotSmaPage";
import { WyckoffPhase1Page } from "./pages/WyckoffPhase1Page";
import { TradePage } from "./pages/TradePage";
import { PhaseDScannerPage } from "./pages/PhaseDScannerPage";
import { ChartinkFiftyTwoWeekHighPage } from "./pages/ChartinkFiftyTwoWeekHighPage";
import { TrailingStopBacktestPage } from "./pages/TrailingStopBacktestPage";
import { FiftyTwoWeekMomentumRule5Page } from "./pages/FiftyTwoWeekMomentumRule5Page";
import { CsvBacktestPage } from "./pages/CsvBacktestPage";
import { BacktestReviewsPage } from "./pages/BacktestReviewsPage";
import { ChartinkEvidencePage } from "./pages/ChartinkEvidencePage";
import { ForwardAccumulationAnalysisPage } from "./pages/ForwardAccumulationAnalysisPage";
import { ForwardAccumulationTimelinePage } from "./pages/ForwardAccumulationTimelinePage";
import { WeeklyFloorReboundPage } from "./pages/WeeklyFloorReboundPage";
import type { AccumulationCaseSnapshot } from "./types";

type V1PageKey =
  | "trade"
  | "wyckoff-phase1"
  | "volume-shocker"
  | "delivery-breakout"
  | "hot-sma"
  | "phase-d"
  | "chartink-52w"
  | "trailing-stop"
  | "52w-momentum-rule5"
  | "csv-backtest"
  | "backtest-reviews"
  | "chartink-evidence"
  | "forward-accumulation"
  | "weekly-floor-rebound";

type PageKey = V1PageKey;

interface ForwardAccumulationTimelineRoute {
  page: "forward-accumulation-timeline";
  runId: number;
  symbol: string;
  chainStartDate: string | null;
  chainEndDate: string | null;
}

type Route = PageKey | ForwardAccumulationTimelineRoute;

const menuItems: MenuProps["items"] = [
  { key: "volume-shocker", label: "Volume Shocker", icon: <FundOutlined /> },
  { key: "delivery-breakout", label: "Delivery Breakout", icon: <FundOutlined /> },
  { key: "hot-sma", label: "SMA Buy Zone", icon: <HeatMapOutlined /> },
  { key: "chartink-52w", label: "Chartink 52W Backtest", icon: <LineChartOutlined /> },
  { key: "trailing-stop", label: "Trailing Stop Backtest", icon: <LineChartOutlined /> },
  { key: "weekly-floor-rebound", label: "Weekly Floor Rebound", icon: <LineChartOutlined /> },
  { key: "52w-momentum-rule5", label: "52W Momentum Rule 5", icon: <LineChartOutlined /> },
  { key: "csv-backtest", label: "CSV Backtest Tool", icon: <LineChartOutlined /> },
  { key: "backtest-reviews", label: "Saved Backtest Reviews", icon: <BookOutlined /> },
  { key: "wyckoff-phase1", label: "Wyckoff Phase-1", icon: <BarChartOutlined /> },
  { key: "phase-d", label: "Phase D Scanner", icon: <FundOutlined /> },
  { key: "chartink-evidence", label: "Chartink Evidence", icon: <FundOutlined /> },
  { key: "forward-accumulation", label: "Forward Accumulation", icon: <BarChartOutlined /> },
  { key: "trade", label: "Trade Journal", icon: <BookOutlined /> },
];

const validPages: PageKey[] = [
  "trade",
  "wyckoff-phase1",
  "volume-shocker",
  "delivery-breakout",
  "hot-sma",
  "phase-d",
  "chartink-52w",
  "trailing-stop",
  "weekly-floor-rebound",
  "52w-momentum-rule5",
  "csv-backtest",
  "backtest-reviews",
  "chartink-evidence",
  "forward-accumulation",
];

export default function App() {
  const getInitialRoute = (): Route => {
    const path = window.location.pathname;
    const baseUrl = import.meta.env.BASE_URL;
    const internalPath = path.startsWith(baseUrl) ? path.slice(baseUrl.length) : path;

    const cleanPath = internalPath.replace(/^\//, "").replace(/\/+$/, "");
    const timelineMatch = cleanPath.match(/^(?:console|console-v1|console-v2)\/forward-accumulation\/timeline\/(\d+)\/([^/]+)$/);
    if (timelineMatch) {
      const runId = Number(timelineMatch[1]);
      if (Number.isInteger(runId) && runId > 0) {
        const params = new URLSearchParams(window.location.search);
        return { page: "forward-accumulation-timeline", runId, symbol: decodeURIComponent(timelineMatch[2]), chainStartDate: params.get("chainStart"), chainEndDate: params.get("chainEnd") };
      }
    }
    if (cleanPath === "" || cleanPath === "console-v1" || cleanPath === "console") {
      return "wyckoff-phase1";
    }

    if (cleanPath.startsWith("console-v1/")) {
      const page = cleanPath.slice("console-v1/".length) as V1PageKey;
      if (validPages.includes(page)) {
        return page;
      }
    }

    if (cleanPath === "console-v2") {
      return "wyckoff-phase1";
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

    return "wyckoff-phase1";
  };

  const [route, setRoute] = useState<Route>(getInitialRoute());

  const handleMenuClick: MenuProps["onClick"] = (e) => {
    const page = String(e.key) as PageKey;
    const baseUrl = import.meta.env.BASE_URL;
    if (validPages.includes(page)) {
      setRoute(page);
      window.history.pushState({}, "", `${baseUrl}console/${page}`);
    }
  };

  const openTimeline = (runId: number, row: AccumulationCaseSnapshot) => {
    const params = new URLSearchParams({ chainStart: row.chainStartDate, chainEnd: row.chainEndDate });
    window.open(`${import.meta.env.BASE_URL}console/forward-accumulation/timeline/${runId}/${encodeURIComponent(row.symbol)}?${params}`, "_blank", "noopener,noreferrer");
  };

  const closeTimeline = () => {
    setRoute("forward-accumulation");
    window.history.pushState({}, "", `${import.meta.env.BASE_URL}console/forward-accumulation`);
  };

  const selectedKeys = [typeof route === "string" ? route : "forward-accumulation"];

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

            {route === "trade" && <TradePage />}
            {route === "wyckoff-phase1" && <WyckoffPhase1Page />}
            {route === "volume-shocker" && <VolumeShockerDashboardPage />}
            {route === "delivery-breakout" && <DeliveryBreakoutScannerPage />}
            {route === "hot-sma" && <HotSmaPage />}
            { route === "phase-d" && <PhaseDScannerPage /> }
            {route === "chartink-52w" && <ChartinkFiftyTwoWeekHighPage />}
            {route === "trailing-stop" && <TrailingStopBacktestPage />}
            {route === "weekly-floor-rebound" && <WeeklyFloorReboundPage />}
            {route === "52w-momentum-rule5" && <FiftyTwoWeekMomentumRule5Page />}
            {route === "csv-backtest" && <CsvBacktestPage />}
            {route === "backtest-reviews" && <BacktestReviewsPage />}
            {route === "chartink-evidence" && <ChartinkEvidencePage />}
            {route === "forward-accumulation" && <ForwardAccumulationAnalysisPage onOpenTimeline={openTimeline} />}
            {typeof route !== "string" && <ForwardAccumulationTimelinePage runId={route.runId} symbol={route.symbol} chainStartDate={route.chainStartDate} chainEndDate={route.chainEndDate} onBack={closeTimeline} />}
          </Layout.Content>
        </Layout>
      </Layout>
    </ConfigProvider>
  );
}
