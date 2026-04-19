import React, { useState } from "react";
import { Button, Input, Card, Space, Typography, Table, message, Alert } from "antd";
import { DownloadOutlined, FileTextOutlined } from "@ant-design/icons";

const { TextArea } = Input;
const { Title, Text } = Typography;

interface CorporateEventRequest {
  symbol: String;
  primaryDate: string;
}

interface Candle {
  candleDate: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}

interface ExportResponse {
  symbol: string;
  primaryDate: string;
  candles: Candle[];
}

export const CorporateResultsPage: React.FC = () => {
  const [inputText, setInputText] = useState("");
  const [loading, setLoading] = useState(false);
  const [results, setResults] = useState<ExportResponse[]>([]);

  const parseInput = () => {
    const trimmedInput = inputText.trim();
    if (!trimmedInput) return [];

    const lines = trimmedInput.split("\n");
    if (lines.length === 0) return [];

    // Detect delimiter: check if first line has commas or tabs
    const firstLine = lines[0];
    const delimiter = firstLine.includes("\t") ? "\t" : ",";
    
    const header = firstLine.split(delimiter).map(h => h.trim().toLowerCase());
    const symbolIdx = header.findIndex(h => h.includes("symbol"));
    const dateIdx = header.findIndex(h => h.includes("date") || h.includes("primarydate"));

    if (symbolIdx === -1 || dateIdx === -1) {
      message.error("Could not find symbol or date columns in header. Please use CSV or tab-separated format with headers.");
      return [];
    }

    const requests: CorporateEventRequest[] = [];
    for (let i = 1; i < lines.length; i++) {
      const parts = lines[i].split(delimiter);
      if (parts.length > Math.max(symbolIdx, dateIdx)) {
        const symbol = parts[symbolIdx].trim().toUpperCase();
        const dateStr = parts[dateIdx].trim();
        
        if (symbol && dateStr) {
          requests.push({
            symbol: symbol,
            primaryDate: dateStr,
          });
        }
      }
    }
    return requests;
  };

  const handleFetch = async () => {
    const requests = parseInput();
    if (requests.length === 0) return;

    setLoading(true);
    try {
      const response = await fetch("/api/corporate-events/export", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify(requests),
      });

      if (!response.ok) {
        throw new Error(`Failed to fetch: ${response.statusText}`);
      }

      const data = await response.json();
      setResults(data);
      message.success(`Fetched data for ${data.length} stocks`);
    } catch (error) {
      console.error(error);
      message.error("Failed to fetch data from backend");
    } finally {
      setLoading(false);
    }
  };

  const handleExport = () => {
    if (results.length === 0) return;

    const dataStr = "data:text/json;charset=utf-8," + encodeURIComponent(JSON.stringify(results, null, 2));
    const downloadAnchorNode = document.createElement('a');
    downloadAnchorNode.setAttribute("href", dataStr);
    downloadAnchorNode.setAttribute("download", `corporate_results_${new Date().toISOString().split('T')[0]}.json`);
    document.body.appendChild(downloadAnchorNode);
    downloadAnchorNode.click();
    downloadAnchorNode.remove();
  };

  return (
    <div style={{ padding: "24px" }}>
      <Space direction="vertical" style={{ width: "100%" }} size="large">
        <Title level={2}>Corporate Results Data Export</Title>
        <Alert
          message="Instructions"
          description={
            <div>
              <p>Paste the CSV (comma-separated) or tab-separated list of stocks from the corporate events page. It should have headers like:</p>
              <pre>nseSymbol,marketCap,type,resultDate</pre>
              <p>Or tab-separated from the results page:</p>
              <pre>nseSymbol	marketCap	type	corporateEventPillDto.primaryDate</pre>
            </div>
          }
          type="info"
          showIcon
        />
        <Card title="Input Data">
          <TextArea
            rows={10}
            placeholder="Paste tab-separated data here..."
            value={inputText}
            onChange={(e) => setInputText(e.target.value)}
          />
          <Button
            type="primary"
            icon={<FileTextOutlined />}
            onClick={handleFetch}
            loading={loading}
            style={{ marginTop: "16px" }}
          >
            Fetch Candle Data
          </Button>
        </Card>

        {results.length > 0 && (
          <Card
            title="Results Preview"
            extra={
              <Button type="primary" icon={<DownloadOutlined />} onClick={handleExport}>
                Export to JSON
              </Button>
            }
          >
            <Table
              dataSource={results}
              columns={[
                { title: "Symbol", dataIndex: "symbol", key: "symbol" },
                { title: "Primary Date", dataIndex: "primaryDate", key: "primaryDate" },
                {
                  title: "Candles Found",
                  key: "candles",
                  render: (_, record) => record.candles.length,
                },
                {
                  title: "Range",
                  key: "range",
                  render: (_, record) => {
                    if (record.candles.length === 0) return "N/A";
                    return `${record.candles[0].candleDate} to ${record.candles[record.candles.length - 1].candleDate}`;
                  },
                },
              ]}
              rowKey="symbol"
              pagination={{ pageSize: 10 }}
            />
          </Card>
        )}
      </Space>
    </div>
  );
};
