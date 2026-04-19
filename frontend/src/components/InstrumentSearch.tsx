import { Spin, AutoComplete } from "antd";
import { useInstrumentSearch } from "../hooks/useInstrumentSearch";
import type { InstrumentSearchResult } from "../types";

interface Props {
  existingStockTokens?: Set<number>;
  onSelect: (instrument: InstrumentSearchResult | null) => void;
  value?: InstrumentSearchResult | null;
  placeholder?: string;
}

export function InstrumentSearch({ existingStockTokens = new Set(), onSelect, value, placeholder }: Props) {
  const { allInstruments, loading, error } = useInstrumentSearch();

  if (loading) {
    return <Spin size="small" style={{ display: "flex", justifyContent: "center", padding: "8px 0" }} />;
  }

  if (error) {
    return (
      <div style={{ fontSize: 12, color: "#ff4d4f", padding: "8px" }}>
        {error}
      </div>
    );
  }

  // Filter to EQ only (equities), exclude already-added stocks
  const availableInstruments = allInstruments.filter(
    (i) => i.instrument_type === "EQ" && !existingStockTokens.has(i.instrument_token),
  );

  const options = availableInstruments.map((inst) => ({
    value: inst.trading_symbol,
    label: inst.trading_symbol,
    instrument: inst,
    searchText: `${inst.trading_symbol} ${inst.company_name} ${inst.exchange}`.toLowerCase(),
  }));

  return (
    <AutoComplete
      style={{ width: "100%" }}
      options={options}
      onSelect={(_: string, option: (typeof options)[0]) => {
        onSelect(option.instrument);
      }}
      onClear={() => onSelect(null)}
      allowClear
      placeholder={placeholder || "Search eg: infy, reliance..."}
      size="small"
      notFoundContent={availableInstruments.length === 0 ? "All stocks already added" : null}
      filterOption={(inputValue, option) =>
        (option as any)?.searchText?.includes(inputValue.toLowerCase()) ?? false
      }
    />
  );
}
