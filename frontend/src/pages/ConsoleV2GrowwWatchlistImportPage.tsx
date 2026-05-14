import { Alert, Button, Card, Space, Typography, Upload } from "antd";
import type { UploadFile } from "antd/es/upload/interface";
import { useMemo, useState } from "react";
import { useStocks } from "../hooks/useStocks";
import { postFormData } from "../utils/api";

const { Text, Title } = Typography;

interface GrowwWatchlistImportRowSkip {
  symbol: string;
  reason: string;
}

interface GrowwWatchlistImportResponse {
  fetchedCount: number;
  syncedCount: number;
  skippedCount: number;
  skippedSample: GrowwWatchlistImportRowSkip[];
}

export function ConsoleV2GrowwWatchlistImportPage() {
  const { refetch: refetchStocks } = useStocks();
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<GrowwWatchlistImportResponse | null>(null);

  const selectedFile = useMemo(() => {
    const file = fileList[0]?.originFileObj;
    return file ?? null;
  }, [fileList]);

  const handleSubmit = async () => {
    setError(null);
    setResult(null);

    if (!selectedFile) {
      setError("Please select a Groww watchlist JSON file first.");
      return;
    }

    const formData = new FormData();
    formData.append("file", selectedFile);

    setSubmitting(true);
    try {
      const payload = await postFormData<GrowwWatchlistImportResponse>(
        "/api/console/v2/groww/watchlist/import",
        formData,
      );
      setResult(payload);
      await refetchStocks();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Import failed");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{ padding: "16px 24px" }}>
      <Space direction="vertical" size={16} style={{ width: "100%", maxWidth: 900 }}>
        <div>
          <Title level={3} style={{ margin: 0 }}>
            Console V2 · Groww Watchlist Import
          </Title>
          <Text type="secondary">
            Upload your Groww watchlist JSON export. We’ll parse NSE stocks and upsert them into the existing{" "}
            <Text code>stocks</Text> table.
          </Text>
        </div>

        <Card>
          <Space direction="vertical" style={{ width: "100%" }} size={12}>
            <Upload.Dragger
              accept=".json,application/json"
              multiple={false}
              maxCount={1}
              fileList={fileList}
              beforeUpload={() => false}
              onChange={(info) => {
                setFileList(info.fileList.slice(-1));
                setError(null);
                setResult(null);
              }}
              onRemove={() => {
                setFileList([]);
                setError(null);
                setResult(null);
              }}
            >
              <p style={{ marginBottom: 8, fontWeight: 600 }}>Drop JSON here or click to select</p>
              <p style={{ margin: 0, color: "#8c8c8c" }}>
                File is sent to backend for parsing. No cron job required.
              </p>
            </Upload.Dragger>

            <Space>
              <Button
                type="primary"
                onClick={handleSubmit}
                loading={submitting}
                disabled={!selectedFile}
              >
                Import into Stocks
              </Button>
              <Button
                onClick={() => {
                  setFileList([]);
                  setError(null);
                  setResult(null);
                }}
                disabled={submitting}
              >
                Clear
              </Button>
            </Space>
          </Space>
        </Card>

        {error && <Alert type="error" showIcon message={error} />}

        {result && (
          <Card title="Import Result">
            <Space direction="vertical" style={{ width: "100%" }}>
              <Text>
                <b>Fetched</b>: {result.fetchedCount} | <b>Upserted</b>: {result.syncedCount} |{" "}
                <b>Skipped</b>: {result.skippedCount}
              </Text>

              {result.skippedCount > 0 && (
                <Alert
                  type="warning"
                  showIcon
                  message={`Skipped ${result.skippedCount} rows (showing up to ${result.skippedSample.length}).`}
                  description={
                    <div style={{ marginTop: 8 }}>
                      {result.skippedSample.map((row) => (
                        <div key={row.symbol}>
                          <Text code>{row.symbol}</Text> — <Text type="secondary">{row.reason}</Text>
                        </div>
                      ))}
                    </div>
                  }
                />
              )}
            </Space>
          </Card>
        )}
      </Space>
    </div>
  );
}

