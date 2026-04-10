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
