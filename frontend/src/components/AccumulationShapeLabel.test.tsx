import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { AccumulationShapeLabel } from "./AccumulationShapeLabel";

describe("AccumulationShapeLabel", () => {
  it("calls out the Golden Flat window length", () => {
    render(<AccumulationShapeLabel shape="FLAT_GOLDEN" goldenFlatNode={{
      windowSessions: 20,
      startDate: "2026-05-13",
      endDate: "2026-06-09",
      metrics: { curvature: 0.001, centerSlopePerTenSessions: 0.1, startSlopePerTenSessions: 0.08, endSlopePerTenSessions: 0.12, vertexPosition: null },
      lineFit: null,
    }} lineFit={null} />);

    expect(screen.getByText("GOLDEN FLAT · 20D")).toBeInTheDocument();
  });

  it("marks an ignored shock on a strict flat", () => {
    render(<AccumulationShapeLabel shape="FLAT" goldenFlatNode={null} lineFit={{
      slopePerTenSessions: -0.4,
      typicalDeviationPercent: 0.8,
      maximumDeviationPercent: 1.4,
      ignoredOutlierDate: "2026-03-04",
      ignoredOutlierDeviationPercent: 4.4,
    }} />);

    expect(screen.getByText("FLAT ⚠")).toBeInTheDocument();
  });
});
