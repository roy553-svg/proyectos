package com.rumbo.ar.ui.componentes

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rumbo.ar.ui.tema.ColoresRumbo

/**
 * Flecha de navegacion AR: el elemento visual principal de la pantalla.
 *
 * - `anguloGrados` es el angulo RELATIVO al rumbo actual del usuario
 *   (0 = recto, positivo = a la derecha, negativo = a la izquierda), tal y como
 *   lo entrega el motor de navegacion.
 * - El giro se anima por el camino corto, asi que pasar de 179 a -179 grados es
 *   un movimiento de 2 grados y no una vuelta completa.
 * - Diseno propio: cuerpo triangular redondeado con degradado, halo suave y un
 *   latido lento. No imita la interfaz de ningun juego existente.
 */
@Composable
fun FlechaAr(
    anguloGrados: Float,
    modifier: Modifier = Modifier,
    tamano: Dp = 240.dp,
    color: Color = ColoresRumbo.Turquesa,
    colorClaro: Color = ColoresRumbo.TurquesaClaro,
    atenuada: Boolean = false,
) {
    // Acumulador continuo: evita el latigazo al cruzar +/-180 grados.
    var acumulado by remember { mutableFloatStateOf(anguloGrados) }
    LaunchedEffect(anguloGrados) {
        var delta = (anguloGrados - acumulado) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        acumulado += delta
    }
    val rotacion by animateFloatAsState(
        targetValue = acumulado,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "rotacionFlecha",
    )

    val latido = rememberInfiniteTransition(label = "latidoFlecha")
    val escala by latido.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "escalaFlecha",
    )

    val alfa = if (atenuada) 0.35f else 1f

    Box(modifier = modifier.size(tamano)) {
        Canvas(modifier = Modifier.size(tamano)) {
            val centro = Offset(size.width / 2f, size.height / 2f)
            // Halo: da sensacion de profundidad sin tapar la imagen de camara.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.22f * alfa), Color.Transparent),
                    center = centro,
                    radius = size.minDimension / 2f,
                ),
                radius = size.minDimension / 2f,
                center = centro,
            )
            rotate(degrees = rotacion, pivot = centro) {
                dibujarCuerpoFlecha(centro, size.minDimension * 0.5f * escala, color, colorClaro, alfa)
            }
        }
    }
}

private fun DrawScope.dibujarCuerpoFlecha(
    centro: Offset,
    radio: Float,
    color: Color,
    colorClaro: Color,
    alfa: Float,
) {
    val camino = Path().apply {
        moveTo(centro.x, centro.y - radio)                       // punta
        lineTo(centro.x + radio * 0.62f, centro.y + radio * 0.38f)
        lineTo(centro.x + radio * 0.22f, centro.y + radio * 0.22f)
        lineTo(centro.x + radio * 0.26f, centro.y + radio * 0.92f)
        lineTo(centro.x - radio * 0.26f, centro.y + radio * 0.92f)
        lineTo(centro.x - radio * 0.22f, centro.y + radio * 0.22f)
        lineTo(centro.x - radio * 0.62f, centro.y + radio * 0.38f)
        close()
    }
    drawPath(
        path = camino,
        brush = Brush.verticalGradient(
            colors = listOf(colorClaro.copy(alpha = 0.98f * alfa), color.copy(alpha = 0.90f * alfa)),
            startY = centro.y - radio,
            endY = centro.y + radio,
        ),
    )
    drawPath(
        path = camino,
        color = Color.White.copy(alpha = 0.55f * alfa),
        style = Stroke(width = radio * 0.045f),
    )
}
