# Requirement: Pre-defined Tagging System

## Problem Statement
Currently, the watchlist allows "wild-west" custom tagging. Users can create any number of tags with arbitrary names and colors. This makes it impossible to build reliable strategy-specific screens (like Remora or Weekly Scanner) because there's no guarantee that the tags used by the user match the tags expected by the strategy.

## Goal
Shift from free-form tagging to a **Configuration-First Tagging** system. The system should only allow stocks to be assigned tags that are pre-defined in a central configuration file.

## User Story
As a trader, when I add a stock to my watchlist or edit an existing one, I want to select from a list of standard tags (e.g., "Remora", "Weekly", "Momentum") so that my stocks are correctly categorized for the automated scanners and strategy dashboards.

## Acceptance Criteria
1.  **Centralized Config:** Use `watchlist_config.json` as the single source of truth for all valid tags.
2.  **Restricted Input (Frontend):** The "Add/Edit Stock" UI must replace the free-text tag input with a selection component (Dropdown/Select) containing only the pre-defined tags.
3.  **Backend Validation (Security):** The backend must validate that any tags being saved to a stock exist in the `watchlist_config.json`.
4.  **Data Consistency:** Strategies (Remora, etc.) should only fetch and process stocks that have their corresponding tag assigned.

## Technical Considerations
-   **Data Source:** `WatchlistConfigService.kt` already loads `watchlist_config.json`.
-   **API:** We need an endpoint (already exists as `GET /api/watchlist/config` or similar) to provide these tags to the frontend.
-   **Storage:** The `tags` JSONB column in the `stocks` table remains the same, but the *contents* are now restricted.

## Out of Scope
-   Allowing users to create new tags via the UI (for now). Tag creation remains a manual change to `watchlist_config.json`.
-   Renaming existing tags in the database (this would require a migration script).

## Complexity Estimate
-   **Backend:** 2-3 hours (validation logic + potential new endpoint).
-   **Frontend:** 3-4 hours (UI refactoring for the tag selector).
-   **Total:** ~1 day.
