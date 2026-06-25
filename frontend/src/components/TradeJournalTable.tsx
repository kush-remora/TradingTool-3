import { CloseCircleOutlined, DeleteOutlined, LogoutOutlined } from "@ant-design/icons";
import { Button, DatePicker, Form, Input, Modal, Space, Table, Tag, Tooltip, Typography, message } from "antd";
import type { ColumnType } from "antd/es/table";
import dayjs from "dayjs";
import { useMemo, useState } from "react";
import { useLiveMarketData } from "../hooks/useLiveMarketData";
import { useStockQuotes } from "../hooks/useStockQuotes";
import type { CloseTradeInput, GttTarget, TradeWithTargets } from "../types";
import { calculatePnL, type PnLResult } from "../utils/pnlUtils";
import { deriveSignalConfigFromTrade } from "../utils/tradeSessionSignals";
import { TradeMarketHistoryPanel } from "./TradeMarketHistoryPanel";
import { renderLiveMarketCell } from "./liveMarketCell";

const { Text } = Typography;

interface TradeJournalTableProps {
  trades: TradeWithTargets[];
  onClose: (tradeId: number, payload: CloseTradeInput) => Promise<void>;
  onDelete: (tradeId: number) => Promise<void>;
  loading?: boolean;
  isClosed?: boolean;
}

interface CloseModalState {
  tradeId: number;
  symbol: string;
  avgBuyPrice: string;
}

interface EnrichedTrade extends TradeWithTargets {
  dayLow: number | null;
  dayHigh: number | null;
  closedPnlData: PnLResult | null;
}

interface CurrentPnlTagProps {
  avgBuyPrice: string;
  quantity: number;
  symbol: string;
  snapshotLtp: number | null;
}

