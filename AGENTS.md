# TradingTool Codex Operating Guide

This repository is a **single-user personal trading tool** for Kush.
Optimize for **clarity, maintainability, and speed of iteration** over scalability.

## Project Context

- Owner and primary user: Kush
- Team size: 1 engineer (backend-focused)
- Typical scale: watchlist of around 20-30 stocks
- Objective: stable, understandable system that is easy to evolve
- Primary product direction: **Project Composite — Wyckoff Institutional Bloodhound**
- Current status: **Phase 1 build**
- Market scope: NSE-focused

## Primary Product Philosophy (Wyckoff First)

Treat Wyckoff as the main product lens for strategy decisions and implementation.

- Core mental model: market behavior is driven by institutional campaigns ("Composite Man").
- Build anomaly-first, not pattern-first: detect mathematical supply-exhaustion footprints first, then validate structural context.
- Avoid generic scanner behavior. If a feature does not improve Wyckoff accumulation/distribution interpretation, de-prioritize it.
- Prefer evidence from price/volume behavior over narrative assumptions.
- Keep terminology and logic aligned to Wyckoff laws, events, and phases.

## Wyckoff Canonical Constraints

Use these principles as non-negotiable design constraints:

- Law of Supply and Demand: direction inference must come from price/volume balance.
- Law of Cause and Effect: trading-range "cause" should map to projected move "effect".
- Law of Effort vs Result: treat price/volume divergence as a potential turning-point signal.
- Accumulation interpretation should remain explicit around PS, SC, AR, ST, Spring/Test, SOS, and LPS.
- Distribution interpretation should remain explicit around PSY, BC, AR, ST/UT, SOW, LPSY, and UTAD.
- Never treat a raw spike as accumulation without context validation.

## Phase 1 Scope (Current Build Target)

Phase 1 is the active delivery scope and should be prioritized over Phase 2/3 enhancements.

- Goal: backward-sweep anomaly scanner for **Wyckoff Stage 1 accumulation floor detection**.
- Universe:
  - Track A/B: NIFTY 50, NIFTY 100, NIFTY MIDCAP 50, NIFTY MIDCAP 150, NIFTY SMALLCAP 50, NIFTY SMALLCAP 250, NIFTY MICROCAP 250
  - Track C: curated 50-100 high-growth/sector-leader list
- Data cadence: daily post-market ingestion (Bhavcopy + delivery), target run around 18:30 IST.
- Phase 1 anomaly rules:
  - Historical anchors: HVQ/HVY/HVE and LVQ/LVY
  - Cap-adaptive delivery thresholds:
    - Large/Mid: >55%
    - Small: >70%
    - Micro: >85%
    - Nano: >92%
  - Rolling density: threshold breach >= 4 times in rolling 15-day window
  - Math shock: delivery volume Z-score >= 2.0 on 60-day baseline
  - Absorption check: daily spread < 20-day average spread
- Track C exception: allow absolute volume-shock logic near structural support without delivery-% gating (denominator-flaw workaround).

## Phase 2/3 Guardrails (Not Current Build Priority)

- Phase 2 context filter ("4-brick base check"):
  - Distance from 200 DMA between -5% and +5%
  - ROC-20 between -5% and +5%
- Phase 3 deployment rule:
  - Do not buy raw anomaly (avoid Phase B time trap)
  - Prefer trigger-based entry on Phase C exhaustion or Phase D launch conditions

## Global Engineering Priorities
1. Readability over cleverness
2. Simple implementations over abstractions
3. Maintainability over premature optimization
4. Minimal diffs that solve today’s problem
5. Refactor when code becomes hard to understand

## Delivery Rules

- Prefer extending existing code paths before introducing new layers.
- Avoid speculative architecture and future-proofing unless explicitly asked.
- Keep function and class responsibilities narrow and obvious.
- Use strict typing practices (Python type hints, avoid `Any` where possible).
- Run relevant tests/checks after code changes and report what was run.

## Local Service Runbook

- If backend startup fails because port `8080` is in use, first find the process:
  - `lsof -i tcp:8080`
- Kill the PID that is listening on `*:http-alt` / `*:8080`:
  - `kill <PID>`
  - If it does not stop, force kill: `kill -9 <PID>`
- Start the service only after confirming the port is free:
  - `lsof -i tcp:8080`

## Role Activation Framework

Use these roles based on task type. Default to one role; combine only when needed.

### 1) PM Role (Business and Product Clarity)

Use when: feature ideas are vague, trading logic needs product framing, or priority decisions are unclear.

Reference: `.agents/workflows/pm.md`

Output expectation:
- Problem statement
- User story
- Acceptance criteria
- Technical considerations (no code)
- Out of scope
- Rough complexity estimate

### 2) Architecture Role (Structure and Boundaries)

Use when: changes affect module boundaries, data flow, service contracts, or long-term maintainability.

Responsibilities:
- Keep architecture simple and explicit
- Challenge unnecessary complexity
- Choose boring, proven patterns
- Call out hidden coupling and operational risk

Guardrail:
- Do not introduce distributed-system-style complexity for single-user workloads unless explicitly required.

### 3) Code Simplifier Role (Readability and Cleanup)

Use when: code is hard to read, overly nested, duplicated, or abstraction-heavy for the current need.

Responsibilities:
- Reduce cognitive load
- Remove indirection that does not add value
- Improve naming and structure
- Preserve behavior while simplifying

## Decision Heuristics

