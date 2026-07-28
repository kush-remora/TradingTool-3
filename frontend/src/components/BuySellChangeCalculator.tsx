import { InputNumber, Space } from "antd";
import { useState } from "react";

function roundToTwoDecimals(value: number): number {
  return Math.round(value * 100) / 100;
}

export function BuySellChangeCalculator() {
  const [buyPrice, setBuyPrice] = useState<number | null>(null);
  const [sellPrice, setSellPrice] = useState<number | null>(null);
  const [changePercent, setChangePercent] = useState<number | null>(null);

  const updateBuyPrice = (nextBuyPrice: number | null): void => {
    setBuyPrice(nextBuyPrice);
    if (nextBuyPrice == null || nextBuyPrice <= 0) return;

    if (sellPrice != null && sellPrice > 0) {
      setChangePercent(roundToTwoDecimals(((sellPrice - nextBuyPrice) / nextBuyPrice) * 100));
      return;
    }
    if (changePercent != null && changePercent > -100) {
      setSellPrice(roundToTwoDecimals(nextBuyPrice * (1 + changePercent / 100)));
    }
  };

  const updateSellPrice = (nextSellPrice: number | null): void => {
    setSellPrice(nextSellPrice);
    if (nextSellPrice == null || nextSellPrice <= 0) return;

    if (buyPrice != null && buyPrice > 0) {
      setChangePercent(roundToTwoDecimals(((nextSellPrice - buyPrice) / buyPrice) * 100));
      return;
    }
    if (changePercent != null && changePercent > -100) {
      setBuyPrice(roundToTwoDecimals(nextSellPrice / (1 + changePercent / 100)));
    }
  };

  const updateChangePercent = (nextChangePercent: number | null): void => {
    setChangePercent(nextChangePercent);
    if (nextChangePercent == null || nextChangePercent <= -100) return;

    if (buyPrice != null && buyPrice > 0) {
      setSellPrice(roundToTwoDecimals(buyPrice * (1 + nextChangePercent / 100)));
      return;
    }
    if (sellPrice != null && sellPrice > 0) {
      setBuyPrice(roundToTwoDecimals(sellPrice / (1 + nextChangePercent / 100)));
    }
  };

  return (
    <Space wrap size={8} aria-label="Buy sell percentage calculator">
      <InputNumber aria-label="Buy price" size="small" min={0.01} precision={2} placeholder="Buy" prefix="₹" value={buyPrice} onChange={updateBuyPrice} style={{ width: 118 }} />
      <InputNumber aria-label="Sell price" size="small" min={0.01} precision={2} placeholder="Sell" prefix="₹" value={sellPrice} onChange={updateSellPrice} style={{ width: 118 }} />
      <InputNumber aria-label="Percentage change" size="small" precision={2} placeholder="Change %" value={changePercent} onChange={updateChangePercent} style={{ width: 118 }} />
    </Space>
  );
}
