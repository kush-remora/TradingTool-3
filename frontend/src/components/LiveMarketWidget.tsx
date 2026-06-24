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
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '2px 8px', marginTop: 4, width: '100%' }}>
          <Text type="secondary" style={{ fontSize: 11 }}>
            O: <Text style={{ fontSize: 11 }}>{data.open != null ? data.open.toLocaleString("en-IN") : '-'}</Text>
          </Text>
          <Text type="secondary" style={{ fontSize: 11 }}>
            H: <Text style={{ fontSize: 11 }}>{data.high != null ? data.high.toLocaleString("en-IN") : '-'}</Text>
          </Text>
          <Text type="secondary" style={{ fontSize: 11 }}>
            L: <Text style={{ fontSize: 11 }}>{data.low != null ? data.low.toLocaleString("en-IN") : '-'}</Text>
          </Text>
          <Tooltip title={`Current: ${data.volume?.toLocaleString("en-IN") || '-'} | Avg: ${data.avgVol20d?.toLocaleString("en-IN") || '-'}`}>
            <Text type="secondary" style={{ fontSize: 11, display: 'flex', alignItems: 'center', gap: 4 }}>
              V: <Text style={{ fontSize: 11 }}>{data.volume != null ? formatVolume(data.volume) : '-'}</Text>
              {data.volumeHeat !== null && data.volumeHeat > 1.5 && (
                <span style={{ color: '#d46b08', fontSize: 10, fontWeight: 500 }}>
                  ({data.volumeHeat.toFixed(1)}x)
                </span>
              )}
            </Text>
          </Tooltip>
        </div>
      )}
    </div>
  );
}
