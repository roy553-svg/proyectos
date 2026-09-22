package com.rumbo.nucleo.grafo

import com.rumbo.nucleo.modelo.Arista
import com.rumbo.nucleo.modelo.Geometria
import com.rumbo.nucleo.modelo.Nodo
import com.rumbo.nucleo.modelo.Plaza
import com.rumbo.nucleo.modelo.Punto2D
import com.rumbo.nucleo.modelo.ResultadoValidacion
import com.rumbo.nucleo.modelo.TipoArista
import kotlin.math.abs

/** Arista ya resuelta con su peso en metros equivalentes. */
data class AristaResuelta(
    val desde: Nodo,
    val hasta: Nodo,
    val tipo: TipoArista,
    val pesoMetros: Double,
)

/**
 * Grafo de navegacion de una plaza. Es una estructura inmutable derivada de
 * [Plaza]; el calculo de rutas (A*) trabaja siempre sobre esta clase, nunca
 * sobre la camara ni sobre los sensores.
 */
class GrafoNavegacion private constructor(
    val plaza: Plaza,
    private val nodosPorId: Map<String, Nodo>,
    private val adyacencia: Map<String, List<AristaResuelta>>,
) {

    val nodos: Collection<Nodo> get() = nodosPorId.values

    fun nodo(id: String): Nodo? = nodosPorId[id]

    fun vecinos(id: String): List<AristaResuelta> = adyacencia[id].orEmpty()

    /** Nodo mas cercano a [punto] dentro de [pisoId]; null si el piso no tiene nodos. */
    fun nodoMasCercano(pisoId: String, punto: Punto2D): Nodo? =
        nodosPorId.values
            .filter { it.pisoId == pisoId }
            .minByOrNull { Geometria.distancia(it.posicion, punto) }

    fun nivelDe(pisoId: String): Int = plaza.piso(pisoId)?.nivel ?: 0

    companion object {
        /** Coste por defecto de un enlace vertical si la arista no declara uno. */
        const val COSTE_VERTICAL_POR_DEFECTO_METROS = 18.0

        fun construir(plaza: Plaza): GrafoNavegacion {
            val nodosPorId = plaza.nodos.associateBy { it.id }
            val adyacencia = HashMap<String, MutableList<AristaResuelta>>()

            fun agregar(a: Nodo, b: Nodo, arista: Arista) {
                val peso = pesoDe(plaza, a, b, arista)
                adyacencia.getOrPut(a.id) { mutableListOf() }
                    .add(AristaResuelta(a, b, arista.tipo, peso))
            }

            for (arista in plaza.aristas) {
                val a = nodosPorId[arista.desdeId] ?: continue
                val b = nodosPorId[arista.hastaId] ?: continue
                agregar(a, b, arista)
                if (arista.bidireccional) agregar(b, a, arista)
            }
            return GrafoNavegacion(plaza, nodosPorId, adyacencia)
        }

        private fun pesoDe(plaza: Plaza, a: Nodo, b: Nodo, arista: Arista): Double {
            val extra = arista.costeExtraMetros
            return if (a.pisoId == b.pisoId) {
                Geometria.distancia(a.posicion, b.posicion) + extra
            } else {
                val niveles = abs(
                    (plaza.piso(a.pisoId)?.nivel ?: 0) - (plaza.piso(b.pisoId)?.nivel ?: 0),
                )
                val base = if (extra > 0.0) extra else COSTE_VERTICAL_POR_DEFECTO_METROS
                base * niveles.coerceAtLeast(1)
            }
        }

        /**
         * Comprueba la coherencia de una plaza: referencias validas, nodos
         * aislados y establecimientos inalcanzables desde las entradas.
         */
        fun validar(plaza: Plaza): ResultadoValidacion {
            val errores = mutableListOf<String>()
            val avisos = mutableListOf<String>()
            val idsNodo = plaza.nodos.map { it.id }.toSet()
            val idsPiso = plaza.pisos.map { it.id }.toSet()

            if (plaza.pisos.isEmpty()) errores += "La plaza no tiene pisos."
            if (plaza.nodos.isEmpty()) errores += "La plaza no tiene nodos de navegacion."

            plaza.nodos.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach {
                errores += "Nodo duplicado: $it"
            }
            plaza.nodos.filter { it.pisoId !in idsPiso }.forEach {
                errores += "El nodo ${it.id} apunta a un piso inexistente (${it.pisoId})."
            }
            plaza.aristas.forEach { a ->
                if (a.desdeId !in idsNodo) errores += "Arista con nodo origen inexistente: ${a.desdeId}"
                if (a.hastaId !in idsNodo) errores += "Arista con nodo destino inexistente: ${a.hastaId}"
            }
            plaza.establecimientos.forEach { e ->
                if (e.pisoId !in idsPiso) errores += "${e.nombre} apunta a un piso inexistente (${e.pisoId})."
                if (e.nodoDestinoId !in idsNodo) {
                    errores += "${e.nombre} apunta a un nodo destino inexistente (${e.nodoDestinoId})."
                }
            }
            plaza.puntosQr.forEach { qr ->
                if (qr.nodoId !in idsNodo) errores += "El QR ${qr.codigo} apunta a un nodo inexistente."
            }
            if (errores.isNotEmpty()) return ResultadoValidacion(errores, avisos)

            val grafo = construir(plaza)
            grafo.nodos.filter { grafo.vecinos(it.id).isEmpty() }.forEach {
                avisos += "El nodo ${it.id} no esta conectado con ningun otro."
            }

            val entradas = plaza.nodos.filter { it.tipo == com.rumbo.nucleo.modelo.TipoNodo.ENTRADA }
            if (entradas.isEmpty()) {
                avisos += "La plaza no tiene nodos de tipo ENTRADA."
            } else {
                val alcanzables = recorrer(grafo, entradas.first().id)
                plaza.establecimientos.filter { it.activo && it.nodoDestinoId !in alcanzables }.forEach {
                    errores += "${it.nombre} no es alcanzable desde la entrada ${entradas.first().id}."
                }
            }
            return ResultadoValidacion(errores, avisos)
        }

        private fun recorrer(grafo: GrafoNavegacion, inicioId: String): Set<String> {
            val vistos = mutableSetOf(inicioId)
            val pendientes = ArrayDeque(listOf(inicioId))
            while (pendientes.isNotEmpty()) {
                val actual = pendientes.removeFirst()
                for (arista in grafo.vecinos(actual)) {
                    if (vistos.add(arista.hasta.id)) pendientes.addLast(arista.hasta.id)
                }
            }
            return vistos
        }
    }
}
