package com.elprofeta.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.elprofeta.app.R
import com.elprofeta.app.domain.model.Article

/**
 * Medio de una noticia.
 *
 * Si la animacion esta lista se reproduce en bucle y sin sonido (como los
 * retratos de 'El Profeta'); si no, se muestra la imagen estatica. La app
 * nunca genera contenido: solo reproduce lo que el backend preparo.
 */
@Composable
fun ArticleMedia(
    article: Article,
    modifier: Modifier = Modifier,
    playVideo: Boolean = true,
) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.BottomStart,
    ) {
        val videoUrl = article.videoUrl
        if (videoUrl != null && playVideo) {
            LoopingVideo(url = videoUrl, modifier = Modifier.fillMaxSize())
        } else {
            AsyncImage(
                model = article.imageUrl,
                contentDescription = stringResource(R.string.article_image),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (article.videoStatus.isInProgress) {
            Text(
                text = stringResource(R.string.video_processing),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .padding(8.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * Reproduce un video en bucle, silenciado y respetando el ciclo de vida.
 *
 * ExoPlayer se libera siempre en `onDispose`: un player vivo fuera de
 * pantalla mantiene abiertos el codec y la conexion.
 */
@Composable
private fun LoopingVideo(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> player.pause()
                Lifecycle.Event.ON_RESUME -> player.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }

    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                this.player = player
                useController = false
            }
        },
        modifier = modifier,
    )
}
