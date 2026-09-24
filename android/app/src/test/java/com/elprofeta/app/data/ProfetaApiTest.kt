package com.elprofeta.app.data

import com.elprofeta.app.data.remote.ProfetaApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit

/** Verifica el contrato con el backend usando un servidor HTTP local. */
class ProfetaApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ProfetaApi

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ProfetaApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `pide la edicion del usuario indicado y parsea la respuesta`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(FEED_JSON))

        val feed = api.getNews(userId = 7)

        val request = server.takeRequest()
        assertEquals("/api/v1/news?user_id=7", request.path)
        assertEquals(1, feed.edition.id)
        assertEquals("2026-09-21", feed.edition.weekStart)
        assertEquals(2, feed.articles.size)
        assertEquals("https://cdn.example.com/v.mp4", feed.articles[0].videoUrl)
        // La segunda noticia no tiene animacion: video_url llega como null.
        assertNull(feed.articles[1].videoUrl)
        assertEquals("processing", feed.articles[1].videoStatus)
    }

    @Test
    fun `un campo nuevo en la respuesta no rompe el parseo`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                FEED_JSON.replace("\"articles\"", "\"campo_futuro\": 42, \"articles\""),
            ),
        )

        assertEquals(2, api.getNews(userId = 1).articles.size)
    }

    @Test(expected = HttpException::class)
    fun `un 404 se propaga como HttpException`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(404).setBody("""{"detail":"No existen preferencias"}"""),
        )

        api.getNews(userId = 999)
    }

    private companion object {
        val FEED_JSON = """
        {
          "edition": {
            "id": 1,
            "week_start": "2026-09-21",
            "week_end": "2026-09-27",
            "title": "El Profeta - Edicion Semanal",
            "published_at": "2026-09-21T08:00:00Z"
          },
          "articles": [
            {
              "id": 1,
              "title": "Varitas inteligentes",
              "content": "Contenido",
              "summary": "Resumen",
              "category": "technology",
              "language": "es",
              "image_url": "https://cdn.example.com/i.jpg",
              "video_url": "https://cdn.example.com/v.mp4",
              "source_url": null,
              "source_name": "Gaceta Magica",
              "video_status": "ready",
              "published_at": "2026-09-21T08:00:00Z",
              "position": 1
            },
            {
              "id": 2,
              "title": "Bowtruckle",
              "content": "Contenido",
              "summary": null,
              "category": "science",
              "language": "es",
              "image_url": "https://cdn.example.com/i2.jpg",
              "video_url": null,
              "source_url": null,
              "source_name": null,
              "video_status": "processing",
              "published_at": "2026-09-21T09:00:00Z",
              "position": 2
            }
          ]
        }
        """.trimIndent()
    }
}
