package com.elprofeta.app.data.mapper

import com.elprofeta.app.data.remote.dto.ArticleDto
import com.elprofeta.app.data.remote.dto.EditionDto
import com.elprofeta.app.data.remote.dto.NewsFeedDto
import com.elprofeta.app.domain.model.Article
import com.elprofeta.app.domain.model.Edition
import com.elprofeta.app.domain.model.NewsFeed
import com.elprofeta.app.domain.model.VideoStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

/**
 * Conversion DTO -> dominio.
 *
 * El backend envia siempre las fechas en UTC con sufijo `Z`; aun asi se
 * toleran valores sin zona horaria para no romper la app si el contrato
 * cambia.
 */
fun NewsFeedDto.toDomain(): NewsFeed = NewsFeed(
    edition = edition.toDomain(),
    articles = articles.map { it.toDomain() }.sortedBy { it.position },
)

fun EditionDto.toDomain(): Edition = Edition(
    id = id,
    title = title,
    weekStart = LocalDate.parse(weekStart),
    weekEnd = LocalDate.parse(weekEnd),
    publishedAt = publishedAt?.toInstantOrNull(),
)

fun ArticleDto.toDomain(): Article = Article(
    id = id,
    title = title,
    content = content,
    summary = summary,
    category = category,
    language = language,
    imageUrl = imageUrl?.takeIf { it.isNotBlank() },
    videoUrl = videoUrl?.takeIf { it.isNotBlank() },
    sourceUrl = sourceUrl?.takeIf { it.isNotBlank() },
    sourceName = sourceName?.takeIf { it.isNotBlank() },
    videoStatus = videoStatus.toVideoStatus(),
    publishedAt = publishedAt.toInstantOrNull() ?: Instant.EPOCH,
    position = position,
)

/** Un estado desconocido no debe romper la app: se trata como "sin animacion". */
private fun String.toVideoStatus(): VideoStatus = when (lowercase()) {
    "pending" -> VideoStatus.PENDING
    "processing" -> VideoStatus.PROCESSING
    "ready" -> VideoStatus.READY
    "failed" -> VideoStatus.FAILED
    else -> VideoStatus.NOT_REQUESTED
}

private fun String.toInstantOrNull(): Instant? {
    // 1) "2026-09-21T08:00:00Z" (lo que envia el backend).
    try {
        return OffsetDateTime.parse(this).toInstant()
    } catch (_: DateTimeParseException) {
        // continua con el siguiente formato
    }
    // 2) "2026-09-21T08:00:00" (sin zona): se interpreta como UTC.
    return try {
        LocalDateTime.parse(this).toInstant(ZoneOffset.UTC)
    } catch (_: DateTimeParseException) {
        null
    }
}
