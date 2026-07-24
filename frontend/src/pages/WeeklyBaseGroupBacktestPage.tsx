import {
  Alert,
  Button,
  Card,
  Select,
  Space,
  Spin,
  Statistic,
  Table,
  Typography,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useState } from "react";
import { useWeeklyBaseGroupBacktest } from "../hooks/useWeeklyBaseGroupBacktest";
import type {
  HotSmaUniverseOption,
  WeeklyBaseGroupBacktestRow,
} from "../types";
import { getJson } from "../utils/api";

const { Text, Title } = Typography;
const UNIVERSES_PATH = "/api/strategy/hot-sma/universes";

export function WeeklyBaseGroupBacktestPage() {
  const [groups, setGroups] = useState<HotSmaUniverseOption[]>([]);
  const [selectedGroups, setSelectedGroups] = useState<string[]>([]);
  const [groupError, setGroupError] = useState<string | null>(null);
  const { data, loading, error, run } = useWeeklyBaseGroupBacktest();
  useEffect(() => {
    void loadGroups(setGroups, setGroupError);
  }, []);
  const columns: ColumnsType<WeeklyBaseGroupBacktestRow> = [
    { title: "Group", dataIndex: "indexKey", key: "indexKey" },
    { title: "Symbol", dataIndex: "symbol", key: "symbol" },
    {
      title: "Valid bases",
      dataIndex: "validBaseCount",
      key: "validBaseCount",
    },
    { title: "Trades", dataIndex: "filledTradeCount", key: "filledTradeCount" },
    { title: "Targets", dataIndex: "targetHitCount", key: "targetHitCount" },
    { title: "Open", dataIndex: "openTradeCount", key: "openTradeCount" },
    {
      title: "Latest zone",
      key: "zone",
      render: (_, row) =>
        row.latestZoneFloor == null
          ? "-"
          : `₹${row.latestZoneFloor.toFixed(2)} – ₹${row.latestZoneCeiling?.toFixed(2)}`,
    },
    {
      title: "SMA distance",
      dataIndex: "latestSmaDistancePct",
      key: "sma",
      render: (value: number | null) =>
        value == null ? "-" : `${value.toFixed(2)}%`,
    },
  ];
  return (
    <div style={{ padding: 24 }}>
      <Space direction="vertical" size={16} style={{ width: "100%" }}>
        <Card>
          <Space direction="vertical" size={12} style={{ width: "100%" }}>
            <Title level={3} style={{ margin: 0 }}>
              Base Rebound Group Backtest
            </Title>
            <Text type="secondary">
              Backtests the latest 200 sessions for each stock using the
              configured base/SMA rule, 1% rebound entry, and 5% target.
            </Text>
            <Select
              mode="multiple"
              style={{ minWidth: 420 }}
              placeholder="Select index groups"
              options={groups.map((group) => ({
                value: group.value,
                label: `${group.value} (${group.count})`,
              }))}
              value={selectedGroups}
              onChange={setSelectedGroups}
            />
            {groupError && <Text type="danger">{groupError}</Text>}
            <Button
              type="primary"
              disabled={selectedGroups.length === 0}
              loading={loading}
              onClick={() => void run({ indexKeys: selectedGroups })}
            >
              Run group backtest
            </Button>
          </Space>
        </Card>
        {error && <Alert type="error" message={error} showIcon />}
        {data && (
          <>
            <Card title={`${data.testedFromDate} to ${data.testedToDate}`}>
              <Space wrap>
                {data.groups.map((group) => (
                  <Statistic
                    key={group.indexKey}
                    title={`${group.indexKey}: targets`}
                    value={`${group.targetHitCount} / ${group.filledTradeCount}`}
                  />
                ))}
              </Space>
            </Card>
            <Card title="Stock results">
              <Table
                rowKey={(row) => `${row.indexKey}-${row.symbol}`}
                columns={columns}
                dataSource={data.rows}
                pagination={{ pageSize: 30 }}
                scroll={{ x: true }}
                size="small"
                expandable={{
                  expandedRowRender: (row) => (
                    <Table
                      rowKey="entryDate"
                      size="small"
                      pagination={false}
                      dataSource={row.trades}
                      columns={[
                        { title: "Entry", dataIndex: "entryDate" },
                        { title: "Target", dataIndex: "targetPrice" },
                        { title: "Exit", dataIndex: "exitDate" },
                        { title: "Outcome", dataIndex: "outcome" },
                        { title: "Hold", dataIndex: "holdingTradingDays" },
                      ]}
                    />
                  ),
                }}
              />
            </Card>
          </>
        )}
      </Space>
    </div>
  );
}

async function loadGroups(
  setGroups: (groups: HotSmaUniverseOption[]) => void,
  setError: (error: string | null) => void,
): Promise<void> {
  try {
    setGroups(
      await getJson<HotSmaUniverseOption[]>(UNIVERSES_PATH, {
        useCache: false,
      }),
    );
  } catch (error) {
    setError(
      error instanceof Error ? error.message : "Unable to load index groups.",
    );
  }
}
