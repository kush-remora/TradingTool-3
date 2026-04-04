import { useEffect, useState } from "react";
import { Table, Tag, Typography, Button, Space, message, Segmented } from "antd";
import { ReloadOutlined } from "@ant-design/icons";
import { WeeklyPatternListResponse, WeeklyPatternResult } from "../types";
import { StockBadge } from "./StockBadge";
import { getJson, postJson, clearCache } from "../utils/api";

const { Text, Title } = Typography;

interface ScreenerOverviewProps {
  onSelectSymbol: (symbol: string) => void;
}

export function ScreenerOverview({ onSelectSymbol }: ScreenerOverviewProps) {
  const [data, setData] = useState<WeeklyPatternListResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [filter, setFilter] = useState("All stocks");

  const fetchData = async (forceRefresh = false) => {
    setLoading(true);
    try {
      const path = "/api/screener/weekly-pattern";
      if (forceRefresh) clearCache(path);
      
      const json = await getJson<WeeklyPatternListResponse>(path);
      setData(json);
    } catch (err) {
      console.error(err);
      message.error("Failed to fetch pattern data");
    } finally {
      setLoading(false);
    }
  };

  const handleSync = async () => {
    setSyncing(true);
    try {
      await postJson("/api/screener/sync", {});
      message.success("Sync complete");
      fetchData(true);
    } catch (err) {
      message.error("Sync failed");
    } finally {
      setSyncing(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const filteredResults = data?.results?.filter((row) => {
    if (filter === "Weekly pattern") return row.patternConfirmed;
    if (filter === "Strong (score > 70)") return row.compositeScore > 70;
    if (filter === "No pattern") return !row.patternConfirmed;
    return true;
  }) || [];

  const columns = [
    {
      title: "Stock",
      dataIndex: "symbol",
      key: "symbol",
      render: (text: string, record: WeeklyPatternResult) => (
        <div style={{ display: "flex", flexDirection: "column" }}>
          <StockBadge symbol={text} instrumentToken={record.instrumentToken} companyName={record.companyName} fontSize={15} />
          <Text type="secondary" style={{ fontSize: 12 }}>{record.companyName}</Text>
        </div>
      )
    },
    {
      title: "Cycle",
      dataIndex: "cycleType",
      key: "cycleType",
      render: (val: string) => {
        let color = "default";
        if (val === "Weekly") color = "success";
        if (val === "Biweekly") color = "warning";
        return <Tag color={color} style={{ borderRadius: 12, padding: '0 12px', fontWeight: 600 }}>{val}</Tag>;
      }
    },
    {
      title: "Buy zone",
      key: "buyZone",
      render: (_: any, record: WeeklyPatternResult) => {
        if (!record.patternConfirmed) return <Text type="secondary">-</Text>;
        return <Text strong style={{ color: '#0958d9' }}>₹{record.buyDayLowMin} - ₹{record.buyDayLowMax}</Text>;
      }
    },
    {
      title: 'Valid Entries',
      key: 'validEntries',
      render: (_: any, record: WeeklyPatternResult) => {
        const skipped = record.weeksAnalyzed - record.reboundConsistency;
        return (
          <div>
            <Text strong>{record.reboundConsistency} / {record.weeksAnalyzed}</Text>
            <div style={{ fontSize: 12, color: '#8c8c8c' }}>{skipped} Skipped</div>
          </div>
        );
      },
      sorter: (a: WeeklyPatternResult, b: WeeklyPatternResult) => a.reboundConsistency - b.reboundConsistency,
    },
    {
      title: 'Swing Logic',
      key: 'swingLogic',
      render: (_: any, record: WeeklyPatternResult) => {
        const winPercent = record.reboundConsistency > 0 
          ? Math.round((record.swingConsistency / record.reboundConsistency) * 100) 
          : 0;
        return (
          <div>
            <Text strong>{record.sellDay} </Text>
            <Tag color={winPercent >= 70 ? 'green' : winPercent > 50 ? 'orange' : 'red'}>{winPercent}% Win</Tag>
            <div style={{ fontSize: 12, color: '#8c8c8c' }}>
              {record.swingConsistency} W / {(record.reboundConsistency - record.swingConsistency)} L
            </div>
            <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 2 }}>actual {record.swingAvgPct}%</div>
          </div>
        );
      },
    },
    {
      title: 'Ideal Potential',
      key: 'idealPotential',
      render: (_: any, record: WeeklyPatternResult) => {
        const captureRatio = record.avgPotentialPct > 0 
          ? Math.round((record.swingAvgPct / record.avgPotentialPct) * 100)
          : 0;
        return (
          <div>
            <Text strong style={{ color: '#0958d9' }}>{record.avgPotentialPct}%</Text>
            <div style={{ fontSize: 11, color: '#8c8c8c' }}>
              Capturing {captureRatio}% of swing
            </div>
          </div>
        );
      },
      sorter: (a: WeeklyPatternResult, b: WeeklyPatternResult) => a.avgPotentialPct - b.avgPotentialPct,
    },
    {
      title: "Score",
      dataIndex: "compositeScore",
      key: "score",
      render: (val: number) => {
        let color = val > 70 ? '#389e0d' : (val > 40 ? '#fa8c16' : '#8c8c8c');
        return <Text strong style={{ color, fontSize: 16 }}>{val}</Text>;
      }
    }
  ];

  return (
    <div style={{ padding: "24px 32px", maxWidth: 1200, margin: "0 auto" }}>
      <div style={{ background: '#fff', borderRadius: 12, padding: 24, boxShadow: '0 4px 12px rgba(0,0,0,0.05)' }}>
        
        {/* Header Section */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
          <div>
            <Title level={4} style={{ margin: 0, padding: 0 }}>Pattern screener</Title>
            <Text type="secondary" style={{ fontSize: 13 }}>
              Last run: {data ? new Date(data.runAt).toLocaleString() : "Never"} • {data?.lookbackWeeks || 8}-week lookback
            </Text>
          </div>
          <Button 
            type="default" 
            icon={<ReloadOutlined />} 
            onClick={handleSync} 
            loading={syncing}
            style={{ borderRadius: 6, fontWeight: 500 }}
          >
            Refresh patterns
          </Button>
        </div>

        {/* Filters */}
        <div style={{ marginBottom: 24 }}>
          <Segmented
            options={['All stocks', 'Weekly pattern', 'Strong (score > 70)', 'No pattern']}
            value={filter}
            onChange={(val) => setFilter(val as string)}
            style={{ padding: 4 }}
          />
        </div>

        {/* List/Table */}
        <Table<WeeklyPatternResult>
          dataSource={filteredResults}
          columns={columns}
          rowKey="symbol"
          pagination={false}
          loading={loading}
          onRow={(record) => ({
            onClick: () => onSelectSymbol(record.symbol),
            style: { cursor: 'pointer' }
          })}
          rowClassName={() => 'screener-row'}
          size="middle"
        />
      </div>

      <style>{`
        .screener-row:hover > td {
          background-color: #fafafa !important;
        }
      `}</style>
    </div>
  );
}
