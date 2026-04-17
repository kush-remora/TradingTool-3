import { describe, expect, it } from "vitest";
import {
  moveColumn,
  parseJsonSafely,
  updateColumnPin,
  updateColumnVisibility,
  type WeeklyScreenerColumnConfig,
} from "./weeklyScreenerTableState";

describe("weeklyScreenerTableState", () => {
  const seed: WeeklyScreenerColumnConfig[] = [
    { key: "symbol", visible: true, pin: "left" },
    { key: "score", visible: true, pin: "none" },
    { key: "bucket", visible: false, pin: "none" },
  ];

  it("moves columns up/down", () => {
    const movedDown = moveColumn(seed, "symbol", "down");
    expect(movedDown.map((column) => column.key)).toEqual(["score", "symbol", "bucket"]);

    const movedUp = moveColumn(movedDown, "bucket", "up");
    expect(movedUp.map((column) => column.key)).toEqual(["score", "bucket", "symbol"]);
  });

  it("updates visibility and pin", () => {
    const visibility = updateColumnVisibility(seed, "bucket", true);
    expect(visibility.find((column) => column.key === "bucket")?.visible).toBe(true);

    const pin = updateColumnPin(visibility, "score", "right");
    expect(pin.find((column) => column.key === "score")?.pin).toBe("right");
  });

  it("parses JSON safely", () => {
    const parsed = parseJsonSafely<{ ok: boolean }>("{\"ok\":true}");
    expect(parsed?.ok).toBe(true);
    expect(parseJsonSafely("{bad-json")).toBeNull();
  });
});
