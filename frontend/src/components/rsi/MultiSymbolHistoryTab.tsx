import { Alert, Button, Card, DatePicker, Select, Space, Spin, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import dayjs from "dayjs";
import { useState, useMemo } from "react";
import { useRsiMomentumMultiSymbolHistory } from "../../hooks/useRsiMomentumMultiSymbolHistory";
import type { RankTimelinePoint, RsiMomentumSnapshot } from "../../types";
import { formatInr, formatNumber } from "./RsiBoard";

const { RangePicker } = DatePicker;

const COLORS = ["#1677ff", "#52c41a", "#722ed1", "#fa8c16", "#eb2f96", "#13c2c2", "#faad14", "#fadb14"];

// ─── Multi-line Rank Chart ───────────────────────────────────────────────────

function MultiRankTimelineChart({ timelines, symbols }: { timelines: Record<string, RankTimelinePoint[]>, symbols: string[] }) {
  const firstTimeline = Object.values(timelines)[0];
  if (!firstTimeline || firstTimeline.length === 0) return null;

  const W = 800;
  const H = 300;
  const PAD = { top: 20, right: 120, bottom: 30, left: 50 };

  const allPoints = Object.values(timelines).flat();
  const validRanks = allPoints.map((p) => p.rank).filter((r): r is number => r != null);
  if (validRanks.length === 0) return null;

  const minRank = 1;
  const maxRank = Math.max(40, ...validRanks);
  
  const toX = (i: number) => PAD.left + ((i / (firstTimeline.length - 1)) * (W - PAD.left - PAD.right));
  const toY = (rank: number) =>
    PAD.top + (((rank - minRank) / (maxRank - minRank)) * (H - PAD.top - PAD.bottom));

  const xLabels = [0, Math.floor(firstTimeline.length / 2), firstTimeline.length - 1].map((i) => ({
    x: toX(i),
    label: firstTimeline[i].date,
  }));

  const yTics = [1, 5, 10, 20, 30, 40].filter(r => r <= maxRank);

  return (
    <div style={{ overflowX: "auto" }}>
      <svg viewBox={`0 0 ${W} ${H}`} style={{ width: "100%", minWidth: 600, height: H }}>
        {/* Y-axis lines */}
        {yTics.map((rank) => (
          <g key={rank}>
            <line x1={PAD.left} y1={toY(rank)} x2={W - PAD.right} y2={toY(rank)} stroke="#f0f0f0" strokeWidth={1} />
            <text x={PAD.left - 8} y={toY(rank) + 4} textAnchor="end" fontSize={11} fill="#999">#{rank}</text>
          </g>
        ))}
        
        {/* X-axis labels */}
        {xLabels.map(({ x, label }) => (
          <text key={label} x={x} y={H - 5} textAnchor="middle" fontSize={11} fill="#999">{label.slice(5)}</text>
        ))}

        {/* Lines for each symbol */}
        {symbols.map((sym, idx) => {
          const timeline = timelines[sym];
          if (!timeline) return null;
          
          let currentPath = "";
          for (let i = 0; i < timeline.length; i++) {
            const p = timeline[i];
            if (p.rank != null) {
              const x = toX(i).toFixed(1);
              const y = toY(p.rank).toFixed(1);
              currentPath += currentPath === "" ? `M${x},${y}` : ` L${x},${y}`;
            }
          }

          const color = COLORS[idx % COLORS.length];
          return (
            <g key={sym}>
              <path d={currentPath} fill="none" stroke={color} strokeWidth={2} strokeLinejoin="round" />
              {/* Legend label at end of line */}
              <text x={W - PAD.right + 10} y={toY(timeline[timeline.length - 1]?.rank || maxRank)} fontSize={12} fill={color} fontWeight="bold">
                {sym}
              </text>
            </g>
          );
        })}
      </svg>
    </div>
  );
}

// ─── Table ───────────────────────────────────────────────────────────────────

interface FlatPoint extends RankTimelinePoint {
  symbol: string;
}

const tableColumns: ColumnsType<FlatPoint> = [
  { title: "Date", dataIndex: "date", key: "date", sorter: (a, b) => a.date.localeCompare(b.date) },
  { title: "Symbol", dataIndex: "symbol", key: "symbol", filters: [], onFilter: (v, r) => r.symbol === v },
  { 
    title: "Rank", 
    dataIndex: "rank", 
    key: "rank",
    render: (r, p) => (
      <Space>
        {r != null ? `#${r}` : "—"}
        {p.inTop10 && <Tag color="green" style={{ fontSize: 10 }}>TOP 10</Tag>}
      </Space>
    )
  },
  { title: "Price", dataIndex: "price", key: "price", render: formatInr },
  { title: "Avg RSI", dataIndex: "avgRsi", key: "avgRsi", render: formatNumber },
];

export function MultiSymbolHistoryTab({ profileId, snapshot }: { profileId: string, snapshot: RsiMomentumSnapshot }) {
  const { data, loading, error, load } = useRsiMomentumMultiSymbolHistory();
  const [dateRange, setDateRange] = useState<[dayjs.Dayjs, dayjs.Dayjs] | null>(null);
  const [selectedSymbols, setSelectedSymbols] = useState<string[]>([]);

  // Collect symbols from snapshot candidates + holdings for the dropdown
  const allAvailableSymbols = useMemo(() => {
    const syms = new Set<string>();
    snapshot.holdings.forEach(h => syms.add(h.symbol));
    snapshot.topCandidates.forEach(c => syms.add(c.symbol));
    return Array.from(syms).sort();
  }, [snapshot]);

  const handleLoad = () => {
    if (selectedSymbols.length === 0) return;
    void load(
      profileId,
      selectedSymbols,
      dateRange?.[0].format("YYYY-MM-DD"),
      dateRange?.[1].format("YYYY-MM-DD")
    );
  };

  const flatData: FlatPoint[] = useMemo(() => {
    if (!data) return [];
    return Object.entries(data.timelines).flatMap(([symbol, timeline]) => 
      timeline.map(p => ({ ...p, symbol }))
    ).sort((a, b) => b.date.localeCompare(a.date)); // Newest first
  }, [data]);

  const dynamicColumns = useMemo(() => {
    const cols = [...tableColumns];
    cols[1].filters = selectedSymbols.map(s => ({ text: s, value: s }));
    return cols;
  }, [selectedSymbols]);

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <Card size="small" title="Compare Multiple Stocks">
        <Space wrap align="center">
          <RangePicker
            value={dateRange}
            onChange={(v) => setDateRange(v as [dayjs.Dayjs, dayjs.Dayjs] | null)}
            placeholder={["From (default: -3m)", "To (default: today)"]}
          />
          <Select
            mode="multiple"
            showSearch
            placeholder="Select symbols to compare"
            style={{ minWidth: 300, maxWidth: 500 }}
            value={selectedSymbols}
            onChange={setSelectedSymbols}
            options={allAvailableSymbols.map(s => ({ label: s, value: s }))}
            maxTagCount="responsive"
          />
          <Button type="primary" onClick={handleLoad} loading={loading} disabled={selectedSymbols.length === 0}>
            Compare
          </Button>
        </Space>
      </Card>

      {error && <Alert type="error" showIcon message={error} />}

      {loading ? <div style={{ textAlign: "center", padding: 32 }}><Spin /></div> : data && (
        <Space direction="vertical" size={16} style={{ width: "100%" }}>
          <Card size="small" title="Rank Trajectories">
            <MultiRankTimelineChart timelines={data.timelines} symbols={selectedSymbols} />
          </Card>

          <Card size="small" title="Comparison Data">
            <Table
              columns={dynamicColumns}
              dataSource={flatData}
              rowKey={(r) => `${r.symbol}-${r.date}`}
              size="small"
              pagination={{ pageSize: 50 }}
              scroll={{ x: 600 }}
            />
          </Card>
        </Space>
      )}
    </Space>
  );
}
