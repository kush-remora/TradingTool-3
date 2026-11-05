const DEV_API_BASE_URL = "http://localhost:8080";
const PROD_API_BASE_URL = "";
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
  ];

  const firstString = candidates.find(
    (candidate) => typeof candidate === "string" && candidate.trim().length > 0,
  );

  if (typeof firstString === "string") {
    return firstString;
  }

  return fallbackMessage;
}

export async function getJson<T>(
  path: string,
  options?: { fallbackMessage?: string },
): Promise<T> {
  const url = `${apiBaseUrl}${path}`;
  const response = await fetch(url, {
    headers: {
      Accept: "application/json",
    },
  });

  const payload = await readResponseBody(response);
  if (!response.ok) {
    throw new Error(
      extractErrorMessage(
        payload,
        options?.fallbackMessage ?? `Request failed (${response.status})`,
      ),
    );
  }

  return payload as T;
}

export async function postJson<TRequest, TResponse>(
  path: string,
  data: TRequest,
  options?: { fallbackMessage?: string },
): Promise<TResponse> {
  const url = `${apiBaseUrl}${path}`;
  const response = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify(data),
  });

  const payload = await readResponseBody(response);
  if (!response.ok) {
    throw new Error(
      extractErrorMessage(
        payload,
        options?.fallbackMessage ?? `Request failed (${response.status})`,
      ),
    );
  }

  return payload as TResponse;
}

export async function postFormData<TResponse>(
  path: string,
  formData: FormData,
  options?: { fallbackMessage?: string },
): Promise<TResponse> {
  const url = `${apiBaseUrl}${path}`;
  const response = await fetch(url, {
    method: "POST",
    headers: {
      Accept: "application/json",
    },
    body: formData,
  });

  const payload = await readResponseBody(response);
  if (!response.ok) {
    throw new Error(
      extractErrorMessage(
        payload,
        options?.fallbackMessage ?? `Request failed (${response.status})`,
      ),
    );
  }

  return payload as TResponse;
}
