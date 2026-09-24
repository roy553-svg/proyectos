package com.rumbo.ar.ui.pantallas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.rumbo.ar.ui.componentes.Aviso
import com.rumbo.ar.ui.componentes.Cabecera
import com.rumbo.ar.ui.componentes.TarjetaOpcion
import com.rumbo.ar.ui.tema.Espacio
import com.rumbo.nucleo.modelo.Plaza

/** Pantalla inicial: NO abre la camara, solo lista las plazas disponibles. */
@Composable
fun PantallaPlazas(
    plazas: List<Plaza>,
    alElegirPlaza: (String) -> Unit,
    alAbrirAdmin: () -> Unit,
) {
    Scaffold { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno),
        ) {
            Cabecera(
                titulo = "Encuentra tu lugar",
                subtitulo = "Selecciona una plaza",
            )
            LazyColumn(
                contentPadding = PaddingValues(Espacio.m),
                verticalArrangement = Arrangement.spacedBy(Espacio.s),
                modifier = Modifier.weight(1f),
            ) {
                items(plazas, key = { it.id }) { plaza ->
                    TarjetaOpcion(
                        titulo = plaza.nombre,
                        detalle = detalleDe(plaza),
                        inicial = plaza.nombre,
                        alPulsar = { alElegirPlaza(plaza.id) },
                    )
                }
                if (plazas.isEmpty()) {
                    item {
                        Aviso("No hay plazas guardadas. Crea una desde el panel de administración.")
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Espacio.m),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TextButton(onClick = alAbrirAdmin) { Text("Panel de administración") }
                Text(
                    text = "Elige un lugar y síguelo en el mundo real",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun detalleDe(plaza: Plaza): String {
    val pisos = plaza.pisos.size
    val locales = plaza.establecimientos.count { it.activo }
    val demo = if (plaza.ficticia) " · datos de demostración" else ""
    return "$pisos ${if (pisos == 1) "piso" else "pisos"} · $locales lugares$demo"
}
