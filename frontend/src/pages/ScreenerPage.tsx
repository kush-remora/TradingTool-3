import { useState } from "react";
import { Segmented, Space } from "antd";
import { FundamentalsScreener } from "../components/FundamentalsScreener";
import { ScreenerOverview } from "../components/ScreenerOverview";
import { ScreenerDetail } from "../components/ScreenerDetail";

type ScreenerMode = "weekly" | "fundamentals";

export function ScreenerPage() {
  const [mode, setMode] = useState<ScreenerMode>("fundamentals");
  const [selectedSymbol, setSelectedSymbol] = useState<string | null>(null);

  if (mode === "weekly" && selectedSymbol) {
    return (
      <ScreenerDetail
        symbol={selectedSymbol}
        onBack={() => setSelectedSymbol(null)}
      />
    );
  }

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
          setSelectedSymbol(null);
        }}
      />
      {mode === "fundamentals" ? <FundamentalsScreener /> : <ScreenerOverview onSelectSymbol={setSelectedSymbol} />}
    </Space>
  );
}
