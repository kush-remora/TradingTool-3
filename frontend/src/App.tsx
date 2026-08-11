import {
  BarChartOutlined,
  BookOutlined,
  DownloadOutlined,
  FundOutlined,
  HeatMapOutlined,
  LineChartOutlined,
} from "@ant-design/icons";
import { ConfigProvider, Layout, Menu } from "antd";
import type { MenuProps } from "antd";
import { useState } from "react";
import { BuySellChangeCalculator } from "./components/BuySellChangeCalculator";

import { VolumeShockerDashboardPage } from "./pages/VolumeShockerDashboardPage";
import { AbsoluteDeliveryBacktestPage } from "./pages/AbsoluteDeliveryBacktestPage";
import { DeliveryBreakoutScannerPage } from "./pages/DeliveryBreakoutScannerPage";
import { HotSmaPage } from "./pages/HotSmaPage";
import { Sma200BacktestPage } from "./pages/Sma200BacktestPage";
import { RsiOversoldPage } from "./pages/RsiOversoldPage";
import { TwoDayGreenCandleBacktestPage } from "./pages/TwoDayGreenCandleBacktestPage";
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
import { WeeklyLowLimitBacktestPage } from "./pages/WeeklyLowLimitBacktestPage";
import { WeeklyLowLimitDailyValidationPage } from "./pages/WeeklyLowLimitDailyValidationPage";
import { WeeklyBaseDefinitionPage } from "./pages/WeeklyBaseDefinitionPage";
import { WeeklyBaseGroupBacktestPage } from "./pages/WeeklyBaseGroupBacktestPage";
import { ThreeWeekStockReviewPage } from "./pages/ThreeWeekStockReviewPage";
import { ThreeWeekWatchlistReviewPage } from "./pages/ThreeWeekWatchlistReviewPage";
import { CompactStockReviewPage } from "./pages/compactStockReview/CompactStockReviewPage";
import { WeeklyLowAlignmentSummaryPage } from "./pages/WeeklyLowAlignmentSummaryPage";
import { WeeklyLowAlignmentBacktestPage } from "./pages/WeeklyLowAlignmentBacktestPage";
import { VolumeEventReviewPage } from "./pages/VolumeEventReviewPage";
import { VolumeEventConfirmationBacktestPage } from "./pages/VolumeEventConfirmationBacktestPage";
import { WeeklyPriceWatchlistScannerPage } from "./pages/WeeklyPriceWatchlistScannerPage";
import { BreakoutTrackerPage } from "./pages/BreakoutTrackerPage";
import { SilentBreakoutBacktestPage } from "./pages/SilentBreakoutBacktestPage";
import { PriceAcceptanceScannerPage } from "./pages/PriceAcceptanceScannerPage";
import { StockHistoryDownloadPage } from "./pages/StockHistoryDownloadPage";
import { NetwebCycleTrackerPage } from "./pages/NetwebCycleTrackerPage";
import { SummaryConsolePage } from "./pages/SummaryConsolePage";
import type { AccumulationCaseSnapshot } from "./types";

type V1PageKey =
  | "summary-console"
  | "trade"
  | "wyckoff-phase1"
  | "volume-shocker"
  | "absolute-delivery"
  | "delivery-breakout"
  | "hot-sma"
  | "sma200-backtest"
  | "rsi-oversold"
  | "two-day-green-candle-backtest"
  | "phase-d"
  | "chartink-52w"
  | "trailing-stop"
  | "52w-momentum-rule5"
  | "csv-backtest"
  | "backtest-reviews"
  | "chartink-evidence"
  | "forward-accumulation"
  | "weekly-floor-rebound"
  | "weekly-low-limit-backtest"
  | "weekly-base-definition"
  | "weekly-base-group-backtest"
  | "three-week-stock-review"
  | "compact-stock-review"
  | "three-week-watchlist-review"
  | "weekly-low-alignment"
  | "weekly-low-alignment-backtest"
  | "volume-event-review"
  | "volume-event-confirmation-backtest"
  | "weekly-price-watchlist-scanner"
  | "breakout-tracker"
  | "silent-breakout-backtest"
  | "price-acceptance"
  | "stock-history-download"
  | "netweb-cycle";

type PageKey = V1PageKey;

interface ForwardAccumulationTimelineRoute {
  page: "forward-accumulation-timeline";
  runId: number;
  symbol: string;
  chainStartDate: string | null;
  chainEndDate: string | null;
}

interface WeeklyLowLimitDailyValidationRoute {
  page: "weekly-low-limit-validation";
  symbol: string;
  instrumentToken: number;
  previousWeekLowDate: string;
  entryWeekStartDate: string;
  entryDate: string | null;
}

