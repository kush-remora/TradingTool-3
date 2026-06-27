import { UploadOutlined, CheckCircleOutlined, CloseCircleOutlined } from "@ant-design/icons";
import { Alert, Button, Card, Space, Table, Typography, Upload, message } from "antd";
import type { UploadFile, UploadProps } from "antd/es/upload/interface";
import { useState } from "react";
import type { Rule5ApiResponse, Rule5ApiRequest, Rule5SymbolResult } from "../types";

const { Text } = Typography;

function formatNumber(value: number | null | undefined, fractionDigits: number = 2): string {
  if (value == null) return "-";
  return value.toLocaleString("en-IN", {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  });
}

function BooleanIcon({ value }: { value: boolean }) {
  return value ? <CheckCircleOutlined style={{ color: "#52c41a" }} /> : <CloseCircleOutlined style={{ color: "#ff4d4f" }} />;
}

export function FiftyTwoWeekMomentumRule5Page() {
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [csvContent, setCsvContent] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [report, setReport] = useState<Rule5ApiResponse | null>(null);

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

  const runAnalysis = async () => {
    if (!csvContent) {
      message.error("Please upload a CSV file first.");
      return;
    }

    setLoading(true);
    setError(null);
    setReport(null);

    try {
      const requestPayload: Rule5ApiRequest = {
        csvContent,
      };

      const response = await fetch("/api/strategy/52w-momentum/rule5/csv", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(requestPayload),
      });

      if (!response.ok) {
        throw new Error(`Failed to run analysis: ${response.statusText}`);
      }

      const data = await response.json();
      setReport(data as Rule5ApiResponse);
      message.success("Analysis completed successfully!");
    } catch (err: any) {
      setError(err.message || "An unexpected error occurred.");
    } finally {
      setLoading(false);
    }
  };

  const symbolColumns = [
    { title: "Sr.", key: "index", width: 60, render: (_: any, __: any, index: number) => index + 1 },
    { title: "Date", dataIndex: "date", key: "date", sorter: (a: Rule5SymbolResult, b: Rule5SymbolResult) => a.date.localeCompare(b.date) },
    { title: "Symbol", dataIndex: "symbol", key: "symbol", sorter: (a: Rule5SymbolResult, b: Rule5SymbolResult) => a.symbol.localeCompare(b.symbol), render: (val: string) => <Text strong>{val}</Text> },
    { title: "Market Cap", dataIndex: "marketCapName", key: "marketCapName", sorter: (a: Rule5SymbolResult, b: Rule5SymbolResult) => a.marketCapName.localeCompare(b.marketCapName) },
    { title: "Sector", dataIndex: "sector", key: "sector", sorter: (a: Rule5SymbolResult, b: Rule5SymbolResult) => a.sector.localeCompare(b.sector) },
    { title: "Close Price", dataIndex: "closePrice", key: "closePrice", sorter: (a: Rule5SymbolResult, b: Rule5SymbolResult) => a.closePrice - b.closePrice, render: (val: number) => `₹${formatNumber(val)}` },
    { title: "200 SMA", dataIndex: "sma200", key: "sma200", sorter: (a: Rule5SymbolResult, b: Rule5SymbolResult) => a.sma200 - b.sma200, render: (val: number) => `₹${formatNumber(val)}` },
    { 
      title: "Dist from 52W High", 
      dataIndex: "distTo52wHighPct", 
      key: "distTo52wHighPct", 
      sorter: (a: Rule5SymbolResult, b: Rule5SymbolResult) => a.distTo52wHighPct - b.distTo52wHighPct,
      render: (val: number, record: Rule5SymbolResult) => (
        <Space direction="vertical" size={0}>
          <Text>{val.toFixed(2)}%</Text>
          <Text type="secondary" style={{ fontSize: '0.8em' }}>High: ₹{formatNumber(record.fiftyTwoWeekHigh)}</Text>
        </Space>
      )
    },
    { 
      title: "Dist from 52W Low", 
      dataIndex: "distTo52wLowPct", 
      key: "distTo52wLowPct", 
      sorter: (a: Rule5SymbolResult, b: Rule5SymbolResult) => a.distTo52wLowPct - b.distTo52wLowPct,
      render: (val: number, record: Rule5SymbolResult) => (
        <Space direction="vertical" size={0}>
          <Text>{val > 0 ? '+' : ''}{val.toFixed(2)}%</Text>
          <Text type="secondary" style={{ fontSize: '0.8em' }}>Low: ₹{formatNumber(record.fiftyTwoWeekLow)}</Text>
        </Space>
      )
    },
    { 
      title: "Days in ±2%", 
      dataIndex: "daysIn2Pct", 
      key: "daysIn2Pct", 
      sorter: (a: Rule5SymbolResult, b: Rule5SymbolResult) => a.daysIn2Pct - b.daysIn2Pct,
      render: (val: number, record: Rule5SymbolResult) => {
        const pct = record.dailyBreakdown.length > 0 ? (val / record.dailyBreakdown.length) * 100 : 0;
        return (
          <Space>
            {val}
            <Text type="secondary" style={{ fontSize: '0.85em' }}>({pct.toFixed(0)}%)</Text>
          </Space>
        );
      }
    },
    { 
      title: "Days in ±3%", 
      dataIndex: "daysIn3Pct", 
      key: "daysIn3Pct", 
      sorter: (a: Rule5SymbolResult, b: Rule5SymbolResult) => a.daysIn3Pct - b.daysIn3Pct,
      render: (val: number, record: Rule5SymbolResult) => {
        const pct = record.dailyBreakdown.length > 0 ? (val / record.dailyBreakdown.length) * 100 : 0;
        return (
          <Space>
            {val}
            <Text type="secondary" style={{ fontSize: '0.85em' }}>({pct.toFixed(0)}%)</Text>
          </Space>
        );
      }
    },
    { 
      title: "Days in ±4%", 
      dataIndex: "daysIn4Pct", 
      key: "daysIn4Pct", 
      sorter: (a: Rule5SymbolResult, b: Rule5SymbolResult) => a.daysIn4Pct - b.daysIn4Pct,
      render: (val: number, record: Rule5SymbolResult) => {
        const pct = record.dailyBreakdown.length > 0 ? (val / record.dailyBreakdown.length) * 100 : 0;
        return (
          <Space>
            {val}
            <Text type="secondary" style={{ fontSize: '0.85em' }}>({pct.toFixed(0)}%)</Text>
          </Space>
        );
      }
    },
  ];

  const dailyColumns = [
    { title: "Date", dataIndex: "date", key: "date" },
    { title: "Close Price", dataIndex: "closePrice", key: "closePrice", render: (val: number) => `₹${formatNumber(val)}` },
    { title: "200 SMA", dataIndex: "sma200", key: "sma200", render: (val: number) => `₹${formatNumber(val)}` },
    { title: "±2%", dataIndex: "in2Pct", key: "in2Pct", render: (val: boolean) => <BooleanIcon value={val} /> },
    { title: "±3%", dataIndex: "in3Pct", key: "in3Pct", render: (val: boolean) => <BooleanIcon value={val} /> },
    { title: "±4%", dataIndex: "in4Pct", key: "in4Pct", render: (val: boolean) => <BooleanIcon value={val} /> },
  ];

  return (
    <div style={{ padding: 24 }}>
      <Card title="52W Momentum - Phase 1 - Rule 5 Analysis">
        <Space direction="vertical" size={16} style={{ width: "100%" }}>
          <Text type="secondary">
            Upload a CSV containing <Text code>date, symbol, marketcapname, sector</Text>. 
            The tool calculates the number of days in the last 30 trading days where the close price orbited within ±2%, ±3%, and ±4% bounds of the 200 SMA.
          </Text>
          
          <Space>
            <Upload {...uploadProps}>
              <Button icon={<UploadOutlined />}>Select CSV File</Button>
            </Upload>
            <Button type="primary" onClick={runAnalysis} loading={loading} disabled={!csvContent}>
              Run Analysis
            </Button>
          </Space>

          {error && <Alert type="error" message={error} showIcon />}

          {report && (
            <Table
              dataSource={report.results}
              columns={symbolColumns}
              rowKey={(row) => `${row.symbol}-${row.date}`}
              pagination={{ pageSize: 200 }}
              size="middle"
              bordered
              expandable={{
                expandedRowRender: (record: Rule5SymbolResult) => (
                  <Table 
                    dataSource={record.dailyBreakdown}
                    columns={dailyColumns}
                    rowKey={(row) => row.date}
                    pagination={false}
                    size="small"
                  />
                )
              }}
            />
          )}
        </Space>
      </Card>
    </div>
  );
}
