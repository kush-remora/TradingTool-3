import {
  ExclamationCircleOutlined,
  FileTextOutlined,
  SearchOutlined,
  SyncOutlined,
  UploadOutlined,
} from "@ant-design/icons";
import { useEffect, useMemo, useState } from "react";
import {
  Alert,
  Button,
  Card,
  Col,
  Empty,
  Input,
  Row,
  Space,
  Spin,
  Statistic,
  Table,
  Tabs,
  Tag,
  Typography,
  Upload,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import type { TabsProps, UploadProps } from "antd";

interface PhaseCWatchlistDto {
  symbol: string;
  stockName: string | null;
  marketCapBucket: string | null;
  closePrice: number | null;
  pctChange: string | null;
  volume: number | null;
  sector: string | null;
  industry: string | null;
  rocePct: number | null;
  ronwPct: number | null;
  netProfitAfterTax: number | null;
  debtEquityRatio: number | null;
  volDry200dMinCount: number | null;
  volDry60dMinCount: number | null;
  volDry200dMin105Count: number | null;
  volDry60dMin105Count: number | null;
  indianPromoterPct: number | null;
  foreignPromoterPct: number | null;
  quarterlyGrossSales: number | null;
  high52w: number | null;
  low52w: number | null;
  dist200dHighPct: number | null;
  dist200dLowPct: number | null;
  atrLt2pctCount: number | null;
}

interface PhaseCWatchlistRow extends PhaseCWatchlistDto {
  addedOn: string;
  lastSeenOn: string;
  status: string;
  instrumentToken: number | null;
  marketFieldsUpdatedOn: string | null;
  phase2DeliveryStatus: string | null;
  phase2Reason: string | null;
  phase2EvaluatedOn: string | null;
  deliveryQuantityToday: number | null;
  deliveryPctToday: number | null;
  wholesaleBaseDq: number | null;
  deliverySpikeRatio: number | null;
  convictionDays10d: number | null;
  convictionDays20d: number | null;
}

type UploadResult = {
  inserted: number;
  updated: number;
};

type DeliveryValidationResult = {
  evaluatedOn: string | null;
  totalStocks: number;
  passed: number;
  watch: number;
  notPassed: number;
  notRun: number;
  dataMissing: number;
};

type FreshFieldRefreshResult = {
  refreshedCount: number;
  refreshedOn: string | null;
};

type QuickFilter = "all" | "resolved" | "unresolved";
type WatchlistTab = "all-phase-1" | "delivery-conviction";

type SummaryStats = {
  totalCandidates: number;
  resolvedTokens: number;
  unresolvedTokens: number;
  phase2Passed: number;
  phase2Watch: number;
  phase2NotPassed: number;
  phase2DataMissing: number;
};

const HEADER_ALIASES: Record<keyof PhaseCWatchlistDto, string[]> = {
  symbol: ["symbol"],
  stockName: ["stock name", "stock_name"],
  marketCapBucket: ["marketcapname", "market cap", "marketcap", "market_cap_bucket"],
  closePrice: ["close", "close_price"],
  pctChange: ["%_change", "% change", "change", "pct_change"],
  volume: ["volume"],
  sector: ["sector"],
  industry: ["industry"],
  rocePct: ["roce", "return on capital employed", "roce_pct"],
  ronwPct: ["ronw", "return on net worth", "ronw_pct"],
  netProfitAfterTax: ["3 quarter ago", "net profit", "net_profit_3q_ago", "net_profit_after_tax"],
  debtEquityRatio: ["yearly debt equity ratio", "debt", "debt_equity_ratio"],
  volDry200dMinCount: ["volume dry on 200 days min", "vol_dry_200d_min_count"],
  volDry60dMinCount: ["volume dry on 60 days min", "vol_dry_60d_min_count"],
  volDry200dMin105Count: ["volume dry on 200 days min * 1.05", "vol_dry_200d_min_105_count"],
  volDry60dMin105Count: ["volume dry on 60days min * 1.05", "volume dry on 60 days min * 1.05", "vol_dry_60d_min_105_count"],
  indianPromoterPct: ["indian promoter", "quarterly indian promoter and group percentage", "indian_promoter_pct"],
  foreignPromoterPct: ["foreign promoter", "quarterly total foreign promoter and group percentage", "foreign_promoter_pct"],
  quarterlyGrossSales: ["gross sales", "quarterly gross sales", "quarterly_gross_sales"],
  high52w: ["max( 252", "high_52w"],
  low52w: ["min( 20", "low_52w"],
  dist200dHighPct: ["daily close -", "max( 200", "dist_200d_high_pct"],
  dist200dLowPct: ["brackets2", "dist_20d_low_pct", "dist_200d_low_pct"],
  atrLt2pctCount: ["count daily atr < 2 %", "atr", "atr_lt_2pct_count"],
};

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

  return `${value.toFixed(1)}%`;
}