type Route = PageKey | ForwardAccumulationTimelineRoute | WeeklyLowLimitDailyValidationRoute;

const menuItems: MenuProps["items"] = [
  { key: "summary-console", label: "Summary Console", icon: <FundOutlined /> },
  { key: "volume-shocker", label: "Volume Shocker", icon: <FundOutlined /> },
  {
    key: "absolute-delivery",
    label: "Absolute Delivery Backtest",
    icon: <FundOutlined />,
  },
  {
    key: "delivery-breakout",
    label: "Delivery Breakout",
    icon: <FundOutlined />,
  },
  { key: "hot-sma", label: "SMA Buy Zone", icon: <HeatMapOutlined /> },
  { key: "sma200-backtest", label: "SMA200 Backtest", icon: <LineChartOutlined /> },
  { key: "rsi-oversold", label: "RSI Low Scanner", icon: <LineChartOutlined /> },
  { key: "two-day-green-candle-backtest", label: "Two-Day Green Candle", icon: <LineChartOutlined /> },
  {
    key: "chartink-52w",
    label: "Chartink 52W Backtest",
    icon: <LineChartOutlined />,
  },
  {
    key: "trailing-stop",
    label: "Trailing Stop Backtest",
    icon: <LineChartOutlined />,
  },
  {
    key: "weekly-floor-rebound",
    label: "Weekly Floor Rebound",
    icon: <LineChartOutlined />,
  },
  {
    key: "weekly-low-limit-backtest",
    label: "Weekly Low Limit Backtest",
    icon: <LineChartOutlined />,
  },
  {
    key: "weekly-low-alignment-backtest",
    label: "Weekly Low Alignment Backtest",
    icon: <LineChartOutlined />,
  },
  {
    key: "weekly-base-definition",
    label: "Weekly Base Definition",
    icon: <LineChartOutlined />,
  },
  {
    key: "weekly-base-group-backtest",
    label: "Base Rebound Group Backtest",
    icon: <LineChartOutlined />,
  },
  {
    key: "three-week-stock-review",
    label: "Three-Week Stock Review",
    icon: <LineChartOutlined />,
  },
  {
    key: "compact-stock-review",
    label: "Compact Stock Review (New)",
    icon: <LineChartOutlined />,
  },
  {
    key: "three-week-watchlist-review",
    label: "Three-Week Stock Review + Current Week",
    icon: <LineChartOutlined />,
  },
  {
    key: "weekly-low-alignment",
    label: "Weekly Low Alignment Summary",
    icon: <LineChartOutlined />,
  },
  {
    key: "volume-event-review",
    label: "Volume Event Review",
    icon: <LineChartOutlined />,
  },
  {
    key: "volume-event-confirmation-backtest",
    label: "Volume Event Confirmation Backtest",
    icon: <LineChartOutlined />,
  },
  {
    key: "weekly-price-watchlist-scanner",
    label: "Base Consolidation Scanner",
    icon: <LineChartOutlined />,
  },
  {
    key: "price-acceptance",
    label: "Price Acceptance Scanner",
    icon: <BarChartOutlined />,
  },
  {
    key: "52w-momentum-rule5",
    label: "52W Momentum Rule 5",
    icon: <LineChartOutlined />,
  },
  {
    key: "csv-backtest",
    label: "CSV Backtest Tool",
    icon: <LineChartOutlined />,
  },
  {
    key: "silent-breakout-backtest",
    label: "Silent Breakout Backtest",
    icon: <LineChartOutlined />,
  },
  {
    key: "backtest-reviews",
    label: "Saved Backtest Reviews",
    icon: <BookOutlined />,
  },
  {
    key: "wyckoff-phase1",
    label: "Wyckoff Phase-1",
    icon: <BarChartOutlined />,
  },
  { key: "phase-d", label: "Phase D Scanner", icon: <FundOutlined /> },
  {
    key: "chartink-evidence",
    label: "Chartink Evidence",
    icon: <FundOutlined />,
  },
  {
    key: "forward-accumulation",
    label: "Forward Accumulation",
    icon: <BarChartOutlined />,
  },
  {
    key: "breakout-tracker",
    label: "Breakout Tracker",
    icon: <LineChartOutlined />,
  },
  {
    key: "netweb-cycle",
    label: "NETWEB Cycle Tracker",
    icon: <LineChartOutlined />,
  },
  { key: "trade", label: "Trade Journal", icon: <BookOutlined /> },
  { key: "stock-history-download", label: "Stock History CSV", icon: <DownloadOutlined /> },
];

