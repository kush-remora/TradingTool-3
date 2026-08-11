import { DeleteOutlined, LogoutOutlined } from "@ant-design/icons";
import { Button, Empty, Input, Modal, Table, Typography, message } from "antd";
import type { ColumnsType } from "antd/es/table";
import dayjs from "dayjs";
import { useMemo, useState } from "react";
import { useStockQuotes } from "../hooks/useStockQuotes";
import type { CloseTradeInput, StockQuoteSnapshot, TradeWithTargets } from "../types";
import {
  formatTradeDate,
  getDaysSinceTrade,
  getTradeCurrentPrice,
  getTradePnl,
} from "../pages/paperTradeBook/paperTradeBookUtils";

const { Text } = Typography;

interface PaperTradePositionsProps {
  trades: TradeWithTargets[];
  onClose: (tradeId: number, payload: CloseTradeInput) => Promise<void>;
  onDelete: (tradeId: number) => Promise<void>;
}

interface CloseState {
  tradeId: number;
  symbol: string;
}

interface TradeRow {
  trade: TradeWithTargets;
  quote: StockQuoteSnapshot | undefined;
}

function formatPrice(value: number | null): string {
  return value == null
    ? "—"
    : "₹" + value.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function formatPnl(
  trade: TradeWithTargets,
  currentPrice: number | null,
): { amount: string; percent: string; isProfit: boolean } | null {
  const pnl = getTradePnl(trade, currentPrice);
  if (!pnl) return null;
  return {
    amount: (pnl.isProfit ? "+" : "−") + formatPrice(Math.abs(pnl.pnl)),
    percent: (pnl.isProfit ? "+" : "−") + Math.abs(pnl.pnlPct).toFixed(2) + "%",
    isProfit: pnl.isProfit,
  };
}

export function PaperTradePositions({ trades, onClose, onDelete }: PaperTradePositionsProps) {
  const [closeState, setCloseState] = useState<CloseState | null>(null);
  const [closePrice, setClosePrice] = useState("");
  const [closing, setClosing] = useState(false);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const symbols = useMemo(() => trades.map((row) => row.trade.nse_symbol), [trades]);
  const { quotesBySymbol, loading: quotesLoading } = useStockQuotes(symbols);

  const rows = useMemo<TradeRow[]>(
    () => trades.map((trade) => ({
      trade,
      quote: quotesBySymbol[trade.trade.nse_symbol.toUpperCase()],
    })),
    [quotesBySymbol, trades],
  );

  const openCloseDialog = (row: TradeRow): void => {
    const currentPrice = getTradeCurrentPrice(row.trade, quotesBySymbol);
    setCloseState({ tradeId: row.trade.trade.id, symbol: row.trade.trade.nse_symbol });
    setClosePrice(currentPrice?.toFixed(2) ?? "");
  };

  const submitClose = async (): Promise<void> => {
    if (!closeState) return;
    const parsedPrice = Number.parseFloat(closePrice);
    if (!Number.isFinite(parsedPrice) || parsedPrice <= 0) {
      message.error("Enter a valid exit price");
      return;
    }
    setClosing(true);
    try {
      const payload: CloseTradeInput = {
        close_price: closePrice,
        close_date: dayjs().format("YYYY-MM-DD"),
      };
      await onClose(closeState.tradeId, payload);
      message.success(closeState.symbol + " closed");
      setCloseState(null);
    } catch (error) {
      message.error(error instanceof Error ? error.message : "Failed to close position");
    } finally {
      setClosing(false);
    }
  };

  const confirmDelete = (tradeId: number, symbol: string): void => {
    Modal.confirm({
      title: "Remove " + symbol + " from Trade Book?",
      content: "This paper trade record will be deleted.",
      okText: "Remove",
      okType: "danger",
      onOk: async () => {
        setDeletingId(tradeId);
        try {
          await onDelete(tradeId);
          message.success(symbol + " removed");
        } catch (error) {
          message.error(error instanceof Error ? error.message : "Failed to remove trade");
        } finally {
          setDeletingId(null);
        }
      },
    });
  };

  const columns: ColumnsType<TradeRow> = [
    {
      title: "Stock",
      key: "stock",
      render: (_, row) => (
        <div className="paper-trade-stock-cell">
          <Text strong>{row.trade.trade.nse_symbol}</Text>
          <span>{formatTradeDate(row.trade.trade.trade_date)}</span>
        </div>
      ),
    },
    {
      title: "Entry",
      key: "entry",
      render: (_, row) => formatPrice(Number.parseFloat(row.trade.trade.avg_buy_price)),
    },
    {
      title: "Current",
      key: "current",
      render: (_, row) => row.quote?.ltp == null ? <Text type="secondary">Waiting…</Text> : formatPrice(row.quote.ltp),
    },
    {
      title: "P&L",
      key: "pnl",
      render: (_, row) => {
        const pnl = formatPnl(row.trade, row.quote?.ltp ?? null);
        if (!pnl) return <Text type="secondary">Waiting for quote</Text>;
        return (
          <div className={"paper-trade-pnl " + (pnl.isProfit ? "is-profit" : "is-loss")}>
            <strong>{pnl.amount}</strong>
            <span>{pnl.percent}</span>
          </div>
        );
      },
    },
    {
      title: "Days",
      key: "days",
      render: (_, row) => getDaysSinceTrade(row.trade.trade.trade_date) + "d",
    },
    {
      title: "",
      key: "actions",
      align: "right",
      render: (_, row) => (
        <div className="paper-trade-actions">
          <Button
            type="text"
            size="small"
            icon={<LogoutOutlined />}
            aria-label={"Close " + row.trade.trade.nse_symbol}
            onClick={() => openCloseDialog(row)}
          />
          <Button
            type="text"
            danger
            size="small"
            icon={<DeleteOutlined />}
            aria-label={"Remove " + row.trade.trade.nse_symbol}
            loading={deletingId === row.trade.trade.id}
            onClick={() => confirmDelete(row.trade.trade.id, row.trade.trade.nse_symbol)}
          />
        </div>
      ),
    },
  ];

  if (trades.length === 0) {
    return (
      <div className="paper-trade-empty">
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No open paper positions" />
        <Text type="secondary">Add a stock when your chart reading feels actionable.</Text>
      </div>
    );
  }

  return (
    <>
      <Table<TradeRow>
        className="paper-trade-table"
        columns={columns}
        dataSource={rows}
        rowKey={(row) => row.trade.trade.id}
        pagination={false}
        size="small"
        loading={quotesLoading}
      />
      <Modal
        title={"Close " + (closeState?.symbol ?? "position")}
        open={closeState !== null}
        okText="Close position"
        confirmLoading={closing}
        onCancel={() => setCloseState(null)}
        onOk={() => void submitClose()}
      >
        <label className="paper-trade-close-field">
          <span>Exit price</span>
          <Input prefix="₹" value={closePrice} onChange={(event) => setClosePrice(event.target.value)} />
        </label>
        <Text type="secondary">The current quote is pre-filled when available.</Text>
      </Modal>
    </>
  );
}
