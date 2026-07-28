package com.tradingtool.core.note

import java.time.OffsetDateTime

data class Note(
    val id: Long,
    val instrumentToken: Long,
    val notes: String,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

data class CreateNoteRequest(val instrumentToken: Long, val notes: String)
