import { useCallback, useState } from "react";
import type { WeeklyBaseDefinitionReport, WeeklyBaseDefinitionRequest } from "../types";
import { postJson } from "../utils/api";

const WEEKLY_BASE_DEFINITION_PATH = "/api/strategy/weekly-base-definition/run";

export function useWeeklyBaseDefinition() {
  const [data, setData] = useState<WeeklyBaseDefinitionReport | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = useCallback(async (request: WeeklyBaseDefinitionRequest): Promise<void> => {
    setLoading(true);
    setError(null);
    setData(null);
    try {
      setData(await postJson<WeeklyBaseDefinitionReport>(WEEKLY_BASE_DEFINITION_PATH, request));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Weekly base definition failed.");
    } finally {
      setLoading(false);
    }
  }, []);

  return { data, loading, error, run };
}
