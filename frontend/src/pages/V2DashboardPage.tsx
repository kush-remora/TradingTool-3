import { Alert, Button, Card, Empty, Input, InputNumber, Space, Table, Typography, message } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useMemo, useRef, useState } from "react";
import { InstrumentSearch } from "../components/InstrumentSearch";
import { useProfitLookback } from "../hooks/useProfitLookback";
import { useInstrumentSearch } from "../hooks/useInstrumentSearch";
import type {
  InstrumentSearchResult,
  ProfitLookbackRequest,
  ProfitLookbackResponse,
  ProfitLookbackTargetResult,
} from "../types";

const defaultTargetsCsv = "5,10,15,20";

interface InputRow {
  id: string;
  instrument: InstrumentSearchResult | null;
  sellDate: string;
  sellOpenPrice: number | null;
  analysisError: string | null;
}

interface ResultRow {
  key: string;
  rowId: string;
  symbol: string;
  sellDate: string;
  sellOpenPrice: number;
  targetPercent: number;
  suggestedBuyDate: string | null;
  buyOpenPrice: number | null;
  daysBefore: number | null;
  returnPercent: number | null;
  maxDrawdownPercent: number | null;
  maxDrawdownDays: number | null;
  status: string;
}

interface AmbiguousMatch {
  token: string;
  candidates: string[];
}

interface BulkAddFeedback {
  addedCount: number;
  skippedUnmatched: string[];
  skippedAmbiguous: AmbiguousMatch[];
}

interface BulkMatchResult {
  matched: InstrumentSearchResult[];
  unmatched: string[];
  ambiguous: AmbiguousMatch[];
}

