import React from 'react';
import { Space, Typography, Tooltip, Button } from 'antd';
import { LineChartOutlined, GlobalOutlined } from '@ant-design/icons';

const { Text } = Typography;

interface StockBadgeProps {
  symbol: string;
  instrumentToken?: number;
  companyName?: string;
  fontSize?: number;
}

export function StockBadge({ symbol, instrumentToken, companyName, fontSize = 14 }: StockBadgeProps) {
  
  const kiteLink = instrumentToken && instrumentToken > 0 
    ? `https://kite.zerodha.com/markets/chart/web/tvc/NSE/${symbol}/${instrumentToken}` 
    : null;

  const growwSlug = companyName
    ? companyName.toLowerCase().replace(/[^a-z0-9]+/g, '-')
    : null;
    
  const cleanGrowwSlug = growwSlug?.replace(/-+$/, '');
  const growwLink = cleanGrowwSlug 
    ? `https://groww.in/stocks/${cleanGrowwSlug}` 
    : null;

  return (
    <Space size={4} align="center">
      <Text strong style={{ fontSize }}>{symbol}</Text>
      
      {kiteLink && (
        <Tooltip title="View chart on Kite">
          <Button 
            type="text" 
            size="small" 
            target="_blank"
            href={kiteLink}
            icon={<LineChartOutlined style={{ color: '#ff5722', fontSize: fontSize - 2 }} />} 
            onClick={(e) => e.stopPropagation()}
            style={{ minWidth: 20, padding: 0, height: 20 }}
          />
        </Tooltip>
      )}

      {growwLink && (
        <Tooltip title="View fundamentals on Groww">
          <Button 
            type="text" 
            size="small" 
            target="_blank"
            href={growwLink}
            icon={<GlobalOutlined style={{ color: '#00d09c', fontSize: fontSize - 2 }} />} 
            onClick={(e) => e.stopPropagation()}
            style={{ minWidth: 20, padding: 0, height: 20 }}
          />
        </Tooltip>
      )}
    </Space>
  );
}
