import { useState, useEffect } from "react";
import { 
  Card, 
  Space, 
  Upload, 
  Button, 
  Typography, 
  message, 
  Table, 
  Alert,
  Form,
  InputNumber,
  Radio,
  Tabs,
  Tag,
  Drawer,
  Select,
  Input
} from "antd";
import { UploadOutlined, EditOutlined } from "@ant-design/icons";
import type { UploadProps, UploadFile } from "antd/es/upload/interface";
import { 
  CsvBacktestApiRequest, 
  CsvBacktestResponse,
  BacktestTradeReviewApiRequest,
  ReviewReasonsResponse,
  ReviewReason
} from "../types";

const { Text } = Typography;
const { TextArea } = Input;

const formatNumber = (num: number | null | undefined, decimals = 2) => {
  if (num === null || num === undefined) return "-";
  return num.toFixed(decimals);
};

export function CsvBacktestPage() {
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [csvContent, setCsvContent] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [response, setResponse] = useState<CsvBacktestResponse | null>(null);
  
  const [form] = Form.useForm();
  const [type, setType] = useState<"FIXED" | "TRAILING">("FIXED");

  // Review Drawer State
  const [reviewDrawerVisible, setReviewDrawerVisible] = useState(false);
  const [selectedTrade, setSelectedTrade] = useState<any | null>(null);
  const [reviewForm] = Form.useForm();
  const [submittingReview, setSubmittingReview] = useState(false);
  const [reviewPassMode, setReviewPassMode] = useState<boolean | null>(null);
  
  const [reviewReasons, setReviewReasons] = useState<ReviewReasonsResponse | null>(null);

  useEffect(() => {
    fetch("/api/strategy/csv-backtest/reviews/reasons")
      .then(res => res.json())
      .then(data => setReviewReasons(data as ReviewReasonsResponse))
      .catch(err => console.error("Failed to load review reasons", err));
  }, []);

  const handleUpload: UploadProps["onChange"] = (info) => {
    let newFileList = [...info.fileList];
    newFileList = newFileList.slice(-1);
    setFileList(newFileList);

    if (newFileList.length > 0 && newFileList[0].originFileObj) {
      const reader = new FileReader();
      reader.onload = (e) => {
        setCsvContent(e.target?.result as string);
      };
      reader.onerror = () => {
        message.error("Failed to read file");
      };
      reader.readAsText(newFileList[0].originFileObj);
    } else {
      setCsvContent(null);
    }
  };

  const uploadProps: UploadProps = {
    onChange: handleUpload,
    multiple: false,
    fileList,
    beforeUpload: () => false, // Prevent auto upload
    accept: ".csv",
  };

  const onFinish = async (values: any) => {
    if (!csvContent) {
      message.error("Please upload a CSV file first.");
      return;
    }

    setLoading(true);
    setError(null);
    setResponse(null);

    try {
      const requestPayload: CsvBacktestApiRequest = {
        csvContent,
        type: values.type,
        targetPct: values.type === "FIXED" ? values.targetPct : null,
        stopLossPct: values.stopLossPct,
      };

      const res = await fetch("/api/strategy/csv-backtest/run", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(requestPayload),
      });

      if (!res.ok) {
        throw new Error(`Failed to run analysis: ${res.statusText}`);
      }

      const data = await res.json();
      setResponse(data as CsvBacktestResponse);
      message.success("Backtest completed successfully!");
    } catch (err: any) {
      setError(err.message || "An unexpected error occurred.");
    } finally {
      setLoading(false);
    }
  };

  const openReviewDrawer = (trade: any) => {
    setSelectedTrade(trade);
    reviewForm.resetFields();
    setReviewPassMode(null);
    setReviewDrawerVisible(true);
  };

  const closeReviewDrawer = () => {
    setReviewDrawerVisible(false);
    setSelectedTrade(null);
  };

  const onReviewFinish = async (values: any) => {
    if (!selectedTrade) return;

    setSubmittingReview(true);
    try {
      const payload: BacktestTradeReviewApiRequest = {
        symbol: selectedTrade.symbol,
        signalDate: selectedTrade.signalDate,
        marketCap: selectedTrade.marketCapName,
        sector: selectedTrade.sector,
        entryDate: selectedTrade.entryDate,
        entryPrice: selectedTrade.entryPrice,
        exitDate: selectedTrade.exitDate,
        exitPrice: selectedTrade.exitPrice,
        pnlPct: selectedTrade.profitLossPct,
        daysHeld: selectedTrade.daysHeld,
        slHit: selectedTrade.slHit,
        isPass: values.isPass === "yes",
        reasonTags: values.reasonTags ? values.reasonTags.join(",") : null,
        notes: values.notes || null,
      };

      const res = await fetch("/api/strategy/csv-backtest/reviews", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });

      if (!res.ok) throw new Error("Failed to save review");
      
      message.success(`Saved review for ${selectedTrade.symbol}`);
      closeReviewDrawer();
    } catch (err: any) {
      message.error(err.message || "Error saving review");
    } finally {
      setSubmittingReview(false);
    }
  };

  const tradesColumns = [
    { title: "Sr.", key: "index", width: 50, render: (_: any, __: any, index: number) => index + 1 },
    { title: "Symbol", dataIndex: "symbol", key: "symbol", render: (val: string) => <Text strong>{val}</Text>, sorter: (a: any, b: any) => a.symbol.localeCompare(b.symbol) },
    { title: "Market Cap", dataIndex: "marketCapName", key: "marketCapName", sorter: (a: any, b: any) => a.marketCapName.localeCompare(b.marketCapName) },
    { title: "Sector", dataIndex: "sector", key: "sector", sorter: (a: any, b: any) => a.sector.localeCompare(b.sector) },
    { title: "Signal Date", dataIndex: "signalDate", key: "signalDate", sorter: (a: any, b: any) => a.signalDate.localeCompare(b.signalDate) },
    { title: "Entry Date", dataIndex: "entryDate", key: "entryDate", render: (val: string | null) => val || "-" },
    { title: "Entry Price", dataIndex: "entryPrice", key: "entryPrice", render: (val: number | null) => val ? `₹${formatNumber(val)}` : "-" },
    { title: "Exit Date", dataIndex: "exitDate", key: "exitDate", render: (val: string | null, record: any) => record.isOpen ? <Tag color="blue">Open</Tag> : (val || "-") },
    { title: "Exit Price", dataIndex: "exitPrice", key: "exitPrice", render: (val: number | null) => val ? `₹${formatNumber(val)}` : "-" },
    { 
      title: "P&L %", 
      dataIndex: "profitLossPct", 
      key: "profitLossPct", 
      sorter: (a: any, b: any) => (a.profitLossPct || 0) - (b.profitLossPct || 0),
      render: (val: number | null) => {
        if (val === null) return "-";
        return <Text type={val >= 0 ? "success" : "danger"}>{val > 0 ? "+" : ""}{val.toFixed(2)}%</Text>;
      }
    },
    { title: "Days Held", dataIndex: "daysHeld", key: "daysHeld", sorter: (a: any, b: any) => a.daysHeld - b.daysHeld },
    { title: "SL Hit", dataIndex: "slHit", key: "slHit", render: (val: boolean) => val ? <Tag color="red">Yes</Tag> : <Tag color="green">No</Tag> },
    {
      title: "Action",
      key: "action",
      render: (_: any, record: any) => (
        <Button size="small" type="dashed" icon={<EditOutlined />} onClick={() => openReviewDrawer(record)}>
          Analyze
        </Button>
      )
    }
  ];

  const summaryColumns = [
    { title: "Month", dataIndex: "month", key: "month", sorter: (a: any, b: any) => a.month.localeCompare(b.month) },
    { title: "Total Trades", dataIndex: "totalTrades", key: "totalTrades" },
    { title: "Win", dataIndex: "winTrades", key: "winTrades", render: (val: number) => <Text type="success">{val}</Text> },
    { title: "Loss", dataIndex: "lossTrades", key: "lossTrades", render: (val: number) => <Text type="danger">{val}</Text> },
    { title: "Win Rate", key: "winRate", render: (_: any, record: any) => `${((record.winTrades / record.totalTrades) * 100).toFixed(1)}%` },
    { title: "Avg Holding", dataIndex: "avgHoldingPeriod", key: "avgHoldingPeriod", render: (val: number) => `${val.toFixed(1)} days` },
    { 
      title: "Avg P&L %", 
      dataIndex: "avgProfitPct", 
      key: "avgProfitPct", 
      render: (val: number) => <Text type={val >= 0 ? "success" : "danger"}>{val > 0 ? "+" : ""}{val.toFixed(2)}%</Text>
    },
  ];

  const getReasonOptions = () => {
    if (!reviewReasons) return [];
    const list = reviewPassMode ? reviewReasons.acceptanceReasons : reviewReasons.rejectionReasons;
    return list.map((reason: ReviewReason) => (
      <Select.Option key={reason.id} value={reason.label} label={reason.label}>
        <div>
          <div style={{ fontWeight: 'bold' }}>{reason.label}</div>
          <div style={{ fontSize: '12px', color: '#888' }}>{reason.description}</div>
        </div>
      </Select.Option>
    ));
  };

  return (
    <div style={{ padding: 24, maxWidth: 1200, margin: '0 auto' }}>
      <Space direction="vertical" size={24} style={{ width: "100%" }}>
        <Card title="CSV Backtesting Engine">
          <Form 
            form={form} 
            layout="vertical" 
            onFinish={onFinish}
            initialValues={{ type: "FIXED", targetPct: 10, stopLossPct: 5 }}
          >
            <Space direction="vertical" size={16} style={{ width: "100%" }}>
              <Text type="secondary">
                Upload a CSV containing <Text code>date, symbol, marketcapname, sector</Text>. 
                We will simulate entering trades at the <b>open</b> of the next trading day. 
                Gaps are processed properly (gap downs below SL trigger an exit at the gap open price).
              </Text>
              
              <Form.Item label="Upload CSV File" required>
                <Upload {...uploadProps}>
                  <Button icon={<UploadOutlined />}>Select CSV File</Button>
                </Upload>
              </Form.Item>

              <Form.Item label="Strategy Type" name="type">
                <Radio.Group onChange={(e) => setType(e.target.value)}>
                  <Radio.Button value="FIXED">Fixed Target & SL</Radio.Button>
                  <Radio.Button value="TRAILING">Trailing SL (High Close)</Radio.Button>
                </Radio.Group>
              </Form.Item>

              <Space size="large">
                {type === "FIXED" && (
                  <Form.Item label="Target %" name="targetPct" rules={[{ required: true }]}>
                    <InputNumber min={0.1} max={1000} step={0.5} addonAfter="%" />
                  </Form.Item>
                )}
                
                <Form.Item label="Stop Loss %" name="stopLossPct" rules={[{ required: true }]}>
                  <InputNumber min={0.1} max={100} step={0.5} addonAfter="%" />
                </Form.Item>
              </Space>

              <Form.Item>
                <Button type="primary" htmlType="submit" loading={loading} disabled={!csvContent}>
                  Run Backtest
                </Button>
              </Form.Item>
            </Space>
          </Form>

          {error && <Alert type="error" message={error} showIcon style={{ marginTop: 16 }} />}
        </Card>

        {response && (
          <Card>
            <Tabs defaultActiveKey="1">
              <Tabs.TabPane tab="Monthly Summary" key="1">
                <Table 
                  dataSource={response.summaries} 
                  columns={summaryColumns} 
                  rowKey="month" 
                  pagination={false}
                  size="middle"
                />
              </Tabs.TabPane>
              <Tabs.TabPane tab="Trade Details" key="2">
                <Table 
                  dataSource={response.trades} 
                  columns={tradesColumns} 
                  rowKey={(row) => `${row.symbol}-${row.signalDate}`} 
                  pagination={{ pageSize: 100 }}
                  size="small"
                  scroll={{ x: 1200 }}
                />
              </Tabs.TabPane>
            </Tabs>
          </Card>
        )}
      </Space>

      <Drawer
        title={selectedTrade ? `Analyze ${selectedTrade.symbol}` : "Analyze Trade"}
        placement="right"
        width={400}
        onClose={closeReviewDrawer}
        open={reviewDrawerVisible}
      >
        {selectedTrade && (
          <Form form={reviewForm} layout="vertical" onFinish={onReviewFinish}>
            <Form.Item name="isPass" label="Pass or Reject?" rules={[{ required: true, message: "Please select an option" }]}>
              <Radio.Group onChange={(e) => setReviewPassMode(e.target.value === "yes")}>
                <Radio.Button value="yes"><Text type="success">Pass</Text></Radio.Button>
                <Radio.Button value="no"><Text type="danger">Reject</Text></Radio.Button>
              </Radio.Group>
            </Form.Item>

            {reviewPassMode !== null && (
              <Form.Item 
                name="reasonTags" 
                label={reviewPassMode ? "Acceptance Reasons" : "Rejection Reasons"}
              >
                <Select 
                  mode="tags" 
                  style={{ width: '100%' }} 
                  placeholder="Select or type reasons..."
                  optionLabelProp="label"
                >
                  {getReasonOptions()}
                </Select>
              </Form.Item>
            )}

            <Form.Item name="notes" label="Custom Notes">
              <TextArea rows={4} placeholder="Add any specific observations..." />
            </Form.Item>

            <Form.Item>
              <Button type="primary" htmlType="submit" loading={submittingReview} block>
                Save Review
              </Button>
            </Form.Item>
          </Form>
        )}
      </Drawer>
    </div>
  );
}
