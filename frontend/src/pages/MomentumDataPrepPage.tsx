import { DownloadOutlined, ReloadOutlined } from "@ant-design/icons";
import { Alert, Button, Card, DatePicker, Select, Space, Spin, Statistic, Typography } from "antd";
import dayjs from "dayjs";
import { useMemo, useState } from "react";
import type { MomentumDataPrepareResponse } from "../types";
import { apiBaseUrl, postJson } from "../utils/api";

const TAG_OPTIONS = [
  { value: "largemidcap250", label: "Large Midcap 250" },
  { value: "smallcap250", label: "Nifty Smallcap 250" },
  { value: "nifty50", label: "Nifty 50" },
];

type DateRange = [dayjs.Dayjs, dayjs.Dayjs];

type PreparePayload = {
  profileId: string;
  fromDate: string;
  toDate: string;
};

export function MomentumDataPrepPage() {
  const [profileId, setProfileId] = useState<string>("largemidcap250");
  const [range, setRange] = useState<DateRange>([dayjs().subtract(3, "month"), dayjs()]);
  const [loading, setLoading] = useState(false);
  const [downloadLoading, setDownloadLoading] = useState<"range" | "today" | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [summary, setSummary] = useState<MomentumDataPrepareResponse | null>(null);

  const canPrepare = useMemo(() => {
    return Boolean(profileId && range[0] && range[1] && !range[0].isAfter(range[1]));
  }, [profileId, range]);

  const handlePrepare = async (): Promise<void> => {
    setLoading(true);
    setError(null);
    setSummary(null);

    const payload: PreparePayload = {
      profileId,
      fromDate: range[0].format("YYYY-MM-DD"),
      toDate: range[1].format("YYYY-MM-DD"),
    };

    try {
      const response = await postJson<MomentumDataPrepareResponse>("/api/strategy/momentum-data/prepare", payload);
      setSummary(response);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to prepare momentum exports.");
    } finally {
      setLoading(false);
    }
  };

  const downloadJson = async (kind: "range" | "today"): Promise<void> => {
    setDownloadLoading(kind);
    setError(null);
    try {
      const path = kind === "range"
        ? `/api/strategy/momentum-data/export/range?profileId=${encodeURIComponent(profileId)}&from=${encodeURIComponent(range[0].format("YYYY-MM-DD"))}&to=${encodeURIComponent(range[1].format("YYYY-MM-DD"))}`
        : `/api/strategy/momentum-data/export/today?profileId=${encodeURIComponent(profileId)}`;
      const response = await fetch(`${apiBaseUrl}${path}`, {
        headers: { Accept: "application/json" },
      });

      if (!response.ok) {
        const payload = await response.text();
        throw new Error(payload || `Download failed with status ${response.status}`);
      }

      const blob = await response.blob();
      const fileName = parseFileName(response.headers.get("content-disposition"))
        ?? defaultFileName(kind, profileId, range[0].format("YYYY-MM-DD"), range[1].format("YYYY-MM-DD"));
      const objectUrl = window.URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = objectUrl;
      anchor.download = fileName;
      anchor.click();
      window.URL.revokeObjectURL(objectUrl);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to download JSON file.");
    } finally {
      setDownloadLoading(null);
    }
  };

  return (
    <div style={{ padding: 24, background: "#f5f7fa", minHeight: "calc(100vh - 48px)" }}>
      <Space direction="vertical" size={16} style={{ width: "100%" }}>
        <div>
          <Typography.Title level={4} style={{ marginBottom: 0 }}>
            Momentum Data Prep
          </Typography.Title>
          <Typography.Text type="secondary">
            Prepare LLM-ready momentum exports: date-range Top 50 daily data and today Top 50 with one-year candles.
          </Typography.Text>
        </div>

        <Card size="small" title="Controls">
          <Space wrap>
            <Select
              style={{ width: 220 }}
              value={profileId}
              onChange={setProfileId}
              options={TAG_OPTIONS}
            />
            <DatePicker.RangePicker
              value={range}
              allowClear={false}
              onChange={(next) => {
                if (next?.[0] && next?.[1]) {
                  setRange([next[0], next[1]]);
                }
              }}
              format="YYYY-MM-DD"
            />
            <Button
              type="primary"
              icon={<ReloadOutlined />}
              loading={loading}
              disabled={!canPrepare}
              onClick={() => void handlePrepare()}
            >
              Prepare Data
            </Button>
            <Button
              icon={<DownloadOutlined />}
              onClick={() => void downloadJson("range")}
              disabled={!summary}
              loading={downloadLoading === "range"}
            >
              Download File 1
            </Button>
            <Button
              icon={<DownloadOutlined />}
              onClick={() => void downloadJson("today")}
              disabled={!summary}
              loading={downloadLoading === "today"}
            >
              Download File 2
            </Button>
          </Space>
        </Card>

        {error && <Alert type="error" showIcon message={error} />}

        {loading ? (
          <div style={{ textAlign: "center", padding: 48 }}>
            <Spin tip="Preparing momentum datasets..." />
          </div>
        ) : summary ? (
          <Card size="small" title="Run Summary">
            <Space size={24} wrap>
              <Statistic title="Tag" value={summary.profileId} />
              <Statistic title="From" value={summary.fromDate} />
              <Statistic title="To" value={summary.toDate} />
              <Statistic title="Trading Days" value={summary.tradingDaysProcessed} />
              <Statistic title="Today Anchor Date" value={summary.todayAsOfDateUsed ?? "N/A"} />
              <Statistic title="Top50 Count (Today)" value={summary.top50Count} />
            </Space>
            {summary.warnings.length > 0 && (
              <Alert
                style={{ marginTop: 16 }}
                type="warning"
                showIcon
                message={summary.warnings.join(" | ")}
              />
            )}
          </Card>
        ) : null}
      </Space>
    </div>
  );
}

function parseFileName(contentDisposition: string | null): string | null {
  if (!contentDisposition) {
    return null;
  }
  const match = contentDisposition.match(/filename=\"?([^\";]+)\"?/i);
  return match?.[1] ?? null;
}

function defaultFileName(kind: "range" | "today", profileId: string, from: string, to: string): string {
  if (kind === "range") {
    return `top50_by_day_${profileId}_${from}_${to}.json`;
  }
  return `top50_today_with_1y_candles_${profileId}.json`;
}
