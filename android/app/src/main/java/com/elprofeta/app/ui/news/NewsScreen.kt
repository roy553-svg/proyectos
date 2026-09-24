package com.elprofeta.app.ui.news

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.elprofeta.app.R
import com.elprofeta.app.domain.model.Article
import com.elprofeta.app.domain.model.NewsFeed
import com.elprofeta.app.ui.components.ArticleMedia
import com.elprofeta.app.ui.components.LoadingView
import com.elprofeta.app.ui.components.Masthead
import com.elprofeta.app.ui.components.MessageView
import com.elprofeta.app.ui.components.OfflineBanner

/** Portada: cabecera del periodico y lista de noticias de la semana. */
@Composable
fun NewsScreen(
    state: NewsUiState,
    onArticleClick: (Int) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                is NewsUiState.Loading -> {
                    Masthead(edition = null)
                    LoadingView()
                }

                is NewsUiState.Ready -> {
                    if (state.fromCache) OfflineBanner()
                    Masthead(edition = state.feed.edition)
                    FeedList(feed = state.feed, onArticleClick = onArticleClick)
                }

                is NewsUiState.Empty -> {
                    Masthead(edition = null)
                    MessageView(
                        message = stringResource(
                            when (state.reason) {
                                EmptyReason.UNKNOWN_USER -> R.string.error_unknown_user
                                EmptyReason.NO_PUBLISHED_EDITION -> R.string.error_no_edition
                            },
                        ),
                        onRetry = onRetry,
                    )
                }

                is NewsUiState.Error -> {
                    Masthead(edition = state.fallback?.edition)
                    MessageView(
                        message = stringResource(R.string.error_generic),
                        onRetry = onRetry,
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedList(
    feed: NewsFeed,
    onArticleClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(items = feed.articles, key = { it.id }) { article ->
            ArticleCard(article = article, onClick = { onArticleClick(article.id) })
        }
    }
}

/** Entrada de la portada: medio animado (o imagen), titular y entradilla. */
@Composable
fun ArticleCard(
    article: Article,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = article.category.uppercase(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Nº ${article.position}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (article.imageUrl != null || article.hasVideo) {
            ArticleMedia(
                article = article,
                modifier = Modifier.fillMaxWidth().height(200.dp),
            )
        }

        Text(
            text = article.title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = article.teaser,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.read_more),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}