function formatRatio(value: number | null | undefined): string {
  if (value == null) {
    return "-";
  }

  return `${value.toFixed(2)}x`;
}

function buildStatusTag(status: string) {
  switch (status.toUpperCase()) {
    case "BREAKOUT_TRIGGERED":
      return <Tag color="green">{status}</Tag>;
    case "EXPIRED":
      return <Tag>{status}</Tag>;
    default:
      return <Tag color="blue">{status}</Tag>;
  }
}

function buildTokenTag(instrumentToken: number | null) {
  if (instrumentToken != null) {
    return <Tag color="green">Resolved</Tag>;
  }
  return <Tag color="gold">Missing</Tag>;
}

function buildPhase2Tag(status: string | null) {
  switch (status) {
    case "PASSED":
      return <Tag color="green">Passed</Tag>;
    case "WATCH":
      return <Tag color="blue">Watch</Tag>;
    case "NOT_PASSED":
      return <Tag color="default">Not passed</Tag>;
    case "DATA_MISSING":
      return <Tag color="gold">Data missing</Tag>;
    default:
      return <Tag color="default">Not run</Tag>;
  }
}

async function readErrorMessage(response: Response, fallbackMessage: string): Promise<string> {
  try {
    const body = (await response.json()) as { detail?: string; error?: string; message?: string };
    return body.detail || body.error || body.message || fallbackMessage;
  } catch {
    return fallbackMessage;
  }
}

function parseDelimitedLine(line: string, separator: string): string[] {
  const values: string[] = [];
  let current = "";
  let inQuotes = false;

  for (let index = 0; index < line.length; index += 1) {
    const char = line[index];
    const nextChar = line[index + 1];

    if (char === '"') {
      if (inQuotes && nextChar === '"') {
        current += '"';
        index += 1;
      } else {
        inQuotes = !inQuotes;
      }
      continue;
    }

    if (char === separator && !inQuotes) {
      values.push(current);
      current = "";
      continue;
    }

    current += char;
  }

  values.push(current);
  return values.map((value) => value.trim());
}

function findColumnIndex(headers: string[], aliases: string[]): number {
  return headers.findIndex((header) => aliases.some((alias) => header.includes(alias)));
}

