package com.rumbo.nucleo

import com.rumbo.nucleo.modelo.Punto2D
import com.rumbo.nucleo.posicionamiento.AlineacionAr
import com.rumbo.nucleo.posicionamiento.FuentePosicion
import com.rumbo.nucleo.posicionamiento.PoseAr
import com.rumbo.nucleo.posicionamiento.PoseMapa
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class AlineacionArTest {

    private val poseQr = PoseMapa(
        pisoId = "p1",
        posicion = Punto2D(40.0, 2.0),
        rumboGrados = 0.0,
        precisionMetros = 0.5,
        fuente = FuentePosicion.QR,
    )

    @Test
    fun `caminar de frente en AR avanza en el rumbo del mapa`() {
        // El usuario lee el QR mirando al rumbo 0 del mapa. ARCore arranca con
        // la camara mirando a su -Z, asi que el desfase debe ser 0.
        val poseArInicial = PoseAr(x = 0.0, y = 1.5, z = 0.0, rumboGrados = 0.0)
        val alineacion = AlineacionAr.alinear(poseArInicial, poseQr)
        assertEquals(0.0, alineacion.desfaseGrados, 1e-9)

        // 10 metros hacia delante en AR (-Z) => 10 metros en +Y del mapa.
        val enMapa = alineacion.aPoseMapa(PoseAr(0.0, 1.5, -10.0, 0.0))
        assertEquals(40.0, enMapa.posicion.x, 1e-9)
        assertEquals(12.0, enMapa.posicion.y, 1e-9)
        assertEquals(0.0, enMapa.rumboGrados, 1e-9)
        assertEquals(FuentePosicion.AR_ODOMETRIA, enMapa.fuente)
    }

    @Test
    fun `desfase cuando el usuario lee el QR mirando al este del mapa`() {
        // QR que mira al rumbo 90 del mapa (hacia +X).
        val pose = poseQr.copy(rumboGrados = 90.0, posicion = Punto2D(10.0, 25.0))
        val alineacion = AlineacionAr.alinear(PoseAr(0.0, 1.5, 0.0, 0.0), pose)
        assertEquals(90.0, alineacion.desfaseGrados, 1e-9)

        // Caminar 8 m de frente en AR => +8 m en X del mapa.
        val tras8 = alineacion.aPoseMapa(PoseAr(0.0, 1.5, -8.0, 0.0))
        assertEquals(18.0, tras8.posicion.x, 1e-6)
        assertEquals(25.0, tras8.posicion.y, 1e-6)

        // Desplazarse 3 m a la derecha en AR (+X) => -3 m en Y del mapa.
        val derecha = alineacion.aPoseMapa(PoseAr(3.0, 1.5, 0.0, 0.0))
        assertEquals(10.0, derecha.posicion.x, 1e-6)
        assertEquals(22.0, derecha.posicion.y, 1e-6)

        // Girar la camara 90 grados en AR tambien gira 90 grados en el mapa.
        val girado = alineacion.aPoseMapa(PoseAr(0.0, 1.5, 0.0, 90.0))
        assertEquals(180.0, girado.rumboGrados, 1e-6)
    }

    @Test
    fun `mapa a AR es la inversa exacta`() {
        val alineacion = AlineacionAr.alinear(
            PoseAr(x = 2.0, y = 1.6, z = -3.0, rumboGrados = 33.0),
            poseQr.copy(rumboGrados = 215.0),
        )
        val puntos = listOf(Punto2D(40.0, 2.0), Punto2D(58.0, 25.0), Punto2D(10.0, 47.0))
        for (punto in puntos) {
            val enAr = alineacion.aPuntoAr(punto, alturaRelativaMetros = -1.2)
            val vuelta = alineacion.aPoseMapa(PoseAr(enAr.x, enAr.y, enAr.z, 0.0))
            assertEquals(punto.x, vuelta.posicion.x, 1e-6)
            assertEquals(punto.y, vuelta.posicion.y, 1e-6)
            assertEquals(1.6 - 1.2, enAr.y, 1e-9)
        }
    }

    @Test
    fun `la precision se degrada con la distancia recorrida`() {
        val alineacion = AlineacionAr.alinear(PoseAr(0.0, 1.5, 0.0, 0.0), poseQr)
        val cerca = alineacion.aPoseMapa(PoseAr(0.0, 1.5, -5.0, 0.0))
        val lejos = alineacion.aPoseMapa(PoseAr(0.0, 1.5, -100.0, 0.0))
        assertTrue(lejos.precisionMetros > cerca.precisionMetros)
        // 0.5 m del QR + 2% de 100 m = 2.5 m
        assertEquals(2.5, lejos.precisionMetros, 1e-6)
    }
}
