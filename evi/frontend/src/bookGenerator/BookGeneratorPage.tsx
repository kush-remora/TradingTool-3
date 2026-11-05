import { Alert, Button, Empty, Input, Radio, Spin, Tabs, Tag, Upload, message } from "antd";
import {
  ArrowLeftOutlined,
  CloseOutlined,
  DeleteOutlined,
  FileImageOutlined,
  FilePdfOutlined,
  FolderOpenOutlined,
  PlusOutlined,
  ThunderboltOutlined,
} from "@ant-design/icons";
import type { UploadFile, UploadProps } from "antd";
import { useCallback, useEffect, useState } from "react";
import {
  Document as PdfDocument,
  Image as PdfImage,
  Page as PdfPage,
  PDFViewer,
  StyleSheet,
  Text as PdfText,
  View as PdfView,
} from "@react-pdf/renderer";
import { apiBaseUrl, getJson, postFormData, postJson } from "../api";
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

type AuthorityMode = "PRESERVE" | "RESTRUCTURE" | "TRANSFORM";

type PreservePlacementStrategy =
  | "ADDED_PAGE_BEFORE_SOURCE"
  | "ADDED_PAGE_AFTER_SOURCE";

interface PreserveVariant {
  variant_id: string;
  variant_label: string;
  placement_strategy: PreservePlacementStrategy;
  anchor_block_id: string;
  asset_ids: string[];
  rationale: string;
}

type RestructureLayoutPattern =
  | "SINGLE_COLUMN_FLOW"
  | "TWO_COLUMN_ASYMMETRIC"
  | "SIDE_BY_SIDE_TEXT_AND_VISUAL"
  | "HIGHLIGHT_CALLOUT_CONTAINER"
  | "STEP_PROCESS_GRID";

type RestructureNodeType =
  | "PARAGRAPH"
  | "SOURCE_HEADING"
  | "BULLETED_LIST"
  | "NUMBERED_LIST"
  | "CALLOUT_BLOCK";

interface RestructureContentNode {
  node_type: RestructureNodeType;
  origin_source_block_ids: string[];
  verbatim_segments: string[];
}

interface RestructureIntegratedVisual {
  asset_id: string;
  placement_anchor: string;
  sizing_directive: string;
  factual_label: string | null;
}

interface RestructureSection {
  section_id: string;
  layout_pattern: RestructureLayoutPattern;
  synthesized_heading: string | null;
  synthesized_subheading: string | null;
  content_nodes: RestructureContentNode[];
  integrated_visuals: RestructureIntegratedVisual[];
}

interface RestructureResult {
  authority_violation_warning: string | null;
  source_coverage_manifest: {
    total_source_blocks_input: number;
    all_source_block_ids_included: string[];
    reordered_block_ids: string[];
  };
  restructured_sections: RestructureSection[];
}

type TransformComponentType =
  | "LEAD_PARAGRAPH"
  | "BODY_PARAGRAPH"
  | "BULLETED_LIST"
  | "NUMBERED_SEQUENCE"
  | "KEY_METRIC_STAT_BOX"
  | "CALLOUT_INSIGHT";

interface TransformBlock {
  block_id: string;
  component_type: TransformComponentType;
  content_text_or_items: string[];
  transformation_type: string;
  grounding_source_block_ids: string[];
}

interface TransformVisualPlacement {
  asset_id: string;
  visual_narrative_role: string;
  placement_slot: string;
  editorial_caption: string;
  grounding_source_block_ids: string[];
}

interface TransformSection {
  section_id: string;
  section_role: string;
  section_heading: string;
  layout_container: string;
  transformed_blocks: TransformBlock[];
  visual_placements: TransformVisualPlacement[];
}

interface TransformVariant {
  variant_id: string;
  variant_name: string;
  communication_archetype: string;
  page_grid_system: string;
  document_title: string;
  document_subtitle: string | null;
  sections: TransformSection[];
  summarization_audit: {
    covered_source_block_ids: string[];
    condensed_or_omitted_details_notes: string[];
  };
}

interface TransformResult {
  variant_divergence_summary: {
    structural_difference_explanation: string;
    narrative_difference_explanation: string;
    visual_integration_difference: string;
  };
  transformation_variants: TransformVariant[];
}

interface AuthorityGenerationRequest {
  authorityMode: AuthorityMode;
  sourceDocumentContent: string;
  sourceStructureJson?: string;
  visualAssets?: VisualBookImageMetadata[];
  userInstructions?: string;
}

interface AuthorityGenerationResponse {
  authorityMode: AuthorityMode;
  generationSource: string;
  result?: unknown;
  debug?: VisualBookDebugInfo;
}

interface PageDesignState {
  imageFile?: File;
  imageUrl?: string;
  sourcePageImageUrl?: string;
  authorityResult?: AuthorityGenerationResponse;
  selectedAuthorityVariantId?: string;
}

const AUTHORITY_MODE_DETAILS: Record<AuthorityMode, { label: string; description: string }> = {
  PRESERVE: {
    label: "Preserve",
    description: "Protect the source wording, structure, and formatting.",
  },
  RESTRUCTURE: {
    label: "Restructure",
    description: "Reorganize the document without changing its language.",
  },
  TRANSFORM: {
    label: "Transform",
    description: "Give the LLM full editorial and design authority.",
  },
};

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

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function getPreserveVariants(result: unknown): PreserveVariant[] {
  if (!isRecord(result) || !Array.isArray(result.preserve_variants)) return [];
  const placementStrategies: PreservePlacementStrategy[] = [
    "ADDED_PAGE_BEFORE_SOURCE",
    "ADDED_PAGE_AFTER_SOURCE",
  ];
  return result.preserve_variants.filter((variant): variant is PreserveVariant => {
    if (!isRecord(variant)) return false;
    const placementStrategy = variant.placement_strategy;
    return (
      typeof variant.variant_id === "string" &&
      typeof variant.variant_label === "string" &&
      typeof placementStrategy === "string" &&
      placementStrategies.includes(placementStrategy as PreservePlacementStrategy) &&
      typeof variant.anchor_block_id === "string" &&
      Array.isArray(variant.asset_ids) &&
      typeof variant.rationale === "string"
    );
  });
}

