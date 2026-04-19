import { ReloadOutlined, SearchOutlined } from "@ant-design/icons";
import {
  Alert,
  Button,
  Card,
  Col,
  Empty,
  Input,
  Row,
  Segmented,
  Select,
  Space,
  Spin,
  Statistic,
  Table,
  Tabs,
  Tag,
  Typography,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { useMemo, useState } from "react";
import { useRsiMomentumLeadersDrawdown } from "../hooks/useRsiMomentumLeadersDrawdown";
import type {
  DrawdownBucketSummary,
  LeadersDrawdownProfileSection,
  MomentumLeaderRow,
} from "../types";

type DrawdownMode = "today" | "min20";

const THRESHOLD_OPTIONS: number[] = [20, 30, 40, 50, 60];

function formatInr(value: number | null): string {
  if (value == null) return "-";
  return `₹${value.toLocaleString("en-IN", { maximumFractionDigits: 2, minimumFractionDigits: 2 })}`;
}

function formatPct(value: number | null): string {
  if (value == null) return "-";
  return `${value.toFixed(2)}%`;
}

function toInputDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function getBucketTags(row: MomentumLeaderRow, mode: DrawdownMode): number[] {
  const flags = mode === "today" ? row.ddTodayBuckets : row.dd20dMinBuckets;
  return THRESHOLD_OPTIONS.filter((threshold) => {
    if (threshold === 20) return flags.atLeast20Pct;
    if (threshold === 30) return flags.atLeast30Pct;
    if (threshold === 40) return flags.atLeast40Pct;
    if (threshold === 50) return flags.atLeast50Pct;
    return flags.atLeast60Pct;
  });
}

function filterRows(
  rows: MomentumLeaderRow[],
  search: string,
  threshold: number,
  mode: DrawdownMode,
  profileFilter: string,
): MomentumLeaderRow[] {
  const term = search.trim().toLowerCase();
  return rows.filter((row) => {
    const drawdown = mode === "today" ? row.ddTodayPct : row.dd20dMinPct;
    const meetsThreshold = drawdown != null && drawdown <= -threshold;
    if (!meetsThreshold) return false;

    const matchesSearch =
      term.length === 0 ||
      row.symbol.toLowerCase().includes(term) ||
      row.companyName.toLowerCase().includes(term);
    if (!matchesSearch) return false;

    if (profileFilter === "ALL") return true;
    return row.profileIds.includes(profileFilter);
  });
}

function summaryLabel(summary: DrawdownBucketSummary): Array<{ label: string; value: number }> {
  return [
    { label: ">=20%", value: summary.atLeast20Pct },
    { label: ">=30%", value: summary.atLeast30Pct },
    { label: ">=40%", value: summary.atLeast40Pct },
    { label: ">=50%", value: summary.atLeast50Pct },
    { label: ">=60%", value: summary.atLeast60Pct },
  ];
}

function buildColumns(mode: DrawdownMode): ColumnsType<MomentumLeaderRow> {
  return [
  {
    title: "Symbol",
    dataIndex: "symbol",
    key: "symbol",
    width: 120,
    render: (value: string, row: MomentumLeaderRow) => (
      <Space direction="vertical" size={0}>
        <Typography.Text strong>{value}</Typography.Text>
        <Typography.Text type="secondary" style={{ fontSize: 11 }}>
          {row.companyName}
        </Typography.Text>
      </Space>
    ),
  },
  {
    title: "Profiles",
    dataIndex: "profileIds",
    key: "profileIds",
    width: 180,
    render: (value: string[]) => (
      <Space size={[4, 4]} wrap>
        {value.map((id) => (
          <Tag key={id} style={{ margin: 0 }}>{id}</Tag>
        ))}
      </Space>
    ),
  },
  {
    title: "Entry Count",
    dataIndex: "entryCount",
    key: "entryCount",
    width: 110,
    sorter: (a, b) => a.entryCount - b.entryCount,
  },
  {
    title: "Best Rank",
    dataIndex: "bestRank",
    key: "bestRank",
    width: 95,
    sorter: (a, b) => a.bestRank - b.bestRank,
    render: (value: number) => `#${value}`,
  },
  {
    title: "First Seen",
    dataIndex: "firstSeen",
    key: "firstSeen",
    width: 110,
  },
  {
    title: "Last Seen",
    dataIndex: "lastSeen",
    key: "lastSeen",
    width: 110,
  },
  {
    title: "1Y High Close",
    dataIndex: "high1yClose",
    key: "high1yClose",
    width: 120,
    render: (value: number | null) => formatInr(value),
  },
  {
    title: "Today Close",
    dataIndex: "todayClose",
    key: "todayClose",
    width: 120,
    render: (value: number | null) => formatInr(value),
  },
  {
    title: "20D Min Close",
    dataIndex: "minClose20d",
    key: "minClose20d",
    width: 130,
    render: (value: number | null) => formatInr(value),
  },
  {
    title: "DD Today",
    dataIndex: "ddTodayPct",
    key: "ddTodayPct",
    width: 100,
    sorter: (a, b) => (a.ddTodayPct ?? 999) - (b.ddTodayPct ?? 999),
    render: (value: number | null) => formatPct(value),
  },
  {
    title: "DD 20D Min",
    dataIndex: "dd20dMinPct",
    key: "dd20dMinPct",
    width: 110,
    sorter: (a, b) => (a.dd20dMinPct ?? 999) - (b.dd20dMinPct ?? 999),
    render: (value: number | null) => formatPct(value),
  },
  {
    title: "Buckets",
    key: "buckets",
    width: 180,
    render: (_, row) => (
      <Space size={[4, 4]} wrap>
        {getBucketTags(row, mode).map((threshold) => (
          <Tag key={`t-${row.symbol}-${threshold}`} color="blue" style={{ margin: 0 }}>
            T {threshold}
          </Tag>
        ))}
      </Space>
    ),
  },
  ];
}

function SectionCard({
  section,
  mode,
  threshold,
  search,
  profileFilter,
}: {
  section: LeadersDrawdownProfileSection | { rows: MomentumLeaderRow[]; ddTodayBucketSummary: DrawdownBucketSummary; dd20dMinBucketSummary: DrawdownBucketSummary; rowCount: number; warnings: string[] };
  mode: DrawdownMode;
  threshold: number;
  search: string;
  profileFilter: string;
}) {
  const filteredRows = useMemo(
    () => filterRows(section.rows, search, threshold, mode, profileFilter),
    [section.rows, search, threshold, mode, profileFilter],
  );
  const columns = useMemo(() => buildColumns(mode), [mode]);

  const summary = mode === "today" ? section.ddTodayBucketSummary : section.dd20dMinBucketSummary;

  return (
    <Space direction="vertical" size={12} style={{ width: "100%" }}>
      {section.warnings.length > 0 && (
        <Alert
          type="warning"
          showIcon
          message={section.warnings.slice(0, 3).join(" | ")}
          description={section.warnings.length > 3 ? `+${section.warnings.length - 3} more warnings` : undefined}
        />
      )}

      <Row gutter={[12, 12]}>
        {summaryLabel(summary).map((item) => (
          <Col xs={12} md={8} xl={4} key={item.label}>
            <Card size="small">
              <Statistic title={item.label} value={item.value} />
            </Card>
          </Col>
        ))}
        <Col xs={12} md={8} xl={4}>
          <Card size="small">
            <Statistic title="Rows" value={section.rowCount} />
          </Card>
        </Col>
      </Row>

      <Card size="small">
        {filteredRows.length === 0 ? (
          <Empty description="No stocks match current filters." />
        ) : (
          <Table
            columns={columns}
            dataSource={filteredRows}
            rowKey={(row) => `${row.symbol}-${row.profileIds.join("-")}`}
            size="small"
            pagination={{ pageSize: 25, showSizeChanger: true }}
            scroll={{ x: 1600 }}
          />
        )}
      </Card>
    </Space>
  );
}

export function RsiMomentumLeadersDrawdownPage() {
  const defaultToDate = toInputDate(new Date());
  const defaultFromDate = toInputDate(new Date(Date.now() - 365 * 24 * 60 * 60 * 1000));

  const { data, loading, error, query, setQuery, reload } = useRsiMomentumLeadersDrawdown({
    fromDate: defaultFromDate,
    toDate: defaultToDate,
    topN: 10,
  });

  const [fromDateInput, setFromDateInput] = useState(defaultFromDate);
  const [toDateInput, setToDateInput] = useState(defaultToDate);
  const [search, setSearch] = useState("");
  const [mode, setMode] = useState<DrawdownMode>("today");
  const [threshold, setThreshold] = useState<number>(20);
  const [profileFilter, setProfileFilter] = useState<string>("ALL");

  const availableProfiles = useMemo(() => {
    if (!data) return [] as string[];
    const ids = new Set<string>();
    data.profiles.forEach((section) => ids.add(section.profileId));
    return Array.from(ids);
  }, [data]);

  return (
    <div style={{ padding: 24, background: "#f5f7fa", minHeight: "calc(100vh - 48px)" }}>
      <Space direction="vertical" size={16} style={{ width: "100%" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12, flexWrap: "wrap" }}>
          <div>
            <Typography.Title level={4} style={{ margin: 0 }}>RSI Leaders Drawdown</Typography.Title>
            <Typography.Text type="secondary">
              Unique top-10 momentum leaders across one year with drawdown buckets by today close and 20-day min close.
            </Typography.Text>
          </div>
          <Button icon={<ReloadOutlined />} onClick={() => void reload()}>
            Refresh
          </Button>
        </div>

        <Card size="small">
          <Space size={[8, 8]} wrap>
            <input type="date" value={fromDateInput} onChange={(event) => setFromDateInput(event.target.value)} />
            <input type="date" value={toDateInput} onChange={(event) => setToDateInput(event.target.value)} />
            <Button
              type="primary"
              onClick={() => setQuery({ fromDate: fromDateInput, toDate: toDateInput, topN: 10 })}
              disabled={loading}
            >
              Apply Date Range
            </Button>
            <Segmented
              options={[
                { label: "Today DD", value: "today" },
                { label: "20D Min DD", value: "min20" },
              ]}
              value={mode}
              onChange={(nextValue) => setMode(nextValue as DrawdownMode)}
            />
            <Select<number>
              value={threshold}
              style={{ width: 130 }}
              onChange={(value) => setThreshold(value)}
              options={THRESHOLD_OPTIONS.map((value) => ({ value, label: `>= ${value}% DD` }))}
            />
            <Select<string>
              value={profileFilter}
              style={{ width: 180 }}
              onChange={(value) => setProfileFilter(value)}
              options={[{ value: "ALL", label: "All Profiles" }, ...availableProfiles.map((id) => ({ value: id, label: id }))]}
            />
            <Input
              allowClear
              style={{ width: 240 }}
              placeholder="Search symbol/company"
              prefix={<SearchOutlined />}
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
          </Space>
        </Card>

        <Card size="small">
          <Typography.Text type="secondary">
            Active window: {query.fromDate} to {query.toDate} | Top N: {query.topN}
          </Typography.Text>
        </Card>

        {error && <Alert type="error" showIcon message={error} />}

        {loading ? (
          <div style={{ textAlign: "center", padding: 64 }}>
            <Spin size="large" />
          </div>
        ) : !data ? (
          <Card><Empty description="No leaders data available." /></Card>
        ) : (
          <Tabs
            defaultActiveKey="combined"
            items={[
              {
                key: "combined",
                label: "Combined",
                children: (
                  <SectionCard
                    section={data.combined}
                    mode={mode}
                    threshold={threshold}
                    search={search}
                    profileFilter={profileFilter}
                  />
                ),
              },
              ...data.profiles.map((section) => ({
                key: section.profileId,
                label: section.profileLabel,
                children: (
                  <SectionCard
                    section={section}
                    mode={mode}
                    threshold={threshold}
                    search={search}
                    profileFilter={profileFilter}
                  />
                ),
              })),
            ]}
          />
        )}
      </Space>
    </div>
  );
}
