import React from "react";
import { Document, Page, Text, View, StyleSheet, Image } from "@react-pdf/renderer";
import { parseStructuredDocument, type DocumentBlock } from "./StructuredDocumentRenderer";

export interface PdfPageItem {
  pageNumber: number;
  pageText: string;
  headline: string;
  summary: string;
  rationale: string;
  template: string;
  imagePlacement: string;
  imageUrl?: string;
  figureCaption?: string;
}

interface VisualBookPdfDocumentProps {
  bookTitle?: string;
  bookObjective?: string;
  pages: PdfPageItem[];
}

const styles = StyleSheet.create({
  page: {
    paddingTop: 28,
    paddingBottom: 28,
    paddingHorizontal: 36,
    backgroundColor: "#FDFBF7",
    fontFamily: "Helvetica",
    color: "#18221B",
    display: "flex",
    flexDirection: "column",
    justifyContent: "space-between",
  },
  header: {
    display: "flex",
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    borderBottomWidth: 1,
    borderBottomColor: "#D3DCD4",
    borderBottomStyle: "solid",
    paddingBottom: 6,
    marginBottom: 14,
  },
  headerTitle: {
    fontSize: 9,
    letterSpacing: 1.2,
    textTransform: "uppercase",
    color: "#4B5A50",
    fontWeight: "bold",
  },
  headerPage: {
    fontSize: 8.5,
    color: "#7E8E83",
  },
  mainContent: {
    flexGrow: 1,
    display: "flex",
    flexDirection: "column",
  },
  kicker: {
    fontSize: 8,
    color: "#2C6E49",
    letterSpacing: 1.2,
    textTransform: "uppercase",
    marginBottom: 4,
    fontWeight: "bold",
  },
  headline: {
    fontSize: 20,
    lineHeight: 1.2,
    fontWeight: "bold",
    color: "#111827",
    marginBottom: 8,
  },
  executiveDeck: {
    fontSize: 9.5,
    fontStyle: "italic",
    lineHeight: 1.45,
    color: "#374151",
    marginBottom: 12,
    paddingBottom: 8,
    borderBottomWidth: 1,
    borderBottomColor: "#E2E8F0",
    borderBottomStyle: "dashed",
  },
  // Hero split layout
  heroSplitRow: {
    display: "flex",
    flexDirection: "row",
    gap: 20,
    flexGrow: 1,
    alignItems: "flex-start",
  },
  splitTextCol: {
    width: "58%",
    display: "flex",
    flexDirection: "column",
  },
  splitImageCol: {
    width: "40%",
    backgroundColor: "#FBFBF9",
    borderWidth: 1,
    borderColor: "#E2E8F0",
    borderRadius: 4,
    padding: 8,
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
    justifyContent: "center",
  },
  pageImage: {
    maxWidth: "100%",
    maxHeight: 340,
    objectFit: "contain",
  },
  figureCaption: {
    fontSize: 7.5,
    fontStyle: "italic",
    color: "#64748B",
    textAlign: "center",
    marginTop: 6,
    paddingHorizontal: 4,
  },
  imagePlaceholder: {
    fontSize: 10,
    color: "#6B7C72",
    fontStyle: "italic",
    textAlign: "center",
    padding: 16,
  },
  rationaleBox: {
    marginTop: 10,
    paddingTop: 6,
    borderTopWidth: 1,
    borderTopColor: "#E2E8F0",
    borderTopStyle: "dashed",
  },
  rationaleText: {
    fontSize: 7.5,
    fontStyle: "italic",
    color: "#718096",
    lineHeight: 1.3,
  },
  // Editorial layout
  editorialBanner: {
    width: "100%",
    maxHeight: 190,
    backgroundColor: "#FBFBF9",
    borderWidth: 1,
    borderColor: "#E2E8F0",
    borderRadius: 4,
    padding: 8,
    marginBottom: 12,
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
    justifyContent: "center",
  },
  editorialBannerImage: {
    maxWidth: "100%",
    maxHeight: 160,
    objectFit: "contain",
  },
  editorialTwoCol: {
    display: "flex",
    flexDirection: "row",
    gap: 18,
  },
  editorialCol: {
    flex: 1,
  },
  // Structured Document Styling inside PDF
  docTitle: {
    fontSize: 15,
    fontWeight: "bold",
    color: "#111827",
    marginBottom: 2,
  },
  docSubtitle: {
    fontSize: 9.5,
    fontWeight: "bold",
    color: "#2C6E49",
    marginBottom: 3,
  },
  docContact: {
    fontSize: 8,
    color: "#4B5563",
    marginBottom: 8,
    paddingBottom: 4,
    borderBottomWidth: 1,
    borderBottomColor: "#E5E7EB",
  },
  sectionHeaderContainer: {
    marginTop: 8,
    marginBottom: 4,
    display: "flex",
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
  },
  sectionHeaderText: {
    fontSize: 8.5,
    fontWeight: "bold",
    color: "#1B4D3E",
    letterSpacing: 0.8,
    textTransform: "uppercase",
  },
  sectionDivider: {
    flexGrow: 1,
    height: 1,
    backgroundColor: "#CBD5E1",
  },
  subHeaderBox: {
    backgroundColor: "#F8FAFC",
    borderLeftWidth: 2,
    borderLeftColor: "#1B4D3E",
    paddingLeft: 4,
    paddingVertical: 2,
    marginTop: 6,
    marginBottom: 3,
  },
  subHeaderText: {
    fontSize: 8.5,
    fontWeight: "bold",
    color: "#111827",
  },
  keyValueRow: {
    display: "flex",
    flexDirection: "row",
    marginTop: 2,
    marginBottom: 2,
  },
  keyText: {
    fontSize: 8,
    fontWeight: "bold",
    color: "#111827",
  },
  valueText: {
    fontSize: 8,
    color: "#374151",
    flexShrink: 1,
  },
  bulletList: {
    marginTop: 2,
    marginBottom: 4,
  },
  bulletRow: {
    display: "flex",
    flexDirection: "row",
    marginBottom: 3,
    paddingLeft: 4,
  },
  bulletDot: {
    fontSize: 7.5,
    color: "#2C6E49",
    marginRight: 4,
    width: 6,
  },
  bulletContent: {
    flex: 1,
  },
  bulletText: {
    fontSize: 7.5,
    color: "#374151",
    lineHeight: 1.35,
  },
  bulletTitle: {
    fontWeight: "bold",
    color: "#111827",
  },
  bodyText: {
    fontSize: 8,
    lineHeight: 1.4,
    color: "#374151",
    marginBottom: 4,
  },
  footer: {
    display: "flex",
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    borderTopWidth: 1,
    borderTopColor: "#D3DCD4",
    borderTopStyle: "solid",
    paddingTop: 6,
    marginTop: 12,
  },
  footerBrand: {
    fontSize: 7.5,
    color: "#7E8E83",
    letterSpacing: 0.8,
  },
  footerMeta: {
    fontSize: 7.5,
    color: "#7E8E83",
  },
});

