import React, { useEffect, useRef } from "react";
import { 
  createChart, 
  ColorType, 
  IChartApi, 
  SeriesMarker, 
  Time, 
  ISeriesApi, 
  CandlestickSeries, 
  LineSeries,
  createSeriesMarkers
} from "lightweight-charts";
import { Card, Slider, Typography, Space, Spin, Empty, Statistic, Row, Col, Tag, Table } from "antd";
import { SwingAnalysisResponse } from "../../types";

const { Text, Title } = Typography;

interface SwingVisualizerProps {
  data: SwingAnalysisResponse | null;
  loading: boolean;
  reversal: number;
  onReversalChange: (val: number) => void;
}

export const SwingVisualizer: React.FC<SwingVisualizerProps> = ({
  data,
  loading,
  reversal,
  onReversalChange,
}) => {
  const chartContainerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<IChartApi | null>(null);
  const candleSeriesRef = useRef<ISeriesApi<"Candlestick"> | null>(null);
  const zigzagSeriesRef = useRef<ISeriesApi<"Line"> | null>(null);

  const initChart = () => {
    if (!chartContainerRef.current || chartRef.current) return;

    const chart = createChart(chartContainerRef.current, {
      layout: {
        background: { type: ColorType.Solid, color: "#ffffff" },
        textColor: "#333",
      },
      width: chartContainerRef.current.clientWidth || 800,
      height: 500,
      grid: {
        vertLines: { color: "#f0f0f0" },
        horzLines: { color: "#f0f0f0" },
      },
      timeScale: {
        borderColor: "#d1d4dc",
        timeVisible: true,
      },
      rightPriceScale: {
        borderColor: "#d1d4dc",
      },
    });

    chartRef.current = chart;

    const handleResize = () => {
      if (chartContainerRef.current && chartRef.current) {
        chartRef.current.applyOptions({ width: chartContainerRef.current.clientWidth });
      }
    };

    window.addEventListener("resize", handleResize);
    
    return () => {
      window.removeEventListener("resize", handleResize);
      if (chartRef.current) {
        chartRef.current.remove();
        chartRef.current = null;
        candleSeriesRef.current = null;
        zigzagSeriesRef.current = null;
      }
    };
  };

  useEffect(() => {
    if (!data) return;

    const cleanup = initChart();
    const chart = chartRef.current;
    if (!chart) return;
    
    if (candleSeriesRef.current) chart.removeSeries(candleSeriesRef.current);
    if (zigzagSeriesRef.current) chart.removeSeries(zigzagSeriesRef.current);

    // 1. Prepare Candlestick Data (Simple string dates now)
    const chartData = data.candles
      .map(c => ({
        time: c.date as Time,
        open: c.open,
        high: c.high,
        low: c.low,
        close: c.close,
      }))
      .sort((a, b) => (a.time > b.time ? 1 : -1))
      .filter((item, index, self) => index === 0 || item.time !== self[index - 1].time);

    const candleSeries = chart.addSeries(CandlestickSeries, {
      upColor: "#26a69a",
      downColor: "#ef5350",
      borderVisible: false,
      wickUpColor: "#26a69a",
      wickDownColor: "#ef5350",
    });
    candleSeriesRef.current = candleSeries;
    candleSeries.setData(chartData);

    // 2. Prepare ZigZag Data
    const zigzagData = data.points
      .map(p => ({
        time: p.date as Time,
        value: p.price,
      }))
      .sort((a, b) => (a.time > b.time ? 1 : -1))
      .filter((item, index, self) => index === 0 || item.time !== self[index - 1].time);

    const zigzagSeries = chart.addSeries(LineSeries, {
      color: "rgba(33, 150, 243, 0.7)",
      lineWidth: 2,
      lastValueVisible: false,
      priceLineVisible: false,
    });
    zigzagSeriesRef.current = zigzagSeries;
    zigzagSeries.setData(zigzagData);

    // 3. Prepare Markers (Attached to BOTH series for safety)
    const markers: SeriesMarker<Time>[] = data.points
      .map(p => ({
        time: p.date as Time,
        position: p.type === "PEAK" ? "aboveBar" : "belowBar",
        color: p.type === "PEAK" ? "#d32f2f" : "#2e7d32",
        shape: p.type === "PEAK" ? "arrowDown" : "arrowUp",
        text: `${p.dayOfWeek.substring(0, 3)} ${p.changePct > 0 ? "+" : ""}${p.changePct}%`,
      } as SeriesMarker<Time>))
      .sort((a, b) => (a.time > b.time ? 1 : -1));

    if (markers.length > 0) {
      // Attach to candle series
      const candleMarkers = createSeriesMarkers(candleSeries, markers, {
        zOrder: "top",
        autoScale: true
      });
      candleSeries.attachPrimitive(candleMarkers);

      // Also attach to zigzag series (different position type for Line)
      const lineMarkers: SeriesMarker<Time>[] = markers.map(m => ({
          ...m,
          position: m.position === "aboveBar" ? "inPriceReal" : "inPriceReal" // Lines don't have "aboveBar"
      }));
    }
    
    chart.timeScale().fitContent();

    return cleanup;
  }, [data]);

  return (
    <Space direction="vertical" style={{ width: "100%" }} size="large">
      <Card title="Structure Controls" size="small">
        <Row align="middle" gutter={24}>
          <Col span={12}>
            <Text>ZigZag Sensitivity (Reversal %): <b>{reversal}%</b></Text>
            <Slider
              min={1}
              max={25}
              step={0.5}
              value={reversal}
              onChange={onReversalChange}
              marks={{ 1: "Noise", 5: "Swing", 10: "Trend", 20: "Major" }}
            />
          </Col>
          <Col span={12}>
             <Text type="secondary">
                A reversal of {reversal}% is required to mark a new peak or trough.
             </Text>
          </Col>
        </Row>
      </Card>

      {loading ? (
        <Card style={{ textAlign: "center", padding: "100px 0" }}>
          <Spin size="large" tip="Analyzing Swing Structure..." />
        </Card>
      ) : !data ? (
        <Card>
          <Empty description="Search for a stock to visualize its swings" />
        </Card>
      ) : (
        <>
          <Row gutter={16}>
            <Col span={6}>
              <Card size="small">
                <Statistic title="Avg Upswing" value={data.stats.averageUpswingPct} precision={2} suffix="%" valueStyle={{ color: "#3f8600" }} />
              </Card>
            </Col>
            <Col span={6}>
              <Card size="small">
                <Statistic title="Avg Downswing" value={data.stats.averageDownswingPct} precision={2} suffix="%" valueStyle={{ color: "#cf1322" }} />
              </Card>
            </Col>
            <Col span={6}>
              <Card size="small">
                <Statistic title="Avg Swing Duration" value={data.stats.averageSwingDurationBars} precision={1} suffix=" days" />
              </Card>
            </Col>
            <Col span={6}>
              <Card size="small">
                <Statistic title="Identified Pivots" value={data.points.length} />
              </Card>
            </Col>
          </Row>

          <Card 
            title={<Title level={4} style={{ margin: 0 }}>{data.symbol} Swing Structure</Title>}
            extra={<Tag color="processing">Auto-calculated Swings</Tag>}
          >
            <div ref={chartContainerRef} style={{ width: "100%", minHeight: "500px" }} />
            <div style={{ marginTop: 16 }}>
               <Text type="secondary" style={{ fontSize: 12 }}>
                 * <b>Blue Line:</b> The ZigZag structure connecting pivots. <br/>
                 * <b>Labels:</b> Day of the week and percentage move from previous pivot.
               </Text>
            </div>
          </Card>

          <Card title="Pivot Data Table" size="small">
            <Table 
              dataSource={data.points.map((p, i) => ({ ...p, key: i })).reverse()} 
              columns={[
                { title: "Date", dataIndex: "date", key: "date" },
                { 
                  title: "Day", 
                  dataIndex: "dayOfWeek", 
                  key: "dayOfWeek",
                  render: (day: string) => (
                    <Text strong style={{ color: day === "Monday" ? "#1890ff" : "inherit" }}>{day}</Text>
                  )
                },
                { title: "Pivot Type", dataIndex: "type", key: "type", render: (t: string) => <Tag color={t === "PEAK" ? "volcano" : "green"}>{t}</Tag> },
                { title: "Pivot Price", dataIndex: "price", key: "price", render: (p: number) => `₹${p.toLocaleString("en-IN")}` },
                { 
                  title: "Move %", 
                  dataIndex: "changePct", 
                  key: "changePct",
                  render: (v: number) => (
                    <Text style={{ color: v >= 0 ? "#3f8600" : "#cf1322" }}>
                      {v >= 0 ? "+" : ""}{v}%
                    </Text>
                  )
                },
                { title: "Duration (Bars)", dataIndex: "barsSinceLast", key: "barsSinceLast" },
              ]}
              pagination={{ pageSize: 10 }}
              size="small"
            />
          </Card>
        </>
      )}
    </Space>
  );
};
