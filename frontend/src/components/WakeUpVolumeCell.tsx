import { Space, Tag, Tooltip, Typography } from "antd";
import { useLiveMarketData } from "../hooks/useLiveMarketData";
import type { StockQuoteSnapshot } from "../types";

type WakeUpSignal = {
  score: number;
  label: string;
  color: string;
  priceChangePct: number | null;
  volumeRatio: number | null;
};

type WakeUpVolumeContext = {
  currentVolume: number | null;
  currentVolumeLabel: string;
  comparisonVolume: number | null;
  comparisonVolumeLabel: string;
  volumeDiffPct: number | null;
};

type WakeUpVolumeCellProps = {
  symbol: string;
  snapshot?: StockQuoteSnapshot | null;
  fallbackChangePercent?: number | null;
  fallbackCurrentVolume?: number | null;
  fallbackPreviousVolume?: number | null;
};

const WAKE_UP_PRICE_MOVE_THRESHOLD_PCT = 4;
const WAKE_UP_VOLUME_RATIO_THRESHOLD = 2;

function formatNumber(value: number | null | undefined, fractionDigits: number = 0): string {
  if (value == null) {
    return "-";
  }

  return value.toLocaleString("en-IN", {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  });
}

function formatPercent(value: number | null | undefined): string {
  if (value == null) {
    return "-";
  }

  return `${value >= 0 ? "+" : ""}${value.toFixed(0)}%`;
}

function formatRatio(value: number | null | undefined): string {
  if (value == null) {
    return "-";
  }

  return `${value.toFixed(2)}x`;
}

export function buildWakeUpSignal(
  priceChangePct: number | null,
  currentVolume: number | null | undefined,
  comparisonVolume: number | null | undefined,
): WakeUpSignal {
  const hasPriceAnomaly =
    priceChangePct != null && Math.abs(priceChangePct) >= WAKE_UP_PRICE_MOVE_THRESHOLD_PCT;
  const volumeRatio =
    currentVolume != null && comparisonVolume != null && comparisonVolume > 0 ? currentVolume / comparisonVolume : null;
  const hasVolumeAnomaly =
    volumeRatio != null && volumeRatio >= WAKE_UP_VOLUME_RATIO_THRESHOLD;
  const volumeRatioLabel = volumeRatio != null ? `${volumeRatio.toFixed(1)}x` : null;

  if (hasPriceAnomaly && hasVolumeAnomaly) {
    return {
      score: 3,
      label: `MOVE + ${volumeRatioLabel}`,
      color: "red",
      priceChangePct,
      volumeRatio,
    };
  }

  if (hasVolumeAnomaly) {
    return {
      score: 2,
      label: `VOL ${volumeRatioLabel}`,
      color: "orange",
      priceChangePct,
      volumeRatio,
    };
  }

  if (hasPriceAnomaly) {
    return {
      score: 1,
      label: `MOVE ${WAKE_UP_PRICE_MOVE_THRESHOLD_PCT}%`,
      color: "gold",
      priceChangePct,
      volumeRatio,
    };
  }

  return {
    score: 0,
    label: "Quiet",
    color: "default",
    priceChangePct,
    volumeRatio,
  };
}

export function resolveWakeUpVolumeContext(
  liveVolume: number | null | undefined,
  snapshotCurrentVolume: number | null | undefined,
  snapshotPreviousVolume: number | null | undefined,
  fallbackCurrentVolume?: number | null,
  fallbackPreviousVolume?: number | null,
): WakeUpVolumeContext {
  const currentVolume = liveVolume ?? snapshotCurrentVolume ?? fallbackCurrentVolume ?? null;
  const comparisonVolume = snapshotPreviousVolume ?? fallbackPreviousVolume ?? null;
  const volumeDiffPct =
    currentVolume != null && comparisonVolume != null && comparisonVolume > 0
      ? ((currentVolume - comparisonVolume) / comparisonVolume) * 100
      : null;

  return {
    currentVolume,
    currentVolumeLabel: liveVolume != null ? "Now" : "Day",
    comparisonVolume,
    comparisonVolumeLabel: "T-1",
    volumeDiffPct,
  };
}

export function resolveWakeUpSortRatio(
  currentVolume: number | null | undefined,
  previousVolume: number | null | undefined,
): number | null {
  if (currentVolume == null || previousVolume == null || previousVolume <= 0) {
    return null;
  }

  return currentVolume / previousVolume;
}

export function WakeUpVolumeCell({
  symbol,
  snapshot = null,
  fallbackChangePercent = null,
  fallbackCurrentVolume = null,
  fallbackPreviousVolume = null,
}: WakeUpVolumeCellProps) {
  const data = useLiveMarketData(`NSE:${symbol}`);
  const volumeContext = resolveWakeUpVolumeContext(
    data?.volume,
    snapshot?.volume,
    snapshot?.previous_day_volume,
    fallbackCurrentVolume,
    fallbackPreviousVolume,
  );
  const signal = buildWakeUpSignal(
    data?.changePercent ?? snapshot?.change_percent ?? fallbackChangePercent,
    volumeContext.currentVolume,
    volumeContext.comparisonVolume,
  );

  const tooltipText = [
    `Price Move: ${signal.priceChangePct != null ? `${signal.priceChangePct.toFixed(2)}%` : "-"}`,
    `${volumeContext.currentVolumeLabel} Vol: ${formatNumber(volumeContext.currentVolume)}`,
    `${volumeContext.comparisonVolumeLabel} Vol: ${formatNumber(volumeContext.comparisonVolume)}`,
    `Vol Ratio: ${signal.volumeRatio != null ? formatRatio(signal.volumeRatio) : "-"}`,
    `Vol Diff: ${formatPercent(volumeContext.volumeDiffPct)}`,
  ].join(" | ");

  return (
    <Space orientation="vertical" size={1}>
      <Tooltip title={tooltipText}>
        <Tag color={signal.color}>{signal.label}</Tag>
      </Tooltip>
      <Typography.Text type="secondary" style={{ fontSize: 11 }}>
        {volumeContext.currentVolumeLabel}: {formatNumber(volumeContext.currentVolume)}
      </Typography.Text>
      <Typography.Text type="secondary" style={{ fontSize: 11 }}>
        {volumeContext.comparisonVolumeLabel}: {formatNumber(volumeContext.comparisonVolume)}
      </Typography.Text>
      <Typography.Text type="secondary" style={{ fontSize: 11 }}>
        Diff: {formatPercent(volumeContext.volumeDiffPct)}
      </Typography.Text>
    </Space>
  );
}
