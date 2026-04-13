import { Alert, Button, Card, DatePicker, Empty, Space, Spin, Typography } from "antd";
import dayjs from "dayjs";
import { useState } from "react";
import { useRsiMomentumHistory } from "../../hooks/useRsiMomentumHistory";
import { RsiMomentumBoardPanel } from "./RsiBoard";

export function HistoryTab({ profileId }: { profileId: string }) {
  const { data, loading, error, fetchByDate } = useRsiMomentumHistory();
  const [selectedDate, setSelectedDate] = useState<dayjs.Dayjs | null>(null);

  const handleFetch = () => {
    if (selectedDate) {
      void fetchByDate(profileId, selectedDate.format("YYYY-MM-DD"));
    }
  };

  const snapshot = data?.snapshot;
  
  // Calculate thresholds if snapshot exists
  const watchThresholdPct = snapshot 
    ? (snapshot.config.maxExtensionAboveSma20ForNewEntryPct ?? (snapshot.config.maxExtensionAboveSma20ForNewEntry * 100))
    : 0;
  const skipThresholdPct = snapshot
    ? (snapshot.config.maxExtensionAboveSma20ForSkipNewEntryPct ?? (snapshot.config.maxExtensionAboveSma20ForSkipNewEntry * 100))
    : 0;

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <Card size="small">
        <Space wrap>
          <Typography.Text>Select Date:</Typography.Text>
          <DatePicker 
            value={selectedDate} 
            onChange={setSelectedDate} 
            format="YYYY-MM-DD"
          />
          <Button 
            type="primary" 
            onClick={handleFetch} 
            loading={loading}
            disabled={!selectedDate}
          >
            Load Snapshot
          </Button>
        </Space>
      </Card>

      {error && <Alert type="error" showIcon message={error} />}

      {loading && (
        <div style={{ textAlign: "center", padding: 32 }}>
          <Spin tip="Loading historical snapshot..." />
        </div>
      )}

      {snapshot ? (
        <RsiMomentumBoardPanel 
          snapshot={snapshot} 
          watchThresholdPct={watchThresholdPct} 
          skipThresholdPct={skipThresholdPct} 
        />
      ) : (
        !loading && !error && (
          <Card size="small">
            <Empty description="Select a date and click Load Snapshot to view historical rankings." />
          </Card>
        )
      )}
    </Space>
  );
}
