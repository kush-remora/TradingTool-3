# Kite Access Token — Daily Refresh

Access tokens expire at **6:00 AM IST every day**. Run this once each morning before using the tool.
The only source of truth is the `kite_tokens` table.

---

## Steps

### 1. Run the script

```bash
cd /Users/kushbhardwaj/Documents/github/TradingTool-3
python3 scripts/generate_kite_token.py
```

### 2. Log in on Zerodha

The script opens this URL in your browser automatically:
```
https://kite.zerodha.com/connect/login?v=3&api_key=6md1efchysjydws2
```
Enter your Zerodha credentials + TOTP.

### 3. Copy the redirect URL

After login, Zerodha redirects you to a URL like:
```
https://tradingtool-3-service.onrender.com/kite/callback?request_token=XXXXXXXXXXXXXXXX&status=success
```
Copy the **full URL** from your browser address bar.

> The page may show an error — that's fine. You only need the URL.

### 4. Paste into the script

Paste the full URL (or just the `request_token` value) when prompted.

### 5. Let the backend persist it

The script calls the backend `/kite/callback` endpoint.
That endpoint exchanges the request token and saves the new access token into:
```
public.kite_tokens
```

### 6. Restart the backend

The backend reads the latest row from `kite_tokens` at startup. Restart it to pick up the new token.
On startup you should see:
```
[InstrumentCache] Loaded XXXX NSE instruments at startup
```

---

## Environment behavior

Local and Render now behave the same way:
- the backend starts only if a token exists in `kite_tokens`
- `/kite/callback` stores refreshed tokens in `kite_tokens`
- config files and env vars are no longer used for `accessToken`

---

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| Search returns nothing | Cache empty — Kite not authenticated | Re-run token script, restart backend |
| `Token exchange failed` | Wrong `request_token` or already used | Re-do steps 2–4 (each `request_token` is one-time use) |
| `KiteException 403` | Token expired after 6 AM IST | Re-run token script |
