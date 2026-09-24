package com.rumbo.ar.ui.pantallas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.rumbo.ar.ui.componentes.Aviso
import com.rumbo.ar.ui.componentes.BarraSuperior
import com.rumbo.ar.ui.componentes.MapaPiso
import com.rumbo.ar.ui.tema.Espacio
import com.rumbo.nucleo.modelo.Establecimiento
import com.rumbo.nucleo.modelo.Plaza
import com.rumbo.nucleo.posicionamiento.PoseMapa
import com.rumbo.nucleo.ruta.Ruta
import kotlin.math.roundToInt

/**
 * Mapa 2D de respaldo. Siempre disponible: si ARCore falta, si la camara no
 * consigue orientarse o si el usuario simplemente prefiere el plano.
 */
@Composable
fun PantallaMapa2D(
    plaza: Plaza,
    destino: Establecimiento?,
    ruta: Ruta?,
    pose: PoseMapa?,
    instruccion: String?,
    puedeSeguirEnAr: Boolean,
    alSeguirEnAr: () -> Unit,
    alVolver: () -> Unit,
) {
    val pisoInicial = pose?.pisoId ?: destino?.pisoId ?: plaza.pisos.firstOrNull()?.id
    var pisoSeleccionado by remember { mutableStateOf(pisoInicial) }
    val piso = plaza.pisos.firstOrNull { it.id == pisoSeleccionado } ?: plaza.pisos.firstOrNull()

    Scaffold(
        topBar = { BarraSuperior(titulo = "Mapa · ${plaza.nombre}", alVolver = alVolver) },
    ) { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno)
                .padding(horizontal = Espacio.m),
            verticalArrangement = Arrangement.spacedBy(Espacio.s),
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Espacio.s),
            ) {
                plaza.pisos.sortedBy { it.nivel }.forEach { candidato ->
                    FilterChip(
                        selected = candidato.id == piso?.id,
                        onClick = { pisoSeleccionado = candidato.id },
                        label = { Text(candidato.nombre) },
                    )
                }
            }

            if (piso == null) {
                Aviso("Esta plaza todavía no tiene pisos.")
                return@Column
            }

            MapaPiso(
                piso = piso,
                establecimientos = plaza.establecimientos.filter { it.activo },
                puntosQr = plaza.puntosQr,
                tramosRuta = ruta?.tramosDelPiso(piso.id).orEmpty(),
                pose = pose,
                destino = destino,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            destino?.let {
                val distancia = ruta?.let { r ->
                    pose?.let { p -> r.distanciaRestanteMetros(0, p.posicion) }
                        ?: r.longitudHorizontalMetros
                }
                Text(
                    text = buildString {
                        append("Destino: ${it.nombre}")
                        if (distancia != null) append(" · ${distancia.roundToInt()} m")
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            instruccion?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (pose == null) {
                Aviso("Marca tu posición o escanea un QR para ver la ruta desde donde estás.")
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Espacio.m),
                horizontalArrangement = Arrangement.spacedBy(Espacio.s),
            ) {
                OutlinedButton(onClick = alVolver, modifier = Modifier.weight(1f)) { Text("Volver") }
                if (puedeSeguirEnAr) {
                    Button(onClick = alSeguirEnAr, modifier = Modifier.weight(1f)) {
                        Text("Seguir en AR")
                    }
                }
            }
        }
    }
}
