import { Alert, Spin, Tabs } from "antd";
import { useState } from "react";
import { QuickCalculatorTab } from "../components/QuickCalculatorTab";
import { TelegramChatWidget } from "../components/TelegramChatWidget";
import { TradeEntryForm } from "../components/TradeEntryForm";
import { TradeJournalTable } from "../components/TradeJournalTable";
import { useTradeData } from "../hooks/useTradeData";

export function TradePage() {
  const { trades, loading, error, createTrade, deleteTrade } = useTradeData();
  const [submitting, setSubmitting] = useState(false);

  const handleCreateTrade = async (payload) => {
    setSubmitting(true);
    try {
      await createTrade(payload);
    } finally {
      setSubmitting(false);
    }
  };

  if (error) {
    return (
      <Alert
        type="error"
        message="Failed to load trades"
        description={error}
        showIcon
        style={{ margin: "16px" }}
      />
    );
  }

  return (
    <>
      <Spin spinning={loading}>
        <div style={{ padding: "16px" }}>
          <h2>Trade Journal & Calculator</h2>

          <Tabs
            defaultActiveKey="journal"
            items={[
              {
                key: "journal",
                label: "Trade Journal",
                children: (
                  <div style={{ marginTop: "16px" }}>
                    <TradeEntryForm onSubmit={handleCreateTrade} loading={submitting} />
                    <TradeJournalTable trades={trades} onDelete={deleteTrade} />
                  </div>
                ),
              },
              {
                key: "calculator",
                label: "Quick Calculator",
                children: (
                  <div style={{ marginTop: "16px" }}>
                    <QuickCalculatorTab />
                  </div>
                ),
              },
            ]}
          />
        </div>
      </Spin>
      <TelegramChatWidget />
    </>
  );
}
