package com.rumbo.nucleo

import com.rumbo.nucleo.modelo.Geometria
import com.rumbo.nucleo.modelo.Punto2D
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class GeometriaTest {

    private val tol = 1e-9

    @Test
    fun `normalizar angulos`() {
        assertEquals(10.0, Geometria.normalizar360(370.0), tol)
        assertEquals(350.0, Geometria.normalizar360(-10.0), tol)
        assertEquals(0.0, Geometria.normalizar360(720.0), tol)
        assertEquals(-90.0, Geometria.normalizar180(270.0), tol)
        assertEquals(180.0, Geometria.normalizar180(180.0), tol)
        assertEquals(-170.0, Geometria.normalizar180(190.0), tol)
    }

    @Test
    fun `diferencia angular toma el camino corto`() {
        // De 350 a 10 grados son 20 grados a la derecha, no 340 a la izquierda.
        assertEquals(20.0, Geometria.diferenciaAngular(350.0, 10.0), tol)
        assertEquals(-20.0, Geometria.diferenciaAngular(10.0, 350.0), tol)
        assertEquals(0.0, Geometria.diferenciaAngular(45.0, 45.0), tol)
    }

    @Test
    fun `rumbo horario desde el eje mas Y`() {
        val origen = Punto2D(0.0, 0.0)
        assertEquals(0.0, Geometria.rumboEntre(origen, Punto2D(0.0, 5.0)), 1e-6)
        assertEquals(90.0, Geometria.rumboEntre(origen, Punto2D(5.0, 0.0)), 1e-6)
        assertEquals(180.0, Geometria.rumboEntre(origen, Punto2D(0.0, -5.0)), 1e-6)
        assertEquals(270.0, Geometria.rumboEntre(origen, Punto2D(-5.0, 0.0)), 1e-6)
        assertEquals(45.0, Geometria.rumboEntre(origen, Punto2D(3.0, 3.0)), 1e-6)
    }

    @Test
    fun `rotar en horario incrementa el rumbo`() {
        val adelante = Punto2D(0.0, 10.0) // rumbo 0
        val rotado = Geometria.rotarHorario(adelante, 90.0)
        assertEquals(10.0, rotado.x, 1e-9)
        assertEquals(0.0, rotado.y, 1e-9)
        assertEquals(90.0, Geometria.rumboEntre(Punto2D(0.0, 0.0), rotado), 1e-6)

        // Rotar y desrotar devuelve el vector original.
        val ida = Geometria.rotarHorario(Punto2D(3.0, -7.0), 37.0)
        val vuelta = Geometria.rotarHorario(ida, -37.0)
        assertEquals(3.0, vuelta.x, 1e-9)
        assertEquals(-7.0, vuelta.y, 1e-9)
    }

    @Test
    fun `distancia a un segmento`() {
        val a = Punto2D(0.0, 0.0)
        val b = Punto2D(10.0, 0.0)
        assertEquals(3.0, Geometria.distanciaASegmento(Punto2D(5.0, 3.0), a, b), tol)
        // Fuera del segmento: se mide al extremo mas cercano.
        assertEquals(5.0, Geometria.distanciaASegmento(Punto2D(15.0, 0.0), a, b), tol)
        assertEquals(0.0, Geometria.distanciaASegmento(Punto2D(4.0, 0.0), a, b), tol)
    }

    @Test
    fun `conversion entre rumbo del mapa y azimut de brujula`() {
        val rumboNorte = 12.0
        val azimut = Geometria.azimutDesdeRumboMapa(rumboMapaGrados = 90.0, rumboNorteGrados = rumboNorte)
        assertEquals(102.0, azimut, tol)
        assertEquals(90.0, Geometria.rumboMapaDesdeAzimut(azimut, rumboNorte), tol)
        assertTrue(Geometria.azimutDesdeRumboMapa(355.0, 12.0) in 0.0..360.0)
    }
}
