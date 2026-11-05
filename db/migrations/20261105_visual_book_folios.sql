CREATE TABLE IF NOT EXISTS public.folios (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title                 TEXT NOT NULL DEFAULT 'Untitled folio',
    objective             TEXT NOT NULL DEFAULT '',
    theme                 TEXT NOT NULL DEFAULT '',
    source_pdf_bucket     TEXT NOT NULL DEFAULT 'ey-books' CHECK (source_pdf_bucket = 'ey-books'),
    source_pdf_path       TEXT,
    source_file_name      TEXT,
    source_file_size_bytes BIGINT CHECK (source_file_size_bytes IS NULL OR source_file_size_bytes >= 0),
    page_count            INTEGER CHECK (page_count IS NULL OR page_count > 0),
    status                TEXT NOT NULL DEFAULT 'draft' CHECK (
        status IN ('draft', 'uploaded', 'ready', 'processing', 'completed', 'failed')
    ),
    error_detail          TEXT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.folio_pages (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    folio_id              UUID NOT NULL REFERENCES public.folios(id) ON DELETE CASCADE,
    page_number           INTEGER NOT NULL CHECK (page_number > 0),
    extracted_text        TEXT,
    image_manifest        JSONB NOT NULL DEFAULT '[]'::jsonb,
    layout_options        JSONB NOT NULL DEFAULT '[]'::jsonb,
    selected_layout_id    TEXT,
    status                TEXT NOT NULL DEFAULT 'pending' CHECK (
        status IN ('pending', 'extracting', 'ready', 'generating', 'awaiting_selection', 'selected', 'failed')
    ),
    error_detail          TEXT,
    processed_at          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (folio_id, page_number)
);

CREATE INDEX IF NOT EXISTS idx_folios_status_updated_at
    ON public.folios (status, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_folio_pages_folio_status_page_number
    ON public.folio_pages (folio_id, status, page_number);