const validPages: PageKey[] = [
  "summary-console",
  "trade",
  "wyckoff-phase1",
  "volume-shocker",
  "absolute-delivery",
  "delivery-breakout",
  "hot-sma",
  "sma200-backtest",
  "rsi-oversold",
  "two-day-green-candle-backtest",
  "phase-d",
  "chartink-52w",
  "trailing-stop",
  "weekly-floor-rebound",
  "weekly-low-limit-backtest",
  "weekly-low-alignment-backtest",
  "weekly-base-definition",
  "weekly-base-group-backtest",
  "three-week-stock-review",
  "compact-stock-review",
  "three-week-watchlist-review",
  "weekly-low-alignment",
  "volume-event-review",
  "volume-event-confirmation-backtest",
  "weekly-price-watchlist-scanner",
  "price-acceptance",
  "52w-momentum-rule5",
  "csv-backtest",
  "silent-breakout-backtest",
  "backtest-reviews",
  "chartink-evidence",
  "forward-accumulation",
  "breakout-tracker",
  "stock-history-download",
  "netweb-cycle",
];

const restoreFallbackRoute = (): void => {
  const redirect = new URLSearchParams(window.location.search).get("redirect");
  if (!redirect) {
    return;
  }

  let requestedUrl: URL;
  try {
    requestedUrl = new URL(redirect, window.location.origin);
  } catch {
    return;
  }
  const baseUrl = import.meta.env.BASE_URL;
  if (
    requestedUrl.origin !== window.location.origin ||
    !requestedUrl.pathname.startsWith(baseUrl)
  ) {
    return;
  }

  window.history.replaceState(
    {},
    "",
    `${requestedUrl.pathname}${requestedUrl.search}${requestedUrl.hash}`,
  );
};

