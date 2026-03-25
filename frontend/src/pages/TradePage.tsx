import { PlusOutlined } from "@ant-design/icons";
import { Alert, Button, Drawer, Spin, Tabs } from "antd";
import { useState } from "react";
import { QuickCalculatorTab } from "../components/QuickCalculatorTab";
import { TelegramChatWidget } from "../components/TelegramChatWidget";
import { TradeEntryForm } from "../components/TradeEntryForm";
import { TradeJournalTable } from "../components/TradeJournalTable";
import { useTradeData } from "../hooks/useTradeData";

export function TradePage() {
  const { trades, loading, error, createTrade, deleteTrade } = useTradeData();
  const [submitting, setSubmitting] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);

  const handleCreateTrade = async (payload: any) => {
    setSubmitting(true);
    try {
      await createTrade(payload);
      setDrawerOpen(false);
    } finally {
      setSubmitting(false);
    }
  };

  if (error) {
    return (
      <Alert
        type="error"
        title="Failed to load trades"
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
                label: "Trade Book",
                children: (
                  <div style={{ marginTop: "16px" }}>
                    <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 12, alignItems: "center" }}>
                      <h3 style={{ margin: 0 }}>Active Trades</h3>
                      <Button type="primary" size="small" icon={<PlusOutlined />} onClick={() => setDrawerOpen(true)}>
                        Add Trade
                      </Button>
                    </div>
                    <Drawer
                      title="Add New Trade"
                      placement="right"
                      onClose={() => setDrawerOpen(false)}
                      open={drawerOpen}
                      width={380}
                      styles={{ body: { paddingBottom: 80 } }}
                    >
                      <TradeEntryForm onSubmit={handleCreateTrade} loading={submitting} />
                    </Drawer>
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
