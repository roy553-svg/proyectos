package com.elprofeta.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.elprofeta.app.R

/** Pantalla de carga con el tono del periodico. */
@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Text(
            text = stringResource(R.string.loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

/** Mensaje de error o de edicion vacia, con accion de reintento. */
@Composable
fun MessageView(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            OutlinedButton(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

/** Aviso fijo de que se esta mostrando la copia descargada. */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.offline_notice),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onPrimary,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .padding(vertical = 6.dp, horizontal = 16.dp),
    )
}
