package com.evi.model

import com.fasterxml.jackson.databind.JsonNode

enum class AuthorityMode(val promptFileName: String) {
    PRESERVE("1-preserve.md"),
    RESTRUCTURE("2-restructure.md"),
    TRANSFORM("3-transform.md"),
}

data class AuthorityGenerationRequest(
    val authorityMode: AuthorityMode? = null,
    val sourceDocumentContent: String,
    val sourceStructureJson: String? = null,
    val visualAssets: List<VisualBookImageMetadata> = emptyList(),
    val userInstructions: String? = null,
)

data class AuthorityGenerationResponse(
    val authorityMode: AuthorityMode,
    val generationSource: String,
    val result: JsonNode? = null,
    val debug: VisualBookDebugInfo? = null,
)
