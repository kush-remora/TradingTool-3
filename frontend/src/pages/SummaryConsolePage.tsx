import { DownloadOutlined, ReloadOutlined } from "@ant-design/icons";
import { Alert, Button, Card, Empty, Select, Space, Spin, Typography } from "antd";
import { useCallback, useEffect, useMemo, useState } from "react";
import { SummaryConsoleFilters } from "../components/SummaryConsoleFilters";
import { SummaryConsoleGuide } from "../components/SummaryConsoleGuide";
import { SummaryConsoleTable } from "../components/SummaryConsoleTable";
import type { SummaryConsoleResponse, UniverseOption, UniverseOptionsResponse } from "../types";
import { getJson } from "../utils/api";
import { buildSummaryConsoleCsv, buildSummaryConsoleGuide, downloadSummaryConsoleFile } from "../utils/summaryConsoleExport";
import {
  filterSummaryConsoleRows,
  describeSummaryConsoleFilter,
  type SummaryConsoleFilterMatch,
  type SummaryConsoleFilterScope,
  type SummaryConsoleSignalKey,
} from "../utils/summaryConsoleFiltering";

const { Text, Title } = Typography;

interface SummaryConsolePageProps {
  onOpenStockReview: (symbol: string) => void;
}

export function SummaryConsolePage({ onOpenStockReview }: SummaryConsolePageProps) {
  const [options, setOptions] = useState<UniverseOption[]>([]);
  const [selectedWatchlists, setSelectedWatchlists] = useState<string[]>([]);
  const [data, setData] = useState<SummaryConsoleResponse | null>(null);
  const [loadingOptions, setLoadingOptions] = useState(true);
  const [loadingScan, setLoadingScan] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selectedSignals, setSelectedSignals] = useState<SummaryConsoleSignalKey[]>([]);
  const [filterMatch, setFilterMatch] = useState<SummaryConsoleFilterMatch>("ANY");
  const [filterScope, setFilterScope] = useState<SummaryConsoleFilterScope>("SAME_SESSION");

  const loadScan = useCallback((): void => {
    if (selectedWatchlists.length === 0) {
      setData(null);
      return;
    }

    setLoadingScan(true);
    setError(null);
    const watchlistQuery = encodeURIComponent(selectedWatchlists.join(","));
    void getJson<SummaryConsoleResponse>(
      `/api/strategy/summary-console/scan?watchlists=${watchlistQuery}`,
      { useCache: false },
    )
      .then(setData)
      .catch((requestError: unknown) => {
        setError(requestError instanceof Error ? requestError.message : "Failed to scan the selected watchlists.");
      })
      .finally(() => setLoadingScan(false));
  }, [selectedWatchlists]);

  useEffect(() => {
    let active = true;
    void getJson<UniverseOptionsResponse>("/api/strategy/summary-console/watchlists")
      .then((response) => {
        if (active) setOptions(response.options);
      })
      .catch((requestError: unknown) => {
        if (active) setError(requestError instanceof Error ? requestError.message : "Failed to load watchlists.");
      })
      .finally(() => {
        if (active) setLoadingOptions(false);
      });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    loadScan();
  }, [loadScan]);

  const filteredRows = useMemo(
    () => data == null ? [] : filterSummaryConsoleRows(data.rows, selectedSignals, filterMatch, filterScope),
    [data, filterMatch, filterScope, selectedSignals],
  );

  const filteredData = useMemo<SummaryConsoleResponse | null>(() => {
    if (!data) return null;
    return {
      ...data,
      eventCount: filteredRows.length,
      uniqueStockCount: new Set(filteredRows.map((row) => row.symbol)).size,
      rows: filteredRows,
    };
  }, [data, filteredRows]);

  const hasActiveFilter = selectedSignals.length > 0;
  const filterDescription = describeSummaryConsoleFilter(selectedSignals, filterMatch, filterScope, data?.lookbackSessions ?? 5);

  const downloadCsv = (): void => {
    if (!filteredData || filteredData.rows.length === 0) return;
    downloadSummaryConsoleFile(
      `summary_console_${filteredData.requestedAsOfDate}${hasActiveFilter ? "_filtered" : ""}.csv`,
      `\uFEFF${buildSummaryConsoleCsv(filteredData)}`,
      "text/csv;charset=utf-8",
    );
  };

  const downloadGuide = (): void => {
    downloadSummaryConsoleFile(
      "summary_console_ai_column_guide.md",
      buildSummaryConsoleGuide(filterDescription),
      "text/markdown;charset=utf-8",
    );
  };

  return (
    <div style={{ padding: "24px 24px 160px" }}>
      <Space orientation="vertical" size={16} style={{ width: "100%" }}>
        <Card
          title={<Title level={3} style={{ margin: 0 }}>Summary Console</Title>}
          extra={(
            <Space>
              <Button icon={<DownloadOutlined />} onClick={downloadCsv} disabled={!filteredData || filteredData.rows.length === 0}>
                Download CSV
              </Button>
              <Button icon={<ReloadOutlined />} onClick={loadScan} loading={loadingScan} disabled={selectedWatchlists.length === 0}>
                Reload
              </Button>
            </Space>
          )}
        >
          <Space orientation="vertical" size={8} style={{ width: "100%" }}>
            <Text type="secondary">Five-session attention view: each row is one stock/session that crossed the 200 SMA, moved more than 3%, showed 2× volume, or crossed a 20D/40D/60D closing level.</Text>
            <Select
              aria-label="Watchlists"
              mode="multiple"
              loading={loadingOptions}
              value={selectedWatchlists}
              onChange={setSelectedWatchlists}
              placeholder="Select one or more watchlists"
              maxTagCount="responsive"
              style={{ width: 520, maxWidth: "100%" }}
              options={options.map((option) => ({ value: option.value, label: `${option.label} (${option.count})` }))}
            />
            {data && <Text type="secondary" style={{ fontSize: 12 }}>
              Last {data.lookbackSessions} trading sessions through {data.requestedAsOfDate} · scanned {data.scannedCount} stocks · {filteredData?.uniqueStockCount ?? 0}{hasActiveFilter ? ` of ${data.uniqueStockCount}` : ""} stocks shown · {filteredRows.length}{hasActiveFilter ? ` of ${data.eventCount}` : ""} event rows
            </Text>}
          </Space>
        </Card>

        {error && <Alert type="error" message={error} showIcon />}
        {loadingScan && <Spin />}
        {!loadingScan && selectedWatchlists.length === 0 && !loadingOptions && <Empty description="Select one or more watchlists to see recent attention events." />}
        {!loadingScan && selectedWatchlists.length > 0 && data && data.rows.length === 0 && <Empty description="No attention events were found in the latest trading sessions." />}
        {data && data.rows.length > 0 && (
          <>
            <SummaryConsoleFilters
              selectedSignals={selectedSignals}
              match={filterMatch}
              scope={filterScope}
              lookbackSessions={data.lookbackSessions}
              onSignalsChange={setSelectedSignals}
              onMatchChange={setFilterMatch}
              onScopeChange={setFilterScope}
              onClear={() => {
                setSelectedSignals([]);
                setFilterMatch("ANY");
                setFilterScope("SAME_SESSION");
              }}
            />
            {filteredRows.length > 0
              ? <SummaryConsoleTable rows={filteredRows} onOpenStockReview={onOpenStockReview} />
              : <Empty description="No rows match the selected filter. Try Any, Same session, or clear the signals." />}
          </>
        )}
        <SummaryConsoleGuide onDownloadGuide={downloadGuide} />
      </Space>
    </div>
  );
}
