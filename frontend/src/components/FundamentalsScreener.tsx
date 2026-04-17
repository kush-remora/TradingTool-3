import { ReloadOutlined } from "@ant-design/icons";
import { Button, Checkbox, Select, Space, Statistic, Table, Tag, Typography, message } from "antd";
import type { TableColumnsType } from "antd";
import { useEffect, useMemo, useState } from "react";
import type { FundamentalsTagOverviewResponse, FundamentalsTableRow } from "../types";
import { getJson, postJson } from "../utils/api";

const { Text, Title } = Typography;

const TAG_OPTIONS = [
  { label: "Nifty 50", value: "NIFTY_50" },
  { label: "Nifty 100", value: "NIFTY_100" },
  { label: "Nifty 200", value: "NIFTY_200" },
  { label: "Nifty Smallcap 250", value: "NIFTY_SMALLCAP_250" },
];

const PROFILE_OPTIONS = [
  { label: "Standard", value: "standard" },
  { label: "Extreme", value: "extreme" },
];

function formatNullableNumber(value: number | null | undefined, digits = 2): string {
  if (value === null || value === undefined) return "-";
  return value.toFixed(digits);
}

function formatPrice(value: number | null | undefined): string {
  if (value === null || value === undefined) return "-";
  return `₹${value.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

export function FundamentalsScreener() {
  const [tag, setTag] = useState<string>("NIFTY_50");
  const [profile, setProfile] = useState<string>("standard");
  const [strictMissingData, setStrictMissingData] = useState<boolean>(false);
  const [loading, setLoading] = useState<boolean>(false);
  const [refreshing, setRefreshing] = useState<boolean>(false);
  const [filtering, setFiltering] = useState<boolean>(false);
  const [data, setData] = useState<FundamentalsTagOverviewResponse | null>(null);

  const loadByTag = async () => {
    setLoading(true);
    try {
      const payload = await getJson<FundamentalsTagOverviewResponse>(
        `/api/screener/fundamentals/by-tag?tag=${encodeURIComponent(tag)}`,
      );
      setData(payload);
    } catch (error) {
      console.error(error);
      message.error("Failed to load fundamentals for selected tag");
    } finally {
      setLoading(false);
    }
  };

  const refreshFundamentals = async () => {
    setRefreshing(true);
    try {
      await postJson(`/api/screener/fundamentals/refresh-by-tag?tag=${encodeURIComponent(tag)}`, {});
      message.success("Fundamentals refreshed");
      await loadByTag();
    } catch (error) {
      console.error(error);
      message.error("Failed to refresh fundamentals");
    } finally {
      setRefreshing(false);
    }
  };

  const applyFilter = async () => {
    setFiltering(true);
    try {
      const payload = await postJson<FundamentalsTagOverviewResponse>("/api/screener/fundamentals/filter", {
        tag,
        profile,
        strictMissingData,
      });
      setData(payload);
    } catch (error) {
      console.error(error);
      message.error("Failed to apply filter");
    } finally {
      setFiltering(false);
    }
  };

  useEffect(() => {
    void loadByTag();
  }, [tag]);

  const rows = data?.rows ?? [];
  const selectedCount = data?.selectedCount ?? null;
  const rejectedCount = data?.rejectedCount ?? null;

  const columns: TableColumnsType<FundamentalsTableRow> = useMemo(
    () => [
      {
        title: "Stock",
        dataIndex: "symbol",
        key: "symbol",
        fixed: "left",
        width: 170,
        render: (_: unknown, row: FundamentalsTableRow) => (
          <div style={{ display: "flex", flexDirection: "column" }}>
            <Text strong>{row.symbol}</Text>
            <Text type="secondary" style={{ fontSize: 12 }}>{row.companyName}</Text>
          </div>
        ),
      },
      {
        title: "Filtered",
        key: "filtered",
        width: 120,
        render: (_: unknown, row: FundamentalsTableRow) => {
          if (row.isSelected === null) return <Text type="secondary">Not run</Text>;
          return (
            <Tag color={row.isSelected ? "success" : "error"} style={{ margin: 0, fontWeight: 600 }}>
              {row.isSelected ? "Selected" : "Rejected"}
            </Tag>
          );
        },
      },
      {
        title: "Reasons",
        dataIndex: "filterReasons",
        key: "filterReasons",
        width: 260,
        render: (reasons: string[]) => (
          reasons.length === 0
            ? <Text type="secondary">-</Text>
            : <Text style={{ fontSize: 12 }}>{reasons.join(", ")}</Text>
        ),
      },
      {
        title: "LTP",
        dataIndex: "ltp",
        key: "ltp",
        width: 120,
        render: (value: number | null) => <Text strong>{formatPrice(value)}</Text>,
      },
      {
        title: "RSI14",
        dataIndex: "rsi14",
        key: "rsi14",
        width: 90,
        render: (value: number | null) => formatNullableNumber(value),
      },
      {
        title: "ROC 1W %",
        dataIndex: "roc1w",
        key: "roc1w",
        width: 110,
        render: (value: number | null) => formatNullableNumber(value),
      },
      {
        title: "ROC 3M %",
        dataIndex: "roc3m",
        key: "roc3m",
        width: 110,
        render: (value: number | null) => formatNullableNumber(value),
      },
      {
        title: "ROCE %",
        dataIndex: "rocePercent",
        key: "rocePercent",
        width: 100,
        render: (value: number | null) => formatNullableNumber(value),
      },
      {
        title: "ROE %",
        dataIndex: "roePercent",
        key: "roePercent",
        width: 100,
        render: (value: number | null) => formatNullableNumber(value),
      },
      {
        title: "Promoter Holding %",
        dataIndex: "promoterHoldingPercent",
        key: "promoterHoldingPercent",
        width: 170,
        render: (value: number | null) => formatNullableNumber(value),
      },
      {
        title: "Market Cap (Cr)",
        dataIndex: "marketCapCr",
        key: "marketCapCr",
        width: 140,
        render: (value: number | null) => (
          value === null
            ? "-"
            : value.toLocaleString("en-IN", { maximumFractionDigits: 0 })
        ),
      },
      {
        title: "Snapshot Date",
        dataIndex: "fundamentalsSnapshotDate",
        key: "fundamentalsSnapshotDate",
        width: 130,
        render: (value: string | null) => value ?? "-",
      },
      {
        title: "Industry",
        dataIndex: "industry",
        key: "industry",
        width: 180,
        render: (value: string | null) => value ?? "-",
      },
    ],
    [],
  );

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 12 }}>
        <div>
          <Title level={4} style={{ margin: 0 }}>Fundamentals Screener</Title>
          <Text type="secondary">Load full index-tag universe, then run profile-based filtering.</Text>
        </div>
        <Space wrap>
          <Select value={tag} options={TAG_OPTIONS} style={{ width: 210 }} onChange={setTag} />
          <Select value={profile} options={PROFILE_OPTIONS} style={{ width: 130 }} onChange={setProfile} />
          <Checkbox checked={strictMissingData} onChange={(e) => setStrictMissingData(e.target.checked)}>
            Strict missing data
          </Checkbox>
          <Button onClick={loadByTag} loading={loading}>Load</Button>
          <Button icon={<ReloadOutlined />} onClick={refreshFundamentals} loading={refreshing}>
            Refresh Fundamentals
          </Button>
          <Button type="primary" onClick={applyFilter} loading={filtering}>Apply Filter</Button>
        </Space>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr))", gap: 12 }}>
        <Statistic title="Total" value={data?.totalStocks ?? 0} />
        <Statistic title="Selected" value={selectedCount ?? 0} />
        <Statistic title="Rejected" value={rejectedCount ?? 0} />
        <Statistic title="Profile" value={(data?.profile ?? "none").toUpperCase()} />
      </div>

      <Table<FundamentalsTableRow>
        rowKey={(row) => row.symbol}
        columns={columns}
        dataSource={rows}
        loading={loading || filtering || refreshing}
        size="small"
        pagination={{ pageSize: 30, showSizeChanger: false }}
        scroll={{ x: 1900 }}
      />
    </Space>
  );
}
