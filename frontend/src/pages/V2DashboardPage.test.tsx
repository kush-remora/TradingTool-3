import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { V2DashboardPage } from "./V2DashboardPage";

const runProfitLookbackMock = vi.fn();
const runProfitLookbackBulkMock = vi.fn();
const useProfitLookbackMock = vi.fn();
const useInstrumentSearchMock = vi.fn();

vi.mock("../hooks/useProfitLookback", () => ({
  useProfitLookback: () => useProfitLookbackMock(),
}));

vi.mock("../hooks/useInstrumentSearch", () => ({
  useInstrumentSearch: () => useInstrumentSearchMock(),
}));

vi.mock("../components/InstrumentSearch", () => {
  return {
    InstrumentSearch: ({ onSelect }: { onSelect: (instrument: unknown) => void }) => (
      <button
        onClick={() =>
          onSelect({
            instrument_token: 408065,
            trading_symbol: "INFY",
            company_name: "Infosys",
            exchange: "NSE",
            instrument_type: "EQ",
          })
        }
      >
        Pick Symbol
      </button>
    ),
  };
});

const defaultInstruments = [
  {
    instrument_token: 408065,
    trading_symbol: "INFY",
    company_name: "Infosys",
    exchange: "NSE",
    instrument_type: "EQ",
  },
  {
    instrument_token: 738561,
    trading_symbol: "RELIANCE",
    company_name: "Reliance Industries",
    exchange: "NSE",
    instrument_type: "EQ",
  },
  {
    instrument_token: 341249,
    trading_symbol: "HDFCBANK",
    company_name: "HDFC Bank",
    exchange: "NSE",
    instrument_type: "EQ",
  },
  {
    instrument_token: 111,
    trading_symbol: "ABC",
    company_name: "Alpha Beta",
    exchange: "NSE",
    instrument_type: "EQ",
  },
  {
    instrument_token: 112,
    trading_symbol: "ABCD",
    company_name: "Alpha Beta Consumer",
    exchange: "NSE",
    instrument_type: "EQ",
  },
];

