package com.rumbo.ar.ui.pantallas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rumbo.ar.ui.componentes.BarraSuperior
import com.rumbo.ar.ui.componentes.TarjetaOpcion
import com.rumbo.ar.ui.tema.Espacio
import com.rumbo.nucleo.modelo.Categoria
import com.rumbo.nucleo.modelo.Plaza
import kotlin.math.roundToInt

/** Tercer paso: el establecimiento concreto. */
@Composable
fun PantallaEstablecimientos(
    plaza: Plaza,
    categoria: Categoria,
    distanciaEstimada: (String) -> Double?,
    alElegirEstablecimiento: (String) -> Unit,
    alVolver: () -> Unit,
) {
    Scaffold(
        topBar = { BarraSuperior(titulo = categoria.plural, alVolver = alVolver) },
    ) { relleno ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno),
            contentPadding = PaddingValues(Espacio.m),
            verticalArrangement = Arrangement.spacedBy(Espacio.s),
        ) {
            items(plaza.establecimientosDe(categoria), key = { it.id }) { establecimiento ->
                val piso = plaza.piso(establecimiento.pisoId)?.nombre ?: establecimiento.pisoId
                val metros = distanciaEstimada(establecimiento.id)
                val detalle = buildString {
                    append(piso)
                    if (metros != null) append(" · ~${metros.roundToInt()} m")
                }
                TarjetaOpcion(
                    titulo = establecimiento.nombre,
                    detalle = detalle,
                    inicial = establecimiento.nombre,
                    alPulsar = { alElegirEstablecimiento(establecimiento.id) },
                )
            }
        }
    }
}
