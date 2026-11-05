# LLM Prompt Library

These files are the source of truth for the EY Visual Book Generator's LLM authority modes.

## Prompt files

- `1-preserve.md` — strict source text & sequence protection with contextual visual insertion. Text and original order cannot be changed; multi-page natural flow is supported.
- `2-restructure.md` — structural and layout freedom with zero lexical mutation. Source wording and meaning must remain unchanged.
- `3-transform.md` — full editorial and design authority with strict factual grounding and genuinely different variants.

## Shared input placeholders

The prompts expect the runtime to provide:

- `{{SOURCE_DOCUMENT_CONTENT}}` — source text or structured source content.
- `{{SOURCE_STRUCTURE_JSON}}` — indexed source blocks and formatting metadata.
- `{{VISUAL_ASSETS_METADATA}}` — asset IDs, metadata, and multimodal asset inputs when available.
- `{{USER_INSTRUCTIONS}}` — optional instructions from the user.

The current application does not yet provide all of these inputs. At present, it sends page text and image metadata to the layout endpoint. The runtime integration task must close that gap before claiming full support for these contracts.

## Runtime rules

1. Select exactly one prompt from the user's explicit authority mode.
2. Pass the matching response schema to the LLM provider's structured-output configuration.
3. Validate the response against the selected mode before rendering it.
4. Preserve source content application-side; do not rely on a model attestation alone.
5. Keep prompt changes version-controlled and review them as product-contract changes.

These prompts define model behavior. They do not replace deterministic validation or PDF rendering rules.
