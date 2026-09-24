package com.elprofeta.app.ui.news

import com.elprofeta.app.domain.model.NewsFeed

/** Estados posibles de la portada. */
sealed interface NewsUiState {

    data object Loading : NewsUiState

    /** Hay edicion. [fromCache] marca que se muestra la copia descargada. */
    data class Ready(val feed: NewsFeed, val fromCache: Boolean) : NewsUiState

    /** El backend respondio 404 (usuario desconocido o sin edicion). */
    data class Empty(val reason: EmptyReason) : NewsUiState

    /** Fallo de red; si hay copia local se muestra igualmente. */
    data class Error(val fallback: NewsFeed?) : NewsUiState
}

enum class EmptyReason { UNKNOWN_USER, NO_PUBLISHED_EDITION }