function renderPdfStructuredBlocks(blocks: DocumentBlock[]) {
  if (blocks.length === 0) {
    return <Text style={styles.bodyText}>(No text detected on this page)</Text>;
  }

  return blocks.map((block, idx) => {
    switch (block.type) {
      case "doc-title":
        return (
          <Text key={idx} style={styles.docTitle}>
            {block.text}
          </Text>
        );
      case "doc-subtitle":
        return (
          <Text key={idx} style={styles.docSubtitle}>
            {block.text}
          </Text>
        );
      case "doc-contact":
        return (
          <Text key={idx} style={styles.docContact}>
            {block.text}
          </Text>
        );
      case "section-header":
        return (
          <View key={idx} style={styles.sectionHeaderContainer}>
            <Text style={styles.sectionHeaderText}>{block.text}</Text>
            <View style={styles.sectionDivider} />
          </View>
        );
      case "sub-header":
        return (
          <View key={idx} style={styles.subHeaderBox}>
            <Text style={styles.subHeaderText}>{block.text}</Text>
          </View>
        );
      case "key-value":
        return (
          <View key={idx} style={styles.keyValueRow}>
            <Text style={styles.keyText}>{block.key} </Text>
            <Text style={styles.valueText}>{block.value}</Text>
          </View>
        );
      case "bullet-list":
        return (
          <View key={idx} style={styles.bulletList}>
            {block.items.map((item, bIdx) => (
              <View key={bIdx} style={styles.bulletRow}>
                <Text style={styles.bulletDot}>•</Text>
                <View style={styles.bulletContent}>
                  <Text style={styles.bulletText}>
                    {item.title ? (
                      <Text style={styles.bulletTitle}>{item.title} </Text>
                    ) : null}
                    {item.body}
                  </Text>
                </View>
              </View>
            ))}
          </View>
        );
      case "paragraph":
        return (
          <Text key={idx} style={styles.bodyText}>
            {block.text}
          </Text>
        );
      default:
        return null;
    }
  });
}