function toDateInputValue(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function parseTargetPercents(input: string): number[] {
  const values = input
    .split(",")
    .map((chunk) => Number(chunk.trim()))
    .filter((value) => Number.isFinite(value) && value > 0);

  return Array.from(new Set(values)).sort((left, right) => left - right);
}

function parseBulkTokens(input: string): string[] {
  return Array.from(
    new Set(
      input
        .split(",")
        .map((token) => token.trim())
        .filter((token) => token.length > 0),
    ),
  );
}

function normalizeForSearch(value: string): string {
  return value.trim().toLowerCase();
}

function formatPrice(value: number | null): string {
  if (value == null) {
    return "-";
  }
  return `₹${value.toFixed(2)}`;
}

function mapResultRows(rowId: string, payload: ProfitLookbackResponse): ResultRow[] {
  return payload.results.map((result: ProfitLookbackTargetResult) => ({
    key: `${rowId}-${result.targetPercent}`,
    rowId,
    symbol: payload.symbol,
    sellDate: payload.resolvedSellDate,
    sellOpenPrice: payload.sellOpenPrice,
    targetPercent: result.targetPercent,
    suggestedBuyDate: result.suggestedBuyDate,
    buyOpenPrice: result.buyOpenPrice,
    daysBefore: result.daysBefore,
    returnPercent: result.returnPercent,
    maxDrawdownPercent: result.maxDrawdownPercent,
    maxDrawdownDays: result.maxDrawdownDays,
    status: result.status,
  }));
}

function findBulkMatches(tokens: string[], instruments: InstrumentSearchResult[]): BulkMatchResult {
  const matched: InstrumentSearchResult[] = [];
  const unmatched: string[] = [];
  const ambiguous: AmbiguousMatch[] = [];

  tokens.forEach((token) => {
    const normalizedToken = normalizeForSearch(token);

    const exactSymbolMatches = instruments.filter(
      (inst) => normalizeForSearch(inst.trading_symbol) === normalizedToken,
    );

    if (exactSymbolMatches.length === 1) {
      matched.push(exactSymbolMatches[0]);
      return;
    }

    if (exactSymbolMatches.length > 1) {
      ambiguous.push({
        token,
        candidates: exactSymbolMatches.map((inst) => inst.trading_symbol).slice(0, 5),
      });
      return;
    }

    const exactCompanyMatches = instruments.filter(
      (inst) => normalizeForSearch(inst.company_name) === normalizedToken,
    );

    if (exactCompanyMatches.length === 1) {
      matched.push(exactCompanyMatches[0]);
      return;
    }

    if (exactCompanyMatches.length > 1) {
      ambiguous.push({
        token,
        candidates: exactCompanyMatches.map((inst) => inst.trading_symbol).slice(0, 5),
      });
      return;
    }

    const containsMatches = instruments.filter((inst) => {
      const symbol = normalizeForSearch(inst.trading_symbol);
      const company = normalizeForSearch(inst.company_name);
      return symbol.includes(normalizedToken) || company.includes(normalizedToken);
    });

    if (containsMatches.length === 1) {
      matched.push(containsMatches[0]);
      return;
    }

    if (containsMatches.length > 1) {
      ambiguous.push({
        token,
        candidates: containsMatches.map((inst) => inst.trading_symbol).slice(0, 5),
      });
      return;
    }

    unmatched.push(token);
  });

  return {
    matched: Array.from(new Map(matched.map((inst) => [inst.instrument_token, inst])).values()),
    unmatched,
    ambiguous,
  };
}

export function V2DashboardPage() {
  const [targetsCsv, setTargetsCsv] = useState<string>(defaultTargetsCsv);
  const [lookbackDays, setLookbackDays] = useState<number>(120);
  const [rows, setRows] = useState<InputRow[]>([]);
  const [results, setResults] = useState<ResultRow[]>([]);
  const [runningRowIds, setRunningRowIds] = useState<Set<string>>(new Set());
  const { loading, error, runProfitLookback, runProfitLookbackBulk } = useProfitLookback();
  const { allInstruments } = useInstrumentSearch();
  const [messageApi, contextHolder] = message.useMessage();
  const nextIdRef = useRef<number>(1);

  const [bulkSymbolsText, setBulkSymbolsText] = useState<string>("");
  const [bulkGlobalDate, setBulkGlobalDate] = useState<string>(toDateInputValue(new Date()));
  const [bulkFeedback, setBulkFeedback] = useState<BulkAddFeedback | null>(null);
  const [resultSearchQuery, setResultSearchQuery] = useState<string>("");

  const parsedTargets = useMemo(() => parseTargetPercents(targetsCsv), [targetsCsv]);
  const hasTargetValidationError = parsedTargets.length === 0;

  const eqInstruments = useMemo(
    () => allInstruments.filter((inst) => inst.instrument_type === "EQ"),
    [allInstruments],
  );

  const targetFilters = useMemo(
    () =>
      Array.from(new Set(results.map((row) => row.targetPercent)))
        .sort((left, right) => left - right)
        .map((value) => ({
          text: `${value.toFixed(2)}%`,
          value: String(value),
        })),
    [results],
  );

  const statusFilters = useMemo(
    () =>
      Array.from(new Set(results.map((row) => row.status)))
        .sort()
        .map((value) => ({
          text: value,
          value,
        })),
    [results],
  );

  const filteredResults = useMemo(() => {
    const normalizedQuery = normalizeForSearch(resultSearchQuery);
    if (!normalizedQuery) {
      return results;
    }

    return results.filter((row) => {
      const fieldsToSearch = [
        row.symbol,
        row.sellDate,
        row.suggestedBuyDate ?? "",
        row.status,
        String(row.targetPercent),
        row.sellOpenPrice.toFixed(2),
        row.buyOpenPrice?.toFixed(2) ?? "",
        row.returnPercent?.toFixed(2) ?? "",
        row.daysBefore == null ? "" : String(row.daysBefore),
        row.maxDrawdownPercent?.toFixed(2) ?? "",
        row.maxDrawdownDays == null ? "" : String(row.maxDrawdownDays),
      ];

      return fieldsToSearch.some((field) => normalizeForSearch(field).includes(normalizedQuery));
    });
  }, [resultSearchQuery, results]);

  const createRow = (instrument: InstrumentSearchResult | null, sellDate: string): InputRow => {
    const nextId = `row-${nextIdRef.current}`;
    nextIdRef.current += 1;
    return {
      id: nextId,
      instrument,
      sellDate,
      sellOpenPrice: null,
      analysisError: null,
    };
  };

  const addRow = (): void => {
    setRows((prev) => [...prev, createRow(null, toDateInputValue(new Date()))]);
  };

  const addBulkRows = (): void => {
    const tokens = parseBulkTokens(bulkSymbolsText);
    if (tokens.length === 0) {
      messageApi.warning("Enter at least one comma-separated token.");
      return;
    }

    if (!bulkGlobalDate) {
      messageApi.warning("Select a global sell date.");
      return;
    }

    const matchResult = findBulkMatches(tokens, eqInstruments);
    const newRows = matchResult.matched.map((instrument) => createRow(instrument, bulkGlobalDate));

    if (newRows.length > 0) {
      setRows((prev) => [...prev, ...newRows]);
    }

    setBulkFeedback({
      addedCount: newRows.length,
      skippedUnmatched: matchResult.unmatched,
      skippedAmbiguous: matchResult.ambiguous,
    });

    if (newRows.length === 0) {
      messageApi.warning("No rows added. Please refine your bulk symbols.");
    } else {
      messageApi.success(`${newRows.length} rows added from bulk input.`);
    }
  };

  const updateRow = (rowId: string, updater: (row: InputRow) => InputRow): void => {
    setRows((prev) => prev.map((row) => (row.id === rowId ? updater(row) : row)));
  };

  const removeRow = (rowId: string): void => {
    setRows((prev) => prev.filter((row) => row.id !== rowId));
    setResults((prev) => prev.filter((row) => row.rowId !== rowId));
    setRunningRowIds((prev) => {
      const next = new Set(prev);
      next.delete(rowId);
      return next;
    });
  };

  const analyzeRow = async (row: InputRow): Promise<void> => {
    if (hasTargetValidationError) {
      messageApi.error("Enter at least one valid positive target percentage.");
      return;
    }

    if (!row.instrument) {
      messageApi.error("Select a symbol before running analysis.");
      return;
    }

    if (!row.sellDate) {
      messageApi.error("Select a sell date before running analysis.");
      return;
    }

    const request: ProfitLookbackRequest = {
      symbol: row.instrument.trading_symbol,
      instrumentToken: row.instrument.instrument_token,
      sellDate: row.sellDate,
      lookbackDays,
      targetPercents: parsedTargets,
    };

    setRunningRowIds((prev) => new Set(prev).add(row.id));

    try {
      const response = await runProfitLookback(request);
      updateRow(row.id, (currentRow) => ({
        ...currentRow,
        sellOpenPrice: response.sellOpenPrice,
        analysisError: null,
      }));

      const mappedRows = mapResultRows(row.id, response);
      setResults((prev) => [...prev.filter((item) => item.rowId !== row.id), ...mappedRows]);
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : "Failed to run profit lookback analysis";
      updateRow(row.id, (currentRow) => ({
        ...currentRow,
        analysisError: errorMessage,
      }));
      messageApi.error(errorMessage);
    } finally {
      setRunningRowIds((prev) => {
        const next = new Set(prev);
        next.delete(row.id);
        return next;
      });
    }
  };

  const analyzeAllRows = async (): Promise<void> => {
    if (hasTargetValidationError) {
      messageApi.error("Enter at least one valid positive target percentage.");
      return;
    }

    const validRows = rows.filter((row) => row.instrument && row.sellDate);

    if (validRows.length === 0) {
      messageApi.info("No valid rows to analyze.");
      return;
    }

    setRunningRowIds(new Set(validRows.map((row) => row.id)));
    try {
      const response = await runProfitLookbackBulk({
        lookbackDays,
        targetPercents: parsedTargets,
        rows: validRows.map((row) => ({
          rowId: row.id,
          symbol: row.instrument!.trading_symbol,
          instrumentToken: row.instrument!.instrument_token,
          sellDate: row.sellDate,
        })),
      });

      const responseByRowId = new Map(response.rows.map((item) => [item.rowId, item]));
      setRows((prev) =>
        prev.map((row) => {
          const rowResponse = responseByRowId.get(row.id);
          if (!rowResponse) {
            return row;
          }
          if (rowResponse.ok && rowResponse.data) {
            return {
              ...row,
              sellOpenPrice: rowResponse.data.sellOpenPrice,
              analysisError: null,
            };
          }
          return {
            ...row,
            analysisError: rowResponse.error ?? "Bulk analysis failed",
          };
        }),
      );

      const rowsToReplace = new Set(response.rows.map((item) => item.rowId));
      const successfulResults = response.rows
        .filter((item) => item.ok && item.data)
        .flatMap((item) => mapResultRows(item.rowId, item.data!));
      setResults((prev) => [...prev.filter((item) => !rowsToReplace.has(item.rowId)), ...successfulResults]);

      const successCount = response.rows.filter((item) => item.ok).length;
      const failedCount = response.rows.length - successCount;
      if (failedCount > 0) {
        messageApi.warning(`Analyze All done. Success: ${successCount}, Failed: ${failedCount}.`);
      } else {
        messageApi.success(`Analyze All done. ${successCount} rows analyzed.`);
      }
    } catch (err) {
      messageApi.error(err instanceof Error ? err.message : "Failed to run bulk profit lookback analysis");
    } finally {
      setRunningRowIds(new Set());
    }
  };

  const exportResultsJson = (): void => {
    if (results.length === 0) {
      messageApi.info("No results available to export.");
      return;
    }

    const exportPayload = results.map((row) => ({
      symbol: row.symbol,
      sellDate: row.sellDate,
      sellOpenPrice: row.sellOpenPrice,
      targetPercent: row.targetPercent,
      suggestedBuyDate: row.suggestedBuyDate,
      buyOpenPrice: row.buyOpenPrice,
      daysBefore: row.daysBefore,
      returnPercent: row.returnPercent,
      maxDrawdownPercent: row.maxDrawdownPercent,
      maxDrawdownDays: row.maxDrawdownDays,
      status: row.status,
    }));

    const json = JSON.stringify(exportPayload, null, 2);
    const blob = new Blob([json], { type: "application/json" });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `v2-dashboard-results-${toDateInputValue(new Date())}.json`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(url);
  };

  const inputColumns: ColumnsType<InputRow> = [
    {
      title: "Symbol",
      key: "symbol",
      width: 280,
      render: (_, row) => (
        <InstrumentSearch
          onSelect={(instrument) => {
            updateRow(row.id, (currentRow) => ({
              ...currentRow,
              instrument,
              sellOpenPrice: null,
              analysisError: null,
            }));
            setResults((prev) => prev.filter((item) => item.rowId !== row.id));
          }}
          value={row.instrument}
          placeholder="Search symbol"
          instruments={eqInstruments}
        />
      ),
    },
    {
      title: "Company Name",
      key: "companyName",
      width: 260,
      render: (_, row) => row.instrument?.company_name ?? "-",
    },
    {
      title: "Sell Date",
      dataIndex: "sellDate",
      key: "sellDate",
      width: 160,
      render: (value: string, row) => (
        <input
          type="date"
          value={value}
          onChange={(event) => {
            const nextDate = event.target.value;
            updateRow(row.id, (currentRow) => ({
              ...currentRow,
              sellDate: nextDate,
              sellOpenPrice: null,
              analysisError: null,
            }));
            setResults((prev) => prev.filter((item) => item.rowId !== row.id));
          }}
        />
      ),
    },
    {
      title: "Sell Open",
      dataIndex: "sellOpenPrice",
      key: "sellOpenPrice",
      width: 140,
      render: (value: number | null) => <Typography.Text>{formatPrice(value)}</Typography.Text>,
    },
    {
      title: "Action",
      key: "action",
      width: 130,
      render: (_, row) => (
        <Space orientation="vertical" size={4}>
          <Button
            size="small"
            type="primary"
            onClick={() => void analyzeRow(row)}
            loading={runningRowIds.has(row.id)}
          >
            Analyze
          </Button>
          {row.analysisError ? (
            <Typography.Text type="danger" style={{ fontSize: 11 }}>
              {row.analysisError}
            </Typography.Text>
          ) : null}
        </Space>
      ),
    },
    {
      title: "Remove",
      key: "remove",
      width: 90,
      render: (_, row) => (
        <Button size="small" danger onClick={() => removeRow(row.id)}>
          Remove
        </Button>
      ),
    },
  ];

  const resultColumns: ColumnsType<ResultRow> = useMemo(
    () => [
      { title: "Symbol", dataIndex: "symbol", key: "symbol", width: 100 },
      { title: "Sell Date", dataIndex: "sellDate", key: "sellDate", width: 120 },
      {
        title: "Sell Open",
        dataIndex: "sellOpenPrice",
        key: "sellOpenPrice",
        width: 120,
        render: (value: number) => formatPrice(value),
      },
      {
        title: "Target %",
        dataIndex: "targetPercent",
        key: "targetPercent",
        width: 100,
        render: (value: number) => `${value.toFixed(2)}%`,
        filters: targetFilters,
        onFilter: (value, row) => String(row.targetPercent) === String(value),
      },
      {
        title: "Suggested Buy Date",
        dataIndex: "suggestedBuyDate",
        key: "suggestedBuyDate",
        width: 150,
        render: (value: string | null) => value ?? "-",
      },
      {
        title: "Buy Open",
        dataIndex: "buyOpenPrice",
        key: "buyOpenPrice",
        width: 120,
        render: (value: number | null) => formatPrice(value),
      },
      {
        title: "Days Before",
        dataIndex: "daysBefore",
        key: "daysBefore",
        width: 110,
        render: (value: number | null) => (value == null ? "-" : String(value)),
      },
      {
        title: "Return %",
        dataIndex: "returnPercent",
        key: "returnPercent",
        width: 100,
        render: (value: number | null) => (value == null ? "-" : `${value.toFixed(2)}%`),
      },
      {
        title: "Max DD %",
        dataIndex: "maxDrawdownPercent",
        key: "maxDrawdownPercent",
        width: 110,
        render: (value: number | null) => (value == null ? "-" : `${value.toFixed(2)}%`),
      },
      {
        title: "DD Days",
        dataIndex: "maxDrawdownDays",
        key: "maxDrawdownDays",
        width: 90,
        render: (value: number | null) => (value == null ? "-" : String(value)),
      },
      {
        title: "Status",
        dataIndex: "status",
        key: "status",
        width: 160,
        filters: statusFilters,
        onFilter: (value, row) => row.status === value,
      },
    ],
    [statusFilters, targetFilters],
  );

  return (
    <div style={{ padding: 24, background: "#f5f7fa", minHeight: "calc(100vh - 48px)" }}>
      {contextHolder}
      <Space orientation="vertical" size={16} style={{ width: "100%" }}>
        <div>
          <Typography.Title level={4} style={{ margin: 0 }}>V2 Dashboard</Typography.Title>
          <Typography.Text type="secondary">
            Find the latest buy date in the lookback window that can achieve each target profit on the selected sell date (Open-to-Open).
          </Typography.Text>
        </div>

        <Card size="small" title="Bulk Add">
          <Space orientation="vertical" size={8} style={{ width: "100%" }}>
            <Typography.Text strong>Bulk symbols (comma separated)</Typography.Text>
            <Input.TextArea
              value={bulkSymbolsText}
              onChange={(event) => setBulkSymbolsText(event.target.value)}
              placeholder="INFY, Reliance, HDFC Bank"
              rows={3}
            />
            <Space wrap align="center">
              <Typography.Text strong>Global sell date:</Typography.Text>
              <input
                type="date"
                value={bulkGlobalDate}
                onChange={(event) => setBulkGlobalDate(event.target.value)}
              />
              <Button type="primary" size="small" onClick={addBulkRows}>Add Bulk Rows</Button>
            </Space>
            {bulkFeedback ? (
              <Alert
                type="info"
                showIcon
                title={`Added: ${bulkFeedback.addedCount} | Unmatched: ${bulkFeedback.skippedUnmatched.length} | Ambiguous: ${bulkFeedback.skippedAmbiguous.length}`}
                description={
                  <div>
                    {bulkFeedback.skippedUnmatched.length > 0 ? (
                      <div>Unmatched: {bulkFeedback.skippedUnmatched.join(", ")}</div>
                    ) : null}
                    {bulkFeedback.skippedAmbiguous.length > 0 ? (
                      <div>
                        Ambiguous: {bulkFeedback.skippedAmbiguous.map((item) => `${item.token} (${item.candidates.join("/")})`).join(", ")}
                      </div>
                    ) : null}
                  </div>
                }
              />
            ) : null}
          </Space>
        </Card>

        <Card size="small" title="Controls">
          <Space wrap align="center">
            <Typography.Text strong>Target % list:</Typography.Text>
            <Input
              value={targetsCsv}
              onChange={(event) => setTargetsCsv(event.target.value)}
              placeholder="5,10,15,20"
              style={{ width: 240 }}
              size="small"
            />
            <Typography.Text strong>Lookback days:</Typography.Text>
            <InputNumber
              min={1}
              max={1000}
              value={lookbackDays}
              onChange={(value) => setLookbackDays(value ?? 120)}
              size="small"
            />
            <Button size="small" type="dashed" onClick={addRow}>Add Row</Button>
            <Button
              size="small"
              type="primary"
              onClick={() => void analyzeAllRows()}
              loading={runningRowIds.size > 0}
            >
              Analyze All
            </Button>
          </Space>
          {hasTargetValidationError ? (
            <Alert
              type="warning"
              showIcon
              style={{ marginTop: 12 }}
              title="Enter at least one valid positive target percentage (CSV)."
            />
          ) : null}
        </Card>

        {error ? <Alert type="error" showIcon title={error} /> : null}

        <Card size="small" title="Input Table">
          {rows.length === 0 ? (
            <Empty description="No rows yet. Click Add Row to start." image={Empty.PRESENTED_IMAGE_SIMPLE} />
          ) : (
            <Table<InputRow>
              size="small"
              columns={inputColumns}
              dataSource={rows}
              rowKey="id"
              pagination={false}
              scroll={{ x: 1120 }}
              virtual={rows.length > 40}
            />
          )}
        </Card>

        <Card
          size="small"
          title="Results Table"
          extra={
            <Space size={8}>
              <Input
                size="small"
                placeholder="Search results"
                value={resultSearchQuery}
                onChange={(event) => setResultSearchQuery(event.target.value)}
                allowClear
                style={{ width: 200 }}
              />
              <Button size="small" onClick={exportResultsJson}>Export Results JSON</Button>
            </Space>
          }
        >
          {results.length === 0 ? (
            <Empty description="Run analysis for a row to see results." image={Empty.PRESENTED_IMAGE_SIMPLE} />
          ) : (
            <Table<ResultRow>
              size="small"
              columns={resultColumns}
              dataSource={filteredResults}
              rowKey="key"
              pagination={{ pageSize: 100, showSizeChanger: true }}
              scroll={{ x: 1100 }}
              loading={loading}
              virtual={results.length > 200}
            />
          )}
        </Card>
      </Space>
    </div>
  );
}
