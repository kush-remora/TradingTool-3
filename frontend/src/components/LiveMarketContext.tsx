import React from 'react';
import { useLiveMarketData } from '../hooks/useLiveMarketData';
import { Card, Statistic, Row, Col, Typography, Tag, Space, Skeleton } from 'antd';
import { ArrowUpOutlined, ArrowDownOutlined, FireOutlined } from '@ant-design/icons';

const { Text } = Typography;

interface Props {
  /** Symbol in EXCHANGE:TRADINGSYMBOL format, e.g. "NSE:INFY" */
  symbol: string;
}

/**
 * LiveMarketContext Component
 * 
 * Displays real-time LTP, Day's High/Low, and Volume Heat (Relative Volume).
 * Handles its own data subscription via useLiveMarketData hook.
 */
export const LiveMarketContext: React.FC<Props> = ({ symbol }) => {
  const data = useLiveMarketData(symbol);

  if (!data) {
    return (
      <Card size="small" style={{ width: 280, borderRadius: 8 }}>
        <Skeleton active paragraph={{ rows: 2 }} />
      </Card>
    );
  }

  const isPositive = data.changePercent >= 0;
  const priceColor = isPositive ? '#3f8600' : '#cf1322';
  
  // Volume Heat logic: > 1.5x is significant, > 3x is extreme
  const volHeat = data.volumeHeat || 0;
  const isVolSpiking = volHeat > 1.5;

  return (
    <Card 
      size="small" 
      title={<Text strong style={{ fontSize: 13 }}>{symbol}</Text>} 
      style={{ width: 280, borderRadius: 8, boxShadow: '0 2px 8px rgba(0,0,0,0.05)' }}
    >
      <Statistic
        title="Last Traded Price"
        value={data.ltp}
        precision={2}
        valueStyle={{ color: priceColor, fontSize: 20, fontWeight: 700 }}
        prefix={isPositive ? <ArrowUpOutlined /> : <ArrowDownOutlined />}
        suffix={<span style={{ fontSize: 12, marginLeft: 4 }}>({isPositive ? '+' : ''}{data.changePercent.toFixed(2)}%)</span>}
      />

      <div style={{ marginTop: 12 }}>
        <Row gutter={16}>
          <Col span={12}>
            <Text type="secondary" style={{ fontSize: 11, display: 'block' }}>Today's High</Text>
            <Text strong style={{ fontSize: 13 }}>{data.high.toLocaleString()}</Text>
          </Col>
          <Col span={12}>
            <Text type="secondary" style={{ fontSize: 11, display: 'block' }}>Today's Low</Text>
            <Text strong style={{ fontSize: 13 }}>{data.low.toLocaleString()}</Text>
          </Col>
        </Row>
      </div>

      <div style={{ marginTop: 16, borderTop: '1px solid #f0f0f0', paddingTop: 10 }}>
        <Space direction="vertical" size={2} style={{ width: '100%' }}>
          <Text type="secondary" style={{ fontSize: 11 }}>Volume Heat</Text>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <Space align="baseline">
              <Text strong style={{ fontSize: 16 }}>{volHeat.toFixed(1)}x</Text>
              <Text type="secondary" style={{ fontSize: 11 }}>Avg Vol</Text>
            </Space>
            {isVolSpiking && (
              <Tag color={volHeat > 3 ? "red" : "orange"} icon={<FireOutlined />} style={{ marginRight: 0 }}>
                {volHeat > 3 ? "EXTREME" : "SPIKING"}
              </Tag>
            )}
          </div>
        </Space>
      </div>
    </Card>
  );
};
