import { useEffect, useState } from "react";
import { Button, Typography, message, Row, Col, Progress, Table, Card, Statistic } from "antd";
import { ArrowLeftOutlined, ArrowRightOutlined } from "@ant-design/icons";
import { WeeklyPatternDetail, WeekHeatmapRow, TechnicalContext } from "../types";
import { StockBadge } from "./StockBadge";
import { LiveMarketContext } from "./LiveMarketContext";
import { getJson } from "../utils/api";

const { Text, Title, Paragraph } = Typography;

interface ScreenerDetailProps {
  symbol: string;
  onBack: () => void;
}

export function ScreenerDetail({ symbol, onBack }: ScreenerDetailProps) {
  const [data, setData] = useState<WeeklyPatternDetail | null>(null);
  const [techContext, setTechContext] = useState<TechnicalContext | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      try {
        const json = await getJson<WeeklyPatternDetail>(`/api/screener/weekly-pattern/${symbol}`);
        setData(json);
      } catch (err) {
        console.error(err);
        message.error("API connection error");
      } finally {
        setLoading(false);
      }
    };
    
    const fetchTech = async () => {
      try {
        const json = await getJson<TechnicalContext>(`/api/stock/${symbol}/technical-context`);
        setTechContext(json);
      } catch (err) {
        console.error("Failed to load technical context");
      }
    };

    fetchData();
    fetchTech();
  }, [symbol]);

  if (loading || !data) {
    return <div style={{ padding: 48, textAlign: 'center' }}>Loading details...</div>;
  }

  const heatmapCols = [
    { 
      title: "Week", 
      dataIndex: "weekLabel", 
      key: "weekLabel", 
      align: 'center' as const,
      render: (val: string, record: WeekHeatmapRow) => (
        <div style={{ lineHeight: '1.2' }}>
          <div>{val}</div>
          <Text type="secondary" style={{ fontSize: 11 }}>{record.startDate} -<br/>{record.endDate}</Text>
        </div>
      )
    },
    { 
      title: "Mon", 
      dataIndex: "mondayChangePct", 
      key: "mondayChangePct", 
      align: 'center' as const,
      render: (val: number | null, record: WeekHeatmapRow) => renderHeatmapCell(val, "Mon", record) 
    },
    { 
      title: "Tue", 
      dataIndex: "tuesdayChangePct", 
      key: "tuesdayChangePct", 
      align: 'center' as const,
      render: (val: number | null, record: WeekHeatmapRow) => renderHeatmapCell(val, "Tue", record) 
    },
    { 
      title: "Wed", 
      dataIndex: "wednesdayChangePct", 
      key: "wednesdayChangePct", 
      align: 'center' as const,
      render: (val: number | null, record: WeekHeatmapRow) => renderHeatmapCell(val, "Wed", record) 
    },
    { 
      title: "Thu", 
      dataIndex: "thursdayChangePct", 
      key: "thursdayChangePct", 
      align: 'center' as const,
      render: (val: number | null, record: WeekHeatmapRow) => renderHeatmapCell(val, "Thu", record) 
    },
    { 
      title: "Fri", 
      dataIndex: "fridayChangePct", 
      key: "fridayChangePct", 
      align: 'center' as const,
      render: (val: number | null, record: WeekHeatmapRow) => renderHeatmapCell(val, "Fri", record) 
    },
    {
      title: 'Execution',
      key: 'execution',
      render: (_: any, record: WeekHeatmapRow) => {
        if (!record.entryTriggered || !record.buyPriceActual) return <Text type="secondary">No entry</Text>;
        const rsiColor = record.reasoning === "Overbought (100d Peak)" ? '#cf1322' : '#8c8c8c';
        return (
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            <Text strong style={{ fontSize: 13 }}>₹{record.buyPriceActual} <ArrowRightOutlined style={{ fontSize: 10, color: '#bfbfbf', margin: '0 4px' }}/> {record.sellPriceActual ? `₹${record.sellPriceActual}` : 'Hold'}</Text>
            {record.buyRsi && <Text style={{ fontSize: 11, color: rsiColor }}>Buy RSI: {record.buyRsi}</Text>}
          </div>
        );
      }
    },
    {
      title: 'Result',
      key: 'result',
      render: (_: any, record: WeekHeatmapRow) => {
        if (!record.entryTriggered) return <Text type="secondary" style={{ fontSize: 12 }}>{record.reasoning}</Text>;
        const color = record.swingTargetHit ? '#389e0d' : '#cf1322';
        return (
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            {record.netSwingPct !== null && <Text strong style={{ color }}>{record.netSwingPct > 0 ? '+' : ''}{record.netSwingPct}%</Text>}
            <Text type="secondary" style={{ fontSize: 11 }}>{record.reasoning}</Text>
          </div>
        );
      }
    },
    {
      title: 'Max Potential',
      key: 'potential',
      render: (_: any, record: WeekHeatmapRow) => (
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          <Text strong style={{ color: '#0958d9' }}>{record.maxPotentialPct ? `+${record.maxPotentialPct}%` : '-'}</Text>
          <Text type="secondary" style={{ fontSize: 11 }}>Raw swing</Text>
        </div>
      )
    }
  ];

  const recentSessionColumns = [
    { title: 'Date', dataIndex: 'date', key: 'date', render: (val: string) => <Text style={{ fontSize: 13 }}>{val}</Text> },
    { title: 'Day', dataIndex: 'dayOfWeek', key: 'dayOfWeek', render: (val: string) => <Text style={{ fontSize: 12 }} type="secondary">{val}</Text> },
    { title: 'Open', dataIndex: 'open', key: 'open', render: (val: number) => `₹${val}` },
    { title: 'High', dataIndex: 'high', key: 'high', render: (val: number) => <Text strong style={{ color: '#0958d9' }}>₹{val}</Text> },
    { title: 'Low', dataIndex: 'low', key: 'low', render: (val: number) => <Text strong style={{ color: '#cf1322' }}>₹{val}</Text> },
    { title: 'Close', dataIndex: 'close', key: 'close', render: (val: number) => `₹${val}` },
    { title: 'Range', dataIndex: 'range', key: 'range', render: (val: number) => `₹${val}` },
    { 
      title: 'Low→High %', 
      dataIndex: 'lowToHighPct',
      key: 'lowToHighPct',
      render: (val: number) => <Text style={{ color: val >= 1.0 ? '#389e0d' : '#8c8c8c', fontWeight: val >= 1.0 ? 600 : 400 }}>{val > 0 ? '+' : ''}{val}%</Text> 
    },
    { 
      title: 'Vol', 
      dataIndex: 'volume', 
      key: 'volume',
      render: (val: number) => {
        const v = (val / 100000).toFixed(2);
        return `${v}L`;
      } 
    }
  ];

  function renderHeatmapCell(val: number | null, day: string, record: WeekHeatmapRow) {
    if (val === null) return <Text type="secondary">-</Text>;
    const isPos = val > 0;
    const txtColor = isPos ? '#389e0d' : '#cf1322';
    const txt = `${isPos ? '+' : ''}${val}%`;
    
    let bg = 'transparent';
    if (day === data?.buyDay) {
      bg = record.entryTriggered ? '#f6ffed' : '#fff1f0';
    } else if (day === data?.sellDay) {
      bg = record.swingTargetHit ? '#f6ffed' : '#fff1f0';
    }

    return (
      <div style={{ background: bg, padding: '8px 4px', borderRadius: 4 }}>
        <Text style={{ color: txtColor, fontWeight: (day === data?.buyDay || day === data?.sellDay) ? 600 : 400 }}>{txt}</Text>
      </div>
    );
  }

  const statCardStyle = { flex: 1, background: '#fafafa', padding: '16px', borderRadius: 8, border: '1px solid #f0f0f0' };

  return (
    <div style={{ padding: "24px 32px", maxWidth: 1000, margin: "0 auto" }}>
      <div style={{ background: '#fff', borderRadius: 12, padding: 32, boxShadow: '0 4px 12px rgba(0,0,0,0.05)' }}>
        
        {/* Header */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 32 }}>
          <div style={{ flex: 1 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 4 }}>
              <StockBadge symbol={data.symbol} instrumentToken={data.instrumentToken} companyName={data.companyName} fontSize={24} />
              <span style={{ 
                background: data.patternConfirmed ? '#f6ffed' : '#fff1f0', 
                color: data.patternConfirmed ? '#389e0d' : '#cf1322', 
                padding: '4px 12px', borderRadius: 20, fontSize: 13, fontWeight: 600
              }}>
                {data.patternConfirmed ? `Weekly pattern confirmed (Buy zone: ₹${data.buyDayLowMin} - ₹${data.buyDayLowMax})` : 'No strict pattern'}
              </span>
            </div>
            <Text type="secondary">
              {data.companyName} • {data.weeksAnalyzed}-week lookback • Score: {data.compositeScore}/100
            </Text>
            
            <div style={{ marginTop: 24 }}>
              <LiveMarketContext symbol={`${data.exchange}:${data.symbol}`} />
            </div>
          </div>
          <div style={{ width: 300, marginLeft: 24 }}>
            <Card size="small" bordered={false}>
              <Statistic 
                title="Win Rate (Swing Efficiency)" 
                value={data.reboundConsistency > 0 ? `${data.swingConsistency} / ${data.reboundConsistency}` : `0 / 0`} 
                suffix="Successful entries" 
                valueStyle={{ color: '#52c41a' }} 
              />
              <div style={{ marginTop: 8, fontSize: 12, color: '#8c8c8c' }}>
                <div style={{ marginBottom: 4 }}><span style={{ color: '#389e0d' }}>✓</span> {data.swingConsistency} Target Hits</div>
                <div style={{ marginBottom: 4 }}><span style={{ color: '#cf1322' }}>✕</span> {data.reboundConsistency - data.swingConsistency} Failed to hit target</div>
                <div><span style={{ color: '#faad14' }}>⚠</span> {data.weeksAnalyzed - data.reboundConsistency} Skipped (Overbought/No dip)</div>
              </div>
            </Card>
          </div>
          <Button icon={<ArrowLeftOutlined />} onClick={onBack}>Back</Button>
        </div>

        {/* 4 Key Stat Boxes */}
        <div style={{ display: 'flex', gap: 16, marginBottom: 32 }}>
          <div style={statCardStyle}>
            <Text type="secondary" style={{ fontSize: 13 }}>Avg {data.buyDay} dip</Text>
            <div style={{ fontSize: 24, fontWeight: 700, margin: '4px 0' }}>{data.buyDayAvgDipPct}%</div>
            <Text type="secondary" style={{ fontSize: 12 }}>Daily Open to Low</Text>
          </div>
          <div style={statCardStyle}>
            <Text type="secondary" style={{ fontSize: 13 }}>1% Rebound consistency</Text>
            <div style={{ fontSize: 24, fontWeight: 700, margin: '4px 0' }}>{data.reboundConsistency}/{data.weeksAnalyzed}</div>
            <Text type="secondary" style={{ fontSize: 12 }}>{data.buyDay} 1% Low bounce</Text>
          </div>
          <div style={statCardStyle}>
            <Text type="secondary" style={{ fontSize: 13 }}>Avg {data.buyDay}→{data.sellDay} swing</Text>
            <div style={{ fontSize: 24, fontWeight: 700, margin: '4px 0', color: '#389e0d' }}>+{data.swingAvgPct}%</div>
            <Text type="secondary" style={{ fontSize: 12 }}>{data.buyDay} bounce → {data.sellDay} high</Text>
          </div>
          <div style={statCardStyle}>
            <Text type="secondary" style={{ fontSize: 13 }}>Avg Ideal Potential</Text>
            <div style={{ fontSize: 24, fontWeight: 700, margin: '4px 0', color: '#0958d9' }}>{data.avgPotentialPct}%</div>
            <Text type="secondary" style={{ fontSize: 12 }}>{data.buyDay} low → Week High</Text>
          </div>
          <div style={statCardStyle}>
            <Text type="secondary" style={{ fontSize: 13 }}>Swing consistency</Text>
            <div style={{ fontSize: 24, fontWeight: 700, margin: '4px 0' }}>{data.swingConsistency}/{data.weeksAnalyzed}</div>
            <Text type="secondary" style={{ fontSize: 12 }}>Pairs ≥ 4% swing</Text>
          </div>
        </div>

        {/* Day of Week Profile */}
        <div style={{ marginBottom: 32 }}>
          <Text strong style={{ display: 'block', marginBottom: 12, fontSize: 15 }}>Day-of-week profile</Text>
          <div style={{ display: 'flex', gap: 12 }}>
            {data.dayOfWeekProfile.map(p => {
              let bg = '#fafafa';
              let borderColor = '#f0f0f0';
              let actionColor = '#8c8c8c';
              
              if (p.action === 'Buy zone') { bg = '#f6ffed'; borderColor = '#b7eb8f'; actionColor = '#389e0d'; }
              if (p.action === 'Watch') { bg = '#fcffe6'; borderColor = '#eaff8f'; actionColor = '#7cb305'; }
              if (p.action === 'Sell zone') { bg = '#fff1f0'; borderColor = '#ffa39e'; actionColor = '#cf1322'; }
              if (p.action === 'Exit') { bg = '#fff2e8'; borderColor = '#ffbb96'; actionColor = '#d4380d'; }

              const isPos = p.avgChangePct > 0;
              const valColor = isPos ? '#389e0d' : (p.avgChangePct < 0 ? '#cf1322' : '#8c8c8c');

              return (
                <div key={p.day} style={{ 
                  flex: 1, background: bg, border: '1px solid ' + borderColor, 
                  borderRadius: 8, padding: '12px 0', textAlign: 'center' 
                }}>
                  <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 2 }}>{p.day}</div>
                  <div style={{ fontSize: 13, color: actionColor, fontWeight: 600, marginBottom: 4 }}>{p.action}</div>
                  <div style={{ fontSize: 15, fontWeight: 700, color: valColor }}>
                    {isPos ? '+' : ''}{p.avgChangePct}%
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Technical Context */}
        {techContext && (
          <div style={{ marginBottom: 32 }}>
            <Text strong style={{ display: 'block', marginBottom: 12, fontSize: 15 }}>Macro context</Text>
            <div style={{ display: 'flex', gap: 16 }}>
              <div style={statCardStyle}>
                <Text type="secondary" style={{ fontSize: 13 }}>LTP / SMA200</Text>
                <div style={{ fontSize: 18, fontWeight: 700, margin: '4px 0', color: techContext.ltp >= techContext.sma200 ? '#389e0d' : '#cf1322' }}>
                  ₹{techContext.ltp} / ₹{techContext.sma200}
                </div>
                <Text type="secondary" style={{ fontSize: 12 }}>Trend condition</Text>
              </div>
              <div style={statCardStyle}>
                <Text type="secondary" style={{ fontSize: 13 }}>Avg True Range (14D)</Text>
                <div style={{ fontSize: 18, fontWeight: 700, margin: '4px 0' }}>₹{techContext.atr14}</div>
                <Text type="secondary" style={{ fontSize: 12 }}>{(techContext.atr14 / techContext.ltp * 100).toFixed(2)}% normal daily swing</Text>
              </div>
              <div style={statCardStyle}>
                <Text type="secondary" style={{ fontSize: 13 }}>Current RSI (14d)</Text>
                <div style={{ fontSize: 18, fontWeight: 700, margin: '4px 0', color: techContext.adaptiveRsi?.isOversold ? '#389e0d' : (techContext.adaptiveRsi?.isOverbought ? '#cf1322' : '#8c8c8c') }}>
                  {techContext.rsi14}
                </div>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  {techContext.adaptiveRsi?.isOverbought ? 'Overbought (100d Peak)' : 
                   techContext.adaptiveRsi?.isOversold ? 'Oversold (100d Low)' : 
                   `Percentile: ${techContext.adaptiveRsi?.percentile}%`}
                </Text>
              </div>
              <div style={statCardStyle}>
                <Text type="secondary" style={{ fontSize: 13 }}>Historic RSI bounds</Text>
                <div style={{ fontSize: 13, fontWeight: 600, margin: '4px 0', color: '#595959', display: 'flex', flexDirection: 'column' }}>
                  <span>50D: {techContext.lowestRsi50d} - {techContext.highestRsi50d}</span>
                  <span>100D: {techContext.lowestRsi100d} - {techContext.highestRsi100d}</span>
                  <span>200D: {techContext.lowestRsi200d} - {techContext.highestRsi200d}</span>
                </div>
                <Text type="secondary" style={{ fontSize: 12 }}>Min vs Max range</Text>
              </div>
            </div>
          </div>
        )}

        {/* Lower row: Mon Low Range & Autocorrelation */}
        <Row gutter={24} style={{ marginBottom: 32 }}>
          <Col span={12}>
            <div style={{ background: '#fafafa', padding: 20, borderRadius: 8, height: '100%' }}>
              <Text strong style={{ fontSize: 14 }}>Avg {data.buyDay} low range (last {data.weeksAnalyzed} weeks)</Text>
              <div style={{ fontSize: 18, fontWeight: 700, margin: '8px 0 16px' }}>₹{data.buyDayLowMin} – ₹{data.buyDayLowMax}</div>
              <Progress percent={70} showInfo={false} strokeColor="#389e0d" trailColor="#d9d9d9" />
              <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 4 }}>
                <Text type="secondary" style={{ fontSize: 12 }}>₹{data.buyDayLowMin}</Text>
                <Text type="secondary" style={{ fontSize: 12 }}>₹{data.buyDayLowMax}</Text>
              </div>
            </div>
          </Col>
          <Col span={12}>
            <div style={{ background: '#fafafa', padding: 20, borderRadius: 8, height: '100%' }}>
              <Text strong style={{ fontSize: 14, display: 'block', marginBottom: 16 }}>Autocorrelation (cycle detection)</Text>
              <div style={{ display: 'flex', alignItems: 'center', marginBottom: 12 }}>
                <div style={{ width: 100, fontSize: 13, color: '#595959' }}>Weekly (5d)</div>
                <div style={{ flex: 1, paddingRight: 16 }}>
                  <Progress percent={Math.max(0, data.autocorrelation.lag5 * 100)} showInfo={false} strokeColor="#389e0d" />
                </div>
                <div style={{ width: 30, textAlign: 'right', fontWeight: 600, color: '#389e0d' }}>{data.autocorrelation.lag5}</div>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', marginBottom: 12 }}>
                <div style={{ width: 100, fontSize: 13, color: '#595959' }}>Biweekly (10d)</div>
                <div style={{ flex: 1, paddingRight: 16 }}>
                  <Progress percent={Math.max(0, data.autocorrelation.lag10 * 100)} showInfo={false} strokeColor="#faad14" />
                </div>
                <div style={{ width: 30, textAlign: 'right', fontWeight: 600, color: '#faad14' }}>{data.autocorrelation.lag10}</div>
              </div>
              <div style={{ display: 'flex', alignItems: 'center' }}>
                <div style={{ width: 100, fontSize: 13, color: '#595959' }}>Monthly (21d)</div>
                <div style={{ flex: 1, paddingRight: 16 }}>
                  <Progress percent={Math.max(0, data.autocorrelation.lag21 * 100)} showInfo={false} strokeColor="#8c8c8c" />
                </div>
                <div style={{ width: 30, textAlign: 'right', fontWeight: 600, color: '#8c8c8c' }}>{data.autocorrelation.lag21}</div>
              </div>
            </div>
          </Col>
        </Row>

        {/* Pattern Summary */}
        <div style={{ background: '#fafafa', padding: 20, borderRadius: 8, marginBottom: 32 }}>
          <Text strong style={{ display: 'block', fontSize: 15, marginBottom: 8 }}>Pattern summary</Text>
          <Paragraph style={{ margin: 0, fontSize: 14 }}>{data.patternSummary}</Paragraph>
        </div>

        {/* Heatmap Table */}
        <div>
          <div style={{ marginTop: 24 }}>
            <Text strong style={{ fontSize: 15 }}>Weekly pattern heatmap</Text>
            <Table 
              size="small" 
              pagination={false} 
              dataSource={data.weeklyHeatmap}
              columns={heatmapCols}
              style={{ marginTop: 8 }}
              rowKey="startDate"
            />
          </div>

          {techContext && (
            <div style={{ marginTop: 24, paddingTop: 24, borderTop: '1px solid #f0f0f0' }}>
              <Text strong style={{ fontSize: 15 }}>Recent Sessions (Raw Context)</Text>
              <Table 
                size="small" 
                pagination={false} 
                dataSource={techContext.recentSessions}
                columns={recentSessionColumns}
                style={{ marginTop: 8 }}
                rowKey="date"
              />
            </div>
          )}
        </div>

      </div>
    </div>
  );
}
