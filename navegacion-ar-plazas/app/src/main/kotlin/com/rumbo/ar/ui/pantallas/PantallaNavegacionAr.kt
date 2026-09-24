package com.rumbo.ar.ui.pantallas

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rumbo.ar.EstadoUi
import com.rumbo.ar.ModoNavegacion
import com.rumbo.ar.NavegacionViewModel
import com.rumbo.ar.ar.ProyeccionAr
import com.rumbo.ar.ar.SesionArCore
import com.rumbo.ar.ar.VistaCamaraAr
import com.rumbo.ar.ui.componentes.Aviso
import com.rumbo.ar.ui.componentes.ConPermisoCamara
import com.rumbo.ar.ui.componentes.FlechaAr
import com.rumbo.ar.ui.componentes.VistaCamaraSimple
import com.rumbo.ar.ui.tema.ColoresRumbo
import com.rumbo.ar.ui.tema.Espacio
import com.rumbo.nucleo.navegacion.Fase
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Pantalla de navegacion AR: camara real de fondo y una unica flecha grande
 * encima, mas la informacion minima (destino, distancia y la instruccion).
 *
 * La camara NO decide la ruta: solo muestra el mundo y da la orientacion. La
 * ruta viene del motor de navegacion (mapa + grafo + A*).
 */
