package com.rumbo.nucleo

import com.rumbo.nucleo.modelo.Punto2D
import com.rumbo.nucleo.posicionamiento.FiltroPose
import com.rumbo.nucleo.posicionamiento.FuentePosicion
import com.rumbo.nucleo.posicionamiento.PoseMapa
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class FiltroPoseTest {

    private fun pose(x: Double, y: Double, rumbo: Double, piso: String = "p1") =
        PoseMapa(piso, Punto2D(x, y), rumbo, 1.0, FuentePosicion.AR_ODOMETRIA)

    @Test
    fun `la primera pose pasa tal cual`() {
        val filtro = FiltroPose()
        val salida = filtro.filtrar(pose(10.0, 20.0, 45.0))
        assertEquals(10.0, salida.posicion.x, 1e-9)
        assertEquals(20.0, salida.posicion.y, 1e-9)
        assertEquals(45.0, salida.rumboGrados, 1e-6)
    }

    @Test
    fun `suaviza el ruido pequeno`() {
        val filtro = FiltroPose(alfaPosicion = 0.25)
        filtro.filtrar(pose(10.0, 20.0, 0.0))
        val salida = filtro.filtrar(pose(11.0, 20.0, 0.0))
        // Solo avanza una fraccion del metro medido.
        assertTrue(salida.posicion.x in 10.1..10.5, "x=${salida.posicion.x}")
    }

    @Test
    fun `un salto grande se aplica de golpe`() {
        val filtro = FiltroPose(saltoMetrosSinSuavizar = 4.0)
        filtro.filtrar(pose(10.0, 20.0, 0.0))
        val salida = filtro.filtrar(pose(40.0, 20.0, 0.0))
        assertEquals(40.0, salida.posicion.x, 1e-9)
    }

    @Test
    fun `el rumbo no da un latigazo al cruzar 360 grados`() {
        val filtro = FiltroPose(alfaRumbo = 0.5)
        filtro.filtrar(pose(0.0, 0.0, 350.0))
        val salida = filtro.filtrar(pose(0.0, 0.0, 10.0))
        // El resultado debe quedar cerca de 0, no cerca de 180.
        val distanciaA0 = minOf(salida.rumboGrados, 360.0 - salida.rumboGrados)
        assertTrue(distanciaA0 < 15.0, "rumbo filtrado = ${salida.rumboGrados}")
    }

    @Test
    fun `un cambio de piso reinicia el filtro`() {
        val filtro = FiltroPose()
        filtro.filtrar(pose(10.0, 20.0, 0.0))
        val salida = filtro.filtrar(pose(40.0, 46.0, 180.0, piso = "p2"))
        assertEquals(40.0, salida.posicion.x, 1e-9)
        assertEquals(46.0, salida.posicion.y, 1e-9)
        assertEquals(180.0, salida.rumboGrados, 1e-6)
    }
}
