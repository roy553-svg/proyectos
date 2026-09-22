package com.rumbo.nucleo

import com.rumbo.nucleo.modelo.Punto2D
import com.rumbo.nucleo.modelo.PuntoQr
import com.rumbo.nucleo.qr.PayloadQr
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class PayloadQrTest {

    private val punto = PuntoQr(
        codigo = "QR_001",
        plazaId = "plaza_aurora",
        pisoId = "p1",
        nodoId = "P1_ENT_SUR",
        posicion = Punto2D(40.0, 2.5),
        rumboGrados = 90.0,
        descripcion = "Entrada Sur",
    )

    @Test
    fun `codificar y decodificar es de ida y vuelta`() {
        val texto = PayloadQr.codificar(punto)
        assertTrue(texto.startsWith("RUMBO1;"))
        val vuelta = PayloadQr.decodificar(texto)
        assertNotNull(vuelta)
        assertEquals(punto.codigo, vuelta.codigo)
        assertEquals(punto.plazaId, vuelta.plazaId)
        assertEquals(punto.pisoId, vuelta.pisoId)
        assertEquals(punto.nodoId, vuelta.nodoId)
        assertEquals(punto.posicion.x, vuelta.posicion.x, 1e-6)
        assertEquals(punto.posicion.y, vuelta.posicion.y, 1e-6)
        assertEquals(punto.rumboGrados, vuelta.rumboGrados, 1e-6)
        assertEquals(punto.descripcion, vuelta.descripcion)
    }

    @Test
    fun `rechaza texto que no es nuestro`() {
        assertNull(PayloadQr.decodificar(null))
        assertNull(PayloadQr.decodificar(""))
        assertNull(PayloadQr.decodificar("https://example.com"))
        // Le falta el rumbo.
        assertNull(PayloadQr.decodificar("RUMBO1;plaza=a;piso=p1;nodo=N1;x=1;y=2"))
    }

    @Test
    fun `tolera espacios y campos desconocidos`() {
        val vuelta = PayloadQr.decodificar(
            "  RUMBO1; plaza=plaza_aurora; piso=p2 ; nodo=P2_A_ascensor; x=40; y=46.5; " +
                "rumbo=180; version=7; desc=Piso 2 - junto al ascensor  ",
        )
        assertNotNull(vuelta)
        assertEquals("p2", vuelta.pisoId)
        assertEquals(46.5, vuelta.posicion.y, 1e-6)
        assertEquals(180.0, vuelta.rumboGrados, 1e-6)
        assertEquals("Piso 2 - junto al ascensor", vuelta.descripcion)
    }
}
