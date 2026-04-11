# TradingTool Codex Operating Guide

This repository is a **single-user personal trading tool** for Kush.
Optimize for **clarity, maintainability, and speed of iteration** over scalability.

## Project Context

- Owner and primary user: Kush
- Team size: 1 engineer (backend-focused)
- Typical scale: watchlist of around 20-30 stocks
- Objective: stable, understandable system that is easy to evolve

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
- Surface trade-offs only when they materially affect delivery.
- Ask clarifying questions only when necessary; otherwise make reasonable assumptions and proceed.

## Agent Operating Rules

### 1) Plan Node Default
- Enter plan mode for ANY non-trivial task (3+ steps or architectural decisions)
- If something goes sideways, STOP and re-plan immediately - don't keep pushing
- Use plan node for verification steps, not just building
- Write detailed specs upfront to reduce ambiguity

### 2) Subagent Strategy
- Use subagents liberally to keep main context window clean
- Offload research, exploration, and parallel analysis to subagents
- For complex problems, throw more compute at it via subagents
- One task per subagent for focused execution

### 3) Self-Improvement Loop
- After ANY correction from the user: update `tasks/lessons.md` with the pattern
- Write rules for yourself that prevent the same mistake
- Ruthlessly iterate on these lessons until mistake rate drops
- Review lessons at session start for relevant project

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

- **Plan First**: Write plan to `tasks/todo.md` with checkable items
- **Verify Plan**: Check in before starting implementation
- **Track Progress**: Mark items complete as you go
- **Explain Changes**: High-level summary at each step
- **Document Results**: Add review section to `tasks/todo.md`
- **Capture Lessons**: Update `tasks/lessons.md` after corrections

## Core Principles

- **Simplicity First**: Make every change as simple as possible. Impact minimal code.
- **No Laziness**: Find root causes. No temporary fixes. Senior developer standards.
- **Minimal Impact**: Changes should only touch what's necessary. Avoid introducing bugs.