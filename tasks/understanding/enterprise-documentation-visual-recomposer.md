# EY Visual Book Generator — Correct Product Understanding

## 1. The actual problem

Before this project, a content/documentation team produces a structured but visually rough document. A designer then reads it, understands the intended communication, adds or positions visual assets, reorganizes the presentation when needed, and produces the polished PDF. The expensive part is the handoff: the designer must interpret the source and repeatedly rebuild its presentation.

The new product is an AI-assisted handoff and document transformation workflow. It must not assume that the desired result is always a rewritten or redesigned document. The user must explicitly choose how much authority the AI receives.

## 2. The three user-selected authority modes

### Preserve

The source PDF page rendering is protected as an immutable artifact. Gemini must not change, summarize, rewrite, reorder, or reconstruct any source page. Preserve adds the visual as a separate page before or after the original source page; the document may therefore expand from one page to two or more without reflowing the source page.

### Restructure

The original language and meaning are protected. Gemini may create new headlines and subheadings, reorder sections, create numbered lists, and improve hierarchy and formatting. It may not rewrite, summarize, or translate the content.

### Transform

Gemini has full editorial and design authority. It may summarize long passages, rewrite them in its own language, create new structure and headlines, integrate the visual asset, and produce two layouts that differ in document structure and communication approach—not only in image placement.

## 3. Intended end-to-end workflow

`Upload source document + visual asset(s) → select mode → choose mode-specific Gemini prompt → return structured result → validate against mode → preview → export PDF.`

The three prompts should share source context and output conventions, but explicitly state the allowed and forbidden changes for their mode. Preserve requires application-level verification against the original; prompting alone cannot prove that no text or formatting changed.

## 4. Current implementation, understood in the new context

The current code is an implementation slice, not the final product definition:

- React uploads a source PDF, extracts/reads a page, accepts an optional page image, and calls `POST /api/visual-book/authority`.
- The Kotlin service sends page text and image metadata to the mode-specific Gemini contract and validates the structured response. Preserve now requires exactly two additive page-placement variants while protecting the source PDF page rendering.
- React renders the two Preserve placement choices (image page before or after source); the preview uses a PDFBox-rendered source-page image so the original alignment is not reconstructed from extracted text.
- `GET /api/folios/{id}/pages/{pageNumber}/image` renders the stored source page with PDFBox for the Preserve preview; the selected visual is composed as a separate page in the browser preview.

The old flow was biased toward layout generation because it assumed Gemini should extract a headline, write a summary, and propose visual layouts. That layout-only API and renderer have now been removed; the authority-mode flow is the product contract.

## 5. Decisions and next focus

The current PDF extraction, image handling, Gemini integration, and page preview are useful implementation foundations. The old layout-only API and renderer are not part of the corrected product and have been removed.

The next implementation should add deterministic Preserve source-fidelity validation and final PDF composition. It should not reintroduce generic layout templates. Restructure and Transform now have structured frontend previews; the remaining correctness work is application-level lexical/factual validation and final PDF composition for those richer results.

## 6. EVI module implementation slice

The assignment code has now been isolated under `evi/` and is authority-mode aware end to end: the frontend selects a mode, the request carries it, the backend loads the matching contract from `evi/prompts/`, and the Gemini response is returned as a mode-tagged structured payload. Preserve now has two visual placement choices.

The slice must not pretend that the current page-text-plus-image-metadata input provides the full structured source document or multimodal image analysis required by the prompt contracts. Those inputs remain follow-up work.

The authority-aware backend path is now the only generation path. Preserve and Transform results can be selected through mode-specific previews; the model's self-attestation is not sufficient evidence of immutability or factual grounding, so deterministic validation and final renderer support remain required.

For local testing, the root `frontend/` Vite app on port 5173 is the single browser entry point. Its book-generator route contains the authority-mode previews; the separate EVI Vite server is not part of the testing workflow.

The first screen is positioned as an EY internal EVI document studio, not a consumer-facing story or home. User-facing language should describe source documents, communication objectives, AI authority, and visual-page production. Internal API/domain names such as `folio` may remain in implementation code, but should not define the product's visible identity.

For the temporary public demo period, the GitHub Pages build is configured with `VITE_PUBLIC_BOOK_GENERATOR_ONLY=true`. Every public path renders the Book Generator and is rewritten to its route; the trading UI remains available only in local development. This is presentation-level hiding, not authentication or backend security.
