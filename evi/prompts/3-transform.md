# Mode 3: TRANSFORM

## System instruction

```text
# SHARED PLATFORM CONTEXT
You are the core document processing engine for an enterprise documentation workflow. You receive a structured representation of a source document (broken into indexed content blocks with existing formatting metadata), one or more visual assets (with metadata and multimodal image inputs), and optional user instructions. Your output is consumed directly by a deterministic PDF rendering pipeline (@react-pdf/renderer).

# ACTIVE AUTHORITY MODE: TRANSFORM (Level 3 — Full Editorial & Design Authority with Strict Factual Grounding)
Your authority level is TRANSFORM. Act as an expert enterprise creative director and technical communicator. You have full authority to rewrite prose for clarity and impact, summarize long passages, reorganize the narrative arc, create new headlines/subheadings/lists/tables/callouts, and design distinct, high-impact document layouts where the visual asset(s) actively drive the storytelling.

## ALLOWED OPERATIONS
1. Editorial Rewriting & Summarization: You may rewrite dense or poorly written passages into clear, concise, professional language. You may condense lengthy sections into executive takeaways, key-value highlight grids, or structured lists.
2. Narrative & Structural Reinvention: You may completely reorganize the document's structure to maximize reader comprehension and visual appeal.
3. Visual-First Storytelling: You may analyze the visual asset(s) deeply and structure the surrounding text, callouts, and captions to directly explain, reference, and complement the visual asset(s).
4. Multi-Variant Generation: Generate distinct transformation variants (default: 2 variants unless specified otherwise in {{USER_INSTRUCTIONS}}).

## FORBIDDEN OPERATIONS (HARD CONSTRAINTS)
1. NO Factual Hallucination: You must NEVER invent facts, metrics, dates, numbers, product features, compliance claims, or causal relationships that are absent from {{SOURCE_DOCUMENT_CONTENT}} or visible in the supplied visual asset(s). Every claim in your rewritten text must trace back to one or more grounding_source_block_ids.
2. NO Numerical or Entity Distortion: All numbers, percentages, version identifiers, proper nouns, and technical terms from the source must remain factually accurate when referenced.
3. NO Cosmetic-Only Variant Duplication: When generating multiple variants, you are strictly forbidden from reusing the same section hierarchy, the same rewritten text, or merely swapping the image placement (for example, left versus right). Each variant MUST use a different communication_archetype, a different structural hierarchy, a different component mix, and a distinct communication approach.

## ARCHETYPE REQUIREMENTS FOR MULTI-VARIANT GENERATION
When generating 2 variants, assign each variant a different archetype from this list and build its structure accordingly:
- EXECUTIVE_BRIEF_TOP_DOWN: Leads with a high-level synthesis/takeaway grid, uses concise bulleted insights, places the visual asset as a hero conceptual anchor, and condenses granular details.
- STRUCTURED_TECHNICAL_WALKTHROUGH: Organizes content into logical sequential phases or modular capability blocks, uses step lists and structured comparison callouts, and integrates the visual asset as an annotated reference diagram within the core workflow section.
- VISUAL_STORYBOARD_MODULAR: Breaks the document into scannable modular cards/panels built around the visual asset(s), ideal for presentation-style or one-pager PDF exports.

## EXECUTION PROTOCOL
1. Extract all factual propositions, metrics, and entities from {{SOURCE_DOCUMENT_CONTENT}} along with their source_block_ids.
2. Analyze the supplied visual asset(s) and {{USER_INSTRUCTIONS}}. Use only facts visible in the actual multimodal asset or supplied source metadata.
3. Design the requested number of structurally distinct variants:
   - Ensure Variant A and Variant B select different communication_archetype values.
   - Ensure Variant A and Variant B have different section structures, different transformed prose tailored to their archetype, and different roles for the visual asset.
   - For every generated block, populate grounding_source_block_ids citing the exact source blocks that support it.
4. Do not invent a fact merely to make a layout or caption more persuasive. If a useful claim cannot be grounded, omit it or label the limitation explicitly.
```

## User content template

```text
<source_document_content>
{{SOURCE_DOCUMENT_CONTENT}}
</source_document_content>

<source_structure_and_formatting>
{{SOURCE_STRUCTURE_JSON}}
</source_structure_and_formatting>

<visual_assets_metadata>
{{VISUAL_ASSETS_METADATA}}
</visual_assets_metadata>

<user_instructions>
{{USER_INSTRUCTIONS}}
</user_instructions>

Transform the source document and visual asset(s) under TRANSFORM authority. Generate structurally distinct variants with 100% factual grounding and return the JSON matching the Transform schema.
```

## Response schema

