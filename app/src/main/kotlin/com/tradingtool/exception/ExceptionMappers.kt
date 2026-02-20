package com.tradingtool.exception

import com.tradingtool.core.watchlist.service.WatchlistServiceError
import com.tradingtool.core.watchlist.service.WatchlistServiceNotConfiguredError
import com.tradingtool.core.watchlist.service.WatchlistValidationError
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider

@Provider
class ValidationExceptionMapper : ExceptionMapper<WatchlistValidationError> {
    override fun toResponse(exception: WatchlistValidationError): Response {
        val json = """{"detail":"${escapeJson(exception.message ?: "Validation failed")}"}"""
        return Response.status(400)
            .entity(json)
            .type(MediaType.APPLICATION_JSON)
            .build()
    }
}

@Provider
class ServiceUnavailableExceptionMapper : ExceptionMapper<WatchlistServiceNotConfiguredError> {
    override fun toResponse(exception: WatchlistServiceNotConfiguredError): Response {
        val json = """{"detail":"${escapeJson(exception.message ?: "Service not configured")}"}"""
        return Response.status(503)
            .entity(json)
            .type(MediaType.APPLICATION_JSON)
            .build()
    }
}

@Provider
class GenericServiceExceptionMapper : ExceptionMapper<WatchlistServiceError> {
    override fun toResponse(exception: WatchlistServiceError): Response {
        val json = """{"detail":"${escapeJson(exception.message ?: "Service error")}"}"""
        return Response.status(500)
            .entity(json)
            .type(MediaType.APPLICATION_JSON)
            .build()
    }
}

private fun escapeJson(text: String): String {
    return text.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
