package com.elprofeta.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs que replican exactamente el contrato del backend.
 *
 * Se mantienen separados del modelo de dominio para que un cambio en la API
 * no obligue a tocar la UI: la conversion vive en [com.elprofeta.app.data.mapper].
 */
@Serializable
data class NewsFeedDto(
    val edition: EditionDto,
    val articles: List<ArticleDto> = emptyList(),
)

@Serializable
data class EditionDto(
    val id: Int,
    val title: String,
    @SerialName("week_start") val weekStart: String,
    @SerialName("week_end") val weekEnd: String,
    @SerialName("published_at") val publishedAt: String? = null,
)

@Serializable
data class ArticleDto(
    val id: Int,
    val title: String,
    val content: String,
    val summary: String? = null,
    val category: String,
    val language: String = "es",
    @SerialName("image_url") val imageUrl: String? = null,
    // Nulo mientras la animacion no este lista: la app muestra la imagen.
    @SerialName("video_url") val videoUrl: String? = null,
    @SerialName("source_url") val sourceUrl: String? = null,
    @SerialName("source_name") val sourceName: String? = null,
    @SerialName("video_status") val videoStatus: String = "not_requested",
    @SerialName("published_at") val publishedAt: String,
    val position: Int,
)

/** Cuerpo de error de la API (`{"detail": "..."}`). */
@Serializable
data class ApiErrorDto(
    val detail: String? = null,
)