function getRestructureResult(result: unknown): RestructureResult | null {
  if (!isRecord(result)) return null;

  const rawCoverage = result.source_coverage_manifest;
  if (!isRecord(rawCoverage)) return null;
  const sourceBlockIds = rawCoverage.all_source_block_ids_included;
  const reorderedBlockIds = rawCoverage.reordered_block_ids;
  if (!Array.isArray(sourceBlockIds) || !Array.isArray(reorderedBlockIds)) return null;

  const layoutPatterns: RestructureLayoutPattern[] = [
    "SINGLE_COLUMN_FLOW",
    "TWO_COLUMN_ASYMMETRIC",
    "SIDE_BY_SIDE_TEXT_AND_VISUAL",
    "HIGHLIGHT_CALLOUT_CONTAINER",
    "STEP_PROCESS_GRID",
  ];
  const nodeTypes: RestructureNodeType[] = [
    "PARAGRAPH",
    "SOURCE_HEADING",
    "BULLETED_LIST",
    "NUMBERED_LIST",
    "CALLOUT_BLOCK",
  ];
  const sections = Array.isArray(result.restructured_sections)
    ? result.restructured_sections.flatMap((rawSection, sectionIndex): RestructureSection[] => {
        if (!isRecord(rawSection)) return [];
        const layoutPattern = rawSection.layout_pattern;
        if (typeof layoutPattern !== "string" || !layoutPatterns.includes(layoutPattern as RestructureLayoutPattern)) {
          return [];
        }

        const contentNodes = Array.isArray(rawSection.content_nodes)
          ? rawSection.content_nodes.flatMap((rawNode): RestructureContentNode[] => {
              if (!isRecord(rawNode)) return [];
              const nodeType = rawNode.node_type;
              const originIds = rawNode.origin_source_block_ids;
              const verbatimSegments = rawNode.verbatim_segments;
              if (
                typeof nodeType !== "string" ||
                !nodeTypes.includes(nodeType as RestructureNodeType) ||
                !Array.isArray(originIds) ||
                !Array.isArray(verbatimSegments) ||
                !originIds.every((value) => typeof value === "string") ||
                !verbatimSegments.every((value) => typeof value === "string")
              ) {
                return [];
              }
              return [
                {
                  node_type: nodeType as RestructureNodeType,
                  origin_source_block_ids: originIds as string[],
                  verbatim_segments: verbatimSegments as string[],
                },
              ];
            })
          : [];

        const integratedVisuals = Array.isArray(rawSection.integrated_visuals)
          ? rawSection.integrated_visuals.flatMap((rawVisual): RestructureIntegratedVisual[] => {
              if (!isRecord(rawVisual) || typeof rawVisual.asset_id !== "string") return [];
              return [
                {
                  asset_id: rawVisual.asset_id,
                  placement_anchor:
                    typeof rawVisual.placement_anchor === "string"
                      ? rawVisual.placement_anchor
                      : "INLINE_AFTER_FIRST_NODE",
                  sizing_directive:
                    typeof rawVisual.sizing_directive === "string"
                      ? rawVisual.sizing_directive
                      : "CONTAIN_NATURAL",
                  factual_label:
                    typeof rawVisual.factual_label === "string" ? rawVisual.factual_label : null,
                },
              ];
            })
          : [];

        return [
          {
            section_id:
              typeof rawSection.section_id === "string"
                ? rawSection.section_id
                : `section-${sectionIndex + 1}`,
            layout_pattern: layoutPattern as RestructureLayoutPattern,
            synthesized_heading:
              typeof rawSection.synthesized_heading === "string"
                ? rawSection.synthesized_heading
                : null,
            synthesized_subheading:
              typeof rawSection.synthesized_subheading === "string"
                ? rawSection.synthesized_subheading
                : null,
            content_nodes: contentNodes,
            integrated_visuals: integratedVisuals,
          },
        ];
      })
    : [];

  if (sections.length === 0) return null;

  return {
    authority_violation_warning:
      typeof result.authority_violation_warning === "string"
        ? result.authority_violation_warning
        : null,
    source_coverage_manifest: {
      total_source_blocks_input:
        typeof rawCoverage.total_source_blocks_input === "number"
          ? rawCoverage.total_source_blocks_input
          : sourceBlockIds.length,
      all_source_block_ids_included: sourceBlockIds.filter(
        (value): value is string => typeof value === "string",
      ),
      reordered_block_ids: reorderedBlockIds.filter(
        (value): value is string => typeof value === "string",
      ),
    },
    restructured_sections: sections,
  };
}

function formatRestructureLabel(value: string): string {
  return value
    .toLowerCase()
    .replace(/_/g, " ")
    .replace(/(^|\s)\S/g, (character) => character.toUpperCase());
}

function getStringArray(value: unknown): string[] {
  return Array.isArray(value)
    ? value.filter((item): item is string => typeof item === "string")
    : [];
}