export function parseCsvOrTsv(text: string): PhaseCWatchlistDto[] {
  const lines = text
    .split("\n")
    .map((line) => line.replace(/\r/g, "").trim())
    .filter((line) => line.length > 0);

  if (lines.length < 2) {
    return [];
  }

  const isTsv = lines[0].includes("\t");
  const separator = isTsv ? "\t" : ",";
  const headers = parseDelimitedLine(lines[0], separator).map((header) => header.trim().toLowerCase());
  const columnIndexByField = Object.fromEntries(
    (Object.entries(HEADER_ALIASES) as Array<[keyof PhaseCWatchlistDto, string[]]>).map(([field, aliases]) => [
      field,
      findColumnIndex(headers, aliases.map((alias) => alias.toLowerCase())),
    ]),
  ) as Record<keyof PhaseCWatchlistDto, number>;

  const parseNumber = (value: string | undefined): number | null => {
    if (!value) {
      return null;
    }
    const cleaned = value.replace(/,/g, "").trim();
    const parsed = Number.parseFloat(cleaned);
    return Number.isNaN(parsed) ? null : parsed;
  };

  const getColumnValue = (columns: string[], field: keyof PhaseCWatchlistDto): string | undefined => {
    const index = columnIndexByField[field];
    return index >= 0 ? columns[index] : undefined;
  };

  const rows: PhaseCWatchlistDto[] = [];

  for (let index = 1; index < lines.length; index += 1) {
    const columns = parseDelimitedLine(lines[index], separator);
    const symbol = getColumnValue(columns, "symbol")?.trim();

    if (!symbol) {
      continue;
    }

    rows.push({
      symbol,
      stockName: getColumnValue(columns, "stockName")?.trim() || null,
      marketCapBucket: getColumnValue(columns, "marketCapBucket")?.trim() || null,
      closePrice: parseNumber(getColumnValue(columns, "closePrice")),
      pctChange: getColumnValue(columns, "pctChange")?.trim() || null,
      volume: parseNumber(getColumnValue(columns, "volume")),
      sector: getColumnValue(columns, "sector")?.trim() || null,
      industry: getColumnValue(columns, "industry")?.trim() || null,
      rocePct: parseNumber(getColumnValue(columns, "rocePct")),
      ronwPct: parseNumber(getColumnValue(columns, "ronwPct")),
      netProfitAfterTax: parseNumber(getColumnValue(columns, "netProfitAfterTax")),
      debtEquityRatio: parseNumber(getColumnValue(columns, "debtEquityRatio")),
      volDry200dMinCount: parseNumber(getColumnValue(columns, "volDry200dMinCount")),
      volDry60dMinCount: parseNumber(getColumnValue(columns, "volDry60dMinCount")),
      volDry200dMin105Count: parseNumber(getColumnValue(columns, "volDry200dMin105Count")),
      volDry60dMin105Count: parseNumber(getColumnValue(columns, "volDry60dMin105Count")),
      indianPromoterPct: parseNumber(getColumnValue(columns, "indianPromoterPct")),
      foreignPromoterPct: parseNumber(getColumnValue(columns, "foreignPromoterPct")),
      quarterlyGrossSales: parseNumber(getColumnValue(columns, "quarterlyGrossSales")),
      high52w: parseNumber(getColumnValue(columns, "high52w")),
      low52w: parseNumber(getColumnValue(columns, "low52w")),
      dist200dHighPct: parseNumber(getColumnValue(columns, "dist200dHighPct")),
      dist200dLowPct: parseNumber(getColumnValue(columns, "dist200dLowPct")),
      atrLt2pctCount: parseNumber(getColumnValue(columns, "atrLt2pctCount")),
    });
  }

  return rows;
}