function formatPrice(value: number | null | undefined): string {
  if (value === null || value === undefined) return "-";
  return `₹${value.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function getWeekdayLabel(date: string): string {
  const [year, month, day] = date.split("-").map((part) => Number.parseInt(part, 10));
  if (!year || !month || !day) return "";
  const weekday = new Date(Date.UTC(year, month - 1, day)).getUTCDay();
  const labels = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"] as const;
  return labels[weekday] ?? "";
}

function CurrentPnlTag({
  avgBuyPrice,
  quantity,
  symbol,
  snapshotLtp,
}: CurrentPnlTagProps) {
  const liveMarketData = useLiveMarketData(`NSE:${symbol}`);
  const pnlData = calculatePnL(avgBuyPrice, liveMarketData?.ltp ?? snapshotLtp, quantity);

  if (!pnlData) {
    return <Text type="secondary">-</Text>;
  }

  const { pnl, pnlPct, isProfit } = pnlData;
  const color = isProfit ? "#00b386" : "#eb3a3a";
  const bg = isProfit ? "#f0fdf4" : "#fff1f0";
  const border = isProfit ? "#bbf7d0" : "#ffa39e";

  return (
    <Tag
      style={{
        background: bg,
        borderColor: border,
        color,
        fontWeight: 700,
        fontSize: 12,
        borderRadius: 6,
        padding: "2px 10px",
      }}
    >
      {isProfit ? "+" : ""}₹{Math.abs(pnl).toLocaleString("en-IN", { maximumFractionDigits: 0 })}
      &nbsp;({isProfit ? "+" : ""}{pnlPct.toFixed(1)}%)
    </Tag>
  );
}

export function TradeJournalTable({
  trades,
  onClose,
  onDelete,
  loading = false,
  isClosed = false,
}: TradeJournalTableProps) {
  const [closeModal, setCloseModal] = useState<CloseModalState | null>(null);
  const [closePrice, setClosePrice] = useState("");
  const [closeDate, setCloseDate] = useState(dayjs().format("YYYY-MM-DD"));
  const [closing, setClosing] = useState(false);
  const [expandedNotes, setExpandedNotes] = useState<Set<number>>(new Set());
  const [expandedHistoryRows, setExpandedHistoryRows] = useState<number[]>([]);

  const openSymbols = useMemo(
    () => trades.filter((item) => !item.trade.close_price).map((item) => item.trade.nse_symbol),
    [trades],
  );
  const { quotesBySymbol } = useStockQuotes(openSymbols);

  const enrichedTrades = useMemo<EnrichedTrade[]>(() => {
    return trades.map((tradeRow) => {
      const quote = quotesBySymbol[tradeRow.trade.nse_symbol.toUpperCase()];
      const closedPriceNum = parseFloat(tradeRow.trade.close_price ?? "0");
      return {
        ...tradeRow,
        dayLow: quote?.day_low ?? null,
        dayHigh: quote?.day_high ?? null,
        closedPnlData: calculatePnL(tradeRow.trade.avg_buy_price, closedPriceNum || null, tradeRow.trade.quantity),
      };
    });
  }, [trades, quotesBySymbol]);

  const handleDeleteConfirm = (tradeId: number) => {
    Modal.confirm({
      title: "Remove this trade?",
      content: "This will permanently delete the record.",
      okText: "Remove",
      okType: "danger",
      onOk: async () => {
        try {
          await onDelete(tradeId);
          message.success("Trade removed");
        } catch (e) {
          message.error(e instanceof Error ? e.message : "Failed to delete");
        }
      },
    });
  };

  const handleCloseSubmit = async () => {
    if (!closeModal) return;
    const price = parseFloat(closePrice);
    if (Number.isNaN(price) || price <= 0) {
      message.error("Enter a valid close price");
      return;
    }
    setClosing(true);
    try {
      await onClose(closeModal.tradeId, { close_price: closePrice, close_date: closeDate });
      message.success(`${closeModal.symbol} position closed`);
      setCloseModal(null);
      setClosePrice("");
    } catch (e) {
      message.error(e instanceof Error ? e.message : "Failed to close position");
    } finally {
      setClosing(false);
    }
  };

  const openColumns: ColumnType<EnrichedTrade>[] = [
    {
      title: <Text style={{ fontSize: 11, fontWeight: 700, letterSpacing: 0.5, color: "#8c8c8c" }}>STOCK</Text>,
      key: "stock",
      width: 150,
      fixed: "left" as const,
      render: (_, record) => (
        <div>
          <Text strong style={{ fontSize: 14, color: "#1a1a2e" }}>{record.trade.nse_symbol}</Text>
          <div>
            <Text type="secondary" style={{ fontSize: 11 }}>
              {getWeekdayLabel(record.trade.trade_date)} · {record.trade.trade_date}
            </Text>
          </div>
        </div>
      ),
    },
    {
      title: <Text style={{ fontSize: 11, fontWeight: 700, letterSpacing: 0.5, color: "#8c8c8c" }}>POSITION</Text>,
      key: "position",
      width: 165,
      render: (_, record) => {
        const totalInvested = parseFloat(record.total_invested);
        return (
          <div>
            <Text style={{ fontSize: 13, fontWeight: 600, color: "#1a1a2e" }}>
              ₹{totalInvested.toLocaleString("en-IN", { maximumFractionDigits: 0 })}
            </Text>
            <div>
              <Text type="secondary" style={{ fontSize: 11 }}>
                {record.trade.quantity} × ₹{record.trade.avg_buy_price}
              </Text>
            </div>
          </div>
        );
      },
    },
    {
      title: <Text style={{ fontSize: 11, fontWeight: 700, letterSpacing: 0.5, color: "#8c8c8c" }}>LIVE MARKET</Text>,
      key: "liveMarket",
      width: 190,
      render: (_, record) => renderLiveMarketCell({
        symbol: record.trade.nse_symbol,
        snapshot: quotesBySymbol[record.trade.nse_symbol.toUpperCase()],
        fallbackLtp: quotesBySymbol[record.trade.nse_symbol.toUpperCase()]?.ltp ?? null,
        mode: "wide",
      }),
    },
    {
      title: <Text style={{ fontSize: 11, fontWeight: 700, letterSpacing: 0.5, color: "#8c8c8c" }}>CURRENT P&amp;L</Text>,
      key: "current_pnl",
      width: 160,
      render: (_, record) => {
        return (
          <CurrentPnlTag
            avgBuyPrice={record.trade.avg_buy_price}
            quantity={record.trade.quantity}
            symbol={record.trade.nse_symbol}
            snapshotLtp={quotesBySymbol[record.trade.nse_symbol.toUpperCase()]?.ltp ?? null}
          />
        );
      },
    },
    {
      title: <Text style={{ fontSize: 11, fontWeight: 700, letterSpacing: 0.5, color: "#8c8c8c" }}>ENTRY DAY LOW</Text>,
      key: "entry_day_low",
      width: 125,
      render: (_, record) => {
        const low = record.trade.today_low;
        if (!low) return <Text type="secondary">-</Text>;
        return <Text style={{ fontWeight: 500, color: "#595959" }}>₹{low}</Text>;
      },
    },
    {
      title: <Text style={{ fontSize: 11, fontWeight: 700, letterSpacing: 0.5, color: "#8c8c8c" }}>STOP LOSS</Text>,
      key: "stop_loss",
      width: 165,
      render: (_, record) => {
        const totalInvested = parseFloat(record.total_invested);
        const slPct = parseFloat(record.trade.stop_loss_percent);
        const riskAmount = (totalInvested * slPct) / 100;
        return (
          <div>
            <Tag
              style={{
                background: "#fff1f0",
                borderColor: "#ffa39e",
                color: "#cf1322",
                fontWeight: 600,
                fontSize: 12,
                borderRadius: 6,
                padding: "1px 8px",
              }}
            >
              ₹{record.trade.stop_loss_price} &nbsp;·&nbsp; -{record.trade.stop_loss_percent}%
            </Tag>
            <div style={{ marginTop: 3 }}>
              <Text style={{ fontSize: 11, color: "#eb3a3a" }}>
                Risk: ₹{riskAmount.toLocaleString("en-IN", { maximumFractionDigits: 0 })}
              </Text>
            </div>
          </div>
        );
      },
    },
    {
      title: <Text style={{ fontSize: 11, fontWeight: 700, letterSpacing: 0.5, color: "#8c8c8c" }}>TARGETS</Text>,
      dataIndex: "gtt_targets",
      key: "gtt_targets",
      width: 310,
      render: (targets: GttTarget[]) => {
        const greenShades = ["#f6ffed", "#d9f7be", "#b7eb8f", "#95de64", "#73d13d", "#52c41a", "#389e0d"];
        const textShades = ["#389e0d", "#389e0d", "#237804", "#237804", "#135200", "#135200", "#092b00"];
        return (
          <div>
            <Space wrap size={4}>
              {targets.map((target, i) => {
                const shade = Math.min(i, greenShades.length - 1);
                return (
                  <Tag
                    key={target.percent}
                    style={{
                      background: greenShades[shade],
                      borderColor: "#b7eb8f",
                      color: textShades[shade],
                      fontWeight: 600,
                      fontSize: 11,
                      borderRadius: 6,
                      padding: "1px 7px",
                      margin: 0,
                    }}
                  >
                    +{target.percent}% ₹{target.price}
                  </Tag>
                );
              })}
            </Space>
            <div style={{ marginTop: 4 }}>
              <Text type="secondary" style={{ fontSize: 11 }}>
                Strategy swing can be based on Entry Day Low or Avg Entry
              </Text>
            </div>
          </div>
        );
      },
    },
    {
      title: <Text style={{ fontSize: 11, fontWeight: 700, letterSpacing: 0.5, color: "#8c8c8c" }}>NOTES</Text>,
      key: "notes",
      width: 150,
      render: (_, record) => {
        const notes = record.trade.notes;
        if (!notes) return <Text type="secondary">-</Text>;
        const isExpanded = expandedNotes.has(record.trade.id);
        return (
          <Tooltip title={notes}>
            <span
              onClick={() => {
                const next = new Set(expandedNotes);
                if (isExpanded) {
                  next.delete(record.trade.id);
                } else {
                  next.add(record.trade.id);
                }
                setExpandedNotes(next);
              }}
              style={{ cursor: "pointer", color: "#595959", fontSize: 12 }}
            >
              {isExpanded ? notes : notes.substring(0, 40)}
              {notes.length > 40 && !isExpanded && (
                <Text style={{ color: "#00b386", fontSize: 11, marginLeft: 2 }}>more</Text>
              )}
            </span>
          </Tooltip>
        );
      },
    },
    {
      title: "",
      key: "action",
      width: 170,
      render: (_, record) => (
        <Space size={2} wrap>
          <Button
            size="small"
            type={expandedHistoryRows.includes(record.trade.id) ? "default" : "text"}
            onClick={() =>
              setExpandedHistoryRows((prev) =>
                prev.includes(record.trade.id)
                  ? prev.filter((id) => id !== record.trade.id)
                  : [...prev, record.trade.id],
              )
            }
          >
            10D Context
          </Button>
          <Tooltip title="Close position">
            <Button
              type="text"
              size="small"
              icon={<LogoutOutlined style={{ color: "#00b386" }} />}
              onClick={() =>
                setCloseModal({
                  tradeId: record.trade.id,
                  symbol: record.trade.nse_symbol,
                  avgBuyPrice: record.trade.avg_buy_price,
                })
              }
            />
          </Tooltip>
          <Tooltip title="Delete trade">
            <Button
              type="text"
              danger
              size="small"
              icon={<DeleteOutlined />}
              onClick={() => handleDeleteConfirm(record.trade.id)}
              loading={loading}
            />
          </Tooltip>
        </Space>
      ),
    },
  ];

  const closedColumns: ColumnType<EnrichedTrade>[] = [
    {
      title: <Text style={{ fontSize: 11, fontWeight: 700, letterSpacing: 0.5, color: "#8c8c8c" }}>STOCK</Text>,
      key: "stock",
      width: 150,
      fixed: "left" as const,
      render: (_, record) => (
        <div>
          <Text strong style={{ fontSize: 14, color: "#595959" }}>{record.trade.nse_symbol}</Text>
          <div>
            <Text type="secondary" style={{ fontSize: 11 }}>
              Opened {getWeekdayLabel(record.trade.trade_date)} · {record.trade.trade_date}
            </Text>
          </div>
        </div>
      ),
    },
    {
      title: <Text style={{ fontSize: 11, fontWeight: 700, letterSpacing: 0.5, color: "#8c8c8c" }}>POSITION</Text>,
      key: "position",
      width: 165,
      render: (_, record) => (
        <div>
          <Text style={{ fontSize: 13, fontWeight: 600, color: "#595959" }}>
            {record.trade.quantity} × ₹{record.trade.avg_buy_price}
          </Text>
          <div>
            <Text type="secondary" style={{ fontSize: 11 }}>
              ₹{parseFloat(record.total_invested).toLocaleString("en-IN", { maximumFractionDigits: 0 })}
            </Text>
          </div>
        </div>
      ),
    },
    {
      title: <Text style={{ fontSize: 11, fontWeight: 700, letterSpacing: 0.5, color: "#8c8c8c" }}>EXIT PRICE</Text>,
      key: "exit_price",
      width: 130,
      render: (_, record) => (
        <div>
          <Text style={{ fontWeight: 600, color: "#1a1a2e" }}>₹{record.trade.close_price}</Text>
          <div>
            <Text type="secondary" style={{ fontSize: 11 }}>{record.trade.close_date}</Text>
          </div>
        </div>
      ),
    },
    {
      title: <Text style={{ fontSize: 11, fontWeight: 700, letterSpacing: 0.5, color: "#8c8c8c" }}>P&amp;L</Text>,
      key: "pnl",
      width: 160,
      render: (_, record) => {
        if (!record.closedPnlData) return <Text type="secondary">-</Text>;
        
        const { pnl, pnlPct, isProfit } = record.closedPnlData;
        const color = isProfit ? "#00b386" : "#eb3a3a";
        const bg = isProfit ? "#f0fdf4" : "#fff1f0";
        const border = isProfit ? "#bbf7d0" : "#ffa39e";
        return (
          <Tag
            style={{
              background: bg,
              borderColor: border,
              color,
              fontWeight: 700,
              fontSize: 12,
              borderRadius: 6,
              padding: "2px 10px",
            }}
          >
            {isProfit ? "+" : ""}₹{Math.abs(pnl).toLocaleString("en-IN", { maximumFractionDigits: 0 })}
            &nbsp;({isProfit ? "+" : ""}{pnlPct.toFixed(1)}%)
          </Tag>
        );
      },
    },
    {
      title: <Text style={{ fontSize: 11, fontWeight: 700, letterSpacing: 0.5, color: "#8c8c8c" }}>NOTES</Text>,
      key: "notes",
      width: 150,
      render: (_, record) => {
        const notes = record.trade.notes;
        if (!notes) return <Text type="secondary">-</Text>;
        return (
          <Tooltip title={notes}>
            <Text style={{ color: "#595959", fontSize: 12 }}>
              {notes.substring(0, 50)}{notes.length > 50 && "..."}
            </Text>
          </Tooltip>
        );
      },
    },
    {
      title: "",
      key: "action",
      width: 48,
      render: (_, record) => (
        <Tooltip title="Delete record">
          <Button
            type="text"
            danger
            size="small"
            icon={<DeleteOutlined />}
            onClick={() => handleDeleteConfirm(record.trade.id)}
          />
        </Tooltip>
      ),
    },
  ];

  const avgBuyNum = closeModal ? parseFloat(closeModal.avgBuyPrice) : 0;
  const closePriceNum = parseFloat(closePrice);
  const modalPnl = closeModal && !Number.isNaN(closePriceNum) && closePriceNum > 0
    ? closePriceNum - avgBuyNum
    : null;

  return (
    <>
      <Table<EnrichedTrade>
        columns={isClosed ? closedColumns : openColumns}
        dataSource={enrichedTrades}
        rowKey={(r) => r.trade.id}
        size="small"
        scroll={{ x: 1200 }}
        pagination={{ pageSize: 20, showSizeChanger: true, size: "small" }}
        onRow={() => ({ style: { verticalAlign: "top" } })}
        expandable={
          isClosed
            ? undefined
            : {
                expandedRowKeys: expandedHistoryRows,
                showExpandColumn: false,
                expandedRowRender: (record) => (
                  <TradeMarketHistoryPanel
                    symbol={record.trade.nse_symbol}
                    days={10}
                    defaultExpanded
                    showToggle={false}
                    title="10D Context"
                    signalConfig={deriveSignalConfigFromTrade(record.trade) ?? undefined}
                    showSignalLegend
                  />
                ),
              }
        }
      />

      <Modal
        title={
          <span>
            <CloseCircleOutlined style={{ color: "#00b386", marginRight: 8 }} />
            Close position - {closeModal?.symbol}
          </span>
        }
        open={!!closeModal}
        onCancel={() => {
          setCloseModal(null);
          setClosePrice("");
        }}
        onOk={handleCloseSubmit}
        okText="Close Position"
        okButtonProps={{
          style: { background: "#00b386", borderColor: "#00b386" },
          loading: closing,
        }}
        destroyOnClose
      >
        <Form layout="vertical" size="small" style={{ marginTop: 16 }}>
          <Form.Item label="Exit / Sell Price (₹)" required>
            <Input
              type="number"
              step="0.01"
              placeholder="e.g. 3350.00"
              value={closePrice}
              onChange={(e) => setClosePrice(e.target.value)}
              autoFocus
              prefix="₹"
            />
          </Form.Item>
          <Form.Item label="Close Date">
            <DatePicker
              style={{ width: "100%" }}
              value={dayjs(closeDate)}
              onChange={(d) => setCloseDate(d?.format("YYYY-MM-DD") ?? closeDate)}
            />
          </Form.Item>
        </Form>

        {modalPnl !== null && (
          <div
            style={{
              padding: "10px 14px",
              background: modalPnl >= 0 ? "#f0fdf4" : "#fff1f0",
              border: `1px solid ${modalPnl >= 0 ? "#bbf7d0" : "#ffa39e"}`,
              borderRadius: 8,
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
              marginTop: 4,
            }}
          >
            <Text style={{ fontSize: 12, color: "#595959" }}>
              P&L per unit
            </Text>
            <Text
              style={{
                fontWeight: 700,
                fontSize: 16,
                color: modalPnl >= 0 ? "#00b386" : "#eb3a3a",
              }}
            >
              {modalPnl >= 0 ? "+" : ""}₹{Math.abs(modalPnl).toFixed(2)}
            </Text>
          </div>
        )}
      </Modal>
    </>
  );
}
