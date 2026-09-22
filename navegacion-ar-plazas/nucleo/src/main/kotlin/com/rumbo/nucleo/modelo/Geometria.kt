package com.rumbo.nucleo.modelo

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlinx.serialization.Serializable

/**
 * CONVENCIONES DE COORDENADAS Y ANGULOS (validas en todo el proyecto)
 *
 * Marco del mapa (uno por piso):
 *   - Unidades: METROS.
 *   - Eje X: hacia la derecha del plano.
 *   - Eje Y: hacia arriba del plano.
 *   - Origen: esquina inferior izquierda del plano del piso.
 *
 * Rumbo ("bearing") de un vector (dx, dy):
 *   - Grados en [0, 360), medidos en sentido HORARIO desde el eje +Y.
 *   - 0 = hacia +Y, 90 = hacia +X, 180 = hacia -Y, 270 = hacia -X.
 *   - Se calcula con atan2(dx, dy), NO con atan2(dy, dx).
 *
 * Relacion con el norte real:
 *   - Cada piso declara `rumboNorteGrados`: el azimut de brujula (0 = norte)
 *     al que apunta el eje +Y del plano.
 *   - azimutBrujula = normalizar360(rumboMapa + rumboNorteGrados)
 *   - rumboMapa     = normalizar360(azimutBrujula - rumboNorteGrados)
 */
@Serializable
data class Punto2D(val x: Double, val y: Double) {
    operator fun plus(otro: Punto2D) = Punto2D(x + otro.x, y + otro.y)
    operator fun minus(otro: Punto2D) = Punto2D(x - otro.x, y - otro.y)
    operator fun times(escala: Double) = Punto2D(x * escala, y * escala)
}

/** Rectangulo alineado a los ejes, en metros. Se usa para dibujar el plano 2D. */
@Serializable
data class Rectangulo(
    val x: Double,
    val y: Double,
    val ancho: Double,
    val alto: Double,
    val etiqueta: String? = null,
) {
    val centro: Punto2D get() = Punto2D(x + ancho / 2.0, y + alto / 2.0)
}

object Geometria {

    fun normalizar360(grados: Double): Double {
        val g = grados % 360.0
        return if (g < 0) g + 360.0 else g
    }

    /** Devuelve el angulo equivalente en (-180, 180]. Util para "gira a la izquierda/derecha". */
    fun normalizar180(grados: Double): Double {
        val g = normalizar360(grados)
        return if (g > 180.0) g - 360.0 else g
    }

    /** Diferencia angular mas corta entre dos rumbos, en (-180, 180]. */
    fun diferenciaAngular(desdeGrados: Double, hastaGrados: Double): Double =
        normalizar180(hastaGrados - desdeGrados)

    fun distancia(a: Punto2D, b: Punto2D): Double = hypot(b.x - a.x, b.y - a.y)

    /** Rumbo (horario desde +Y) del vector que va de [desde] a [hasta]. */
    fun rumboEntre(desde: Punto2D, hasta: Punto2D): Double {
        val dx = hasta.x - desde.x
        val dy = hasta.y - desde.y
        if (abs(dx) < 1e-9 && abs(dy) < 1e-9) return 0.0
        return normalizar360(Math.toDegrees(atan2(dx, dy)))
    }

    /** Rumbo de un vector expresado como (derecha, adelante). */
    fun rumboDeVector(derecha: Double, adelante: Double): Double {
        if (abs(derecha) < 1e-9 && abs(adelante) < 1e-9) return 0.0
        return normalizar360(Math.toDegrees(atan2(derecha, adelante)))
    }

    /**
     * Rota el vector [p] en sentido HORARIO [grados], es decir incrementa su rumbo
     * en [grados]. Es la operacion que convierte un desplazamiento medido en el
     * marco de ARCore al marco del mapa.
     */
    fun rotarHorario(p: Punto2D, grados: Double): Punto2D {
        val r = Math.toRadians(grados)
        val c = cos(r)
        val s = sin(r)
        return Punto2D(
            x = p.x * c + p.y * s,
            y = -p.x * s + p.y * c,
        )
    }

    /** Punto del segmento [a]-[b] mas cercano a [p]. */
    fun proyectarEnSegmento(p: Punto2D, a: Punto2D, b: Punto2D): Punto2D {
        val vx = b.x - a.x
        val vy = b.y - a.y
        val largo2 = vx * vx + vy * vy
        if (largo2 < 1e-12) return a
        var t = ((p.x - a.x) * vx + (p.y - a.y) * vy) / largo2
        t = t.coerceIn(0.0, 1.0)
        return Punto2D(a.x + vx * t, a.y + vy * t)
    }

    fun distanciaASegmento(p: Punto2D, a: Punto2D, b: Punto2D): Double =
        distancia(p, proyectarEnSegmento(p, a, b))

    fun azimutDesdeRumboMapa(rumboMapaGrados: Double, rumboNorteGrados: Double): Double =
        normalizar360(rumboMapaGrados + rumboNorteGrados)

    fun rumboMapaDesdeAzimut(azimutGrados: Double, rumboNorteGrados: Double): Double =
        normalizar360(azimutGrados - rumboNorteGrados)
}
