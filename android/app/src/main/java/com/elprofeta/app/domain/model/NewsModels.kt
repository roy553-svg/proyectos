package com.elprofeta.app.domain.model

import java.time.Instant
import java.time.LocalDate

/** Estado de la animacion de una noticia (lo calcula el backend en la Fase 2). */
enum class VideoStatus {
    NOT_REQUESTED,
    PENDING,
    PROCESSING,
    READY,
    FAILED,
    ;

    /** True si el backend esta generando la animacion en este momento. */
    val isInProgress: Boolean
        get() = this == PENDING || this == PROCESSING
}

/** Cabecera de la edicion semanal publicada. */
data class Edition(
    val id: Int,
    val title: String,
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val publishedAt: Instant?,
)

/** Noticia lista para mostrar. */
data class Article(
    val id: Int,
    val title: String,
    val content: String,
    val summary: String?,
    val category: String,
    val language: String,
    val imageUrl: String?,
    val videoUrl: String?,
    val sourceUrl: String?,
    val sourceName: String?,
    val videoStatus: VideoStatus,
    val publishedAt: Instant,
    val position: Int,
) {
    /** True si hay una animacion reproducible. */
    val hasVideo: Boolean
        get() = !videoUrl.isNullOrBlank()

    /** Texto breve para la portada: el resumen del backend o el inicio del cuerpo. */
    val teaser: String
        get() = summary?.takeIf { it.isNotBlank() } ?: content.take(180).trimEnd() + "…"
}

/** Edicion + noticias, tal y como responde GET /api/v1/news. */
data class NewsFeed(
    val edition: Edition,
    val articles: List<Article>,
) {
    fun articleById(id: Int): Article? = articles.firstOrNull { it.id == id }
}
