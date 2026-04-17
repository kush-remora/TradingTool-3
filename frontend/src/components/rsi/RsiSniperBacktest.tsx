import {
  LineChartOutlined,
  PlayCircleOutlined,
  ArrowUpOutlined,
  ArrowDownOutlined,
  LinkOutlined,
  EyeOutlined
} from "@ant-design/icons";
import {
  Alert,
  Button,
  Card,
  Checkbox,
  Col,
  DatePicker,
  Form,
  InputNumber,
  Radio,
  Row,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
  Tooltip
} from "antd";
import type { ColumnsType } from "antd/es/table";
import dayjs from "dayjs";
import { useRsiSniperBacktest } from "../../hooks/useRsiSniperBacktest";
import type { BacktestTrade, RsiBacktestExitMode, RsiMomentumBacktestRequest } from "../../types";
import { formatInr } from "./RsiBoard";

interface RsiSniperBacktestProps {
  profileId: string;
}

export function RsiSniperBacktest({ profileId }: RsiSniperBacktestProps) {
  const { report, loading, error, runBacktest } = useRsiSniperBacktest();
  const [form] = Form.useForm();
  const formatDateWithDay = (value: string) => `${value} (${dayjs(value).format("ddd")})`;

  const onFinish = (values: any) => {
    const request: RsiMomentumBacktestRequest = {
      profileId,
      logicType: values.logicType,
      fromDate: values.range?.[0]?.format("YYYY-MM-DD"),
      toDate: values.range?.[1]?.format("YYYY-MM-DD"),
      initialCapital: values.initialCapital,
      targetPct: values.targetPct,
      stopLossPct: values.stopLossPct,
      runBackfill: true,
      entryRankMin: values.entryRankMin,
      entryRankMax: values.entryRankMax,
      rankLookbackDays: values.rankLookbackDays,
      jumpMin: values.jumpMin,
      jumpMax: values.jumpMax,
      blockedEntryDays: values.blockedEntryDays ?? [],
      exitMode: values.exitMode as RsiBacktestExitMode,
      rsiExitThreshold: values.rsiExitThreshold,
    };
    void runBacktest(request);
  };

  const columns: ColumnsType<BacktestTrade> = [
    {
      title: "Stock",
      key: "stock",
      fixed: "left",
      width: 180,
      render: (_, row) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{row.symbol}</Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 11 }}>
            {row.companyName}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: "Entry",
      key: "entry",
      width: 150,
      render: (_, row) => (
        <Space direction="vertical" size={0}>
          <Typography.Text>{formatDateWithDay(row.entryDate)}</Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            Rank #{row.entryRank} ({row.entryRankImprovement && row.entryRankImprovement > 0 ? "+" : ""}{row.entryRankImprovement || 0})
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: "Jump Filter",
      key: "jump",
      width: 150,
      render: (_, row) => (
        <Space direction="vertical" size={0}>
          <Typography.Text>Farthest: #{row.entryFarthestRankInLookback ?? "-"}</Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            Jump: {typeof row.entryJumpFromFarthest === "number" ? `+${row.entryJumpFromFarthest}` : "-"}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: "Exit",
      dataIndex: "exitDate",
      key: "exitDate",
      width: 120,
      render: (value: string) => formatDateWithDay(value),
    },
    {
      title: "RSI22",
      key: "rsi22",
      width: 150,
      render: (_, row) => (
        <Space direction="vertical" size={0}>
          <Typography.Text>Entry: {typeof row.entryRsi22 === "number" ? row.entryRsi22.toFixed(2) : "-"}</Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            Exit: {typeof row.exitRsi22 === "number" ? row.exitRsi22.toFixed(2) : "-"}
          </Typography.Text>
        </Space>
      ),
    },
    {
        title: "Prices",
        key: "prices",
        width: 180,
        render: (_, row) => (
            <Space direction="vertical" size={0}>
                <Typography.Text>Entry: {formatInr(row.entryPrice)}</Typography.Text>
                <Typography.Text>Exit: {formatInr(row.exitPrice)}</Typography.Text>
            </Space>
        )
    },
    {
      title: "Result",
      dataIndex: "result",
      key: "result",
      width: 120,
      render: (val: string, row) => (
        <Tag color={val === "PROFIT" ? "success" : "error"} style={{ fontWeight: 600 }}>
          {val === "PROFIT" ? <ArrowUpOutlined /> : <ArrowDownOutlined />} {row.profitPct.toFixed(2)}%
        </Tag>
      ),
    },
    {
      title: "Exit Reason",
      dataIndex: "exitReason",
      key: "exitReason",
      width: 160,
      render: (val: string) => <Tag>{val}</Tag>,
    },
    {
        title: "Days",
        dataIndex: "holdingDays",
        key: "holdingDays",
        width: 100,
        render: (val) => `${val}d`
    },
    {
      title: "Actions",
      key: "actions",
      fixed: "right",
      width: 150,
      render: (_, row) => (
        <Space>
          <Tooltip title="View Snapshot">
             <Button 
                size="small" 
                icon={<EyeOutlined />} 
                onClick={() => {
                  const baseUrl = import.meta.env.BASE_URL.endsWith('/') 
                    ? import.meta.env.BASE_URL.slice(0, -1) 
                    : import.meta.env.BASE_URL;
                  window.open(`${baseUrl}/rsi-momentum-safe?date=${row.entryDate}&profileId=${profileId}`, "_blank");
                }} 
             />
          </Tooltip>
          <Tooltip title="Groww Chart">
            <Button
              size="small"
              icon={<LineChartOutlined />}
              onClick={() => window.open(`https://groww.in/stocks/${row.symbol.toLowerCase().replace("&", "")}-ltd`, "_blank")}
            />
          </Tooltip>
          <Tooltip title="Kite Chart">
            <Button
              size="small"
              icon={<LinkOutlined />}
              onClick={() => window.open(`https://kite.zerodha.com/chart/ext/tvc/NSE/${row.symbol}/${row.entryRank}`, "_blank")}
            />
          </Tooltip>
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <Card size="small" title="Backtest Configuration">
        <Form
          form={form}
          layout="inline"
          onFinish={onFinish}
          initialValues={{
            logicType: "HYBRID",
            range: [dayjs().subtract(3, "month"), dayjs()],
            initialCapital: 100000,
            targetPct: 10,
            stopLossPct: 3,
            entryRankMin: 21,
            entryRankMax: 40,
            rankLookbackDays: 5,
            jumpMin: 0,
            jumpMax: 3,
            blockedEntryDays: ["WEDNESDAY", "FRIDAY"],
            exitMode: "T_PLUS_3_OR_RSI_60",
            rsiExitThreshold: 60,
          }}
        >
          <Form.Item name="logicType" label="Logic">
            <Radio.Group buttonStyle="solid" size="small">
              <Radio.Button value="LEADER">Leader</Radio.Button>
              <Radio.Button value="JUMPER">Jumper</Radio.Button>
              <Radio.Button value="HYBRID">Hybrid</Radio.Button>
            </Radio.Group>
          </Form.Item>
          <Form.Item name="range" label="Range">
            <DatePicker.RangePicker size="small" />
          </Form.Item>
          <Form.Item name="initialCapital" label="Capital">
            <InputNumber size="small" style={{ width: 100 }} prefix="₹" />
          </Form.Item>
          <Form.Item name="targetPct" label="Target">
            <InputNumber size="small" style={{ width: 70 }} suffix="%" />
          </Form.Item>
          <Form.Item name="stopLossPct" label="SL">
            <InputNumber size="small" style={{ width: 70 }} suffix="%" />
          </Form.Item>
          <Form.Item name="entryRankMin" label="Rank Min">
            <InputNumber size="small" style={{ width: 80 }} min={1} />
          </Form.Item>
          <Form.Item name="entryRankMax" label="Rank Max">
            <InputNumber size="small" style={{ width: 80 }} min={1} />
          </Form.Item>
          <Form.Item name="rankLookbackDays" label="Lookback">
            <InputNumber size="small" style={{ width: 80 }} min={5} max={10} />
          </Form.Item>
          <Form.Item name="jumpMin" label="Jump Min">
            <InputNumber size="small" style={{ width: 80 }} />
          </Form.Item>
          <Form.Item name="jumpMax" label="Jump Max">
            <InputNumber size="small" style={{ width: 80 }} />
          </Form.Item>
          <Form.Item name="blockedEntryDays" label="No Entry Days">
            <Checkbox.Group
              options={[
                { label: "Mon", value: "MONDAY" },
                { label: "Tue", value: "TUESDAY" },
                { label: "Wed", value: "WEDNESDAY" },
                { label: "Thu", value: "THURSDAY" },
                { label: "Fri", value: "FRIDAY" },
              ]}
            />
          </Form.Item>
          <Form.Item name="exitMode" label="Exit">
            <Radio.Group buttonStyle="solid" size="small">
              <Radio.Button value="T_PLUS_3">T+3</Radio.Button>
              <Radio.Button value="RSI_60">RSI</Radio.Button>
              <Radio.Button value="T_PLUS_3_OR_RSI_60">T+3 or RSI</Radio.Button>
            </Radio.Group>
          </Form.Item>
          <Form.Item name="rsiExitThreshold" label="RSI Exit">
            <InputNumber size="small" style={{ width: 80 }} min={40} max={90} />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" icon={<PlayCircleOutlined />} loading={loading} size="small">
              Run
            </Button>
          </Form.Item>
        </Form>
      </Card>

      {error && <Alert type="error" showIcon message={error} />}

      {report && (
        <>
          <Row gutter={16}>
            <Col span={6}>
              <Card size="small">
                <Statistic
                  title="Final Capital"
                  value={report.finalCapital}
                  prefix="₹"
                  valueStyle={{ color: report.totalProfit >= 0 ? "#3f8600" : "#cf1322" }}
                />
              </Card>
            </Col>
            <Col span={6}>
              <Card size="small">
                <Statistic
                  title="Total P&L"
                  value={report.totalProfitPct}
                  suffix="%"
                  precision={2}
                  valueStyle={{ color: report.totalProfit >= 0 ? "#3f8600" : "#cf1322" }}
                />
              </Card>
            </Col>
            <Col span={6}>
              <Card size="small">
                <Statistic title="Win Rate" value={report.winRate} suffix="%" precision={1} />
              </Card>
            </Col>
            <Col span={6}>
              <Card size="small">
                <Statistic title="Trades" value={report.totalTrades} />
              </Card>
            </Col>
          </Row>

          <Card size="small" title="Trade History">
            <Table
              columns={columns}
              dataSource={report.trades}
              rowKey={(r) => `${r.symbol}-${r.entryDate}`}
              size="small"
              pagination={{ pageSize: 10 }}
              scroll={{ x: 1000 }}
            />
          </Card>
          
          <Alert
            type="info"
            message="Backtest Methodology"
            description={
              <ul style={{ margin: 0, paddingLeft: 16 }}>
                <li><b>Entry:</b> Rank band {report.entryRankMin}-{report.entryRankMax}, jump band {report.jumpMin}-{report.jumpMax}, lookback {report.rankLookbackDays} days.</li>
                <li><b>Entry RSI:</b> RSI22 must be between 50 and 55.</li>
                <li><b>Trend Guard:</b> Entry only if Close &gt; EMA20 on entry day.</li>
                <li><b>No Hidden Filters:</b> Move-from-low, max-daily-move, and volume filters are not applied in this backtest mode.</li>
                <li><b>Blocked days:</b> {report.blockedEntryDays.length > 0 ? report.blockedEntryDays.join(", ") : "None"}.</li>
                <li><b>Exit:</b> {report.exitMode} (RSI threshold: {report.rsiExitThreshold}).</li>
                <li><b>Execution:</b> Entry at close, exit at close of trigger day.</li>
              </ul>
            }
          />
        </>
      )}
    </Space>
  );
}