describe("V2DashboardPage", () => {
  beforeEach(() => {
    runProfitLookbackMock.mockReset();
    runProfitLookbackBulkMock.mockReset();
    useProfitLookbackMock.mockReturnValue({
      loading: false,
      error: null,
      runProfitLookback: runProfitLookbackMock,
      runProfitLookbackBulk: runProfitLookbackBulkMock,
    });
    useInstrumentSearchMock.mockReturnValue({
      allInstruments: defaultInstruments,
      loading: false,
      error: null,
    });
  });

  it("renders controls and starts with empty input table", () => {
    render(<V2DashboardPage />);

    expect(screen.getByText("V2 Dashboard")).toBeInTheDocument();
    expect(screen.getByText("No rows yet. Click Add Row to start.")).toBeInTheDocument();
    expect(screen.getByDisplayValue("5,10,15,20")).toBeInTheDocument();
    expect(screen.getByText("Bulk Add")).toBeInTheDocument();
  });

  it("adds bulk rows with matched tokens and shows skip summary", async () => {
    render(<V2DashboardPage />);

    fireEvent.change(screen.getByPlaceholderText("INFY, Reliance, HDFC Bank"), {
      target: { value: "INFY, Reliance Industries, alpha, NO_MATCH" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Add Bulk Rows" }));

    await waitFor(() => {
      expect(screen.getByText(/Added: 2 \| Unmatched: 1 \| Ambiguous: 1/)).toBeInTheDocument();
      expect(screen.getByText(/Unmatched: NO_MATCH/)).toBeInTheDocument();
      expect(screen.getByText(/Ambiguous: alpha \(ABC\/ABCD\)/i)).toBeInTheDocument();
    });
  });

  it("parses target csv as positive deduped sorted values before API call", async () => {
    runProfitLookbackMock.mockResolvedValue({
      symbol: "INFY",
      instrumentToken: 408065,
      requestedSellDate: "2026-04-20",
      resolvedSellDate: "2026-04-20",
      sellOpenPrice: 100,
      results: [
        {
          targetPercent: 5,
          status: "ACHIEVED",
          suggestedBuyDate: "2026-04-10",
          buyOpenPrice: 95,
          daysBefore: 10,
          returnPercent: 5.26,
          maxDrawdownPercent: -2.5,
          maxDrawdownDays: 2,
        },
      ],
    });

    render(<V2DashboardPage />);

    fireEvent.change(screen.getByDisplayValue("5,10,15,20"), { target: { value: "10,5,-2,foo,10" } });
    fireEvent.click(screen.getByRole("button", { name: "Add Row" }));
    fireEvent.click(screen.getByRole("button", { name: "Pick Symbol" }));
    fireEvent.click(screen.getByRole("button", { name: "Analyze" }));

    await waitFor(() => {
      expect(runProfitLookbackMock).toHaveBeenCalledTimes(1);
    });

    const request = runProfitLookbackMock.mock.calls[0][0];
    expect(request.targetPercents).toEqual([5, 10]);
  });

  it("analyzes all valid rows using Analyze All button", async () => {
    runProfitLookbackBulkMock.mockResolvedValue({
      rows: [
        {
          rowId: "row-1",
          ok: true,
          error: null,
          data: {
            symbol: "INFY",
            instrumentToken: 408065,
            requestedSellDate: "2026-04-20",
            resolvedSellDate: "2026-04-20",
            sellOpenPrice: 100,
            results: [
              {
                targetPercent: 5,
                status: "ACHIEVED",
                suggestedBuyDate: "2026-04-15",
                buyOpenPrice: 95,
                daysBefore: 5,
                returnPercent: 5.26,
                maxDrawdownPercent: -3.1,
                maxDrawdownDays: 1,
              },
            ],
          },
        },
        {
          rowId: "row-2",
          ok: true,
          error: null,
          data: {
            symbol: "RELIANCE",
            instrumentToken: 738561,
            requestedSellDate: "2026-04-20",
            resolvedSellDate: "2026-04-20",
            sellOpenPrice: 2500,
            results: [
              {
                targetPercent: 5,
                status: "NOT_ACHIEVABLE",
                suggestedBuyDate: null,
                buyOpenPrice: null,
                daysBefore: null,
                returnPercent: null,
                maxDrawdownPercent: null,
                maxDrawdownDays: null,
              },
            ],
          },
        },
      ],
    });

    render(<V2DashboardPage />);

    fireEvent.change(screen.getByPlaceholderText("INFY, Reliance, HDFC Bank"), {
      target: { value: "INFY, RELIANCE" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Add Bulk Rows" }));
    fireEvent.click(screen.getByRole("button", { name: "Analyze All" }));

    await waitFor(() => {
      expect(runProfitLookbackBulkMock).toHaveBeenCalledTimes(1);
      expect(runProfitLookbackMock).toHaveBeenCalledTimes(0);
      expect(screen.getByText("INFY")).toBeInTheDocument();
      expect(screen.getByText("RELIANCE")).toBeInTheDocument();
    });
  }, 10000);

  it("filters results using search input", async () => {
    runProfitLookbackBulkMock.mockResolvedValue({
      rows: [
        {
          rowId: "row-1",
          ok: true,
          error: null,
          data: {
            symbol: "INFY",
            instrumentToken: 408065,
            requestedSellDate: "2026-04-20",
            resolvedSellDate: "2026-04-20",
            sellOpenPrice: 100,
            results: [
              {
                targetPercent: 5,
                status: "ACHIEVED",
                suggestedBuyDate: "2026-04-15",
                buyOpenPrice: 95,
                daysBefore: 5,
                returnPercent: 5.26,
                maxDrawdownPercent: -3.1,
                maxDrawdownDays: 1,
              },
            ],
          },
        },
        {
          rowId: "row-2",
          ok: true,
          error: null,
          data: {
            symbol: "RELIANCE",
            instrumentToken: 738561,
            requestedSellDate: "2026-04-20",
            resolvedSellDate: "2026-04-20",
            sellOpenPrice: 2500,
            results: [
              {
                targetPercent: 5,
                status: "NOT_ACHIEVABLE",
                suggestedBuyDate: null,
                buyOpenPrice: null,
                daysBefore: null,
                returnPercent: null,
                maxDrawdownPercent: null,
                maxDrawdownDays: null,
              },
            ],
          },
        },
      ],
    });

    render(<V2DashboardPage />);

    fireEvent.change(screen.getByPlaceholderText("INFY, Reliance, HDFC Bank"), {
      target: { value: "INFY, RELIANCE" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Add Bulk Rows" }));
    fireEvent.click(screen.getByRole("button", { name: "Analyze All" }));

    await waitFor(() => {
      expect(screen.getByText("INFY")).toBeInTheDocument();
      expect(screen.getByText("RELIANCE")).toBeInTheDocument();
    });

    fireEvent.change(screen.getByPlaceholderText("Search results"), {
      target: { value: "NOT_ACHIEVABLE" },
    });

    await waitFor(() => {
      expect(screen.queryByText("INFY")).not.toBeInTheDocument();
      expect(screen.getByText("RELIANCE")).toBeInTheDocument();
      expect(screen.getByText("NOT_ACHIEVABLE")).toBeInTheDocument();
    });
  });

  it("shows row-level error when bulk analyze partially fails", async () => {
    runProfitLookbackBulkMock.mockResolvedValue({
      rows: [
        {
          rowId: "row-1",
          ok: true,
          error: null,
          data: {
            symbol: "INFY",
            instrumentToken: 408065,
            requestedSellDate: "2026-04-20",
            resolvedSellDate: "2026-04-20",
            sellOpenPrice: 100,
            results: [
              {
                targetPercent: 5,
                status: "ACHIEVED",
                suggestedBuyDate: "2026-04-15",
                buyOpenPrice: 95,
                daysBefore: 5,
                returnPercent: 5.26,
                maxDrawdownPercent: -3.1,
                maxDrawdownDays: 1,
              },
            ],
          },
        },
        { rowId: "row-2", ok: false, data: null, error: "sellDate must be in YYYY-MM-DD format." },
      ],
    });

    render(<V2DashboardPage />);

    fireEvent.change(screen.getByPlaceholderText("INFY, Reliance, HDFC Bank"), {
      target: { value: "INFY, RELIANCE" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Add Bulk Rows" }));
    fireEvent.click(screen.getByRole("button", { name: "Analyze All" }));

    await waitFor(() => {
      expect(screen.getByText("sellDate must be in YYYY-MM-DD format.")).toBeInTheDocument();
      expect(screen.getByText("INFY")).toBeInTheDocument();
    });
  });

  it("exports results table as JSON", async () => {
    const createObjectURLMock = vi.fn(() => "blob:test");
    const revokeObjectURLMock = vi.fn();
    const appendChildSpy = vi.spyOn(document.body, "appendChild");
    const removeChildSpy = vi.spyOn(document.body, "removeChild");
    const clickMock = vi.fn();
    const originalCreateElement = document.createElement.bind(document);
    const createElementSpy = vi.spyOn(document, "createElement").mockImplementation((tagName: string) => {
      const element = originalCreateElement(tagName);
      if (tagName.toLowerCase() === "a") {
        (element as HTMLAnchorElement).click = clickMock;
      }
      return element;
    });

    const originalCreateObjectURL = window.URL.createObjectURL;
    const originalRevokeObjectURL = window.URL.revokeObjectURL;
    window.URL.createObjectURL = createObjectURLMock;
    window.URL.revokeObjectURL = revokeObjectURLMock;

    runProfitLookbackMock.mockResolvedValue({
      symbol: "INFY",
      instrumentToken: 408065,
      requestedSellDate: "2026-04-20",
      resolvedSellDate: "2026-04-20",
      sellOpenPrice: 100,
      results: [
        {
          targetPercent: 5,
          status: "ACHIEVED",
          suggestedBuyDate: "2026-04-15",
          buyOpenPrice: 95,
          daysBefore: 5,
          returnPercent: 5.26,
          maxDrawdownPercent: -3.1,
          maxDrawdownDays: 1,
        },
      ],
    });

    render(<V2DashboardPage />);

    fireEvent.click(screen.getByRole("button", { name: "Add Row" }));
    fireEvent.click(screen.getByRole("button", { name: "Pick Symbol" }));
    fireEvent.click(screen.getByRole("button", { name: "Analyze" }));

    await waitFor(() => {
      expect(screen.getByText("ACHIEVED")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: "Export Results JSON" }));

    await waitFor(() => {
      expect(createObjectURLMock).toHaveBeenCalledTimes(1);
      expect(clickMock).toHaveBeenCalledTimes(1);
      expect(revokeObjectURLMock).toHaveBeenCalledTimes(1);
    });

    createElementSpy.mockRestore();
    appendChildSpy.mockRestore();
    removeChildSpy.mockRestore();
    window.URL.createObjectURL = originalCreateObjectURL;
    window.URL.revokeObjectURL = originalRevokeObjectURL;
  }, 10000);
});
