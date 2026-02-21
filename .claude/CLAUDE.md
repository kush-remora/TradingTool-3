# Trading Tool — Claude Context

## Project Overview
Personal weekend project. Solo developer. Prioritize **simplicity and maintainability** over cleverness.
When in doubt, choose the simpler solution.

## Interaction Style
**Before writing any code, always state your understanding of the task in 2-4 sentences.**
Wait for the user to confirm before starting implementation.
Only skip this step for trivial one-liner changes (typos, renaming a variable, etc.).

## Development Phase
**This project is in active development. There are no backward compatibility or migration constraints.**
- Feel free to rename, restructure, delete, or rewrite any code, files, or DB schema
- Do not add deprecation shims, compatibility layers, or migration guards
- If a better structure is found, refactor directly — no need to preserve the old one
- DB migrations in `db/migrations/` are applied manually to Supabase; dropping and recreating tables is fine

## Stack
| Layer | Tech |
|-------|------|
| Backend | Kotlin + Dropwizard 4.x (Jakarta EE) |
| Database ORM | JDBI3 |
| Database | Supabase (PostgreSQL) |
| Frontend | React (functional components + hooks only) |
| Build Tool | Maven (`pom.xml`) |

## Architecture
```
/
├── backend/         # Kotlin + Dropwizard API
├── frontend/        # React app
├── db/
│   └── migrations/  # SQL migration files (numbered: 001_init.sql)
└── CLAUDE.md
```

## Reference Docs (Always Read Before Coding)

Before writing or modifying code, read the relevant doc in `docs/`:

| Doc | When to read |
|-----|-------------|
| [`docs/intellij-maven-kotlin-setup.md`](../docs/intellij-maven-kotlin-setup.md) | Setting up local dev environment, running the service |
| [`docs/kotlin-migration-plan.md`](../docs/kotlin-migration-plan.md) | Understanding migration phases, API contract that must be preserved |
| [`docs/telegram-webhook-setup.md`](../docs/telegram-webhook-setup.md) | Telegram webhook env vars, registering/removing webhook on Render |
| [`docs/watchlist-api.md`](../docs/watchlist-api.md) | Watchlist REST endpoint surface — must be preserved exactly |
| [`docs/watchlist-schema.md`](../docs/watchlist-schema.md) | DB schema shape, table names, key SQL patterns |
| [`docs/kite-connect-api.md`](../docs/kite-connect-api.md) | Kite Connect SDK reference: auth, historical data, orders, WebSocket, rate limits, module placement |

## Backend Conventions (Kotlin + Dropwizard 4.x)
- Use **Dropwizard 4.x** with **Jakarta EE** (`jakarta.ws.rs.*`, not `javax.ws.rs.*`)
- Use `@Path`, `@GET`, `@POST`, `@Produces`, `@Consumes` from `jakarta.ws.rs`
- Use **JDBI3** for all database access — no raw JDBC (Dropwizard has native JDBI3 support)
- Keep resources in `resources/` folder, one file per domain (e.g. `TradeResource.kt`)
- Use data classes for request/response models
- Prefer simple functions over complex class hierarchies
- Handle errors with Dropwizard `ExceptionMapper`
- Use **Jackson** for JSON serialization/deserialization (Dropwizard includes it by default)
- Register Kotlin Jackson module (`jackson-module-kotlin`) for data class support

## Database Conventions (Supabase / PostgreSQL)
- Supabase is the **single source of truth**
- All schema changes via numbered SQL migration files in `db/migrations/`
- Use snake_case for table and column names
- Always add `created_at` and `updated_at` timestamps
- Use Supabase Row Level Security (RLS) only if needed — skip for personal use initially

## Frontend Conventions (React + Ant Design)
- Functional components only — no class components
- **No raw HTML or CSS** — use Ant Design components exclusively for UI
- Use `lightweight-charts` + `lightweight-charts-react` for all trading charts
- Keep components small and focused
- Use plain `fetch` or a simple wrapper — avoid heavy libraries unless needed
- Store API base URL in `.env` as `VITE_API_URL`
- Simple folder structure: `components/`, `pages/`, `hooks/`, `utils/`

## Frontend Dependencies
```json
{
  "antd": "^6.3.0",
  "lightweight-charts": "^5.1.0",
  "lightweight-charts-react": "^0.0.2",
  "react": "^19.2.0",
  "react-dom": "^19.2.0"
}
```

## API Design
- RESTful endpoints: `GET /trades`, `POST /trades`, `DELETE /trades/:id`
- JSON request/response
- Return meaningful HTTP status codes
- Keep API versioning simple — no versioning needed for personal tool

## Simplicity Rules (ALWAYS APPLY)

### Core Principles
- **Readability > Optimization** — not building HFT, understanding code 6 months from now matters most
- **Explicit > Implicit** — avoid magic, code should be traceable top-to-bottom
- **YAGNI** — build only what's needed *today*, no hypothetical features
- **Low Cognitive Load** — understand a function without jumping through 5 files

### Size Limits
- **Class Length:** Max 200 lines — refactor trigger
- **Function Length:** Target < 50 lines — extract helpers if longer

### Anti-Patterns (Reject These)
- No premature optimization before profiling proves need
- No `AbstractFactoryStrategyBuilder` when simple class works
- No inheritance deeper than 2 levels — prefer composition
- No clever one-liners — break into readable steps

### Behavior
- Generate simple, linear implementations
- If code is complex, propose refactor to simplify
- If file grows large, suggest splitting
- Prefer well-maintained libs with minimal dependencies
- Ask before adding a new dependency
- Add brief comments on *why*, not *what*

## Environment Variables
```
# Backend
DATABASE_URL=
PORT=8080

# Kite Connect
KITE_API_KEY=
KITE_API_SECRET=
KITE_USER_ID=
KITE_REDIRECT_URL=

# Frontend
VITE_API_URL=http://localhost:8080
```
