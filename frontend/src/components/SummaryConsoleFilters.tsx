import { ClearOutlined } from "@ant-design/icons";
import { Button, Card, Radio, Select, Space, Typography } from "antd";
import type {
  SummaryConsoleFilterMatch,
  SummaryConsoleFilterScope,
  SummaryConsoleSignalKey,
} from "../utils/summaryConsoleFiltering";
import { SUMMARY_CONSOLE_SIGNAL_OPTIONS } from "../utils/summaryConsoleFiltering";

const { Text } = Typography;

interface SummaryConsoleFiltersProps {
  selectedSignals: SummaryConsoleSignalKey[];
  match: SummaryConsoleFilterMatch;
  scope: SummaryConsoleFilterScope;
  lookbackSessions: number;
  onSignalsChange: (signals: SummaryConsoleSignalKey[]) => void;
  onMatchChange: (match: SummaryConsoleFilterMatch) => void;
  onScopeChange: (scope: SummaryConsoleFilterScope) => void;
  onClear: () => void;
}

export function SummaryConsoleFilters({
  selectedSignals,
  match,
  scope,
  lookbackSessions,
  onSignalsChange,
  onMatchChange,
  onScopeChange,
  onClear,
}: SummaryConsoleFiltersProps) {
  return (
    <Card
      size="small"
      title="Filter events"
      extra={(
        <Button
          type="text"
          size="small"
          icon={<ClearOutlined />}
          onClick={onClear}
          disabled={selectedSignals.length === 0 && match === "ANY" && scope === "SAME_SESSION"}
        >
          Clear
        </Button>
      )}
    >
      <Space orientation="vertical" size={8} style={{ width: "100%" }}>
        <Space wrap size={12}>
          <Text strong>Require signals</Text>
          <Select<SummaryConsoleSignalKey[]>
            aria-label="Summary Console signals"
            mode="multiple"
            allowClear
            value={selectedSignals}
            onChange={onSignalsChange}
            placeholder="Choose signals to narrow the rows"
            options={SUMMARY_CONSOLE_SIGNAL_OPTIONS.map((option) => ({ value: option.key, label: option.label }))}
            maxTagCount="responsive"
            style={{ minWidth: 360, maxWidth: "100%" }}
          />
        </Space>
        <Space wrap size={16}>
          <Space size={8}>
            <Text strong>Match</Text>
            <Radio.Group
              aria-label="Summary Console match mode"
              value={match}
              onChange={(event) => onMatchChange(event.target.value as SummaryConsoleFilterMatch)}
              optionType="button"
              buttonStyle="solid"
              options={[
                { label: "Any", value: "ANY" },
                { label: "All", value: "ALL" },
              ]}
            />
          </Space>
          <Space size={8}>
            <Text strong>Scope</Text>
            <Radio.Group
              aria-label="Summary Console filter scope"
              value={scope}
              onChange={(event) => onScopeChange(event.target.value as SummaryConsoleFilterScope)}
              optionType="button"
              buttonStyle="solid"
              options={[
                { label: "Same session", value: "SAME_SESSION" },
                { label: `Across ${lookbackSessions} sessions`, value: "WINDOW" },
              ]}
            />
          </Space>
        </Space>
        <Text type="secondary" style={{ fontSize: 12 }}>
          {selectedSignals.length === 0
            ? "Choose signals to narrow the event rows."
            : match === "ALL" && scope === "WINDOW"
              ? `All selected signals must occur for the stock somewhere across the last ${lookbackSessions} sessions; they may be on different dates.`
              : match === "ALL"
                ? "All selected signals must be true on the same session row."
                : scope === "WINDOW"
                  ? `A stock is shown when any selected signal occurred somewhere across the last ${lookbackSessions} sessions.`
                  : "A row is shown when any selected signal is true on that session."
          }
        </Text>
      </Space>
    </Card>
  );
}
