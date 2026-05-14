import { Spin, AutoComplete } from "antd";
import { memo, useDeferredValue, useEffect, useMemo, useState } from "react";
import { useInstrumentSearch } from "../hooks/useInstrumentSearch";
import type { InstrumentSearchResult } from "../types";

interface Props {
  existingStockTokens?: Set<number>;
  onSelect: (instrument: InstrumentSearchResult | null) => void;
  value?: InstrumentSearchResult | null;
  placeholder?: string;
  instruments?: InstrumentSearchResult[];
  maxOptions?: number;
}

const DEFAULT_MAX_OPTIONS = 50;

export const InstrumentSearch = memo(function InstrumentSearch({
  existingStockTokens = new Set(),
  onSelect,
  value,
  placeholder,
  instruments,
  maxOptions = DEFAULT_MAX_OPTIONS,
}: Props) {
  if (instruments != null) {
    return (
      <InstrumentSearchCore
        existingStockTokens={existingStockTokens}
        onSelect={onSelect}
        value={value ?? null}
        placeholder={placeholder ?? "Search eg: infy, reliance..."}
        instruments={instruments}
        loading={false}
        error={null}
        maxOptions={maxOptions}
      />
    );
  }

  return (
    <InstrumentSearchWithHook
      existingStockTokens={existingStockTokens}
      onSelect={onSelect}
      value={value ?? null}
      placeholder={placeholder ?? "Search eg: infy, reliance..."}
      maxOptions={maxOptions}
    />
  );
});

function InstrumentSearchWithHook({
  existingStockTokens,
  onSelect,
  value,
  placeholder,
  maxOptions,
}: Required<Pick<Props, "existingStockTokens" | "onSelect" | "value" | "placeholder" | "maxOptions">>) {
  const { allInstruments, loading, error } = useInstrumentSearch();
  return (
    <InstrumentSearchCore
      existingStockTokens={existingStockTokens}
      onSelect={onSelect}
      value={value}
      placeholder={placeholder}
      instruments={allInstruments}
      loading={loading}
      error={error}
      maxOptions={maxOptions}
    />
  );
}

interface CoreProps {
  existingStockTokens: Set<number>;
  onSelect: (instrument: InstrumentSearchResult | null) => void;
  value: InstrumentSearchResult | null;
  placeholder: string;
  instruments: InstrumentSearchResult[];
  loading: boolean;
  error: string | null;
  maxOptions: number;
}

function InstrumentSearchCore({
  existingStockTokens,
  onSelect,
  value,
  placeholder,
  instruments,
  loading,
  error,
  maxOptions,
}: CoreProps) {
  const [inputValue, setInputValue] = useState<string>(value?.trading_symbol ?? "");
  const deferredInputValue = useDeferredValue(inputValue);

  useEffect(() => {
    setInputValue(value?.trading_symbol ?? "");
  }, [value?.instrument_token, value?.trading_symbol]);

  const availableInstruments = useMemo(
    () =>
      instruments.filter(
        (item) => item.instrument_type === "EQ" && !existingStockTokens.has(item.instrument_token),
      ),
    [existingStockTokens, instruments],
  );

  const options = useMemo(() => {
    const query = deferredInputValue.trim().toLowerCase();
    const matches: InstrumentSearchResult[] = [];
    for (const instrument of availableInstruments) {
      if (query.length > 0) {
        const searchText = `${instrument.trading_symbol} ${instrument.company_name} ${instrument.exchange}`.toLowerCase();
        if (!searchText.includes(query)) {
          continue;
        }
      }
      matches.push(instrument);
      if (matches.length >= maxOptions) {
        break;
      }
    }

    return matches.map((inst) => ({
      value: inst.trading_symbol,
      label: `${inst.trading_symbol} - ${inst.company_name}`,
      instrument: inst,
    }));
  }, [availableInstruments, deferredInputValue, maxOptions]);

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

  return (
    <AutoComplete
      style={{ width: "100%" }}
      value={inputValue}
      options={options}
      onSelect={(_: string, option: (typeof options)[0]) => {
        setInputValue(option.instrument.trading_symbol);
        onSelect(option.instrument);
      }}
      onSearch={(nextValue) => setInputValue(nextValue)}
      onChange={(nextValue) => setInputValue(nextValue)}
      onClear={() => {
        setInputValue("");
        onSelect(null);
      }}
      allowClear
      placeholder={placeholder || "Search eg: infy, reliance..."}
      size="small"
      notFoundContent={availableInstruments.length === 0 ? "All stocks already added" : null}
      filterOption={false}
    />
  );
}
