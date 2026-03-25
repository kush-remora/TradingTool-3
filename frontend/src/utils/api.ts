const DEV_API_BASE_URL = "http://localhost:8080";
const PROD_API_BASE_URL = "https://tradingtool-3-service.onrender.com";
const DEFAULT_API_BASE_URL = import.meta.env.DEV
  ? DEV_API_BASE_URL
  : PROD_API_BASE_URL;

export const apiBaseUrl = (
  import.meta.env.VITE_API_BASE_URL ?? DEFAULT_API_BASE_URL
)
  .trim()
  .replace(/\/+$/, "");

function asRecord(value: unknown): Record<string, unknown> | null {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    return null;
  }
  return value as Record<string, unknown>;
}

async function readResponseBody(response: Response): Promise<unknown> {
  const text = await response.text();
  if (text.trim() === "") return {};

  try {
    return JSON.parse(text) as unknown;
  } catch {
    return { message: text };
  }
}

function extractErrorMessage(
  payload: unknown,
  fallbackMessage: string,
): string {
  if (typeof payload === "string" && payload.trim().length > 0) {
    return payload;
  }

  const record = asRecord(payload);
  if (!record) {
    return fallbackMessage;
  }

  const candidates: Array<unknown> = [
    record.detail,
    record.message,
    record.error,
    record.telegramDescription,
  ];

  const firstString = candidates.find(
    (candidate) => typeof candidate === "string" && candidate.trim().length > 0,
  );

  if (typeof firstString === "string") {
    return firstString;
  }

  return fallbackMessage;
}

export async function sendRequest<T = Record<string, unknown>>(
  path: string,
  requestInit: RequestInit,
): Promise<T> {
  const method = (requestInit.method ?? "GET").toUpperCase();
  const response = await fetch(`${apiBaseUrl}${path}`, requestInit);
  const payload = await readResponseBody(response);
  const payloadObject = asRecord(payload);
  const payloadOkFlag = payloadObject?.ok;

  if (!response.ok || payloadOkFlag === false) {
    throw new Error(
      extractErrorMessage(
        payload,
        `${method} ${path} failed with status ${response.status}`,
      ),
    );
  }

  return payload as T;
}

export async function getJson<T>(path: string): Promise<T> {
  return sendRequest<T>(path, {
    headers: { Accept: "application/json" },
  });
}

export async function postJson<T>(path: string, body: unknown): Promise<T> {
  const serialized = JSON.stringify(body);
  console.log(`📮 POST ${path}:`, serialized);
  return sendRequest<T>(path, {
    method: "POST",
    headers: { "Content-Type": "application/json", Accept: "application/json" },
    body: serialized,
  });
}

export async function patchJson<T>(path: string, body: unknown): Promise<T> {
  const serialized = JSON.stringify(body);
  console.log(`🔧 PATCH ${path}:`, serialized);
  return sendRequest<T>(path, {
    method: "PATCH",
    headers: { "Content-Type": "application/json", Accept: "application/json" },
    body: serialized,
  });
}

export async function deleteJson(path: string): Promise<void> {
  await sendRequest<unknown>(path, {
    method: "DELETE",
    headers: { Accept: "application/json" },
  });
}