function getTransformResult(result: unknown): TransformResult | null {
  if (!isRecord(result) || !isRecord(result.variant_divergence_summary)) return null;
  const rawSummary = result.variant_divergence_summary;
  if (
    typeof rawSummary.structural_difference_explanation !== "string" ||
    typeof rawSummary.narrative_difference_explanation !== "string" ||
    typeof rawSummary.visual_integration_difference !== "string" ||
    !Array.isArray(result.transformation_variants)
  ) {
    return null;
  }

  const variants = result.transformation_variants.flatMap((rawVariant): TransformVariant[] => {
    if (!isRecord(rawVariant) || !Array.isArray(rawVariant.sections)) return [];
    const sections = rawVariant.sections.flatMap((rawSection): TransformSection[] => {
      if (!isRecord(rawSection) || typeof rawSection.section_id !== "string") return [];
      const transformedBlocks = Array.isArray(rawSection.transformed_blocks)
        ? rawSection.transformed_blocks.flatMap((rawBlock): TransformBlock[] => {
            if (
              !isRecord(rawBlock) ||
              typeof rawBlock.block_id !== "string" ||
              typeof rawBlock.component_type !== "string"
            ) {
              return [];
            }
            return [
              {
                block_id: rawBlock.block_id,
                component_type: rawBlock.component_type as TransformComponentType,
                content_text_or_items: getStringArray(rawBlock.content_text_or_items),
                transformation_type:
                  typeof rawBlock.transformation_type === "string"
                    ? rawBlock.transformation_type
                    : "REWRITTEN_FOR_CLARITY",
                grounding_source_block_ids: getStringArray(rawBlock.grounding_source_block_ids),
              },
            ];
          })
        : [];
      const visualPlacements = Array.isArray(rawSection.visual_placements)
        ? rawSection.visual_placements.flatMap((rawVisual): TransformVisualPlacement[] => {
            if (!isRecord(rawVisual) || typeof rawVisual.asset_id !== "string") return [];
            return [
              {
                asset_id: rawVisual.asset_id,
                visual_narrative_role:
                  typeof rawVisual.visual_narrative_role === "string"
                    ? rawVisual.visual_narrative_role
                    : "SIDEBAR_REFERENCE",
                placement_slot:
                  typeof rawVisual.placement_slot === "string"
                    ? rawVisual.placement_slot
                    : "FULL_WIDTH_BAND",
                editorial_caption:
                  typeof rawVisual.editorial_caption === "string"
                    ? rawVisual.editorial_caption
                    : "",
                grounding_source_block_ids: getStringArray(rawVisual.grounding_source_block_ids),
              },
            ];
          })
        : [];
      return [
        {
          section_id: rawSection.section_id,
          section_role:
            typeof rawSection.section_role === "string" ? rawSection.section_role : "CORE_CONCEPT",
          section_heading:
            typeof rawSection.section_heading === "string" ? rawSection.section_heading : "",
          layout_container:
            typeof rawSection.layout_container === "string"
              ? rawSection.layout_container
              : "FULL_WIDTH_BAND",
          transformed_blocks: transformedBlocks,
          visual_placements: visualPlacements,
        },
      ];
    });

    if (
      typeof rawVariant.variant_id !== "string" ||
      typeof rawVariant.variant_name !== "string" ||
      typeof rawVariant.communication_archetype !== "string" ||
      typeof rawVariant.page_grid_system !== "string" ||
      typeof rawVariant.document_title !== "string"
    ) {
      return [];
    }
    const rawAudit = isRecord(rawVariant.summarization_audit)
      ? rawVariant.summarization_audit
      : {};
    return [
      {
        variant_id: rawVariant.variant_id,
        variant_name: rawVariant.variant_name,
        communication_archetype: rawVariant.communication_archetype,
        page_grid_system: rawVariant.page_grid_system,
        document_title: rawVariant.document_title,
        document_subtitle:
          typeof rawVariant.document_subtitle === "string" ? rawVariant.document_subtitle : null,
        sections,
        summarization_audit: {
          covered_source_block_ids: getStringArray(rawAudit.covered_source_block_ids),
          condensed_or_omitted_details_notes: getStringArray(
            rawAudit.condensed_or_omitted_details_notes,
          ),
        },
      },
    ];
  });

  return variants.length === 2
    ? {
        variant_divergence_summary: {
          structural_difference_explanation: rawSummary.structural_difference_explanation,
          narrative_difference_explanation: rawSummary.narrative_difference_explanation,
          visual_integration_difference: rawSummary.visual_integration_difference,
        },
        transformation_variants: variants,
      }
    : null;
}

function formatPreservePlacement(strategy: PreservePlacementStrategy): string {
  switch (strategy) {
    case "ADDED_PAGE_BEFORE_SOURCE":
      return "Image page before the original PDF page";
    case "ADDED_PAGE_AFTER_SOURCE":
      return "Image page after the original PDF page";
  }
}

const preservePdfStyles = StyleSheet.create({
  page: {
    padding: 48,
    backgroundColor: "#fffefa",
  },
  imageBlock: {
    alignItems: "center",
    justifyContent: "center",
  },
  image: {
    width: 460,
    maxHeight: 420,
    objectFit: "contain",
  },
  sourcePage: {
    padding: 0,
    backgroundColor: "#fffefa",
  },
  sourcePageImage: {
    width: "100%",
    height: "100%",
    objectFit: "contain",
  },
  placeholder: {
    padding: 24,
    backgroundColor: "#edf1e8",
    color: "#315b46",
    fontSize: 12,
  },
});

