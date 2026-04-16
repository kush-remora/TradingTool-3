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
import type { BacktestTrade, RsiMomentumBacktestRequest } from "../../types";
import { formatInr } from "./RsiBoard";

interface RsiSniperBacktestProps {
  profileId: string;
}

export function RsiSniperBacktest({ profileId }: RsiSniperBacktestProps) {
  const { report, loading, error, runBacktest } = useRsiSniperBacktest();
  const [form] = Form.useForm();

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
          <Typography.Text>{row.entryDate}</Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            Rank #{row.entryRank} ({row.entryRankImprovement && row.entryRankImprovement > 0 ? "+" : ""}{row.entryRankImprovement || 0})
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: "Exit",
      dataIndex: "exitDate",
      key: "exitDate",
      width: 120,
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
                <li><b>Entry:</b> Executed at day's close when filters and logic are met.</li>
                <li><b>Target:</b> Fixed {report.trades[0]?.targetPrice ? ((report.trades[0].targetPrice / report.trades[0].entryPrice - 1) * 100).toFixed(0) : 10}% profit.</li>
                <li><b>Stop Loss:</b> Fixed {report.trades[0]?.stopLossPrice ? ((1 - report.trades[0].stopLossPrice / report.trades[0].entryPrice) * 100).toFixed(0) : 3}% loss.</li>
                <li><b>Verification:</b> High/Low of daily candles used to check if exit hit.</li>
              </ul>
            }
          />
        </>
      )}
    </Space>
  );
}
