import {
  ArrowRightOutlined,
  CheckCircleFilled,
  ClockCircleFilled,
  CloseCircleFilled,
  InfoCircleFilled,
  SearchOutlined,
} from "@ant-design/icons";
import { Alert, Button, Card, DatePicker, Input, InputNumber, Space, Spin, Table, Tag, Typography } from "antd";
import type { TableColumnsType } from "antd";
import dayjs, { type Dayjs } from "dayjs";
import { useEffect, useMemo, useState, type ReactNode } from "react";
import { BuySellChangeCalculator } from "../components/BuySellChangeCalculator";
import { useStockDetail } from "../hooks/useStockDetail";
import type {
  BreakoutDayQualityResponse,
  BreakoutQualityDecision,
  BreakoutQualityRuleResult,
  BreakoutQualityVerdict,
} from "../types";
import { getJson } from "../utils/api";
import { evaluateNextMorningOpen } from "../utils/breakoutExecution";
import "./breakoutBuyReview.css";

const { Text, Title } = Typography;

const RULE_GUIDE = [
  { key: "close", check: "Close near the high", pass: "≥80%", wait: "60–79%", reject: "<60%" },
  { key: "volume", check: "Volume vs prior 10D", pass: "≥1.5×", wait: "1.0–1.49×", reject: "<1.0×" },
  { key: "delivery", check: "Delivered qty vs prior 20D", pass: "≥1.25×", wait: "1.0–1.24×", reject: "<1.0×" },
  { key: "line", check: "Close above breakout line", pass: "≥0.10 ATR", wait: "0–0.09 ATR", reject: "At/below" },
  { key: "extension", check: "Not too extended", pass: "0–0.50 ATR", wait: "0.51–1.0 ATR", reject: ">1.0 ATR" },
];

function formatPrice(value: number | null): string {
  return value == null ? "—" : `₹${value.toLocaleString("en-IN", { maximumFractionDigits: 2 })}`;
}

function formatNumber(value: number | null): string {
  return value == null ? "—" : value.toLocaleString("en-IN", { maximumFractionDigits: 0 });
}

function label(value: string): string {
  return value.toLowerCase().replaceAll("_", " ").replace(/\b\w/g, (character) => character.toUpperCase());
}

function verdictIcon(verdict: BreakoutQualityVerdict | BreakoutQualityDecision): ReactNode {
  if (verdict === "PASS") return <CheckCircleFilled />;
  if (verdict === "REJECT") return <CloseCircleFilled />;
  if (verdict === "WAIT") return <ClockCircleFilled />;
  return <InfoCircleFilled />;
}

