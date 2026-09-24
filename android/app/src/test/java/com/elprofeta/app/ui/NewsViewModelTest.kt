package com.elprofeta.app.ui

import com.elprofeta.app.data.repository.FeedResult
import com.elprofeta.app.data.repository.NewsRepository
import com.elprofeta.app.domain.model.Article
import com.elprofeta.app.domain.model.Edition
import com.elprofeta.app.domain.model.NewsFeed
import com.elprofeta.app.domain.model.VideoStatus
import com.elprofeta.app.ui.news.EmptyReason
import com.elprofeta.app.ui.news.NewsUiState
import com.elprofeta.app.ui.news.NewsViewModel
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NewsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `muestra la edicion cuando el repositorio responde`() = runTest(dispatcher) {
        val viewModel = NewsViewModel(FakeRepository(FeedResult.Success(feed(), fromCache = false)))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is NewsUiState.Ready)
        assertEquals(1, (state as NewsUiState.Ready).feed.articles.size)
        assertFalse(state.fromCache)
    }

    @Test
    fun `avisa cuando la edicion procede de la cache`() = runTest(dispatcher) {
        val viewModel = NewsViewModel(FakeRepository(FeedResult.Success(feed(), fromCache = true)))
        advanceUntilIdle()

        assertTrue((viewModel.uiState.value as NewsUiState.Ready).fromCache)
    }

    @Test
    fun `un usuario desconocido produce el estado vacio correspondiente`() = runTest(dispatcher) {
        val viewModel = NewsViewModel(
            FakeRepository(FeedResult.NotAvailable(FeedResult.Reason.UNKNOWN_USER)),
        )
        advanceUntilIdle()

        assertEquals(
            NewsUiState.Empty(EmptyReason.UNKNOWN_USER),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `sin edicion publicada se informa al lector`() = runTest(dispatcher) {
        val viewModel = NewsViewModel(
            FakeRepository(FeedResult.NotAvailable(FeedResult.Reason.NO_PUBLISHED_EDITION)),
        )
        advanceUntilIdle()

        assertEquals(
            NewsUiState.Empty(EmptyReason.NO_PUBLISHED_EDITION),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `un fallo de red con copia local muestra la copia`() = runTest(dispatcher) {
        val viewModel = NewsViewModel(
            FakeRepository(FeedResult.Failure(IOException("sin red"), cached = feed())),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is NewsUiState.Ready)
        assertTrue((state as NewsUiState.Ready).fromCache)
    }

    @Test
    fun `un fallo de red sin copia local muestra el error`() = runTest(dispatcher) {
        val viewModel = NewsViewModel(
            FakeRepository(FeedResult.Failure(IOException("sin red"), cached = null)),
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is NewsUiState.Error)
    }

    @Test
    fun `cambiar de lector vuelve a pedir la edicion`() = runTest(dispatcher) {
        val repository = FakeRepository(FeedResult.Success(feed(), fromCache = false))
        val viewModel = NewsViewModel(repository)
        advanceUntilIdle()

        viewModel.changeUser(2)
        advanceUntilIdle()

        assertEquals(2, viewModel.userId)
        assertEquals(listOf(1, 2), repository.requestedUserIds)
    }

    @Test
    fun `cambiar al mismo lector no genera otra peticion`() = runTest(dispatcher) {
        val repository = FakeRepository(FeedResult.Success(feed(), fromCache = false))
        val viewModel = NewsViewModel(repository)
        advanceUntilIdle()

        viewModel.changeUser(NewsViewModel.DEFAULT_USER_ID)
        advanceUntilIdle()

        assertEquals(listOf(1), repository.requestedUserIds)
    }

    @Test
    fun `articleById encuentra la noticia cargada`() = runTest(dispatcher) {
        val viewModel = NewsViewModel(FakeRepository(FeedResult.Success(feed(), fromCache = false)))
        advanceUntilIdle()

        assertEquals("Titular", viewModel.articleById(10)?.title)
        assertEquals(null, viewModel.articleById(999))
    }

    private class FakeRepository(private val result: FeedResult) : NewsRepository {
        val requestedUserIds = mutableListOf<Int>()

        override suspend fun getFeed(userId: Int): FeedResult {
            requestedUserIds += userId
            return result
        }
    }

    private fun feed() = NewsFeed(
        edition = Edition(
            id = 1,
            title = "El Profeta",
            weekStart = LocalDate.of(2026, 9, 21),
            weekEnd = LocalDate.of(2026, 9, 27),
            publishedAt = Instant.parse("2026-09-21T08:00:00Z"),
        ),
        articles = listOf(
            Article(
                id = 10,
                title = "Titular",
                content = "Contenido",
                summary = "Resumen",
                category = "magic",
                language = "es",
                imageUrl = null,
                videoUrl = null,
                sourceUrl = null,
                sourceName = null,
                videoStatus = VideoStatus.NOT_REQUESTED,
                publishedAt = Instant.parse("2026-09-21T08:00:00Z"),
                position = 1,
            ),
        ),
    )
}
