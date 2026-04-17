import { ReloadOutlined, HistoryOutlined } from "@ant-design/icons";
import { Alert, Button, Card, Descriptions, Drawer, Empty, Space, Spin, Table, Tag, Typography, message } from "antd";
import type { TableColumnsType } from "antd";
import { useEffect, useMemo, useState } from "react";
import type { WeeklyPatternListResponse, WeeklyPatternResult } from "../types";
import { clearCache, getJson, postJson } from "../utils/api";
import { StockBadge } from "../components/StockBadge";

const { Text, Title } = Typography;

export function WeeklySwingPage() {
  const [data, setData] = useState<WeeklyPatternListResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [selectedRow, setSelectedRow] = useState<WeeklyPatternResult | null>(null);

  const fetchData = async (forceRefresh = false) => {
    setLoading(true);
    try {
      const path = "/api/screener/weekly-pattern";
      if (forceRefresh) clearCache(path);
      const json = await getJson<WeeklyPatternListResponse>(path);
      setData(json);
    } catch (err) {
      message.error("Failed to fetch weekly swing data");
    } finally {
      setLoading(false);
    }
  };

  const handleSync = async () => {
    setSyncing(true);
    try {
      await postJson("/api/screener/sync", {});
      message.success("Sync complete");
      await fetchData(true);
    } catch (err) {
      message.error("Sync failed");
    } finally {
      setSyncing(false);
    }
  };

  useEffect(() => {
    void fetchData();
  }, []);

  const columns: TableColumnsType<WeeklyPatternResult> = [
    {
      title: "Stock",
      dataIndex: "symbol",
      key: "symbol",
      fixed: "left",
      width: 200,
      render: (text: string, record: WeeklyPatternResult) => (
        <StockBadge symbol={text} instrumentToken={record.instrumentToken} companyName={record.companyName} fontSize={15} />
      ),
    },
    {
        title: "VCP Tightness",
        dataIndex: "vcpTightnessPct",
        key: "vcpTightnessPct",
        width: 140,
        sorter: (a, b) => (a.vcpTightnessPct || 0) - (b.vcpTightnessPct || 0),
        render: (val: number) => {
            if (val == null) return "-";
            const color = val <= 5 ? "success" : val <= 10 ? "warning" : "default";
            return <Tag color={color} style={{ fontWeight: 600 }}>{val.toFixed(2)}%</Tag>;
        }
    },
    {
        title: "Vol Signature",
        dataIndex: "volumeSignatureRatio",
        key: "volumeSignatureRatio",
        width: 140,
        sorter: (a, b) => (a.volumeSignatureRatio || 0) - (b.volumeSignatureRatio || 0),
        render: (val: number) => {
            if (val == null) return "-";
            const color = val >= 1.5 ? "success" : "default";
            return <Text strong style={{ color: val >= 1.5 ? "#52c41a" : undefined }}>{val.toFixed(2)}x</Text>;
        }
    },
    {
        title: "Monday Strike Rate",
        dataIndex: "mondayStrikeRatePct",
        key: "mondayStrikeRatePct",
        width: 160,
        sorter: (a, b) => (a.mondayStrikeRatePct || 0) - (b.mondayStrikeRatePct || 0),
        render: (val: number) => {
            if (val == null) return "-";
            const color = val >= 70 ? "success" : val >= 50 ? "warning" : "error";
            return <Tag color={color} style={{ borderRadius: 12, fontWeight: 700 }}>{val.toFixed(0)}%</Tag>;
        }
    },
    {
      title: "Wholesale Floor (Buy Zone)",
      key: "buyZoneRange",
      width: 200,
      render: (_: unknown, record: WeeklyPatternResult) => (
        <div style={{ display: "flex", flexDirection: "column" }}>
          <Text strong style={{ color: "#0958d9" }}>₹{record.buyDayLowMin.toFixed(2)} - ₹{record.buyDayLowMax.toFixed(2)}</Text>
          <Text type="secondary" style={{ fontSize: 11 }}>Historical {record.buyDay} lows</Text>
        </div>
      ),
    },
    {
      title: "Expected Swing",
      dataIndex: "expectedSwingPct",
      key: "expectedSwingPct",
      width: 120,
      sorter: (a, b) => (a.expectedSwingPct ?? 0) - (b.expectedSwingPct ?? 0),
      render: (val: number | undefined) => val == null ? "-" : <Text strong style={{ color: "#1677ff" }}>{val.toFixed(2)}%</Text>
    },
    {
      title: "Baseline Dist",
      dataIndex: "baselineDistancePct",
      key: "baselineDistancePct",
      width: 130,
      sorter: (a, b) => (a.baselineDistancePct ?? Number.POSITIVE_INFINITY) - (b.baselineDistancePct ?? Number.POSITIVE_INFINITY),
      render: (val: number | null) => {
        if (val == null) return "-";
        const color = val <= 8 ? "success" : val <= 15 ? "warning" : "default";
        return <Tag color={color}>{val.toFixed(2)}%</Tag>;
      },
    },
    {
      title: "Setup Score",
      dataIndex: "setupQualityScore",
      key: "setupQualityScore",
      fixed: "right",
      width: 110,
      render: (val: number | undefined) => {
        if (val == null) return "-";
        const color = val > 70 ? "#389e0d" : (val > 40 ? "#fa8c16" : "#8c8c8c");
        return <Text strong style={{ color, fontSize: 16 }}>{val}</Text>;
      },
    },
  ];

  const filteredResults = useMemo(() => {
    return (data?.results ?? []).filter(r => r.patternConfirmed);
  }, [data]);

  return (
    <div style={{ padding: 24, background: "#f5f7fa", minHeight: "calc(100vh - 48px)" }}>
      <Space direction="vertical" size={16} style={{ width: "100%" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <div>
            <Title level={4} style={{ margin: 0 }}>
                <HistoryOutlined style={{ marginRight: 8, color: "#1677ff" }} />
                Weekly Swing Accumulation
            </Title>
            <Text type="secondary">
              Stocks coiling in a 5-8% range (VCP) with strong Monday entry probability.
            </Text>
            {!!data?.universeSourceTags?.length && (
              <div style={{ marginTop: 4 }}>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  Universe: {data.universeSourceTags.join(", ")}
                </Text>
              </div>
            )}
          </div>
          <Button icon={<ReloadOutlined />} onClick={handleSync} loading={syncing}>
            Refresh
          </Button>
        </div>

        {loading ? (
          <div style={{ textAlign: "center", padding: 64 }}>
            <Spin size="large" />
          </div>
        ) : filteredResults.length === 0 ? (
          <Card>
            <Empty description="No confirmed weekly swing patterns found." />
          </Card>
        ) : (
          <Card size="small" style={{ borderRadius: 12 }}>
            <Table
              columns={columns}
              dataSource={filteredResults}
              rowKey="symbol"
              pagination={false}
              size="small"
              scroll={{ x: 1200 }}
              onRow={(record) => ({
                onClick: () => setSelectedRow(record),
                style: { cursor: "pointer" },
              })}
            />
          </Card>
        )}

        <Alert 
            type="success"
            showIcon
            message="Wholesale Floor Entry Strategy"
            description={
                <ul style={{ margin: 0, paddingLeft: 16 }}>
                    <li><b>VCP Tightness:</b> Lower is better. Indicates institutional absorption.</li>
                    <li><b>Vol Signature:</b> Ratio &gt; 1.5x means buying pressure exceeds selling.</li>
                    <li><b>Monday Strike Rate:</b> % chance of hitting 5% profit if bought on Monday Open.</li>
                    <li><b>Buy Zone:</b> Only enter when price is near the historical low range.</li>
                </ul>
            }
        />

        <Drawer
          title={selectedRow ? `${selectedRow.symbol} Swing Setup` : "Swing Setup"}
          open={selectedRow != null}
          onClose={() => setSelectedRow(null)}
          width={520}
        >
          {selectedRow?.swingSetup ? (
            <Space direction="vertical" size={16} style={{ width: "100%" }}>
              <Descriptions column={1} size="small" bordered>
                <Descriptions.Item label="Buy Zone">
                  ₹{selectedRow.swingSetup.buyZoneMin.toFixed(2)} - ₹{selectedRow.swingSetup.buyZoneMax.toFixed(2)}
                </Descriptions.Item>
                <Descriptions.Item label="Target Plan">
                  Safe {selectedRow.swingSetup.safeTargetPct.toFixed(2)}% | Recommended {selectedRow.swingSetup.recommendedTargetPct.toFixed(2)}% | Aggressive {selectedRow.swingSetup.aggressiveTargetPct.toFixed(2)}%
                </Descriptions.Item>
                <Descriptions.Item label="Hard Stop-Loss">
                  {selectedRow.swingSetup.hardStopLossPct.toFixed(2)}%
                </Descriptions.Item>
                <Descriptions.Item label="Expected Swing">
                  {selectedRow.swingSetup.expectedSwingPct.toFixed(2)}%
                </Descriptions.Item>
                <Descriptions.Item label="Confidence">
                  <Tag color={selectedRow.swingSetup.confidence === "HIGH" ? "success" : selectedRow.swingSetup.confidence === "MEDIUM" ? "warning" : "default"}>
                    {selectedRow.swingSetup.confidence}
                  </Tag>
                </Descriptions.Item>
                <Descriptions.Item label="Invalidation">
                  {selectedRow.swingSetup.invalidationCondition}
                </Descriptions.Item>
              </Descriptions>

              <Card size="small" title="Reasoning">
                <Text>{selectedRow.swingSetup.reasoning}</Text>
              </Card>
            </Space>
          ) : (
            <Empty description="No swing setup available for this stock." />
          )}
        </Drawer>
      </Space>
    </div>
  );
}
