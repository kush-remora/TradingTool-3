import { useCallback, useEffect, useRef, useState } from "react";
import { getJson, putJson } from "../utils/api";
import type { LayoutData, UserLayout } from "../types";

const EMPTY_LAYOUT: LayoutData = { watchlistOrder: [], stockOrder: {} };

function parseLayout(raw: string): LayoutData {
  try {
    const parsed = JSON.parse(raw) as LayoutData;
    return {
      watchlistOrder: parsed.watchlistOrder ?? [],
      stockOrder: parsed.stockOrder ?? {},
    };
  } catch {
    return EMPTY_LAYOUT;
  }
}

export function useLayout() {
  const [layout, setLayout] = useState<LayoutData>(EMPTY_LAYOUT);
  const [loading, setLoading] = useState(false);
  // Debounce save so rapid drag events don't flood the backend
  const saveTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    setLoading(true);
    getJson<UserLayout>("/api/layout")
      .then((data) => setLayout(parseLayout(data.layout_data)))
      .catch(() => setLayout(EMPTY_LAYOUT))
      .finally(() => setLoading(false));
  }, []);

  const saveLayout = useCallback((next: LayoutData) => {
    setLayout(next);
    if (saveTimer.current) clearTimeout(saveTimer.current);
    saveTimer.current = setTimeout(() => {
      void putJson("/api/layout", { layout_data: JSON.stringify(next) });
    }, 600); // debounce — save 600ms after last drag event
  }, []);

  return { layout, loading, saveLayout };
}
