import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";

type ProbeConfig = {
  searchId: string;
  companyId: string;
  page: number;
  size: number;
  outputDir: string;
};

type GrowwRequest = {
  label: string;
  url: string;
};

type ProbeResult = {
  label: string;
  url: string;
  status: number;
  ok: boolean;
  savedTo: string;
};

type SummaryReport = {
  generatedAt: string;
  searchId: string;
  companyId: string;
  usedCookieHeader: boolean;
  usedAuthorizationHeader: boolean;
  results: ProbeResult[];
};

const DEFAULT_SEARCH_ID = "mtar-technologies-ltd";
const DEFAULT_COMPANY_ID = "GSTK543270";
const DEFAULT_PAGE = 0;
const DEFAULT_SIZE = 10;
const DEFAULT_OUTPUT_DIR = path.join("build", "reports", "groww-api-probe");

async function main(): Promise<void> {
  const config = readConfig(process.argv.slice(2));
  const headers = buildHeaders();
  const requests = buildRequests(config);

  await mkdir(config.outputDir, { recursive: true });

  const results: ProbeResult[] = [];
  for (const request of requests) {
    results.push(await fetchAndPersist(request, headers, config.outputDir));
  }

  const summary: SummaryReport = {
    generatedAt: new Date().toISOString(),
    searchId: config.searchId,
    companyId: config.companyId,
    usedCookieHeader: Boolean(process.env.GROWW_COOKIE_HEADER),
    usedAuthorizationHeader: Boolean(process.env.GROWW_AUTHORIZATION_HEADER),
    results,
  };

  const summaryPath = path.join(config.outputDir, "summary.json");
  await writeJson(summaryPath, summary);

  process.stdout.write(formatSummary(summary, summaryPath));
}

function readConfig(args: string[]): ProbeConfig {
  const values = new Map<string, string>();

  for (const arg of args) {
    if (!arg.startsWith("--")) {
      throw new Error(`Unsupported argument: ${arg}`);
    }

    const [rawKey, rawValue] = arg.slice(2).split("=", 2);
    if (!rawKey || rawValue === undefined || rawValue.trim() === "") {
      throw new Error(`Expected --key=value format, received: ${arg}`);
    }

    values.set(rawKey, rawValue);
  }

  return {
    searchId: values.get("search-id") ?? DEFAULT_SEARCH_ID,
    companyId: values.get("company-id") ?? DEFAULT_COMPANY_ID,
    page: readNumber(values.get("page"), DEFAULT_PAGE, "page"),
    size: readNumber(values.get("size"), DEFAULT_SIZE, "size"),
    outputDir: values.get("output-dir") ?? DEFAULT_OUTPUT_DIR,
  };
}

function readNumber(rawValue: string | undefined, defaultValue: number, label: string): number {
  if (rawValue === undefined) {
    return defaultValue;
  }

  const parsed = Number.parseInt(rawValue, 10);
  if (!Number.isInteger(parsed) || parsed < 0) {
    throw new Error(`${label} must be a non-negative integer. Received: ${rawValue}`);
  }

  return parsed;
}

function buildHeaders(): HeadersInit {
  const headers = new Headers({
    Accept: "application/json, text/plain, */*",
    Origin: "https://groww.in",
    Referer: "https://groww.in/",
    "User-Agent":
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36",
  });

  const cookieHeader = process.env.GROWW_COOKIE_HEADER?.trim();
  if (cookieHeader) {
    headers.set("Cookie", cookieHeader);
  }

  const authorizationHeader = process.env.GROWW_AUTHORIZATION_HEADER?.trim();
  if (authorizationHeader) {
    headers.set("Authorization", authorizationHeader);
  }

  const extraHeadersJson = process.env.GROWW_EXTRA_HEADERS_JSON?.trim();
  if (extraHeadersJson) {
    const extraHeaders = JSON.parse(extraHeadersJson) as Record<string, string>;
    for (const [key, value] of Object.entries(extraHeaders)) {
      headers.set(key, value);
    }
  }

  return headers;
}

function buildRequests(config: ProbeConfig): GrowwRequest[] {
  const query = `page=${config.page}&size=${config.size}`;

  return [
    {
      label: "company",
      url: `https://groww.in/v1/api/stocks_data/v1/company/search_id/${config.searchId}?${query}`,
    },
    {
      label: "news",
      url: `https://groww.in/v1/api/groww-news/v2/stocks/news/${config.companyId}?${query}`,
    },
  ];
}

async function fetchAndPersist(
  request: GrowwRequest,
  headers: HeadersInit,
  outputDir: string,
): Promise<ProbeResult> {
  const response = await fetch(request.url, {
    method: "GET",
    headers,
  });

  const responseText = await response.text();
  const filePath = path.join(outputDir, `${request.label}.json`);
  await writePrettyJson(filePath, responseText);

  return {
    label: request.label,
    url: request.url,
    status: response.status,
    ok: response.ok,
    savedTo: filePath,
  };
}

async function writePrettyJson(filePath: string, responseText: string): Promise<void> {
  const parsed = JSON.parse(responseText) as unknown;
  await writeJson(filePath, parsed);
}

async function writeJson(filePath: string, value: unknown): Promise<void> {
  const content = `${JSON.stringify(value, null, 2)}\n`;
  await writeFile(filePath, content, "utf8");
}

function formatSummary(summary: SummaryReport, summaryPath: string): string {
  const lines = [
    "Groww API probe complete.",
    `searchId=${summary.searchId}`,
    `growwCompanyId=${summary.companyId}`,
    `cookieHeaderUsed=${summary.usedCookieHeader}`,
    `authorizationHeaderUsed=${summary.usedAuthorizationHeader}`,
  ];

  for (const result of summary.results) {
    lines.push(`${result.label}: status=${result.status} ok=${result.ok} file=${result.savedTo}`);
  }

  lines.push(`summary=${summaryPath}`);
  return `${lines.join("\n")}\n`;
}

void main().catch((error: unknown) => {
  const message = error instanceof Error ? error.message : String(error);
  process.stderr.write(`Groww API probe failed: ${message}\n`);
  process.exitCode = 1;
});