- If a choice is between “clean simple code now” vs “scalable architecture later,” choose simple now.
- If optimization does not solve a measured bottleneck, skip it.
- If a refactor improves readability for future-you (6+ months), do it.
- If a change increases complexity without immediate user value, reject it.

## Communication Style

- Be direct and concise.
- Prefer responses of 100 words or fewer when that still preserves necessary context.
- Go longer when brevity would hide important risks, tradeoffs, or verification details.
- Surface trade-offs only when they materially affect delivery.
- Ask clarifying questions only when necessary; otherwise make reasonable assumptions and proceed.
- Use `Standard` and `Crisp Summary` sections for plans, reviews, and longer explanations.
- Skip forced two-section formatting for tiny updates, simple answers, and quick status messages.
- Default to `Crisp Summary` first when the user explicitly asks for short output.

## Instruction Precedence

- Follow repo instructions when they fit the active tool and runtime constraints.
- If a requested workflow is unavailable in the current environment, use the closest practical equivalent and say so briefly.
- Prefer practical compliance over literal-but-brittle process when the two conflict.

## Agent Operating Rules

### 1) Plan Node Default
- For non-trivial tasks (3+ steps, cross-module edits, or architectural decisions), create an explicit plan before implementation.
- Record the current understanding in a discussion document under `tasks/understanding/`; use a concise in-thread checklist only for tiny tasks.
- If something goes sideways, stop and re-plan instead of pushing through confusion.
- Include verification in the plan, not just implementation.
- Write specs that remove ambiguity, but avoid ceremony that does not help execution.

### 2) Subagent Strategy
- Use subagents selectively when they reduce context load or enable meaningful parallel work.
- Offload research, exploration, or isolated analysis when it clearly helps.
- Avoid subagents for tiny, linear tasks where direct execution is simpler.
- Give each subagent one clear task.

### 3) Self-Improvement Loop
- After a meaningful correction or repeated miss, update `tasks/lessons.md` with the pattern.
- Write lessons as concrete prevention rules, not vague reminders.
- Review relevant lessons before similar tasks when they apply.

### 4) Verification Before Done
- Never mark a task complete without proving it works
- Diff behavior between main and your changes when relevant
- Ask yourself: "Would a staff engineer approve this?"
- Run tests, check logs, demonstrate correctness

### 5) Demand Elegance (Balanced)
- For non-trivial changes: pause and ask "is there a more elegant way?"
- If a fix feels hacky: "Knowing everything I know now, implement the elegant solution"
- Skip this for simple, obvious fixes - don't over-engineer
- Challenge your own work before presenting it

### 6) Autonomous Bug Fixing
- When given a bug report: just fix it. Don't ask for hand-holding
- Point at logs, errors, failing tests - then resolve them
- Zero context switching required from the user
- Go fix failing CI tests without being told how

## Task Management

- **Start With Understanding**: For each new project or discussion, create a discussion-specific understanding document under `tasks/understanding/`.
- **Keep It Live**: Update that document as the conversation evolves so the current understanding stays explicit.
- **Keep It Short**: Prefer a compact 2-paragraph understanding note over a large template or checklist.
- **Keep Context Safe**: Do not overwrite unrelated discussion documents; update only the active one.
- **Skip Ceremony When Small**: Tiny one-step requests do not need a formal understanding document unless they open a broader discussion.
- **Document Results**: If work was substantial, end by updating the same understanding document with the final understanding, decisions, and validation outcome.
- **Capture Lessons**: Update `tasks/lessons.md` after corrections
- **Feature Journal (Mandatory)**: After each feature implementation, create or update a day-specific doc inside that feature's module docs (for example `wyckoff-market-cycle/docs/journeys/2026-05-16.md`) with:
  - feature name and why it was built
  - what was implemented
  - key decisions/tradeoffs
  - validation run and outcomes
  - next follow-ups

## Core Principles

- **Simplicity First**: Make every change as simple as possible. Impact minimal code.
- **No Laziness**: Find root causes. No temporary fixes. Senior developer standards.
- **Minimal Impact**: Changes should only touch what's necessary. Avoid introducing bugs.
- **Wyckoff Fidelity**: Preserve institutional-footprint interpretation over generic technical heuristics.

## Mandatory Skill Invocation

For implementation tasks in this repo, invoke `coding-standards` plus the area-relevant skills below before coding:

- `coding-standards`
- `backend-architect`
- `kotlin-patterns`
- `frontend-patterns`
- `kotlin-reviewer`

Implementation task means any task that changes:
- application code
- config
- schema
- API behavior
- UI behavior
- or architecture

Documentation-only tasks and planning-only tasks do not require this five-skill workflow.

Required implementation workflow:
- `coding-standards` is required for every implementation task.
- `backend-architect` is required for backend, data-flow, contract, model, and API work.
- `frontend-patterns` is required for UI, rendering, state, and data-presentation work.
- `kotlin-patterns` is required when Kotlin implementation structure is being changed.
- `kotlin-reviewer` is required as the review gate for Kotlin-related changes.
- If a relevant skill is unavailable in the current environment, note that briefly and continue with the closest practical equivalent.

Completion rules:
- Kotlin changes are not complete until `kotlin-reviewer` has been run as the review pass.
- Frontend changes are not complete unless `frontend-patterns` has been consulted.
- Backend, data-model, and API changes are not complete unless `backend-architect` has been consulted.
- `coding-standards` is always required, regardless of task area.

