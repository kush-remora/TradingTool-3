# Groww Web API POC

## Goal
Prove that TradingTool can fetch Groww website API data for stock/company/news workflows without taking on a brittle browser-automation dependency too early.

## Current finding
As of 2026-06-11, these two sample endpoints respond with `HTTP 200` and JSON without reusing a logged-in Chrome session:

- `https://groww.in/v1/api/stocks_data/v1/company/search_id/mtar-technologies-ltd?page=0&size=10`
- `https://groww.in/v1/api/groww-news/v2/stocks/news/GSTK543270?page=0&size=10`

That means the first proof of concept can stay read-only and session-free for public stock/company/news data.

## Probe script
Run:

```bash
npm run groww:probe
```

The script writes artifacts to `build/reports/groww-api-probe/`:

- `company.json`
- `news.json`
- `summary.json`

## Override target stock
Run:

```bash
npm run groww:probe -- --search-id=mtar-technologies-ltd --company-id=GSTK543270
```

Optional query overrides:

- `--page=0`
- `--size=10`
- `--output-dir=build/reports/groww-api-probe`

## If a later endpoint needs login
Keep the same script, but copy request data from Chrome DevTools after logging in to Groww:

1. Open Groww in Chrome and log in manually.
2. Open DevTools Network tab.
3. Replay the target API call in the website.
4. Copy the request `Cookie` header or any required auth header.
5. Pass them in as environment variables:

```bash
export GROWW_COOKIE_HEADER='name=value; other=value'
export GROWW_AUTHORIZATION_HEADER='Bearer ...'
npm run groww:probe
```

You can also add one-off headers with:

```bash
export GROWW_EXTRA_HEADERS_JSON='{"x-header-name":"value"}'
```

## Recommendation for app integration
For now, keep Groww integration as:

1. read-only
2. manual-triggered from the app
3. fail-fast on HTTP/parse errors
4. artifact-backed while the endpoint surface is still being learned

Once the exact daily news workflow is stable, the next step should be a small backend endpoint that wraps this read-only fetch path for the frontend button.
