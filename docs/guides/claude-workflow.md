# Everything-Claude-Code Plugin Guide

> A practical reference for using the `everything-claude-code` plugin in this project.
> Plugin version: **1.9.0** — installed 2026-03-24.

---

## Project Context


This is a personal trading tool with the following stack:

| Layer | Technology |
|-------|-----------|
| Backend | **Kotlin + Dropwizard 4.x** (Jakarta EE, REST APIs) |
| ORM | **JDBI3** (SQL mapping, no raw JDBC) |
| Database | **Supabase (PostgreSQL)** (hosted, migrations in `db/migrations/`) |
| Frontend | **React 19 + TypeScript** (functional components, hooks only) |
| UI Library | **Ant Design 6** (no raw CSS) |
| Charts | **lightweight-charts** (all price/OHLC visuals) |
| Build | **Maven** (`pom.xml`, multi-module: `core/`, `resources/`, `service/`, `cron-job/`) |
| Live Data | **Kite Connect** (WebSocket ticks, historical OHLC, orders) |
| Messaging | **Telegram bot** (alerts via webhook) |
| Deploy | **Render** (backend service) |

The backend is split into Maven modules:
- `core/` — domain models, DAOs, strategy logic
- `resources/` — REST resource classes (one file per domain)
- `service/` — Dropwizard Application entry point, DI wiring
- `cron-job/` — scheduled jobs (indicator sync, Kite reminders)
- `event-service/` — SSE live price streaming

---

## How the Plugin Works

The plugin gives you three types of tools:

- **Commands** — slash commands you type (e.g. `/kotlin-review`). These are the main thing you use daily.
- **Agents** — specialist sub-processes Claude spins up automatically in the background. You rarely call these directly; commands invoke them.
- **Skills** — knowledge modules (patterns, rules) that agents load to do their job well. These are installed to `~/.claude/rules/` and work silently.

Think of it like a team: you give orders (commands), Claude assigns the right specialist (agent), and the specialist follows their training manual (skills/rules).

---

## Top Commands for This Project

These are the commands you should actually use, ordered by how often you'll reach for them.

### Daily Use

#### `/kotlin-review`
**What it does:** Reviews your Kotlin code like a senior engineer would. Checks for null-safety bugs (`!!` usage), coroutine mistakes, security issues (SQL injection, hardcoded secrets), and non-idiomatic patterns.

**When to use:** Before committing any Kotlin file — resource classes, services, DAOs.

```
/kotlin-review
```

---

#### `/code-review`
**What it does:** A general security and quality scan of everything you've changed since your last commit. Catches hardcoded API keys, functions that are too long, missing error handling, and frontend XSS risks.

**When to use:** Before any commit, especially when you've touched both backend and frontend in the same session.

```
/code-review
```

---

#### `/plan "description"`
**What it does:** Before writing a single line of code, Claude restates what you asked for, breaks it into phases, identifies risks, and **waits for your approval** before doing anything. Prevents building the wrong thing.

**When to use:** Whenever you're about to add a feature that touches more than one or two files. E.g., adding a new strategy, a new REST endpoint + frontend page, or a database schema change.

```
/plan "Add a P&L chart to the trade history page"
/plan "Create a new cron job that syncs option chain data every 15 minutes"
```

---

#### `/tdd "what to build"`
**What it does:** Forces the correct order — writes tests first, then writes the minimum code to make them pass. Targets 80%+ test coverage.

**When to use:** When adding new logic to `core/` (strategies, DAOs, services) where correctness matters.

```
/tdd "RemoraService signal detection logic"
```

---

### When Something Breaks

#### `/kotlin-test`
**What it does:** Runs your Kotlin tests, shows failures with context, and suggests fixes. Uses Kotest conventions.

**When to use:** When `./mvn test` fails and you want Claude to diagnose and fix it.

```
/kotlin-test
```

---

#### `/gradle-build`
**What it does:** Runs the Gradle/Maven build, parses every compilation error, fixes them one by one with minimal changes, and re-runs to confirm. Stops and asks you if it gets stuck.

**When to use:** When `./mvn compile` blows up with red errors and you want them fixed fast.

> Note: This project uses Maven, not Gradle — but this command still handles Kotlin compilation errors effectively.

```
/gradle-build
```

---

