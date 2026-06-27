import { UploadOutlined, SettingOutlined } from "@ant-design/icons";
import { Alert, Button, Card, Col, Divider, Row, Space, Statistic, Table, Typography, Upload, message, InputNumber } from "antd";
import type { UploadFile, UploadProps } from "antd/es/upload/interface";
import { useMemo, useState } from "react";
import type { TrailingStopBacktestReport, TrailingStopBacktestApiRequest, TrailingStopTradeRow } from "../types";

const { Title, Text } = Typography;

function formatNumber(value: number | null | undefined, fractionDigits: number = 2): string {
  if (value == null) return "-";
  return value.toLocaleString("en-IN", {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  });
}

function formatPercent(value: number | null | undefined): string {
  if (value == null) return "-";
  return `${formatNumber(value, 2)}%`;
}

export function TrailingStopBacktestPage() {
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [csvContent, setCsvContent] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [report, setReport] = useState<TrailingStopBacktestReport | null>(null);
  const [successThresholdPct, setSuccessThresholdPct] = useState<number>(10);

  const handleUpload: UploadProps["onChange"] = (info) => {
    let fileList = [...info.fileList];
    fileList = fileList.slice(-1);
    setFileList(fileList);

    if (fileList.length > 0 && fileList[0].originFileObj) {
      const reader = new FileReader();
      reader.onload = (e) => {
        setCsvContent(e.target?.result as string);
      };
      reader.onerror = () => {
        message.error("Failed to read file");
      };
      reader.readAsText(fileList[0].originFileObj);
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

  const runBacktest = async () => {
    if (!csvContent) {
      message.error("Please upload a CSV file first.");
      return;
    }

    setLoading(true);
    setError(null);
    setReport(null);

    try {
      const requestPayload: TrailingStopBacktestApiRequest = {
        csvContent,
        allocationPerTrade: 10000.0, // Default to 10k as requested
      };

      const response = await fetch("/api/strategy/trailing-stop-backtest/run", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(requestPayload),
      });

      if (!response.ok) {
        throw new Error(`Failed to run backtest: ${response.statusText}`);
      }

      const data = await response.json();
      setReport(data as TrailingStopBacktestReport);
      message.success("Backtest completed successfully!");
    } catch (err: any) {
      setError(err.message || "An unexpected error occurred.");
    } finally {
      setLoading(false);
    }
  };

  const enteredTrades = useMemo(() => report?.trades.filter(t => t.entryDate !== null) || [], [report]);
  
  const overallSummary = useMemo(() => {
    const totalTrades = enteredTrades.length;
    const profitableTrades = enteredTrades.filter(t => (t.profitLossPct ?? 0) >= successThresholdPct).length;
    const totalProfitLoss = enteredTrades.reduce((sum, t) => sum + t.profitLoss, 0);
    const totalInvested = enteredTrades.reduce((sum, t) => sum + t.investedAmount, 0);
    const averageReturnPct = totalInvested > 0 ? (totalProfitLoss / totalInvested) * 100 : 0;
    const winRatePct = totalTrades > 0 ? (profitableTrades / totalTrades) * 100 : 0;
    return { totalTrades, winRatePct, averageReturnPct, totalProfitLoss };
  }, [enteredTrades, successThresholdPct]);

  const groupBy = (trades: TrailingStopTradeRow[], key: keyof TrailingStopTradeRow) => {
    const groups = new Map<string, TrailingStopTradeRow[]>();
    for (const t of trades) {
      const val = String(t[key]);
      const current = groups.get(val) || [];
      current.push(t);
      groups.set(val, current);
    }
    
    return Array.from(groups.entries()).map(([name, groupTrades]) => {
      const totalTrades = groupTrades.length;
      const profitableTrades = groupTrades.filter(t => (t.profitLossPct ?? 0) >= successThresholdPct).length;
      const totalProfitLoss = groupTrades.reduce((sum, t) => sum + t.profitLoss, 0);
      const totalInvested = groupTrades.reduce((sum, t) => sum + t.investedAmount, 0);
      const averageReturnPct = totalInvested > 0 ? (totalProfitLoss / totalInvested) * 100 : 0;
      const winRatePct = totalTrades > 0 ? (profitableTrades / totalTrades) * 100 : 0;
      return { key: name, name, totalTrades, profitableTrades, winRatePct, averageReturnPct, totalProfitLoss };
    });
  };

  const marketCapSummary = useMemo(() => groupBy(enteredTrades, 'marketCapName'), [enteredTrades, successThresholdPct]);
  const sectorSummary = useMemo(() => groupBy(enteredTrades, 'sector'), [enteredTrades, successThresholdPct]);

  const groupedBySectorAndMarketCap = useMemo(() => {
    const groups = new Map<string, TrailingStopTradeRow[]>();
    for (const t of enteredTrades) {
      const key = `${t.sector} - ${t.marketCapName}`;
      const current = groups.get(key) || [];
      current.push(t);
      groups.set(key, current);
    }
    
    return Array.from(groups.entries()).map(([key, groupTrades]) => {
      const totalTrades = groupTrades.length;
      const profitableTrades = groupTrades.filter(t => (t.profitLossPct ?? 0) >= successThresholdPct).length;
      const totalProfitLoss = groupTrades.reduce((sum, t) => sum + t.profitLoss, 0);
      const totalInvested = groupTrades.reduce((sum, t) => sum + t.investedAmount, 0);
      const averageReturnPct = totalInvested > 0 ? (totalProfitLoss / totalInvested) * 100 : 0;
      const winRatePct = totalTrades > 0 ? (profitableTrades / totalTrades) * 100 : 0;
      return { 
        key, 
        name: key, 
        totalTrades, 
        profitableTrades, 
        winRatePct, 
        averageReturnPct, 
        totalProfitLoss,
        trades: groupTrades.sort((a, b) => a.signalDate.localeCompare(b.signalDate))
      };
    }).sort((a, b) => a.name.localeCompare(b.name));
  }, [enteredTrades, successThresholdPct]);

  const aggregateColumns = (keyTitle: string) => [
    { title: keyTitle, dataIndex: "name", key: "name", render: (val: string) => <Text strong>{val}</Text> },
    { title: "Total Trades", dataIndex: "totalTrades", key: "totalTrades", sorter: (a: any, b: any) => a.totalTrades - b.totalTrades },
    { title: "Wins", dataIndex: "profitableTrades", key: "profitableTrades" },
    { title: "Win Rate", dataIndex: "winRatePct", key: "winRatePct", render: (val: number) => formatPercent(val), sorter: (a: any, b: any) => a.winRatePct - b.winRatePct },
    { title: "Avg Return", dataIndex: "averageReturnPct", key: "averageReturnPct", render: (val: number) => formatPercent(val), sorter: (a: any, b: any) => a.averageReturnPct - b.averageReturnPct },
    { title: "P&L", dataIndex: "totalProfitLoss", key: "totalProfitLoss", render: (val: number) => `₹${formatNumber(val)}`, sorter: (a: any, b: any) => a.totalProfitLoss - b.totalProfitLoss },
  ];

  const tradeColumns = [
    { title: "Signal Date", dataIndex: "signalDate", key: "signalDate" },
    { title: "Symbol", dataIndex: "symbol", key: "symbol", render: (val: string) => <Text strong>{val}</Text> },
    { title: "Outcome", dataIndex: "outcome", key: "outcome", render: (val: string) => {
        let color = "default";
        if (val === "STOP_LOSS_HIT" || val === "STOP_LOSS_GAP_DOWN" || val === "STOP_LOSS_EMA20") color = "red";
        else if (val === "TARGET_HIT") color = "green";
        else if (val === "OPEN_MTM") color = "blue";
        return <Typography.Text style={{ color }}>{val}</Typography.Text>;
    }},
    { title: "Entry", key: "entry", render: (_: any, row: TrailingStopTradeRow) => (
      <Space direction="vertical" size={0}>
        <Text>{row.entryDate}</Text>
        <Text type="secondary">₹{formatNumber(row.entryPrice)}</Text>
      </Space>
    )},
    { title: "Exit", key: "exit", render: (_: any, row: TrailingStopTradeRow) => (
      <Space direction="vertical" size={0}>
        <Text>{row.exitDate}</Text>
        <Text type="secondary">₹{formatNumber(row.exitPrice)}</Text>
      </Space>
    )},
    { title: "Hold Days", dataIndex: "holdingTradingDays", key: "holdingTradingDays" },
    { title: "P&L", dataIndex: "profitLoss", key: "profitLoss", render: (val: number) => (
      <Text style={{ color: val >= 0 ? '#3f8600' : '#cf1322' }}>₹{formatNumber(val)}</Text>
    )},
    { title: "Return", dataIndex: "profitLossPct", key: "profitLossPct", render: (val: number) => (
      <Text style={{ color: val >= 0 ? '#3f8600' : '#cf1322' }}>{formatPercent(val)}</Text>
    )},
  ];

  return (
    <div style={{ padding: 24 }}>
      <Card title="Trailing Stop Loss Backtest Engine">
        <Space direction="vertical" size={16} style={{ width: "100%" }}>
          <Text type="secondary">
            Upload a CSV containing <Text code>date, symbol, marketcapname, sector</Text> to simulate a trailing stop loss strategy.
            It buys ₹10k of shares at the next day's open and exits when the trailing stop (previous day's low) is hit.
          </Text>
          
          <Space>
            <Upload {...uploadProps}>
              <Button icon={<UploadOutlined />}>Select CSV File</Button>
            </Upload>
            <Button type="primary" onClick={runBacktest} loading={loading} disabled={!csvContent}>
              Run Backtest
            </Button>
            <Space style={{ marginLeft: 16 }}>
              <Text type="secondary">Success Target (%):</Text>
              <InputNumber 
                value={successThresholdPct} 
                onChange={(v) => setSuccessThresholdPct(v ?? 0)} 
                min={0} 
                max={100}
                addonAfter="%"
              />
            </Space>
          </Space>

          {error && <Alert type="error" message={error} showIcon />}

          {report && (
            <div style={{ marginTop: 24 }}>
              <Divider orientation="left">Overall Performance</Divider>
              <Row gutter={[16, 16]}>
                <Col xs={24} sm={12} md={6}>
                  <Card size="small">
                    <Statistic title="Total Trades" value={overallSummary.totalTrades} />
                  </Card>
                </Col>
                <Col xs={24} sm={12} md={6}>
                  <Card size="small">
                    <Statistic title="Win Rate" value={overallSummary.winRatePct} precision={2} suffix="%" />
                  </Card>
                </Col>
                <Col xs={24} sm={12} md={6}>
                  <Card size="small">
                    <Statistic title="Average Return" value={overallSummary.averageReturnPct} precision={2} suffix="%" />
                  </Card>
                </Col>
                <Col xs={24} sm={12} md={6}>
                  <Card size="small">
                    <Statistic 
                      title="Total P&L" 
                      value={overallSummary.totalProfitLoss} 
                      precision={2} 
                      prefix="₹" 
                      valueStyle={{ color: overallSummary.totalProfitLoss >= 0 ? '#3f8600' : '#cf1322' }} 
                    />
                  </Card>
                </Col>
              </Row>

              <Divider orientation="left">Deep Dive: Sector x Market Cap</Divider>
              <Table 
                dataSource={groupedBySectorAndMarketCap} 
                columns={aggregateColumns('Sector & Market Cap')}
                pagination={{ pageSize: 15 }}
                size="middle"
                bordered
                expandable={{
                  expandedRowRender: (record) => (
                    <Table 
                      dataSource={record.trades}
                      columns={tradeColumns}
                      rowKey={(row) => `${row.symbol}-${row.signalDate}`}
                      pagination={false}
                      size="small"
                    />
                  )
                }}
              />

              <Divider orientation="left">Performance by Market Cap</Divider>
              <Table 
                dataSource={marketCapSummary} 
                columns={aggregateColumns('Market Cap')}
                pagination={false}
                size="middle"
                bordered
              />

              <Divider orientation="left">Performance by Sector</Divider>
              <Table 
                dataSource={sectorSummary} 
                columns={aggregateColumns('Sector')}
                pagination={{ pageSize: 15 }}
                size="middle"
                bordered
              />
            </div>
          )}
        </Space>
      </Card>
    </div>
  );
}
