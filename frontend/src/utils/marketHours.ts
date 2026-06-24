const MARKET_TIME_FORMATTER = new Intl.DateTimeFormat("en-GB", {
  timeZone: "Asia/Kolkata",
  weekday: "short",
  hour: "2-digit",
  minute: "2-digit",
  hour12: false,
});

const OPEN_MINUTES = (9 * 60) + 14;
const CLOSE_MINUTES = (15 * 60) + 31;
const CLOSED_WEEKDAYS = new Set(["Sat", "Sun"]);

function getMarketParts(now: Date): { weekday: string; totalMinutes: number } {
  const parts = MARKET_TIME_FORMATTER.formatToParts(now);
  const weekday = parts.find((part) => part.type === "weekday")?.value ?? "";
  const hour = Number(parts.find((part) => part.type === "hour")?.value ?? "0");
  const minute = Number(parts.find((part) => part.type === "minute")?.value ?? "0");

  return {
    weekday,
    totalMinutes: (hour * 60) + minute,
  };
}

export function isIndianEquityMarketOpen(now: Date = new Date()): boolean {
  const marketParts = getMarketParts(now);
  if (CLOSED_WEEKDAYS.has(marketParts.weekday)) {
    return false;
  }

  return marketParts.totalMinutes > OPEN_MINUTES && marketParts.totalMinutes < CLOSE_MINUTES;
}
