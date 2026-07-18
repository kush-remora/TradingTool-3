import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { AccumulationShapePath } from "./AccumulationShapePath";

describe("AccumulationShapePath", () => {
  it("shows the three consecutive chunk story in plain language", () => {
    render(<AccumulationShapePath chunks={[
      chunk(1, "FLAT"),
      chunk(2, "FLAT"),
      chunk(3, "UPWARD_DRIFT"),
    ]} />);

    expect(screen.getByText("flat → flat → rising")).toBeInTheDocument();
  });
});

function chunk(position: number, shape: string) {
  return {
    position,
    startDate: "2026-04-14",
    endDate: "2026-05-11",
    shape,
    goldenFlat: false,
    metrics: { curvature: 0.001, centerSlopePerTenSessions: 0.1, startSlopePerTenSessions: 0.08, endSlopePerTenSessions: 0.12, vertexPosition: null },
  };
}
