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

## New Developer Setup

### 1. Set up the code

Prerequisites:

- JDK 21
- Maven 3.9+
- Node.js 20+ and npm
- Docker (Docker Desktop recommended)

Install and verify Docker:

- macOS (Homebrew): `brew install --cask docker`
- Windows: install Docker Desktop from https://www.docker.com/products/docker-desktop/
- Linux: install Docker Engine from https://docs.docker.com/engine/install/
- Start Docker, then verify:
  ```bash
  docker version
  docker run --rm hello-world
  ```

Clone and install frontend dependencies:

```bash
git clone <your-repo-url>
cd TradingTool-3
npm --prefix frontend install
```

Set required backend environment variable:

```bash
export SUPABASE_DB_URL="jdbc:postgresql://db.<project-ref>.supabase.co:5432/postgres?sslmode=require"
```

### 2. Run frontend and backend locally

Run backend (terminal 1):

```bash
mvn -f pom.xml -pl service -am package -DskipTests
java -jar service/target/service-0.1.0-SNAPSHOT.jar server service/src/main/resources/localconfig.yaml
```

Run frontend (terminal 2):

```bash
npm --prefix frontend run dev -- --host 0.0.0.0 --port 5173
```

Open:

- Frontend: http://localhost:5173
- Backend health: http://localhost:8080/health

Alternative: run both with one command:

```bash
./run-local.sh
```

Build and run backend with Docker (optional):

```bash
docker build -t trading-tool-backend .
docker run --rm --name trading-tool-backend-local -p 8080:8080 trading-tool-backend
```

### 3. Debug if you face issues

- Check backend health quickly:
  ```bash
  curl http://localhost:8080/health
  curl http://localhost:8080/health/config
  ```
- If backend fails to start, verify:
  - `SUPABASE_DB_URL` is set in the same shell session.
  - `service/src/main/resources/localconfig.yaml` exists.
- If frontend cannot call backend, confirm backend is running on `localhost:8080`.
- If ports are busy (`8080` or `5173`), stop existing listeners:
  ```bash
  lsof -ti tcp:8080 -sTCP:LISTEN | xargs kill -9
  lsof -ti tcp:5173 -sTCP:LISTEN | xargs kill -9
  ```
- If Docker build fails while pulling base images (for example TLS timeout), retry once and check network/proxy/VPN connectivity to Docker Hub.
- Rebuild and rerun backend after config/dependency changes:
  ```bash
  mvn -f pom.xml -pl service -am clean package -DskipTests
  ```

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
