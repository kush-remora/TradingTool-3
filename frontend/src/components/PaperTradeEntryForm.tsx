import { AutoComplete, Button, Input, Spin, Typography, message } from "antd";
import { useMemo, useState } from "react";
import { useInstrumentSearch } from "../hooks/useInstrumentSearch";
import type { CreateTradeInput, InstrumentSearchResult } from "../types";

const { Text } = Typography;

interface PaperTradeEntryFormProps {
  onSubmit: (payload: CreateTradeInput) => Promise<void>;
  loading?: boolean;
  initialInstrument?: InstrumentSearchResult | null;
  initialEntryPrice?: string;
}

interface SelectedInstrument {
  instrument: InstrumentSearchResult;
  label: string;
  searchText: string;
  value: string;
}

export function PaperTradeEntryForm({
  onSubmit,
  loading = false,
  initialInstrument = null,
  initialEntryPrice = "",
}: PaperTradeEntryFormProps) {
  const { allInstruments, loading: instrumentsLoading } = useInstrumentSearch();
  const [selectedInstrument, setSelectedInstrument] = useState<InstrumentSearchResult | null>(initialInstrument);
  const [symbolInput, setSymbolInput] = useState(initialInstrument?.trading_symbol ?? "");
  const [entryPrice, setEntryPrice] = useState(initialEntryPrice);

  const instrumentOptions = useMemo<SelectedInstrument[]>(
    () => allInstruments
      .filter((instrument) => instrument.instrument_type === "EQ")
      .map((instrument) => ({
        value: instrument.trading_symbol,
        label: instrument.trading_symbol + " — " + instrument.company_name,
        searchText: (instrument.trading_symbol + " " + instrument.company_name).toLowerCase(),
        instrument,
      })),
    [allInstruments],
  );

  const resetForm = (): void => {
    setSelectedInstrument(null);
    setSymbolInput("");
    setEntryPrice("");
  };

  const handleSubmit = async (): Promise<void> => {
    const parsedPrice = Number.parseFloat(entryPrice);
    if (!selectedInstrument) {
      message.error("Select a stock");
      return;
    }
    if (!Number.isFinite(parsedPrice) || parsedPrice <= 0) {
      message.error("Enter a valid entry price");
      return;
    }

    await onSubmit({
      instrument_token: selectedInstrument.instrument_token,
      company_name: selectedInstrument.company_name,
      exchange: selectedInstrument.exchange,
      nse_symbol: selectedInstrument.trading_symbol,
      quantity: 1,
      avg_buy_price: entryPrice,
      stop_loss_percent: "5",
    });
    resetForm();
    message.success(selectedInstrument.trading_symbol + " added to Trade Book");
  };

  return (
    <div className="paper-trade-entry-form">
      <div className="paper-trade-form-fields">
        <label className="paper-trade-field">
          <span>Stock</span>
          {instrumentsLoading ? (
            <Spin size="small" />
          ) : (
            <AutoComplete
              options={instrumentOptions}
              value={symbolInput}
              onChange={(value) => {
                setSymbolInput(value);
                if (selectedInstrument?.trading_symbol !== value) setSelectedInstrument(null);
              }}
              onSelect={(_value, option) => {
                const selected = (option as SelectedInstrument).instrument;
                setSelectedInstrument(selected);
                setSymbolInput(selected.trading_symbol);
              }}
              allowClear
              placeholder="Search NSE stock"
              filterOption={(inputValue, option) =>
                (option as SelectedInstrument).searchText.includes(inputValue.toLowerCase())
              }
            />
          )}
        </label>
        <label className="paper-trade-field">
          <span>Entry price</span>
          <Input
            prefix="₹"
            inputMode="decimal"
            placeholder="Price paid"
            value={entryPrice}
            onChange={(event) => setEntryPrice(event.target.value)}
            onPressEnter={() => void handleSubmit()}
          />
        </label>
      </div>
      <div className="paper-trade-form-footer">
        <Text type="secondary">1 share · 5% default stop · today</Text>
        <Button type="primary" loading={loading} onClick={() => void handleSubmit()}>
          Add position
        </Button>
      </div>
    </div>
  );
}
