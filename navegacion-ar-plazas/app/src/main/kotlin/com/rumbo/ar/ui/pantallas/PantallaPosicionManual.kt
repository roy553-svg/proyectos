package com.rumbo.ar.ui.pantallas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import com.rumbo.nucleo.modelo.Plaza
import com.rumbo.nucleo.modelo.Punto2D

/**
 * Posicion inicial marcada a mano, en dos toques:
 *   1. donde estas,
 *   2. hacia donde miras.
 *
 * El segundo toque es lo que hace que la flecha AR pueda orientarse sin depender
 * de la brujula (que dentro de un centro comercial se desvia por el metal).
 */
@Composable
fun PantallaPosicionManual(
    plaza: Plaza,
    pisoSugerido: String?,
    alConfirmar: (String, Punto2D, Punto2D) -> Unit,
    alVolver: () -> Unit,
) {
    var pisoSeleccionado by remember {
        mutableStateOf(pisoSugerido ?: plaza.pisos.firstOrNull()?.id)
    }
    var donde by remember { mutableStateOf<Punto2D?>(null) }
    var mirando by remember { mutableStateOf<Punto2D?>(null) }
    val piso = plaza.pisos.firstOrNull { it.id == pisoSeleccionado } ?: plaza.pisos.firstOrNull()

    Scaffold(
        topBar = { BarraSuperior(titulo = "¿Dónde estás?", alVolver = alVolver) },
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
                        onClick = {
                            pisoSeleccionado = candidato.id
                            donde = null
                            mirando = null
                        },
                        label = { Text(candidato.nombre) },
                    )
                }
            }

            Text(
                text = when {
                    donde == null -> "1. Toca el punto del plano donde estás ahora"
                    mirando == null -> "2. Toca un punto hacia el que estés mirando"
                    else -> "Listo: revisa el punto y confirma"
                },
                style = MaterialTheme.typography.titleMedium,
            )

            if (piso == null) {
                Aviso("Esta plaza todavía no tiene pisos.")
                return@Column
            }

            MapaPiso(
                piso = piso,
                establecimientos = plaza.establecimientos.filter { it.activo },
                puntosQr = plaza.puntosQr,
                pose = donde?.let { punto ->
                    com.rumbo.nucleo.posicionamiento.PoseMapa(
                        pisoId = piso.id,
                        posicion = punto,
                        rumboGrados = mirando?.let {
                            com.rumbo.nucleo.modelo.Geometria.rumboEntre(punto, it)
                        } ?: 0.0,
                        precisionMetros = 3.0,
                        fuente = com.rumbo.nucleo.posicionamiento.FuentePosicion.MANUAL,
                    )
                },
                puntoAuxiliar = mirando,
                alTocar = { punto ->
                    if (donde == null) donde = punto else mirando = punto
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            Aviso(
                "Marcar el punto a mano da una precisión de unos 3 metros. " +
                    "Si la plaza tiene códigos QR, escanearlos es más exacto.",
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Espacio.m),
                horizontalArrangement = Arrangement.spacedBy(Espacio.s),
            ) {
                OutlinedButton(
                    onClick = {
                        donde = null
                        mirando = null
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Reiniciar") }
                Button(
                    onClick = {
                        val posicion = donde
                        val direccion = mirando
                        if (posicion != null && direccion != null) {
                            alConfirmar(piso.id, posicion, direccion)
                        }
                    },
                    enabled = donde != null && mirando != null,
                    modifier = Modifier.weight(1f),
                ) { Text("Confirmar") }
            }
        }
    }
}
