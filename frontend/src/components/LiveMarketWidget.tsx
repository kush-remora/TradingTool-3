import { Typography, Space, Tooltip, Tag } from "antd";
import { useLiveMarketData } from "../hooks/useLiveMarketData";
import { ArrowUpOutlined, ArrowDownOutlined } from "@ant-design/icons";

const { Text } = Typography;

interface LiveMarketWidgetProps {
  symbol: string;
  showDetails?: boolean;
  showChange?: boolean;
  fallbackLtp?: number | null;
  fallbackChangePercent?: number | null;
}

export function LiveMarketWidget({
  symbol,
  showDetails = true,
  showChange = true,
  fallbackLtp = null,
  fallbackChangePercent = null,
}: LiveMarketWidgetProps) {
  const data = useLiveMarketData(symbol);

  if (!data && showDetails) {
    return <Text type="secondary" style={{ fontSize: 12 }}>Loading...</Text>;
  }

  const ltp = data?.ltp ?? fallbackLtp;
  const changePercent = data?.changePercent ?? fallbackChangePercent;

  if (ltp === null) {
    return <Text type="secondary" style={{ fontSize: 12 }}>Loading...</Text>;
  }

  const isPositive = (changePercent ?? 0) >= 0;
  const color = isPositive ? "#52c41a" : "#ff4d4f";
  const Icon = isPositive ? ArrowUpOutlined : ArrowDownOutlined;

  const formatVolume = (vol: number) => {
    if (vol >= 10000000) return `${(vol / 10000000).toFixed(2)} Cr`;
    if (vol >= 100000) return `${(vol / 100000).toFixed(2)} L`;
    if (vol >= 1000) return `${(vol / 1000).toFixed(1)} K`;
    return vol.toString();
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', minWidth: 120 }}>
      <Space size={4} align="baseline">
        <Text strong style={{ fontSize: 16 }}>₹{ltp.toLocaleString("en-IN", { minimumFractionDigits: 2 })}</Text>
        {showChange && changePercent !== null && (
          <Text style={{ color, fontSize: 12, fontWeight: 500 }}>
            <Icon style={{ fontSize: 10 }} /> {Math.abs(changePercent).toFixed(2)}%
          </Text>
        )}
      </Space>

      {showDetails && data && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 2, marginTop: 2 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', width: '100%' }}>
            <Text type="secondary" style={{ fontSize: 11 }}>
              L: <Text style={{ fontSize: 11 }}>{data.low.toFixed(1)}</Text>
            </Text>
            <Text type="secondary" style={{ fontSize: 11 }}>
              H: <Text style={{ fontSize: 11 }}>{data.high.toFixed(1)}</Text>
            </Text>
          </div>
          
          <div style={{ display: 'flex', justifyContent: 'space-between', width: '100%', alignItems: 'center' }}>
            <Tooltip title={`Current: ${data.volume.toLocaleString()} | Avg: ${data.avgVol20d?.toLocaleString() || '-'}`}>
              <Text type="secondary" style={{ fontSize: 11 }}>
                V: <Text style={{ fontSize: 11 }}>{formatVolume(data.volume)}</Text>
              </Text>
            </Tooltip>
            {data.volumeHeat !== null && (
              <Tooltip title="Volume relative to 20-day average">
                <Tag color={data.volumeHeat > 1.5 ? "orange" : "default"} style={{ margin: 0, padding: '0 4px', fontSize: 10, borderRadius: 4, lineHeight: '16px' }}>
                  {data.volumeHeat.toFixed(1)}x
                </Tag>
              </Tooltip>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
