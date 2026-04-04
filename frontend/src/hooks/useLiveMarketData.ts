import { useEffect, useState } from "react";
import { LiveMarketUpdate } from "../types";
import { apiBaseUrl } from "../utils/api";

type Listener = (update: LiveMarketUpdate) => void;

/**
 * Singleton manager to coordinate a single SSE connection for multiple symbols.
 */
class LiveMarketManager {
  private subscribers = new Map<string, number>(); // symbol -> mount count
  private eventSource: EventSource | null = null;
  private listeners = new Set<Listener>();
  private cache = new Map<string, LiveMarketUpdate>();
  private updateTimer: any = null;

  subscribe(symbol: string, listener: Listener) {
    this.subscribers.set(symbol, (this.subscribers.get(symbol) || 0) + 1);
    this.listeners.add(listener);
    
    // Provide immediate cached value if available
    const cached = this.cache.get(symbol);
    if (cached) listener(cached);
    
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
    if (this.updateTimer) clearTimeout(this.updateTimer);
    this.updateTimer = setTimeout(() => this.updateConnection(), 100);
  }

  private updateConnection() {
    if (this.subscribers.size === 0) {
      if (this.eventSource) {
        console.log("🔌 Closing LiveMarket SSE (no subscribers)");
        this.eventSource.close();
        this.eventSource = null;
      }
      return;
    }

    const symbols = Array.from(this.subscribers.keys()).sort().join(",");
    const url = `${apiBaseUrl}/api/market/live?symbols=${symbols}`;

    // If already connected to the same set of symbols, do nothing
    if (this.eventSource) {
      // EventSource.url is absolute
      const absoluteUrl = new URL(url, window.location.origin).toString();
      if (this.eventSource.url === absoluteUrl) return;
      
      console.log(`🔌 Reconnecting LiveMarket SSE for symbols: ${symbols}`);
      this.eventSource.close();
    } else {
      console.log(`🔌 Connecting LiveMarket SSE for symbols: ${symbols}`);
    }

    this.eventSource = new EventSource(url);
    
    this.eventSource.onmessage = (event) => {
      try {
        const update = JSON.parse(event.data) as LiveMarketUpdate;
        this.cache.set(update.symbol, update);
        this.listeners.forEach((l) => l(update));
      } catch (e) {
        // Ignore malformed JSON
      }
    };

    this.eventSource.onerror = () => {
      // EventSource automatically handles reconnection logic
    };
  }
}

const manager = new LiveMarketManager();

/**
 * Hook to subscribe to real-time market updates for a specific symbol.
 * Shares a single SSE connection across all components using this hook.
 */
export function useLiveMarketData(symbol: string) {
  const [data, setData] = useState<LiveMarketUpdate | null>(null);

  useEffect(() => {
    if (!symbol) return;

    const listener = (update: LiveMarketUpdate) => {
      if (update.symbol === symbol) {
        setData(update);
      }
    };

    manager.subscribe(symbol, listener);
    return () => manager.unsubscribe(symbol, listener);
  }, [symbol]);

  return data;
}
