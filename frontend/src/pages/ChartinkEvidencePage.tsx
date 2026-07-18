import { UploadOutlined } from "@ant-design/icons";
import { useEffect, useMemo, useRef, useState } from "react";
import { Alert, Button, Card, Empty, Select, Space, Spin, Table, Tabs, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useChartinkEvidence } from "../hooks/useChartinkEvidence";
import type { ChartinkEvidenceDashboardRow, ChartinkEvidenceUploadStatus } from "../types";

type UploadSlot = { key: string; label: string; description: string };

const UPLOAD_SLOTS: UploadSlot[] = [
  { key: "ACCUMULATION_NIFTY_100", label: "Accumulation · Nifty 100", description: "9-month Chartink history" },
  { key: "ACCUMULATION_NIFTY_MIDCAP_150", label: "Accumulation · Midcap 150", description: "9-month Chartink history" },
  { key: "ACCUMULATION_NIFTY_SMALLCAP_250", label: "Accumulation · Smallcap 250", description: "9-month Chartink history" },
  { key: "ACCUMULATION_NIFTY_MICROCAP_250", label: "Accumulation · Microcap 250", description: "9-month Chartink history" },
  { key: "PHASE_D", label: "Phase D", description: "Cash-market scan" },
  { key: "T2_HIGH", label: "T2 High", description: "Cash-market scan" },
  { key: "FRESH_BREAKOUT", label: "Fresh Breakout", description: "Cash-market scan" },
];

const UNIVERSE_TABS = [
  { key: "all", label: "All" },
  { key: "nifty_100", label: "Nifty 100" },
  { key: "nifty_midcap_150", label: "Midcap 150" },
  { key: "nifty_smallcap_250", label: "Smallcap 250" },
  { key: "nifty_microcap_250", label: "Microcap 250" },
];

function formatDate(value: string | null): string { return value ?? "-"; }

function formatUploadStatus(status: ChartinkEvidenceUploadStatus | undefined): string {
  if (!status) return "Not uploaded";
  return `Uploaded ${new Date(status.uploadedAt).toLocaleString("en-IN")} · ${status.sourceFileName}`;
}

export function ChartinkEvidencePage() {
  const { dashboard, loadingDashboard, uploadingSlot, error, loadDashboard, upload } = useChartinkEvidence();
  const [months, setMonths] = useState(1);
  const [universeKey, setUniverseKey] = useState("all");
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const fileInputs = useRef<Record<string, HTMLInputElement | null>>({});

  useEffect(() => { void loadDashboard(months).catch(() => undefined); }, [loadDashboard, months]);

  const uploadFile = async (slot: UploadSlot, file: File): Promise<void> => {
    const result = await upload({ slot: slot.key, csvContent: await file.text(), fileName: file.name });
    setSuccessMessage(`${slot.label}: stored ${result.storedCount}, skipped ${result.skippedOutsideUniverseCount}, duplicates ${result.duplicateCount}.`);
    await loadDashboard(months);
  };

  const columns = useMemo<ColumnsType<ChartinkEvidenceDashboardRow>>(() => [
    { title: "Symbol", dataIndex: "symbol", key: "symbol", fixed: "left", sorter: (left, right) => left.symbol.localeCompare(right.symbol) },
    { title: "Universe", dataIndex: "universeKey", key: "universeKey" },
    { title: "Watchlists", dataIndex: "curatedWatchlists", key: "curatedWatchlists", render: (items: string[]) => items.length === 0 ? "-" : items.map((item) => <Tag color="gold" key={item}>{item}</Tag>) },
    { title: "Accumulation", dataIndex: "accumulationLatestDate", key: "accumulationLatestDate", render: formatDate },
    { title: "Phase D", dataIndex: "phaseDLatestDate", key: "phaseDLatestDate", render: formatDate },
    { title: "T2 High", dataIndex: "t2HighLatestDate", key: "t2HighLatestDate", render: formatDate },
    { title: "Fresh Breakout", dataIndex: "freshBreakoutLatestDate", key: "freshBreakoutLatestDate", render: formatDate },
  ], []);
  const uploadStatuses = useMemo(() => new Map(dashboard?.uploadStatuses.map((status) => [status.slot, status])), [dashboard]);
  const displayedRows = useMemo(
    () => dashboard?.rows.filter((row) => universeKey === "all" || row.universeKey === universeKey) ?? [],
    [dashboard, universeKey],
  );

  return (
    <div style={{ padding: 24, background: "#f5f7fa", minHeight: "calc(100vh - 48px)" }}>
      <Space orientation="vertical" size={16} style={{ width: "100%" }}>
        <div><Typography.Title level={4} style={{ margin: 0 }}>Chartink Evidence</Typography.Title><Typography.Text type="secondary">Upload the seven scanners and review matched Nifty candidates.</Typography.Text></div>
        {error && <Alert type="error" showIcon message={error} />}
        {successMessage && <Alert type="success" showIcon message={successMessage} closable onClose={() => setSuccessMessage(null)} />}
        <Card size="small" title="Daily uploads">
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))", gap: 12 }}>
            {UPLOAD_SLOTS.map((slot) => (
              <Card key={slot.key} size="small">
                <Typography.Text strong>{slot.label}</Typography.Text><div><Typography.Text type="secondary">{slot.description}</Typography.Text></div>
                <div><Typography.Text type={uploadStatuses.has(slot.key) ? "success" : "secondary"}>{formatUploadStatus(uploadStatuses.get(slot.key))}</Typography.Text></div>
                <input ref={(input) => { fileInputs.current[slot.key] = input; }} type="file" accept=".csv,text/csv" hidden onChange={(event) => {
                  const file = event.target.files?.[0];
                  if (file) void uploadFile(slot, file).catch(() => undefined);
                  event.target.value = "";
                }} />
                <Button icon={<UploadOutlined />} loading={uploadingSlot === slot.key} style={{ marginTop: 8 }} onClick={() => fileInputs.current[slot.key]?.click()}>Upload CSV</Button>
              </Card>
            ))}
          </div>
        </Card>
        <Card size="small" title="Candidate evidence" extra={<Select value={months} style={{ width: 140 }} onChange={setMonths} options={[1, 2, 3, 9].map((value) => ({ value, label: `${value} month${value === 1 ? "" : "s"}` }))} />}>
          <Tabs activeKey={universeKey} onChange={setUniverseKey} items={UNIVERSE_TABS} />
          {loadingDashboard ? <Spin /> : displayedRows.length === 0 ? <Empty description="No evidence in this period" /> : <Table rowKey="symbol" columns={columns} dataSource={displayedRows} size="small" scroll={{ x: 1000 }} pagination={{ pageSize: 50, showSizeChanger: false }} />}
        </Card>
      </Space>
    </div>
  );
}
