import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { WeeklyBaseGroupBacktestPage } from "./WeeklyBaseGroupBacktestPage";

const runMock = vi.fn();

vi.mock("../hooks/useWeeklyBaseGroupBacktest", () => ({
  useWeeklyBaseGroupBacktest: () => ({
    data: null,
    loading: false,
    error: null,
    run: runMock,
  }),
}));

vi.mock("../utils/api", () => ({
  getJson: async () => [{ value: "NIFTY 50", count: 50 }],
}));

describe("WeeklyBaseGroupBacktestPage", () => {
  it("runs the selected index group", async () => {
    render(<WeeklyBaseGroupBacktestPage />);

    fireEvent.mouseDown(screen.getByRole("combobox"));
    fireEvent.click(await screen.findByText("NIFTY 50 (50)"));
    fireEvent.click(
      screen.getByRole("button", { name: /run group backtest/i }),
    );

    expect(runMock).toHaveBeenCalledWith({ indexKeys: ["NIFTY 50"] });
  });
});
