# EY Visual Book Generator (EVI Module)

An AI-assisted visual book generator that creates the first typesetting pass for large, content-heavy marketing documents. Designers remain in the loop to review and refine the generated layouts.

---

## 1. Executive Summary & Problem Framing

Marketing teams create large, content-heavy documents, sometimes up to 500 pages. Traditionally, a designer manually turns that content into a well-designed layout, going through multiple iterations before there is a usable first draft.

This manual handoff is brittle and slow:
* If the designer reflows the page, critical technical language can be accidentally modified, truncated, or divorced from its visual context.
* If the layout is too rigid, diagrams are cropped or relegated to unreadable appendices.

The **EY Visual Book Generator** automates this handoff by establishing explicit **AI Authority Contracts** that govern how much editorial and structural freedom the AI possesses over the source document.

---

## 2. The Three Authority Modes

Rather than treating layout as a visual theme or styling preset, the system implements **three distinct permission contracts**:

| Authority Mode | Source Text | Structure & Flow | Visual Placement | Conflict Policy |
| :--- | :--- | :--- | :--- | :--- |
| **1. Preserve** | Strictly protected source PDF page rendering | Original PDF page remains unchanged; added pages may expand the document | Added visual page before or after the source page | Rejects rewrites, reordering, and source-page reflow |
| **2. Restructure** | Verbatim text and meaning protected | Allowed to reorder sections, add headlines/subheadings, and convert into numbered lists | Context-aware inline placement matching restructured sections | Prohibits summarizing or rewriting source wording |
| **3. Transform** | Full editorial rewrite & executive summarization | Completely redesigned document hierarchy | High-impact hero split, editorial banner, or visual-first composition | Generates meaningfully distinct layout variants |

The canonical system instructions, user templates, and JSON schemas for each mode are documented under [`evi/prompts/`](./prompts/).

---

## 3. Architecture & Tech Stack

```
evi/
├── backend/                  # Kotlin / Maven microservice module (package com.evi.*)
│   ├── src/main/kotlin/com/evi/
│   │   ├── client/           # JDK 21 HTTP Client for the LLM & Supabase APIs
│   │   ├── database/         # Jdbi 3 Postgres DAOs and handlers
│   │   ├── model/            # Folio and Authority Data Models
│   │   ├── resource/         # JAX-RS REST Endpoints (/api/visual-book, /api/folios)
│   │   └── service/          # AuthorityGenerationService, prompt loading & FolioService
│   └── src/test/kotlin/      # Comprehensive unit & validation tests
├── frontend/                 # React 19 + TypeScript + Vite web client
│   ├── src/bookGenerator/    # Multi-page PDF reader, authority selection, and previews
│   │   └── BookGeneratorPage.tsx
│   └── package.json
├── prompts/                  # Canonical LLM authority prompt contracts
│   ├── 1-preserve.md
│   ├── 2-restructure.md
│   └── 3-transform.md
├── db/                       # PostgreSQL schema migrations
│   └── schema.sql
└── README.md
```

### Backend Capabilities
* **Resilient Multi-Model Cascade:** Automatically falls back across provider model candidates and provides deterministic emergency fallbacks when the LLM provider is unavailable.
* **Server-Side PDF Parsing:** Apache PDFBox 3.x extracts page-by-page text dynamically, isolates visual boundaries, and caches extracted text.
* **Storage Isolation:** Private Supabase Storage bucket (`ey-books`) stores raw PDFs securely behind signed backend calls.
* **Authority Contract Evaluation:** `POST /api/visual-book/authority` selects one of the three canonical prompt contracts and validates the mode-specific JSON envelope. Preserve must return exactly two additive page-placement variants.

### Frontend Capabilities
* **Interactive Document Reader:** Real-time page flipping, extracted text view, and multimodal image uploads.
* **Authority Mode Selection:** Lets the user choose Preserve, Restructure, or Transform. Preserve renders two visual placement choices instead of exposing raw LLM JSON as the primary experience.
* **Preserve placement choices:** Preserve returns two additive page-placement variants while keeping the original PDF page rendering unchanged. The selected placement is shown with the original page rendered as an immutable image.
* **PDF composition:** The renderer will consume the selected authority result in the next implementation slice.

---

## 4. Getting Started & Running Standalone

### Prerequisites
* JDK 21+
* Maven 3.9+
* Node.js 20+ and npm

### Running Backend Standalone
```bash
# 1. Set environment variables
export GEMINI_API_KEY="your-gemini-key"
export SUPABASE_DB_URL="jdbc:postgresql://..."
export SUPABASE_URL="https://your-ref.supabase.co"
export SUPABASE_SERVICE_ROLE_KEY="your-service-role-key"

# 2. Compile and run tests
cd evi/backend
mvn clean test
```

### Running Frontend Standalone
```bash
cd evi/frontend
npm install
npm run dev
# Opens at http://localhost:5174
```
