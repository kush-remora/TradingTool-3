import { Button, Card, Collapse, Table } from "antd";
import type { ColumnsType } from "antd/es/table";
import { DownloadOutlined } from "@ant-design/icons";
import { SUMMARY_CONSOLE_COLUMN_GUIDE, type SummaryConsoleColumnGuideEntry } from "../utils/summaryConsoleExport";

interface SummaryConsoleGuideProps {
  onDownloadGuide: () => void;
}

const columns: ColumnsType<SummaryConsoleColumnGuideEntry> = [
  { title: "CSV column", dataIndex: "column", key: "column", width: 245 },
  { title: "Meaning", dataIndex: "meaning", key: "meaning", width: 320 },
  { title: "Designed to do", dataIndex: "designedToDo", key: "designedToDo", width: 440 },
];

export function SummaryConsoleGuide({ onDownloadGuide }: SummaryConsoleGuideProps) {
  return (
    <Card
      size="small"
      title="CSV guide for AI analysis"
      extra={<Button icon={<DownloadOutlined />} onClick={onDownloadGuide}>Download AI guide</Button>}
    >
      <Collapse
        defaultActiveKey={["guide"]}
        items={[{
          key: "guide",
          label: "How to interpret the exported data",
          children: (
            <Table<SummaryConsoleColumnGuideEntry>
              size="small"
              pagination={false}
              scroll={{ x: 1005 }}
              rowKey="column"
              columns={columns}
              dataSource={SUMMARY_CONSOLE_COLUMN_GUIDE}
            />
          ),
        }]}
      />
    </Card>
  );
}

