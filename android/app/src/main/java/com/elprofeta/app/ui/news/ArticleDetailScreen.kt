package com.elprofeta.app.ui.news

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elprofeta.app.R
import com.elprofeta.app.domain.model.Article
import com.elprofeta.app.ui.components.ArticleMedia
import com.elprofeta.app.ui.components.MessageView

/** Noticia completa, con su animacion si el backend ya la genero. */
@Composable
fun ArticleDetailScreen(
    article: Article?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (article == null) {
            MessageView(
                message = stringResource(R.string.error_generic),
                onRetry = onBack,
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.back))
            }

            Text(
                text = article.category.uppercase(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = article.title,
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 32.sp),
                color = MaterialTheme.colorScheme.onBackground,
            )

            if (article.imageUrl != null || article.hasVideo) {
                ArticleMedia(
                    article = article,
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                )
            }

            article.summary?.let { summary ->
                Text(
                    text = summary,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = article.content,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            article.sourceName?.let { source ->
                Text(
                    text = "${stringResource(R.string.source_prefix)} $source",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
