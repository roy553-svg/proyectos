package com.elprofeta.app.ui.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elprofeta.app.ProfetaApplication
import com.elprofeta.app.data.repository.FeedResult
import com.elprofeta.app.data.repository.NewsRepository
import com.elprofeta.app.domain.model.Article
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Estado de la portada.
 *
 * La Fase 1 del backend identifica al lector con `user_id`, sin autenticacion:
 * la app lo mantiene aqui y lo envia en cada peticion.
 */
class NewsViewModel(
    private val repository: NewsRepository,
    initialUserId: Int = DEFAULT_USER_ID,
) : ViewModel() {

    private val _uiState = MutableStateFlow<NewsUiState>(NewsUiState.Loading)
    val uiState: StateFlow<NewsUiState> = _uiState.asStateFlow()

    var userId: Int = initialUserId
        private set

    init {
        refresh()
    }

    /** Vuelve a pedir la edicion al backend. */
    fun refresh() {
        viewModelScope.launch {
            _uiState.value = NewsUiState.Loading
            _uiState.value = when (val result = repository.getFeed(userId)) {
                is FeedResult.Success -> NewsUiState.Ready(result.feed, result.fromCache)
                is FeedResult.NotAvailable -> NewsUiState.Empty(result.reason.toUiReason())
                is FeedResult.Failure -> result.cached
                    ?.let { NewsUiState.Ready(it, fromCache = true) }
                    ?: NewsUiState.Error(fallback = null)
            }
        }
    }

    /** Cambia de lector (pantalla de ajustes / pruebas) y recarga. */
    fun changeUser(newUserId: Int) {
        if (newUserId == userId || newUserId <= 0) return
        userId = newUserId
        refresh()
    }

    /** Noticia por id, para la pantalla de detalle. */
    fun articleById(articleId: Int): Article? =
        (_uiState.value as? NewsUiState.Ready)?.feed?.articleById(articleId)

    companion object {
        const val DEFAULT_USER_ID = 1

        /** Factoria que toma el repositorio del contenedor de la aplicacion. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as ProfetaApplication
                NewsViewModel(application.container.newsRepository)
            }
        }
    }
}

private fun FeedResult.Reason.toUiReason(): EmptyReason = when (this) {
    FeedResult.Reason.UNKNOWN_USER -> EmptyReason.UNKNOWN_USER
    FeedResult.Reason.NO_PUBLISHED_EDITION -> EmptyReason.NO_PUBLISHED_EDITION
}
