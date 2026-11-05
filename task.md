# EY Visual Book Generator — Task Tracker

## Product direction correction

- [x] Define the product around explicit AI authority modes: Preserve, Restructure, and Transform.
- [x] Store the three authority-mode prompt contracts in `config/gemini-prompts/`.
- [ ] Replace the current layout-only Gemini contract with mode-specific prompts, outputs, and validation.

## Current task

- [x] Gemini configuration and first-page layout slice.
- [x] Folio schema, create/list UI, and private-PDF upload API.
- [x] Align selected-PDF UI and create readable private object names.
- [x] Page reader & text extraction slice: PDFBox 3.0.8 extraction, `folio_pages` persistence, `GET /api/folios/{id}/pages/{pageNumber}`, and Back/Next reader UI with URL resume.
- [x] Page image upload slot, Gemini headline/context extraction, and two-layout options generator with interactive selection.
- [x] Gemini API observability: transparent request, response, and latency inspector surfaced in the UI and logs.
- [x] Multi-model resiliency: automatic cascade across active Gemini candidates (`gemini-3-flash-preview`, `gemini-3.1-flash-lite`, `gemini-3.8-flash`) to eliminate HTTP 503 high-demand errors.
- [x] Option 2: `@react-pdf/renderer` integration for visual book PDF composition and direct download.
- [x] Restructure and decouple all assignment code into a self-contained top-level `evi/` module (`evi/backend`, `evi/frontend`, `evi/prompts`, `evi/db`, and standalone `evi/README.md`).
- [ ] Review and test the user experience of image upload, Gemini layout generation, and selection on active folios.

## Next

- Persist page image files and selected layout ID into `folio_pages`.
- Deterministic final PDF compilation and signed download link.

## Submission

- Deploy the demo, record a walkthrough, and send the demo link, recording, brief documentation, and source repository.