#### `/verify`
**What it does:** Full health check — runs build, tests, and static analysis in sequence. Like a pre-flight checklist before you push code.

**When to use:** Before pushing to `main` or creating a PR.

```
/verify
```

---

### Keeping Code Clean

#### `/refactor-clean`
**What it does:** Finds dead code — unused functions, unreferenced classes, leftover imports — and removes it safely, running tests after each deletion to make sure nothing breaks.

**When to use:** After a feature is done and you want to clean up the leftovers, or when a file is getting too long.

```
/refactor-clean
```

---

#### `/update-docs`
**What it does:** Scans the codebase and updates documentation to match reality — syncs env variable docs, endpoint references, and any generated docs from source files.

**When to use:** After adding new API endpoints, new env vars, or changing the DB schema.

```
/update-docs
```

---

### Frontend-Specific

There is no dedicated `/typescript-review` command shortcut, but the `typescript-reviewer` agent runs **automatically** when Claude writes or modifies `.tsx` / `.ts` files. You can also explicitly trigger it:

```
/code-review   ← covers TypeScript/React changes too
```

---

## Top Agents (Run Automatically)

You don't call these directly — Claude uses them behind the scenes. But knowing they exist helps you understand what's happening.

| Agent | What It Does for You |
|-------|---------------------|
| `kotlin-reviewer` | Reviews Kotlin code for null safety, coroutine bugs, Dropwizard patterns |
| `typescript-reviewer` | Reviews React/TypeScript for type safety, async issues, hook correctness |
| `database-reviewer` | Reviews SQL migrations and JDBI queries for performance and security (Supabase-aware) |
| `security-reviewer` | Scans API endpoints and input handling for injection, SSRF, exposed secrets |
| `planner` | Runs when you use `/plan` — creates step-by-step implementation plans (uses Claude Opus) |
| `architect` | Runs when making structural decisions — module layout, API design, system boundaries |
| `kotlin-build-resolver` | Automatically invoked when Kotlin builds fail — fixes compilation errors |
| `tdd-guide` | Runs when you use `/tdd` — enforces write-tests-first workflow |
| `refactor-cleaner` | Runs when you use `/refactor-clean` — dead code analysis and removal |
| `doc-updater` | Runs when you use `/update-docs` — keeps docs in sync with code |

---

## Top Skills (Loaded Silently)

Skills are the "rulebooks" agents follow. These are the most relevant ones for your stack, installed at `~/.claude/rules/`:

| Skill | Relevant To |
|-------|------------|
| `kotlin/coding-style.md` | Idiomatic Kotlin patterns, naming, data classes |
| `kotlin/security.md` | SQL injection via JDBI, safe coroutine patterns |
| `kotlin/testing.md` | Kotest conventions, MockK usage |
| `typescript/coding-style.md` | React hooks, TypeScript strict mode patterns |
| `typescript/security.md` | XSS prevention, safe fetch patterns |
| `common/git-workflow.md` | Commit message standards, branch discipline |
| `common/security.md` | OWASP Top 10, secrets management |
| `common/testing.md` | Test structure, coverage expectations |

---

## Recommended Workflow

Here's a practical habit loop for this project:

```
1. Starting a new feature?
   → /plan "describe what you want"
   → Read the plan, confirm or modify it
   → Claude implements it

2. Writing backend logic (Kotlin)?
   → /tdd "the unit being built"         (for core/ logic)
   → /kotlin-review                       (before committing)

3. Something broke?
   → /gradle-build                        (compilation errors)
   → /kotlin-test                         (test failures)

4. Before pushing?
   → /verify                              (full health check)
   → /code-review                         (security + quality)

5. Cleanup after a sprint?
   → /refactor-clean                      (remove dead code)
   → /update-docs                         (sync documentation)
```

---

## Quick Reference Card

| I want to... | Command |
|-------------|---------|
| Plan a feature before coding | `/plan "description"` |
| Review my Kotlin changes | `/kotlin-review` |
| Review everything (frontend + backend) | `/code-review` |
| Fix a broken build | `/gradle-build` |
| Run & fix failing tests | `/kotlin-test` |
| Write tests before code | `/tdd "what to build"` |
| Run full pre-push check | `/verify` |
| Remove dead/unused code | `/refactor-clean` |
| Keep docs up to date | `/update-docs` |
