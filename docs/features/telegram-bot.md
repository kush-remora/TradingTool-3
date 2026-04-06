# Telegram Webhook Setup (Render + Ktor)

Use this when deploying Telegram updates via Ktor webhook endpoint.

## 0. Render Blueprint file

This repo now includes:

- `render.yaml`

Render can auto-create/update service config from this file on GitHub push.

## 1. Required env vars

Set in Render service environment:

```bash
TELEGRAM_BOT_TOKEN=<bot_id>:<secret_from_botfather>
TELEGRAM_CHAT_ID=974882412
TELEGRAM_WEBHOOK_SECRET=<random_secret_string>
TELEGRAM_DOWNLOAD_DIR=data/telegram_downloads
RENDER_EXTERNAL_URL=https://tradingtool-3.onrender.com
GITHUB_PAGES_URL=https://kushb2.github.io/TradingTool-3/
```

If using `render.yaml`, Render will prompt for secret values where `sync: false`.

## 2. Start Ktor service

Render Start Command:

```bash
mvn -pl service -am exec:java -Dexec.mainClass=com.tradingtool.ApplicationKt
```

If using `render.yaml`, this is already configured.

## 3. Register webhook with Telegram

Run once after deploy:

```bash
curl -sS -X POST "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/setWebhook" \
  --data-urlencode "url=${RENDER_EXTERNAL_URL}/telegram/webhook" \
  --data-urlencode "secret_token=${TELEGRAM_WEBHOOK_SECRET}"
```

## 4. Check webhook status

```bash
curl -sS "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/getWebhookInfo"
```

## 5. Remove webhook (if needed)

```bash
curl -sS -X POST "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/deleteWebhook"
```

## 6. Important operational rule

Do not run webhook and long polling at the same time for the same bot token.
