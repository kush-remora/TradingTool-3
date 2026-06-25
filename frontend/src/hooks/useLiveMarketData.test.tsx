import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { LiveMarketUpdate } from "../types";

vi.mock("../utils/marketHours", () => ({
  isIndianEquityMarketOpen: vi.fn(() => true),
}));

class MockEventSource {
  static instances: MockEventSource[] = [];

  readonly url: string;
  onmessage: ((event: MessageEvent<string>) => void) | null = null;
  onerror: (() => void) | null = null;
  close = vi.fn();

  constructor(url: string) {
    this.url = url;
    MockEventSource.instances.push(this);
  }

  emit(updates: LiveMarketUpdate[]) {
    this.onmessage?.({
      data: JSON.stringify(updates),
    } as MessageEvent<string>);
  }
}

describe("useLiveMarketData", () => {
  beforeEach(() => {
    vi.resetModules();
    MockEventSource.instances = [];
    vi.stubGlobal("EventSource", MockEventSource);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("opens one shared connection and only updates listeners for the matching symbol", async () => {
    const { useLiveMarketData } = await import("./useLiveMarketData");

    const infyHook = renderHook(() => useLiveMarketData("NSE:INFY"));
    const tcsHook = renderHook(() => useLiveMarketData("NSE:TCS"));

    await act(async () => {
      await new Promise((resolve) => globalThis.setTimeout(resolve, 150));
    });

    expect(MockEventSource.instances).toHaveLength(1);
    expect(MockEventSource.instances[0]?.url).toContain("/api/market/live?symbols=NSE%3AINFY%2CNSE%3ATCS");

    act(() => {
      MockEventSource.instances[0]?.emit([
        {
          symbol: "NSE:INFY",
          instrumentToken: 408065,
          ltp: 1520,
          averagePrice: 1514,
          changePercent: 0.75,
          open: 1508,
          high: 1525,
          low: 1502,
          volume: 2500000,
          buyQuantity: 120000,
          sellQuantity: 90000,
          buyPressurePct: null,
          sellPressurePct: null,
          buyerDominancePass: null,
          pressureSide: "NEUTRAL",
          avgVol20d: null,
          volumeHeat: null,
          updatedAt: 1,
        },
      ]);
    });

    expect(infyHook.result.current?.ltp).toBe(1520);
    expect(tcsHook.result.current).toBeNull();

    infyHook.unmount();
    tcsHook.unmount();
  });
});
