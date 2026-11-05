import React, { useMemo } from "react";
import "./structuredDocument.css";

export interface BulletItem {
  title?: string;
  body: string;
}

export type DocumentBlock =
  | { type: "doc-title"; text: string }
  | { type: "doc-subtitle"; text: string }
  | { type: "doc-contact"; text: string }
  | { type: "section-header"; text: string }
  | { type: "sub-header"; text: string }
  | { type: "key-value"; key: string; value: string }
  | { type: "bullet-list"; items: BulletItem[] }
  | { type: "paragraph"; text: string };

export function parseStructuredDocument(rawText: string): DocumentBlock[] {
  if (!rawText || !rawText.trim()) return [];

  const lines = rawText
    .split("\n")
    .map((l) => l.trim())
    .filter(Boolean);

  const blocks: DocumentBlock[] = [];

  const isBullet = (l: string) => /^[•\-\*]\s*/.test(l) || /^\d+\.\s+/.test(l);
  const isSectionHeader = (l: string) => {
    if (l.includes(":") && !l.endsWith(":")) return false;
    const isAllCaps = l === l.toUpperCase() && /[A-Z]/.test(l) && !l.includes("|") && !l.includes("@");
    const isStd = /^(PROFESSIONAL SUMMARY|TECHNICAL SKILLS|PROFESSIONAL EXPERIENCE|EXPERIENCE|EDUCATION|SUMMARY|ARCHITECTURE|SPECIFICATIONS|WORKFLOW|OBJECTIVE|OVERVIEW)/i.test(l);
    return (isAllCaps && l.length < 50) || isStd;
  };
  const isSubHeader = (l: string) =>
    l.includes("|") && (/Present/i.test(l) || /\d{4}/.test(l) || /Nagarro|Udaan|Google|Microsoft|EY/i.test(l));
  const isKeyValue = (l: string) => {
    const colonIdx = l.indexOf(":");
    return colonIdx > 0 && colonIdx < 35 && !l.startsWith("http");
  };

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];

    // 1. Detect Bullet Items (•, -, *, or numbered like 1.)
    if (isBullet(line)) {
      const cleanBullet = line.replace(/^[•\-\*]\s*/, "").replace(/^\d+\.\s+/, "");
      const colonIdx = cleanBullet.indexOf(":");
      let title: string | undefined = undefined;
      let body = cleanBullet;

      if (colonIdx > 0 && colonIdx < 50) {
        title = cleanBullet.substring(0, colonIdx + 1);
        body = cleanBullet.substring(colonIdx + 1).trim();
      }

      const lastBlock = blocks[blocks.length - 1];
      if (lastBlock && lastBlock.type === "bullet-list") {
        lastBlock.items.push({ title, body });
      } else {
        blocks.push({ type: "bullet-list", items: [{ title, body }] });
      }
      continue;
    }

    // 2. Detect Document Header lines (first 3 lines of resume/doc)
    if (i === 0 && line.length < 50 && !line.includes(":")) {
      blocks.push({ type: "doc-title", text: line });
      continue;
    }
    if (i === 1 && line.length < 60 && !line.includes(":") && !line.includes("|")) {
      blocks.push({ type: "doc-subtitle", text: line });
      continue;
    }
    if ((i === 1 || i === 2) && (line.includes("@") || line.includes("|") || line.includes("+91") || line.includes("http"))) {
      blocks.push({ type: "doc-contact", text: line });
      continue;
    }

    // 3. Detect Section Headers (e.g. ALL CAPS or prominent technical headers)
    if (isSectionHeader(line)) {
      blocks.push({ type: "section-header", text: line });
      continue;
    }

    // 4. Detect Sub-headers / Role lines with dates/locations
    if (isSubHeader(line)) {
      blocks.push({ type: "sub-header", text: line });
      continue;
    }

    // 5. Detect Key-Value specifications (e.g. "Languages & Frameworks: Java, Kotlin...")
    if (isKeyValue(line)) {
      const colonIdx = line.indexOf(":");
      blocks.push({
        type: "key-value",
        key: line.substring(0, colonIdx + 1),
        value: line.substring(colonIdx + 1).trim(),
      });
      continue;
    }

    // 6. Wrapped Line Continuation Check (PDFBox splits lines within paragraphs and bullets)
    const lastBlock = blocks[blocks.length - 1];
    if (lastBlock) {
      if (lastBlock.type === "bullet-list" && lastBlock.items.length > 0) {
        const lastItem = lastBlock.items[lastBlock.items.length - 1];
        lastItem.body = lastItem.body ? `${lastItem.body} ${line}` : line;
        continue;
      }
      if (lastBlock.type === "key-value") {
        lastBlock.value = lastBlock.value ? `${lastBlock.value} ${line}` : line;
        continue;
      }
      if (lastBlock.type === "paragraph") {
        lastBlock.text = lastBlock.text ? `${lastBlock.text} ${line}` : line;
        continue;
      }
    }

    // 7. Regular Paragraph
    blocks.push({ type: "paragraph", text: line });
  }

  return blocks;
}

interface StructuredDocumentRendererProps {
  rawText: string;
  className?: string;
}

export const StructuredDocumentRenderer: React.FC<StructuredDocumentRendererProps> = ({
  rawText,
  className = "",
}) => {
  const blocks = useMemo(() => parseStructuredDocument(rawText), [rawText]);

  if (blocks.length === 0) {
    return (
      <div className={`structured-doc-empty ${className}`}>
        <em>(No formatted document text detected)</em>
      </div>
    );
  }

  return (
    <div className={`structured-doc-root ${className}`}>
      {blocks.map((block, index) => {
        switch (block.type) {
          case "doc-title":
            return (
              <h1 key={index} className="structured-doc-title">
                {block.text}
              </h1>
            );

          case "doc-subtitle":
            return (
              <h2 key={index} className="structured-doc-subtitle">
                {block.text}
              </h2>
            );

          case "doc-contact":
            return (
              <div key={index} className="structured-doc-contact">
                {block.text.split("|").map((item, ci) => (
                  <span key={ci} className="structured-contact-pill">
                    {item.trim()}
                  </span>
                ))}
              </div>
            );

          case "section-header":
            return (
              <div key={index} className="structured-section-header">
                <span className="structured-section-title">{block.text}</span>
                <span className="structured-section-divider" />
              </div>
            );

          case "sub-header":
            return (
              <h3 key={index} className="structured-sub-header">
                {block.text}
              </h3>
            );

          case "key-value":
            return (
              <div key={index} className="structured-key-value">
                <strong className="structured-key">{block.key}</strong>{" "}
                <span className="structured-value">{block.value}</span>
              </div>
            );

          case "bullet-list":
            return (
              <ul key={index} className="structured-bullet-list">
                {block.items.map((item, bi) => (
                  <li key={bi} className="structured-bullet-item">
                    {item.title && <strong className="structured-bullet-title">{item.title} </strong>}
                    <span className="structured-bullet-body">{item.body}</span>
                  </li>
                ))}
              </ul>
            );

          case "paragraph":
            return (
              <p key={index} className="structured-paragraph">
                {block.text}
              </p>
            );

          default:
            return null;
        }
      })}
    </div>
  );
};
