# EY Visual Book Generator — Task Tracker

## Product direction correction

- [x] Define the product around explicit AI authority modes: Preserve, Restructure, and Transform.
- [x] Store the three authority-mode prompt contracts in `evi/prompts/`.
- [x] Replace the current layout-only Gemini contract with mode-specific prompts, outputs, and validation.

## Current task

- [x] Gemini configuration and first-page layout slice.
- [x] Folio schema, create/list UI, and private-PDF upload API.
- [x] Align selected-PDF UI and create readable private object names.
- [x] Page reader & text extraction slice: PDFBox 3.0.8 extraction, `folio_pages` persistence, `GET /api/folios/{id}/pages/{pageNumber}`, and Back/Next reader UI with URL resume.
- [x] Page image upload slot and authority-mode generation with interactive selection.
- [x] Gemini API observability: transparent request, response, and latency inspector surfaced in the UI and logs.
- [x] Multi-model resiliency: automatic cascade across active Gemini candidates (`gemini-3-flash-preview`, `gemini-3.1-flash-lite`, `gemini-3.8-flash`) to eliminate HTTP 503 high-demand errors.
- [x] Option 2: `@react-pdf/renderer` integration for visual book PDF composition and direct download.
- [x] Restructure and decouple all assignment code into a self-contained top-level `evi/` module (`evi/backend`, `evi/frontend`, `evi/prompts`, `evi/db`, and standalone `evi/README.md`).
- [x] Add authority-mode selection, prompt loading, structured authority generation, and a frontend contract-result preview under the isolated EVI module.
- [x] Register the authority endpoint in the root service and verify its CORS preflight and live POST path.
- [x] Remove the legacy layout-options API, service, frontend flow, and layout-only renderer.
- [x] Make Preserve return exactly two additive page-placement variants for user selection while preserving the original PDF page rendering.
- [ ] Review and test the user experience of image upload, Gemini layout generation, and selection on active folios.

## Next

- Persist page image files and selected layout ID into `folio_pages`.
- Deterministic final PDF compilation and signed download link.

## Submission

- Deploy the demo, record a walkthrough, and send the demo link, recording, brief documentation, and source repository.
