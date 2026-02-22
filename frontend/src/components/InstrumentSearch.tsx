import { Button, Space, Spin } from "antd";
import { useState } from "react";
import { useInstrumentSearch } from "../hooks/useInstrumentSearch";
import { postJson } from "../utils/api";
import type { InstrumentSearchResult, Stock } from "../types";
import { AutoComplete } from "antd";

interface Props {
  watchlistId: number;
  existingStockIds: Set<number>;
  onStockAdded: (stock: Stock) => void;
}

export function InstrumentSearch({ watchlistId, existingStockIds, onStockAdded }: Props) {
  const { allInstruments, loading, error } = useInstrumentSearch();
  const [selected, setSelected] = useState<InstrumentSearchResult | null>(null);
  const [adding, setAdding] = useState(false);

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
    (i) => i.instrumentType === "EQ" && !existingStockIds.has(i.instrumentToken),
  );

  const options = availableInstruments.map((inst) => ({
    value: inst.tradingSymbol,
    label: (
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", width: "100%" }}>
        <strong style={{ fontSize: 13 }}>{inst.tradingSymbol}</strong>
        {inst.companyName && (
          <span style={{ color: "#999", fontSize: 10, marginLeft: 8, textAlign: "right" }}>
            {inst.companyName}
          </span>
        )}
      </div>
    ),
    instrument: inst,
    // For client-side search/filtering (search by symbol, company name, or exchange)
    searchText: `${inst.tradingSymbol} ${inst.companyName} ${inst.exchange}`.toLowerCase(),
  }));

  const handleSelect = (_: string, option: (typeof options)[0]) => {
    setSelected(option.instrument);
  };

  const handleAdd = async () => {
    if (!selected) return;
    setAdding(true);
    try {
      // Upsert stock (create if not exists, or fetch existing)
      const stock = await postJson<Stock>("/api/watchlist/stocks", {
        symbol: selected.tradingSymbol,
        instrumentToken: selected.instrumentToken,
        companyName: selected.companyName,
        exchange: selected.exchange,
      }).catch(async () => {
        // Stock already exists — fetch by symbol
        const { getJson } = await import("../utils/api");
        return getJson<Stock>(`/api/watchlist/stocks/by-symbol/${selected.tradingSymbol}`);
      });

      if (!existingStockIds.has(stock.id)) {
        await postJson("/api/watchlist/items", {
          watchlistId: watchlistId,
          stockId: stock.id,
        });
        onStockAdded(stock);
      }
      setSelected(null);
    } finally {
      setAdding(false);
    }
  };

  return (
    <Space.Compact style={{ width: "100%" }}>
      <AutoComplete
        style={{ flex: 1 }}
        options={options}
        onSelect={handleSelect}
        onClear={() => setSelected(null)}
        onPressEnter={() => void handleAdd()}
        allowClear
        placeholder="Search eg: infy, reliance..."
        size="small"
        notFoundContent={availableInstruments.length === 0 ? "All stocks already added" : null}
        filterOption={(inputValue, option) =>
          (option as any)?.searchText?.includes(inputValue.toLowerCase()) ?? false
        }
      />
      <Button
        type="primary"
        size="small"
        loading={adding}
        disabled={!selected}
        onClick={() => void handleAdd()}
      >
        Add
      </Button>
    </Space.Compact>
  );
}
