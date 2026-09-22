package com.rumbo.nucleo.posicionamiento

import com.rumbo.nucleo.modelo.Geometria
import com.rumbo.nucleo.modelo.Punto2D
import kotlin.math.cos
import kotlin.math.sin

/**
 * Suavizado de la pose para que la flecha no tiemble.
 *
 * - Posicion: filtro exponencial simple.
 * - Rumbo: media circular (se promedian seno y coseno), para que el paso por
 *   360->0 grados no provoque un latigazo.
 *
 * Un salto grande de posicion (por ejemplo un nuevo QR) se aplica de golpe en
 * lugar de suavizarse, porque es informacion mas fiable que la acumulada.
 */
class FiltroPose(
    private val alfaPosicion: Double = 0.25,
    private val alfaRumbo: Double = 0.2,
    private val saltoMetrosSinSuavizar: Double = 4.0,
) {
    private var posicion: Punto2D? = null
    private var senoRumbo = 0.0
    private var cosenoRumbo = 0.0
    private var pisoId: String? = null

    fun reiniciar() {
        posicion = null
        senoRumbo = 0.0
        cosenoRumbo = 0.0
        pisoId = null
    }

    fun filtrar(pose: PoseMapa): PoseMapa {
        val anterior = posicion
        val cambioDePiso = pisoId != null && pisoId != pose.pisoId
        val nuevaPosicion = when {
            anterior == null || cambioDePiso -> pose.posicion
            Geometria.distancia(anterior, pose.posicion) > saltoMetrosSinSuavizar -> pose.posicion
            else -> Punto2D(
                x = anterior.x + alfaPosicion * (pose.posicion.x - anterior.x),
                y = anterior.y + alfaPosicion * (pose.posicion.y - anterior.y),
            )
        }
        val radianes = Math.toRadians(pose.rumboGrados)
        if (anterior == null || cambioDePiso) {
            senoRumbo = sin(radianes)
            cosenoRumbo = cos(radianes)
        } else {
            senoRumbo += alfaRumbo * (sin(radianes) - senoRumbo)
            cosenoRumbo += alfaRumbo * (cos(radianes) - cosenoRumbo)
        }
        posicion = nuevaPosicion
        pisoId = pose.pisoId
        val rumboFiltrado = Geometria.normalizar360(
            Math.toDegrees(kotlin.math.atan2(senoRumbo, cosenoRumbo)),
        )
        return pose.copy(posicion = nuevaPosicion, rumboGrados = rumboFiltrado)
    }
}
