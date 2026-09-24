package com.rumbo.nucleo.ruta

import com.rumbo.nucleo.modelo.Geometria
import com.rumbo.nucleo.modelo.Punto2D
import com.rumbo.nucleo.modelo.TipoArista

/**
 * Un punto de la ruta. [tipoLlegada] indica como se llega desde el punto
 * anterior: por pasillo, por ascensor o por escalera.
 */
data class PuntoRuta(
    val nodoId: String,
    val pisoId: String,
    val posicion: Punto2D,
    val tipoLlegada: TipoArista? = null,
    val nombre: String? = null,
)

/** Ruta calculada: una secuencia de puntos de navegacion hasta el destino. */
data class Ruta(
    val puntos: List<PuntoRuta>,
    val destinoEstablecimientoId: String? = null,
    val costeMetros: Double = 0.0,
) {
    val esVacia: Boolean get() = puntos.size < 2

    val destino: PuntoRuta? get() = puntos.lastOrNull()

    /** Longitud caminada (sin contar los saltos verticales entre pisos). */
    val longitudHorizontalMetros: Double
        get() = puntos.zipWithNext()
            .filter { (a, b) -> a.pisoId == b.pisoId }
            .sumOf { (a, b) -> Geometria.distancia(a.posicion, b.posicion) }

    /**
     * Distancia restante desde [posicion] (en el piso del punto [indiceObjetivo])
     * hasta el final de la ruta.
     */
    fun distanciaRestanteMetros(indiceObjetivo: Int, posicion: Punto2D): Double {
        if (puntos.isEmpty()) return 0.0
        val i = indiceObjetivo.coerceIn(0, puntos.lastIndex)
        var total = Geometria.distancia(posicion, puntos[i].posicion)
        for (k in i until puntos.lastIndex) {
            val a = puntos[k]
            val b = puntos[k + 1]
            total += if (a.pisoId == b.pisoId) Geometria.distancia(a.posicion, b.posicion) else 0.0
        }
        return total
    }

    /**
     * Trozos continuos de la ruta que caen en [pisoId]. Se devuelven por separado
     * para que el mapa 2D no dibuje una linea recta entre dos tramos unidos por
     * un ascensor o una escalera.
     */
    fun tramosDelPiso(pisoId: String): List<List<Punto2D>> {
        val tramos = mutableListOf<List<Punto2D>>()
        var actual = mutableListOf<Punto2D>()
        for (punto in puntos) {
            if (punto.pisoId == pisoId) {
                actual.add(punto.posicion)
            } else if (actual.isNotEmpty()) {
                tramos.add(actual)
                actual = mutableListOf()
            }
        }
        if (actual.isNotEmpty()) tramos.add(actual)
        return tramos
    }

    /** Indica si entre [indice] - 1 e [indice] hay un cambio de piso. */
    fun esCambioDePiso(indice: Int): Boolean {
        if (indice <= 0 || indice > puntos.lastIndex) return false
        return puntos[indice - 1].pisoId != puntos[indice].pisoId
    }
}
