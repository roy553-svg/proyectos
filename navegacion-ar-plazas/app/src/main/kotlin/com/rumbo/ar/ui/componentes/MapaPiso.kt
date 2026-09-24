package com.rumbo.ar.ui.componentes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.rumbo.ar.ui.tema.ColoresRumbo
import com.rumbo.nucleo.modelo.Arista
import com.rumbo.nucleo.modelo.Establecimiento
import com.rumbo.nucleo.modelo.Nodo
import com.rumbo.nucleo.modelo.Piso
import com.rumbo.nucleo.modelo.Punto2D
import com.rumbo.nucleo.modelo.PuntoQr
import com.rumbo.nucleo.modelo.Rectangulo
import com.rumbo.nucleo.posicionamiento.PoseMapa
import kotlin.math.min

/** Conversion entre metros del plano y pixeles del lienzo (manteniendo la escala). */
class TransformadorMapa(
    private val anchoLienzo: Float,
    private val altoLienzo: Float,
    private val anchoMetros: Float,
    private val altoMetros: Float,
) {
    val escala: Float = min(anchoLienzo / anchoMetros, altoLienzo / altoMetros)
    private val desplazamientoX = (anchoLienzo - anchoMetros * escala) / 2f
    private val desplazamientoY = (altoLienzo - altoMetros * escala) / 2f

    /** El eje Y del plano va hacia arriba y el de la pantalla hacia abajo: se invierte. */
    fun aPantalla(punto: Punto2D) = Offset(
        x = desplazamientoX + punto.x.toFloat() * escala,
        y = desplazamientoY + (altoMetros - punto.y.toFloat()) * escala,
    )

    fun aMapa(posicion: Offset) = Punto2D(
        x = ((posicion.x - desplazamientoX) / escala).toDouble(),
        y = (altoMetros - (posicion.y - desplazamientoY) / escala).toDouble(),
    )

    fun metrosAPixeles(metros: Double): Float = metros.toFloat() * escala
}

/**
 * Mapa 2D de un piso. Se usa en tres sitios: el mapa de respaldo, la seleccion
 * manual de la posicion inicial y el editor del panel de administracion.
 *
 * Dibuja solo geometria del modelo de datos, sin imagenes: asi funciona aunque
 * la plaza no tenga plano escaneado.
 */
@Composable
fun MapaPiso(
    piso: Piso,
    establecimientos: List<Establecimiento>,
    modifier: Modifier = Modifier,
    nodos: List<Nodo> = emptyList(),
    aristas: List<Arista> = emptyList(),
    puntosQr: List<PuntoQr> = emptyList(),
    tramosRuta: List<List<Punto2D>> = emptyList(),
    pose: PoseMapa? = null,
    destino: Establecimiento? = null,
    mostrarNodos: Boolean = false,
    mostrarEtiquetas: Boolean = true,
    puntoAuxiliar: Punto2D? = null,
    seleccionado: String? = null,
    alTocar: ((Punto2D) -> Unit)? = null,
) {
    val medidor = rememberTextMeasurer()
    val colorPasillo = MaterialTheme.colorScheme.surfaceVariant
    val colorLocal = MaterialTheme.colorScheme.surface
    val colorTexto = MaterialTheme.colorScheme.onSurfaceVariant
    val colorBorde = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)

    Canvas(
        modifier = modifier.then(
            if (alTocar == null) {
                Modifier
            } else {
                Modifier.pointerInput(piso.id) {
                    detectTapGestures { posicion ->
                        val transformador = TransformadorMapa(
                            size.width.toFloat(),
                            size.height.toFloat(),
                            piso.anchoMetros.toFloat(),
                            piso.altoMetros.toFloat(),
                        )
                        alTocar(transformador.aMapa(posicion))
                    }
                }
            },
        ),
    ) {
        val t = TransformadorMapa(
            size.width,
            size.height,
            piso.anchoMetros.toFloat(),
            piso.altoMetros.toFloat(),
        )

        // Contorno del piso.
        val esquina = t.aPantalla(Punto2D(0.0, piso.altoMetros))
        drawRect(
            color = colorLocal.copy(alpha = 0.35f),
            topLeft = esquina,
            size = Size(t.metrosAPixeles(piso.anchoMetros), t.metrosAPixeles(piso.altoMetros)),
        )

        piso.pasillos.forEach { dibujarRectangulo(it, t, colorPasillo) }
        piso.locales.forEach { local ->
            dibujarRectangulo(local, t, colorLocal, colorBorde)
            if (mostrarEtiquetas) dibujarEtiquetaLocal(local, t, medidor, colorTexto)
        }

        // Grafo de navegacion (solo en el editor).
        if (mostrarNodos) {
            val porId = nodos.associateBy { it.id }
            aristas.forEach { arista ->
                val a = porId[arista.desdeId] ?: return@forEach
                val b = porId[arista.hastaId] ?: return@forEach
                if (a.pisoId != piso.id || b.pisoId != piso.id) return@forEach
                drawLine(
                    color = ColoresRumbo.TurquesaClaro.copy(alpha = 0.45f),
                    start = t.aPantalla(a.posicion),
                    end = t.aPantalla(b.posicion),
                    strokeWidth = 2f,
                )
            }
            nodos.filter { it.pisoId == piso.id }.forEach { nodo ->
                val resaltado = nodo.id == seleccionado
                drawCircle(
                    color = if (resaltado) ColoresRumbo.Ambar else ColoresRumbo.TurquesaClaro,
                    radius = if (resaltado) 9f else 5f,
                    center = t.aPantalla(nodo.posicion),
                )
            }
        }

        puntosQr.filter { it.pisoId == piso.id }.forEach { qr ->
            val centro = t.aPantalla(qr.posicion)
            drawRect(
                color = ColoresRumbo.Ambar,
                topLeft = Offset(centro.x - 7f, centro.y - 7f),
                size = Size(14f, 14f),
                style = Stroke(width = 3f),
            )
        }

        // Ruta calculada (un trazo por tramo del piso).
        tramosRuta.filter { it.size >= 2 }.forEach { tramo ->
            val camino = Path().apply {
                val inicio = t.aPantalla(tramo.first())
                moveTo(inicio.x, inicio.y)
                tramo.drop(1).forEach { punto ->
                    val p = t.aPantalla(punto)
                    lineTo(p.x, p.y)
                }
            }
            drawPath(
                path = camino,
                color = ColoresRumbo.Turquesa,
                style = Stroke(width = 8f, pathEffect = PathEffect.cornerPathEffect(12f)),
            )
            tramo.forEach { punto ->
                drawCircle(ColoresRumbo.TurquesaClaro, radius = 4f, center = t.aPantalla(punto))
            }
        }

        // Destino.
        destino?.takeIf { it.pisoId == piso.id }?.let { establecimiento ->
            val centro = t.aPantalla(establecimiento.posicion)
            drawCircle(ColoresRumbo.Ambar.copy(alpha = 0.25f), radius = 22f, center = centro)
            drawCircle(ColoresRumbo.Ambar, radius = 9f, center = centro)
            if (mostrarEtiquetas) {
                dibujarTexto(medidor, establecimiento.nombre, centro + Offset(14f, -8f), ColoresRumbo.Ambar, 13f)
            }
        }

        // Establecimientos (punto discreto, la etiqueta ya va en el local).
        establecimientos.filter { it.pisoId == piso.id && it.id != destino?.id }.forEach {
            drawCircle(colorTexto.copy(alpha = 0.5f), radius = 3f, center = t.aPantalla(it.posicion))
        }

        // Punto auxiliar (segundo toque al marcar la direccion a la que se mira).
        puntoAuxiliar?.let {
            drawCircle(
                color = ColoresRumbo.TurquesaClaro,
                radius = 6f,
                center = t.aPantalla(it),
                style = Stroke(width = 2f),
            )
        }

        // Usuario: circulo mas cono de orientacion.
        pose?.takeIf { it.pisoId == piso.id }?.let { actual ->
            val centro = t.aPantalla(actual.posicion)
            val radioPrecision = t.metrosAPixeles(actual.precisionMetros.coerceAtMost(25.0))
            drawCircle(ColoresRumbo.Turquesa.copy(alpha = 0.12f), radius = radioPrecision, center = centro)
            dibujarConoOrientacion(centro, actual.rumboGrados.toFloat(), t.metrosAPixeles(6.0))
            drawCircle(Color.White, radius = 7f, center = centro)
            drawCircle(ColoresRumbo.Turquesa, radius = 5f, center = centro)
        }
    }
}

