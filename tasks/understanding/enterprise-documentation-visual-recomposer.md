# EY Visual Book Generator — Correct Product Understanding

## 1. The actual problem

Before this project, a content/documentation team produces a structured but visually rough document. A designer then reads it, understands the intended communication, adds or positions visual assets, reorganizes the presentation when needed, and produces the polished PDF. The expensive part is the handoff: the designer must interpret the source and repeatedly rebuild its presentation.

The new product is an AI-assisted handoff and document transformation workflow. It must not assume that the desired result is always a rewritten or redesigned document. The user must explicitly choose how much authority the AI receives.

## 2. The three user-selected authority modes

### Preserve

The source document is protected. Gemini must not change the text, language, order, structure, or formatting. A visual may be associated or inserted only if that does not violate the preservation rule. If adding it necessarily changes the page, the product must make that limitation explicit instead of silently changing the document.

### Restructure

The original language and meaning are protected. Gemini may create new headlines and subheadings, reorder sections, create numbered lists, and improve hierarchy and formatting. It may not rewrite, summarize, or translate the content.

### Transform

Gemini has full editorial and design authority. It may summarize long passages, rewrite them in its own language, create new structure and headlines, integrate the visual asset, and produce two layouts that differ in document structure and communication approach—not only in image placement.

## 3. Intended end-to-end workflow

`Upload source document + visual asset(s) → select mode → choose mode-specific Gemini prompt → return structured result → validate against mode → preview → export PDF.`

The three prompts should share source context and output conventions, but explicitly state the allowed and forbidden changes for their mode. Preserve requires application-level verification against the original; prompting alone cannot prove that no text or formatting changed.

## 4. Current implementation, understood in the new context

The current code is an implementation slice, not the final product definition:

- React uploads a source PDF, extracts/reads a page, accepts an optional page image, and calls `POST /api/visual-book/layout-options`.
- The Kotlin service sends page text and image metadata to Gemini, currently asks for exactly two layout options, validates the JSON response, and falls back to deterministic options when Gemini is unavailable.
- React renders the selected option and exports a PDF with `@react-pdf/renderer`.

This current flow is biased toward layout generation because it has no authority-mode selection and the prompt assumes that Gemini should extract a headline, write a summary, and propose visual layouts. The next design work should adapt this existing path to the three modes instead of treating the current prompt as the product contract.

## 5. Decisions and next focus

The current PDF extraction, image handling, Gemini integration, layout preview, and PDF rendering are useful implementation foundations. They should not be discarded solely because the product framing changed.

The next implementation should first add the mode selection and mode-specific contracts. It should not begin by adding more layout templates or polishing existing visual styles. Preserve validation, Restructure language preservation, and Transform structural variation are the core correctness questions.
