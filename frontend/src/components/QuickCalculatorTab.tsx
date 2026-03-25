import { Input, InputNumber, Space, Table } from "antd";
import type { ColumnType } from "antd/es/table";
import { useState } from "react";

interface TargetRow {
  percent: number;
  price: string;
  perUnit: string;
  totalGain: string;
  yield: string;
}

export function QuickCalculatorTab() {
  const [avgPrice, setAvgPrice] = useState("");
  const [qty, setQty] = useState<number>(1);

  const calculateTargets = (): TargetRow[] => {
    if (!avgPrice) return [];

    const base = parseFloat(avgPrice);
    if (isNaN(base) || base <= 0) return [];

    const percents = [2, 3, 5, 7, 8, 9, 10];
    return percents.map((p) => {
      const price = base * (1 + p / 100);
      const perUnit = price - base;
      const totalGain = perUnit * qty;
      return {
        percent: p,
        price: price.toFixed(2),
        perUnit: perUnit.toFixed(2),
        totalGain: totalGain.toFixed(2),
        yield: ((perUnit / base) * 100).toFixed(2),
      };
    });
  };

  const rows = calculateTargets();
  const spread =
    rows.length > 0
      ? ((parseFloat(rows[rows.length - 1].price) - parseFloat(rows[0].price)) * qty).toFixed(2)
      : "0";

  const columns: ColumnType<TargetRow>[] = [
    {
      title: "Target %",
      dataIndex: "percent",
      key: "percent",
      render: (p) => `+${p}%`,
    },
    {
      title: "Price (₹)",
      dataIndex: "price",
      key: "price",
      render: (p) => `₹${p}`,
    },
    {
      title: "Gain / Unit (₹)",
      dataIndex: "perUnit",
      key: "perUnit",
      render: (g) => `₹${g}`,
    },
    {
      title: `Total Gain (qty ${qty})`,
      dataIndex: "totalGain",
      key: "totalGain",
      render: (g) => `₹${g}`,
    },
    {
      title: "Yield %",
      dataIndex: "yield",
      key: "yield",
      render: (y) => `${y}%`,
    },
  ];

  return (
    <div style={{ padding: "16px", background: "white", borderRadius: "4px" }}>
      <h3>Quick Calculator</h3>

      <Space direction="vertical" style={{ width: "100%" }} size="large">
        <div style={{ display: "grid", gridTemplateColumns: "2fr 1fr", gap: "12px" }}>
          <div>
            <label>Avg Price (₹)</label>
            <Input
              type="number"
              step="0.01"
              placeholder="Enter avg buy price"
              value={avgPrice}
              onChange={(e) => setAvgPrice(e.target.value)}
              autoFocus
              style={{ marginTop: "4px" }}
            />
          </div>
          <div>
            <label>Quantity</label>
            <InputNumber
              min={1}
              value={qty}
              onChange={(val) => setQty(val || 1)}
              style={{ width: "100%", marginTop: "4px" }}
            />
          </div>
        </div>

        {rows.length > 0 && (
          <>
            <Table<TargetRow>
              columns={columns}
              dataSource={rows}
              rowKey={(r) => r.percent}
              size="small"
              pagination={false}
            />

            <div
              style={{
                padding: "12px",
                background: "#f5f5f5",
                borderRadius: "4px",
                textAlign: "center",
                fontSize: "14px",
                fontWeight: "500",
              }}
            >
              Total Spread (₹{((parseFloat(rows[rows.length - 1].price) - parseFloat(rows[0].price)).toFixed(2))} × {qty}): ₹{spread}
            </div>
          </>
        )}
      </Space>
    </div>
  );
}