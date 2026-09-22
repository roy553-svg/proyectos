package com.rumbo.nucleo

import com.rumbo.nucleo.datos.SemillaPlazas
import com.rumbo.nucleo.modelo.Geometria
import com.rumbo.nucleo.modelo.Plaza
import com.rumbo.nucleo.modelo.Punto2D
import com.rumbo.nucleo.navegacion.ConfigNavegacion
import com.rumbo.nucleo.navegacion.EstadoNavegacion
import com.rumbo.nucleo.navegacion.Fase
import com.rumbo.nucleo.navegacion.Giro
import com.rumbo.nucleo.navegacion.MotorNavegacion
import com.rumbo.nucleo.posicionamiento.FuentePosicion
import com.rumbo.nucleo.posicionamiento.PoseMapa
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class MotorNavegacionTest {

    private val plaza: Plaza = requireNonNull(SemillaPlazas.cargar("/plazas/plaza_aurora.json"))

    private fun <T> requireNonNull(valor: T?): T = valor ?: error("No se pudo cargar la plaza de demostracion")

    private fun poseEntradaSur(rumbo: Double = 0.0) = PoseMapa(
        pisoId = "p1",
        posicion = Punto2D(40.0, 2.0),
        rumboGrados = rumbo,
        precisionMetros = 0.5,
        fuente = FuentePosicion.QR,
    )

    private fun avanzar(desde: Punto2D, rumboGrados: Double, metros: Double): Punto2D {
        val r = Math.toRadians(rumboGrados)
        return Punto2D(desde.x + sin(r) * metros, desde.y + cos(r) * metros)
    }

    /** Camina siguiendo la flecha, un metro por iteracion. Devuelve el recorrido. */
    private fun caminarSiguiendoLaFlecha(
        motor: MotorNavegacion,
        poseInicial: PoseMapa,
        pasosMaximos: Int = 400,
    ): Recorrido {
        var pose = poseInicial
        var estado = motor.actualizar(pose)
        val indices = mutableListOf(estado.indiceObjetivo)
        val desvios = mutableListOf<String>()
        var cambiosDePiso = 0
        var pasos = 0
        while (estado.fase != Fase.LLEGADO && pasos < pasosMaximos) {
            pasos++
            if (estado.fase == Fase.CAMBIO_DE_PISO) {
                cambiosDePiso++
                pose = requireNonNull(motor.poseTrasCambioDePiso(pose.rumboGrados))
                estado = motor.actualizar(pose)
                indices += estado.indiceObjetivo
                continue
            }
            val objetivo = estado.posicionObjetivo ?: break
            // El usuario hace exactamente lo que dice la flecha: girar y avanzar.
            val rumbo = Geometria.normalizar360(pose.rumboGrados + estado.anguloRelativoGrados)
            assertEquals(Geometria.rumboEntre(pose.posicion, objetivo), rumbo, 1e-6)
            val paso = minOf(1.0, Geometria.distancia(pose.posicion, objetivo))
            pose = pose.copy(posicion = avanzar(pose.posicion, rumbo, paso), rumboGrados = rumbo)
            estado = motor.actualizar(pose)
            indices += estado.indiceObjetivo
            estado.aviso?.let { if (it.contains("desviado")) desvios += it }
        }
        return Recorrido(estado, pose, pasos, indices, desvios, cambiosDePiso)
    }

    private data class Recorrido(
        val estado: EstadoNavegacion,
        val pose: PoseMapa,
        val pasos: Int,
        val indices: List<Int>,
        val desvios: List<String>,
        val cambiosDePiso: Int,
    )

    @Test
    fun `llega a una tienda del mismo piso siguiendo la flecha`() {
        val destino = requireNonNull(plaza.establecimientos.firstOrNull { it.nombre == "Moda Lumen" })
        val motor = MotorNavegacion(plaza, destino)
        val ruta = requireNonNull(motor.calcularRuta(poseEntradaSur()))
        assertTrue(ruta.puntos.size >= 4, "la ruta deberia tener varios puntos: ${ruta.puntos.map { it.nodoId }}")
        assertEquals(destino.nodoDestinoId, ruta.puntos.last().nodoId)

        val recorrido = caminarSiguiendoLaFlecha(motor, poseEntradaSur())
        assertEquals(Fase.LLEGADO, recorrido.estado.fase)
        assertEquals("Has llegado", recorrido.estado.instruccion)
        assertTrue(recorrido.desvios.isEmpty(), "no deberia detectar desvios: ${recorrido.desvios}")
        // Los puntos de la ruta se consumen en orden, sin volver atras.
        assertEquals(recorrido.indices.sorted(), recorrido.indices)
        // El recorrido real no puede ser mucho mayor que la ruta calculada.
        assertTrue(
            recorrido.pasos <= ruta.longitudHorizontalMetros + 10,
            "pasos=${recorrido.pasos} longitud=${ruta.longitudHorizontalMetros}",
        )
    }

    @Test
    fun `llega a un establecimiento de otro piso usando el ascensor`() {
        val destino = requireNonNull(plaza.establecimientos.firstOrNull { it.nombre == "Sushi Koi" })
        assertEquals("p2", destino.pisoId)
        val motor = MotorNavegacion(plaza, destino)
        val recorrido = caminarSiguiendoLaFlecha(motor, poseEntradaSur())
        assertEquals(Fase.LLEGADO, recorrido.estado.fase)
        assertEquals(1, recorrido.cambiosDePiso)
        assertEquals("p2", recorrido.pose.pisoId)
    }

    @Test
    fun `la flecha indica izquierda derecha o recto segun el rumbo`() {
        val destino = requireNonNull(plaza.establecimientos.firstOrNull { it.nombre == "Moda Lumen" })
        val motor = MotorNavegacion(plaza, destino)
        // De pie en el cruce del pasillo central; el siguiente punto queda al oeste.
        val cruce = Punto2D(40.0, 25.0)
        motor.calcularRuta(PoseMapa("p1", cruce, 0.0, 0.5, FuentePosicion.QR))

        val mirandoAlNorte = motor.actualizar(PoseMapa("p1", cruce, 0.0, 0.5, FuentePosicion.QR))
        assertEquals(Giro.IZQUIERDA, mirandoAlNorte.giro)
        assertEquals(-90.0, mirandoAlNorte.anguloRelativoGrados, 1.0)

        val mirandoAlSur = motor.actualizar(PoseMapa("p1", cruce, 180.0, 0.5, FuentePosicion.QR))
        assertEquals(Giro.DERECHA, mirandoAlSur.giro)
        assertEquals(90.0, mirandoAlSur.anguloRelativoGrados, 1.0)

        val mirandoAlOeste = motor.actualizar(PoseMapa("p1", cruce, 270.0, 0.5, FuentePosicion.QR))
        assertEquals(Giro.ADELANTE, mirandoAlOeste.giro)
        assertEquals(0.0, mirandoAlOeste.anguloRelativoGrados, 1.0)
        assertTrue(mirandoAlOeste.instruccion.startsWith("Sigue recto"))

        val mirandoAlEste = motor.actualizar(PoseMapa("p1", cruce, 90.0, 0.5, FuentePosicion.QR))
        assertEquals(Giro.MEDIA_VUELTA, mirandoAlEste.giro)
    }

    @Test
    fun `avisa del desvio y recalcula`() {
        val destino = requireNonNull(plaza.establecimientos.firstOrNull { it.nombre == "Moda Lumen" })
        val motor = MotorNavegacion(plaza, destino, ConfigNavegacion(lecturasParaDesvio = 3))
        var pose = poseEntradaSur()
        motor.calcularRuta(pose)
        val rutaInicial = requireNonNull(motor.ruta).puntos.map { it.nodoId }

        // El usuario se va en diagonal hacia el noreste, lejos de la ruta calculada.
        val fases = mutableListOf(motor.actualizar(pose).fase)
        val avisos = mutableListOf<String>()
        repeat(20) {
            pose = pose.copy(posicion = avanzar(pose.posicion, 45.0, 1.5), rumboGrados = 45.0)
            val estado = motor.actualizar(pose)
            fases += estado.fase
            estado.aviso?.let { avisos += it }
        }
        assertTrue(Fase.DESVIADO in fases, "nunca se detecto el desvio: $fases")
        assertTrue(avisos.any { it.contains("desviado") }, "no se aviso del desvio: $avisos")

        // Tras recalcular, la ruta es otra y sigue terminando en el destino.
        val rutaNueva = requireNonNull(motor.ruta)
        assertEquals(destino.nodoDestinoId, rutaNueva.puntos.last().nodoId)
        assertTrue(rutaNueva.puntos.map { it.nodoId } != rutaInicial)

        // Y desde la nueva posicion todavia se llega caminando.
        assertEquals(Fase.LLEGADO, caminarSiguiendoLaFlecha(motor, pose).estado.fase)
    }

    @Test
    fun `sin posicion no hay flecha`() {
        val destino = plaza.establecimientos.first()
        val motor = MotorNavegacion(plaza, destino)
        val estado = motor.actualizar(null)
        assertEquals(Fase.SIN_POSICION, estado.fase)
        assertTrue(!estado.mostrarFlecha)
    }

    @Test
    fun `la distancia restante disminuye al avanzar`() {
        val destino = requireNonNull(plaza.establecimientos.firstOrNull { it.nombre == "Perfumería Luna" })
        val motor = MotorNavegacion(plaza, destino)
        var pose = poseEntradaSur()
        motor.calcularRuta(pose)
        var anterior = motor.actualizar(pose).distanciaRestanteMetros
        repeat(15) {
            val estado = motor.actualizar(pose)
            val objetivo = requireNonNull(estado.posicionObjetivo)
            val rumbo = Geometria.rumboEntre(pose.posicion, objetivo)
            pose = pose.copy(posicion = avanzar(pose.posicion, rumbo, 1.0), rumboGrados = rumbo)
            val ahora = motor.actualizar(pose).distanciaRestanteMetros
            assertTrue(ahora <= anterior + 1e-6, "la distancia restante crecio: $anterior -> $ahora")
            anterior = ahora
        }
    }
}
