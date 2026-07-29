import { useState, useEffect, useMemo, useRef, type Key } from "react";
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
  Input,
  Switch,
  Tooltip
} from "antd";
import { UploadOutlined, EditOutlined } from "@ant-design/icons";
import type { TableProps } from "antd";
import type { UploadProps, UploadFile } from "antd/es/upload/interface";
import { 
  CsvBacktestApiRequest, 
  CsvBacktestResponse,
  CsvBacktestTradeResult,
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

export const formatBreakoutSpan = (
  sessions: number | null,
  isLowerBound: boolean,
): string => {
  if (sessions === null) return "-";
  return `${sessions}${isLowerBound ? "+" : ""} days`;
};

const filterCsv = (rawCsv: string, selectedMonths: string[], selectedMarketCaps: string[]) => {
  if ((!selectedMonths || !selectedMonths.length) && (!selectedMarketCaps || !selectedMarketCaps.length)) return rawCsv;
  
  const lines = rawCsv.split('\n');
  if (lines.length <= 1) return rawCsv;
  
  const header = lines[0];
  const headerCols = header.toLowerCase().split(',').map(s => s.trim().replace(/"/g, ''));
  const dateIdx = headerCols.indexOf("date");
  const mcIdx = headerCols.indexOf("marketcapname");
  
  if (dateIdx === -1) return rawCsv;
  
  const filteredLines = [header];
  for (let i = 1; i < lines.length; i++) {
    const line = lines[i];
    if (!line.trim()) continue;
    const cols = line.split(',').map(s => s.trim());
    if (cols.length <= dateIdx) continue;
    
    const dateStr = cols[dateIdx];
    const mcStr = mcIdx !== -1 && cols.length > mcIdx ? cols[mcIdx] : "";
    
    const parts = dateStr.split('-');
    if (parts.length === 3) {
      const monthYear = `${parts[1]}-${parts[2]}`;
      const passMonth = !selectedMonths || selectedMonths.length === 0 || selectedMonths.includes(monthYear);
      const passMc = !selectedMarketCaps || selectedMarketCaps.length === 0 || selectedMarketCaps.includes(mcStr);
      
      if (passMonth && passMc) {
        filteredLines.push(line);
      }
    } else {
      filteredLines.push(line);
    }
  }
  return filteredLines.join('\n');
};

const combineCsvFiles = async (files: UploadFile[]): Promise<string> => {
  const contents = await Promise.all(
    files.map(async (file) => file.originFileObj?.text() ?? ""),
  );

  return contents.reduce<string>((combinedCsv, content) => {
    const lines = content.split(/\r?\n/).filter((line) => line.trim());
    if (!lines.length) return combinedCsv;
    if (!combinedCsv) return lines.join("\n");
    return `${combinedCsv}\n${lines.slice(1).join("\n")}`;
  }, "");
};

export const buildSectorFilterOptions = (trades: CsvBacktestTradeResult[]) =>
  Array.from(new Set(trades.map((trade) => trade.sector).filter(Boolean)))
    .sort()
    .map((sector) => ({ text: sector, value: sector }));

export const matchesSectorFilter = (
  value: boolean | Key,
  trade: CsvBacktestTradeResult,
): boolean => typeof value === "string" && trade.sector === value;

export const matchesMaximumV2RunPct = (
  trade: CsvBacktestTradeResult,
  maximumV2RunPct: number | null,
): boolean =>
  maximumV2RunPct === null ||
  (
    trade.v2MoveFromRecentBasePct !== null &&
    trade.v2MoveFromRecentBasePct <= maximumV2RunPct
  );

export const matchesSelectedSectors = (
  trade: CsvBacktestTradeResult,
  selectedSectors: string[],
): boolean => selectedSectors.length === 0 || selectedSectors.includes(trade.sector);

export interface TargetOutcomeSummary {
  total: number;
  targetHits: number;
  stopLossHits: number;
  unresolved: number;
  targetHitRatePct: number;
}

export const calculateTargetOutcomeSummary = (
  trades: CsvBacktestTradeResult[],
): TargetOutcomeSummary => {
  const targetHits = trades.filter((trade) => trade.targetHit).length;
  const stopLossHits = trades.filter((trade) => trade.slHit).length;
  const unresolved = trades.length - targetHits - stopLossHits;

  return {
    total: trades.length,
    targetHits,
    stopLossHits,
    unresolved,
    targetHitRatePct: trades.length === 0 ? 0 : (targetHits / trades.length) * 100,
  };
};

interface CsvBacktestTableRow extends CsvBacktestTradeResult {
  tableRowId: string;
}

export const buildCsvBacktestTableRows = (
  trades: CsvBacktestTradeResult[],
): CsvBacktestTableRow[] => {
  const seenTradeIds = new Set<string>();

  return trades.flatMap((trade) => {
    const tableRowId = `${trade.symbol}-${trade.signalDate}`;
    if (seenTradeIds.has(tableRowId)) return [];

    seenTradeIds.add(tableRowId);
    return [{ ...trade, tableRowId }];
  });
};

export function CsvBacktestPage() {
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const uploadSequence = useRef(0);
  const [csvContent, setCsvContent] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [response, setResponse] = useState<CsvBacktestResponse | null>(null);
  const [maximumV2RunPct, setMaximumV2RunPct] = useState<number | null>(null);
  const [selectedSectors, setSelectedSectors] = useState<string[]>([]);
  
  const [form] = Form.useForm();
  const [type, setType] = useState<"FIXED" | "TRAILING">("FIXED");
  const [entryStrategy, setEntryStrategy] = useState<"NEXT_DAY_OPEN" | "TWO_GREEN_CANDLES" | "RETEST" | "CONFIRMED_RETEST">("NEXT_DAY_OPEN");

  // Filter State
  const [availableMonths, setAvailableMonths] = useState<string[]>([]);
  const [availableMarketCaps, setAvailableMarketCaps] = useState<string[]>([]);

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

  useEffect(() => {
    if (!csvContent) {
      setAvailableMonths([]);
      setAvailableMarketCaps([]);
      return;
    }
    const lines = csvContent.split('\n');
    if (lines.length <= 1) return;
    
    const header = lines[0].toLowerCase().split(',').map(s => s.trim().replace(/"/g, ''));
    const dateIdx = header.indexOf("date");
    const mcIdx = header.indexOf("marketcapname");
    
    const months = new Set<string>();
    const mcs = new Set<string>();
    
    for (let i = 1; i < lines.length; i++) {
      const line = lines[i];
      if (!line.trim()) continue;
      const cols = line.split(',').map(s => s.trim());
      if (cols.length > dateIdx && dateIdx !== -1) {
        const parts = cols[dateIdx].split('-');
        if (parts.length === 3) {
          months.add(`${parts[1]}-${parts[2]}`);
        }
      }
      if (cols.length > mcIdx && mcIdx !== -1) {
        if (cols[mcIdx]) mcs.add(cols[mcIdx]);
      }
    }
    setAvailableMonths(Array.from(months).sort());
    setAvailableMarketCaps(Array.from(mcs).sort());
  }, [csvContent]);

  const handleUpload: UploadProps["onChange"] = (info) => {
    const newFileList = [...info.fileList];
    const sequence = uploadSequence.current + 1;
    uploadSequence.current = sequence;
    setFileList(newFileList);

    void combineCsvFiles(newFileList)
      .then((combinedCsv) => {
        if (sequence !== uploadSequence.current) return;
        setCsvContent(combinedCsv || null);
        form.setFieldsValue({ filterMonths: [], filterMarketCaps: [] });
      })
      .catch(() => message.error("Failed to read selected CSV files"));
  };

  const uploadProps: UploadProps = {
    onChange: handleUpload,
    multiple: true,
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
    setMaximumV2RunPct(null);
    setSelectedSectors([]);

    try {
      const filteredCsv = filterCsv(csvContent, values.filterMonths || [], values.filterMarketCaps || []);

      const requestPayload: CsvBacktestApiRequest = {
        csvContent: filteredCsv,
        type: values.type,
        targetPct: values.targetPct,
        stopLossPct: values.stopLossPct,
        initialStopLossSessions: values.initialStopLossSessions,
        trailingStopLossPct: values.trailingStopLossPct,
        entryStrategy: values.entryStrategy,
        retestWindowDays: values.retestWindowDays,
        retestTolerancePct: values.retestTolerancePct,
        applyV2Validation: values.applyV2Validation,
        breakoutLookbackSessions: values.breakoutLookbackSessions,
        maxCloseToCloseGainPct: values.maxCloseToCloseGainPct,
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

  const tradeRows = useMemo(
    () => buildCsvBacktestTableRows(response?.trades ?? []),
    [response],
  );
  const sectorFilterOptions = useMemo(
    () => buildSectorFilterOptions(tradeRows),
    [tradeRows],
  );

  const tradesColumns = [
    { title: "Sr.", key: "index", width: 50, render: (_: any, __: any, index: number) => index + 1 },
    { 
      title: "Symbol", 
      dataIndex: "symbol", 
      key: "symbol", 
      render: (val: string, record: any) => record.instrumentToken ? (
        <a href={`https://kite.zerodha.com/chart/web/tvc/NSE/${val}/${record.instrumentToken}`} target="_blank" rel="noopener noreferrer">
          <Text strong>{val}</Text>
        </a>
      ) : (
        <Text strong>{val}</Text>
      ), 
      sorter: (a: any, b: any) => a.symbol.localeCompare(b.symbol) 
    },
    { title: "Market Cap", dataIndex: "marketCapName", key: "marketCapName", sorter: (a: any, b: any) => a.marketCapName.localeCompare(b.marketCapName) },
    {
      title: "Sector",
      dataIndex: "sector",
      key: "sector",
      sorter: (a: any, b: any) => a.sector.localeCompare(b.sector),
      filters: sectorFilterOptions,
      filterMode: "tree" as const,
      filterSearch: true,
      onFilter: matchesSectorFilter,
    },
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
    { title: "SL", dataIndex: "slHit", key: "slHit", render: (val: boolean) => val ? <Tag color="red">Yes</Tag> : <Tag color="green">No</Tag> },
    { title: "Signal Date", dataIndex: "signalDate", key: "signalDate", sorter: (a: any, b: any) => a.signalDate.localeCompare(b.signalDate) },
    { title: "Entry Rule", dataIndex: "entryStrategy", key: "entryStrategy" },
    { title: "Breakout Level", dataIndex: "breakoutLevel", key: "breakoutLevel", render: (val: number | null) => val ? `₹${formatNumber(val)}` : "-" },
    {
      title: "Breakout Span",
      dataIndex: "breakoutSpanSessions",
      key: "breakoutSpanSessions",
      sorter: (a: CsvBacktestTradeResult, b: CsvBacktestTradeResult) =>
        (a.breakoutSpanSessions ?? 0) - (b.breakoutSpanSessions ?? 0),
      render: (val: number | null, record: CsvBacktestTradeResult) =>
        formatBreakoutSpan(val, record.breakoutSpanIsLowerBound),
    },
    {
      title: "Breakout Day Move %",
      dataIndex: "breakoutDayMovePct",
      key: "breakoutDayMovePct",
      sorter: (a: any, b: any) => (a.breakoutDayMovePct ?? 0) - (b.breakoutDayMovePct ?? 0),
      render: (val: number | null) => val === null
        ? "-"
        : <Text type={val >= 0 ? "success" : "danger"}>{val > 0 ? "+" : ""}{val.toFixed(2)}%</Text>,
    },
    { title: "Breakout Delivery %", dataIndex: "breakoutDayDeliveryPct", key: "breakoutDayDeliveryPct", render: (val: number | null) => val === null ? "-" : `${val.toFixed(2)}%` },
    {
      title: "T−5 Max Delivery %",
      dataIndex: "priorFiveDaysMaxDeliveryPct",
      key: "priorFiveDaysMaxDeliveryPct",
      sorter: (a: CsvBacktestTradeResult, b: CsvBacktestTradeResult) =>
        (a.priorFiveDaysMaxDeliveryPct ?? 0) - (b.priorFiveDaysMaxDeliveryPct ?? 0),
      render: (val: number | null, record: CsvBacktestTradeResult) => {
        if (val === null) return "-";
        return (
          <Tooltip
            title={
              <div>
                {record.priorFiveDaysDelivery.map((day) => (
                  <div key={day.date}>
                    {day.date}: {day.deliveryPct === null ? "-" : `${day.deliveryPct.toFixed(2)}%`}
                  </div>
                ))}
              </div>
            }
          >
            <span>{val.toFixed(2)}%</span>
          </Tooltip>
        );
      },
    },
    { title: "Entry Date", dataIndex: "entryDate", key: "entryDate", render: (val: string | null) => val || "-" },
    { title: "Entry Price", dataIndex: "entryPrice", key: "entryPrice", render: (val: number | null) => val ? `₹${formatNumber(val)}` : "-" },
    { title: "5D Low", dataIndex: "firstFiveDaysLowestPrice", key: "firstFiveDaysLowestPrice", render: (val: number | null) => val ? `₹${formatNumber(val)}` : "-" },
    { title: "5D Drop ₹", dataIndex: "firstFiveDaysDropAmount", key: "firstFiveDaysDropAmount", render: (val: number | null) => val === null ? "-" : `₹${formatNumber(val)}` },
    { title: "5D Drop %", dataIndex: "firstFiveDaysDropPct", key: "firstFiveDaysDropPct", render: (val: number | null) => val === null ? "-" : `${val.toFixed(2)}%` },
    { title: "3D Red Candles", dataIndex: "firstThreeDaysRedCandleCount", key: "firstThreeDaysRedCandleCount", render: (val: number | null) => val ?? "-" },
    { title: "V2 Vol Spike", dataIndex: "v2MaxPreBreakoutVolumeRatio", key: "v2MaxPreBreakoutVolumeRatio", render: (val: number | null) => val === null ? "-" : `${val.toFixed(2)}×` },
    { title: "V2 Failed Tests", dataIndex: "v2FailedResistanceAttempts", key: "v2FailedResistanceAttempts", render: (val: number | null) => val ?? "-" },
    { title: "V2 Base ₹", dataIndex: "v2RecentRunBasePrice", key: "v2RecentRunBasePrice", render: (val: number | null) => val === null ? "-" : `₹${formatNumber(val)}` },
    { title: "V2 Run %", dataIndex: "v2MoveFromRecentBasePct", key: "v2MoveFromRecentBasePct", render: (val: number | null) => val === null ? "-" : `${val.toFixed(2)}%` },
    { title: "Exit Date", dataIndex: "exitDate", key: "exitDate", render: (val: string | null, record: any) => record.isOpen ? <Tag color="blue">Open</Tag> : (val || "-") },
    { title: "Exit Price", dataIndex: "exitPrice", key: "exitPrice", render: (val: number | null) => val ? `₹${formatNumber(val)}` : "-" },
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
    { title: "Target Hit", dataIndex: "targetHitTrades", key: "targetHitTrades", render: (val: number) => <Text type="success">{val}</Text> },
    { title: "Stop Hit", dataIndex: "stopLossHitTrades", key: "stopLossHitTrades", render: (val: number) => <Text type="danger">{val}</Text> },
    { title: "Unresolved", dataIndex: "unresolvedTrades", key: "unresolvedTrades" },
    { title: "Target Hit Rate", key: "targetHitRate", render: (_: unknown, record: CsvBacktestResponse["summaries"][number]) => `${((record.targetHitTrades / record.totalTrades) * 100).toFixed(1)}%` },
    { title: "Avg Holding", dataIndex: "avgHoldingPeriod", key: "avgHoldingPeriod", render: (val: number) => `${val.toFixed(1)} days` },
    { title: "Avg 5D Drop %", dataIndex: "avgFirstFiveDaysDropPct", key: "avgFirstFiveDaysDropPct", render: (val: number) => `${val.toFixed(2)}%` },
    { 
      title: "Avg P&L %", 
      dataIndex: "avgProfitPct", 
      key: "avgProfitPct", 
      render: (val: number) => <Text type={val >= 0 ? "success" : "danger"}>{val > 0 ? "+" : ""}{val.toFixed(2)}%</Text>
    },
  ];

  const displayedTrades = useMemo(
    () => tradeRows.filter((trade) => matchesMaximumV2RunPct(trade, maximumV2RunPct)),
    [maximumV2RunPct, tradeRows],
  );
  const filteredTrades = useMemo(
    () => displayedTrades.filter((trade) => matchesSelectedSectors(trade, selectedSectors)),
    [displayedTrades, selectedSectors],
  );
  const targetOutcomeSummary = useMemo(
    () => calculateTargetOutcomeSummary(filteredTrades),
    [filteredTrades],
  );
  const hasV2TradeMetrics = tradeRows.some((trade) => trade.v2MoveFromRecentBasePct !== null);
  const handleTradesTableChange: TableProps<CsvBacktestTableRow>["onChange"] = (
    _pagination,
    filters,
  ) => {
    const sectorValues = filters.sector;
    setSelectedSectors(Array.isArray(sectorValues) ? sectorValues.map(String) : []);
  };

  const getOptions = () => {
    if (!reviewReasons) return [];
    const list = reviewPassMode ? reviewReasons.acceptanceReasons : reviewReasons.rejectionReasons;
    if (!list) return [];
    return list.map((reason: ReviewReason) => ({
      value: reason.label,
      label: (
        <div>
          <div style={{ fontWeight: 'bold', lineHeight: '1.2' }}>{reason.label}</div>
          <div style={{ fontSize: '12px', color: '#888', whiteSpace: 'normal', lineHeight: '1.2' }}>{reason.description}</div>
        </div>
      )
    }));
  };

  return (
    <div style={{ padding: 24, maxWidth: '100%', margin: '0 auto' }}>
      <Space direction="vertical" size={24} style={{ width: "100%" }}>
        <Card title="CSV Backtesting Engine">
          <Form 
            form={form} 
            layout="vertical" 
            onFinish={onFinish}
            initialValues={{
              type: "FIXED",
              targetPct: 20,
              stopLossPct: 10,
              initialStopLossSessions: 5,
              trailingStopLossPct: 5,
              entryStrategy: "NEXT_DAY_OPEN",
              retestWindowDays: 5,
              retestTolerancePct: 1,
              applyV2Validation: false,
              breakoutLookbackSessions: 100,
              maxCloseToCloseGainPct: 6,
            }}
          >
            <Space direction="vertical" size={16} style={{ width: "100%" }}>
              <Text type="secondary">
                Upload one or more CSV files containing <Text code>date, symbol, marketcapname, sector</Text>.
                We will simulate entering trades at the <b>open</b> of the next trading day. 
                The entry day's low/high is evaluated, and stop loss takes priority if both stop and target are hit.
              </Text>
              
              <Form.Item label="Upload CSV File" required>
                <Upload {...uploadProps}>
                  <Button icon={<UploadOutlined />}>Select CSV Files</Button>
                </Upload>
              </Form.Item>

              {csvContent && (
                <Space size="large" style={{ display: 'flex' }}>
                  <Form.Item label="Filter by Month" name="filterMonths" style={{ width: 250 }}>
                    <Select 
                      mode="multiple" 
                      placeholder="All Months"
                      options={availableMonths.map(m => ({ label: m, value: m }))}
                      allowClear
                    />
                  </Form.Item>
                  
                  <Form.Item label="Filter by Market Cap" name="filterMarketCaps" style={{ width: 350 }}>
                    <Select 
                      mode="multiple" 
                      placeholder="All Market Caps"
                      options={availableMarketCaps.map(mc => ({ label: mc, value: mc }))}
                      allowClear
                    />
                  </Form.Item>
                </Space>
              )}

              <Form.Item label="Strategy Type" name="type">
                <Radio.Group onChange={(e) => setType(e.target.value)}>
                  <Radio.Button value="FIXED">Fixed Target & SL</Radio.Button>
                  <Radio.Button value="TRAILING">Target + Trailing SL</Radio.Button>
                </Radio.Group>
              </Form.Item>

              <Space size={12} wrap align="start">
                <Form.Item label="Target %" name="targetPct" rules={[{ required: true }]}>
                  <InputNumber size="small" min={0.1} max={1000} step={0.5} addonAfter="%" />
                </Form.Item>
                
                <Form.Item label={type === "TRAILING" ? "Initial Stop Loss %" : "Stop Loss %"} name="stopLossPct" rules={[{ required: true }]}>
                  <InputNumber size="small" min={0.1} max={100} step={0.5} addonAfter="%" />
                </Form.Item>

                {type === "TRAILING" && (
                  <>
                    <Form.Item
                      label="Initial Stop Sessions"
                      name="initialStopLossSessions"
                      extra="Includes the entry session. A value of 3 activates trailing on session 4."
                      rules={[{ required: true }]}
                    >
                      <InputNumber size="small" min={1} max={20} step={1} />
                    </Form.Item>

                    <Form.Item label="Trailing Stop %" name="trailingStopLossPct" rules={[{ required: true }]}>
                      <InputNumber size="small" min={0.1} max={100} step={0.5} addonAfter="%" />
                    </Form.Item>
                  </>
                )}
              </Space>

              <Form.Item label="Entry Rule" name="entryStrategy">
                <Radio.Group onChange={(e) => setEntryStrategy(e.target.value)}>
                  <Radio.Button value="NEXT_DAY_OPEN">Next-Day Open</Radio.Button>
                  <Radio.Button value="TWO_GREEN_CANDLES">2 Green Candles</Radio.Button>
                  <Radio.Button value="RETEST">Breakout Retest</Radio.Button>
                  <Radio.Button value="CONFIRMED_RETEST">Confirmed Retest</Radio.Button>
                </Radio.Group>
              </Form.Item>

              <Form.Item
                label="Apply additional V2 filters"
                name="applyV2Validation"
                valuePropName="checked"
                extra="Fresh-breakout validation always runs. This adds the 2× volume spike, extension guard, and recent-base rules."
              >
                <Switch />
              </Form.Item>

              <Form.Item
                label="Breakout lookback (trading sessions)"
                name="breakoutLookbackSessions"
                extra="For 60 sessions, today's high must exceed the maximum close of the prior 60 sessions; none of the prior 59 sessions may satisfy the same rule. Default: 100."
                rules={[{ required: true }]}
              >
                <InputNumber min={10} max={250} step={5} />
              </Form.Item>

              <Form.Item
                label="Maximum close-to-close gain %"
                name="maxCloseToCloseGainPct"
                extra="Rejects an overextended V2 breakout or either required green candle."
              >
                <InputNumber min={0} max={100} step={0.5} addonAfter="%" />
              </Form.Item>

              {(entryStrategy === "RETEST" || entryStrategy === "CONFIRMED_RETEST") && (
                <Space size="large">
                  <Form.Item label="Retest Window (trading days)" name="retestWindowDays" rules={[{ required: true }]}>
                    <InputNumber min={1} max={20} step={1} />
                  </Form.Item>
                  <Form.Item label="Retest Zone Above Breakout %" name="retestTolerancePct" rules={[{ required: true }]}>
                    <InputNumber min={0} max={10} step={0.25} addonAfter="%" />
                  </Form.Item>
                </Space>
              )}

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
            <Space size="large" wrap>
              <Text type="secondary">
                Signals: {response.inputSignalCount} · Passed validation: {response.validatedSignalCount}
              </Text>
              <Space size={8}>
                <Text>Maximum V2 Run %</Text>
                <InputNumber
                  aria-label="Maximum V2 Run percentage"
                  value={maximumV2RunPct}
                  min={0}
                  max={1_000}
                  addonAfter="%"
                  placeholder="No limit"
                  disabled={!hasV2TradeMetrics}
                  onChange={setMaximumV2RunPct}
                />
                <Text type="secondary">Showing {targetOutcomeSummary.total} of {tradeRows.length} trades</Text>
              </Space>
            </Space>
            <Space size={16} wrap style={{ marginTop: 8 }}>
              <Text strong>Filtered total: {targetOutcomeSummary.total}</Text>
              <Text type="success">Target hit: {targetOutcomeSummary.targetHits}</Text>
              <Text type="danger">Stop hit: {targetOutcomeSummary.stopLossHits}</Text>
              <Text>Unresolved: {targetOutcomeSummary.unresolved}</Text>
              <Text strong type="success">
                Target hit rate: {targetOutcomeSummary.targetHitRatePct.toFixed(1)}%
              </Text>
            </Space>
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
                  dataSource={displayedTrades}
                  columns={tradesColumns} 
                  rowKey="tableRowId"
                  onChange={handleTradesTableChange}
                  pagination={{ pageSize: 100 }}
                  size="small"
                  scroll={{ x: 'max-content' }}
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
                  optionLabelProp="value"
                  options={getOptions()}
                />
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
