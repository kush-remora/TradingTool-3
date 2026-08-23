import { SearchOutlined } from "@ant-design/icons";
import { Alert, Button, Card, DatePicker, Input, Space, Spin, Typography } from "antd";
import dayjs, { type Dayjs } from "dayjs";
import { useEffect, useState, type ReactNode } from "react";
import type { BreakoutDayQualityResponse } from "../types";
import { getJson } from "../utils/api";
import "./breakoutBuyReview.css";

const { Text, Title } = Typography;

function formatPrice(value: number | null): string {
  return value == null ? "—" : `₹${value.toLocaleString("en-IN", { maximumFractionDigits: 2 })}`;
}

function formatNumber(value: number | null): string {
  return value == null ? "—" : value.toLocaleString("en-IN", { maximumFractionDigits: 0 });
}

function formatMovement(from: number, to: number): string {
  if (from <= 0) return "—";
  const movementPct = (to / from - 1) * 100;
  return `${movementPct >= 0 ? "+" : ""}${movementPct.toFixed(2)}%`;
}

function formatSignedPercent(value: number | null): string {
  return value == null ? "—" : `${value >= 0 ? "+" : ""}${value.toFixed(1)}%`;
}

function formatDate(value: string): string {
  return dayjs(value).format("DD MMM YYYY");
}

function closePositionPct(low: number, high: number, close: number): number | null {
  const range = high - low;
  return range > 0 ? ((close - low) / range) * 100 : null;
}

function closePositionBand(value: number): "HIGH" | "MID" | "LOW" {
  if (value >= 70) return "HIGH";
  if (value <= 30) return "LOW";
  return "MID";
}

function ClosePositionCell({ low, high, close }: { low: number; high: number; close: number }): ReactNode {
  const value = closePositionPct(low, high, close);
  if (value == null) return <span>—</span>;
  const boundedValue = Math.max(0, Math.min(100, value));
  const band = closePositionBand(value);
  return (
    <div
      aria-label={`Close ${value.toFixed(1)}% between low and high`}
      className={`breakout-buy-close-position breakout-buy-close-position-${band.toLowerCase()}`}
    >
      <div className="breakout-buy-close-position-heading">
        <span className="breakout-buy-close-position-band">{band}</span>
        <span className="breakout-buy-close-position-value">{value.toFixed(1)}%</span>
      </div>
      <div className="breakout-buy-close-position-scale">
        <span>L</span>
        <span className="breakout-buy-close-position-track">
          <span className="breakout-buy-close-position-marker" style={{ left: `${boundedValue}%` }} />
        </span>
        <span>H</span>
      </div>
    </div>
  );
}

function queryDefaults(): { symbol: string; date: Dayjs | null } {
  const params = new URLSearchParams(window.location.search);
  const rawDate = params.get("date");
  return {
    symbol: params.get("symbol")?.trim().toUpperCase() ?? "",
    date: rawDate && dayjs(rawDate, "YYYY-MM-DD", true).isValid() ? dayjs(rawDate) : null,
  };
}