private fun DrawScope.dibujarRectangulo(
    rectangulo: Rectangulo,
    t: TransformadorMapa,
    color: Color,
    borde: Color? = null,
) {
    val arriba = t.aPantalla(Punto2D(rectangulo.x, rectangulo.y + rectangulo.alto))
    val tamano = Size(t.metrosAPixeles(rectangulo.ancho), t.metrosAPixeles(rectangulo.alto))
    drawRect(color = color, topLeft = arriba, size = tamano)
    borde?.let { drawRect(color = it, topLeft = arriba, size = tamano, style = Stroke(width = 1.5f)) }
}

private fun DrawScope.dibujarEtiquetaLocal(
    local: Rectangulo,
    t: TransformadorMapa,
    medidor: TextMeasurer,
    color: Color,
) {
    val etiqueta = local.etiqueta ?: return
    val anchoPx = t.metrosAPixeles(local.ancho)
    if (anchoPx < 46f) return
    val centro = t.aPantalla(local.centro)
    val medida = medidor.measure(etiqueta, TextStyle(fontSize = 10.sp, color = color))
    if (medida.size.width > anchoPx) return
    drawText(
        textLayoutResult = medida,
        topLeft = Offset(centro.x - medida.size.width / 2f, centro.y - medida.size.height / 2f),
    )
}

private fun DrawScope.dibujarTexto(
    medidor: TextMeasurer,
    texto: String,
    posicion: Offset,
    color: Color,
    tamanoSp: Float,
) {
    val medida = medidor.measure(texto, TextStyle(fontSize = tamanoSp.sp, color = color))
    drawText(textLayoutResult = medida, topLeft = posicion)
}

/** Cono que indica hacia donde mira el usuario (rumbo horario desde +Y). */
private fun DrawScope.dibujarConoOrientacion(centro: Offset, rumboGrados: Float, largo: Float) {
    val radianes = Math.toRadians(rumboGrados.toDouble())
    val apertura = Math.toRadians(26.0)
    fun extremo(angulo: Double) = Offset(
        x = centro.x + (Math.sin(angulo) * largo).toFloat(),
        // En pantalla, +Y del mapa apunta hacia arriba: se invierte el coseno.
        y = centro.y - (Math.cos(angulo) * largo).toFloat(),
    )
    val camino = Path().apply {
        moveTo(centro.x, centro.y)
        val izquierda = extremo(radianes - apertura)
        val derecha = extremo(radianes + apertura)
        lineTo(izquierda.x, izquierda.y)
        lineTo(derecha.x, derecha.y)
        close()
    }
    drawPath(camino, ColoresRumbo.Turquesa.copy(alpha = 0.45f))
}
