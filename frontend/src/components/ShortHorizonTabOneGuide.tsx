import { BookOutlined } from "@ant-design/icons";
import { Alert, Button, Modal, Space, Spin, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useState } from "react";
import type { ShortHorizonGuideColumn, ShortHorizonTabOneGuide } from "../types";
import { getJson } from "../utils/api";

const { Text } = Typography;

const columns: ColumnsType<ShortHorizonGuideColumn> = [
  { title: "Column", dataIndex: "column", key: "column", width: 155 },
  { title: "What it shows", dataIndex: "whatItShows", key: "whatItShows", width: 260 },
  { title: "Why it matters", dataIndex: "whyImportant", key: "whyImportant", width: 260 },
  { title: "How to read", dataIndex: "howToRead", key: "howToRead", width: 310 },
  { title: "Caution", dataIndex: "caution", key: "caution", width: 290 },
];

export function ShortHorizonTabOneGuide() {
  const [guide, setGuide] = useState<ShortHorizonTabOneGuide | null>(null);
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const openGuide = (): void => {
    setOpen(true);
    setLoading(true);
    setError(null);
    void getJson<ShortHorizonTabOneGuide>("/api/strategy/short-horizon-selector/tab-one-guide", { useCache: false })
      .then(setGuide)
      .catch((requestError: unknown) => {
        setError(requestError instanceof Error ? requestError.message : "Failed to load the Tab 1 guide.");
      })
      .finally(() => setLoading(false));
  };

  return (
    <>
      <Button aria-label="How to read Tab 1" icon={<BookOutlined />} onClick={openGuide}>How to read Tab 1</Button>
      <Modal
        title={guide?.title ?? "How to read Tab 1"}
        open={open}
        onCancel={() => setOpen(false)}
        footer={null}
        width={1180}
      >
        {loading && <Space><Spin size="small" /><Text type="secondary">Loading guide…</Text></Space>}
        {error && <Alert type="error" message={error} showIcon />}
        {guide && !loading && (
          <Space orientation="vertical" size={12} style={{ width: "100%" }}>
            <Text type="secondary">{guide.description}</Text>
            <Text><strong>Reading order:</strong> {guide.readingOrder.join(" → ")}</Text>
            <Table<ShortHorizonGuideColumn>
              size="small"
              pagination={false}
              scroll={{ x: 1275 }}
              rowKey="column"
              columns={columns}
              dataSource={guide.columns}
            />
            <Text><strong>Best combination:</strong> {guide.bestCombination}</Text>
            <Text type="secondary"><strong>Important:</strong> {guide.importantNote}</Text>
          </Space>
        )}
      </Modal>
    </>
  );
}
