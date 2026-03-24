import { Alert, Drawer, Spin, Table, Tag, Typography } from "antd";
import type { DayDetail } from "../types";
import { useStockDetail } from "../hooks/useStockDetail";

const { Text } = Typography;

// ── Sub-components ────────────────────────────────────────────────────────────

/** Horizontal bar showing each day's low-to-high range relative to the week's full range. */
function RangeBar({ days, day }: { days: DayDetail[]; day: DayDetail }) {
  const weekMin = Math.min(...days.map((d) => d.low));
  const weekMax = Math.max(...days.map((d) => d.high));
  const range = weekMax - weekMin || 1;

  const leftPct = ((day.low - weekMin) / range) * 100;
  const widthPct = ((day.high - day.low) / range) * 100;
  const isUp = day.close >= day.open;

  return (
    <div style={{ position: "relative", height: 10, background: "#f0f0f0", borderRadius: 2, width: 90 }}>
      <div
        style={{
          position: "absolute",
          left: `${leftPct}%`,
          width: `${Math.max(widthPct, 2)}%`,
          height: "100%",
          background: isUp ? "#52c41a" : "#ff4d4f",
          borderRadius: 2,
        }}
      />
    </div>
  );
}

/** 7-point SVG sparkline for RSI with 70/30 reference lines. */
function RsiSparkline({ days }: { days: DayDetail[] }) {
  const rsiValues = days.map((d) => d.rsi14).filter((v): v is number => v !== null);
  if (rsiValues.length < 2) return <Text type="secondary">Not enough data</Text>;

  const W = 260;
  const H = 60;
  const P = 6; // padding

  // Always anchor y-axis to 0–100 so the 70/30 lines are meaningful
  const toX = (i: number) => P + (i / (rsiValues.length - 1)) * (W - P * 2);
  const toY = (v: number) => H - P - (v / 100) * (H - P * 2);

  const pathD = rsiValues.map((v, i) => `${i === 0 ? "M" : "L"} ${toX(i)} ${toY(v)}`).join(" ");
  const y70 = toY(70);
  const y30 = toY(30);

  return (
    <svg width={W} height={H} style={{ display: "block" }}>
      <line x1={P} x2={W - P} y1={y70} y2={y70} stroke="#ff4d4f" strokeWidth={1} strokeDasharray="3,2" opacity={0.6} />
      <line x1={P} x2={W - P} y1={y30} y2={y30} stroke="#52c41a" strokeWidth={1} strokeDasharray="3,2" opacity={0.6} />
      <path d={pathD} stroke="#4fa3ff" strokeWidth={2} fill="none" strokeLinejoin="round" />
      {rsiValues.map((v, i) => (
        <circle key={i} cx={toX(i)} cy={toY(v)} r={3} fill="#4fa3ff" />
      ))}
      <text x={W - P} y={y70 - 3} fontSize={8} fill="#ff4d4f" textAnchor="end">70</text>
      <text x={W - P} y={y30 + 9} fontSize={8} fill="#52c41a" textAnchor="end">30</text>
    </svg>
  );
}

// ── Main component ────────────────────────────────────────────────────────────

interface Props {
  symbol: string | null;
  onClose: () => void;
}

function fmtVol(vol: number): string {
  if (vol >= 1_000_000) return `${(vol / 1_000_000).toFixed(1)}M`;
  if (vol >= 1_000) return `${(vol / 1_000).toFixed(0)}K`;
  return String(vol);
}