export function BreakoutBuyReviewPage(): ReactNode {
  const defaults = queryDefaults();
  const [symbol, setSymbol] = useState(defaults.symbol);
  const [date, setDate] = useState<Dayjs | null>(defaults.date);
  const [report, setReport] = useState<BreakoutDayQualityResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const runReview = (requestedSymbol: string = symbol, requestedDate: Dayjs | null = date): void => {
    const normalizedSymbol = requestedSymbol.trim().toUpperCase();
    if (!normalizedSymbol || !requestedDate) {
      setError("Enter a stock symbol and completed trading date.");
      return;
    }
    const formattedDate = requestedDate.format("YYYY-MM-DD");
    setLoading(true);
    setError(null);
    const params = new URLSearchParams({ symbol: normalizedSymbol, date: formattedDate });
    window.history.replaceState({}, "", `${window.location.pathname}?${params}`);
    void getJson<BreakoutDayQualityResponse>(`/api/strategy/adaptive-breakout/buy-review?${params}`, { useCache: false })
      .then(setReport)
      .catch((requestError: unknown) => {
        setReport(null);
        setError(requestError instanceof Error ? requestError.message : "Unable to load breakout-day data.");
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    if (defaults.symbol && defaults.date) runReview(defaults.symbol, defaults.date);
    // Query defaults are intentionally read once when this standalone console opens.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <main className="breakout-buy-page">
      <header className="breakout-buy-header">
        <div>
          <Text className="breakout-buy-eyebrow">Historical · raw session data</Text>
          <Title level={3}>Breakout Buy Review</Title>
          <Text type="secondary">Select a completed session to inspect its breakout-day candle.</Text>
        </div>
        <Space.Compact className="breakout-buy-search">
          <Input
            aria-label="Stock symbol"
            onChange={(event) => setSymbol(event.target.value.toUpperCase())}
            onPressEnter={() => runReview()}
            placeholder="Symbol, e.g. KRN"
            value={symbol}
          />
          <DatePicker
            aria-label="Review date"
            disabledDate={(candidate) => candidate.isAfter(dayjs(), "day")}
            format="DD MMM YYYY"
            onChange={setDate}
            value={date}
          />
          <Button icon={<SearchOutlined />} loading={loading} onClick={() => runReview()} type="primary">Review</Button>
        </Space.Compact>
      </header>

      {error && <Alert closable message={error} onClose={() => setError(null)} showIcon type="error" />}
      {loading && <div className="breakout-buy-loading"><Spin /><Text type="secondary">Loading breakout-day data…</Text></div>}

      {report && !loading && (
        <Card
          className="breakout-buy-raw-card"
          size="small"
          title={
            <div className="breakout-buy-raw-title">
              <strong>Breakout-day raw data</strong>
              <Text type="secondary">{report.symbol} · {dayjs(report.date).format("DD MMM YYYY")}</Text>
            </div>
          }
        >
          <div className="breakout-buy-raw-grid">
            <div className="breakout-buy-raw-metric"><span>Open</span><strong>{formatPrice(report.open)}</strong></div>
            <div className="breakout-buy-raw-metric"><span>High</span><strong>{formatPrice(report.high)}</strong></div>
            <div className="breakout-buy-raw-metric"><span>Low</span><strong>{formatPrice(report.low)}</strong></div>
            <div className="breakout-buy-raw-metric"><span>Close</span><strong>{formatPrice(report.close)}</strong></div>
            <div className="breakout-buy-raw-metric"><span>Volume</span><strong>{formatNumber(report.volume)}</strong></div>
            <div className="breakout-buy-raw-metric">
              <span>Volume vs prior 10D avg</span>
              <strong>{report.volumeVsTenDayAveragePct == null ? "—" : `${report.volumeVsTenDayAveragePct.toFixed(1)}%`}</strong>
            </div>
            <div className="breakout-buy-raw-metric">
              <span>Volume vs 20D quiet avg</span>
              <strong>{report.volumeVsTwentyDayQuietAveragePct == null ? "—" : `${report.volumeVsTwentyDayQuietAveragePct.toFixed(1)}%`}</strong>
            </div>
            <div className="breakout-buy-raw-metric"><span>Vs prior 20D high</span><strong>{formatSignedPercent(report.volumeVsPriorTwentyDayHighChangePct)}</strong></div>
            <div className="breakout-buy-raw-metric"><span>Low → High</span><strong>{formatMovement(report.low, report.high)}</strong></div>
            <div className="breakout-buy-raw-metric"><span>Open → Close</span><strong>{formatMovement(report.open, report.close)}</strong></div>
          </div>
          <Text className="breakout-buy-raw-note" type="secondary">The 10D average and 20D quiet/high comparisons exclude the selected day. Quiet average = {report.twentyDayQuietAverageVolume == null ? "—" : formatNumber(report.twentyDayQuietAverageVolume)} shares, calculated from the five lowest volumes in the prior 20 sessions.</Text>
        </Card>
      )}

      {report && !loading && (
        <Card
          className="breakout-buy-volume-card"
          size="small"
          title={
            <div className="breakout-buy-raw-title">
              <strong>Recent abnormal-volume days</strong>
              <Text type="secondary">Prior 20 sessions · ≥5× quiet baseline</Text>
            </div>
          }
        >
          {report.recentAbnormalVolumeDays.length === 0 ? (
            <Text type="secondary">No session reached 5× the quiet-volume baseline.</Text>
          ) : (
            <div className="breakout-buy-volume-table-wrap">
              <table className="breakout-buy-volume-table">
                <thead>
                  <tr>
                    <th>Date</th><th>Open</th><th>High</th><th>Low</th><th>Close</th>
                    <th>Volume</th><th>Vs quiet avg</th><th>Close position</th><th>L → H</th><th>O → C</th>
                  </tr>
                </thead>
                <tbody>
                  {report.recentAbnormalVolumeDays.map((day) => (
                    <tr key={day.date}>
                      <td>{formatDate(day.date)}</td>
                      <td>{formatPrice(day.open)}</td>
                      <td>{formatPrice(day.high)}</td>
                      <td>{formatPrice(day.low)}</td>
                      <td>{formatPrice(day.close)}</td>
                      <td>{formatNumber(day.volume)}</td>
                      <td>{day.volumeVsQuietAveragePct.toFixed(1)}%</td>
                      <td><ClosePositionCell close={day.close} high={day.high} low={day.low} /></td>
                      <td>{formatSignedPercent(day.lowToHighPct)}</td>
                      <td>{formatSignedPercent(day.openToClosePct)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      )}
    </main>
  );
}