export function PhaseDScannerPage() {
  const [watchlist, setWatchlist] = useState<PhaseCWatchlistRow[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [uploading, setUploading] = useState<boolean>(false);
  const [runningValidation, setRunningValidation] = useState<boolean>(false);
  const [refreshingFreshFields, setRefreshingFreshFields] = useState<boolean>(false);
  const [uploadResult, setUploadResult] = useState<UploadResult | null>(null);
  const [validationResult, setValidationResult] = useState<DeliveryValidationResult | null>(null);
  const [freshFieldResult, setFreshFieldResult] = useState<FreshFieldRefreshResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState<string>("");
  const [quickFilter, setQuickFilter] = useState<QuickFilter>("all");
  const [activeTab, setActiveTab] = useState<WatchlistTab>("all-phase-1");

  const fetchWatchlist = async (): Promise<void> => {
    try {
      setLoading(true);
      const response = await fetch("/api/strategy/phase-c/dashboard");
      if (!response.ok) {
        throw new Error(await readErrorMessage(response, "Failed to fetch watchlist"));
      }
      const data = (await response.json()) as PhaseCWatchlistRow[] | unknown;
      setWatchlist(Array.isArray(data) ? (data as PhaseCWatchlistRow[]) : []);
    } catch (fetchError) {
      const message = fetchError instanceof Error ? fetchError.message : "Failed to fetch watchlist";
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void fetchWatchlist();
  }, []);

  const handleFileUpload = async (file: File): Promise<boolean> => {
    setUploading(true);
    setError(null);
    setUploadResult(null);
    setValidationResult(null);
    setFreshFieldResult(null);

    try {
      const text = await file.text();
      const parsedRows = parseCsvOrTsv(text);

      if (parsedRows.length === 0) {
        throw new Error("No valid rows found in the file.");
      }

      const response = await fetch("/api/strategy/phase-c/upload", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ rows: parsedRows }),
      });

      if (!response.ok) {
        throw new Error(await readErrorMessage(response, "Failed to upload rows"));
      }

      const data = (await response.json()) as {
        insertedCount: number;
        updatedCount: number;
      };

      setUploadResult({
        inserted: data.insertedCount,
        updated: data.updatedCount,
      });

      await fetchWatchlist();
    } catch (uploadError) {
      const message = uploadError instanceof Error ? uploadError.message : "Failed to upload rows";
      setError(message);
    } finally {
      setUploading(false);
    }

    return false;
  };

  const handleRunDeliveryValidation = async (): Promise<void> => {
    setRunningValidation(true);
    setError(null);
    setValidationResult(null);

    try {
      const response = await fetch("/api/strategy/phase-c/delivery-validation/run", {
        method: "POST",
      });
      if (!response.ok) {
        throw new Error(await readErrorMessage(response, "Failed to run delivery validation"));
      }

      const data = (await response.json()) as DeliveryValidationResult;
      setValidationResult(data);
      await fetchWatchlist();
    } catch (runError) {
      const message = runError instanceof Error ? runError.message : "Failed to run delivery validation";
      setError(message);
    } finally {
      setRunningValidation(false);
    }
  };

  const handleRefreshFreshFields = async (): Promise<void> => {
    setRefreshingFreshFields(true);
    setError(null);
    setFreshFieldResult(null);

    try {
      const response = await fetch("/api/strategy/phase-c/fresh-fields/update", {
        method: "POST",
      });
      if (!response.ok) {
        throw new Error(await readErrorMessage(response, "Failed to update fresh fields"));
      }

      const data = (await response.json()) as FreshFieldRefreshResult;
      setFreshFieldResult(data);
      await fetchWatchlist();
    } catch (refreshError) {
      const message = refreshError instanceof Error ? refreshError.message : "Failed to update fresh fields";
      setError(message);
    } finally {
      setRefreshingFreshFields(false);
    }
  };

  const summary = useMemo<SummaryStats>(() => {
    const totalCandidates = watchlist.length;
    const resolvedTokens = watchlist.filter((row) => row.instrumentToken != null).length;
    const unresolvedTokens = totalCandidates - resolvedTokens;
    const phase2Passed = watchlist.filter((row) => row.phase2DeliveryStatus === "PASSED").length;
    const phase2Watch = watchlist.filter((row) => row.phase2DeliveryStatus === "WATCH").length;
    const phase2NotPassed = watchlist.filter((row) => row.phase2DeliveryStatus === "NOT_PASSED").length;
    const phase2DataMissing = watchlist.filter((row) => row.phase2DeliveryStatus === "DATA_MISSING").length;

    return {
      totalCandidates,
      resolvedTokens,
      unresolvedTokens,
      phase2Passed,
      phase2Watch,
      phase2NotPassed,
      phase2DataMissing,
    };
  }, [watchlist]);

  const filteredRows = useMemo(() => {
    const normalizedQuery = searchQuery.trim().toLowerCase();

    return watchlist.filter((row) => {
      if (activeTab === "delivery-conviction" && row.phase2DeliveryStatus !== "PASSED") {
        return false;
      }

      if (quickFilter === "resolved" && row.instrumentToken == null) {
        return false;
      }

      if (quickFilter === "unresolved" && row.instrumentToken != null) {
        return false;
      }

      if (!normalizedQuery) {
        return true;
      }

      const haystack = [
        row.symbol,
        row.stockName,
        row.sector,
        row.industry,
        row.marketCapBucket,
        row.phase2Reason,
      ]
        .filter((value): value is string => Boolean(value))
        .join(" ")
        .toLowerCase();

      return haystack.includes(normalizedQuery);
    });
  }, [activeTab, quickFilter, searchQuery, watchlist]);

  const freshnessSummary = useMemo(() => {
    if (watchlist.length === 0) {
      return null;
    }

    const staleRows = watchlist.filter((row) => row.marketFieldsUpdatedOn == null);
    if (staleRows.length > 0) {
      return {
        type: "warning" as const,
        message: "Fresh market fields are not updated yet.",
        description: `${staleRows.length} of ${watchlist.length} rows are still showing uploaded CSV values. Run Update Fresh Fields to pull daily candle data from Kite.`,
      };
    }

    const refreshedDates = Array.from(
      new Set(
        watchlist
          .map((row) => row.marketFieldsUpdatedOn)
          .filter((value): value is string => Boolean(value)),
      ),
    ).sort();
    const latestRefreshDate = refreshedDates[refreshedDates.length - 1] ?? null;

    return {
      type: "info" as const,
      message: "Fresh market fields are up to date.",
      description: latestRefreshDate
        ? `All ${watchlist.length} rows are using market data from ${latestRefreshDate}.`
        : "All rows have a refresh date recorded.",
    };
  }, [watchlist]);

  const uploadProps: UploadProps = {
    accept: ".csv,.tsv,text/plain",
    showUploadList: false,
    beforeUpload: (file) => handleFileUpload(file as File),
    disabled: uploading,
  };

  const columns = useMemo<ColumnsType<PhaseCWatchlistRow>>(
    () => [
      {
        title: "Candidate",
        key: "candidate",
        width: 220,
        fixed: "left",
        render: (_value, row) => (
          <Space orientation="vertical" size={0}>
            <Typography.Text strong>{row.symbol}</Typography.Text>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {row.stockName || "-"}
            </Typography.Text>
            <Typography.Text type="secondary" style={{ fontSize: 11 }}>
              {[row.marketCapBucket || "Cap unknown", row.sector || "Sector unknown"].join(" / ")}
            </Typography.Text>
          </Space>
        ),
      },
      {
        title: "Phase 2",
        key: "phase2",
        width: 190,
        render: (_value, row) => (
          <Space orientation="vertical" size={2}>
            {buildPhase2Tag(row.phase2DeliveryStatus)}
            <Typography.Text type="secondary" style={{ fontSize: 11 }}>
              {row.phase2Reason || "awaiting_delivery_validation"}
            </Typography.Text>
            <Typography.Text type="secondary" style={{ fontSize: 11 }}>
              {row.phase2EvaluatedOn ? `Evaluated ${row.phase2EvaluatedOn}` : "Not evaluated yet"}
            </Typography.Text>
          </Space>
        ),
      },
      {
        title: "Token",
        key: "token",
        width: 130,
        render: (_value, row) => (
          <Space orientation="vertical" size={2}>
            {buildTokenTag(row.instrumentToken)}
            <Typography.Text type="secondary" style={{ fontSize: 11 }}>
              {row.instrumentToken != null ? formatNumber(row.instrumentToken) : "Needs resolver match"}
            </Typography.Text>
          </Space>
        ),
      },
      {
        title: "Delivery Validation",
        key: "deliveryValidation",
        width: 210,
        render: (_value, row) => (
          <Space orientation="vertical" size={0}>
            <Typography.Text style={{ fontSize: 12 }}>Today DQ: {formatNumber(row.deliveryQuantityToday)}</Typography.Text>
            <Typography.Text style={{ fontSize: 12 }}>Today Del %: {formatPercent(row.deliveryPctToday)}</Typography.Text>
            <Typography.Text style={{ fontSize: 12 }}>Base DQ: {formatNumber(row.wholesaleBaseDq)}</Typography.Text>
            <Typography.Text style={{ fontSize: 12 }}>Spike: {formatRatio(row.deliverySpikeRatio)}</Typography.Text>
          </Space>
        ),
      },
      {
        title: "Rolling Counts",
        key: "rollingCounts",
        width: 170,
        render: (_value, row) => (
          <Space orientation="vertical" size={0}>
            <Typography.Text style={{ fontSize: 12 }}>10D Conviction: {formatNumber(row.convictionDays10d)}</Typography.Text>
            <Typography.Text style={{ fontSize: 12 }}>20D Conviction: {formatNumber(row.convictionDays20d)}</Typography.Text>
          </Space>
        ),
      },
      {
        title: "Close",
        key: "close",
        width: 110,
        align: "right",
        render: (_value, row) => (
          <Space orientation="vertical" size={0} style={{ width: "100%" }}>
            <Typography.Text>{formatNumber(row.closePrice, 2)}</Typography.Text>
            <Typography.Text type="secondary" style={{ fontSize: 11 }}>
              {row.pctChange || "-"}
            </Typography.Text>
          </Space>
        ),
      },
      {
        title: "Volume",
        dataIndex: "volume",
        key: "volume",
        width: 120,
        align: "right",
        render: (value: number | null) => formatNumber(value),
      },
      {
        title: "Dry-Up Profile",
        key: "dryup",
        width: 190,
        render: (_value, row) => (
          <Space orientation="vertical" size={0}>
            <Typography.Text style={{ fontSize: 12 }}>200D Min: {formatNumber(row.volDry200dMinCount)}</Typography.Text>
            <Typography.Text style={{ fontSize: 12 }}>60D Min: {formatNumber(row.volDry60dMinCount)}</Typography.Text>
            <Typography.Text style={{ fontSize: 12 }}>200D x1.05: {formatNumber(row.volDry200dMin105Count)}</Typography.Text>
            <Typography.Text style={{ fontSize: 12 }}>60D x1.05: {formatNumber(row.volDry60dMin105Count)}</Typography.Text>
          </Space>
        ),
      },
      {
        title: "Structure",
        key: "structure",
        width: 190,
        render: (_value, row) => (
          <Space orientation="vertical" size={0}>
            <Typography.Text style={{ fontSize: 12 }}>Dist 200D High: {formatPercent(row.dist200dHighPct)}</Typography.Text>
            <Typography.Text style={{ fontSize: 12 }}>ATR Count: {formatNumber(row.atrLt2pctCount)}</Typography.Text>
            <Typography.Text style={{ fontSize: 12 }}>52W Low: {formatNumber(row.low52w, 2)}</Typography.Text>
            <Typography.Text style={{ fontSize: 12 }}>52W High: {formatNumber(row.high52w, 2)}</Typography.Text>
          </Space>
        ),
      },
      {
        title: "Ownership / Quality",
        key: "quality",
        width: 190,
        render: (_value, row) => (
          <Space orientation="vertical" size={0}>
            <Typography.Text style={{ fontSize: 12 }}>Promoter: {formatPercent(row.indianPromoterPct)}</Typography.Text>
            <Typography.Text style={{ fontSize: 12 }}>FII: {formatPercent(row.foreignPromoterPct)}</Typography.Text>
            <Typography.Text style={{ fontSize: 12 }}>ROCE: {formatPercent(row.rocePct)}</Typography.Text>
            <Typography.Text style={{ fontSize: 12 }}>Debt / Eq: {formatNumber(row.debtEquityRatio, 2)}</Typography.Text>
          </Space>
        ),
      },
      {
        title: "Dates",
        key: "dates",
        width: 180,
        render: (_value, row) => (
          <Space orientation="vertical" size={0}>
            <Typography.Text style={{ fontSize: 12 }}>Added {row.addedOn}</Typography.Text>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              Last seen {row.lastSeenOn}
            </Typography.Text>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {row.marketFieldsUpdatedOn ? `Fresh fields ${row.marketFieldsUpdatedOn}` : "Fresh fields pending"}
            </Typography.Text>
          </Space>
        ),
      },
    ],
    [],
  );

  const tabItems: TabsProps["items"] = [
    {
      key: "all-phase-1",
      label: `All Phase 1 Stocks (${summary.totalCandidates})`,
    },
    {
      key: "delivery-conviction",
      label: `Delivery Conviction (${summary.phase2Passed})`,
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <Space orientation="vertical" size={16} style={{ width: "100%" }}>
        <Card>
          <Space orientation="vertical" size={8} style={{ width: "100%" }}>
            <Space wrap size={8}>
              <Tag color="blue">Phase 1 Intake</Tag>
              <Tag color="green">Phase 2 Delivery Validation</Tag>
            </Space>
            <Typography.Title level={2} style={{ margin: 0 }}>
              Review quiet Phase 1 candidates, then validate delivery conviction on demand.
            </Typography.Title>
            <Typography.Text type="secondary">
              This screen keeps the current Phase 1 watchlist fresh, resolves Kite tokens, and runs the Phase 2
              delivery pass on the same stocks whenever you want to re-check conviction.
            </Typography.Text>

            <Row gutter={[12, 12]} style={{ marginTop: 4 }}>
              <Col xs={12} md={6}>
                <Card size="small">
                  <Statistic title="Phase 1 Stocks" value={summary.totalCandidates} />
                </Card>
              </Col>
              <Col xs={12} md={6}>
                <Card size="small">
                  <Statistic title="Tokens Resolved" value={summary.resolvedTokens} />
                </Card>
              </Col>
              <Col xs={12} md={6}>
                <Card size="small">
                  <Statistic title="Phase 2 Passed" value={summary.phase2Passed} styles={{ content: { color: "#389e0d" } }} />
                </Card>
              </Col>
              <Col xs={12} md={6}>
                <Card size="small">
                  <Statistic title="Phase 2 Watch" value={summary.phase2Watch} styles={{ content: { color: "#1677ff" } }} />
                </Card>
              </Col>
              <Col xs={12} md={6}>
                <Card size="small">
                  <Statistic title="Phase 2 Not Passed" value={summary.phase2NotPassed} />
                </Card>
              </Col>
              <Col xs={12} md={6}>
                <Card size="small">
                  <Statistic title="Data Missing" value={summary.phase2DataMissing} styles={{ content: { color: "#d48806" } }} />
                </Card>
              </Col>
              <Col xs={12} md={6}>
                <Card size="small">
                  <Statistic title="Unresolved Tokens" value={summary.unresolvedTokens} styles={{ content: { color: "#d48806" } }} />
                </Card>
              </Col>
            </Row>
          </Space>
        </Card>

        <Row gutter={[16, 16]}>
          <Col xs={24} xl={16}>
            <Card
              title="Import or re-run the current watchlist"
              extra={
                <Space>
                  <Button icon={<SyncOutlined />} onClick={() => void fetchWatchlist()} loading={loading}>
                    Refresh
                  </Button>
                  <Button
                    icon={<SyncOutlined />}
                    onClick={() => void handleRefreshFreshFields()}
                    loading={refreshingFreshFields}
                    disabled={watchlist.length === 0}
                  >
                    Update Fresh Fields
                  </Button>
                  <Button
                    type="primary"
                    icon={<SyncOutlined />}
                    onClick={() => void handleRunDeliveryValidation()}
                    loading={runningValidation}
                    disabled={watchlist.length === 0}
                  >
                    Run Delivery Validation
                  </Button>
                </Space>
              }
            >
              <Space orientation="vertical" size={16} style={{ width: "100%" }}>
                <Typography.Text type="secondary">
                  Upload the Chartink shortlist to refresh Phase 1 rows, then run Phase 2 to see which names show real
                  delivery-based conviction.
                </Typography.Text>

                <Upload.Dragger {...uploadProps}>
                  <p className="ant-upload-drag-icon">
                    {uploading ? <SyncOutlined spin /> : <UploadOutlined />}
                  </p>
                  <p className="ant-upload-text">
                    {uploading ? "Importing and resolving tokens..." : "Drop CSV/TSV or click to upload"}
                  </p>
                  <p className="ant-upload-hint">
                    Best for the raw Chartink export used to seed the Phase 1 watchlist.
                  </p>
                </Upload.Dragger>

                {error ? <Alert type="error" showIcon icon={<ExclamationCircleOutlined />} title={error} /> : null}

                {uploadResult ? (
                  <Alert
                    type="success"
                    showIcon
                    icon={<FileTextOutlined />}
                    title={`Imported ${uploadResult.inserted} rows. Updates reported: ${uploadResult.updated}.`}
                  />
                ) : null}

                {validationResult ? (
                  <Alert
                    type="info"
                    showIcon
                    title={`Validated ${validationResult.totalStocks} stocks: ${validationResult.passed} passed, ${validationResult.watch} watch, ${validationResult.notPassed} not passed, ${validationResult.dataMissing} data missing.`}
                    description={
                      validationResult.evaluatedOn
                        ? `Latest delivery evaluation date: ${validationResult.evaluatedOn}`
                        : undefined
                    }
                  />
                ) : null}

                {freshFieldResult ? (
                  <Alert
                    type="success"
                    showIcon
                    title={`Updated fresh market fields for ${freshFieldResult.refreshedCount} rows.`}
                    description={
                      freshFieldResult.refreshedOn
                        ? `Latest market-data date applied: ${freshFieldResult.refreshedOn}`
                        : undefined
                    }
                  />
                ) : null}
              </Space>
            </Card>
          </Col>

          <Col xs={24} xl={8}>
            <Card title="Current screen purpose">
              <Space orientation="vertical" size={12} style={{ width: "100%" }}>
                <Card size="small">
                  <Typography.Text strong>Step 1</Typography.Text>
                  <br />
                  <Typography.Text type="secondary">Collect Phase 1 quiet-setup candidates from Chartink.</Typography.Text>
                </Card>
                <Card size="small">
                  <Typography.Text strong>Step 2</Typography.Text>
                  <br />
                  <Typography.Text type="secondary">
                    Resolve each symbol into a tradable Kite instrument token and keep one latest row per symbol.
                  </Typography.Text>
                </Card>
                <Card size="small">
                  <Typography.Text strong>Step 3</Typography.Text>
                  <br />
                  <Typography.Text type="secondary">
                    Re-run delivery validation on the same watchlist and compare the full list against conviction names.
                  </Typography.Text>
                </Card>
              </Space>
            </Card>
          </Col>
        </Row>

        <Card title="Phase 1 watchlist with Phase 2 delivery validation">
          <Space orientation="vertical" size={16} style={{ width: "100%" }}>
            <Typography.Text type="secondary">
              Tab 1 keeps every Phase 1 stock visible. Tab 2 narrows the same columns down to names with confirmed
              delivery conviction.
            </Typography.Text>

            {freshnessSummary ? (
              <Alert
                type={freshnessSummary.type}
                showIcon
                title={freshnessSummary.message}
                description={freshnessSummary.description}
              />
            ) : null}

            <Space wrap size={12} style={{ width: "100%", justifyContent: "space-between" }}>
              <Input
                allowClear
                prefix={<SearchOutlined />}
                placeholder="Search symbol, sector, delivery reason"
                value={searchQuery}
                onChange={(event) => setSearchQuery(event.target.value)}
                style={{ width: 320 }}
              />

              <Space size={8}>
                <Button type={quickFilter === "all" ? "primary" : "default"} size="small" onClick={() => setQuickFilter("all")}>
                  All
                </Button>
                <Button
                  type={quickFilter === "resolved" ? "primary" : "default"}
                  size="small"
                  onClick={() => setQuickFilter("resolved")}
                >
                  Resolved
                </Button>
                <Button
                  type={quickFilter === "unresolved" ? "primary" : "default"}
                  size="small"
                  onClick={() => setQuickFilter("unresolved")}
                >
                  Unresolved
                </Button>
              </Space>
            </Space>

            <Tabs
              activeKey={activeTab}
              items={tabItems}
              onChange={(key) => setActiveTab(key as WatchlistTab)}
            />

            {loading && watchlist.length === 0 ? (
              <div style={{ display: "flex", justifyContent: "center", padding: "48px 0" }}>
                <Spin size="large" />
              </div>
            ) : null}

            {!loading && filteredRows.length === 0 ? (
              <Empty
                description={
                  watchlist.length === 0
                    ? "No candidates stored yet. Upload a Chartink file to begin."
                    : "No rows match the current tab or filter."
                }
              />
            ) : null}

            {filteredRows.length > 0 ? (
              <Table<PhaseCWatchlistRow>
                rowKey={(row) => row.symbol}
                columns={columns}
                dataSource={filteredRows}
                size="small"
                pagination={{ pageSize: 25, showSizeChanger: true }}
                scroll={{ x: 1860 }}
              />
            ) : null}
          </Space>
        </Card>
      </Space>
    </div>
  );
}
