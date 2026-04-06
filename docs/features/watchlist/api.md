# Watchlist API (FastAPI)

Base prefix: `/api/watchlist`

## Table Check
- `GET /tables`

## Stocks
- `POST /stocks`
- `GET /stocks`
- `GET /stocks/{stock_id}`
- `GET /stocks/by-symbol/{nse_symbol}`
- `PATCH /stocks/{stock_id}`
- `DELETE /stocks/{stock_id}`

## Watchlists
- `POST /lists`
- `GET /lists`
- `GET /lists/{watchlist_id}`
- `GET /lists/by-name/{name}`
- `PATCH /lists/{watchlist_id}`
- `DELETE /lists/{watchlist_id}`

## Watchlist Items
- `POST /items`
- `GET /items/{watchlist_id}/{stock_id}`
- `GET /lists/{watchlist_id}/items`
- `PATCH /items/{watchlist_id}/{stock_id}`
- `DELETE /items/{watchlist_id}/{stock_id}`