function PreserveRenderedPreview({
  variant,
  imageUrl,
  sourcePageImageUrl,
}: {
  variant: PreserveVariant;
  imageUrl?: string;
  sourcePageImageUrl?: string;
}) {
  const isImageBefore = variant.placement_strategy === "ADDED_PAGE_BEFORE_SOURCE";
  const image = imageUrl ? (
    <PdfImage src={imageUrl} style={preservePdfStyles.image} />
  ) : (
    <PdfText style={preservePdfStyles.placeholder}>Selected visual preview</PdfText>
  );

  return (
    <section className="preserve-rendered-preview" aria-label="Rendered Preserve layout preview">
      <div className="preserve-rendered-preview-header">
        <div>
          <span className="book-generator-option-kicker">RENDERED PREVIEW</span>
          <h4>{variant.variant_label}</h4>
        </div>
        <span className="preserve-rendered-preview-note">Original PDF page preserved</span>
      </div>
      <div className="preserve-pdf-viewer-shell">
        <PDFViewer className="preserve-pdf-viewer" showToolbar={false}>
          <PdfDocument title={`Preserve preview — ${variant.variant_label}`}>
            {isImageBefore && (
              <PdfPage size="A4" style={preservePdfStyles.page}>
                <PdfView style={preservePdfStyles.imageBlock}>{image}</PdfView>
              </PdfPage>
            )}
            <PdfPage size="A4" style={preservePdfStyles.sourcePage}>
              {sourcePageImageUrl ? (
                <PdfImage src={sourcePageImageUrl} style={preservePdfStyles.sourcePageImage} />
              ) : (
                <PdfText style={preservePdfStyles.placeholder}>Original PDF page preview is loading.</PdfText>
              )}
            </PdfPage>
            {!isImageBefore && (
              <PdfPage size="A4" style={preservePdfStyles.page}>
                <PdfView style={preservePdfStyles.imageBlock}>{image}</PdfView>
              </PdfPage>
            )}
          </PdfDocument>
        </PDFViewer>
      </div>
      <p className="preserve-rendered-preview-caption">
        The original PDF page is rendered unchanged. The selected visual is added as a separate page, preserving the source page&apos;s fonts, spacing, headlines, lines, and alignment.
      </p>
    </section>
  );
}

function PreserveVariantPreview({
  variant,
  sourcePageImageUrl,
  imageUrl,
  selected,
  onSelect,
}: {
  variant: PreserveVariant;
  sourcePageImageUrl?: string;
  imageUrl?: string;
  selected: boolean;
  onSelect: () => void;
}) {
  const imagePage = (
    <div className="preserve-preview-page preserve-preview-image-page">
      {imageUrl ? <img src={imageUrl} alt="Selected visual" /> : <FileImageOutlined />}
      <span>Added visual page</span>
    </div>
  );
  const sourcePage = (
    <div className="preserve-preview-page preserve-preview-source-page">
      {sourcePageImageUrl ? (
        <img src={sourcePageImageUrl} alt="Original PDF page" />
      ) : (
        <span>Original PDF page is loading</span>
      )}
    </div>
  );
  const isImageBefore = variant.placement_strategy === "ADDED_PAGE_BEFORE_SOURCE";

  return (
    <article className={`authority-variant-card ${selected ? "is-selected" : ""}`}>
      <div className="authority-variant-card-header">
        <div>
          <span className="book-generator-option-kicker">PRESERVE OPTION</span>
          <h4>{variant.variant_label}</h4>
        </div>
        <Tag color={selected ? "green" : undefined}>{selected ? "Selected" : "Option"}</Tag>
      </div>
      <div className="preserve-preview-stack">
        {isImageBefore ? imagePage : sourcePage}
        {isImageBefore ? sourcePage : imagePage}
      </div>
      <p className="authority-variant-placement">
        <strong>{formatPreservePlacement(variant.placement_strategy)}</strong>
        <br />
        The original PDF page remains unchanged; only a separate visual page is added.
      </p>
      <Button type={selected ? "primary" : "default"} onClick={onSelect}>
        {selected ? "Preview selected layout" : "Preview this layout"}
      </Button>
    </article>
  );
}

function RestructureResultPreview({
  result,
  imageUrl,
}: {
  result: unknown;
  imageUrl?: string;
}) {
  const restructure = getRestructureResult(result);
  if (!restructure) {
    return (
      <Alert
        type="warning"
        showIcon
        message="A readable Restructure preview was not returned"
        description="Generate again so the LLM returns sections with source coverage, verbatim content nodes, and layout instructions."
      />
    );
  }

  const coverage = restructure.source_coverage_manifest;
  return (
    <div className="restructure-result-preview">
      <div className="restructure-coverage-bar">
        <div>
          <span className="book-generator-option-kicker">SOURCE COVERAGE</span>
          <strong>
            {coverage.all_source_block_ids_included.length} of {coverage.total_source_blocks_input} source blocks included
          </strong>
        </div>
        <Tag color={coverage.reordered_block_ids.length > 0 ? "blue" : undefined}>
          {coverage.reordered_block_ids.length > 0
            ? `${coverage.reordered_block_ids.length} block(s) reordered`
            : "Original block order"}
        </Tag>
      </div>

      {restructure.authority_violation_warning && (
        <Alert
          type="warning"
          showIcon
          message="Some requested edits were outside Restructure authority"
          description={restructure.authority_violation_warning}
          className="restructure-authority-warning"
        />
      )}

      <div className="restructure-section-list">
        {restructure.restructured_sections.map((section, sectionIndex) => (
          <article
            className={`restructure-section-card restructure-layout-${section.layout_pattern.toLowerCase()}`}
            key={section.section_id}
          >
            <div className="restructure-section-header">
              <div>
                <span className="book-generator-option-kicker">SECTION {String(sectionIndex + 1).padStart(2, "0")}</span>
                {section.synthesized_heading && <h4>{section.synthesized_heading}</h4>}
                {section.synthesized_subheading && <p>{section.synthesized_subheading}</p>}
              </div>
              <Tag>{formatRestructureLabel(section.layout_pattern)}</Tag>
            </div>

            <div className="restructure-section-body">
              <div className="restructure-content-nodes">
                {section.content_nodes.map((node, nodeIndex) => {
                  const isList = node.node_type === "BULLETED_LIST" || node.node_type === "NUMBERED_LIST";
                  const sourceText = node.verbatim_segments.join("");
                  if (isList) {
                    const List = node.node_type === "NUMBERED_LIST" ? "ol" : "ul";
                    return (
                      <List className="restructure-verbatim-list" key={`${section.section_id}-${nodeIndex}`}>
                        {node.verbatim_segments.map((segment, segmentIndex) => (
                          <li key={`${section.section_id}-${nodeIndex}-${segmentIndex}`}>{segment}</li>
                        ))}
                      </List>
                    );
                  }
                  if (node.node_type === "SOURCE_HEADING") {
                    return (
                      <h5 className="restructure-source-heading" key={`${section.section_id}-${nodeIndex}`}>
                        {sourceText}
                      </h5>
                    );
                  }
                  return (
                    <p
                      className={node.node_type === "CALLOUT_BLOCK" ? "restructure-verbatim-callout" : "restructure-verbatim-paragraph"}
                      key={`${section.section_id}-${nodeIndex}`}
                    >
                      {sourceText}
                    </p>
                  );
                })}
              </div>

              {section.integrated_visuals.length > 0 && (
                <div className="restructure-visual-list">
                  {section.integrated_visuals.map((visual) => (
                    <figure className="restructure-visual-card" key={`${section.section_id}-${visual.asset_id}`}>
                      <div className="restructure-visual-frame">
                        {imageUrl ? (
                          <img src={imageUrl} alt={visual.factual_label ?? "Selected visual asset"} />
                        ) : (
                          <FileImageOutlined />
                        )}
                      </div>
                      <figcaption>
                        <strong>{visual.factual_label ?? "Selected visual asset"}</strong>
                        <span>
                          {formatRestructureLabel(visual.placement_anchor)} · {formatRestructureLabel(visual.sizing_directive)}
                        </span>
                      </figcaption>
                    </figure>
                  ))}
                </div>
              )}
            </div>
          </article>
        ))}
      </div>

      <p className="restructure-preview-note">
        New headings and layout groupings are AI-created. Every rendered source node above remains verbatim; Restructure does not rewrite the source language.
      </p>
    </div>
  );
}

