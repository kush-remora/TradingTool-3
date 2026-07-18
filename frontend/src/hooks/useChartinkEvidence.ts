import { useCallback, useState } from "react";
import { getJson, postJson } from "../utils/api";
import type {
  ChartinkEvidenceDashboardResponse,
  ChartinkEvidenceUploadRequest,
  ChartinkEvidenceUploadResult,
} from "../types";

export function useChartinkEvidence() {
  const [dashboard, setDashboard] = useState<ChartinkEvidenceDashboardResponse | null>(null);
  const [loadingDashboard, setLoadingDashboard] = useState(false);
  const [uploadingSlot, setUploadingSlot] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const loadDashboard = useCallback(async (months: number) => {
    setLoadingDashboard(true);
    setError(null);
    try {
      const data = await getJson<ChartinkEvidenceDashboardResponse>(
        `/api/strategy/chartink-evidence/dashboard?months=${months}`,
        { useCache: false },
      );
      setDashboard(data);
      return data;
    } catch (cause) {
      const message = cause instanceof Error ? cause.message : "Failed to load Chartink evidence";
      setError(message);
      throw cause;
    } finally {
      setLoadingDashboard(false);
    }
  }, []);

  const upload = useCallback(async (request: ChartinkEvidenceUploadRequest) => {
    setUploadingSlot(request.slot);
    setError(null);
    try {
      return await postJson<ChartinkEvidenceUploadResult>("/api/strategy/chartink-evidence/upload", request);
    } catch (cause) {
      const message = cause instanceof Error ? cause.message : "Failed to upload Chartink evidence";
      setError(message);
      throw cause;
    } finally {
      setUploadingSlot(null);
    }
  }, []);

  return { dashboard, loadingDashboard, uploadingSlot, error, loadDashboard, upload };
}