export function StockDetailDrawer({ symbol, onClose }: Props) {
  const { data, loading, error } = useStockDetail(symbol);

  const columns = [
    {
      title: "Date",
      dataIndex: "date",
      key: "date",
      width: 100,
      render: (v: string) => <Text style={{ fontSize: 12 }}>{v}</Text>,
    },
    {
      title: "Close",
      dataIndex: "close",
      key: "close",
      width: 95,
      render: (v: number) => (
        <Text strong style={{ fontSize: 12 }}>
          ₹{v.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
        </Text>
      ),
    },
    {
      title: "Day %",
      dataIndex: "daily_change_pct",
      key: "daily_change_pct",
      width: 80,
      render: (v: number | null) => {
        if (v === null) return <Text type="secondary">-</Text>;
        const color = v > 0 ? "#52c41a" : v < 0 ? "#ff4d4f" : "#bfbfbf";
        return (
          <Text style={{ color, fontWeight: 600, fontSize: 12 }}>
            {v > 0 ? "+" : ""}{v.toFixed(2)}%
          </Text>
        );
      },
    },
    {
      title: "Range",
      key: "range",
      width: 110,
      render: (_: unknown, record: DayDetail) =>
        data ? <RangeBar days={data.days} day={record} /> : null,
    },
    {
      title: "RSI 14",
      dataIndex: "rsi14",
      key: "rsi14",
      width: 75,
      render: (v: number | null) => {
        if (v === null) return <Text type="secondary">-</Text>;
        const color = v > 70 ? "#ff4d4f" : v < 30 ? "#52c41a" : "#bfbfbf";
        return <Text style={{ color, fontWeight: 500, fontSize: 12 }}>{v.toFixed(1)}</Text>;
      },
    },
    {
      title: "Volume",
      key: "volume",
      width: 120,
      render: (_: unknown, record: DayDetail) => {
        const { volume, vol_ratio } = record;
        const ratioColor =
          vol_ratio !== null && vol_ratio > 2
            ? "#faad14"
            : vol_ratio !== null && vol_ratio > 1.25
            ? "#52c41a"
            : "#8c8c8c";
        return (
          <div style={{ display: "flex", flexDirection: "column", gap: 1 }}>
            <Text style={{ fontSize: 12 }}>{fmtVol(volume)}</Text>
            {vol_ratio !== null && (
              <Text style={{ fontSize: 10, color: ratioColor, fontWeight: 600 }}>
                {vol_ratio.toFixed(1)}x avg
              </Text>
            )}
          </div>
        );
      },
    },
  ];

  return (
    <Drawer
      title={
        <div>
          <Text strong style={{ fontSize: 16 }}>{symbol}</Text>
          <Text type="secondary" style={{ fontSize: 12, marginLeft: 8 }}>7-Day Detail</Text>
        </div>
      }
      open={!!symbol}
      onClose={onClose}
      width={760}
      styles={{ body: { padding: "16px 20px" } }}
    >
      {loading && <Spin style={{ display: "block", margin: "40px auto" }} />}
      {error && <Alert type="error" message={error} style={{ marginBottom: 16 }} />}

      {data && !loading && (
        <div style={{ display: "flex", flexDirection: "column", gap: 20 }}>

          {/* Trend strip */}
          <div>
            <Text type="secondary" style={{ fontSize: 11, display: "block", marginBottom: 6, letterSpacing: 0.5 }}>
              DAILY TREND
            </Text>
            <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
              {data.days.map((day) => {
                const pct = day.daily_change_pct;
                if (pct === null) return null;
                const isUp = pct >= 0;
                return (
                  <Tag
                    key={day.date}
                    color={isUp ? "success" : "error"}
                    style={{ fontWeight: 600, fontSize: 12, margin: 0 }}
                  >
                    {isUp ? "+" : ""}{pct.toFixed(2)}%
                  </Tag>
                );
              })}
            </div>
          </div>

          {/* OHLCV table */}
          <Table
            dataSource={data.days}
            columns={columns}
            rowKey="date"
            pagination={false}
            size="small"
          />

          {/* RSI sparkline */}
          <div>
            <Text type="secondary" style={{ fontSize: 11, display: "block", marginBottom: 6, letterSpacing: 0.5 }}>
              RSI (14) — LAST 7 DAYS
            </Text>
            <RsiSparkline days={data.days} />
          </div>

          {/* 20-day avg volume baseline */}
          {data.avg_volume_20d !== null && (
            <div>
              <Text type="secondary" style={{ fontSize: 11 }}>20-Day Avg Volume: </Text>
              <Text style={{ fontSize: 12, fontWeight: 600 }}>{fmtVol(data.avg_volume_20d)}</Text>
            </div>
          )}
        </div>
      )}
    </Drawer>
  );
}
