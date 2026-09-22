package com.rumbo.nucleo

import com.rumbo.nucleo.datos.AlmacenMemoria
import com.rumbo.nucleo.datos.RepositorioPlazasJson
import com.rumbo.nucleo.datos.SemillaPlazas
import com.rumbo.nucleo.grafo.AEstrella
import com.rumbo.nucleo.grafo.GrafoNavegacion
import com.rumbo.nucleo.modelo.Categoria
import com.rumbo.nucleo.modelo.Plaza
import com.rumbo.nucleo.modelo.TipoNodo
import com.rumbo.nucleo.qr.PayloadQr
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Test

/** Comprueba que los datos de demostracion son completos y navegables. */
class PlazaAuroraTest {

    private val plaza: Plaza = SemillaPlazas.cargar("/plazas/plaza_aurora.json")
        ?: error("No se encontro el recurso /plazas/plaza_aurora.json")

    @Test
    fun `la plaza de demostracion es valida`() {
        val resultado = GrafoNavegacion.validar(plaza)
        assertTrue(resultado.valida, "errores: ${resultado.errores}")
        assertTrue(resultado.avisos.isEmpty(), "avisos: ${resultado.avisos}")
        assertTrue(plaza.ficticia, "los datos de demostracion deben marcarse como ficticios")
    }

    @Test
    fun `tiene el contenido pedido para el primer prototipo`() {
        assertEquals(2, plaza.pisos.size)
        val comerciales = plaza.establecimientos.filter {
            it.categoria in setOf(Categoria.TIENDA, Categoria.RESTAURANTE, Categoria.FARMACIA, Categoria.CAJERO)
        }
        assertEquals(20, comerciales.size)
        assertEquals(2, plaza.establecimientos.count { it.categoria == Categoria.BANO })
        assertEquals(2, plaza.establecimientos.count { it.categoria == Categoria.ASCENSOR })
        assertEquals(2, plaza.establecimientos.count { it.categoria == Categoria.ESCALERA })
        assertEquals(2, plaza.establecimientos.count { it.categoria == Categoria.ENTRADA })
        assertTrue(plaza.puntosQr.size >= 4, "se esperaban varios puntos QR")
        // Cada piso tiene geometria para dibujar el mapa 2D.
        plaza.pisos.forEach { piso ->
            assertTrue(piso.pasillos.isNotEmpty(), "el piso ${piso.id} no tiene pasillos")
            assertTrue(piso.locales.isNotEmpty(), "el piso ${piso.id} no tiene locales")
            assertTrue(piso.anchoMetros > 0 && piso.altoMetros > 0)
        }
    }

    @Test
    fun `hay ruta desde cada entrada hasta cada establecimiento activo`() {
        val grafo = GrafoNavegacion.construir(plaza)
        val entradas = plaza.nodos.filter { it.tipo == TipoNodo.ENTRADA }
        assertTrue(entradas.isNotEmpty())
        for (entrada in entradas) {
            for (establecimiento in plaza.establecimientos.filter { it.activo }) {
                val ruta = AEstrella.buscar(grafo, entrada.id, establecimiento.nodoDestinoId)
                assertNotNull(ruta, "sin ruta de ${entrada.id} a ${establecimiento.nombre}")
                assertEquals(establecimiento.nodoDestinoId, ruta.puntos.last().nodoId)
                assertTrue(ruta.costeMetros > 0.0 || ruta.puntos.size == 1)
            }
        }
    }

    @Test
    fun `los establecimientos estan cerca de su nodo de destino`() {
        for (establecimiento in plaza.establecimientos) {
            val nodo = plaza.nodo(establecimiento.nodoDestinoId)
            assertNotNull(nodo, "${establecimiento.nombre} apunta a un nodo inexistente")
            assertEquals(establecimiento.pisoId, nodo.pisoId, "${establecimiento.nombre}: piso distinto al del nodo")
            val distancia = com.rumbo.nucleo.modelo.Geometria.distancia(establecimiento.posicion, nodo.posicion)
            assertTrue(distancia <= 15.0, "${establecimiento.nombre} esta a $distancia m de su nodo de acceso")
        }
    }

    @Test
    fun `los QR de la plaza se pueden imprimir y volver a leer`() {
        for (qr in plaza.puntosQr) {
            val texto = PayloadQr.codificar(qr)
            val leido = PayloadQr.decodificar(texto)
            assertNotNull(leido, "no se pudo decodificar $texto")
            assertEquals(qr.nodoId, leido.nodoId)
            assertEquals(qr.pisoId, leido.pisoId)
            assertEquals(qr.rumboGrados, leido.rumboGrados, 1e-6)
            val nodo = plaza.nodo(qr.nodoId)
            assertNotNull(nodo, "el QR ${qr.codigo} apunta a un nodo inexistente")
            assertEquals(nodo.posicion.x, qr.posicion.x, 1e-6)
            assertEquals(nodo.posicion.y, qr.posicion.y, 1e-6)
        }
    }

    @Test
    fun `el repositorio guarda y recupera la plaza`() {
        val repositorio = RepositorioPlazasJson(AlmacenMemoria())
        assertEquals(0, repositorio.listar().size)
        assertEquals(1, repositorio.sembrarSiHaceFalta())
        assertEquals(0, repositorio.sembrarSiHaceFalta(), "no debe volver a sembrar si ya hay datos")

        val recuperada = repositorio.obtener("plaza_aurora")
        assertNotNull(recuperada)
        assertEquals(plaza.nombre, recuperada.nombre)
        assertEquals(plaza.nodos.size, recuperada.nodos.size)
        assertEquals(plaza.establecimientos.size, recuperada.establecimientos.size)
        assertEquals(plaza.pisos.map { it.rumboNorteGrados }, recuperada.pisos.map { it.rumboNorteGrados })

        // Una edicion del administrador se persiste.
        val editada = recuperada.copy(nombre = "Plaza Aurora (editada)")
        repositorio.guardar(editada)
        assertEquals("Plaza Aurora (editada)", repositorio.obtener("plaza_aurora")?.nombre)

        repositorio.eliminar("plaza_aurora")
        assertEquals(0, repositorio.listar().size)
    }

    @Test
    fun `las categorias de la UI salen de los datos`() {
        val categorias = plaza.categoriasDisponibles()
        assertTrue(Categoria.TIENDA in categorias)
        assertTrue(Categoria.RESTAURANTE in categorias)
        assertTrue(Categoria.BANO in categorias)
        assertTrue(Categoria.FARMACIA in categorias)
        assertTrue(Categoria.CAJERO in categorias)
        assertTrue(Categoria.ASCENSOR in categorias)
        assertTrue(plaza.establecimientosDe(Categoria.TIENDA).isNotEmpty())
        // Ordenadas por nombre para la lista.
        val tiendas = plaza.establecimientosDe(Categoria.TIENDA).map { it.nombre }
        assertEquals(tiendas.sorted(), tiendas)
    }
}
