import { AutoComplete, Button, Space } from "antd";
import { useState } from "react";
import { useInstrumentSearch } from "../hooks/useInstrumentSearch";
import { postJson } from "../utils/api";
import type { InstrumentSearchResult, Stock } from "../types";

interface Props {
  watchlistId: number;
  existingStockIds: Set<number>;
  onStockAdded: (stock: Stock) => void;
}

export function InstrumentSearch({ watchlistId, existingStockIds, onStockAdded }: Props) {
  const { results, searching, search, clearResults } = useInstrumentSearch();
  const [selected, setSelected] = useState<InstrumentSearchResult | null>(null);
  const [adding, setAdding] = useState(false);

  const options = results
    .filter((r) => {
      // Only show EQ instruments to keep the list clean
      return r.instrumentType === "EQ";
    })
    .map((r) => ({
      value: String(r.instrumentToken),
      label: (
        <span>
          <strong>{r.tradingSymbol}</strong>
          <span style={{ color: "#888", fontSize: 11, marginLeft: 6 }}>{r.companyName}</span>
        </span>
      ),
      instrument: r,
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
        // Stock already exists — fetch by instrument token
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
      clearResults();
    } finally {
      setAdding(false);
    }
  };

  return (
    <Space.Compact style={{ width: "100%" }}>
      <AutoComplete
        style={{ flex: 1 }}
        options={options}
        onSearch={(q) => search(q)}
        onSelect={handleSelect}
        onClear={() => { setSelected(null); clearResults(); }}
        allowClear
        placeholder="Search instrument (e.g. RELIANCE)"
        size="small"
        notFoundContent={searching ? "Searching..." : ""}
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
