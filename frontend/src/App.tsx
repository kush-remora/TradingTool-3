import { BarChartOutlined, BookOutlined, FundOutlined, ThunderboltOutlined, UnorderedListOutlined } from "@ant-design/icons";
import { ConfigProvider, Layout, Menu } from "antd";
import type { MenuProps } from "antd";
import { useState } from "react";

import { WyckoffPhase1Page } from "./pages/WyckoffPhase1Page";
import { RemoraPage } from "./pages/RemoraPage";
import { TradePage } from "./pages/TradePage";

type V1PageKey =
  | "trade"
  | "remora"
  | "wyckoff-phase1";

type PageKey = V1PageKey;

const menuItems: MenuProps["items"] = [
  { key: "wyckoff-phase1", label: "Wyckoff Phase-1", icon: <BarChartOutlined /> },
  { key: "trade", label: "Trade Journal", icon: <BookOutlined /> },
  { key: "remora", label: "Remora", icon: <FundOutlined /> },
];

const validPages: PageKey[] = [
  "trade",
  "remora",
  "wyckoff-phase1",
];

export default function App() {
  const getInitialRoute = (): PageKey => {
    const path = window.location.pathname;
    const baseUrl = import.meta.env.BASE_URL;
    const internalPath = path.startsWith(baseUrl) ? path.slice(baseUrl.length) : path;

    const cleanPath = internalPath.replace(/^\//, "").replace(/\/+$/, "");
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

            {route === "trade" && <TradePage />}
            {route === "remora" && <RemoraPage />}
            {route === "wyckoff-phase1" && <WyckoffPhase1Page />}
          </Layout.Content>
        </Layout>
      </Layout>
    </ConfigProvider>
  );
}
