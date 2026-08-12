import type { CompactDailyRow, CompactWeeklyRow } from "./compactStockReview";
import { formatPrice, formatQuantity, formatShortDate, formatSignedPercent, formatSignedPrice, formatWeekdayDate } from "./compactStockReview";
import { useEffect, useState } from "react";

interface CompactReviewTablesProps {
  weeks: CompactWeeklyRow[];
  recentDays: CompactDailyRow[];
  allDays: CompactDailyRow[];
}

const toneClass = (value: number | null): string => (
  value == null || value === 0 ? "" : value > 0 ? "compact-positive" : "compact-negative"
);

const formatRatio = (value: number | null): string => value == null ? "—" : `${value.toFixed(2)}×`;

const ratioClass = (value: number | null): string => {
  if (value == null) return "";
  if (value < 0.8) return "compact-participation-quiet";
  if (value > 1.2) return "compact-participation-active";
  return "compact-participation-average";
};

const weekLabel = (week: CompactWeeklyRow, index: number): string => week.isCurrent ? "WTD" : `W−${index}`;

export function CompactReviewTables({ weeks, recentDays, allDays }: CompactReviewTablesProps) {
  const [isTapeExpanded, setIsTapeExpanded] = useState(false);
  const visibleDays = isTapeExpanded ? [...allDays].reverse().slice(0, 30) : recentDays;
  const weeklyLowDates = new Set(weeks.map((week) => week.lowDate));
  const weeklyHighDates = new Set(weeks.map((week) => week.highDate));
  const topVolumeDays = [...allDays]
    .slice(-40)
    .sort((left, right) => right.volume - left.volume || right.date.localeCompare(left.date))
    .slice(0, 4);

  useEffect(() => {
    setIsTapeExpanded(false);
  }, [allDays.at(-1)?.date]);

  return (
    <div className="compact-review-lower-grid">
      <section className="compact-review-week-panel" aria-label="Four-week structure">
        <div className="compact-review-section-heading">
          <strong>Four-week structure</strong>
          <span>L = low day · H = high day · Week % = first open → last close · second line = low/high range</span>
        </div>
        <div className="compact-review-table-wrap">
          <table className="compact-week-table">
            <thead><tr><th>Week</th><th>Week %</th><th>Low</th><th>High</th><th>Volume · raw / 10D</th><th>Delivery %</th><th>Day %</th></tr></thead>
            <tbody>
              {weeks.map((week, index) => (
                <tr key={week.weekStart} className={week.isCurrent ? "compact-current-row" : ""}>
                  <td>
                    <strong>{weekLabel(week, index)}</strong>
                    <small>{formatShortDate(week.weekStart)}–{formatShortDate(week.endDate)}</small>
                  </td>
                  <td className={toneClass(week.weeklyMovePct)}>
                    <strong>{formatSignedPercent(week.weeklyMovePct, 1)}</strong>
                    <small className={week.lowHighDirection === "LOW_FIRST" ? "compact-positive" : week.lowHighDirection === "HIGH_FIRST" ? "compact-negative" : "compact-muted"}>
                      {week.lowHighDirection === "LOW_FIRST" ? "L→H" : week.lowHighDirection === "HIGH_FIRST" ? "H→L" : "L/H same day"} {formatSignedPercent(week.lowHighPct, 1)}
                    </small>
                  </td>
                  <td>
                    <strong>{formatPrice(week.low)}</strong>
                    <small>{formatWeekdayDate(week.lowDate)}</small>
                  </td>
                  <td>
                    <strong>{formatPrice(week.high)}</strong>
                    <small>{formatWeekdayDate(week.highDate)}</small>
                  </td>
                  <td>
                    <span className="compact-week-pair" title="Raw shares followed by the ratio to the prior 10-session average. Below 0.8× is quiet, 0.8–1.2× is near average, and above 1.2× is active.">
                      <span className={`compact-week-pair-line ${ratioClass(week.lowVolumeRatio)}`}>L {formatQuantity(week.lowVolume)} · {formatRatio(week.lowVolumeRatio)}</span>
                      <span className={`compact-week-pair-line ${ratioClass(week.highVolumeRatio)}`}>H {formatQuantity(week.highVolume)} · {formatRatio(week.highVolumeRatio)}</span>
                    </span>
                  </td>
                  <td>
                    <span className="compact-week-pair" title="Delivery percentage on the low and high days">
                      <span className="compact-week-pair-line">L {week.lowDeliveryPct == null ? "—" : `${week.lowDeliveryPct.toFixed(1)}%`}</span>
                      <span className="compact-week-pair-line">H {week.highDeliveryPct == null ? "—" : `${week.highDeliveryPct.toFixed(1)}%`}</span>
                    </span>
                  </td>
                  <td>
                    <span className="compact-week-pair"><span className={toneClass(week.lowDayPct)}>L {formatSignedPercent(week.lowDayPct, 1)}</span><span className={toneClass(week.highDayPct)}>H {formatSignedPercent(week.highDayPct, 1)}</span></span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {topVolumeDays.length > 0 && (
          <section className="compact-volume-days" aria-label="Top volume days">
            <div className="compact-review-section-heading">
              <strong>Top volume days · 40 sessions</strong>
              <span>Largest raw-volume sessions</span>
            </div>
            <div className="compact-review-table-wrap">
              <table className="compact-volume-table">
                <thead><tr><th>Date / day</th><th>Volume</th><th>O / H / L / C</th><th>Effort → result</th><th>Day %</th><th>Delivery</th></tr></thead>
                <tbody>
                  {topVolumeDays.map((day) => (
                    <tr key={day.date} className="compact-volume-row">
                      <td>{formatWeekdayDate(day.date)}</td>
                      <td className="compact-signal-cell"><strong>{formatQuantity(day.volume)}</strong><small>{day.volumeVsPrior10dPct == null ? "—" : `${(day.volumeVsPrior10dPct / 100).toFixed(2)}× vs 10D`}</small></td>
                      <td>
                        <span className="compact-volume-candle">
                          <small>O {formatPrice(day.open)} · H {formatPrice(day.high)}</small>
                          <small>L {formatPrice(day.low)} · C {formatPrice(day.close)}</small>
                        </span>
                      </td>
                      <td className="compact-effort-result">
                        <span className="compact-tape-pair">
                          <strong>{day.volumeVsPrior10dPct == null ? "—" : `${(day.volumeVsPrior10dPct / 100).toFixed(2)}×`}</strong>
                          <small>→ {formatSignedPercent(day.daily_change_pct, 1)}</small>
                        </span>
                      </td>
                      <td className={toneClass(day.daily_change_pct)}>{formatSignedPercent(day.daily_change_pct, 1)}</td>
                      <td>{day.deliveryPct == null ? "—" : `${day.deliveryPct.toFixed(1)}%`}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        )}
      </section>

      <section className="compact-review-tape-panel" aria-label="Recent market tape">
        <div className="compact-review-section-heading"><strong>{isTapeExpanded ? "Daily candle data · 30 sessions" : "Recent tape"}</strong><span>{isTapeExpanded ? "Latest first" : "Today versus previous sessions"}</span></div>
        <div className="compact-review-table-wrap">
          <table className="compact-tape-table">
            <thead>
              <tr><th>Date / day</th><th>Open</th><th>High</th><th>Low</th><th>Close</th><th>Open → Low</th><th>Day % / L→H %</th><th>Volume / vs 10D</th><th>Delivery</th></tr>
            </thead>
            <tbody>
              {visibleDays.map((day, index) => (
                <tr
                  key={day.date}
                  className={[
                    index === 0 ? "compact-current-row" : "",
                    weeklyLowDates.has(day.date) ? "compact-week-low-row" : "",
                    weeklyHighDates.has(day.date) ? "compact-week-high-row" : "",
                  ].filter(Boolean).join(" ")}
                >
                  <td>
                    <span>{formatWeekdayDate(day.date)}</span>
                    {weeklyLowDates.has(day.date) && <small className="compact-week-marker compact-week-marker-low">W low</small>}
                    {weeklyHighDates.has(day.date) && <small className="compact-week-marker compact-week-marker-high">W high</small>}
                  </td>
                  <td>{formatPrice(day.open)}</td>
                  <td>{formatPrice(day.high)}</td>
                  <td>{formatPrice(day.low)}</td>
                  <td>{formatPrice(day.close)}</td>
                  <td className={`compact-open-low-cell ${toneClass(day.openToLowPct)}`} title="Maximum downside from the session open to the session low">
                    <span className="compact-tape-pair">
                      <strong>{formatSignedPrice(day.low - day.open)}</strong>
                      <small>{formatSignedPercent(day.openToLowPct, 1)}</small>
                    </span>
                  </td>
                  <td>
                    <span className="compact-tape-pair">
                      <strong className={toneClass(day.daily_change_pct)}>{formatSignedPercent(day.daily_change_pct, 1)}</strong>
                      <small>L→H {formatSignedPercent(day.spreadPct, 1).replace("+", "")}</small>
                    </span>
                  </td>
                  <td className={(day.volumeVsPrior10dPct ?? 0) >= 130 ? "compact-signal-cell" : ""}>
                    <span className="compact-tape-pair"><strong>{formatQuantity(day.volume)}</strong><small>{day.volumeVsPrior10dPct == null ? "vs 10D —" : `vs 10D ${day.volumeVsPrior10dPct.toFixed(0)}%`}</small></span>
                  </td>
                  <td>{day.deliveryPct == null ? "—" : `${day.deliveryPct.toFixed(1)}%`}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <button
          type="button"
          className="compact-tape-toggle"
          aria-expanded={isTapeExpanded}
          onClick={() => setIsTapeExpanded((expanded) => !expanded)}
        >
          {isTapeExpanded ? "Collapse to 10 days" : "Show last 30 days"}
        </button>
      </section>
    </div>
  );
}
