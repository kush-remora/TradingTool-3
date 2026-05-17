import {
  DeleteOutlined,
  PlusOutlined,
  ReloadOutlined,
  InfoCircleOutlined,
} from "@ant-design/icons";
import {
  Button,
  Card,
  DatePicker,
  Form,
  InputNumber,
  Space,
  Table,
  Typography,
  message,
  Tag,
  Select,
  Tooltip,
} from "antd";
import type { TableColumnsType } from "antd";
import { useEffect, useState } from "react";
import { postJson, getJson, deleteJson } from "../utils/api";
import { InstrumentSearch } from "./InstrumentSearch";
import type {
  InstrumentSearchResult,
  SqueezePositionInput,
  SqueezeTrackResponse,
  SqueezeTrackResult,
  TradeWithTargets,
} from "../types";

const { Text, Title } = Typography;

interface TrackerRow extends SqueezeTrackResult {
  tradeId?: number;
}

interface AddTradeFormValues {
  buyDate: { format: (pattern: string) => string };
  buyPrice: number;
  quantity: number;
  strategy: string;
}

export function BollingerSqueezeTracker() {
  const [results, setResults] = useState<TrackerRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [form] = Form.useForm();
  const [selectedInstrument, setSelectedInstrument] = useState<InstrumentSearchResult | null>(null);

  useEffect(() => {
    void fetchAndTrack();
  }, []);

  const fetchAndTrack = async () => {
    setLoading(true);
    try {
      // 1. Fetch all open trades from DB
      const tradesResponse = await getJson<TradeWithTargets[]>("/api/trades");
      const openTrades = tradesResponse.filter(t => t.trade.close_price == null);
      
      if (openTrades.length === 0) {
        setResults([]);
        return;
      }

      // 2. Convert to SqueezePositionInput for tracking calculation
      const positions: SqueezePositionInput[] = openTrades.map(t => ({
        symbol: t.trade.nse_symbol,
        buyDate: t.trade.trade_date,
        buyPrice: parseFloat(t.trade.avg_buy_price)
      })).filter((pos) => Number.isFinite(pos.buyPrice) && pos.buyPrice > 0);

      // 3. Get tracked metrics from backend
      const trackingResponse = await postJson<SqueezeTrackResponse>("/api/screener/bollinger-squeeze/track", positions);
      
      // 4. Enrich tracking results with DB trade IDs for deletion
      const enrichedResults: TrackerRow[] = trackingResponse.results.map((res) => {
          const originalTrade = openTrades.find(t => t.trade.nse_symbol === res.symbol && t.trade.trade_date === res.buyDate);
          return { ...res, tradeId: originalTrade?.trade.id };
      });

      setResults(enrichedResults);
    } catch (err) {
      console.error(err);
      message.error("Failed to fetch tracker data");
    } finally {
      setLoading(false);
    }
  };

  const onAdd = async (values: AddTradeFormValues) => {
    if (!selectedInstrument) {
      message.error("Please select a stock");
      return;
    }
    
    const payload = {
        instrument_token: selectedInstrument.instrument_token,
        company_name: selectedInstrument.company_name,
        exchange: selectedInstrument.exchange,
        nse_symbol: selectedInstrument.trading_symbol,
        quantity: values.quantity,
        avg_buy_price: values.buyPrice.toString(),
        stop_loss_percent: "5.0", // Default, will be recalculated by tracker
        trade_date: values.buyDate.format("YYYY-MM-DD"),
        strategy: values.strategy
    };

    try {
        await postJson("/api/trades", payload);
        message.success(`Added ${payload.nse_symbol} to Trades`);
        form.resetFields();
        setSelectedInstrument(null);
        void fetchAndTrack();
    } catch (err) {
        message.error("Failed to save trade");
    }
  };

  const removePosition = async (tradeId: number | undefined, symbol: string) => {
    if (tradeId == null) {
      message.error(`Cannot remove ${symbol}: trade id is missing`);
      return;
    }
    try {
        await deleteJson(`/api/trades/${tradeId}`);
        message.info(`Removed ${symbol}`);
        void fetchAndTrack();
    } catch (err) {
        message.error("Failed to remove trade");
    }
  };

  const columns: TableColumnsType<TrackerRow> = [
    {
      title: "Stock",
      key: "symbol",
      width: 150,
      fixed: "left",
      render: (_, row) => (
        <Space orientation="vertical" size={0}>
          <Text strong>{row.symbol}</Text>
          <Text type="secondary" style={{ fontSize: 11 }}>{row.companyName}</Text>
        </Space>
      ),
    },
    {
      title: "Entry Details",
      key: "entry",
      width: 180,
      render: (_, row) => (
        <Space orientation="vertical" size={0}>
          <Text style={{ fontSize: 12 }}>Date: <Text strong>{row.buyDate}</Text></Text>
          <Text style={{ fontSize: 12 }}>Price: <Text strong>₹{row.buyPrice.toLocaleString()}</Text></Text>
        </Space>
      ),
    },
    {
      title: "Current Status",
      key: "current",
      width: 180,
      render: (_, row) => (
        <Space orientation="vertical" size={0}>
          <Text style={{ fontSize: 12 }}>LTP: <Text strong>₹{row.ltp.toLocaleString()}</Text></Text>
          <Text style={{ fontSize: 12 }}>Profit: <Text strong style={{ color: row.profitPct >= 0 ? "#389e0d" : "#cf1322" }}>{row.profitPct.toFixed(2)}%</Text></Text>
        </Space>
      ),
    },
    {
        title: "Max DD",
        dataIndex: "maxDrawdownPct",
        width: 100,
        render: (val) => <Text type="danger">{val.toFixed(2)}%</Text>,
    },
    {
      title: "Current Phase",
      dataIndex: "currentPhase",
      width: 150,
      render: (phase) => {
        let color = "default";
        if (phase.includes("Safety")) color = "blue";
        if (phase.includes("Protection")) color = "cyan";
        if (phase.includes("Profit")) color = "green";
        return <Tag color={color} style={{ fontWeight: "bold" }}>{phase}</Tag>;
      },
    },
    {
      title: (
        <Space>
          Required SL (GTT)
          <Tooltip title="Safety: 5-day structural low. Protection: Break-even (+2% profit). Profit: Yesterday's Low (new high reached).">
            <InfoCircleOutlined style={{ color: "#8c8c8c" }} />
          </Tooltip>
        </Space>
      ),
      dataIndex: "requiredSl",
      width: 180,
      render: (sl) => <Text strong style={{ color: "#cf1322", fontSize: 16 }}>₹{sl.toLocaleString()}</Text>,
    },
    {
      title: "Context",
      key: "context",
      width: 220,
      render: (_, row) => (
        <Space orientation="vertical" size={0}>
          <Text style={{ fontSize: 11 }}>RSI: {row.todayRsi?.toFixed(2)} (Max: {row.maxRsi1y?.toFixed(2)})</Text>
          <Text style={{ fontSize: 11 }}>BB: [{row.bbLower.toFixed(0)}, {row.bbUpper.toFixed(0)}]</Text>
        </Space>
      ),
    },
    {
      title: "Action",
      key: "action",
      width: 100,
      render: (_, row) => (
        <Button 
          type="text" 
          danger 
          icon={<DeleteOutlined />} 
          onClick={() => removePosition(row.tradeId, row.symbol)} 
        />
      ),
    },
  ];

  return (
    <div style={{ height: "100%", display: "flex", flexDirection: "column", gap: 16 }}>
      <Card size="small" title="Add New Position (Saves to DB)" style={{ borderRadius: 8 }}>
        <Form form={form} layout="vertical" onFinish={onAdd} initialValues={{ quantity: 1, strategy: "BOLLINGER_SQUEEZE" }}>
          <Space wrap align="end">
            <Form.Item label="Stock" name="symbol" style={{ width: 250, marginBottom: 8 }}>
                <InstrumentSearch onSelect={setSelectedInstrument} value={selectedInstrument} />
            </Form.Item>
            <Form.Item label="Buy Date" name="buyDate" rules={[{ required: true, message: "Required" }]} style={{ marginBottom: 8 }}>
                <DatePicker style={{ width: 140 }} />
            </Form.Item>
            <Form.Item label="Buy Price" name="buyPrice" rules={[{ required: true, message: "Required" }]} style={{ marginBottom: 8 }}>
                <InputNumber style={{ width: 110 }} min={0.01} />
            </Form.Item>
            <Form.Item label="Qty" name="quantity" rules={[{ required: true }]} style={{ marginBottom: 8 }}>
                <InputNumber style={{ width: 70 }} min={1} />
            </Form.Item>
            <Form.Item label="Strategy" name="strategy" style={{ marginBottom: 8 }}>
                <Select style={{ width: 180 }}>
                    <Select.Option value="BOLLINGER_SQUEEZE">Bollinger Squeeze</Select.Option>
                    <Select.Option value="MEAN_REVERSION">Mean Reversion</Select.Option>
                    <Select.Option value="MOMENTUM">Momentum</Select.Option>
                    <Select.Option value="OTHER">Other</Select.Option>
                </Select>
            </Form.Item>
            <Form.Item style={{ marginBottom: 8 }}>
                <Button type="primary" icon={<PlusOutlined />} htmlType="submit">Add Trade</Button>
            </Form.Item>
            <Form.Item style={{ marginBottom: 8 }}>
                <Button icon={<ReloadOutlined />} onClick={fetchAndTrack} loading={loading}>Refresh All</Button>
            </Form.Item>
          </Space>
        </Form>
      </Card>

      <div style={{ background: "#fff", borderRadius: 12, padding: "0px", flex: 1, display: "flex", flexDirection: "column" }}>
        <div style={{ padding: "16px 20px" }}>
          <Title level={5} style={{ margin: 0 }}>Active Trade Tracker (DB-Synced)</Title>
          <Text type="secondary" style={{ fontSize: 12 }}>
            Real-time monitoring of all open trades in the database.
          </Text>
        </div>
        <Table<TrackerRow>
          dataSource={results}
          columns={columns}
          rowKey={(record) => `${record.symbol}-${record.buyDate}`}
          pagination={false}
          scroll={{ x: 1300, y: "calc(100vh - 400px)" }}
          loading={loading}
          size="small"
        />
      </div>
    </div>
  );
}
