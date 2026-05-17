import { useState } from "react";
import { Segmented, Space } from "antd";
import { BollingerSqueezeScreener } from "../components/BollingerSqueezeScreener";
import { BollingerSqueezeTracker } from "../components/BollingerSqueezeTracker";

type PageMode = "screener" | "tracker";

export function BollingerSqueezePage() {
  const [mode, setMode] = useState<PageMode>("screener");

  return (
    <Space direction="vertical" size={16} style={{ width: "100%", padding: 24, background: "#f5f7fa", minHeight: "calc(100vh - 48px)" }}>
      <Segmented
        options={[
          { label: "Screener", value: "screener" },
          { label: "Tracker", value: "tracker" },
        ]}
        value={mode}
        onChange={(value) => setMode(value as PageMode)}
      />
      {mode === "screener" ? <BollingerSqueezeScreener /> : <BollingerSqueezeTracker />}
    </Space>
  );
}
