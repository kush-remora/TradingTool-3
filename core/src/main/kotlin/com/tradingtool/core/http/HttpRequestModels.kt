package com.tradingtool.core.http

import java.net.URI
import java.time.Duration

data class HttpRequestData(
    val method: String,
    val uri: URI,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
    val timeout: Duration? = null,
)

data class HttpResponseData(
    val statusCode: Int,
    val body: String,
)

data class HttpBinaryResponseData(
    val statusCode: Int,
    val body: ByteArray,
)
