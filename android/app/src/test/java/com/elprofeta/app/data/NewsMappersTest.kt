package com.elprofeta.app.data

import com.elprofeta.app.data.mapper.toDomain
import com.elprofeta.app.data.remote.dto.ArticleDto
import com.elprofeta.app.data.remote.dto.EditionDto
import com.elprofeta.app.data.remote.dto.NewsFeedDto
import com.elprofeta.app.domain.model.VideoStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsMappersTest {

    @Test
    fun `mapea la edicion y ordena las noticias por posicion`() {
        val dto = NewsFeedDto(
            edition = edition(),
            articles = listOf(article(id = 2, position = 2), article(id = 1, position = 1)),
        )

        val feed = dto.toDomain()

        assertEquals(LocalDate.of(2026, 9, 21), feed.edition.weekStart)
        assertEquals(LocalDate.of(2026, 9, 27), feed.edition.weekEnd)
        assertEquals(listOf(1, 2), feed.articles.map { it.id })
    }

    @Test
    fun `una noticia sin animacion se mapea con videoUrl nulo`() {
        val article = article(videoUrl = null, videoStatus = "not_requested").toDomain()

        assertNull(article.videoUrl)
        assertFalse(article.hasVideo)
        assertEquals(VideoStatus.NOT_REQUESTED, article.videoStatus)
        assertFalse(article.videoStatus.isInProgress)
    }

    @Test
    fun `una animacion en curso se marca como en progreso`() {
        val article = article(videoUrl = null, videoStatus = "processing").toDomain()

        assertTrue(article.videoStatus.isInProgress)
    }

    @Test
    fun `un estado de video desconocido no rompe el mapeo`() {
        val article = article(videoStatus = "algo-nuevo").toDomain()

        assertEquals(VideoStatus.NOT_REQUESTED, article.videoStatus)
    }

    @Test
    fun `las cadenas vacias se normalizan a nulo`() {
        val article = article(videoUrl = "", imageUrl = "  ").toDomain()

        assertNull(article.videoUrl)
        assertNull(article.imageUrl)
    }

    @Test
    fun `la fecha en UTC se convierte a Instant`() {
        val article = article(publishedAt = "2026-09-21T08:00:00Z").toDomain()

        assertEquals("2026-09-21T08:00:00Z", article.publishedAt.toString())
    }

    @Test
    fun `una fecha sin zona horaria se interpreta como UTC`() {
        val article = article(publishedAt = "2026-09-21T08:00:00").toDomain()

        assertEquals("2026-09-21T08:00:00Z", article.publishedAt.toString())
    }

    @Test
    fun `el teaser usa el resumen si existe`() {
        assertEquals("Resumen corto.", article(summary = "Resumen corto.").toDomain().teaser)
    }

    @Test
    fun `el teaser cae al contenido si no hay resumen`() {
        val article = article(summary = null, content = "Cuerpo de la noticia.").toDomain()

        assertTrue(article.teaser.startsWith("Cuerpo de la noticia."))
    }

    private fun edition() = EditionDto(
        id = 1,
        title = "El Profeta",
        weekStart = "2026-09-21",
        weekEnd = "2026-09-27",
        publishedAt = "2026-09-21T08:00:00Z",
    )

    private fun article(
        id: Int = 1,
        position: Int = 1,
        summary: String? = "Resumen",
        content: String = "Contenido",
        imageUrl: String? = "https://cdn.example.com/i.jpg",
        videoUrl: String? = "https://cdn.example.com/v.mp4",
        videoStatus: String = "ready",
        publishedAt: String = "2026-09-21T08:00:00Z",
    ) = ArticleDto(
        id = id,
        title = "Titular $id",
        content = content,
        summary = summary,
        category = "magic",
        language = "es",
        imageUrl = imageUrl,
        videoUrl = videoUrl,
        sourceUrl = null,
        sourceName = null,
        videoStatus = videoStatus,
        publishedAt = publishedAt,
        position = position,
    )
}
