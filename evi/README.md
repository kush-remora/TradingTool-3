# EY Visual Book Generator (EVI Module)

An AI-assisted document transformation platform designed to eliminate the manual bottleneck in the handoff between technical content teams and visual designers.

---

## 1. Executive Summary & Problem Framing

In enterprise technical documentation workflows, technical writers create detailed, structured documents (specifications, architecture guides, process manuals), and visual artists create diagrams and flowcharts. Traditionally, a graphic designer manually interprets where and how diagrams fit onto pages, reformatting text and reflowing layouts in Adobe InDesign or Figma to produce a polished deliverable.

This manual handoff is brittle and slow:
* If the designer reflows the page, critical technical language can be accidentally modified, truncated, or divorced from its visual context.
* If the layout is too rigid, diagrams are cropped or relegated to unreadable appendices.

The **EY Visual Book Generator** automates this handoff by establishing explicit **AI Authority Contracts** that govern how much editorial and structural freedom the AI possesses over the source document.

---

## 2. The Three Authority Modes

Rather than treating layout as a visual theme or styling preset, the system implements **three distinct permission contracts**:

| Authority Mode | Source Text | Structure & Flow | Visual Placement | Conflict Policy |
| :--- | :--- | :--- | :--- | :--- |
| **1. Preserve** | Strictly protected (0% lexical mutation) | Unchanged original block sequence | Allowed only in designated empty slots or trailing whitespace without moving text | Blocks modification if insertion causes text reflow; reports explicit conflict |
| **2. Restructure** | Verbatim text and meaning protected | Allowed to reorder sections, add headlines/subheadings, and convert into numbered lists | Context-aware inline placement matching restructured sections | Prohibits summarizing or rewriting source wording |
| **3. Transform** | Full editorial rewrite & executive summarization | Completely redesigned document hierarchy | High-impact hero split, editorial banner, or visual-first composition | Generates meaningfully distinct layout variants |

The canonical system instructions, user templates, and JSON schemas for each mode are documented under [`evi/prompts/`](./prompts/).

---

## 3. Architecture & Tech Stack

```
evi/
├── backend/                  # Kotlin / Maven microservice module (package com.evi.*)
│   ├── src/main/kotlin/com/evi/
│   │   ├── client/           # JDK 21 HTTP Client for Gemini & Supabase APIs
│   │   ├── database/         # Jdbi 3 Postgres DAOs and handlers
│   │   ├── model/            # Folio, Layout, and Authority Data Models
│   │   ├── resource/         # JAX-RS REST Endpoints (/api/visual-book, /api/folios)
│   │   └── service/          # VisualBookLayoutService & FolioService (PDFBox 3.x)
│   └── src/test/kotlin/      # Comprehensive unit & validation tests
├── frontend/                 # React 19 + TypeScript + Vite web client
│   ├── src/bookGenerator/    # Multi-page PDF reader, layout picker, & PDF export
│   │   ├── BookGeneratorPage.tsx
│   │   ├── StructuredDocumentRenderer.tsx
│   │   └── VisualBookPdfDocument.tsx (@react-pdf/renderer)
│   └── package.json
├── prompts/                  # Canonical Gemini authority prompt contracts
│   ├── 1-preserve.md
│   ├── 2-restructure.md
│   └── 3-transform.md
├── db/                       # PostgreSQL schema migrations
│   └── schema.sql
└── README.md
```

### Backend Capabilities
* **Resilient Multi-Model Cascade:** Automatically falls back across Gemini candidates (`gemini-3-flash-preview`, `gemini-3.1-flash-lite`, `gemini-3.8-flash`) and provides deterministic emergency fallbacks when provider demand spikes.
* **Server-Side PDF Parsing:** Apache PDFBox 3.x extracts page-by-page text dynamically, isolates visual boundaries, and caches extracted text.
* **Storage Isolation:** Private Supabase Storage bucket (`ey-books`) stores raw PDFs securely behind signed backend calls.

### Frontend Capabilities
* **Interactive Document Reader:** Real-time page flipping, extracted text view, and multimodal image uploads.
* **Side-by-Side Candidate Selection:** Renders candidate layout proposals (Hero-Split vs. Editorial Inline) with visual reasoning rationale.
* **Client-Side PDF Generation:** Produces production-ready, downloadable PDFs directly via `@react-pdf/renderer`.

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
