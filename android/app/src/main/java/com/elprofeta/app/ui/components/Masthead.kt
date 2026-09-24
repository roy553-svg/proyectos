package com.elprofeta.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.elprofeta.app.R
import com.elprofeta.app.domain.model.Edition
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_FORMAT = DateTimeFormatter.ofPattern("d 'de' MMMM", Locale.forLanguageTag("es"))

/** Cabecera del periodico con el rango de fechas de la edicion. */
@Composable
fun Masthead(edition: Edition?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Text(
            text = stringResource(R.string.masthead),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = edition?.let {
                "${it.weekStart.format(DATE_FORMAT)} — ${it.weekEnd.format(DATE_FORMAT)}"
            } ?: stringResource(R.string.masthead_subtitle),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}
