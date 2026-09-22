package com.rumbo.nucleo

import com.rumbo.nucleo.grafo.AEstrella
import com.rumbo.nucleo.grafo.GrafoNavegacion
import com.rumbo.nucleo.modelo.Arista
import com.rumbo.nucleo.modelo.Categoria
import com.rumbo.nucleo.modelo.Establecimiento
import com.rumbo.nucleo.modelo.Nodo
import com.rumbo.nucleo.modelo.Piso
import com.rumbo.nucleo.modelo.Plaza
import com.rumbo.nucleo.modelo.Punto2D
import com.rumbo.nucleo.modelo.TipoArista
import com.rumbo.nucleo.modelo.TipoNodo
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class AEstrellaTest {

    /**
     * Grafo del enunciado:
     *   ENTRADA - A - B - C - D - DESTINO
     * y un atajo mas largo B - E - D para comprobar que se elige el optimo.
     */
    private fun plazaLineal(): Plaza {
        val piso = Piso("p1", 1, "Piso 1", 100.0, 100.0)
        val nodos = listOf(
            Nodo("ENTRADA", "p1", Punto2D(0.0, 0.0), TipoNodo.ENTRADA),
            Nodo("A", "p1", Punto2D(0.0, 10.0)),
            Nodo("B", "p1", Punto2D(0.0, 20.0)),
            Nodo("C", "p1", Punto2D(10.0, 20.0)),
            Nodo("D", "p1", Punto2D(10.0, 30.0)),
            Nodo("E", "p1", Punto2D(40.0, 20.0)),
            Nodo("DESTINO", "p1", Punto2D(10.0, 40.0), TipoNodo.ACCESO),
        )
        val aristas = listOf(
            Arista("ENTRADA", "A"),
            Arista("A", "B"),
            Arista("B", "C"),
            Arista("C", "D"),
            Arista("D", "DESTINO"),
            Arista("B", "E"),
            Arista("E", "D"),
        )
        return Plaza(
            id = "test", nombre = "Test", pisos = listOf(piso), nodos = nodos, aristas = aristas,
            establecimientos = listOf(
                Establecimiento("tienda", "Tienda", Categoria.TIENDA, "p1", Punto2D(10.0, 45.0), "DESTINO"),
            ),
        )
    }

    @Test
    fun `encuentra la ruta mas corta`() {
        val grafo = GrafoNavegacion.construir(plazaLineal())
        val ruta = AEstrella.buscar(grafo, "ENTRADA", "DESTINO")
        assertNotNull(ruta)
        assertEquals(
            listOf("ENTRADA", "A", "B", "C", "D", "DESTINO"),
            ruta.puntos.map { it.nodoId },
        )
        // 10 + 10 + 10 + 10 + 10 = 50 m
        assertEquals(50.0, ruta.costeMetros, 1e-6)
        assertEquals(50.0, ruta.longitudHorizontalMetros, 1e-6)
    }

    @Test
    fun `A estrella y Dijkstra coinciden en el coste`() {
        val grafo = GrafoNavegacion.construir(plazaLineal())
        val conHeuristica = AEstrella.buscar(grafo, "ENTRADA", "DESTINO", usarHeuristica = true)
        val sinHeuristica = AEstrella.buscar(grafo, "ENTRADA", "DESTINO", usarHeuristica = false)
        assertNotNull(conHeuristica)
        assertNotNull(sinHeuristica)
        assertEquals(sinHeuristica.costeMetros, conHeuristica.costeMetros, 1e-6)
    }

    @Test
    fun `ruta de un solo punto cuando origen es el destino`() {
        val grafo = GrafoNavegacion.construir(plazaLineal())
        val ruta = AEstrella.buscar(grafo, "B", "B")
        assertNotNull(ruta)
        assertEquals(1, ruta.puntos.size)
        assertTrue(ruta.esVacia)
    }

    @Test
    fun `sin ruta si el destino esta aislado`() {
        val base = plazaLineal()
        val plaza = base.copy(
            nodos = base.nodos + Nodo("AISLADO", "p1", Punto2D(90.0, 90.0)),
        )
        val grafo = GrafoNavegacion.construir(plaza)
        assertNull(AEstrella.buscar(grafo, "ENTRADA", "AISLADO"))
        assertNull(AEstrella.buscar(grafo, "ENTRADA", "NO_EXISTE"))
    }

    @Test
    fun `prefiere el ascensor frente a la escalera cuando es mas barato`() {
        val pisos = listOf(
            Piso("p1", 1, "Piso 1", 50.0, 50.0),
            Piso("p2", 2, "Piso 2", 50.0, 50.0),
        )
        val nodos = listOf(
            Nodo("P1_INICIO", "p1", Punto2D(0.0, 0.0), TipoNodo.ENTRADA),
            Nodo("P1_ASC", "p1", Punto2D(10.0, 0.0), TipoNodo.ASCENSOR),
            Nodo("P1_ESC", "p1", Punto2D(10.0, 0.0), TipoNodo.ESCALERA),
            Nodo("P2_ASC", "p2", Punto2D(10.0, 0.0), TipoNodo.ASCENSOR),
            Nodo("P2_ESC", "p2", Punto2D(10.0, 0.0), TipoNodo.ESCALERA),
            Nodo("P2_DESTINO", "p2", Punto2D(20.0, 0.0), TipoNodo.ACCESO),
        )
        val aristas = listOf(
            Arista("P1_INICIO", "P1_ASC"),
            Arista("P1_INICIO", "P1_ESC"),
            Arista("P1_ASC", "P2_ASC", TipoArista.ASCENSOR, costeExtraMetros = 18.0),
            Arista("P1_ESC", "P2_ESC", TipoArista.ESCALERA, costeExtraMetros = 40.0),
            Arista("P2_ASC", "P2_DESTINO"),
            Arista("P2_ESC", "P2_DESTINO"),
        )
        val grafo = GrafoNavegacion.construir(
            Plaza(id = "v", nombre = "Vertical", pisos = pisos, nodos = nodos, aristas = aristas),
        )
        val ruta = AEstrella.buscar(grafo, "P1_INICIO", "P2_DESTINO")
        assertNotNull(ruta)
        assertEquals(listOf("P1_INICIO", "P1_ASC", "P2_ASC", "P2_DESTINO"), ruta.puntos.map { it.nodoId })
        assertEquals(TipoArista.ASCENSOR, ruta.puntos[2].tipoLlegada)
        assertTrue(ruta.esCambioDePiso(2))
        // La longitud caminada no incluye el salto vertical: 10 + 10 = 20 m
        assertEquals(20.0, ruta.longitudHorizontalMetros, 1e-6)
    }
}
