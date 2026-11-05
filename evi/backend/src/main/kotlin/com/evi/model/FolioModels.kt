package com.evi.model

import java.util.UUID

data class CreateFolioRequest(
    val title: String,
    val objective: String,
    val theme: String?,
    val sourceFileName: String,
    val sourceFileSizeBytes: Long,
)

data class FolioSummary(
    val id: String,
    val title: String,
    val objective: String,
    val theme: String,
    val sourceFileName: String,
    val sourceFileSizeBytes: Long,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
)

data class StoredFolio(
    val id: UUID,
    val title: String,
    val objective: String,
    val theme: String,
    val sourcePdfPath: String,
    val sourceFileName: String,
    val sourceFileSizeBytes: Long,
)

data class StoredFolioRecord(
    val id: UUID,
    val title: String,
    val objective: String,
    val theme: String,
    val sourcePdfPath: String?,
    val sourceFileName: String?,
    val sourceFileSizeBytes: Long?,
    val pageCount: Int?,
    val status: String,
)

data class StoredFolioPage(
    val id: UUID,
    val folioId: UUID,
    val pageNumber: Int,
    val extractedText: String?,
    val status: String,
)

data class FolioPageSummary(
    val folioId: String,
    val pageNumber: Int,
    val totalPages: Int,
    val extractedText: String,
    val status: String,
)
