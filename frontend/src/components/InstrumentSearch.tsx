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
    value: String(inst.instrumentToken),
    label: (
      <span>
        <strong>{inst.tradingSymbol}</strong>
        <span style={{ color: "#888", fontSize: 11, marginLeft: 6 }}>
          {inst.companyName || inst.exchange}
        </span>
      </span>
    ),
    instrument: inst,
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
        instrument_token: selected.instrumentToken,
        company_name: selected.companyName,
        exchange: selected.exchange,
      }).catch(async () => {
        // Stock already exists — fetch by symbol
        const { getJson } = await import("../utils/api");
        return getJson<Stock>(`/api/watchlist/stocks/by-symbol/${selected.tradingSymbol}`);
      });

      if (!existingStockIds.has(stock.id)) {
        await postJson("/api/watchlist/items", {
          watchlist_id: watchlistId,
          stock_id: stock.id,
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
        allowClear
        placeholder="Search eg: infy, reliance..."
        size="small"
        notFoundContent={availableInstruments.length === 0 ? "All stocks already added" : null}
        filterOption={(inputValue, option) =>
          option?.label?.toString().toLowerCase().includes(inputValue.toLowerCase())
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