export const VisualBookPdfDocument: React.FC<VisualBookPdfDocumentProps> = ({
  bookTitle,
  bookObjective,
  pages,
}) => {
  const displayTitle = bookTitle || "EY Visual Book";
  const totalPages = Math.max(1, pages.length);

  return (
    <Document title={displayTitle} author="EY Visual Book Generator">
      {pages.map((page) => {
        const isEditorial = page.template === "editorial";
        const hasImage = Boolean(page.imageUrl);
        const parsedBlocks = parseStructuredDocument(page.pageText);

        return (
          <Page key={page.pageNumber} size="A4" orientation="landscape" style={styles.page}>
            {/* Running Header */}
            <View style={styles.header}>
              <Text style={styles.headerTitle}>{displayTitle}</Text>
              <Text style={styles.headerPage}>
                Page {page.pageNumber} of {totalPages}
              </Text>
            </View>

            {/* Main Page Layout */}
            <View style={styles.mainContent}>
              <Text style={styles.kicker}>
                {page.template.replace("-", " ")} · {page.imagePlacement}
              </Text>
              <Text style={styles.headline}>{page.headline}</Text>

              {page.summary ? (
                <Text style={styles.executiveDeck}>{page.summary}</Text>
              ) : null}

              {isEditorial ? (
                // Editorial Layout (Inline Diagram Banner + Technical Specs)
                <View>
                  {hasImage && (
                    <View style={styles.editorialBanner}>
                      <Image src={page.imageUrl!} style={styles.editorialBannerImage} />
                      {page.figureCaption ? (
                        <Text style={styles.figureCaption}>{page.figureCaption}</Text>
                      ) : null}
                    </View>
                  )}
                  <View style={styles.editorialTwoCol}>
                    <View style={styles.editorialCol}>
                      {renderPdfStructuredBlocks(parsedBlocks.slice(0, Math.ceil(parsedBlocks.length / 2)))}
                    </View>
                    <View style={styles.editorialCol}>
                      {renderPdfStructuredBlocks(parsedBlocks.slice(Math.ceil(parsedBlocks.length / 2)))}
                      {page.rationale ? (
                        <View style={styles.rationaleBox}>
                          <Text style={styles.rationaleText}>Rationale: {page.rationale}</Text>
                        </View>
                      ) : null}
                    </View>
                  </View>
                </View>
              ) : (
                // Hero-Split Layout (Side-by-side technical specs + Diagram Panel)
                <View style={styles.heroSplitRow}>
                  {page.imagePlacement === "left" && (
                    <View style={styles.splitImageCol}>
                      {hasImage ? (
                        <>
                          <Image src={page.imageUrl!} style={styles.pageImage} />
                          {page.figureCaption ? (
                            <Text style={styles.figureCaption}>{page.figureCaption}</Text>
                          ) : null}
                        </>
                      ) : (
                        <Text style={styles.imagePlaceholder}>Visual Element</Text>
                      )}
                    </View>
                  )}

                  <View style={styles.splitTextCol}>
                    {renderPdfStructuredBlocks(parsedBlocks)}
                    {page.rationale ? (
                      <View style={styles.rationaleBox}>
                        <Text style={styles.rationaleText}>Rationale: {page.rationale}</Text>
                      </View>
                    ) : null}
                  </View>

                  {page.imagePlacement !== "left" && (
                    <View style={styles.splitImageCol}>
                      {hasImage ? (
                        <>
                          <Image src={page.imageUrl!} style={styles.pageImage} />
                          {page.figureCaption ? (
                            <Text style={styles.figureCaption}>{page.figureCaption}</Text>
                          ) : null}
                        </>
                      ) : (
                        <Text style={styles.imagePlaceholder}>Visual Element</Text>
                      )}
                    </View>
                  )}
                </View>
              )}
            </View>

            {/* Running Footer */}
            <View style={styles.footer}>
              <Text style={styles.footerBrand}>
                EY Visual Book Generator · {bookObjective || "Enterprise Documentation"}
              </Text>
              <Text style={styles.footerMeta}>Composed Layout</Text>
            </View>
          </Page>
        );
      })}
    </Document>
  );
};
