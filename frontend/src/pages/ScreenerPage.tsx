import { useState } from "react";
import { Segmented, Space } from "antd";
import { FundamentalsScreener } from "../components/FundamentalsScreener";
import { ScreenerOverview } from "../components/ScreenerOverview";

type ScreenerMode = "weekly" | "fundamentals";

export function ScreenerPage() {
  const [mode, setMode] = useState<ScreenerMode>("fundamentals");

  return (
    <Space direction="vertical" size={16} style={{ width: "100%", padding: 24, background: "#f5f7fa", minHeight: "calc(100vh - 48px)" }}>
      <Segmented
        options={[
          { label: "Fundamentals", value: "fundamentals" },
          { label: "Weekly Pattern", value: "weekly" },
        ]}
        value={mode}
        onChange={(value) => {
          setMode(value as ScreenerMode);
        }}
      />
      {mode === "fundamentals" ? <FundamentalsScreener /> : <ScreenerOverview />}
    </Space>
  );
}
