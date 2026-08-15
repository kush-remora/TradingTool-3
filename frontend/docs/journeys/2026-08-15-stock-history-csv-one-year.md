# Stock History CSV — 1 Year Export

## Feature and why

Add a one-year option to the Stock History CSV console so a selected NSE equity can be exported with a longer daily context for review.

## Implemented

- Added a `1 year` period option to the existing stock-history selector.
- Requests 365 daily candles through the existing stock-detail endpoint.
- Raised the endpoint's display limit from 200 to 365 days.
- Kept the existing CSV columns, delivery join, preview table, and filename pattern.

## Key decisions

- Used 365 calendar-day rows as the one-year request, matching the page's existing day-based API contract.
- Kept delivery data best-effort because the endpoint only returns the delivery history already available to the stock-detail response.

## Validation

- Focused Stock History CSV page tests passed.
- Frontend production build passed.

## Next follow-up

- None for this scoped export option.
