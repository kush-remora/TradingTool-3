import { useEffect, useMemo, useState } from "react";
import * as LightweightChartsReact from "lightweight-charts-react";
import {
  Alert,
  Card,
  Col,
  Divider,
  Layout,
  Row,
  Space,
  Table,
  Tag,
  Typography,
} from "antd";

const { Header, Content, Footer } = Layout;
const { Title, Paragraph, Text } = Typography;

const DEFAULT_API_BASE_URL = "https://tradingtool-3-service.onrender.com";

const watchlistRows = [
  { key: "1", symbol: "RELIANCE", price: "2,938.30", move: "+0.91%" },
  { key: "2", symbol: "INFY", price: "1,982.10", move: "-0.35%" },
  { key: "3", symbol: "TCS", price: "4,275.80", move: "+0.42%" },
];

const watchlistColumns = [
  { title: "Symbol", dataIndex: "symbol", key: "symbol" },
  { title: "Price", dataIndex: "price", key: "price" },
  {
    title: "Move",
    dataIndex: "move",
    key: "move",
    render: (value) => {
      const color = value.startsWith("-") ? "red" : "green";
      return <Tag color={color}>{value}</Tag>;
    },
  },
];

function LightweightChartsReactPanel() {
  const exportedMembers = Object.keys(LightweightChartsReact);

  if (exportedMembers.length === 0) {
    return (
      <Alert
        type="warning"
        showIcon
        message="lightweight-charts-react is installed"
        description="The package currently exposes no public React components. Ant Design UI setup is complete and ready for chart wiring once a chart component export is available."
      />
    );
  }

  return (
    <Space direction="vertical" size="small">
      <Text>Exported members detected from lightweight-charts-react:</Text>
      <Text code>{exportedMembers.join(", ")}</Text>
    </Space>
  );
}

export default function App() {
  const apiBaseUrl = useMemo(() => {
    const rawValue = import.meta.env.VITE_API_BASE_URL ?? DEFAULT_API_BASE_URL;
    return rawValue.trim().replace(/\/+$/, "");
  }, []);

  const [backendHealth, setBackendHealth] = useState({
    isLoading: true,
    status: "",
    error: "",
  });

  useEffect(() => {
    let isActive = true;

    const checkHealth = async () => {
      try {
        const response = await fetch(`${apiBaseUrl}/health`, {
          method: "GET",
          credentials: "include",
          headers: {
            Accept: "application/json",
          },
        });

        if (!response.ok) {
          throw new Error(`HTTP ${response.status}`);
        }

        const payload = await response.json();
        const status =
          typeof payload?.status === "string" ? payload.status : "unknown";

        if (!isActive) {
          return;
        }

        setBackendHealth({
          isLoading: false,
          status,
          error: "",
        });
      } catch (error) {
        if (!isActive) {
          return;
        }

        setBackendHealth({
          isLoading: false,
          status: "",
          error: error instanceof Error ? error.message : "Request failed",
        });
      }
    };

    void checkHealth();
    return () => {
      isActive = false;
    };
  }, [apiBaseUrl]);

  return (
    <Layout>
      <Header>
        <Title level={3} style={{ color: "white", margin: 0 }}>
          TradingTool-3
        </Title>
      </Header>
      <Content style={{ padding: 24 }}>
        <Space direction="vertical" size="large" style={{ width: "100%" }}>
          <Card>
            <Title level={4}>React Setup</Title>
            <Paragraph>
              Frontend is configured with React + Ant Design, and the requested{" "}
              <Text code>lightweight-charts-react</Text> dependency.
            </Paragraph>
            <Paragraph>
              Backend base URL: <Text code>{apiBaseUrl}</Text>
            </Paragraph>
            {backendHealth.isLoading ? (
              <Alert
                type="info"
                showIcon
                message="Checking Render backend connection..."
              />
            ) : backendHealth.error ? (
              <Alert
                type="error"
                showIcon
                message="Backend check failed"
                description={backendHealth.error}
              />
            ) : (
              <Alert
                type="success"
                showIcon
                message={`Backend status: ${backendHealth.status}`}
              />
            )}
          </Card>

          <Row gutter={[16, 16]}>
            <Col xs={24} lg={12}>
              <Card title="Watchlist (Ant Design Table)">
                <Table
                  columns={watchlistColumns}
                  dataSource={watchlistRows}
                  pagination={false}
                  size="middle"
                />
              </Card>
            </Col>
            <Col xs={24} lg={12}>
              <Card title="Chart Module">
                <LightweightChartsReactPanel />
                <Divider />
                <Paragraph type="secondary">
                  UI components are built with Ant Design only, with no custom
                  stylesheet files.
                </Paragraph>
              </Card>
            </Col>
          </Row>
        </Space>
      </Content>
      <Footer style={{ textAlign: "center" }}>
        TradingTool-3 Frontend
      </Footer>
    </Layout>
  );
}
