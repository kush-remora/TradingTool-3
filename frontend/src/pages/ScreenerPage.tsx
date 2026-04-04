import { useState } from "react";
import { ScreenerOverview } from "../components/ScreenerOverview";
import { ScreenerDetail } from "../components/ScreenerDetail";

export function ScreenerPage() {
  const [selectedSymbol, setSelectedSymbol] = useState<string | null>(null);

  if (selectedSymbol) {
    return (
      <ScreenerDetail
        symbol={selectedSymbol}
        onBack={() => setSelectedSymbol(null)}
      />
    );
  }

  return <ScreenerOverview onSelectSymbol={setSelectedSymbol} />;
}
