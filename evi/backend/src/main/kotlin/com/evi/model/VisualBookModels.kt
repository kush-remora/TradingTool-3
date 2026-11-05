package com.evi.model

data class VisualBookImageMetadata(
    val name: String,
    val mimeType: String? = null,
    val byteSize: Long? = null,
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
