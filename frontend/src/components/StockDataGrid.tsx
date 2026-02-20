import { Table, Spin, Typography } from "antd";
import type { TableColumnsType } from "antd";
import type { StockRow } from "../hooks/useWatchlistStocks";

const C = {
  up: "#26a69a",
  down: "#ef5350",
  text: "#ccc",
  label: "#888",
  panel: "#141414",
  warn: "#f59e0b",
};

const mono: React.CSSProperties = {
  fontFamily: "'Roboto Mono', 'JetBrains Mono', monospace",
  fontSize: 11,
};

function numCell(value: number, decimals = 2): React.ReactNode {
  return <span style={mono}>{value.toFixed(decimals)}</span>;
}

function rsiCell(rsi: number, min15d: number, min100d: number, min200d: number): React.ReactNode {
  const nearMin = rsi <= min15d * 1.05 || rsi <= min100d * 1.05 || rsi <= min200d * 1.05;
  return (
    <span
      style={{
        ...mono,
        color: nearMin ? C.up : C.text,
        background: nearMin ? "rgba(38,166,154,0.12)" : "transparent",
        borderRadius: 3,
        padding: "0 3px",
      }}
    >
      {rsi.toFixed(1)}
    </span>
  );
}

function drawdownCell(value: number): React.ReactNode {
  const severity = Math.abs(value);
  let bg = "transparent";
  if (severity > 20) bg = "rgba(239,83,80,0.22)";
  else if (severity > 10) bg = "rgba(239,83,80,0.12)";
  else if (severity > 5) bg = "rgba(239,83,80,0.06)";

  return (
    <span style={{ ...mono, color: C.down, background: bg, borderRadius: 3, padding: "0 3px" }}>
      {value.toFixed(1)}%
    </span>
  );
}

function buildColumns(): TableColumnsType<StockRow> {
  return [
    {
      title: "Symbol",
      dataIndex: "symbol",
      key: "symbol",
      fixed: "left",
      width: 90,
      sorter: (a, b) => a.symbol.localeCompare(b.symbol),
      render: (v: string) => (
        <span style={{ fontSize: 11, fontWeight: 600, color: "#fff", letterSpacing: 0.5 }}>{v}</span>
      ),
    },
    {
      title: "Price",
      dataIndex: "price",
      key: "price",
      width: 80,
      sorter: (a, b) => a.price - b.price,
      render: (v: number) => numCell(v),
      align: "right",
    },
    {
      title: "Prev Close",
      dataIndex: "prevClose",
      key: "prevClose",
      width: 90,
      sorter: (a, b) => a.prevClose - b.prevClose,
      render: (v: number) => numCell(v),
      align: "right",
    },
    {
      title: "RSI",
      dataIndex: "rsi",
      key: "rsi",
      width: 70,
      sorter: (a, b) => a.rsi - b.rsi,
      render: (_: number, row: StockRow) =>
        rsiCell(row.rsi, row.rsi15dMin, row.rsi100dMin, row.rsi200dMin),
      align: "right",
    },
    {
      title: "15D Min",
      dataIndex: "rsi15dMin",
      key: "rsi15dMin",
      width: 72,
      sorter: (a, b) => a.rsi15dMin - b.rsi15dMin,
      render: (v: number) => numCell(v, 1),
      align: "right",
    },
    {
      title: "100D Min",
      dataIndex: "rsi100dMin",
      key: "rsi100dMin",
      width: 78,
      sorter: (a, b) => a.rsi100dMin - b.rsi100dMin,
      render: (v: number) => numCell(v, 1),
      align: "right",
    },
    {
      title: "200D Min",
      dataIndex: "rsi200dMin",
      key: "rsi200dMin",
      width: 78,
      sorter: (a, b) => a.rsi200dMin - b.rsi200dMin,
      render: (v: number) => numCell(v, 1),
      align: "right",
    },
    {
      title: "R1",
      dataIndex: "r1",
      key: "r1",
      width: 80,
      sorter: (a, b) => a.r1 - b.r1,
      render: (v: number) => numCell(v),
      align: "right",
    },
    {
      title: "R2",
      dataIndex: "r2",
      key: "r2",
      width: 80,
      sorter: (a, b) => a.r2 - b.r2,
      render: (v: number) => numCell(v),
      align: "right",
    },
    {
      title: "R3",
      dataIndex: "r3",
      key: "r3",
      width: 80,
      sorter: (a, b) => a.r3 - b.r3,
      render: (v: number) => numCell(v),
      align: "right",
    },
    {
      title: "Mean Rev",
      dataIndex: "meanRevBaseline",
      key: "meanRevBaseline",
      width: 82,
      sorter: (a, b) => a.meanRevBaseline - b.meanRevBaseline,
      render: (v: number) => numCell(v),
      align: "right",
    },
    {
      title: "Drawdown",
      dataIndex: "drawdown",
      key: "drawdown",
      width: 88,
      sorter: (a, b) => a.drawdown - b.drawdown,
      render: (v: number) => drawdownCell(v),
      align: "right",
    },
    {
      title: "Max DD",
      dataIndex: "maxDrawdown",
      key: "maxDrawdown",
      width: 80,
      sorter: (a, b) => a.maxDrawdown - b.maxDrawdown,
      render: (v: number) => drawdownCell(v),
      align: "right",
    },
  ];
}

interface Props {
  rows: StockRow[];
  loading: boolean;
  error: string | null;
  watchlistName?: string;
}

export function StockDataGrid({ rows, loading, error, watchlistName }: Props) {
  if (error) {
    return (
      <div style={{ padding: 24, color: "#ef5350", fontSize: 12 }}>
        Error: {error}
      </div>
    );
  }

  return (
    <div style={{ display: "flex", flexDirection: "column", height: "100%", overflow: "hidden" }}>
      {/* Grid header */}
      <div
        style={{
          padding: "5px 12px",
          borderBottom: "1px solid #1f1f1f",
          display: "flex",
          alignItems: "center",
          gap: 8,
        }}
      >
        <span style={{ fontSize: 11, color: "#888", letterSpacing: 1, fontWeight: 600 }}>
          {watchlistName ? watchlistName.toUpperCase() : "SELECT A WATCHLIST"}
        </span>
        {rows.length > 0 && (
          <span style={{ fontSize: 10, color: "#555" }}>
            {rows.length} stocks
          </span>
        )}
        {loading && <Spin size="small" style={{ marginLeft: 4 }} />}
      </div>

      {/* Empty state */}
      {!loading && rows.length === 0 && (
        <div style={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center" }}>
          <Typography.Text style={{ fontSize: 12, color: "#555" }}>
            {watchlistName ? "No stocks in this watchlist" : "← Select a watchlist"}
          </Typography.Text>
        </div>
      )}

      {/* Table */}
      {rows.length > 0 && (
        <div style={{ flex: 1, overflow: "auto" }}>
          <Table<StockRow>
            size="small"
            dataSource={rows}
            columns={buildColumns()}
            pagination={false}
            scroll={{ x: "max-content" }}
            style={{ fontSize: 11 }}
            sticky
          />
        </div>
      )}
    </div>
  );
}
