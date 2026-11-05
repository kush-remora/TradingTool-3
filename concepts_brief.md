# Page-by-Page Folio Generation

* **Author:** kushb2
* **Date:** 2026-11-05

## Overview

Folio accepts a text-based PDF and turns it into a new visual book without changing its content. The system extracts each source page's text, lets the user add zero or one image to that page, produces two layout options, saves the user's selection, and then moves to the next page. When every page is selected, Folio renders one downloadable PDF.

The MVP is deliberately a re-composer, not a content writer. It must not summarize, rewrite, translate, correct, reorder, or invent page content. OCR, scanned-PDF classification, source-image extraction, editing controls, image analysis, queues, and parallel processing are out of scope. If page text cannot be read, the user sees one clear failure message rather than a special recovery flow.

## Analysis

The current schema already has a useful page checkpoint: extracted text, image manifest, layout options, selected layout ID, and status. The missing work is a PDF extractor, a page workflow service, a deterministic renderer, and endpoints/UI for page processing and final download.

The risky part is treating LLM output as directly renderable. The LLM should select only from a small set of renderer-owned templates and style tokens. The server validates the exact two returned options before saving them. Source text is stored separately and rendered verbatim; the model never supplies replacement body text.

The workflow must survive refreshes. For each page the sequence is: show extracted text, optionally upload one image, save two validated options, select one option, then advance. The user can return to a previous page and replace its selection. A refresh resumes at the first unselected page and must not call the LLM again for pages that already have saved choices. Final generation is blocked until every page has a valid selection.

## Goals vs. Non-Goals

* **In-Scope Goals:** a PDF upload; page-by-page text extraction; zero or one user-uploaded image per page; two validated layout choices; forward/backward navigation; selection and resume; deterministic final PDF; private Storage for source, page images, and export.
* **Out-of-Scope Non-Goals:** OCR; PDF-type classification; source-image extraction; text/layout editing; image understanding; background jobs; queues; parallel generation; authentication redesign.

## Alternatives Weighed

Use the source PDF's embedded images automatically: rejected for this MVP because image extraction, association, and fidelity handling enlarge scope. The user supplies one optional image for the relevant page instead.

Generate new text or illustrations: rejected because the product purpose is to preserve the supplied content and redesign its presentation.

Store only a selected layout ID: rejected because final rendering needs a repeatable, versioned layout specification. Store both validated options and the selected ID.

Extract all pages in a background pipeline: rejected for this MVP. Process one page at a time and persist each checkpoint.

## Key Decisions & Staging for Next Steps

1. Add one server-side PDF library for text extraction and deterministic PDF output. Reject unsupported PDFs before page processing.
2. Define input limits before LLM calls: maximum pages, maximum source size, maximum text per page, and one image's permitted MIME type and size.
3. Define the typed layout schema: template, image placement, palette/font tokens, and fixed renderer primitives. The LLM may choose only these values.
4. Implement a deterministic hard-coded page renderer before LLM integration, then add layout recommendation and selection.
5. Add a final review/download state only when every page is selected. Store the export path and return a signed download URL.

Open decisions: the MVP page limit, exact PDF page size, image formats and size limit, whether a prior selection can be replaced, and whether a final non-editable review screen is required before export.

## First Implementation Slice

Clicking a saved Folio will open a reader for the current source page. The backend will load the private source PDF, extract only the requested page's text, save that text in `folio_pages`, and return the page count plus current page state. The UI will show raw page text and Back/Next navigation; refresh will use the saved text instead of extracting that page again.

This slice deliberately excludes page-image upload, LLM recommendations, layout selection, and final PDF export. It provides the durable page checkpoint those later steps require.
