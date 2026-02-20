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

async function readResponseBody(
  response: Response,
): Promise<Record<string, unknown>> {
  const text = await response.text();
  if (text.trim() === "") return {};
  try {
    return JSON.parse(text) as Record<string, unknown>;
  } catch {
    return { message: text };
  }
}

export async function sendRequest(
  path: string,
  requestInit: RequestInit,
): Promise<Record<string, unknown>> {
  const response = await fetch(`${apiBaseUrl}${path}`, requestInit);
  const payload = await readResponseBody(response);

  if (!response.ok || payload.ok === false) {
    const errorMessage =
      (payload.telegramDescription as string | undefined) ??
      (payload.message as string | undefined) ??
      `Request failed with status ${response.status}`;
    throw new Error(errorMessage);
  }

  return payload;
}
