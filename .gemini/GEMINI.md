# ECC for Gemini CLI

This file provides Gemini CLI with the baseline ECC workflow, review standards, and security checks for repositories that install the Gemini target.

## Overview

Everything Claude Code (ECC) is a cross-harness coding system with 36 specialized agents, 142 skills, and 68 commands.

Gemini support is currently focused on a strong project-local instruction layer via `.gemini/GEMINI.md`, plus the shared MCP catalog and package-manager setup assets shipped by the installer.

## Documentation and Tracking

1. **Continuous Documentation:** Every time we discuss a new task, a new requirement, or any architectural decision, it MUST be documented immediately. 
2. **Task Tracker:** Maintain a single file (e.g., `TRACKER.md`) that acts as the source of truth for all active, pending, and completed work. This document should be updated at the end of every significant turn or decision to ensure continuity.
3. **Traceability:** Documentation should be structured so that a return to the project after several days allows for immediate context recovery.
4. **Sanity Checks:** After every task, the following must be performed:
    - Build the backend to ensure no compilation errors.
    - Build the frontend to ensure no compilation errors.
    - Confirm that the application starts as expected.

## Core Workflow

1. Plan before editing large features.
2. Prefer test-first changes for bug fixes and new functionality.
3. Review for security before shipping.
4. Keep changes self-contained, readable, and easy to revert.

## Coding Standards

- Prefer immutable updates over in-place mutation.
- Keep functions small and files focused.
- Validate user input at boundaries.
- Never hardcode secrets.
- Fail loudly with clear error messages instead of silently swallowing problems.

## Security Checklist

Before any commit:

- No hardcoded API keys, passwords, or tokens
- All external input validated
- Parameterized queries for database writes
- Sanitized HTML output where applicable
- Authz/authn checked for sensitive paths
- Error messages scrubbed of sensitive internals

## Delivery Standards

- Use conventional commits: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`, `perf`, `ci`
- Run targeted verification for touched areas before shipping
- Prefer contained local implementations over adding new third-party runtime dependencies

## ECC Areas To Reuse

- `AGENTS.md` for repo-wide operating rules
- `skills/` for deep workflow guidance
- `commands/` for slash-command patterns worth adapting into prompts/macros
- `mcp-configs/` for shared connector baselines