export default function App() {
  const getInitialRoute = (): Route => {
    restoreFallbackRoute();
    const path = window.location.pathname;
    const baseUrl = import.meta.env.BASE_URL;
    const internalPath = path.startsWith(baseUrl)
      ? path.slice(baseUrl.length)
      : path;

    const cleanPath = internalPath.replace(/^\//, "").replace(/\/+$/, "");
    if (cleanPath === "console/weekly-low-limit-validation") {
      const params = new URLSearchParams(window.location.search);
      const instrumentToken = Number(params.get("instrumentToken"));
      const symbol = params.get("symbol")?.trim().toUpperCase();
      const previousWeekLowDate = params.get("previousWeekLowDate");
      const entryWeekStartDate = params.get("entryWeekStartDate");
      if (symbol && Number.isInteger(instrumentToken) && instrumentToken > 0 && previousWeekLowDate && entryWeekStartDate) {
        return {
          page: "weekly-low-limit-validation",
          symbol,
          instrumentToken,
          previousWeekLowDate,
          entryWeekStartDate,
          entryDate: params.get("entryDate"),
        };
      }
    }
    const timelineMatch = cleanPath.match(
      /^(?:console|console-v1|console-v2)\/forward-accumulation\/timeline\/(\d+)\/([^/]+)$/,
    );
    if (timelineMatch) {
      const runId = Number(timelineMatch[1]);
      if (Number.isInteger(runId) && runId > 0) {
        const params = new URLSearchParams(window.location.search);
        return {
          page: "forward-accumulation-timeline",
          runId,
          symbol: decodeURIComponent(timelineMatch[2]),
          chainStartDate: params.get("chainStart"),
          chainEndDate: params.get("chainEnd"),
        };
      }
    }
    if (
      cleanPath === "" ||
      cleanPath === "console-v1" ||
      cleanPath === "console"
    ) {
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
    const params = new URLSearchParams({
      chainStart: row.chainStartDate,
      chainEnd: row.chainEndDate,
    });
    window.open(
      `${import.meta.env.BASE_URL}console/forward-accumulation/timeline/${runId}/${encodeURIComponent(row.symbol)}?${params}`,
      "_blank",
      "noopener,noreferrer",
    );
  };

  const closeTimeline = () => {
    setRoute("forward-accumulation");
    window.history.pushState(
      {},
      "",
      `${import.meta.env.BASE_URL}console/forward-accumulation`,
    );
  };

  const openStockReview = (symbol: string) => {
    window.open(
      `${import.meta.env.BASE_URL}console/three-week-stock-review?symbol=${encodeURIComponent(symbol)}`,
      "_blank",
      "noopener,noreferrer",
    );
  };

  const selectedKeys = [
    typeof route === "string" ? route : "forward-accumulation",
  ];

  return (
    <ConfigProvider>
      <Layout style={{ minHeight: "100vh" }}>
        <Layout.Header
          style={{
            display: "flex",
            alignItems: "center",
            padding: "0 20px",
            background: "#fff",
            borderBottom: "1px solid #f0f0f0",
          }}
        >
          <div
            style={{
              color: "#000",
              fontSize: "1.2rem",
              fontWeight: "bold",
              marginRight: "40px",
            }}
          >
            TradingTool
          </div>
          <div className="app-header-calculator" data-testid="global-buy-sell-calculator">
            <BuySellChangeCalculator />
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
            {route === "summary-console" && <SummaryConsolePage onOpenStockReview={openStockReview} />}
            {route === "wyckoff-phase1" && <WyckoffPhase1Page />}
            {route === "volume-shocker" && <VolumeShockerDashboardPage />}
            {route === "absolute-delivery" && <AbsoluteDeliveryBacktestPage />}
            {route === "delivery-breakout" && <DeliveryBreakoutScannerPage />}
            {route === "hot-sma" && <HotSmaPage />}
            {route === "sma200-backtest" && <Sma200BacktestPage />}
            {route === "rsi-oversold" && <RsiOversoldPage />}
            {route === "two-day-green-candle-backtest" && <TwoDayGreenCandleBacktestPage />}
            {route === "phase-d" && <PhaseDScannerPage />}
            {route === "chartink-52w" && <ChartinkFiftyTwoWeekHighPage />}
            {route === "trailing-stop" && <TrailingStopBacktestPage />}
            {route === "weekly-floor-rebound" && <WeeklyFloorReboundPage />}
            {route === "weekly-low-limit-backtest" && <WeeklyLowLimitBacktestPage />}
            {route === "weekly-low-alignment-backtest" && <WeeklyLowAlignmentBacktestPage />}
            {typeof route !== "string" && route.page === "weekly-low-limit-validation" && (
              <WeeklyLowLimitDailyValidationPage
                symbol={route.symbol}
                instrumentToken={route.instrumentToken}
                previousWeekLowDate={route.previousWeekLowDate}
                entryWeekStartDate={route.entryWeekStartDate}
                entryDate={route.entryDate}
              />
            )}
            {route === "weekly-base-definition" && <WeeklyBaseDefinitionPage />}
            {route === "weekly-base-group-backtest" && (
              <WeeklyBaseGroupBacktestPage />
            )}
            {route === "three-week-stock-review" && <ThreeWeekStockReviewPage />}
            {route === "compact-stock-review" && <CompactStockReviewPage />}
            {route === "three-week-watchlist-review" && (
              <ThreeWeekWatchlistReviewPage onOpenStockReview={openStockReview} />
            )}
            {route === "weekly-low-alignment" && (
              <WeeklyLowAlignmentSummaryPage onOpenStockReview={openStockReview} />
            )}
            {route === "volume-event-review" && <VolumeEventReviewPage onOpenStockReview={openStockReview} />}
            {route === "volume-event-confirmation-backtest" && <VolumeEventConfirmationBacktestPage />}
            {route === "weekly-price-watchlist-scanner" && (
              <WeeklyPriceWatchlistScannerPage onOpenStockReview={openStockReview} />
            )}
            {route === "price-acceptance" && <PriceAcceptanceScannerPage />}
            {route === "52w-momentum-rule5" && (
              <FiftyTwoWeekMomentumRule5Page />
            )}
            {route === "csv-backtest" && <CsvBacktestPage />}
            {route === "silent-breakout-backtest" && <SilentBreakoutBacktestPage onOpenStockReview={openStockReview} />}
            {route === "backtest-reviews" && <BacktestReviewsPage />}
            {route === "chartink-evidence" && <ChartinkEvidencePage />}
            {route === "forward-accumulation" && (
              <ForwardAccumulationAnalysisPage onOpenTimeline={openTimeline} />
            )}
            {route === "breakout-tracker" && <BreakoutTrackerPage onOpenStockReview={openStockReview} />}
            {route === "stock-history-download" && <StockHistoryDownloadPage />}
            {route === "netweb-cycle" && <NetwebCycleTrackerPage />}
            {typeof route !== "string" && route.page === "forward-accumulation-timeline" && (
              <ForwardAccumulationTimelinePage
                runId={route.runId}
                symbol={route.symbol}
                chainStartDate={route.chainStartDate}
                chainEndDate={route.chainEndDate}
                onBack={closeTimeline}
              />
            )}
          </Layout.Content>
        </Layout>
      </Layout>
    </ConfigProvider>
  );
}
