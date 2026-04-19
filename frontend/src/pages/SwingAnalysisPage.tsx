import { Layout, Space, Typography, message } from "antd";
import { useState, useEffect } from "react";
import { InstrumentSearch } from "../components/InstrumentSearch";
import { SwingVisualizer } from "../components/analysis/SwingVisualizer";
import { useSwingAnalysis } from "../hooks/useSwingAnalysis";
import { TelegramChatWidget } from "../components/TelegramChatWidget";

const { Content } = Layout;
const { Title } = Typography;

export function SwingAnalysisPage() {
  const [symbol, setSymbol] = useState<string>("");
  const [reversal, setReversal] = useState<number>(5.0);
  const { data, loading, error, fetchSwings } = useSwingAnalysis();

  useEffect(() => {
    if (symbol) {
      fetchSwings(symbol, reversal, 365);
    }
  }, [symbol, reversal, fetchSwings]);

  useEffect(() => {
    if (error) {
      message.error(error);
    }
  }, [error]);

  return (
    <Content style={{ padding: "24px", minHeight: "calc(100vh - 64px)", overflowY: "auto" }}>
      <Space direction="vertical" style={{ width: "100%" }} size="large">
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <Title level={2} style={{ margin: 0 }}>Swing Structure Analysis</Title>
          <div style={{ width: 300 }}>
            <InstrumentSearch 
              onSelect={(inst) => setSymbol(inst?.trading_symbol || "")} 
              placeholder="Search stock (e.g. INFY)"
            />
          </div>
        </div>

        <SwingVisualizer 
          data={data} 
          loading={loading} 
          reversal={reversal} 
          onReversalChange={setReversal} 
        />
      </Space>
      <TelegramChatWidget />
    </Content>
  );
}
