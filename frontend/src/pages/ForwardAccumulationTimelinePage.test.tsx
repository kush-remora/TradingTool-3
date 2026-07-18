import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ForwardAccumulationTimelinePage } from "./ForwardAccumulationTimelinePage";

const getJsonMock = vi.fn();
vi.mock("../utils/api", () => ({ getJson: (...args: unknown[]) => getJsonMock(...args), postJson: vi.fn() }));

describe("ForwardAccumulationTimelinePage", () => {
  beforeEach(() => {
    getJsonMock.mockResolvedValue({ run: { id: 3 }, isStale: false, rows: [] });
  });

  it("loads the selected stock timeline on its own page", async () => {
    render(<ForwardAccumulationTimelinePage runId={3} symbol="TATAPOWER" chainStartDate="2026-01-02" chainEndDate="2026-01-10" onBack={vi.fn()} />);

    await waitFor(() => expect(getJsonMock).toHaveBeenCalledWith(
      "/api/strategy/accumulation-analysis/runs/3/symbols/TATAPOWER?chainStart=2026-01-02&chainEnd=2026-01-10",
      { useCache: false },
    ));
    expect(screen.getByText("TATAPOWER · 2026-01-02 → 2026-01-10")).toBeInTheDocument();
    expect(screen.getByText("No timeline snapshots found")).toBeInTheDocument();
  });
});
