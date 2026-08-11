import { BookOutlined, PlusOutlined } from "@ant-design/icons";
import { Alert, Button, Drawer, Spin, Typography } from "antd";
import { useState } from "react";
import { PaperTradeEntryForm } from "../components/PaperTradeEntryForm";
import { PaperTradePositions } from "../components/PaperTradePositions";
import { useTradeData } from "../hooks/useTradeData";
import type { CreateTradeInput, TradeWithTargets } from "../types";
import { calculatePnL } from "../utils/pnlUtils";
import {
  formatTradeDate,
  getDaysSinceTrade,
} from "./paperTradeBook/paperTradeBookUtils";

const { Text } = Typography;

function formatPrice(value: number): string {
  return "₹" + value.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function ClosedPaperTrades({ trades }: { trades: TradeWithTargets[] }) {
  if (trades.length === 0) return null;

  return (
    <details className="paper-trade-closed">
      <summary>
        <span>Closed paper trades</span>
        <Text type="secondary">{trades.length}</Text>
      </summary>
      <div className="paper-trade-closed-list">
        {trades.map((row) => {
          const entry = Number.parseFloat(row.trade.avg_buy_price);
          const exit = Number.parseFloat(row.trade.close_price ?? "0");
          const pnl = calculatePnL(row.trade.avg_buy_price, exit, row.trade.quantity);
          const heldDays = row.trade.close_date
            ? getDaysSinceTrade(row.trade.trade_date, row.trade.close_date)
            : 0;
          return (
            <div className="paper-trade-closed-row" key={row.trade.id}>
              <div>
                <strong>{row.trade.nse_symbol}</strong>
                <span>{formatTradeDate(row.trade.trade_date)}</span>
              </div>
              <span>{formatPrice(entry)} → {formatPrice(exit)}</span>
              <span className={pnl?.isProfit ? "is-profit" : "is-loss"}>
                {pnl ? (pnl.isProfit ? "+" : "−") + Math.abs(pnl.pnlPct).toFixed(2) + "%" : "—"}
              </span>
              <span>{heldDays}d</span>
            </div>
          );
        })}
      </div>
    </details>
  );
}

export function TradePage() {
  const { trades, loading, error, createTrade, closeTrade, deleteTrade } = useTradeData();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const openTrades = trades.filter((row) => !row.trade.close_price);
  const closedTrades = trades.filter((row) => Boolean(row.trade.close_price));

  const handleCreateTrade = async (payload: CreateTradeInput): Promise<void> => {
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
        message="Failed to load Trade Book"
        description={error}
        showIcon
        style={{ margin: 16 }}
      />
    );
  }

  return (
    <Spin spinning={loading}>
      <main className="paper-trade-page">
        <header className="paper-trade-header">
          <div className="paper-trade-title">
            <div className="paper-trade-eyebrow"><BookOutlined /> PAPER TRADING</div>
            <h1>Trade Book</h1>
            <p>Track the chart reads you acted on. One share, one entry price, no noise.</p>
          </div>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setDrawerOpen(true)}
          >
            Add paper trade
          </Button>
        </header>

        <section className="paper-trade-card">
          <div className="paper-trade-card-header">
            <div>
              <h2>Open positions</h2>
              <span>{openTrades.length === 1 ? "1 position" : openTrades.length + " positions"} · live P&L</span>
            </div>
            <Text type="secondary">Default: 1 share · 5% stop</Text>
          </div>
          <PaperTradePositions
            trades={openTrades}
            onClose={closeTrade}
            onDelete={deleteTrade}
          />
        </section>

        <ClosedPaperTrades trades={closedTrades} />
      </main>

      <Drawer
        title={<span className="paper-trade-drawer-title">Add paper trade</span>}
        placement="right"
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        size={390}
      >
        <PaperTradeEntryForm onSubmit={handleCreateTrade} loading={submitting} />
      </Drawer>
    </Spin>
  );
}
