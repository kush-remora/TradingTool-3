import { DownloadOutlined, LineChartOutlined, ReloadOutlined, TableOutlined } from "@ant-design/icons";
import { Alert, Button, Card, DatePicker, Empty, Popconfirm, Select, Space, Spin, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import dayjs from "dayjs";
import { useEffect, useMemo, useState } from "react";
import { useRsiMomentum } from "../hooks/useRsiMomentum";
import { useRsiMomentumBaseHistory } from "../hooks/useRsiMomentumBaseHistory";
import { postJson } from "../utils/api";
import type { BackfillFreshResponse, RsiMomentumHistoryEntry, RsiMomentumRankedStock } from "../types";

type DateSnapshotRow = {
  asOfDate: string;
  rowCount: number;
  bestSymbol: string;
  bestScore: number | null;
};

type StockTrailRow = {
  date: string;
  rank: number | null;
  rankChange: number | null;
};

const scoreFormatter = (value: number): string => value.toFixed(2);

const momentumColumns: ColumnsType<RsiMomentumRankedStock> = [
  {
    title: "Rank",
    dataIndex: "rank",
    key: "rank",
    width: 70,
    sorter: (a, b) => a.rank - b.rank,
    defaultSortOrder: "ascend",
  },
  {
    title: "Symbol",
    dataIndex: "symbol",
    key: "symbol",
    width: 110,
    fixed: "left",
  },
  {
    title: "Company",
    dataIndex: "companyName",
    key: "companyName",
    width: 200,
    ellipsis: true,
  },
  {
    title: "Momentum Score",
    dataIndex: "avgRsi",
    key: "avgRsi",
    width: 130,
    sorter: (a, b) => a.avgRsi - b.avgRsi,
    render: (value: number) => <Typography.Text strong>{scoreFormatter(value)}</Typography.Text>,
  },
  {
    title: "RSI 22",
    dataIndex: "rsi22",
    key: "rsi22",
    width: 90,
    render: scoreFormatter,
  },
  {
    title: "RSI 44",
    dataIndex: "rsi44",
    key: "rsi44",
    width: 90,
    render: scoreFormatter,
  },
  {
    title: "RSI 66",
    dataIndex: "rsi66",
    key: "rsi66",
    width: 90,
    render: scoreFormatter,
  },
  {
    title: "Rank 5d Ago",
    dataIndex: "rank5DaysAgo",
    key: "rank5DaysAgo",
    width: 110,
    render: (value: number | null) => value ?? "—",
  },
  {
    title: "Rank Jump",
    dataIndex: "rankImprovement",
    key: "rankImprovement",
    width: 100,
    sorter: (a, b) => (a.rankImprovement ?? -999) - (b.rankImprovement ?? -999),
    render: (value: number | null) => {
      if (value == null) return "—";
      if (value > 0) return <Tag color="green">+{value}</Tag>;
      if (value < 0) return <Tag color="red">{value}</Tag>;
      return <Tag>0</Tag>;
    },
  },
];

export function RsiMomentumBasePage() {
  const { data: latest, loading: profileLoading, error: profileError } = useRsiMomentum();
  const { data: entries, loading, error, load } = useRsiMomentumBaseHistory();

  const [profileId, setProfileId] = useState<string | undefined>(undefined);
  const [range, setRange] = useState<[dayjs.Dayjs, dayjs.Dayjs]>([
    dayjs().subtract(30, "day"),
    dayjs(),
  ]);
  const [selectedDate, setSelectedDate] = useState<string | null>(null);
  const [selectedSymbol, setSelectedSymbol] = useState<string | null>(null);
  const [backfillFreshLoading, setBackfillFreshLoading] = useState(false);
  const [backfillFreshMessage, setBackfillFreshMessage] = useState<string | null>(null);

  const profiles = latest?.profiles ?? [];

  useEffect(() => {
    if (!profileId && profiles.length > 0) {
      setProfileId(profiles[0].profileId);
    }
  }, [profileId, profiles]);

  const canLoad = Boolean(profileId && range[0] && range[1]);

  const snapshotRows: DateSnapshotRow[] = useMemo(() => {
    return entries.map((entry) => {
      const top = entry.snapshot.topCandidates[0];
      return {
        asOfDate: entry.asOfDate,
        rowCount: entry.snapshot.topCandidates.length,
        bestSymbol: top?.symbol ?? "—",
        bestScore: top?.avgRsi ?? null,
      };
    });
  }, [entries]);

  useEffect(() => {
    if (snapshotRows.length === 0) {
      setSelectedDate(null);
      return;
    }
    if (!selectedDate || !snapshotRows.some((row) => row.asOfDate === selectedDate)) {
      setSelectedDate(snapshotRows[0].asOfDate);
    }
  }, [selectedDate, snapshotRows]);

  const selectedEntry: RsiMomentumHistoryEntry | null = useMemo(() => {
    if (!selectedDate) return null;
    return entries.find((entry) => entry.asOfDate === selectedDate) ?? null;
  }, [entries, selectedDate]);

  const symbols: string[] = useMemo(() => {
    const all = entries.flatMap((entry) => entry.snapshot.topCandidates.map((stock) => stock.symbol));
    return Array.from(new Set(all)).sort((a, b) => a.localeCompare(b));
  }, [entries]);

  useEffect(() => {
    if (!selectedSymbol && symbols.length > 0) {
      setSelectedSymbol(symbols[0]);
    }
    if (selectedSymbol && !symbols.includes(selectedSymbol)) {
      setSelectedSymbol(symbols[0] ?? null);
    }
  }, [selectedSymbol, symbols]);

  const stockTrailRows: StockTrailRow[] = useMemo(() => {
    if (!selectedSymbol) return [];
    let previousRank: number | null = null;

    return entries.map((entry) => {
      const found = entry.snapshot.topCandidates.find((stock) => stock.symbol === selectedSymbol);
      const currentRank = found?.rank ?? null;
      const rankChange = previousRank != null && currentRank != null ? currentRank - previousRank : null;
      previousRank = currentRank;
      return {
        date: entry.asOfDate,
        rank: currentRank,
        rankChange,
      };
    });
  }, [entries, selectedSymbol]);

  const handleLoad = (): void => {
    if (!profileId) return;
    void load(profileId, range[0].format("YYYY-MM-DD"), range[1].format("YYYY-MM-DD"));
  };

  const handleBackfillFresh = async (): Promise<void> => {
    if (!profileId) return;
    setBackfillFreshLoading(true);
    setBackfillFreshMessage(null);
    try {
      const response = await postJson<BackfillFreshResponse>("/api/strategy/rsi-momentum/backfill/fresh", {
        fromDate: range[0].format("YYYY-MM-DD"),
        toDate: range[1].format("YYYY-MM-DD"),
      });
      setBackfillFreshMessage(
        `Backfill fresh complete. Cleared ${response.rebuild.clearedRows} snapshot rows and rebuilt ${response.rebuild.profileResults.length} profile(s).`,
      );
      await load(profileId, range[0].format("YYYY-MM-DD"), range[1].format("YYYY-MM-DD"));
    } catch (err) {
      setBackfillFreshMessage(err instanceof Error ? err.message : "Backfill fresh failed.");
    } finally {
      setBackfillFreshLoading(false);
    }
  };

  const exportCsv = (): void => {
    if (!profileId || entries.length === 0) return;
    const header = [
      "profileId",
      "date",
      "rank",
      "symbol",
      "companyName",
      "momentumScore",
      "rsi22",
      "rsi44",
      "rsi66",
      "rank5DaysAgo",
      "rankImprovement",
      "close",
      "entryAction",
    ];

    const rows = entries.flatMap((entry) =>
      entry.snapshot.topCandidates.map((stock) => [
        profileId,
        entry.asOfDate,
        stock.rank,
        stock.symbol,
        stock.companyName,
        stock.avgRsi,
        stock.rsi22,
        stock.rsi44,
        stock.rsi66,
        stock.rank5DaysAgo ?? "",
        stock.rankImprovement ?? "",
        stock.close,
        stock.entryAction ?? "",
      ]),
    );

    const escapeCell = (value: string | number): string => {
      const raw = String(value);
      if (raw.includes(",") || raw.includes("\"") || raw.includes("\n")) {
        return `"${raw.replace(/"/g, "\"\"")}"`;
      }
      return raw;
    };

    const csv = [header, ...rows].map((line) => line.map(escapeCell).join(",")).join("\n");
    const blob = new Blob([csv], { type: "text/csv;charset=utf-8;" });
    const fileName = `rsi-momentum-base-${profileId}-${range[0].format("YYYYMMDD")}-${range[1].format("YYYYMMDD")}.csv`;
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = fileName;
    anchor.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div style={{ padding: 24, background: "#f4f6f8", minHeight: "calc(100vh - 48px)" }}>
      <Space direction="vertical" size={16} style={{ width: "100%" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12, flexWrap: "wrap" }}>
          <div>
            <Typography.Title level={4} style={{ margin: 0 }}>
              RSI Momentum Base
            </Typography.Title>
            <Typography.Text type="secondary">
              Pure momentum score view for date ranges with CSV export and rank movement tracking.
            </Typography.Text>
          </div>
          <Tag color="blue" icon={<TableOutlined />}>
            Base Research View
          </Tag>
        </div>

        <Card size="small" title="Controls">
          <Space wrap>
            <Select
              value={profileId}
              onChange={setProfileId}
              loading={profileLoading}
              placeholder="Select profile"
              style={{ width: 220 }}
              options={profiles.map((profile) => ({
                value: profile.profileId,
                label: profile.profileLabel || profile.profileId,
              }))}
            />
            <DatePicker.RangePicker
              value={range}
              onChange={(next) => {
                if (next?.[0] && next?.[1]) {
                  setRange([next[0], next[1]]);
                }
              }}
              allowClear={false}
            />
            <Button
              type="primary"
              icon={<ReloadOutlined />}
              onClick={handleLoad}
              disabled={!canLoad}
              loading={loading}
            >
              Load Range
            </Button>
            <Button icon={<DownloadOutlined />} onClick={exportCsv} disabled={entries.length === 0}>
              Download CSV
            </Button>
            <Popconfirm
              title="Backfill fresh?"
              description="This will clear RSI snapshot data in Postgres and Redis, then rebuild fresh."
              okText="Yes, rebuild"
              cancelText="Cancel"
              onConfirm={() => void handleBackfillFresh()}
            >
              <Button danger loading={backfillFreshLoading}>
                Backfill Fresh
              </Button>
            </Popconfirm>
          </Space>
        </Card>

        {profileError && <Alert type="error" showIcon message={profileError} />}
        {error && <Alert type="error" showIcon message={error} />}
        {backfillFreshMessage && <Alert type="info" showIcon message={backfillFreshMessage} />}

        {loading ? (
          <div style={{ textAlign: "center", padding: 48 }}>
            <Spin tip="Loading momentum history..." />
          </div>
        ) : entries.length === 0 ? (
          <Card size="small">
            <Empty description="Pick profile + date range, then click Load Range." />
          </Card>
        ) : (
          <>
            <Card size="small" title={`Loaded Dates (${snapshotRows.length})`}>
              <Table<DateSnapshotRow>
                rowKey={(row) => row.asOfDate}
                size="small"
                pagination={{ pageSize: 10 }}
                dataSource={snapshotRows}
                rowSelection={{
                  type: "radio",
                  selectedRowKeys: selectedDate ? [selectedDate] : [],
                  onChange: (keys) => setSelectedDate((keys[0] as string) ?? null),
                }}
                columns={[
                  { title: "Date", dataIndex: "asOfDate", key: "asOfDate", sorter: (a, b) => a.asOfDate.localeCompare(b.asOfDate) },
                  { title: "Rows", dataIndex: "rowCount", key: "rowCount", width: 90 },
                  { title: "Top Symbol", dataIndex: "bestSymbol", key: "bestSymbol", width: 130 },
                  {
                    title: "Top Score",
                    dataIndex: "bestScore",
                    key: "bestScore",
                    width: 120,
                    render: (value: number | null) => (value == null ? "—" : scoreFormatter(value)),
                  },
                ]}
              />
            </Card>

            <Card
              size="small"
              title={`Top Momentum List (${selectedEntry?.asOfDate ?? "No date"})`}
              extra={<Tag>{selectedEntry?.snapshot.topCandidates.length ?? 0} rows</Tag>}
            >
              <Table<RsiMomentumRankedStock>
                rowKey={(row) => `${selectedEntry?.asOfDate ?? "na"}-${row.symbol}-${row.rank}`}
                size="small"
                pagination={{ pageSize: 20 }}
                scroll={{ x: 1200 }}
                dataSource={selectedEntry?.snapshot.topCandidates ?? []}
                columns={momentumColumns}
              />
            </Card>

            <Card size="small" title="Stock Rank Movement" extra={<LineChartOutlined />}>
              <Space direction="vertical" size={12} style={{ width: "100%" }}>
                <Select
                  value={selectedSymbol}
                  onChange={setSelectedSymbol}
                  style={{ width: 220 }}
                  showSearch
                  optionFilterProp="label"
                  options={symbols.map((symbol) => ({ value: symbol, label: symbol }))}
                />

                <Table<StockTrailRow>
                  rowKey={(row) => row.date}
                  size="small"
                  pagination={false}
                  dataSource={stockTrailRows}
                  columns={[
                    { title: "Date", dataIndex: "date", key: "date", width: 130 },
                    {
                      title: "Rank",
                      dataIndex: "rank",
                      key: "rank",
                      width: 100,
                      render: (value: number | null) => (value == null ? "— (not in top list)" : `#${value}`),
                    },
                    {
                      title: "Change vs Prev Day",
                      dataIndex: "rankChange",
                      key: "rankChange",
                      width: 160,
                      render: (value: number | null) => {
                        if (value == null) return "—";
                        if (value < 0) return <Tag color="green">Improved {Math.abs(value)}</Tag>;
                        if (value > 0) return <Tag color="red">Dropped {value}</Tag>;
                        return <Tag>No change</Tag>;
                      },
                    },
                  ]}
                />
              </Space>
            </Card>
          </>
        )}
      </Space>
    </div>
  );
}
