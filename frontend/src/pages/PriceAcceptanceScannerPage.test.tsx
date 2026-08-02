import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { PriceAcceptanceScannerPage } from "./PriceAcceptanceScannerPage";

const getJsonMock = vi.fn();

vi.mock("../utils/api", () => ({
  getJson: (...args: unknown[]) => getJsonMock(...args),
}));

describe("PriceAcceptanceScannerPage", () => {
  it("loads a universe and runs the price acceptance scan", async () => {
    getJsonMock.mockImplementation((path: string) => {
      if (path === "/api/strategy/price-acceptance/universes") {
        return Promise.resolve({ options: [{ label: "WATCHLIST", value: "WATCHLIST", count: 1 }] });
      }

      return Promise.resolve({
        selectedIndexKey: "WATCHLIST",
        requestedAsOfDate: "2026-08-02",
        scannedStockCount: 1,
        resultCount: 1,
        rows: [{
          symbol: "INFY",
          companyName: "Infosys",
          indexKey: "WATCHLIST",
          instrumentToken: 101,
          anchorDate: "2026-08-01",
          open: 100,
          close: 102,
          bodyLow: 100,
          bodyHigh: 102,
          bodyRangePct: 2,
          priorSessionCount: 100,
          closeHits20: 8,
          closeHitRate20Pct: 40,
          closeHits40: 12,
          closeHitRate40Pct: 30,
          closeHits60: 15,
          closeHitRate60Pct: 25,
          closeHits80: 17,
          closeHitRate80Pct: 21.25,
          closeHits100: 20,
          closeHitRate100Pct: 20,
        }],
      });
    });

    render(<PriceAcceptanceScannerPage />);

    const runButton = await screen.findByRole("button", { name: "Run Scanner" });
    fireEvent.click(runButton);

    await waitFor(() => expect(screen.getByText("INFY")).toBeInTheDocument());
    expect(screen.getByRole("columnheader", { name: "20D hits (rate)" })).toBeInTheDocument();
    expect(screen.getByText("8 (40.0%)")).toBeInTheDocument();
    expect(getJsonMock).toHaveBeenCalledWith(
      expect.stringMatching(/^\/api\/strategy\/price-acceptance\/scan\?indexKey=WATCHLIST&asOfDate=\d{4}-\d{2}-\d{2}$/),
      { useCache: false },
    );
  });
});