@Composable
fun PantallaNavegacionAr(
    vistaModelo: NavegacionViewModel,
    estado: EstadoUi,
    alEscanearQr: () -> Unit,
    alMarcarEnMapa: () -> Unit,
    alVerMapa: () -> Unit,
    alSalir: () -> Unit,
) {
    val plaza = estado.plaza
    val destino = estado.destino
    if (plaza == null || destino == null) {
        alSalir()
        return
    }

    // Posicion en pantalla del marcador anclado al siguiente punto de la ruta.
    val marcadorEnPantalla = remember { MutableStateFlow<Offset?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        when (estado.modo) {
            ModoNavegacion.AR -> {
                ConPermisoCamara(alRechazar = alVerMapa) {
                    CapaCamaraAr(vistaModelo, marcadorEnPantalla)
                }
            }

            ModoNavegacion.SENSORES -> {
                ConPermisoCamara(alRechazar = alVerMapa) {
                    VistaCamaraSimple(modifier = Modifier.fillMaxSize())
                }
            }

            ModoNavegacion.SOLO_MAPA -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                )
            }
        }

        // Degradados para que el texto se lea sobre cualquier imagen de camara.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.55f),
                        0.28f to Color.Transparent,
                        0.68f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.75f),
                    ),
                ),
        )

        MarcadorPunto(marcadorEnPantalla)

        val navegacion = estado.navegacion
        if (navegacion.mostrarFlecha) {
            FlechaAr(
                anguloGrados = navegacion.anguloRelativoGrados.toFloat(),
                modifier = Modifier.align(Alignment.Center),
                atenuada = estado.modo == ModoNavegacion.AR && !estado.seguimientoAr.rastreando,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(Espacio.m),
            verticalArrangement = Arrangement.spacedBy(Espacio.s),
        ) {
            TarjetaDestino(
                nombre = destino.nombre,
                piso = plaza.piso(destino.pisoId)?.nombre.orEmpty(),
                distanciaRestante = navegacion.distanciaRestanteMetros,
                fuente = estado.pose?.fuente?.etiqueta,
                precisionMetros = estado.pose?.precisionMetros,
            )
            estado.seguimientoAr.aviso?.let { Aviso(it) }
            estado.mensaje?.let { Aviso(it, color = ColoresRumbo.Ambar) }
            if (navegacion.fase == Fase.DESVIADO) {
                Aviso("Te has desviado", color = ColoresRumbo.Rojo)
            } else {
                navegacion.aviso?.let { Aviso(it) }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(Espacio.m),
            verticalArrangement = Arrangement.spacedBy(Espacio.s),
        ) {
            when (navegacion.fase) {
                Fase.SIN_POSICION -> TarjetaPosicionInicial(alEscanearQr, alMarcarEnMapa)

                Fase.CAMBIO_DE_PISO -> TarjetaCambioDePiso(
                    instruccion = navegacion.instruccion,
                    alConfirmar = { vistaModelo.confirmarCambioDePiso() },
                )

                Fase.LLEGADO -> TarjetaLlegada(destino.nombre, alSalir)

                else -> {
                    Text(
                        text = navegacion.instruccion,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (navegacion.distanciaAlObjetivoMetros > 0) {
                        Text(
                            text = "Siguiente punto a ${navegacion.distanciaAlObjetivoMetros.roundToInt()} m",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.75f),
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
                OutlinedButton(onClick = alSalir, modifier = Modifier.weight(1f)) { Text("Salir") }
                Button(onClick = alVerMapa, modifier = Modifier.weight(1f)) { Text("Mapa") }
            }
        }
    }
}

/** Capa de camara con ARCore: publica cada frame al ViewModel. */
@Composable
private fun CapaCamaraAr(
    vistaModelo: NavegacionViewModel,
    marcadorEnPantalla: MutableStateFlow<Offset?>,
) {
    val contexto = LocalContext.current
    val actividad = contexto as? Activity
    if (actividad == null) {
        Box(modifier = Modifier.fillMaxSize())
        return
    }
    val sesionAr = remember { SesionArCore(actividad) }
    // Cada sesion AR nueva estrena origen de coordenadas: hay que realinear.
    LaunchedEffect(Unit) { vistaModelo.prepararSesionAr() }

    VistaCamaraAr(
        sesionAr = sesionAr,
        modifier = Modifier.fillMaxSize(),
        alError = { mensaje -> vistaModelo.avisarErrorAr(mensaje) },
        alFrame = { frame, ancho, alto ->
            vistaModelo.procesarFrame(frame)
            // El marcador del siguiente punto se ancla en el mundo AR: se
            // proyecta su posicion del mapa a la pantalla con las matrices de
            // vista y proyeccion del frame.
            val objetivo = vistaModelo.estado.value.navegacion.posicionObjetivo
            val alineacion = vistaModelo.alineacionAr
            marcadorEnPantalla.value = if (objetivo != null && alineacion != null) {
                val suelo = vistaModelo.proveedorArCore.alturaSueloAr
                val alturaRelativa = if (suelo != null) {
                    (suelo + ALTURA_MARCADOR) - alineacion.alturaArOrigen
                } else {
                    -DESCENSO_MARCADOR
                }
                val puntoAr = alineacion.aPuntoAr(objetivo, alturaRelativa)
                ProyeccionAr.aPantalla(frame.camera, puntoAr, ancho, alto)
                    ?.let { Offset(it[0], it[1]) }
            } else {
                null
            }
        },
    )
}

/** Disco luminoso sobre el siguiente punto real de la ruta. */
@Composable
private fun MarcadorPunto(marcadorEnPantalla: MutableStateFlow<Offset?>) {
    val posicion by marcadorEnPantalla.collectAsState()
    val densidad = LocalDensity.current
    val actual = posicion ?: return
    val x = with(densidad) { actual.x.toDp() }
    val y = with(densidad) { actual.y.toDp() }
    Box(
        modifier = Modifier
            .offset(x = x - 26.dp, y = y - 26.dp)
            .size(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(ColoresRumbo.TurquesaClaro.copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ColoresRumbo.TurquesaClaro),
        )
    }
}

@Composable
private fun TarjetaDestino(
    nombre: String,
    piso: String,
    distanciaRestante: Double,
    fuente: String?,
    precisionMetros: Double?,
) {
    Surface(
        color = Color.Black.copy(alpha = 0.45f),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(Espacio.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(nombre, style = MaterialTheme.typography.titleLarge, color = Color.White)
                val detalle = buildString {
                    append(piso)
                    if (fuente != null) {
                        append(" · ")
                        append(fuente)
                        if (precisionMetros != null && precisionMetros < 100) {
                            append(" ±${precisionMetros.roundToInt()} m")
                        }
                    }
                }
                Text(
                    text = detalle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
            Text(
                text = "${distanciaRestante.roundToInt()} m",
                style = MaterialTheme.typography.headlineMedium,
                color = ColoresRumbo.TurquesaClaro,
            )
        }
    }
}

@Composable
private fun TarjetaPosicionInicial(alEscanearQr: () -> Unit, alMarcarEnMapa: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Espacio.m),
            verticalArrangement = Arrangement.spacedBy(Espacio.s),
        ) {
            Text("¿Dónde estás?", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "ARCore sabe cómo te mueves, pero no en qué parte de la plaza estás. " +
                    "Escanea un QR (más preciso) o marca tu posición en el mapa.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = alEscanearQr, modifier = Modifier.fillMaxWidth()) {
                Text("Escanear QR de la plaza")
            }
            OutlinedButton(onClick = alMarcarEnMapa, modifier = Modifier.fillMaxWidth()) {
                Text("Marcar mi posición en el mapa")
            }
        }
    }
}

@Composable
private fun TarjetaCambioDePiso(instruccion: String, alConfirmar: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Espacio.m),
            verticalArrangement = Arrangement.spacedBy(Espacio.s),
        ) {
            Text(instruccion, style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Al llegar, confirma el piso: así se vuelve a fijar tu posición y la " +
                    "flecha sigue apuntando bien.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = alConfirmar, modifier = Modifier.fillMaxWidth()) {
                Text("Ya estoy en ese piso")
            }
        }
    }
}

@Composable
private fun TarjetaLlegada(nombre: String, alSalir: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Espacio.l),
            verticalArrangement = Arrangement.spacedBy(Espacio.s),
        ) {
            Text("Has llegado", style = MaterialTheme.typography.displaySmall)
            Text(nombre, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(Espacio.xs))
            Button(onClick = alSalir, modifier = Modifier.fillMaxWidth()) { Text("Finalizar") }
        }
    }
}

/** Altura a la que se dibuja el marcador sobre el suelo detectado (metros). */
private const val ALTURA_MARCADOR = 0.9

/** Si no se detecta el suelo, se baja el marcador respecto a la altura de la camara. */
private const val DESCENSO_MARCADOR = 0.6
