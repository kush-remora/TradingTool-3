import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ForwardAccumulationAnalysisPage } from "./ForwardAccumulationAnalysisPage";

const getJsonMock = vi.fn();
vi.mock("../utils/api", () => ({ getJson: (...args: unknown[]) => getJsonMock(...args), postJson: vi.fn() }));

describe("ForwardAccumulationAnalysisPage", () => {
  beforeEach(() => { getJsonMock.mockResolvedValue([]); });

  it("shows manual universe replay controls", async () => {
    render(<ForwardAccumulationAnalysisPage />);
    await waitFor(() => expect(getJsonMock).toHaveBeenCalledWith("/api/strategy/accumulation-analysis/runs", { useCache: false }));
    expect(screen.getByText("Forward Accumulation Analysis")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Run replay" })).toBeInTheDocument();
    expect(screen.getByText("Run a universe to view saved snapshots")).toBeInTheDocument();
  });
});
