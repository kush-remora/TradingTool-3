import { ReloadOutlined, HistoryOutlined } from "@ant-design/icons";
import { Alert, Button, Card, Descriptions, Drawer, Empty, Input, Space, Spin, Switch, Table, Tag, Tooltip, Typography, message } from "antd";
import type { TableColumnsType } from "antd";
import type { Key } from "react";
import { useEffect, useMemo, useState } from "react";
import type { WeeklyPatternListResponse, WeeklyPatternResult } from "../types";
import { clearCache, getJson, postJson } from "../utils/api";
import { StockBadge } from "../components/StockBadge";

const { Text, Title } = Typography;

function bucketFilters(results: WeeklyPatternResult[]): { text: string; value: string }[] {
  const buckets = new Set<string>();
  results.forEach((row) => (row.sourceBuckets ?? []).forEach((bucket) => buckets.add(bucket)));
  return Array.from(buckets).sort().map((bucket) => ({ text: bucket, value: bucket }));
}

function stockFilters(results: WeeklyPatternResult[]): { text: string; value: string }[] {
  return results
    .map((row) => ({ text: `${row.symbol} - ${row.companyName}`, value: row.symbol }))
    .sort((a, b) => a.value.localeCompare(b.value));
}

export function WeeklySwingPage() {
  const [data, setData] = useState<WeeklyPatternListResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [selectedRow, setSelectedRow] = useState<WeeklyPatternResult | null>(null);
  const [selectedRowKeys, setSelectedRowKeys] = useState<Key[]>([]);
  const [stockQuery, setStockQuery] = useState("");
  const [showSelectedOnly, setShowSelectedOnly] = useState(false);

  const fetchData = async (forceRefresh = false) => {
    setLoading(true);
    try {
      const path = "/api/screener/weekly-pattern";
      if (forceRefresh) clearCache(path);
      const json = await getJson<WeeklyPatternListResponse>(path);
      setData(json);
    } catch {
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
    } catch {
      message.error("Sync failed");
    } finally {
      setSyncing(false);
    }
  };

  useEffect(() => {
    void fetchData();
  }, []);

  const baseResults = useMemo(() => (data?.results ?? []).filter((row) => row.patternConfirmed), [data]);

  const searchedResults = useMemo(() => {
    const q = stockQuery.trim().toLowerCase();
    if (!q) return baseResults;
    return baseResults.filter((row) => row.symbol.toLowerCase().includes(q) || row.companyName.toLowerCase().includes(q));
  }, [baseResults, stockQuery]);

  const tableData = useMemo(() => {
    if (!showSelectedOnly) return searchedResults;
    const selected = new Set(selectedRowKeys.map((key) => String(key)));
    return searchedResults.filter((row) => selected.has(row.symbol));
  }, [searchedResults, showSelectedOnly, selectedRowKeys]);

  const columns: TableColumnsType<WeeklyPatternResult> = [
    {
      title: "Stock",
      dataIndex: "symbol",
      key: "symbol",
      fixed: "left",
      width: 220,
      sorter: (a, b) => a.symbol.localeCompare(b.symbol),
      filters: stockFilters(baseResults),
      filterMultiple: true,
      filterSearch: true,
      onFilter: (value, record) => record.symbol === String(value),
      render: (text: string, record: WeeklyPatternResult) => (
        <StockBadge symbol={text} instrumentToken={record.instrumentToken} companyName={record.companyName} fontSize={15} />
      ),
    },
    {
      title: "Bucket",
      key: "sourceBuckets",
      width: 280,
      sorter: (a, b) => (a.sourceBuckets ?? []).join(",").localeCompare((b.sourceBuckets ?? []).join(",")),
      filters: bucketFilters(baseResults),
      filterMultiple: true,
      filterSearch: true,
      onFilter: (value, record) => (record.sourceBuckets ?? []).includes(String(value)),
      render: (_: unknown, record: WeeklyPatternResult) => {
        const buckets = record.sourceBuckets ?? [];
        if (buckets.length === 0) return "-";
        return (
          <Space size={4} wrap>
            {buckets.slice(0, 2).map((bucket) => <Tag key={bucket}>{bucket}</Tag>)}
            {buckets.length > 2 ? <Tag>+{buckets.length - 2}</Tag> : null}
          </Space>
        );
      },
    },
    {
      title: <Tooltip title="VCP Tightness = avg % range of last 4 weeks. Lower means tighter price contraction and better absorption.">VCP Tightness</Tooltip>,
      dataIndex: "vcpTightnessPct",
      key: "vcpTightnessPct",
      width: 150,
      sorter: (a, b) => (a.vcpTightnessPct || 0) - (b.vcpTightnessPct || 0),
      filters: [
        { text: "<= 5%", value: "vcp_0_5" },
        { text: "5-10%", value: "vcp_5_10" },
        { text: "> 10%", value: "vcp_10_inf" },
      ],
      filterMultiple: true,
      onFilter: (value, record) => {
        const v = record.vcpTightnessPct ?? Number.POSITIVE_INFINITY;
        return (value === "vcp_0_5" && v <= 5) ||
          (value === "vcp_5_10" && v > 5 && v <= 10) ||
          (value === "vcp_10_inf" && v > 10);
      },
      render: (val: number | null) => {
        if (val == null) return "-";
        const color = val <= 5 ? "success" : val <= 10 ? "warning" : "default";
        return <Tag color={color} style={{ fontWeight: 600 }}>{val.toFixed(2)}%</Tag>;
      }
    },
    {
      title: "Vol Signature",
      dataIndex: "volumeSignatureRatio",
      key: "volumeSignatureRatio",
      width: 150,
      sorter: (a, b) => (a.volumeSignatureRatio || 0) - (b.volumeSignatureRatio || 0),
      filters: [
        { text: "< 1.0x", value: "vol_0_1" },
        { text: "1.0x - 1.5x", value: "vol_1_1_5" },
        { text: ">= 1.5x", value: "vol_1_5_inf" },
      ],
      filterMultiple: true,
      onFilter: (value, record) => {
        const v = record.volumeSignatureRatio ?? 0;
        return (value === "vol_0_1" && v < 1.0) ||
          (value === "vol_1_1_5" && v >= 1.0 && v < 1.5) ||
          (value === "vol_1_5_inf" && v >= 1.5);
      },
      render: (val: number | null) => {
        if (val == null) return "-";
        return <Text strong style={{ color: val >= 1.5 ? "#52c41a" : undefined }}>{val.toFixed(2)}x</Text>;
      }
    },
    {
      title: "Monday Strike Rate",
      dataIndex: "mondayStrikeRatePct",
      key: "mondayStrikeRatePct",
      width: 170,
      sorter: (a, b) => (a.mondayStrikeRatePct || 0) - (b.mondayStrikeRatePct || 0),
      filters: [
        { text: "< 50%", value: "msr_0_50" },
        { text: "50-70%", value: "msr_50_70" },
        { text: ">= 70%", value: "msr_70_inf" },
      ],
      filterMultiple: true,
      onFilter: (value, record) => {
        const v = record.mondayStrikeRatePct ?? 0;
        return (value === "msr_0_50" && v < 50) ||
          (value === "msr_50_70" && v >= 50 && v < 70) ||
          (value === "msr_70_inf" && v >= 70);
      },
      render: (val: number | null) => {
        if (val == null) return "-";
        const color = val >= 70 ? "success" : val >= 50 ? "warning" : "error";
        return <Tag color={color} style={{ borderRadius: 12, fontWeight: 700 }}>{val.toFixed(0)}%</Tag>;
      }
    },
    {
      title: "Buy Zone",
      key: "buyZoneRange",
      width: 220,
      sorter: (a, b) => a.buyDayLowMin - b.buyDayLowMin,
      filters: [
        { text: "< ₹200", value: "zone_0_200" },
        { text: "₹200-₹500", value: "zone_200_500" },
        { text: "₹500-₹1000", value: "zone_500_1000" },
        { text: ">= ₹1000", value: "zone_1000_inf" },
      ],
      filterMultiple: true,
      onFilter: (value, record) => {
        const v = record.buyDayLowMin;
        return (value === "zone_0_200" && v < 200) ||
          (value === "zone_200_500" && v >= 200 && v < 500) ||
          (value === "zone_500_1000" && v >= 500 && v < 1000) ||
          (value === "zone_1000_inf" && v >= 1000);
      },
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
      width: 130,
      sorter: (a, b) => (a.expectedSwingPct ?? 0) - (b.expectedSwingPct ?? 0),
      filters: [
        { text: "< 4%", value: "swing_0_4" },
        { text: "4-6%", value: "swing_4_6" },
        { text: "6-8%", value: "swing_6_8" },
        { text: ">= 8%", value: "swing_8_inf" },
      ],
      filterMultiple: true,
      onFilter: (value, record) => {
        const v = record.expectedSwingPct ?? 0;
        return (value === "swing_0_4" && v < 4) ||
          (value === "swing_4_6" && v >= 4 && v < 6) ||
          (value === "swing_6_8" && v >= 6 && v < 8) ||
          (value === "swing_8_inf" && v >= 8);
      },
      render: (val: number | undefined) => val == null ? "-" : <Text strong style={{ color: "#1677ff" }}>{val.toFixed(2)}%</Text>
    },
    {
      title: "Baseline Dist",
      dataIndex: "baselineDistancePct",
      key: "baselineDistancePct",
      width: 140,
      sorter: (a, b) => (a.baselineDistancePct ?? Number.POSITIVE_INFINITY) - (b.baselineDistancePct ?? Number.POSITIVE_INFINITY),
      filters: [
        { text: "<= 8%", value: "base_0_8" },
        { text: "8-15%", value: "base_8_15" },
        { text: "> 15%", value: "base_15_inf" },
      ],
      filterMultiple: true,
      onFilter: (value, record) => {
        const v = record.baselineDistancePct ?? Number.POSITIVE_INFINITY;
        return (value === "base_0_8" && v <= 8) ||
          (value === "base_8_15" && v > 8 && v <= 15) ||
          (value === "base_15_inf" && v > 15);
      },
      render: (val: number | null | undefined) => {
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
      width: 120,
      sorter: (a, b) => (a.setupQualityScore ?? 0) - (b.setupQualityScore ?? 0),
      filters: [
        { text: "< 40", value: "score_0_40" },
        { text: "40-70", value: "score_40_70" },
        { text: ">= 70", value: "score_70_inf" },
      ],
      filterMultiple: true,
      onFilter: (value, record) => {
        const v = record.setupQualityScore ?? 0;
        return (value === "score_0_40" && v < 40) ||
          (value === "score_40_70" && v >= 40 && v < 70) ||
          (value === "score_70_inf" && v >= 70);
      },
      render: (val: number | undefined) => {
        if (val == null) return "-";
        const color = val > 70 ? "#389e0d" : (val > 40 ? "#fa8c16" : "#8c8c8c");
        return <Text strong style={{ color, fontSize: 16 }}>{val}</Text>;
      },
    },
  ];

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

        <Card size="small" style={{ borderRadius: 12 }}>
          <Space align="center" wrap style={{ width: "100%", justifyContent: "space-between" }}>
            <Input
              allowClear
              placeholder="Search stock (symbol/company)"
              value={stockQuery}
              onChange={(e) => setStockQuery(e.target.value)}
              style={{ width: 320 }}
            />
            <Space align="center">
              <Text type="secondary">Show selected only</Text>
              <Switch checked={showSelectedOnly} onChange={setShowSelectedOnly} />
              <Tag color="blue">Selected: {selectedRowKeys.length}</Tag>
            </Space>
          </Space>
        </Card>

        {loading ? (
          <div style={{ textAlign: "center", padding: 64 }}>
            <Spin size="large" />
          </div>
        ) : tableData.length === 0 ? (
          <Card>
            <Empty description="No weekly swing patterns found for the current filter." />
          </Card>
        ) : (
          <Card size="small" style={{ borderRadius: 12 }}>
            <Table
              columns={columns}
              dataSource={tableData}
              rowKey="symbol"
              pagination={{ pageSize: 30, showSizeChanger: true }}
              size="small"
              scroll={{ x: 1650 }}
              rowSelection={{
                selectedRowKeys,
                onChange: (keys) => setSelectedRowKeys(keys),
              }}
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
              <li><b>VCP Tightness:</b> Average weekly high-low range over last 4 weeks. Lower means tighter institutional absorption.</li>
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
                <Descriptions.Item label="Buckets">
                  {(selectedRow.sourceBuckets ?? []).join(", ") || "-"}
                </Descriptions.Item>
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
