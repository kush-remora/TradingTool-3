import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { WeeklyLowLimitDailyValidationPage } from "./WeeklyLowLimitDailyValidationPage";

const loadMock = vi.fn();

vi.mock("../hooks/useWeeklyLowLimitDailyValidation", () => ({
  useWeeklyLowLimitDailyValidation: () => ({
    data: {
      symbol: "TEST",
      previousWeekLowDate: "2025-09-29",
      entryWeekStartDate: "2025-10-06",
      entryDate: "2025-10-07",
      rows: [
        { date: "2025-09-29", open: 102, high: 103, low: 100, close: 102, dailyChangePct: null },
        { date: "2025-10-07", open: 100, high: 106, low: 99, close: 105, dailyChangePct: 2.94 },
      ],
    },
    loading: false,
    error: null,
    load: loadMock,
  }),
}));

describe("WeeklyLowLimitDailyValidationPage", () => {
  beforeEach(() => vi.clearAllMocks());

  it("loads and renders the daily validation path with markers", async () => {
    render(
      <WeeklyLowLimitDailyValidationPage
        symbol="TEST"
        instrumentToken={123}
        previousWeekLowDate="2025-09-29"
        entryWeekStartDate="2025-10-06"
        entryDate="2025-10-07"
      />,
    );

    await waitFor(() => expect(loadMock).toHaveBeenCalledWith({
      symbol: "TEST",
      instrumentToken: 123,
      previousWeekLowDate: "2025-09-29",
      entryWeekStartDate: "2025-10-06",
      entryDate: "2025-10-07",
    }));
    expect(screen.getByText("Daily validation · TEST")).toBeInTheDocument();
    expect(screen.getByText("Previous low")).toBeInTheDocument();
    expect(screen.getByText("Entry")).toBeInTheDocument();
    expect(screen.getByText("Mon, 2025-09-29")).toBeInTheDocument();
    expect(screen.getByText("Tue, 2025-10-07")).toBeInTheDocument();
  });
});
