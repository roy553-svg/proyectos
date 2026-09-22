package com.rumbo.nucleo

import com.rumbo.nucleo.posicionamiento.OrientacionAr
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.assertEquals
import org.junit.Test

class OrientacionArTest {

    /** Cuaternion de una rotacion de [grados] alrededor del eje Y (vertical). */
    private fun giroY(grados: Double): DoubleArray {
        val mitad = Math.toRadians(grados) / 2.0
        return doubleArrayOf(0.0, sin(mitad), 0.0, cos(mitad))
    }

    private fun giroX(grados: Double): DoubleArray {
        val mitad = Math.toRadians(grados) / 2.0
        return doubleArrayOf(sin(mitad), 0.0, 0.0, cos(mitad))
    }

    @Test
    fun `camara sin rotar mira al rumbo cero`() {
        assertEquals(0.0, OrientacionAr.rumboDeCamara(0.0, 0.0, 0.0, 1.0), 1e-6)
    }

    @Test
    fun `girar a la derecha aumenta el rumbo`() {
        // Un giro positivo alrededor de +Y gira a la izquierda (regla de la mano
        // derecha), asi que el rumbo disminuye.
        val izquierda = giroY(90.0)
        assertEquals(270.0, OrientacionAr.rumboDeCamara(izquierda[0], izquierda[1], izquierda[2], izquierda[3]), 1e-6)

        val derecha = giroY(-90.0)
        assertEquals(90.0, OrientacionAr.rumboDeCamara(derecha[0], derecha[1], derecha[2], derecha[3]), 1e-6)

        val atras = giroY(180.0)
        assertEquals(180.0, OrientacionAr.rumboDeCamara(atras[0], atras[1], atras[2], atras[3]), 1e-6)
    }

    @Test
    fun `apuntando al suelo se usa el eje vertical de la pantalla`() {
        // Inclinar la camara 90 grados hacia abajo: el eje de vision deja de dar
        // informacion horizontal, pero el rumbo debe seguir siendo 0.
        val abajo = giroX(-90.0)
        assertEquals(0.0, OrientacionAr.rumboDeCamara(abajo[0], abajo[1], abajo[2], abajo[3]), 1e-6)
    }

    @Test
    fun `rotar un vector por un cuaternion`() {
        val q = giroY(90.0)
        val (x, y, z) = OrientacionAr.rotarPorCuaternion(q[0], q[1], q[2], q[3], 0.0, 0.0, -1.0)
        assertEquals(-1.0, x, 1e-9)
        assertEquals(0.0, y, 1e-9)
        assertEquals(0.0, z, 1e-9)
    }
}
