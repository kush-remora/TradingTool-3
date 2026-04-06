# Implementation Plan: Pre-defined Tagging

## Overview
Reconfigure both backend and frontend to enforce the use of tags from `watchlist_config.json`.

---

## 🏗️ Backend Changes (Kotlin)

### 1. `WatchlistConfigService.kt`
- Ensure this service reliably returns all defined tags.
- Add a helper method `isValidTag(tagName: String): Boolean` to check if a tag name exists in the configuration.

### 2. `StockService.kt`
- In `create(input: CreateStockInput)`: Filter/Validate the `tags` list. If any tag in the input is not in the `WatchlistConfigService`, either reject the request (400 Bad Request) or drop the invalid tags.
- In `update(id: Long, payload: UpdateStockPayload)`: Similar validation for the incoming tags.

### 3. `StockResource.kt` (Optional)
- Add or verify an endpoint to expose the configuration tags to the frontend (e.g., `GET /api/stocks/config/tags`). This is better than relying on `listAllTags()` which only returns tags *already assigned* to stocks.

---

## 🎨 Frontend Changes (React + Ant Design)

### 1. `useWatchlistConfig.ts` (New Hook)
- Create a hook to fetch the pre-defined tags from the new backend endpoint.

### 2. `StockEntryDrawer.tsx`
- Replace the current "Add Tag" UI (Text input + Color picker) with an Ant Design `<Select mode="multiple">` component.
- The options for the `<Select>` should be the pre-defined tags from the config.
- When a user selects a tag, it should automatically use the color defined in the config.

---

## 🧪 Validation Strategy
1.  **Unit Tests:** Add tests to `StockService` to ensure it rejects invalid tags.
2.  **Manual UI Test:** Verify that the "Add Stock" drawer only shows the pre-defined tags and no longer allows typing custom ones.
3.  **Data Persistence:** Confirm that a stock saved with a tag like "Remora" actually appears on the Remora screen.
