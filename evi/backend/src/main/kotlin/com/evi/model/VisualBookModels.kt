package com.evi.model

data class VisualBookImageMetadata(
    val name: String,
    val mimeType: String? = null,
    val byteSize: Long? = null,
)

data class VisualBookLayoutRequest(
    val bookTitle: String? = null,
    val objective: String? = null,
    val pageText: String,
    val images: List<VisualBookImageMetadata> = emptyList(),
)

data class VisualBookLayoutOption(
    val id: String,
    val template: String,
    val imagePlacement: String,
    val headline: String,
    val summary: String,
    val rationale: String,
    val figureCaption: String? = null,
)

data class VisualBookDebugInfo(
    val latencyMs: Long,
    val apiKeyConfigured: Boolean,
    val providerStatus: String,
    val errorMessage: String? = null,
    val model: String,
    val endpoint: String? = null,
    val requestPromptSnippet: String? = null,
    val responseStatusCode: Int? = null,
    val responseSnippet: String? = null,
)

data class VisualBookLayoutResponse(
    val options: List<VisualBookLayoutOption>,
    val generationSource: String,
    val debug: VisualBookDebugInfo? = null,
)
