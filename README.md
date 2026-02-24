# TradingTool-3

Kotlin migration bootstrap for the TradingTool backend.

## Module layout

- `cron-job`: scheduled/background jobs (empty scaffold)
- `core`: core business logic and shared domain (empty scaffold)
- `service`: API/service layer (active module with current Ktor app)
- `event-service`: event-driven processing module (empty scaffold)


## Stack

- Kotlin 2.3.10
- JDK 21
- Maven
- Ktor 3.4.0

## Run locally

```bash
mvn clean test
mvn -f pom.xml -pl service -am package -DskipTests
java -jar service/target/service-0.1.0-SNAPSHOT.jar server /Users/kushbhardwaj/Documents/github/TradingTool-3/service/src/main/resources/localconfig.yaml
```

Production startup:

```bash
java -jar service/target/service-0.1.0-SNAPSHOT.jar server service/src/main/resources/serverConfig.yml
```

## Frontend (React + Ant Design)

Frontend lives in `frontend/` and uses:

- `react`
- `antd`
- `lightweight-charts-react`

Note: `lightweight-charts-react` has legacy React peer metadata; install is configured via `frontend/.npmrc` (`legacy-peer-deps=true`).

Run frontend:

```bash
cd frontend
npm install
npm run dev
```

Build frontend:

```bash
cd frontend
npm run build
```

Server settings are in:

- `service/src/main/resources/serverConfig.yml`
- `service/src/main/resources/localconfig.yaml`
- Environment variables override YAML values when both are present.

Health checks:

```bash
curl http://localhost:8080/
curl http://localhost:8080/health
curl http://localhost:8080/health/config
```

## Kotlin migration docs

- `docs/kotlin-migration-plan.md`
- `docs/kotlin-dependencies.md`
- `docs/intellij-maven-kotlin-setup.md`
- `docs/telegram-webhook-setup.md`

Render deployment blueprint:

- `render.yaml`

## Migrated keys

The full migrated key set is documented in:

- `.env.example`

Current key groups:

- Telegram: `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID`, `TELEGRAM_WEBHOOK_SECRET`, `TELEGRAM_DOWNLOAD_DIR`, timeout/retry keys
- Supabase: `SUPABASE_DB_URL`
- Deployment: `RENDER_EXTERNAL_URL`, `GITHUB_PAGES_URL`, `CORS_ALLOWED_ORIGINS`

Set these as real environment variables in your shell/CI/Render runtime.
