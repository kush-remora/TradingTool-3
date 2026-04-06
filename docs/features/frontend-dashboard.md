# Frontend Dashboard

Single-page watchlist management UI built with React + Ant Design (light theme).

## Layout

```
┌──────────────────────────────────────────────────────┬────────────────────────────┐
│ Main Content (left)                                  │ Right Panel (menu)         │
│                                                      │ Watchlist Management       │
│ - View Watch List                                    │ - View Watch List          │
│ - Create/Delete Watchlist                            │ - Create/Delete Watchlist  │
│ - Create/Update Stock                                │ - Create/Update Stock      │
│ - View Stock                                         │ - View Stock               │
└──────────────────────────────────────────────────────┴────────────────────────────┘
```

- **Left**: Active page content (forms/tables/actions)
- **Right**: Fixed navigation panel with menu options under **Watchlist Management**

## Files

| File | Purpose |
|------|---------|
| `frontend/src/App.tsx` | Root light-theme layout, right-side menu, page rendering, CRUD wiring |
| `frontend/src/hooks/useWatchlists.ts` | Fetch/create/delete watchlists |
| `frontend/src/hooks/useStocks.ts` | Fetch/create/update stocks |
| `frontend/src/hooks/useWatchlistItems.ts` | Fetch/add/remove stocks inside a selected watchlist |
| `frontend/src/utils/api.ts` | Shared HTTP helpers with parsed backend error messages |
| `frontend/src/types.ts` | Shared TypeScript types |

## Pages

### 1. View Watch List

- Select watchlist
- View selected watchlist details
- Add stock to selected watchlist
- Remove stock from selected watchlist
- Add/remove tags on selected watchlist
- View table of assigned stocks

### 2. Create or Delete Watchlist

- Create watchlist using:
  - `name` (required)
  - `description` (required)
- Delete existing watchlists from table actions

### 3. Create or Update Stock

- Create stock using:
  - `symbol`
  - `instrumentToken`
  - `companyName`
  - `exchange`
  - `description` (optional)
  - `priority` (optional positive integer)
- Update existing stock:
  - select stock
  - edit `companyName`, `exchange`, `description`, and/or `priority`
- Add/remove tags on selected stock

### 4. View Stock

- View all stocks in table format
- Search by symbol, company name, or exchange

## API Calls

| Feature | Endpoint | Method |
|---------|----------|--------|
| List watchlists | `/api/watchlist/lists?limit=200` | GET |
| Create watchlist | `/api/watchlist/lists` | POST |
| Delete watchlist | `/api/watchlist/lists/{id}` | DELETE |
| List stocks | `/api/watchlist/stocks?limit=1000` | GET |
| Create stock | `/api/watchlist/stocks` | POST |
| Update stock | `/api/watchlist/stocks/{id}` | PATCH |
| List tags | `/api/watchlist/tags?limit=200` | GET |
| List watchlist items | `/api/watchlist/lists/{watchlistId}/items` | GET |
| Add watchlist item | `/api/watchlist/items` | POST |
| Remove watchlist item | `/api/watchlist/items/{watchlistId}/{stockId}` | DELETE |
| List stock tags | `/api/watchlist/stocks/{stockId}/tags` | GET |
| Add stock tag | `/api/watchlist/stocks/{stockId}/tags` | POST |
| Remove stock tag | `/api/watchlist/stocks/{stockId}/tags/{tagId}` | DELETE |
| List watchlist tags | `/api/watchlist/lists/{watchlistId}/tags` | GET |
| Add watchlist tag | `/api/watchlist/lists/{watchlistId}/tags` | POST |
| Remove watchlist tag | `/api/watchlist/lists/{watchlistId}/tags/{tagId}` | DELETE |

## Theme

- Ant Design light theme (`theme.defaultAlgorithm`)
- Background: `#f3f6fb`
- Card background: `#ffffff`
- Border: `#d8dee8`
- Primary action color: `#2563eb`