function VerdictTag({ verdict }: { verdict: BreakoutQualityVerdict | BreakoutQualityDecision }): ReactNode {
  return <Tag className={`breakout-buy-verdict breakout-buy-verdict-${verdict.toLowerCase()}`}>{verdictIcon(verdict)} {label(verdict)}</Tag>;
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
  const [nextOpen, setNextOpen] = useState<number | null>(null);
  const { data: stockDetail } = useStockDetail(report?.symbol ?? null, 75);
  const execution = useMemo(() => report ? evaluateNextMorningOpen(report, nextOpen) : null, [report, nextOpen]);
  const deliveryFallback = useMemo(
    () => stockDetail?.delivery_days.find((day) => day.date === report?.date),
    [report?.date, stockDetail?.delivery_days],
  );
  const displayedDeliveryPercentage = report?.deliveryPercentage ?? deliveryFallback?.delivery_percentage ?? null;
  const displayedDeliveredQuantity = report?.deliveredQuantity ?? deliveryFallback?.delivered_quantity ?? null;

  const runReview = (requestedSymbol: string = symbol, requestedDate: Dayjs | null = date): void => {
    const normalizedSymbol = requestedSymbol.trim().toUpperCase();
    if (!normalizedSymbol || !requestedDate) {
      setError("Enter a stock symbol and completed trading date.");
      return;
    }
    const formattedDate = requestedDate.format("YYYY-MM-DD");
    setLoading(true);
    setNextOpen(null);
    setError(null);
    const params = new URLSearchParams({ symbol: normalizedSymbol, date: formattedDate });
    window.history.replaceState({}, "", `${window.location.pathname}?${params}`);
    void getJson<BreakoutDayQualityResponse>(`/api/strategy/adaptive-breakout/buy-review?${params}`, { useCache: false })
      .then(setReport)
      .catch((requestError: unknown) => {
        setReport(null);
        setError(requestError instanceof Error ? requestError.message : "Unable to review this session.");
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    if (defaults.symbol && defaults.date) runReview(defaults.symbol, defaults.date);
    // Query defaults are intentionally read once when this standalone console opens.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const resultColumns: TableColumnsType<BreakoutQualityRuleResult> = [
    { title: "Check", dataIndex: "label", key: "label", width: 190, render: (value: string) => <strong>{value}</strong> },
    { title: "Rule", dataIndex: "rule", key: "rule", width: 285 },
    { title: "This day", dataIndex: "actual", key: "actual", width: 190, render: (value: string) => <strong>{value}</strong> },
    { title: "Result", dataIndex: "verdict", key: "verdict", width: 115, render: (value: BreakoutQualityVerdict) => <VerdictTag verdict={value} /> },
    { title: "Meaning", dataIndex: "explanation", key: "explanation" },
  ];

  return (
    <main className="breakout-buy-page">
      <header className="breakout-buy-header">
        <div>
          <Text className="breakout-buy-eyebrow">Historical · causal replay</Text>
          <Title level={3}>Breakout Buy Review</Title>
          <Text type="secondary">Pick any completed session. Later candles are never used.</Text>
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

      <Card className="breakout-buy-rule-card" size="small">
        <div className="breakout-buy-section-title"><strong>1A · Breakout-day rules</strong><Text type="secondary">Read this first; baselines exclude the selected day.</Text></div>
        <table className="breakout-buy-rule-grid" aria-label="Breakout day quality rules">
          <thead><tr><th>Check</th><th className="pass">Pass</th><th className="wait">Wait</th><th className="reject">Reject</th></tr></thead>
          <tbody>{RULE_GUIDE.map((rule) => <tr key={rule.key}>
            <th scope="row">{rule.check}</th><td className="pass">{rule.pass}</td><td className="wait">{rule.wait}</td><td className="reject">{rule.reject}</td>
          </tr>)}</tbody>
        </table>
      </Card>

      {error && <Alert closable message={error} onClose={() => setError(null)} showIcon type="error" />}
      {loading && <div className="breakout-buy-loading"><Spin /><Text type="secondary">Replaying structure through the selected date…</Text></div>}

      {report && !loading && <>
        <Card className={`breakout-buy-decision-card decision-${report.overallDecision.toLowerCase()}`} size="small">
          <div className="breakout-buy-decision">
            <div>
              <Text className="breakout-buy-eyebrow">1B · {report.symbol} · {dayjs(report.date).format("DD MMM YYYY")}</Text>
              <div className="breakout-buy-decision-line"><VerdictTag verdict={report.overallDecision} /><Title level={4}>{report.decisionSummary}</Title></div>
              <Text>{label(report.structureDecision)} <ArrowRightOutlined /> {report.structureExplanation}</Text>
            </div>
            <div className="breakout-buy-candle">
              <span>O <b>{formatPrice(report.open)}</b></span><span>H <b>{formatPrice(report.high)}</b></span>
              <span>L <b>{formatPrice(report.low)}</b></span><span>C <b>{formatPrice(report.close)}</b></span>
            </div>
          </div>
          <div className="breakout-buy-metrics">
            <span>Floor <b>{formatPrice(report.floor)}</b></span><span>Peak <b>{formatPrice(report.peak)}</b></span>
            <span>Breakout line <b>{formatPrice(report.breakoutLine)}</b></span><span>ATR <b>{formatPrice(report.atr)}</b></span>
            <span>SMA 50 <b>{formatPrice(report.sma50)}</b></span><span>SMA 200 <b>{formatPrice(report.sma200)}</b></span>
            <span>Volume <b>{formatNumber(report.volume)}</b></span>
            <span>Delivery % <b>{displayedDeliveryPercentage == null ? "—" : `${displayedDeliveryPercentage.toFixed(1)}%`}</b></span>
            <span>Delivered qty <b>{formatNumber(displayedDeliveredQuantity)}</b></span>
          </div>
        </Card>

        <Card className="breakout-buy-results-card" size="small">
          <div className="breakout-buy-section-title"><strong>1C · What this day actually did</strong><Text type="secondary">A missing input stays unavailable; it is never guessed.</Text></div>
          <Table<BreakoutQualityRuleResult>
            columns={resultColumns}
            dataSource={report.rules}
            pagination={false}
            rowClassName={(rule) => `breakout-buy-result-${rule.verdict.toLowerCase()}`}
            rowKey="key"
            scroll={{ x: 980 }}
            size="small"
          />
        </Card>

        <Card className={`breakout-buy-results-card breakout-chart-card decision-${report.chartContext.overallDecision.toLowerCase()}`} size="small">
          <div className="breakout-buy-context-heading">
            <div>
              <div className="breakout-buy-section-title"><strong>2 · Chart context</strong><Text type="secondary">Trend is context, not the breakout trigger.</Text></div>
              <div className="breakout-buy-decision-line">
                <VerdictTag verdict={report.chartContext.overallDecision} />
                <Text strong>{report.chartContext.decisionSummary}</Text>
              </div>
            </div>
            <div className="breakout-buy-context-summary">
              <span>Next obstacle<b>{report.chartContext.nextObstacleLabel ?? "Clear runway"}</b></span>
              <span>Level<b>{formatPrice(report.chartContext.nextObstaclePrice)}</b></span>
              <span>Room<b>{report.chartContext.roomToObstacleAtr == null ? "Clear" : `${report.chartContext.roomToObstacleAtr.toFixed(2)} ATR · ${report.chartContext.roomToObstaclePct?.toFixed(1)}%`}</b></span>
            </div>
          </div>
          <Table<BreakoutQualityRuleResult>
            columns={resultColumns}
            dataSource={report.chartContext.rules}
            pagination={false}
            rowClassName={(rule) => `breakout-buy-result-${rule.verdict.toLowerCase()}`}
            rowKey="key"
            scroll={{ x: 980 }}
            size="small"
          />
        </Card>

        <Card className="breakout-buy-execution-card" size="small">
          <div className="breakout-buy-execution-heading">
            <div>
              <div className="breakout-buy-section-title"><strong>3 · Next-morning execution</strong><Text type="secondary">Enter the next session's actual or expected open.</Text></div>
              <div className="breakout-buy-entry-zones">
                <span className="pass">Consider ≤0.25 ATR <b>{formatPrice(report.breakoutLine == null ? null : report.breakoutLine + 0.25 * report.atr)}</b></span>
                <span className="wait">Wait 0.25–0.50 ATR</span>
                <span className="reject">Do not chase &gt;0.50 ATR <b>{formatPrice(report.breakoutLine == null ? null : report.breakoutLine + 0.5 * report.atr)}</b></span>
              </div>
            </div>
            <InputNumber
              aria-label="Next session open price"
              className="breakout-buy-open-input"
              min={0.01}
              onChange={setNextOpen}
              placeholder="Next open"
              precision={2}
              prefix="₹"
              value={nextOpen}
            />
          </div>

          {execution ? <div className={`breakout-buy-execution-result execution-${execution.decision.toLowerCase()}`}>
            <VerdictTag verdict={execution.decision} />
            <strong>{execution.summary}</strong>
            <span>Open vs line <b>{execution.openingExtensionAtr == null ? "—" : `${execution.openingExtensionAtr.toFixed(2)} ATR`}</b></span>
            <span>Room left <b>{execution.roomToObstacleAtr == null ? "Clear" : `${execution.roomToObstacleAtr.toFixed(2)} ATR`}</b></span>
          </div> : <Text className="breakout-buy-open-prompt" type="secondary">Enter the open price to see Pass, Wait, or Reject. This does not place an order.</Text>}

          <div className="breakout-buy-inline-calculator">
            <Text strong>Quick price calculator</Text>
            <BuySellChangeCalculator />
            <Text type="secondary">Enter any two: buy, sell, or change %.</Text>
          </div>
        </Card>
      </>}
    </main>
  );
}
