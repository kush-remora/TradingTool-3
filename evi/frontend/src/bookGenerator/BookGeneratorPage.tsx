import { Alert, Button, Collapse, Empty, Input, Spin, Tabs, Tag, Upload, message } from "antd";
import {
  ArrowLeftOutlined,
  CheckCircleOutlined,
  CheckOutlined,
  CloseOutlined,
  DeleteOutlined,
  DownloadOutlined,
  FileImageOutlined,
  FilePdfOutlined,
  FolderOpenOutlined,
  LeftOutlined,
  PlusOutlined,
  RightOutlined,
  ThunderboltOutlined,
} from "@ant-design/icons";
import type { UploadFile, UploadProps } from "antd";
import { useCallback, useEffect, useState } from "react";
import { PDFDownloadLink } from "@react-pdf/renderer";
import { VisualBookPdfDocument, type PdfPageItem } from "./VisualBookPdfDocument";
import { StructuredDocumentRenderer } from "./StructuredDocumentRenderer";
import { getJson, postFormData, postJson } from "../api";
import "./bookGenerator.css";

interface FolioSummary {
  id: string;
  title: string;
  objective: string;
  theme: string;
  sourceFileName: string;
  sourceFileSizeBytes: number;
  status: string;
  createdAt: string;
  updatedAt: string;
}

interface FolioPageSummary {
  folioId: string;
  pageNumber: number;
  totalPages: number;
  extractedText: string;
  status: string;
}

interface VisualBookImageMetadata {
  name: string;
  mimeType?: string;
  byteSize?: number;
}

interface VisualBookLayoutOption {
  id: string;
  template: string;
  imagePlacement: string;
  headline: string;
  summary: string;
  rationale: string;
}

interface VisualBookDebugInfo {
  latencyMs: number;
  apiKeyConfigured: boolean;
  providerStatus: string;
  errorMessage?: string;
  model: string;
  endpoint?: string;
  requestPromptSnippet?: string;
  responseStatusCode?: number;
  responseSnippet?: string;
}

interface VisualBookLayoutResponse {
  options: VisualBookLayoutOption[];
  generationSource: string;
  debug?: VisualBookDebugInfo;
}

interface VisualBookLayoutRequest {
  bookTitle?: string;
  objective?: string;
  pageText: string;
  images?: VisualBookImageMetadata[];
}

interface PageDesignState {
  imageFile?: File;
  imageUrl?: string;
  layoutOptions?: VisualBookLayoutOption[];
  selectedLayoutId?: string;
  generationSource?: string;
  debug?: VisualBookDebugInfo;
}

const { TextArea } = Input;

function formatFileSize(byteSize: number): string {
  if (byteSize < 1024 * 1024) return `${Math.max(1, Math.round(byteSize / 1024))} KB`;
  return `${(byteSize / (1024 * 1024)).toFixed(1)} MB`;
}

function formatCreatedAt(value: string): string {
  return new Intl.DateTimeFormat("en", {
    day: "2-digit",
    month: "short",
    year: "numeric",
  }).format(new Date(value));
}

function formatTemplateName(template: string): string {
  return template
    .split("-")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ");
}

