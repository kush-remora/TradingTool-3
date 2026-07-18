import { useCallback, useState } from "react";
import { getJson, postJson } from "../utils/api";
import type { AccumulationAnalysisRun, AccumulationAnalysisRunRequest, AccumulationAnalysisSummary, AccumulationAnalysisTimeline } from "../types";

export function useAccumulationAnalysis() {
  const [runs, setRuns] = useState<AccumulationAnalysisRun[]>([]);
  const [summary, setSummary] = useState<AccumulationAnalysisSummary | null>(null);
  const [timeline, setTimeline] = useState<AccumulationAnalysisTimeline | null>(null);
  const [loading, setLoading] = useState(false);
  const [timelineLoading, setTimelineLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const loadRuns = useCallback(async () => { const data = await getJson<AccumulationAnalysisRun[]>("/api/strategy/accumulation-analysis/runs", { useCache: false }); setRuns(data); return data; }, []);
  const loadSummary = useCallback(async (runId: number) => { const data = await getJson<AccumulationAnalysisSummary>(`/api/strategy/accumulation-analysis/runs/${runId}`, { useCache: false }); setSummary(data); return data; }, []);
  const loadTimeline = useCallback(async (runId: number, symbol: string, chainStartDate: string | null, chainEndDate: string | null) => {
    setTimelineLoading(true);
    setError(null);
    try {
      const params = new URLSearchParams();
      if (chainStartDate) params.set("chainStart", chainStartDate);
      if (chainEndDate) params.set("chainEnd", chainEndDate);
      const suffix = params.size ? `?${params}` : "";
      const data = await getJson<AccumulationAnalysisTimeline>(`/api/strategy/accumulation-analysis/runs/${runId}/symbols/${symbol}${suffix}`, { useCache: false });
      setTimeline(data);
      return data;
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not load timeline");
      throw cause;
    } finally {
      setTimelineLoading(false);
    }
  }, []);
  const run = useCallback(async (request: AccumulationAnalysisRunRequest) => { setLoading(true); setError(null); try { const data = await postJson<AccumulationAnalysisRun>("/api/strategy/accumulation-analysis/runs", request); await loadRuns(); await loadSummary(data.id); return data; } catch (cause) { setError(cause instanceof Error ? cause.message : "Analysis failed"); throw cause; } finally { setLoading(false); } }, [loadRuns, loadSummary]);
  return { runs, summary, timeline, loading, timelineLoading, error, loadRuns, loadSummary, loadTimeline, run };
}
