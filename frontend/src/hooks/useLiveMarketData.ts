import { useEffect, useState } from "react";
import { LiveMarketUpdate } from "../types";
import { apiBaseUrl } from "../utils/api";
import { isIndianEquityMarketOpen } from "../utils/marketHours";

type Listener = (updates: LiveMarketUpdate[]) => void;
const MARKET_HOURS_CHECK_MS = 60_000;

/**
 * Singleton manager to coordinate a single SSE connection for multiple symbols.
 */
class LiveMarketManager {
  private subscribers = new Map<string, number>(); // symbol -> mount count
  private eventSource: EventSource | null = null;
  private listeners = new Set<Listener>();
  private cache = new Map<string, LiveMarketUpdate>();
  private updateTimer: number | null = null;
  private marketHoursTimer: number | null = null;
  private buyerDominancePct: number | null = null;
  private unloadHandlerRegistered = false;

  subscribe(symbol: string, buyerDominancePct: number | undefined, listener: Listener) {
    this.buyerDominancePct = buyerDominancePct ?? null;
    this.subscribers.set(symbol, (this.subscribers.get(symbol) || 0) + 1);
    this.listeners.add(listener);
    
    // Provide immediate cached value if available
    const cached = this.cache.get(symbol);
    if (cached) listener([cached]);
    
    this.debounceUpdate();
  }

  unsubscribe(symbol: string, listener: Listener) {
    const count = this.subscribers.get(symbol) || 0;
    if (count <= 1) {
      this.subscribers.delete(symbol);
    } else {
      this.subscribers.set(symbol, count - 1);
    }
    this.listeners.delete(listener);
    this.debounceUpdate();
  }

  private debounceUpdate() {
    if (this.updateTimer !== null) window.clearTimeout(this.updateTimer);
    this.updateTimer = setTimeout(() => this.updateConnection(), 100);
  }

  private updateConnection() {
    this.ensureMarketHoursWatcher();

    if (this.subscribers.size === 0) {
      this.closeConnection();
      this.stopMarketHoursWatcher();
      return;
    }

    if (!isIndianEquityMarketOpen()) {
      this.closeConnection();
      return;
    }

    const symbols = Array.from(this.subscribers.keys()).sort().join(",");
    const query = new URLSearchParams({ symbols });
    if (this.buyerDominancePct != null) {
      query.set("buyerDominancePct", String(this.buyerDominancePct));
    }
    const url = `${apiBaseUrl}/api/market/live?${query.toString()}`;

    // If already connected to the same set of symbols, do nothing
    if (this.eventSource) {
      // EventSource.url is absolute
      const absoluteUrl = new URL(url, window.location.origin).toString();
      if (this.eventSource.url === absoluteUrl) return;

      this.eventSource.close();
    }

    this.eventSource = new EventSource(url);
    this.ensureUnloadHandler();
    
    this.eventSource.onmessage = (event) => {
      try {
        const payload = JSON.parse(event.data);
        const updates = Array.isArray(payload) ? payload as LiveMarketUpdate[] : [payload as LiveMarketUpdate];
        updates.forEach((u) => this.cache.set(u.symbol, u));
        this.listeners.forEach((l) => l(updates));
      } catch (e) {
        // Ignore malformed JSON
      }
    };

    this.eventSource.onerror = () => {
      if (!isIndianEquityMarketOpen()) {
        this.closeConnection();
      }
    };
  }

  private closeConnection() {
    if (!this.eventSource) return;

    this.eventSource.close();
    this.eventSource = null;
  }

  private ensureMarketHoursWatcher() {
    if (this.marketHoursTimer !== null) {
      return;
    }

    this.marketHoursTimer = window.setInterval(() => {
      this.updateConnection();
    }, MARKET_HOURS_CHECK_MS);
  }

  private stopMarketHoursWatcher() {
    if (this.marketHoursTimer === null) {
      return;
    }

    window.clearInterval(this.marketHoursTimer);
    this.marketHoursTimer = null;
  }

  private ensureUnloadHandler() {
    if (this.unloadHandlerRegistered) {
      return;
    }

    window.addEventListener("beforeunload", this.handleBeforeUnload);
    this.unloadHandlerRegistered = true;
  }

  private readonly handleBeforeUnload = () => {
    this.closeConnection();
  };
}

const manager = new LiveMarketManager();

/**
 * Hook to subscribe to real-time market updates for a specific symbol.
 * Shares a single SSE connection across all components using this hook.
 */
export function useLiveMarketData(symbol: string, buyerDominancePct?: number) {
  const [data, setData] = useState<LiveMarketUpdate | null>(null);

  useEffect(() => {
    if (!symbol) return;

    const listener = (updates: LiveMarketUpdate[]) => {
      const myUpdate = updates.find((u) => u.symbol === symbol);
      if (myUpdate) {
        setData(myUpdate);
      }
    };

    manager.subscribe(symbol, buyerDominancePct, listener);
    return () => manager.unsubscribe(symbol, listener);
  }, [symbol, buyerDominancePct]);

  return data;
}
