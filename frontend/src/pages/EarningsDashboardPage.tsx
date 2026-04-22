import { DownloadOutlined, ReloadOutlined } from "@ant-design/icons";
import { Alert, Button, Card, Input, InputNumber, Space, Switch, Table, Typography, message } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useMemo, useState } from "react";
import type { EarningsDashboardResponse, EarningsDashboardRow, EarningsDashboardExportDocument } from "../types";
import { getJson } from "../utils/api";

const { Text } = Typography;

function formatPct(value: number | null): string {
  if (value == null) return "-";
  return `${value.toFixed(2)}%`;
}

function formatPrice(value: number | null): string {
  if (value == null) return "-";
  return `₹${value.toFixed(2)}`;
}

function formatVolume(value: number | null): string {
  if (value == null) return "-";
  return value.toLocaleString("en-IN");
}

export function EarningsDashboardPage() {
  const [messageApi, contextHolder] = message.useMessage();
  const [daysAhead, setDaysAhead] = useState<number>(15);
  const [growwOnly, setGrowwOnly] = useState<boolean>(true);
  const [search, setSearch] = useState<string>("");
  const [loading, setLoading] = useState<boolean>(false);
  const [rows, setRows] = useState<EarningsDashboardRow[]>([]);
  const [asOfDate, setAsOfDate] = useState<string>("");

  const fetchRows = async (): Promise<void> => {
    setLoading(true);
    try {
      const payload = await getJson<EarningsDashboardResponse>(
        `/api/corporate-events/dashboard?daysAhead=${daysAhead}&growwOnly=${growwOnly}`,
      );
      setRows(payload.rows ?? []);
      setAsOfDate(payload.asOfDate ?? "");
    } catch (error) {
      const text = error instanceof Error ? error.message : "Failed to load earnings dashboard.";
      messageApi.error(text);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchRows();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const exportJson = async (): Promise<void> => {
    try {
      const response = await fetch(`/api/corporate-events/dashboard/export?daysAhead=${daysAhead}&growwOnly=${growwOnly}`);
      if (!response.ok) {
        throw new Error(`Export failed: HTTP ${response.status}`);
      }
      const payload = (await response.json()) as EarningsDashboardExportDocument;
      const json = JSON.stringify(payload, null, 2);
      const blob = new Blob([json], { type: "application/json" });
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `earnings-dashboard-${new Date().toISOString().split("T")[0]}.json`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
      messageApi.success("Export downloaded.");
    } catch (error) {
      const text = error instanceof Error ? error.message : "Failed to export dashboard.";
      messageApi.error(text);
    }
  };

  const filteredRows = useMemo(() => {
    const q = search.trim().toUpperCase();
    if (!q) return rows;
    return rows.filter((row) => row.symbol.toUpperCase().includes(q));
  }, [rows, search]);

  const columns: ColumnsType<EarningsDashboardRow> = [
    {
      title: "Symbol",
      dataIndex: "symbol",
      key: "symbol",
      sorter: (a, b) => a.symbol.localeCompare(b.symbol),
      defaultSortOrder: "ascend",
      fixed: "left",
      width: 110,
    },
    {
      title: "Result Date",
      dataIndex: "resultDate",
      key: "resultDate",
      sorter: (a, b) => a.resultDate.localeCompare(b.resultDate),
      width: 125,
    },
    {
      title: "D-Left",
      dataIndex: "daysToResult",
      key: "daysToResult",
      sorter: (a, b) => a.daysToResult - b.daysToResult,
      width: 90,
    },
    {
      title: "Pre15%",
      dataIndex: "pre15dReturnPct",
      key: "pre15dReturnPct",
      render: (value) => formatPct(value),
      sorter: (a, b) => (a.pre15dReturnPct ?? -9999) - (b.pre15dReturnPct ?? -9999),
      width: 95,
    },
    {
      title: "Pre10%",
      dataIndex: "pre10dReturnPct",
      key: "pre10dReturnPct",
      render: (value) => formatPct(value),
      sorter: (a, b) => (a.pre10dReturnPct ?? -9999) - (b.pre10dReturnPct ?? -9999),
      width: 95,
    },
    {
      title: "Pre15 DD%",
      dataIndex: "pre15dMaxDrawdownPct",
      key: "pre15dMaxDrawdownPct",
      render: (value) => formatPct(value),
      sorter: (a, b) => (a.pre15dMaxDrawdownPct ?? -9999) - (b.pre15dMaxDrawdownPct ?? -9999),
      width: 105,
    },
    {
      title: "Event O-C%",
      dataIndex: "eventDayOcPct",
      key: "eventDayOcPct",
      render: (value) => formatPct(value),
      sorter: (a, b) => (a.eventDayOcPct ?? -9999) - (b.eventDayOcPct ?? -9999),
      width: 110,
    },
    {
      title: "Event O-H%",
      dataIndex: "eventDayOhPct",
      key: "eventDayOhPct",
      render: (value) => formatPct(value),
      sorter: (a, b) => (a.eventDayOhPct ?? -9999) - (b.eventDayOhPct ?? -9999),
      width: 110,
    },
    {
      title: "Next O-C%",
      dataIndex: "nextDayOcPct",
      key: "nextDayOcPct",
      render: (value) => formatPct(value),
      sorter: (a, b) => (a.nextDayOcPct ?? -9999) - (b.nextDayOcPct ?? -9999),
      width: 110,
    },
    {
      title: "Next O-H%",
      dataIndex: "nextDayOhPct",
      key: "nextDayOhPct",
      render: (value) => formatPct(value),
      sorter: (a, b) => (a.nextDayOhPct ?? -9999) - (b.nextDayOhPct ?? -9999),
      width: 110,
    },
    {
      title: "Latest Close",
      dataIndex: "latestClose",
      key: "latestClose",
      render: (value) => formatPrice(value),
      sorter: (a, b) => (a.latestClose ?? -1) - (b.latestClose ?? -1),
      width: 120,
    },
    {
      title: "Latest Vol",
      dataIndex: "latestVolume",
      key: "latestVolume",
      render: (value) => formatVolume(value),
      sorter: (a, b) => (a.latestVolume ?? -1) - (b.latestVolume ?? -1),
      width: 120,
    },
    {
      title: "20D Candles",
      dataIndex: "candleCoverage20d",
      key: "candleCoverage20d",
      sorter: (a, b) => a.candleCoverage20d - b.candleCoverage20d,
      width: 105,
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      {contextHolder}
      <Space direction="vertical" size="middle" style={{ width: "100%" }}>
        <Card>
          <Space wrap>
            <Space>
              <Text strong>Days Ahead</Text>
              <InputNumber min={1} max={60} value={daysAhead} onChange={(value) => setDaysAhead(value ?? 15)} />
            </Space>
            <Space>
              <Text strong>Groww Only</Text>
              <Switch checked={growwOnly} onChange={setGrowwOnly} />
            </Space>
            <Input
              allowClear
              placeholder="Search symbol"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              style={{ width: 220 }}
            />
            <Button type="primary" icon={<ReloadOutlined />} loading={loading} onClick={fetchRows}>
              Refresh
            </Button>
            <Button icon={<DownloadOutlined />} onClick={exportJson}>
              Export JSON
            </Button>
          </Space>
        </Card>

        <Alert
          type="info"
          showIcon
          message={`Earnings observation dashboard (${filteredRows.length} rows)${asOfDate ? ` • as of ${asOfDate}` : ""}`}
          description="Default scope is Groww watchlist with upcoming result dates in the selected window."
        />

        <Table<EarningsDashboardRow>
          bordered
          size="small"
          rowKey={(record) => `${record.symbol}_${record.resultDate}`}
          loading={loading}
          columns={columns}
          dataSource={filteredRows}
          scroll={{ x: 1700 }}
          pagination={{ pageSize: 30, showSizeChanger: true }}
        />
      </Space>
    </div>
  );
}