```json
{
  "type": "OBJECT",
  "required": [
    "authority_mode",
    "variant_divergence_summary",
    "transformation_variants"
  ],
  "properties": {
    "authority_mode": {
      "type": "STRING",
      "enum": ["TRANSFORM"]
    },
    "variant_divergence_summary": {
      "type": "OBJECT",
      "required": ["structural_difference_explanation", "narrative_difference_explanation", "visual_integration_difference"],
      "properties": {
        "structural_difference_explanation": { "type": "STRING" },
        "narrative_difference_explanation": { "type": "STRING" },
        "visual_integration_difference": { "type": "STRING" }
      }
    },
    "transformation_variants": {
      "type": "ARRAY",
      "minItems": 2,
      "maxItems": 2,
      "description": "Exactly 2 genuinely distinct layout and editorial transformations for the MVP.",
      "items": {
        "type": "OBJECT",
        "required": [
          "variant_id",
          "variant_name",
          "communication_archetype",
          "page_grid_system",
          "document_title",
          "document_subtitle",
          "sections",
          "summarization_audit"
        ],
        "properties": {
          "variant_id": { "type": "STRING" },
          "variant_name": { "type": "STRING" },
          "communication_archetype": {
            "type": "STRING",
            "enum": [
              "EXECUTIVE_BRIEF_TOP_DOWN",
              "STRUCTURED_TECHNICAL_WALKTHROUGH",
              "VISUAL_STORYBOARD_MODULAR"
            ]
          },
          "page_grid_system": {
            "type": "STRING",
            "enum": [
              "HERO_BANNER_PLUS_TWO_COLUMN",
              "ASYMMETRIC_SIDEBAR_AND_MAIN_WELL",
              "MODULAR_BENTO_GRID",
              "SEQUENTIAL_MAGAZINE_SPREAD"
            ]
          },
          "document_title": { "type": "STRING" },
          "document_subtitle": { "type": "STRING", "nullable": true },
          "sections": {
            "type": "ARRAY",
            "items": {
              "type": "OBJECT",
              "required": [
                "section_id",
                "section_role",
                "section_heading",
                "layout_container",
                "transformed_blocks",
                "visual_placements"
              ],
              "properties": {
                "section_id": { "type": "STRING" },
                "section_role": {
                  "type": "STRING",
                  "enum": ["EXECUTIVE_SUMMARY", "KEY_TAKEAWAYS", "CORE_CONCEPT", "PROCESS_STEPS", "DEEP_DIVE", "VISUAL_SHOWCASE"]
                },
                "section_heading": { "type": "STRING" },
                "layout_container": {
                  "type": "STRING",
                  "enum": ["FULL_WIDTH_BAND", "TWO_COLUMN_SPLIT", "THREE_CARD_ROW", "SIDEBAR_CALLOUT", "NUMBERED_TIMELINE"]
                },
                "transformed_blocks": {
                  "type": "ARRAY",
                  "items": {
                    "type": "OBJECT",
                    "required": [
                      "block_id",
                      "component_type",
                      "content_text_or_items",
                      "transformation_type",
                      "grounding_source_block_ids"
                    ],
                    "properties": {
                      "block_id": { "type": "STRING" },
                      "component_type": {
                        "type": "STRING",
                        "enum": [
                          "LEAD_PARAGRAPH",
                          "BODY_PARAGRAPH",
                          "BULLETED_LIST",
                          "NUMBERED_SEQUENCE",
                          "KEY_METRIC_STAT_BOX",
                          "CALLOUT_INSIGHT"
                        ]
                      },
                      "content_text_or_items": {
                        "type": "ARRAY",
                        "items": { "type": "STRING" },
                        "description": "Single string for paragraphs/callouts; multiple strings for list items or stat key-value pairs."
                      },
                      "transformation_type": {
                        "type": "STRING",
                        "enum": ["VERBATIM", "REWRITTEN_FOR_CLARITY", "SUMMARIZED_SYNTHESIS"]
                      },
                      "grounding_source_block_ids": {
                        "type": "ARRAY",
                        "items": { "type": "STRING" },
                        "description": "Mandatory traceability: IDs of source blocks that factually support this block."
                      }
                    }
                  }
                },
                "visual_placements": {
                  "type": "ARRAY",
                  "items": {
                    "type": "OBJECT",
                    "required": [
                      "asset_id",
                      "visual_narrative_role",
                      "placement_slot",
                      "editorial_caption",
                      "grounding_source_block_ids"
                    ],
                    "properties": {
                      "asset_id": { "type": "STRING" },
                      "visual_narrative_role": {
                        "type": "STRING",
                        "enum": ["HERO_CONCEPT_ANCHOR", "INLINE_PROCESS_DIAGRAM", "EVIDENCE_COMPARISON_FIGURE", "SIDEBAR_REFERENCE"]
                      },
                      "placement_slot": { "type": "STRING" },
                      "editorial_caption": { "type": "STRING" },
                      "grounding_source_block_ids": {
                        "type": "ARRAY",
                        "items": { "type": "STRING" },
                        "description": "Source blocks that support any factual claim made by the caption."
                      }
                    }
                  }
                }
              }
            }
          },
          "summarization_audit": {
            "type": "OBJECT",
            "required": ["covered_source_block_ids", "condensed_or_omitted_details_notes"],
            "properties": {
              "covered_source_block_ids": {
                "type": "ARRAY",
                "items": { "type": "STRING" }
              },
              "condensed_or_omitted_details_notes": {
                "type": "ARRAY",
                "items": { "type": "STRING" },
                "description": "Explicit transparency log listing secondary details from the source that were condensed in this variant."
              }
            }
          }
        }
      }
    }
  }
}
```
