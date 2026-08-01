import { Alert, Button, Card, Space, Spin, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect } from "react";
import { useWeeklyLowLimitDailyValidation } from "../hooks/useWeeklyLowLimitDailyValidation";
import type {
  WeeklyLowLimitDailyValidationRequest,
  WeeklyLowLimitDailyValidationRow,
} from "../types";

const { Text, Title } = Typography;

function formatDateWithDay(value: string): string {
  const [year, month, day] = value.split("-").map(Number);
  const date = new Date(year, month - 1, day);
  return `${date.toLocaleDateString("en-IN", { weekday: "short" })}, ${value}`;
}

function formatPrice(value: number): string {
  return `₹${value.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function formatPercent(value: number | null): string {
  return value == null ? "-" : `${value > 0 ? "+" : ""}${value.toFixed(2)}%`;
}

interface WeeklyLowLimitDailyValidationPageProps extends WeeklyLowLimitDailyValidationRequest {}

export function WeeklyLowLimitDailyValidationPage({
  symbol,
  instrumentToken,
  previousWeekLowDate,
  entryWeekStartDate,
  entryDate,
}: WeeklyLowLimitDailyValidationPageProps) {
  const { data, loading, error, load } = useWeeklyLowLimitDailyValidation();

  useEffect(() => {
    void load({ symbol, instrumentToken, previousWeekLowDate, entryWeekStartDate, entryDate });
  }, [entryDate, entryWeekStartDate, instrumentToken, load, previousWeekLowDate, symbol]);

  const columns: ColumnsType<WeeklyLowLimitDailyValidationRow> = [
    { title: "Date", dataIndex: "date", key: "date", render: formatDateWithDay },
    { title: "Low", dataIndex: "low", key: "low", render: formatPrice },
    { title: "Open", dataIndex: "open", key: "open", render: formatPrice },
    { title: "Close", dataIndex: "close", key: "close", render: formatPrice },
    { title: "High", dataIndex: "high", key: "high", render: formatPrice },
    { title: "Daily change %", dataIndex: "dailyChangePct", key: "dailyChangePct", render: formatPercent },
    {
      title: "Marker",
      key: "marker",
      render: (_, row) => row.date === previousWeekLowDate ? <Tag color="gold">Previous low</Tag> : row.date === entryDate ? <Tag color="blue">Entry</Tag> : null,
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <Space orientation="vertical" size={16} style={{ width: "100%" }}>
        <Button onClick={() => window.history.back()}>Back to backtest</Button>
        <Card>
          <Space orientation="vertical" size={8}>
            <Title level={3} style={{ margin: 0 }}>Daily validation · {symbol}</Title>
            <Text type="secondary">From the previous-week low through the entry week and the next five trading sessions.</Text>
            <Text>Previous low: {formatDateWithDay(previousWeekLowDate)} · Entry week: {formatDateWithDay(entryWeekStartDate)}{entryDate ? ` · Entry: ${formatDateWithDay(entryDate)}` : " · No fill"}</Text>
          </Space>
        </Card>
        {loading && <Spin />}
        {error && <Alert type="error" message={error} showIcon />}
        {data && <Card title={`${data.rows.length} daily candles`}>
          <Table rowKey="date" columns={columns} dataSource={data.rows} pagination={false} size="small" />
        </Card>}
      </Space>
    </div>
  );
}
