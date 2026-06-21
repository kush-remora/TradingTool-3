export interface PnLResult {
  pnl: number;
  pnlPct: number;
  isProfit: boolean;
}

export function calculatePnL(avgPriceStr: string | null | undefined, currentPrice: number | null | undefined, quantity: number): PnLResult | null {
  if (!avgPriceStr || !currentPrice) return null;
  
  const avgPrice = parseFloat(avgPriceStr);
  if (Number.isNaN(avgPrice) || avgPrice <= 0) return null;

  const pnl = (currentPrice - avgPrice) * quantity;
  const pnlPct = ((currentPrice - avgPrice) / avgPrice) * 100;
  
  return {
    pnl,
    pnlPct,
    isProfit: pnl >= 0,
  };
}
