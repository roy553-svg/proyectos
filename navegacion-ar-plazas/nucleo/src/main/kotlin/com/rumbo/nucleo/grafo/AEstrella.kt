package com.rumbo.nucleo.grafo

import com.rumbo.nucleo.modelo.Geometria
import com.rumbo.nucleo.modelo.Nodo
import com.rumbo.nucleo.ruta.PuntoRuta
import com.rumbo.nucleo.ruta.Ruta
import java.util.PriorityQueue
import kotlin.math.abs

/**
 * Busqueda de ruta A* sobre el grafo de navegacion.
 *
 * Heuristica: distancia euclidea en metros dentro del piso, mas una penalizacion
 * por cada cambio de nivel. La penalizacion ([PESO_HEURISTICO_NIVEL]) es menor
 * que el coste real de un enlace vertical
 * ([GrafoNavegacion.COSTE_VERTICAL_POR_DEFECTO_METROS]), asi que la heuristica
 * nunca sobreestima y A* sigue devolviendo la ruta optima.
 *
 * Si se prefiere Dijkstra basta con pasar `usarHeuristica = false`.
 */
object AEstrella {

    const val PESO_HEURISTICO_NIVEL = 12.0

    fun buscar(
        grafo: GrafoNavegacion,
        origenId: String,
        destinoId: String,
        destinoEstablecimientoId: String? = null,
        usarHeuristica: Boolean = true,
    ): Ruta? {
        val origen = grafo.nodo(origenId) ?: return null
        val destino = grafo.nodo(destinoId) ?: return null

        if (origenId == destinoId) {
            return Ruta(
                puntos = listOf(aPuntoRuta(origen, null)),
                destinoEstablecimientoId = destinoEstablecimientoId,
                costeMetros = 0.0,
            )
        }

        val costeReal = HashMap<String, Double>().apply { put(origenId, 0.0) }
        val anterior = HashMap<String, AristaResuelta>()
        val cerrados = HashSet<String>()
        val abiertos = PriorityQueue<Candidato>(compareBy { it.prioridad })
        abiertos.add(Candidato(origenId, heuristica(grafo, origen, destino, usarHeuristica)))

        while (abiertos.isNotEmpty()) {
            val actual = abiertos.poll()
            if (!cerrados.add(actual.nodoId)) continue
            if (actual.nodoId == destinoId) {
                return reconstruir(grafo, origenId, destinoId, anterior, costeReal, destinoEstablecimientoId)
            }
            val gActual = costeReal[actual.nodoId] ?: continue
            for (arista in grafo.vecinos(actual.nodoId)) {
                val siguiente = arista.hasta.id
                if (siguiente in cerrados) continue
                val gNuevo = gActual + arista.pesoMetros
                if (gNuevo < (costeReal[siguiente] ?: Double.MAX_VALUE)) {
                    costeReal[siguiente] = gNuevo
                    anterior[siguiente] = arista
                    val h = heuristica(grafo, arista.hasta, destino, usarHeuristica)
                    abiertos.add(Candidato(siguiente, gNuevo + h))
                }
            }
        }
        return null
    }

    private fun heuristica(
        grafo: GrafoNavegacion,
        desde: Nodo,
        hasta: Nodo,
        usarHeuristica: Boolean,
    ): Double {
        if (!usarHeuristica) return 0.0
        val plano = Geometria.distancia(desde.posicion, hasta.posicion)
        val niveles = abs(grafo.nivelDe(desde.pisoId) - grafo.nivelDe(hasta.pisoId))
        return plano + niveles * PESO_HEURISTICO_NIVEL
    }

    private fun reconstruir(
        grafo: GrafoNavegacion,
        origenId: String,
        destinoId: String,
        anterior: Map<String, AristaResuelta>,
        costeReal: Map<String, Double>,
        destinoEstablecimientoId: String?,
    ): Ruta {
        val puntos = ArrayDeque<PuntoRuta>()
        var actual = destinoId
        while (actual != origenId) {
            val arista = anterior[actual] ?: break
            puntos.addFirst(aPuntoRuta(arista.hasta, arista.tipo))
            actual = arista.desde.id
        }
        grafo.nodo(origenId)?.let { puntos.addFirst(aPuntoRuta(it, null)) }
        return Ruta(
            puntos = puntos.toList(),
            destinoEstablecimientoId = destinoEstablecimientoId,
            costeMetros = costeReal[destinoId] ?: 0.0,
        )
    }

    private fun aPuntoRuta(nodo: Nodo, tipoLlegada: com.rumbo.nucleo.modelo.TipoArista?) = PuntoRuta(
        nodoId = nodo.id,
        pisoId = nodo.pisoId,
        posicion = nodo.posicion,
        tipoLlegada = tipoLlegada,
        nombre = nodo.nombre,
    )

    private data class Candidato(val nodoId: String, val prioridad: Double)
}
