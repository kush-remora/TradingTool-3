# Understanding: 52-Week Momentum (Phase 1, Rule 5) UI

## Objective
Build a UI tool and backend API to analyze stocks based on Rule 5 of the 52-Week Momentum strategy ("Is price orbiting its long-term average?").

## User Story
As a user, I want to upload a CSV file containing `date, symbol, marketcapname, sector`. The tool should process each symbol, analyze the 30-day window ending on the given date, and calculate the percentage of days the close price was within ±2%, ±3%, and ±4% of the 200-day Simple Moving Average (SMA). The results should be displayed in a table, with an expandable row for each symbol showing the daily breakdown.

## Implementation Steps

### 1. Backend: API and Processing
- **Endpoint**: Create a new REST endpoint (e.g., `POST /api/strategy/52w-momentum/rule5/csv`) that accepts a multipart file (CSV).
- **Service Logic**: 
  - Parse the CSV to extract `(symbol, date, marketcapname, sector)`.
  - For each row, calculate the 30-day window ending on `date`.
  - Fetch historical daily candles from the database (and trigger a `CandleDataService.syncDailyRange` if data is missing, looking back ~400 days to ensure enough data for a 200-day SMA).
  - Calculate the 200 SMA for each day in the 30-day window.
  - Determine if the close price for that day falls within the ±2%, ±3%, and ±4% bounds of the 200 SMA.
  - Aggregate the percentage of days within bounds for the symbol.
- **Output**: Return a JSON response containing the summary metrics for each symbol, along with the 30-day daily breakdown.

### 2. Frontend: UI Component
- **Component**: Create a new page/component in React using Ant Design (`antd`).
- **File Upload**: Add an `Upload` component for the CSV file.
- **Table**: 
  - Columns: Date, Symbol, Market Cap, Sector, Close Price (on end date), 200 SMA (on end date), % in ±2%, % in ±3%, % in ±4%.
  - Expandable Row: When clicked, displays a sub-table with the 30 days of data. Columns: Date, Close Price, 200 SMA, ±2% (True/False), ±3% (True/False), ±4% (True/False).
- **Integration**: Connect the frontend to the new backend API.

## Technical Considerations
- **Kite API & Caching**: We must leverage the existing Kite API cache workflow (`CandleDataService` and DB) to efficiently get historical data without hitting Kite rate limits excessively.
- **Data Completeness**: If the DB does not have the required ~400 days of history for a symbol, the backend must initiate a fetch from Kite before processing.
- **Performance**: Processing a large CSV might take time if many symbols require Kite API fetches. The frontend should show a loading state, and the backend should handle rate-limiting correctly.

## Out of Scope
- Scoring or generating actionable trading signals (this is Phase 1: data extraction only).
- Other rules from the 52w-momentum strategy (e.g., volume drying up).