function TransformResultPreview({
  result,
  imageUrl,
  selectedVariantId,
  onSelect,
}: {
  result: unknown;
  imageUrl?: string;
  selectedVariantId?: string;
  onSelect: (variantId: string) => void;
}) {
  const transform = getTransformResult(result);
  if (!transform) {
    return (
      <Alert
        type="warning"
        showIcon
        message="Two Transform layouts were not returned"
        description="Generate again so the LLM returns two factually grounded variants with different structures and communication approaches."
      />
    );
  }

  return (
    <div className="transform-result-preview">
      <div className="transform-divergence-summary">
        <span className="book-generator-option-kicker">WHY THESE ARE DIFFERENT</span>
        <div className="transform-divergence-grid">
          <div>
            <strong>Structure</strong>
            <p>{transform.variant_divergence_summary.structural_difference_explanation}</p>
          </div>
          <div>
            <strong>Narrative</strong>
            <p>{transform.variant_divergence_summary.narrative_difference_explanation}</p>
          </div>
          <div>
            <strong>Visual role</strong>
            <p>{transform.variant_divergence_summary.visual_integration_difference}</p>
          </div>
        </div>
      </div>

      <div className="transform-variant-grid">
        {transform.transformation_variants.map((variant) => {
          const selected = selectedVariantId === variant.variant_id;
          return (
            <article
              className={`transform-variant-card ${selected ? "is-selected" : ""}`}
              key={variant.variant_id}
            >
              <div className="transform-variant-card-header">
                <div>
                  <span className="book-generator-option-kicker">TRANSFORM OPTION</span>
                  <h4>{variant.variant_name}</h4>
                  <p>{variant.document_title}</p>
                </div>
                <Tag color={selected ? "green" : undefined}>
                  {selected ? "Selected" : formatRestructureLabel(variant.communication_archetype)}
                </Tag>
              </div>

              {variant.document_subtitle && (
                <p className="transform-variant-subtitle">{variant.document_subtitle}</p>
              )}

              <div className="transform-variant-meta">
                <span>{formatRestructureLabel(variant.page_grid_system)}</span>
                <span>{variant.sections.length} sections</span>
              </div>

              <div className="transform-section-list">
                {variant.sections.map((section) => (
                  <section className="transform-section-card" key={section.section_id}>
                    <div className="transform-section-header">
                      <div>
                        <span>{formatRestructureLabel(section.section_role)}</span>
                        <h5>{section.section_heading}</h5>
                      </div>
                      <Tag>{formatRestructureLabel(section.layout_container)}</Tag>
                    </div>
                    <div className="transform-section-content">
                      {section.transformed_blocks.map((block) => {
                        const isList =
                          block.component_type === "BULLETED_LIST" ||
                          block.component_type === "NUMBERED_SEQUENCE";
                        const BlockList = block.component_type === "NUMBERED_SEQUENCE" ? "ol" : "ul";
                        return (
                          <div
                            className={`transform-content-block transform-component-${block.component_type.toLowerCase()}`}
                            key={block.block_id}
                          >
                            {isList ? (
                              <BlockList>
                                {block.content_text_or_items.map((item, itemIndex) => (
                                  <li key={`${block.block_id}-${itemIndex}`}>{item}</li>
                                ))}
                              </BlockList>
                            ) : (
                              block.content_text_or_items.map((item, itemIndex) => (
                                <p key={`${block.block_id}-${itemIndex}`}>{item}</p>
                              ))
                            )}
                            {block.grounding_source_block_ids.length > 0 && (
                              <small>
                                Grounded in {block.grounding_source_block_ids.join(", ")}
                              </small>
                            )}
                          </div>
                        );
                      })}

                      {section.visual_placements.map((visual) => (
                        <figure className="transform-visual-card" key={`${section.section_id}-${visual.asset_id}`}>
                          <div className="transform-visual-frame">
                            {imageUrl ? <img src={imageUrl} alt={visual.editorial_caption || "Selected visual asset"} /> : <FileImageOutlined />}
                          </div>
                          <figcaption>
                            <strong>{visual.editorial_caption || "Selected visual asset"}</strong>
                            <span>
                              {formatRestructureLabel(visual.visual_narrative_role)} · {formatRestructureLabel(visual.placement_slot)}
                            </span>
                          </figcaption>
                        </figure>
                      ))}
                    </div>
                  </section>
                ))}
              </div>

              <div className="transform-audit">
                <strong>Source coverage</strong>
                <span>{variant.summarization_audit.covered_source_block_ids.join(", ") || "No source IDs listed"}</span>
                {variant.summarization_audit.condensed_or_omitted_details_notes.length > 0 && (
                  <ul>
                    {variant.summarization_audit.condensed_or_omitted_details_notes.map((note, noteIndex) => (
                      <li key={`${variant.variant_id}-note-${noteIndex}`}>{note}</li>
                    ))}
                  </ul>
                )}
              </div>

              <Button type={selected ? "primary" : "default"} onClick={() => onSelect(variant.variant_id)}>
                {selected ? "Preview selected layout" : "Preview this layout"}
              </Button>
            </article>
          );
        })}
      </div>
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

  // Authority generation & image state
  const [pageDesigns, setPageDesigns] = useState<Record<number, PageDesignState>>({});
  const [authorityMode, setAuthorityMode] = useState<AuthorityMode>("PRESERVE");
  const [isGeneratingAuthority, setIsGeneratingAuthority] = useState(false);
  const [authorityGenerationError, setAuthorityGenerationError] = useState<string | null>(null);

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
        setPageDesigns((prev) => ({
          ...prev,
          [response.pageNumber]: {
            ...prev[response.pageNumber],
            sourcePageImageUrl: `${apiBaseUrl}/api/folios/${folioId}/pages/${response.pageNumber}/image`,
          },
        }));
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

  const generateAuthorityResult = async (): Promise<void> => {
    if (!activePageData) return;
    if (authorityMode === "PRESERVE" && !currentDesign?.imageFile) {
      message.warning("Select an image before generating Preserve layouts.");
      return;
    }
    setIsGeneratingAuthority(true);
    setAuthorityGenerationError(null);
    try {
      const image = currentDesign?.imageFile;
      const sourceText = activePageData.extractedText || "No text detected on this page.";
      const request: AuthorityGenerationRequest = {
        authorityMode,
        sourceDocumentContent: sourceText,
        sourceStructureJson: JSON.stringify({
          blocks: [
            {
              source_block_id: `page-${currentPageNumber}-text`,
              block_type: "PAGE_TEXT",
              verbatim_text: sourceText,
              original_style_token: "SOURCE_PAGE_TEXT",
            },
          ],
        }),
        visualAssets: image
          ? [
              {
                name: image.name,
                mimeType: image.type,
                byteSize: image.size,
              },
            ]
          : [],
        userInstructions: "",
      };

      const response = await postJson<AuthorityGenerationResponse>(
        "/api/visual-book/authority",
        request,
      );

      setPageDesigns((prev) => ({
        ...prev,
        [currentPageNumber]: {
          ...prev[currentPageNumber],
          authorityResult: response,
          selectedAuthorityVariantId: (() => {
            if (authorityMode === "PRESERVE") {
              return getPreserveVariants(response.result)[0]?.variant_id;
            }
            if (authorityMode === "TRANSFORM") {
              return getTransformResult(response.result)?.transformation_variants[0]?.variant_id;
            }
            return undefined;
          })(),
        },
      }));
      message.success(
        authorityMode === "PRESERVE"
          ? "Generated two Preserve layouts with the original PDF page protected. Choose one below."
          : authorityMode === "TRANSFORM"
            ? "Generated two distinct Transform layouts. Choose one below."
          : `${AUTHORITY_MODE_DETAILS[authorityMode].label} contract evaluated.`,
      );
    } catch (error) {
      setAuthorityGenerationError(
        error instanceof Error ? error.message : "Unable to evaluate the authority contract.",
      );
    } finally {
      setIsGeneratingAuthority(false);
    }
  };

  const selectAuthorityVariant = (variantId: string): void => {
    setPageDesigns((prev) => ({
      ...prev,
      [currentPageNumber]: {
        ...prev[currentPageNumber],
        selectedAuthorityVariantId: variantId,
      },
    }));
    message.success(`${AUTHORITY_MODE_DETAILS[authorityMode].label} layout selected for Page ${currentPageNumber}`);
  };

  const saveAndAdvancePage = (): void => {
    if (
      currentDesign?.authorityResult?.authorityMode === "PRESERVE" &&
      !currentDesign.selectedAuthorityVariantId
    ) {
      message.warning("Select one of the two Preserve layouts before continuing.");
      return;
    }
    if (
      currentDesign?.authorityResult?.authorityMode === "RESTRUCTURE" &&
      !currentRestructureResult
    ) {
      message.warning("Generate a valid Restructure preview before continuing.");
      return;
    }
    message.success(`Page ${currentPageNumber} confirmed and saved.`);
    if (activePageData && currentPageNumber < activePageData.totalPages) {
      goToPage(currentPageNumber + 1);
    } else {
      message.success("All pages designed! Ready for final book compilation.");
    }
  };

  const handleAuthorityModeChange = (nextMode: AuthorityMode): void => {
    setAuthorityMode(nextMode);
    setAuthorityGenerationError(null);
    setPageDesigns((prev) => ({
      ...prev,
      [currentPageNumber]: {
        ...prev[currentPageNumber],
        authorityResult: undefined,
        selectedAuthorityVariantId: undefined,
      },
    }));
  };

  const currentPreserveVariants =
    currentDesign?.authorityResult?.authorityMode === "PRESERVE"
      ? getPreserveVariants(currentDesign.authorityResult.result)
      : [];
  const currentRestructureResult =
    currentDesign?.authorityResult?.authorityMode === "RESTRUCTURE"
      ? getRestructureResult(currentDesign.authorityResult.result)
      : null;
  const hasReadyAuthorityResult = Boolean(
    currentDesign?.selectedAuthorityVariantId || currentRestructureResult,
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
      message.error("Add a title, communication objective, and source PDF before creating a visual book.");
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
      message.success("Visual book created and ready for page processing.");
    } catch (error) {
      message.error(error instanceof Error ? error.message : "Unable to create visual book.");
    } finally {
      setIsCreating(false);
    }
  };

  return (
    <main className="book-generator-page">
      <header className="book-generator-header">
        <div className="book-generator-brand">
          <span className="book-generator-brand-ey">EY</span>
          <span>EVI</span>
        </div>
        <span className="book-generator-status">Internal document visualization</span>
      </header>

      <section className="book-generator-intro">
        <p className="book-generator-eyebrow">EY INTERNAL TOOL · EVI DOCUMENT STUDIO</p>
        <h1>Turn documents into visual books.</h1>
        <p>
          Convert structured enterprise content into polished visual communication with explicit control over how much AI may change.
        </p>
      </section>

      {selectedFolio ? (
        <section className="book-generator-reader" aria-label="Visual book workspace">
          <div className="book-generator-reader-header">
            <Button
              className="book-generator-back-btn"
              icon={<ArrowLeftOutlined />}
              onClick={closeReader}
            >
              All visual books
            </Button>
            <div className="book-generator-reader-meta">
              <span className="book-generator-eyebrow">VISUAL BOOK WORKSPACE</span>
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
                {currentDesign?.selectedAuthorityVariantId ? (
                  <Tag color="green">Page {currentPageNumber}: Layout selected</Tag>
                ) : (
                  <Tag color="default">Page {currentPageNumber}: Layout pending</Tag>
                )}

                <div className="book-generator-reader-nav">
                  <Button
                    disabled={currentPageNumber <= 1 || isLoadingPage}
                    onClick={() => goToPage(currentPageNumber - 1)}
                  >
                    Previous page
                  </Button>
                  <Button
                    type="primary"
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
                      Attach an image for Page {currentPageNumber} and choose how it is added without changing the source.
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

              <section className="book-generator-authority-panel" aria-label="AI authority mode">
                <div className="book-generator-image-slot-header">
                  <div>
                    <span className="book-generator-sheet-kicker">AI AUTHORITY</span>
                    <h3>How much may the LLM change?</h3>
                    <p className="book-generator-image-hint">
                      This selection controls the document contract used for generation.
                    </p>
                  </div>
                </div>
                <Radio.Group
                  value={authorityMode}
                  onChange={(event) => handleAuthorityModeChange(event.target.value as AuthorityMode)}
                  optionType="button"
                  buttonStyle="solid"
                  className="book-generator-authority-options"
                >
                  {(Object.keys(AUTHORITY_MODE_DETAILS) as AuthorityMode[]).map((mode) => (
                    <Radio.Button key={mode} value={mode}>
                      {AUTHORITY_MODE_DETAILS[mode].label}
                    </Radio.Button>
                  ))}
                </Radio.Group>
                <p className="book-generator-authority-description">
                  {AUTHORITY_MODE_DETAILS[authorityMode].description}
                </p>
                <Button
                  type="primary"
                  size="large"
                  icon={<ThunderboltOutlined />}
                  loading={isGeneratingAuthority}
                  disabled={!activePageData || isLoadingPage || (authorityMode === "PRESERVE" && !currentDesign?.imageFile)}
                  onClick={() => void generateAuthorityResult()}
                  className="book-generator-authority-button"
                >
                  {isGeneratingAuthority
                    ? "Evaluating authority contract"
                    : `Generate ${AUTHORITY_MODE_DETAILS[authorityMode].label} result`}
                </Button>
              </section>

              {authorityGenerationError && (
                <Alert
                  type="error"
                  message="Authority generation error"
                  description={authorityGenerationError}
                  showIcon
                  closable
                  onClose={() => setAuthorityGenerationError(null)}
                  className="book-generator-generation-alert"
                />
              )}

              {currentDesign?.authorityResult && (
                <section className="book-generator-authority-result" aria-label="Authority result">
                  <div className="book-generator-options-header">
                    <div>
                      <span className="book-generator-eyebrow">VISUAL LAYOUT OPTIONS</span>
                      <h3>
                        {currentDesign.authorityResult.authorityMode === "PRESERVE"
                          ? "Choose where to add the image"
                          : currentDesign.authorityResult.authorityMode === "RESTRUCTURE"
                            ? "Review the restructured document"
                            : "Choose a transformed layout"}
                      </h3>
                      <p>
                        {currentDesign.authorityResult.authorityMode === "PRESERVE"
                          ? "Both options keep the original PDF page unchanged. The selected visual is added as a separate page before or after it."
                          : currentDesign.authorityResult.authorityMode === "RESTRUCTURE"
                            ? "The LLM may change hierarchy and layout, but every source wording block remains verbatim."
                            : "Choose between two genuinely different editorial and structural treatments, not just two image positions."}
                      </p>
                    </div>
                    <Tag color={currentDesign.authorityResult.generationSource === "llm" ? "blue" : "orange"}>
                      {currentDesign.authorityResult.generationSource === "llm" ? "LLM" : "Template fallback"}
                    </Tag>
                  </div>
                  {currentDesign.authorityResult.debug?.errorMessage && (
                    <Alert
                      type="warning"
                      showIcon
                      message="Provider note"
                      description={currentDesign.authorityResult.debug.errorMessage}
                    />
                  )}
                  {currentDesign.authorityResult.authorityMode === "PRESERVE" &&
                    (currentPreserveVariants.length === 2 ? (
                      <>
                        <div className="authority-variant-grid">
                          {currentPreserveVariants.map((variant) => (
                            <PreserveVariantPreview
                              key={variant.variant_id}
                              variant={variant}
                              sourcePageImageUrl={currentDesign.sourcePageImageUrl}
                              imageUrl={currentDesign.imageUrl}
                              selected={currentDesign.selectedAuthorityVariantId === variant.variant_id}
                              onSelect={() => selectAuthorityVariant(variant.variant_id)}
                            />
                          ))}
                        </div>
                        {currentDesign.selectedAuthorityVariantId && (
                          <PreserveRenderedPreview
                            variant={currentPreserveVariants.find(
                              (variant) => variant.variant_id === currentDesign.selectedAuthorityVariantId,
                            ) ?? currentPreserveVariants[0]}
                            imageUrl={currentDesign.imageUrl}
                            sourcePageImageUrl={currentDesign.sourcePageImageUrl}
                          />
                        )}
                      </>
                    ) : (
                      <Alert
                        type="warning"
                        showIcon
                        message="Two Preserve layouts were not returned"
                        description="Generate again after selecting an image. Preserve requires two additive page placements without changing the original PDF page."
                      />
                    ))}

                  {currentDesign.authorityResult.authorityMode === "RESTRUCTURE" && (
                    <RestructureResultPreview
                      result={currentDesign.authorityResult.result}
                      imageUrl={currentDesign.imageUrl}
                    />
                  )}

                  {currentDesign.authorityResult.authorityMode === "TRANSFORM" && (
                    <TransformResultPreview
                      result={currentDesign.authorityResult.result}
                      imageUrl={currentDesign.imageUrl}
                      selectedVariantId={currentDesign.selectedAuthorityVariantId}
                      onSelect={selectAuthorityVariant}
                    />
                  )}

                  {hasReadyAuthorityResult && (
                    <div className="authority-variant-confirmation">
                      <Button type="primary" size="large" onClick={saveAndAdvancePage}>
                        {activePageData && currentPageNumber >= activePageData.totalPages
                          ? "Confirm layout & finish"
                          : `Confirm layout & continue to Page ${currentPageNumber + 1}`}
                      </Button>
                    </div>
                  )}
                </section>
              )}

              <footer className="book-generator-reader-footer">
                <small>
                  Source text is stored verbatim in database checkpoints. Selections and layouts persist across page navigation.
                </small>
              </footer>
            </div>

          </div>
        </section>
      ) : (
        <section className="book-generator-tabs" aria-label="Visual book workspace">
          <Tabs
            activeKey={activeTab}
            onChange={setActiveTab}
            items={[
              {
                key: "create",
                label: "New visual book",
                children: (
                  <div className="book-generator-workspace">
                    <div className="book-generator-form">
                      <div className="book-generator-section-heading">
                        <span>01</span>
                        <div>
                          <h2>Define the source</h2>
                          <p>Set the communication brief before EVI processes the document.</p>
                        </div>
                      </div>

                      <label>
                        <span>Visual book title</span>
                        <Input
                          value={title}
                          onChange={(event) => setTitle(event.target.value)}
                          placeholder="The Quiet Future"
                          maxLength={120}
                        />
                      </label>
                      <label>
                        <span>Communication objective</span>
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
                        Create visual book
                      </Button>
                    </div>

                    <aside className="book-generator-preview" aria-label="Visual book creation guide">
                      <div className="book-generator-preview-topline">
                        <span>WHAT HAPPENS NEXT</span>
                        <span>01 / 03</span>
                      </div>
                      <div className="book-generator-sheet book-generator-guide-sheet">
                        <span className="book-generator-guide-icon">
                          <FolderOpenOutlined />
                        </span>
                        <p className="book-generator-sheet-kicker">EVI WORKSPACE</p>
                        <h2>{title || "Your book title"}</h2>
                        <p>
                          {objective ||
                            "The source PDF and its communication brief will be ready for controlled visual processing."}
                        </p>
                        <ol>
                            <li>Upload the source document</li>
                            <li>Choose AI authority</li>
                            <li>Build visual pages</li>
                        </ol>
                      </div>
                    </aside>
                  </div>
                ),
              },
              {
                key: "folios",
                label: `Visual books${folios.length ? ` (${folios.length})` : ""}`,
                children: (
                  <section className="book-generator-folio-list" aria-live="polite">
                    <div className="book-generator-list-heading">
                      <div>
                        <p className="book-generator-eyebrow">SAVED WORK</p>
                        <h2>Continue a document project.</h2>
                      </div>
                      <Button onClick={() => void loadFolios()} loading={isLoadingFolios}>
                        Refresh
                      </Button>
                    </div>

                    {isLoadingFolios && (
                      <div className="book-generator-list-state">
                        <Spin /> Loading visual books…
                      </div>
                    )}
                    {folioError && (
                      <div className="book-generator-list-state is-error">
                        <p>{folioError}</p>
                        <Button onClick={() => void loadFolios()}>Try again</Button>
                      </div>
                    )}
                    {!isLoadingFolios && !folioError && folios.length === 0 && (
                      <Empty description="No visual books yet. Create your first document project." />
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
                              <span>Open workspace →</span>
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
