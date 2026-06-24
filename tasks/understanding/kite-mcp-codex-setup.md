June 24, 2026

The goal is to connect Codex desktop to Zerodha's Kite MCP server using Zerodha's published remote MCP endpoint. The current machine already has a `kite` entry in `/Users/kushbhardwaj/.codex/config.toml`, but this thread does not expose Kite tools yet, so the likely gap is Codex-side remote MCP enablement and/or OAuth login plus app reload.

Plan: keep the existing Kite server config minimal, enable the remote MCP client feature if needed, attempt the Codex login flow for `kite`, and verify whether a restart or new thread is required for tool discovery. Validation will be based on the Codex config state, CLI/login feedback, and whether Kite tools become available after reload.