function renderLayoutMockup(option: VisualBookLayoutOption, imageUrl?: string) {
  const isHeroSplit = option.template === "hero-split";
  const isEditorial = option.template === "editorial";
  const isImageLed = option.template === "image-led";
  const isFullBleed = option.imagePlacement === "full-bleed";
  const isLeft = option.imagePlacement === "left";

  if (isImageLed) {
    return (
      <div className="mockup-sheet mockup-template-image-led">
        <div className="mockup-lead-media">
          {imageUrl ? (
            <img src={imageUrl} alt="Page visual" className="mockup-img" />
          ) : (
            <div className="mockup-placeholder-img">
              <FileImageOutlined />
              <span>Full-Bleed Visual Canvas</span>
            </div>
          )}
        </div>
        <div className="mockup-caption-bar">
          <div className="mockup-headline-bar" />
          <div className="mockup-line is-long" />
        </div>
      </div>
    );
  }

  if (isEditorial) {
    return (
      <div className="mockup-sheet mockup-template-editorial">
        <div className="mockup-editorial-banner">
          {imageUrl ? (
            <img src={imageUrl} alt="Page visual" className="mockup-img" />
          ) : (
            <div className="mockup-placeholder-img">
              <FileImageOutlined />
              <span>Technical Workflow Diagram</span>
            </div>
          )}
        </div>
        <div className="mockup-editorial-prose">
          <div className="mockup-headline-bar is-centered" />
          <div className="mockup-subhead-bar is-centered" />
          <div className="mockup-editorial-dual-columns">
            <div className="mockup-prose-column">
              <div className="mockup-line is-long" />
              <div className="mockup-line is-medium" />
              <div className="mockup-line is-long" />
            </div>
            <div className="mockup-prose-column">
              <div className="mockup-line is-long" />
              <div className="mockup-line is-long" />
              <div className="mockup-line is-short" />
            </div>
          </div>
        </div>
      </div>
    );
  }

  // hero-split (default)
  return (
    <div className={`mockup-sheet mockup-template-hero-split ${isLeft ? "is-left" : "is-right"}`}>
      {isLeft && (
        <div className="mockup-split-box">
          {imageUrl ? (
            <img src={imageUrl} alt="Page visual" className="mockup-img" />
          ) : (
            <div className="mockup-placeholder-thumb">
              <FileImageOutlined />
              <span>Hero visual</span>
            </div>
          )}
        </div>
      )}

      <div className="mockup-split-content">
        <div className="mockup-headline-bar" />
        <div className="mockup-subhead-bar" />
        <div className="mockup-line is-long" />
        <div className="mockup-line is-medium" />
        <div className="mockup-line is-long" />
        <div className="mockup-line is-short" />
      </div>

      {!isLeft && (
        <div className="mockup-split-box">
          {imageUrl ? (
            <img src={imageUrl} alt="Page visual" className="mockup-img" />
          ) : (
            <div className="mockup-placeholder-thumb">
              <FileImageOutlined />
              <span>Hero visual</span>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

export function BookGeneratorPage() {
  const [activeTab, setActiveTab] = useState("create");
  const [title, setTitle] = useState("");
  const [objective, setObjective] = useState("");
  const [theme, setTheme] = useState("");
  const [files, setFiles] = useState<UploadFile[]>([]);
  const [folios, setFolios] = useState<FolioSummary[]>([]);
  const [isLoadingFolios, setIsLoadingFolios] = useState(true);
  const [folioError, setFolioError] = useState<string | null>(null);
  const [isCreating, setIsCreating] = useState(false);

  // Reader state
  const [selectedFolio, setSelectedFolio] = useState<FolioSummary | null>(null);
  const [currentPageNumber, setCurrentPageNumber] = useState(1);
  const [activePageData, setActivePageData] = useState<FolioPageSummary | null>(null);
  const [isLoadingPage, setIsLoadingPage] = useState(false);
  const [pageError, setPageError] = useState<string | null>(null);

  // Page layout generation & image state
  const [pageDesigns, setPageDesigns] = useState<Record<number, PageDesignState>>({});
  const [isGeneratingLayouts, setIsGeneratingLayouts] = useState(false);
  const [generationError, setGenerationError] = useState<string | null>(null);

  const currentDesign = pageDesigns[currentPageNumber];

  const updateUrlParams = useCallback((folioId: string | null, page: number | null) => {
    const url = new URL(window.location.href);
    if (folioId) {
      url.searchParams.set("folioId", folioId);
      url.searchParams.set("page", String(page ?? 1));
    } else {
      url.searchParams.delete("folioId");
      url.searchParams.delete("page");
    }
    window.history.replaceState({}, "", url.toString());
  }, []);

  const loadFolioPage = useCallback(
    async (folioId: string, pageNum: number): Promise<void> => {
      setIsLoadingPage(true);
      setPageError(null);
      try {
        const response = await getJson<FolioPageSummary>(
          `/api/folios/${folioId}/pages/${pageNum}`,
          { useCache: false },
        );
        setActivePageData(response);
        setCurrentPageNumber(response.pageNumber);
      } catch (error) {
        setPageError(error instanceof Error ? error.message : "Unable to load page content.");
      } finally {
        setIsLoadingPage(false);
      }
    },
    [],
  );

  const openFolio = useCallback(
    (folio: FolioSummary, pageNum = 1): void => {
      setSelectedFolio(folio);
      setCurrentPageNumber(pageNum);
      updateUrlParams(folio.id, pageNum);
      void loadFolioPage(folio.id, pageNum);
    },
    [loadFolioPage, updateUrlParams],
  );

  const closeReader = useCallback((): void => {
    setSelectedFolio(null);
    setActivePageData(null);
    setPageError(null);
    updateUrlParams(null, null);
    setActiveTab("folios");
  }, [updateUrlParams]);

  const goToPage = (targetPage: number): void => {
    if (!selectedFolio || targetPage < 1) return;
    if (activePageData && targetPage > activePageData.totalPages) return;
    setCurrentPageNumber(targetPage);
    updateUrlParams(selectedFolio.id, targetPage);
    void loadFolioPage(selectedFolio.id, targetPage);
  };

  const handleImageUpload = (file: File): void => {
    if (!file.type.startsWith("image/")) {
      message.error("Please select an image file (PNG, JPG, WebP).");
      return;
    }
    const previousUrl = pageDesigns[currentPageNumber]?.imageUrl;
    if (previousUrl) {
      URL.revokeObjectURL(previousUrl);
    }
    const imageUrl = URL.createObjectURL(file);
    setPageDesigns((prev) => ({
      ...prev,
      [currentPageNumber]: {
        ...prev[currentPageNumber],
        imageFile: file,
        imageUrl,
      },
    }));
    message.success(`Attached ${file.name} to Page ${currentPageNumber}.`);
  };

  const removeImage = (): void => {
    const previousUrl = pageDesigns[currentPageNumber]?.imageUrl;
    if (previousUrl) {
      URL.revokeObjectURL(previousUrl);
    }
    setPageDesigns((prev) => {
      const copy = { ...prev };
      if (copy[currentPageNumber]) {
        copy[currentPageNumber] = {
          ...copy[currentPageNumber],
          imageFile: undefined,
          imageUrl: undefined,
        };
      }
      return copy;
    });
    message.info(`Removed image from Page ${currentPageNumber}.`);
  };

  const generateLayouts = async (): Promise<void> => {
    if (!selectedFolio || !activePageData) return;
    setIsGeneratingLayouts(true);
    setGenerationError(null);
    try {
      const images: VisualBookImageMetadata[] = currentDesign?.imageFile
        ? [
            {
              name: currentDesign.imageFile.name,
              mimeType: currentDesign.imageFile.type,
              byteSize: currentDesign.imageFile.size,
            },
          ]
        : [];

      const request: VisualBookLayoutRequest = {
        bookTitle: selectedFolio.title,
        objective: selectedFolio.objective,
        pageText: activePageData.extractedText || "No text detected on this page.",
        images,
      };

      const response = await postJson<VisualBookLayoutResponse>(
        "/api/visual-book/layout-options",
        request,
      );

      console.log("[VisualBook] Gemini API Layout Generation Response:", {
        model: response.debug?.model,
        endpoint: response.debug?.endpoint,
        latencyMs: response.debug?.latencyMs,
        status: response.debug?.responseStatusCode,
        source: response.generationSource,
        options: response.options,
        debug: response.debug,
      });

      setPageDesigns((prev) => ({
        ...prev,
        [currentPageNumber]: {
          ...prev[currentPageNumber],
          layoutOptions: response.options,
          generationSource: response.generationSource,
          debug: response.debug,
          selectedLayoutId:
            prev[currentPageNumber]?.selectedLayoutId || response.options[0]?.id,
        },
      }));
      message.success("Generated two layout options with identified headline and context.");
    } catch (error) {
      setGenerationError(
        error instanceof Error ? error.message : "Unable to generate layout options.",
      );
    } finally {
      setIsGeneratingLayouts(false);
    }
  };

  const selectLayout = (layoutId: string): void => {
    setPageDesigns((prev) => ({
      ...prev,
      [currentPageNumber]: {
        ...prev[currentPageNumber],
        selectedLayoutId: layoutId,
      },
    }));
    message.success(`Layout selected for Page ${currentPageNumber}`);
  };

  const saveAndAdvancePage = (): void => {
    message.success(`Page ${currentPageNumber} confirmed and saved.`);
    if (activePageData && currentPageNumber < activePageData.totalPages) {
      goToPage(currentPageNumber + 1);
    } else {
      message.success("All pages designed! Ready for final book compilation.");
    }
  };

  const selectedOption = currentDesign?.layoutOptions?.find(
    (opt) => opt.id === currentDesign.selectedLayoutId,
  );

  const loadFolios = useCallback(async (): Promise<void> => {
    setIsLoadingFolios(true);
    setFolioError(null);
    try {
      const response = await getJson<FolioSummary[]>("/api/folios", { useCache: false });
      setFolios(response);
    } catch (error) {
      setFolioError(error instanceof Error ? error.message : "Unable to load folios.");
    } finally {
      setIsLoadingFolios(false);
    }
  }, []);

  useEffect(() => {
    void loadFolios();
  }, [loadFolios]);

  // Initial resume from URL params if present
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const folioId = params.get("folioId");
    const pageParam = parseInt(params.get("page") || "1", 10) || 1;
    if (folioId && folios.length > 0 && !selectedFolio) {
      const matched = folios.find((f) => f.id === folioId);
      if (matched) {
        openFolio(matched, pageParam);
      }
    }
  }, [folios, selectedFolio, openFolio]);

  const uploadProps: UploadProps = {
    accept: "application/pdf,.pdf",
    beforeUpload: () => false,
    fileList: files,
    maxCount: 1,
    showUploadList: false,
    onChange: ({ fileList }) => setFiles(fileList.slice(-1)),
  };

  const createFolio = async (): Promise<void> => {
    const sourcePdf = files[0]?.originFileObj;
    if (!title.trim() || !objective.trim() || !sourcePdf) {
      message.error("Add a title, objective, and PDF before creating a folio.");
      return;
    }

    setIsCreating(true);
    try {
      const formData = new FormData();
      formData.append("title", title.trim());
      formData.append("objective", objective.trim());
      formData.append("theme", theme.trim());
      formData.append("file", sourcePdf, sourcePdf.name);
      await postFormData<FolioSummary>("/api/folios", formData);

      setTitle("");
      setObjective("");
      setTheme("");
      setFiles([]);
      await loadFolios();
      setActiveTab("folios");
      message.success("Folio uploaded and ready for page processing.");
    } catch (error) {
      message.error(error instanceof Error ? error.message : "Unable to create folio.");
    } finally {
      setIsCreating(false);
    }
  };

  const compiledPdfPages: PdfPageItem[] = Object.entries(pageDesigns)
    .filter(([_, design]) => Boolean(design.selectedLayoutId))
    .map(([pageNumStr, design]) => {
      const selected = design.layoutOptions?.find((opt) => opt.id === design.selectedLayoutId);
      const isCurrent = Number(pageNumStr) === currentPageNumber;
      const text = isCurrent ? (activePageData?.extractedText || "") : "";
      return {
        pageNumber: Number(pageNumStr),
        pageText: text,
        headline: selected?.headline || selectedFolio?.title || `Page ${pageNumStr}`,
        summary: selected?.summary || "",
        rationale: selected?.rationale || "",
        template: selected?.template || "hero-split",
        imagePlacement: selected?.imagePlacement || "right",
        imageUrl: design.imageUrl,
        figureCaption: selected?.figureCaption,
      };
    });

  const activePdfPages: PdfPageItem[] =
    compiledPdfPages.length > 0
      ? compiledPdfPages
      : selectedOption
      ? [
          {
            pageNumber: currentPageNumber,
            pageText: activePageData?.extractedText || "",
            headline: selectedOption.headline,
            summary: selectedOption.summary,
            rationale: selectedOption.rationale,
            template: selectedOption.template,
            imagePlacement: selectedOption.imagePlacement,
            imageUrl: currentDesign?.imageUrl,
            figureCaption: selectedOption.figureCaption,
          },
        ]
      : [];

  return (
    <main className="book-generator-page">
      <header className="book-generator-header">
        <div className="book-generator-brand">Folio</div>
        <span className="book-generator-status">Visual book workspace</span>
      </header>

      <section className="book-generator-intro">
        <p className="book-generator-eyebrow">AI-POWERED VISUAL BOOKS</p>
        <h1>Give the story a home.</h1>
        <p>Create a folio once, then step through its pages as the visual book takes shape.</p>
      </section>

      {selectedFolio ? (
        <section className="book-generator-reader" aria-label="Folio reader">
          <div className="book-generator-reader-header">
            <Button
              className="book-generator-back-btn"
              icon={<ArrowLeftOutlined />}
              onClick={closeReader}
            >
              All folios
            </Button>
            <div className="book-generator-reader-meta">
              <span className="book-generator-eyebrow">FOLIO READER</span>
              <h2>{selectedFolio.title}</h2>
              {selectedFolio.objective && (
                <p className="book-generator-reader-objective">{selectedFolio.objective}</p>
              )}
            </div>
            <Tag color={selectedFolio.status === "uploaded" ? "green" : "default"}>
              {selectedFolio.status}
            </Tag>
          </div>

          <div className="book-generator-context-banner">
            <div className="book-generator-context-item">
              <span className="book-generator-context-label">BOOK TITLE</span>
              <span className="book-generator-context-value">{selectedFolio.title}</span>
            </div>
            {selectedFolio.objective && (
              <div className="book-generator-context-item is-wide">
                <span className="book-generator-context-label">OBJECTIVE</span>
                <span className="book-generator-context-value">{selectedFolio.objective}</span>
              </div>
            )}
            {selectedFolio.theme && (
              <div className="book-generator-context-item">
                <span className="book-generator-context-label">VISUAL THEME</span>
                <span className="book-generator-context-value is-tag">{selectedFolio.theme}</span>
              </div>
            )}
          </div>

          <div className="book-generator-reader-body">
            <div className="book-generator-reader-toolbar">
              <div className="book-generator-page-counter">
                <span>Page</span>
                <strong>{currentPageNumber}</strong>
                <span>of</span>
                <strong>{activePageData?.totalPages ?? "…"}</strong>
              </div>

              <div className="book-generator-reader-toolbar-right">
                {activePdfPages.length > 0 && selectedFolio && (
                  <PDFDownloadLink
                    document={
                      <VisualBookPdfDocument
                        bookTitle={selectedFolio.title}
                        bookObjective={selectedFolio.objective}
                        pages={activePdfPages}
                      />
                    }
                    fileName={`${selectedFolio.title.replace(/\s+/g, "-")}-draft.pdf`}
                    className="book-generator-pdf-download-link"
                  >
                    {({ loading }) => (
                      <Button
                        type="default"
                        icon={<DownloadOutlined />}
                        loading={loading}
                        style={{
                          borderColor: "var(--folio-green)",
                          color: "var(--folio-green)",
                          fontWeight: 600,
                        }}
                      >
                        Download PDF Book ({activePdfPages.length} {activePdfPages.length === 1 ? "Page" : "Pages"})
                      </Button>
                    )}
                  </PDFDownloadLink>
                )}

                {selectedOption ? (
                  <Tag color="green" icon={<CheckCircleOutlined />}>
                    Page {currentPageNumber}: {formatTemplateName(selectedOption.template)} Selected
                  </Tag>
                ) : (
                  <Tag color="default">
                    Page {currentPageNumber}: Layout Pending
                  </Tag>
                )}

                <div className="book-generator-reader-nav">
                  <Button
                    icon={<LeftOutlined />}
                    disabled={currentPageNumber <= 1 || isLoadingPage}
                    onClick={() => goToPage(currentPageNumber - 1)}
                  >
                    Previous page
                  </Button>
                  <Button
                    type="primary"
                    icon={<RightOutlined />}
                    disabled={
                      Boolean(activePageData && currentPageNumber >= activePageData.totalPages) ||
                      isLoadingPage
                    }
                    onClick={() => goToPage(currentPageNumber + 1)}
                  >
                    Next page
                  </Button>
                </div>
              </div>
            </div>

            <div className="book-generator-reader-sheet">
              <div className="book-generator-sheet-kicker-row">
                <span className="book-generator-sheet-kicker">EXTRACTED SOURCE TEXT (VERBATIM)</span>
                <span className="book-generator-sheet-tag">Page {currentPageNumber}</span>
              </div>

              {isLoadingPage && (
                <div className="book-generator-reader-state">
                  <Spin size="large" />
                  <p>Extracting page {currentPageNumber} from private source PDF…</p>
                </div>
              )}

              {pageError && !isLoadingPage && (
                <div className="book-generator-reader-state is-error">
                  <p>{pageError}</p>
                  <Button onClick={() => void loadFolioPage(selectedFolio.id, currentPageNumber)}>
                    Try page {currentPageNumber} again
                  </Button>
                </div>
              )}

              {!isLoadingPage && !pageError && activePageData && (
                <div className="book-generator-reader-content">
                  {activePageData.extractedText.trim().length === 0 ? (
                    <div className="book-generator-page-empty">
                      <em>(No text detected on page {currentPageNumber} of this PDF)</em>
                    </div>
                  ) : (
                    <pre className="book-generator-page-text">{activePageData.extractedText}</pre>
                  )}
                </div>
              )}

              {/* Page-level Image Slot */}
              <div className="book-generator-page-image-slot">
                <div className="book-generator-image-slot-header">
                  <div>
                    <span className="book-generator-sheet-kicker">PAGE IMAGE (OPTIONAL)</span>
                    <p className="book-generator-image-hint">
                      Attach an image for Page {currentPageNumber} to merge into the generated layouts.
                    </p>
                  </div>
                  {currentDesign?.imageFile && (
                    <Button
                      danger
                      size="small"
                      icon={<DeleteOutlined />}
                      onClick={removeImage}
                    >
                      Remove image
                    </Button>
                  )}
                </div>

                {currentDesign?.imageFile && currentDesign?.imageUrl ? (
                  <div className="book-generator-image-preview-card">
                    <img
                      src={currentDesign.imageUrl}
                      alt="Page preview"
                      className="book-generator-image-thumb"
                    />
                    <div className="book-generator-image-meta">
                      <strong>{currentDesign.imageFile.name}</strong>
                      <span>
                        {formatFileSize(currentDesign.imageFile.size)} ·{" "}
                        {currentDesign.imageFile.type || "image"}
                      </span>
                    </div>
                  </div>
                ) : (
                  <Upload.Dragger
                    accept="image/png,image/jpeg,image/webp,image/gif"
                    maxCount={1}
                    showUploadList={false}
                    beforeUpload={(file) => {
                      handleImageUpload(file);
                      return false;
                    }}
                    className="book-generator-image-dragger"
                  >
                    <FileImageOutlined className="book-generator-image-dragger-icon" />
                    <p>Drop an image for Page {currentPageNumber}, or browse</p>
                    <small>PNG, JPG, WebP · up to 10 MB</small>
                  </Upload.Dragger>
                )}
              </div>

              {/* Action button to generate 2 layout options */}
              <div className="book-generator-action-bar">
                <Button
                  type="primary"
                  size="large"
                  icon={<ThunderboltOutlined />}
                  loading={isGeneratingLayouts}
                  disabled={!activePageData || isLoadingPage}
                  onClick={generateLayouts}
                  className="book-generator-generate-layouts-btn"
                >
                  {currentDesign?.layoutOptions
                    ? "Re-generate Layout Options"
                    : "Generate 2 Layout Options"}
                </Button>
                <span className="book-generator-action-hint">
                  Gemini identifies headline, page context, and proposes 2 tailored visual layouts.
                </span>
              </div>

              {generationError && (
                <Alert
                  type="error"
                  message="Layout generation error"
                  description={generationError}
                  showIcon
                  closable
                  onClose={() => setGenerationError(null)}
                  className="book-generator-generation-alert"
                />
              )}

              <footer className="book-generator-reader-footer">
                <small>
                  Source text is stored verbatim in database checkpoints. Selections and layouts persist across page navigation.
                </small>
              </footer>
            </div>

            {/* Generated 2 Layout Options Section */}
            {currentDesign?.layoutOptions && (
              <section className="book-generator-options-section" aria-label="Layout choices">
                <div className="book-generator-options-header">
                  <div>
                    <span className="book-generator-eyebrow">AI DESIGN PROPOSALS</span>
                    <h3>Choose your layout for Page {currentPageNumber}</h3>
                    <p>
                      Gemini extracted the headline, summarized context, and generated two visual
                      compositions for this page.
                    </p>
                  </div>
                  {currentDesign.generationSource && (
                    <div className="book-generator-source-badges">
                      <Tag
                        color={
                          currentDesign.generationSource === "gemini" ? "blue" : "orange"
                        }
                      >
                        {currentDesign.generationSource === "gemini"
                          ? "AI Generated (Gemini)"
                          : "Contextual Fallback"}
                      </Tag>
                      {currentDesign.debug && (
                        <Tag color="default">
                          ⚡ {currentDesign.debug.latencyMs} ms · {currentDesign.debug.model}
                        </Tag>
                      )}
                    </div>
                  )}
                </div>

                {currentDesign.debug?.errorMessage && (
                  <Alert
                    type="warning"
                    showIcon
                    message="Provider Note"
                    description={`Using server-side fallback layouts: ${currentDesign.debug.errorMessage}`}
                    className="book-generator-debug-alert"
                  />
                )}

                {currentDesign.debug && (
                  <Collapse
                    size="small"
                    ghost
                    className="book-generator-api-collapse"
                    items={[
                      {
                        key: "api-log",
                        label: (
                          <div className="book-generator-api-collapse-header">
                            <span
                              className="book-generator-api-dot"
                              style={{
                                background:
                                  currentDesign.generationSource === "gemini" ? "#52c41a" : "#faad14",
                              }}
                            />
                            <strong>Gemini API Inspector & Latency Log:</strong>
                            <span>{currentDesign.debug.model}</span>
                            <span className="book-generator-api-latency">
                              ⚡ {currentDesign.debug.latencyMs} ms
                            </span>
                            <span className="book-generator-api-status">
                              {currentDesign.debug.responseStatusCode
                                ? `HTTP ${currentDesign.debug.responseStatusCode}`
                                : currentDesign.debug.providerStatus}
                            </span>
                          </div>
                        ),
                        children: (
                          <div className="book-generator-api-log-body">
                            <div className="book-generator-api-log-row">
                              <span className="book-generator-api-log-label">Endpoint:</span>
                              <code>{currentDesign.debug.endpoint || "Gemini generateContent"}</code>
                            </div>
                            <div className="book-generator-api-log-row">
                              <span className="book-generator-api-log-label">Latency:</span>
                              <span>{currentDesign.debug.latencyMs} milliseconds</span>
                            </div>
                            <div className="book-generator-api-log-row">
                              <span className="book-generator-api-log-label">Source Mode:</span>
                              <span>
                                {currentDesign.generationSource === "gemini"
                                  ? "AI Generated (Active Gemini candidate)"
                                  : `Fallback (${currentDesign.debug.errorMessage || "Unavailable"})`}
                              </span>
                            </div>
                            {currentDesign.debug.requestPromptSnippet && (
                              <div className="book-generator-api-log-row is-block">
                                <span className="book-generator-api-log-label">Request Prompt Sent:</span>
                                <pre className="book-generator-api-pre">
                                  {currentDesign.debug.requestPromptSnippet}
                                </pre>
                              </div>
                            )}
                            {currentDesign.debug.responseSnippet && (
                              <div className="book-generator-api-log-row is-block">
                                <span className="book-generator-api-log-label">Response Body / Payload:</span>
                                <pre className="book-generator-api-pre">
                                  {currentDesign.debug.responseSnippet}
                                </pre>
                              </div>
                            )}
                          </div>
                        ),
                      },
                    ]}
                  />
                )}

                <div className="book-generator-option-grid">
                  {currentDesign.layoutOptions.map((option) => {
                    const isSelected = currentDesign.selectedLayoutId === option.id;
                    return (
                      <article
                        key={option.id}
                        className={`book-generator-option is-${option.template} ${
                          isSelected ? "is-selected" : ""
                        }`}
                        onClick={() => selectLayout(option.id)}
                        role="button"
                        tabIndex={0}
                        onKeyDown={(event) => {
                          if (event.key === "Enter" || event.key === " ") {
                            selectLayout(option.id);
                          }
                        }}
                      >
                        <div className="book-generator-option-topline">
                          <span className="book-generator-option-template-tag">
                            {formatTemplateName(option.template)}
                          </span>
                          {isSelected ? (
                            <Tag color="green" icon={<CheckCircleOutlined />}>
                              Selected
                            </Tag>
                          ) : (
                            <Tag>Option {option.id.replace("option-", "")}</Tag>
                          )}
                        </div>

                        <div className="book-generator-mockup-canvas">
                          {renderLayoutMockup(option, currentDesign.imageUrl)}
                        </div>

                        <div className="book-generator-option-details">
                          <div className="book-generator-option-headline-box">
                            <span className="book-generator-option-kicker">
                              IDENTIFIED HEADLINE
                            </span>
                            <h4>{option.headline}</h4>
                          </div>

                          <div className="book-generator-option-summary-box">
                            <span className="book-generator-option-kicker">PAGE CONTEXT</span>
                            <p>{option.summary}</p>
                          </div>

                          <div className="book-generator-option-rationale-box">
                            <span className="book-generator-option-kicker">
                              EDITORIAL RATIONALE
                            </span>
                            <p className="book-generator-option-rationale">{option.rationale}</p>
                          </div>
                        </div>

                        <footer className="book-generator-option-footer">
                          <span className="book-generator-option-placement">
                            Placement: {option.imagePlacement}
                          </span>
                          <Button
                            type={isSelected ? "primary" : "default"}
                            icon={isSelected ? <CheckOutlined /> : undefined}
                            onClick={(event) => {
                              event.stopPropagation();
                              selectLayout(option.id);
                            }}
                          >
                            {isSelected ? "Layout Selected" : "Select this layout"}
                          </Button>
                        </footer>
                      </article>
                    );
                  })}
                </div>
              </section>
            )}

            {/* Final Rendered Page Preview & Save/Advance Action */}
            {selectedOption && (
              <section className="book-generator-final-page-preview" aria-label="Final Composed Page">
                <div className="book-generator-final-page-header">
                  <div>
                    <span className="book-generator-eyebrow">FINAL COMPOSED PAGE</span>
                    <h3>Page {currentPageNumber} Live Book Sheet</h3>
                    <p>
                      Composed using <strong>{formatTemplateName(selectedOption.template)}</strong> layout with{" "}
                      <code>{selectedOption.imagePlacement}</code> placement.
                    </p>
                  </div>
                  <Button
                    type="primary"
                    size="large"
                    icon={<RightOutlined />}
                    onClick={saveAndAdvancePage}
                    className="book-generator-advance-btn"
                  >
                    {activePageData && currentPageNumber >= activePageData.totalPages
                      ? "Save Page & Finish Book Review"
                      : `Save Page ${currentPageNumber} & Proceed to Page ${currentPageNumber + 1} →`}
                  </Button>
                </div>

                <div
                  className={`book-page-canvas is-template-${selectedOption.template} is-placement-${selectedOption.imagePlacement}`}
                >
                  <header className="book-page-canvas-header">
                    <span className="book-page-canvas-running-title">{selectedFolio.title}</span>
                    <span className="book-page-canvas-page-num">Page {currentPageNumber}</span>
                  </header>

                  {/* Template: editorial with banner image */}
                  {selectedOption.template === "editorial" && currentDesign?.imageUrl && (
                    <div className="book-page-editorial-hero">
                      <img src={currentDesign.imageUrl} alt="Page visual" />
                      {selectedOption.figureCaption && (
                        <p className="book-page-figure-caption">{selectedOption.figureCaption}</p>
                      )}
                    </div>
                  )}

                  <div
                    className={`book-page-canvas-body ${
                      selectedOption.template === "hero-split" ? "has-split-layout" : ""
                    }`}
                  >
                    <div className="book-page-content-column">
                      <h2 className="book-page-headline">{selectedOption.headline}</h2>
                      {selectedOption.summary && (
                        <p className="book-page-deck">{selectedOption.summary}</p>
                      )}
                      <div className="book-page-prose">
                        {activePageData?.extractedText ? (
                          <StructuredDocumentRenderer rawText={activePageData.extractedText} />
                        ) : (
                          <p>
                            <em>(No text detected on this page)</em>
                          </p>
                        )}
                      </div>
                    </div>

                    {/* Template: hero-split with image */}
                    {selectedOption.template === "hero-split" && (
                      <div className="book-page-split-media-column">
                        {currentDesign?.imageUrl ? (
                          <>
                            <img
                              src={currentDesign.imageUrl}
                              alt="Page visual"
                              className="book-page-split-img"
                            />
                            {selectedOption.figureCaption && (
                              <p className="book-page-figure-caption">{selectedOption.figureCaption}</p>
                            )}
                          </>
                        ) : (
                          <div className="book-page-split-placeholder">
                            <FileImageOutlined />
                            <span>Supporting Visual</span>
                          </div>
                        )}
                      </div>
                    )}
                  </div>

                  <footer className="book-page-canvas-footer">
                    <span>{selectedFolio.theme || "Folio Visual Book"}</span>
                    <span>{currentPageNumber}</span>
                  </footer>
                </div>
              </section>
            )}
          </div>
        </section>
      ) : (
        <section className="book-generator-tabs" aria-label="Folio workspace">
          <Tabs
            activeKey={activeTab}
            onChange={setActiveTab}
            items={[
              {
                key: "create",
                label: "New folio",
                children: (
                  <div className="book-generator-workspace">
                    <div className="book-generator-form">
                      <div className="book-generator-section-heading">
                        <span>01</span>
                        <div>
                          <h2>Set the context</h2>
                          <p>These details guide every page we generate later.</p>
                        </div>
                      </div>

                      <label>
                        <span>Book title</span>
                        <Input
                          value={title}
                          onChange={(event) => setTitle(event.target.value)}
                          placeholder="The Quiet Future"
                          maxLength={120}
                        />
                      </label>
                      <label>
                        <span>Objective</span>
                        <TextArea
                          value={objective}
                          onChange={(event) => setObjective(event.target.value)}
                          placeholder="A short introduction to regenerative cities"
                          autoSize={{ minRows: 4, maxRows: 6 }}
                          maxLength={600}
                          showCount
                        />
                      </label>
                      <label>
                        <span>
                          Visual theme <small>Optional</small>
                        </span>
                        <Input
                          value={theme}
                          onChange={(event) => setTheme(event.target.value)}
                          placeholder="Editorial, warm, minimal"
                          maxLength={120}
                        />
                      </label>

                      <div className="book-generator-section-heading book-generator-page-heading">
                        <span>02</span>
                        <div>
                          <h2>Upload the source PDF</h2>
                          <p>Your private source is saved once and processed page by page later.</p>
                        </div>
                      </div>

                      <div className="book-generator-upload">
                        <span>Source PDF</span>
                        <Upload.Dragger {...uploadProps} aria-label="Upload source PDF">
                          <FilePdfOutlined />
                          <p>Drop one PDF here, or browse</p>
                          <small>PDF only · maximum 50 MB · stored privately</small>
                        </Upload.Dragger>
                        {files[0] && (
                          <div className="book-generator-selected-file">
                            <FilePdfOutlined aria-hidden="true" />
                            <span>{files[0].name}</span>
                            <button
                              type="button"
                              aria-label="Remove selected PDF"
                              onClick={() => setFiles([])}
                            >
                              <CloseOutlined aria-hidden="true" />
                            </button>
                          </div>
                        )}
                      </div>

                      <Button
                        type="primary"
                        size="large"
                        className="book-generator-generate"
                        icon={<PlusOutlined />}
                        loading={isCreating}
                        onClick={createFolio}
                      >
                        Create folio
                      </Button>
                    </div>

                    <aside className="book-generator-preview" aria-label="Folio creation guide">
                      <div className="book-generator-preview-topline">
                        <span>WHAT HAPPENS NEXT</span>
                        <span>01 / 03</span>
                      </div>
                      <div className="book-generator-sheet book-generator-guide-sheet">
                        <span className="book-generator-guide-icon">
                          <FolderOpenOutlined />
                        </span>
                        <p className="book-generator-sheet-kicker">YOUR FOLIO</p>
                        <h2>{title || "Your book title"}</h2>
                        <p>
                          {objective ||
                            "The source PDF and its context will be ready when you start creating visual pages."}
                        </p>
                        <ol>
                          <li>Upload once</li>
                          <li>Step through each page</li>
                          <li>Resume anytime</li>
                        </ol>
                      </div>
                    </aside>
                  </div>
                ),
              },
              {
                key: "folios",
                label: `Your folios${folios.length ? ` (${folios.length})` : ""}`,
                children: (
                  <section className="book-generator-folio-list" aria-live="polite">
                    <div className="book-generator-list-heading">
                      <div>
                        <p className="book-generator-eyebrow">SAVED WORK</p>
                        <h2>Pick up where you left off.</h2>
                      </div>
                      <Button onClick={() => void loadFolios()} loading={isLoadingFolios}>
                        Refresh
                      </Button>
                    </div>

                    {isLoadingFolios && (
                      <div className="book-generator-list-state">
                        <Spin /> Loading folios…
                      </div>
                    )}
                    {folioError && (
                      <div className="book-generator-list-state is-error">
                        <p>{folioError}</p>
                        <Button onClick={() => void loadFolios()}>Try again</Button>
                      </div>
                    )}
                    {!isLoadingFolios && !folioError && folios.length === 0 && (
                      <Empty description="No folios yet. Create your first one." />
                    )}
                    {!isLoadingFolios && !folioError && folios.length > 0 && (
                      <div className="book-generator-folio-grid">
                        {folios.map((folio) => (
                          <article
                            className="book-generator-folio-card is-interactive"
                            key={folio.id}
                            onClick={() => openFolio(folio)}
                            role="button"
                            tabIndex={0}
                            onKeyDown={(event) => {
                              if (event.key === "Enter" || event.key === " ") {
                                openFolio(folio);
                              }
                            }}
                          >
                            <div className="book-generator-folio-card-topline">
                              <FilePdfOutlined />
                              <Tag color={folio.status === "uploaded" ? "green" : "default"}>
                                {folio.status}
                              </Tag>
                            </div>
                            <h3>{folio.title}</h3>
                            <p>{folio.objective}</p>
                            {folio.theme && (
                              <span className="book-generator-folio-theme">{folio.theme}</span>
                            )}
                            <footer>
                              <span>
                                {folio.sourceFileName} · {formatFileSize(folio.sourceFileSizeBytes)}
                              </span>
                              <span>{formatCreatedAt(folio.updatedAt)}</span>
                            </footer>
                            <div className="book-generator-folio-card-action">
                              <span>Read pages →</span>
                            </div>
                          </article>
                        ))}
                      </div>
                    )}
                  </section>
                ),
              },
            ]}
          />
        </section>
      )}
    </main>
  );
}
