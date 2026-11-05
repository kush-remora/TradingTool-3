# EY Visual Book Generator MVP - Understanding

The EY exercise is separate from the trading product. It asks for a browser-based MVP that accepts optional book context (title and objective) and page-by-page text plus images; generates two context-aware layout options for each page; lets the user select one; and produces a complete book draft. The specified deliverables are a working web prototype (client-side preferred), basic input/selection UI, and concise documentation of the approach, assumptions, and trade-offs. The accompanying email also asks for a recording. The PDF does not explicitly require a code submission.

With a maximum ten-hour window, preserve this repository and its deployment rather than replace the trading product. Build the exercise as a self-contained, static React experience under a distinct route or a dedicated deployment branch/repository. Use a small Render API only if an LLM key is available; keep a deterministic context-aware layout fallback so the demo always works. The prototype should demonstrate one polished multi-page book flow, not attempt advanced editing, persistence, bulk processing, or true 500-page rendering. Validation will cover the full input -> two choices -> selection -> completed-book path, production build, deployed link, docs, and a short recording. Open question: the email's stated deadline, "Thursday (05th Oct 2026)", conflicts with the current date and weekday, so submit as soon as possible and avoid relying on that date.

## First implementation plan: Gemini layout endpoint

1. Load the existing `GEMINI_API_KEY` from ignored local environment configuration; keep the secret outside tracked files.
2. Add `POST /api/visual-book/layout-options`, accepting bounded book context and one page. It returns exactly two typed layout specifications, not generated HTML.
3. Use Gemini JSON structured output and validate its response on the server. Return deterministic context-aware layouts marked as a fallback if Gemini fails, so the UI always remains demoable.
4. Add a small per-client rate limit and focused tests for validation, Gemini parsing, and fallback behaviour.
5. Run module tests, package the service, and smoke-test the endpoint locally without printing the key.

