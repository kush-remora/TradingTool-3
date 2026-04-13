import { Alert, Button, Card, Col, DatePicker, Row, Select, Space, Spin, Statistic, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import dayjs from "dayjs";
import { useEffect, useState } from "react";
import { useRsiMomentumLifecycle } from "../../hooks/useRsiMomentumLifecycle";
import type { LifecycleEpisode, RankBucketTransition, RankTimelinePoint, RsiMomentumSnapshot } from "../../types";
import { formatInr, formatNumber } from "./RsiBoard";

const { RangePicker } = DatePicker;

// ─── Rank timeline SVG ────────────────────────────────────────────────────────

function RankTimelineChart({ timeline }: { timeline: RankTimelinePoint[] }) {
  if (timeline.length === 0) return null;

  const W = 800;
  const H = 200;
  const PAD = { top: 20, right: 20, bottom: 30, left: 50 };

  const validRanks = timeline.map((p) => p.rank).filter((r): r is number => r != null);
  if (validRanks.length === 0) {
    return <Typography.Text type="secondary">No ranking data found in this window.</Typography.Text>;
  }

  const minRank = 1;
  const maxRank = Math.max(10, ...validRanks);
  
  const toX = (i: number) => PAD.left + ((i / (timeline.length - 1)) * (W - PAD.left - PAD.right));
  const toY = (rank: number) =>
    PAD.top + (((rank - minRank) / (maxRank - minRank)) * (H - PAD.top - PAD.bottom));

  // Build segments for the rank line
  const segments: string[] = [];
  let currentPath = "";
  for (let i = 0; i < timeline.length; i++) {
    const p = timeline[i];
    if (p.rank != null) {
      const x = toX(i).toFixed(1);
      const y = toY(p.rank).toFixed(1);
      currentPath += currentPath === "" ? `M${x},${y}` : ` L${x},${y}`;
    } else {
      if (currentPath) {
        segments.push(currentPath);
        currentPath = "";
      }
    }
  }
  if (currentPath) segments.push(currentPath);

  const xLabels = [0, Math.floor(timeline.length / 2), timeline.length - 1].map((i) => ({
    x: toX(i),
    label: timeline[i].date,
  }));

  const yTics = [1, 5, 10, 20, 30, 40].filter(r => r <= maxRank);
  if (!yTics.includes(maxRank)) yTics.push(maxRank);

  return (
    <div style={{ overflowX: "auto" }}>
      <svg viewBox={`0 0 ${W} ${H}`} style={{ width: "100%", minWidth: 600, height: H }}>
        {/* Y-axis lines */}
        {yTics.map((rank) => (
          <g key={rank}>
            <line 
              x1={PAD.left} 
              y1={toY(rank)} 
              x2={W - PAD.right} 
              y2={toY(rank)} 
              stroke={rank === 10 ? "#ffccc7" : "#f0f0f0"} 
              strokeWidth={rank === 10 ? 2 : 1}
              strokeDasharray={rank === 10 ? "4" : "0"}
            />
            <text x={PAD.left - 8} y={toY(rank) + 4} textAnchor="end" fontSize={11} fill="#999">
              #{rank}
            </text>
          </g>
        ))}
        
        {/* X-axis labels */}
        {xLabels.map(({ x, label }) => (
          <text key={label} x={x} y={H - 5} textAnchor="middle" fontSize={11} fill="#999">
            {label.slice(5)}
          </text>
        ))}

        {/* Path segments */}
        {segments.map((d, i) => (
          <path key={i} d={d} fill="none" stroke="#1677ff" strokeWidth={2.5} strokeLinejoin="round" />
        ))}

        {/* Data points */}
        {timeline
          .map((p, i) => {
            if (p.rank == null) return null;
            return (
              <Tooltip 
                key={i}
                title={
                  <div style={{ fontSize: 12 }}>
                    <div>Date: {p.date}</div>
                    <div>Rank: #{p.rank}</div>
                    <div>Price: {formatInr(p.price)}</div>
                    <div>RSI: {formatNumber(p.avgRsi)}</div>
                  </div>
                }
              >
                <circle
                  cx={toX(i).toFixed(1)}
                  cy={toY(p.rank).toFixed(1)}
                  r={4}
                  fill={p.inTop10 ? "#1677ff" : "#d9d9d9"}
                  stroke="#fff"
                  strokeWidth={1}
                  style={{ cursor: "pointer" }}
                />
              </Tooltip>
            );
          })}
      </svg>
    </div>
  );
}

// ─── Table Columns ────────────────────────────────────────────────────────────

const episodeColumns: ColumnsType<LifecycleEpisode> = [
  { title: "Entry", dataIndex: "entryDate", key: "entryDate", width: 110 },
  {
    title: "Exit",
    dataIndex: "exitDate",
    key: "exitDate",
    width: 110,
    render: (v: string | null) => v ?? "—",
  },
  { title: "Days in Top 10", dataIndex: "daysInTop10", key: "daysInTop10", width: 120 },
  {
    title: "Best Rank",
    dataIndex: "bestRank",
    key: "bestRank",
    width: 100,
    render: (rank: number) => <Typography.Text strong>#{rank}</Typography.Text>,
  },
  { title: "Best Rank Date", dataIndex: "bestRankDate", key: "bestRankDate", width: 120 },
  {
    title: "Exit Reason",
    dataIndex: "exitReason",
    key: "exitReason",
    width: 140,
    render: (reason: string | null) =>
      reason ? (
        <Tag color={reason === "DROPPED_OUT" ? "red" : "blue"}>{reason}</Tag>
      ) : (
        "—"
      ),
  },
];

const dailyTimelineColumns: ColumnsType<RankTimelinePoint> = [
  { title: "Date", dataIndex: "date", key: "date", width: 110 },
  { 
    title: "Rank", 
    dataIndex: "rank", 
    key: "rank", 
    width: 90,
    render: (r: number | null, p) => (
      <Space>
        {r != null ? `#${r}` : "—"}
        {p.inTop10 && <Tag color="green" style={{ fontSize: 10, margin: 0 }}>TOP 10</Tag>}
      </Space>
    )
  },
  { 
    title: "Price", 
    dataIndex: "price", 
    key: "price", 
    width: 110,
    render: (v) => formatInr(v)
  },
  { 
    title: "Avg RSI", 
    dataIndex: "avgRsi", 
    key: "avgRsi", 
    width: 100,
    render: (v) => formatNumber(v)
  },
];

const transitionColumns: ColumnsType<RankBucketTransition> = [
  { title: "From", dataIndex: "from", key: "from", width: 80 },
  { title: "To", dataIndex: "to", key: "to", width: 80 },
  { title: "Count", dataIndex: "count", key: "count", width: 80 },
];

// ─── Main component ───────────────────────────────────────────────────────────

export function LifecycleTab({
  profileId,
  snapshot,
}: {
  profileId: string;
  snapshot: RsiMomentumSnapshot;
}) {
  const { summary, symbolDetail, loadingSummary, loadingSymbol, error, loadSummary, loadSymbol } =
    useRsiMomentumLifecycle(profileId);

  const [dateRange, setDateRange] = useState<[dayjs.Dayjs, dayjs.Dayjs] | null>(null);
  const [selectedSymbol, setSelectedSymbol] = useState<string | null>(null);

  const holdingSymbols = snapshot.holdings.map((h) => h.symbol);

  const handleLoadSummary = () => {
    void loadSummary(
      dateRange?.[0].format("YYYY-MM-DD"),
      dateRange?.[1].format("YYYY-MM-DD"),
    );
  };

  const handleLoadSymbol = () => {
    if (!selectedSymbol) return;
    void loadSymbol(
      selectedSymbol,
      dateRange?.[0].format("YYYY-MM-DD"),
      dateRange?.[1].format("YYYY-MM-DD"),
    );
  };

  useEffect(() => {
    void loadSummary();
  }, [profileId, loadSummary]);

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <Card size="small">
        <Space wrap>
          <RangePicker
            value={dateRange}
            onChange={(v) => setDateRange(v as [dayjs.Dayjs, dayjs.Dayjs] | null)}
            placeholder={["From (default: -3m)", "To (default: today)"]}
          />
          <Button onClick={handleLoadSummary} loading={loadingSummary}>
            Load Summary
          </Button>
        </Space>
      </Card>

      {error && <Alert type="error" showIcon message={error} />}

      {loadingSummary ? (
        <Spin />
      ) : summary ? (
        <Card title="Cohort Summary" size="small">
          <Row gutter={[12, 12]}>
            <Col xs={12} md={6}>
              <Statistic title="Total Episodes" value={summary.totalEpisodes} />
            </Col>
            <Col xs={12} md={6}>
              <Statistic title="Avg Days in Top 10" value={summary.avgDaysInTop10} precision={1} />
            </Col>
            <Col xs={12} md={6}>
              <Statistic title="Median Days" value={summary.medianDaysInTop10} precision={1} />
            </Col>
            <Col xs={12} md={6}>
              <Statistic
                title="Short-Stay Churn (≤5d)"
                value={summary.shortStayChurnRate * 100}
                precision={1}
                suffix="%"
              />
            </Col>
          </Row>

          {summary.rankBucketTransitions.length > 0 && (
            <div style={{ marginTop: 16 }}>
              <Typography.Text strong style={{ display: "block", marginBottom: 8 }}>
                Rank Bucket Transitions
              </Typography.Text>
              <Table
                columns={transitionColumns}
                dataSource={summary.rankBucketTransitions}
                rowKey={(row) => `${row.from}-${row.to}`}
                size="small"
                pagination={false}
                scroll={{ x: 300 }}
              />
            </div>
          )}
        </Card>
      ) : null}

      <Card title="Symbol Detailed Ranking History" size="small">
        <Space wrap style={{ marginBottom: 12 }}>
          <Select
            showSearch
            placeholder="Pick a symbol"
            style={{ width: 220 }}
            value={selectedSymbol}
            onChange={setSelectedSymbol}
            options={holdingSymbols.map((s) => ({ value: s, label: s }))}
            allowClear
          />
          <Button onClick={handleLoadSymbol} loading={loadingSymbol} disabled={!selectedSymbol}>
            Load Historical Movement
          </Button>
        </Space>

        {loadingSymbol ? (
          <Spin />
        ) : symbolDetail ? (
          <Space direction="vertical" size={24} style={{ width: "100%" }}>
            {symbolDetail.episodes.map((ep, i) => (
              <Card
                key={i}
                size="small"
                styles={{ header: { background: "#fafafa" } }}
                title={
                  <Space>
                    <Typography.Text strong>{symbolDetail.symbol}</Typography.Text>
                    <Typography.Text type="secondary">|</Typography.Text>
                    <Tag color="blue">{ep.entryDate}</Tag>
                    <Typography.Text type="secondary">→</Typography.Text>
                    <Tag color={ep.exitReason === "DROPPED_OUT" ? "red" : "green"}>
                      {ep.exitDate ?? "Active Position"}
                    </Tag>
                    <Tag color="purple">Stay: {ep.daysInTop10}d</Tag>
                    <Tag color="cyan">Best: #{ep.bestRank}</Tag>
                  </Space>
                }
              >
                <div style={{ marginBottom: 24 }}>
                   <Typography.Text type="secondary" style={{ display: "block", marginBottom: 8 }}>Ranking Trajectory</Typography.Text>
                   <RankTimelineChart timeline={ep.rankTimeline} />
                </div>

                <div>
                   <Typography.Text type="secondary" style={{ display: "block", marginBottom: 8 }}>Daily Metrics Breakdown</Typography.Text>
                   <Table
                      columns={dailyTimelineColumns}
                      dataSource={ep.rankTimeline}
                      rowKey="date"
                      size="small"
                      pagination={false}
                      scroll={{ x: 400 }}
                      rowClassName={(record) => record.inTop10 ? "ant-table-row-selected" : ""}
                   />
                </div>
              </Card>
            ))}

            {symbolDetail.episodes.length === 0 && (
              <Typography.Text type="secondary">
                No ranking movement found for {symbolDetail.symbol} in this period.
              </Typography.Text>
            )}

            <div style={{ marginTop: 20 }}>
               <Typography.Title level={5}>Episode Summary</Typography.Title>
               <Table
                columns={episodeColumns}
                dataSource={symbolDetail.episodes}
                rowKey={(ep) => ep.entryDate}
                size="small"
                pagination={false}
                scroll={{ x: 620 }}
               />
            </div>
          </Space>
        ) : (
          <Typography.Text type="secondary">
            Select a symbol and click Load Historical Movement.
          </Typography.Text>
        )}
      </Card>
    </Space>
  );
}

// Internal SVG Tooltip wrapper to avoid extra dependency if possible
function Tooltip({ children, title }: { children: React.ReactNode, title: React.ReactNode }) {
   // Simplified version using title attribute for browser default if antd Tooltip has issues inside SVG
   // but since antd Tooltip works on children, we just use it.
   const { Tooltip: AntdTooltip } = require('antd');
   return <AntdTooltip title={title}>{children}</AntdTooltip>;
}
