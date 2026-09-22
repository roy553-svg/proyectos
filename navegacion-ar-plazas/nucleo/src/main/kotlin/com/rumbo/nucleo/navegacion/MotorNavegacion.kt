package com.rumbo.nucleo.navegacion

import com.rumbo.nucleo.grafo.AEstrella
import com.rumbo.nucleo.grafo.GrafoNavegacion
import com.rumbo.nucleo.modelo.Establecimiento
import com.rumbo.nucleo.modelo.Geometria
import com.rumbo.nucleo.modelo.Plaza
import com.rumbo.nucleo.modelo.Punto2D
import com.rumbo.nucleo.modelo.TipoArista
import com.rumbo.nucleo.posicionamiento.FuentePosicion
import com.rumbo.nucleo.posicionamiento.PoseMapa
import com.rumbo.nucleo.ruta.PuntoRuta
import com.rumbo.nucleo.ruta.Ruta
import kotlin.math.abs
import kotlin.math.roundToInt

enum class Fase { SIN_POSICION, SIN_RUTA, EN_RUTA, CAMBIO_DE_PISO, DESVIADO, LLEGADO }

enum class Giro { ADELANTE, IZQUIERDA, DERECHA, MEDIA_VUELTA }

data class ConfigNavegacion(
    /** Radio para considerar alcanzado un nodo intermedio. */
    val radioNodoMetros: Double = 2.5,
    /** Radio para considerar alcanzado el destino final. */
    val radioDestinoMetros: Double = 4.0,
    /** Distancia a la polilinea de la ruta a partir de la cual se considera desvio. */
    val umbralDesvioMetros: Double = 7.0,
    /** Lecturas consecutivas fuera de ruta antes de avisar (evita falsos positivos). */
    val lecturasParaDesvio: Int = 4,
    val recalculoAutomatico: Boolean = true,
    val umbralGiroGrados: Double = 25.0,
    val umbralMediaVueltaGrados: Double = 135.0,
)

/**
 * Estado de navegacion. Es lo unico que la capa de UI/AR necesita para dibujar:
 * la flecha usa [anguloRelativoGrados] y el marcador AR usa
 * [posicionObjetivo] + el piso.
 */
data class EstadoNavegacion(
    val fase: Fase,
    val ruta: Ruta? = null,
    val indiceObjetivo: Int = 0,
    val posicionObjetivo: Punto2D? = null,
    val pisoObjetivo: String? = null,
    val distanciaAlObjetivoMetros: Double = 0.0,
    val distanciaRestanteMetros: Double = 0.0,
    /** Rumbo absoluto al objetivo, en el marco del mapa. */
    val rumboObjetivoGrados: Double = 0.0,
    /** Angulo que debe girar la flecha en pantalla: 0 = recto, + = derecha, - = izquierda. */
    val anguloRelativoGrados: Double = 0.0,
    val giro: Giro = Giro.ADELANTE,
    val instruccion: String = "",
    val aviso: String? = null,
    val precisionMetros: Double = 0.0,
    val fuentePosicion: FuentePosicion = FuentePosicion.DESCONOCIDA,
) {
    val mostrarFlecha: Boolean get() = fase == Fase.EN_RUTA || fase == Fase.DESVIADO
}

/**
 * Motor de navegacion determinista.
 *
 * Entra una [PoseMapa] (de donde venga) y sale un [EstadoNavegacion]. No conoce
 * la camara, ni ARCore, ni Android: por eso se puede sustituir el sistema de
 * posicionamiento (QR -> BLE -> Wi-Fi RTT) sin tocar nada de esta clase.
 */
class MotorNavegacion(
    val plaza: Plaza,
    val destino: Establecimiento,
    private val config: ConfigNavegacion = ConfigNavegacion(),
    private val grafo: GrafoNavegacion = GrafoNavegacion.construir(plaza),
) {

    var ruta: Ruta? = null
        private set

    private var indiceObjetivo = 1
    private var lecturasFueraDeRuta = 0
    private var llegado = false
    private var esperandoCambioDePiso = false

    val puntoObjetivo: PuntoRuta?
        get() = ruta?.puntos?.getOrNull(indiceObjetivo)

    /** Calcula (o recalcula) la ruta desde la posicion actual del usuario. */
    fun calcularRuta(pose: PoseMapa): Ruta? {
        val origen = grafo.nodoMasCercano(pose.pisoId, pose.posicion) ?: return null
        val nueva = AEstrella.buscar(
            grafo = grafo,
            origenId = origen.id,
            destinoId = destino.nodoDestinoId,
            destinoEstablecimientoId = destino.id,
        )
        ruta = nueva
        indiceObjetivo = if (nueva != null && nueva.puntos.size > 1) 1 else 0
        lecturasFueraDeRuta = 0
        llegado = false
        esperandoCambioDePiso = false
        return nueva
    }

    /**
     * Procesa una nueva pose y devuelve el estado de navegacion.
     * Es idempotente respecto al tiempo: solo depende de la pose recibida.
     */
    fun actualizar(pose: PoseMapa?): EstadoNavegacion {
        if (pose == null || pose.fuente == FuentePosicion.DESCONOCIDA) {
            return EstadoNavegacion(
                fase = Fase.SIN_POSICION,
                ruta = ruta,
                instruccion = "Indica dónde estás para empezar",
            )
        }
        val rutaActual = ruta ?: calcularRuta(pose) ?: return EstadoNavegacion(
            fase = Fase.SIN_RUTA,
            instruccion = "No hay una ruta disponible hasta ${destino.nombre}",
            aviso = "Revisa el grafo de navegación de la plaza",
            precisionMetros = pose.precisionMetros,
            fuentePosicion = pose.fuente,
        )
        if (rutaActual.puntos.isEmpty()) {
            return EstadoNavegacion(fase = Fase.SIN_RUTA, instruccion = "Ruta vacía")
        }

        if (llegado) return estadoLlegada(rutaActual, pose)

        avanzarObjetivo(rutaActual, pose)

        val destinoRuta = rutaActual.puntos.last()
        val enPisoDestino = pose.pisoId == destinoRuta.pisoId
        if (enPisoDestino &&
            Geometria.distancia(pose.posicion, destinoRuta.posicion) <= config.radioDestinoMetros
        ) {
            llegado = true
            return estadoLlegada(rutaActual, pose)
        }

        val objetivo = rutaActual.puntos.getOrNull(indiceObjetivo) ?: destinoRuta

        // Cambio de piso: la flecha no puede ayudar entre plantas, hay que tomar
        // el ascensor o la escalera y volver a fijar la posicion al llegar.
        if (objetivo.pisoId != pose.pisoId) {
            esperandoCambioDePiso = true
            val nivel = plaza.piso(objetivo.pisoId)?.nombre ?: objetivo.pisoId
            val medio = when (objetivo.tipoLlegada) {
                TipoArista.ESCALERA -> "las escaleras"
                else -> "el ascensor"
            }
            return EstadoNavegacion(
                fase = Fase.CAMBIO_DE_PISO,
                ruta = rutaActual,
                indiceObjetivo = indiceObjetivo,
                posicionObjetivo = objetivo.posicion,
                pisoObjetivo = objetivo.pisoId,
                distanciaRestanteMetros = rutaActual.distanciaRestanteMetros(indiceObjetivo, objetivo.posicion),
                instruccion = "Toma $medio hasta $nivel",
                aviso = "Cuando llegues, confirma el piso para continuar",
                precisionMetros = pose.precisionMetros,
                fuentePosicion = pose.fuente,
            )
        }
        esperandoCambioDePiso = false

        val distanciaObjetivo = Geometria.distancia(pose.posicion, objetivo.posicion)
        val rumboObjetivo = Geometria.rumboEntre(pose.posicion, objetivo.posicion)
        val relativo = Geometria.diferenciaAngular(pose.rumboGrados, rumboObjetivo)
        val giro = clasificarGiro(relativo)

        val desviado = evaluarDesvio(rutaActual, pose)
        if (desviado && config.recalculoAutomatico) {
            calcularRuta(pose)?.let {
                val nuevoObjetivo = it.puntos.getOrNull(indiceObjetivo) ?: it.puntos.last()
                val nuevoRumbo = Geometria.rumboEntre(pose.posicion, nuevoObjetivo.posicion)
                val nuevoRelativo = Geometria.diferenciaAngular(pose.rumboGrados, nuevoRumbo)
                return EstadoNavegacion(
                    fase = Fase.DESVIADO,
                    ruta = it,
                    indiceObjetivo = indiceObjetivo,
                    posicionObjetivo = nuevoObjetivo.posicion,
                    pisoObjetivo = nuevoObjetivo.pisoId,
                    distanciaAlObjetivoMetros = Geometria.distancia(pose.posicion, nuevoObjetivo.posicion),
                    distanciaRestanteMetros = it.distanciaRestanteMetros(indiceObjetivo, pose.posicion),
                    rumboObjetivoGrados = nuevoRumbo,
                    anguloRelativoGrados = nuevoRelativo,
                    giro = clasificarGiro(nuevoRelativo),
                    instruccion = instruccionDe(clasificarGiro(nuevoRelativo), Geometria.distancia(pose.posicion, nuevoObjetivo.posicion)),
                    aviso = "Te has desviado. Ruta recalculada.",
                    precisionMetros = pose.precisionMetros,
                    fuentePosicion = pose.fuente,
                )
            }
        }

        return EstadoNavegacion(
            fase = if (desviado) Fase.DESVIADO else Fase.EN_RUTA,
            ruta = rutaActual,
            indiceObjetivo = indiceObjetivo,
            posicionObjetivo = objetivo.posicion,
            pisoObjetivo = objetivo.pisoId,
            distanciaAlObjetivoMetros = distanciaObjetivo,
            distanciaRestanteMetros = rutaActual.distanciaRestanteMetros(indiceObjetivo, pose.posicion),
            rumboObjetivoGrados = rumboObjetivo,
            anguloRelativoGrados = relativo,
            giro = giro,
            instruccion = instruccionDe(giro, distanciaObjetivo),
            aviso = if (desviado) "Te has desviado" else avisoPrecision(pose),
            precisionMetros = pose.precisionMetros,
            fuentePosicion = pose.fuente,
        )
    }

    /**
     * Pose con la que continuar despues de un cambio de piso. La UI la usa cuando
     * el usuario confirma "ya estoy en el piso X": el nodo de llegada del enlace
     * vertical es una posicion conocida, asi que sirve para re-anclar ARCore.
     */
    fun poseTrasCambioDePiso(rumboGrados: Double): PoseMapa? {
        if (!esperandoCambioDePiso) return null
        val objetivo = ruta?.puntos?.getOrNull(indiceObjetivo) ?: return null
        return PoseMapa(
            pisoId = objetivo.pisoId,
            posicion = objetivo.posicion,
            rumboGrados = rumboGrados,
            precisionMetros = FuentePosicion.MANUAL.precisionTipicaMetros,
            fuente = FuentePosicion.MANUAL,
        )
    }

    private fun avanzarObjetivo(ruta: Ruta, pose: PoseMapa) {
        while (indiceObjetivo < ruta.puntos.lastIndex) {
            val objetivo = ruta.puntos[indiceObjetivo]
            if (objetivo.pisoId != pose.pisoId) return
            val distancia = Geometria.distancia(pose.posicion, objetivo.posicion)
            val siguiente = ruta.puntos[indiceObjetivo + 1]
            val alcanzado = distancia <= config.radioNodoMetros
            // Tambien se avanza si el siguiente punto del mismo piso ya esta mas
            // cerca que el actual: el usuario "corto" la esquina.
            val adelantado = siguiente.pisoId == pose.pisoId &&
                Geometria.distancia(pose.posicion, siguiente.posicion) < distancia &&
                Geometria.distanciaASegmento(pose.posicion, objetivo.posicion, siguiente.posicion) <=
                config.radioNodoMetros
            if (alcanzado || adelantado) indiceObjetivo++ else return
        }
    }

    private fun evaluarDesvio(ruta: Ruta, pose: PoseMapa): Boolean {
        val distancia = distanciaALaRuta(ruta, pose)
        if (distancia > config.umbralDesvioMetros + pose.precisionMetros) {
            lecturasFueraDeRuta++
        } else {
            lecturasFueraDeRuta = 0
        }
        return lecturasFueraDeRuta >= config.lecturasParaDesvio
    }

    /** Distancia del usuario al tramo de ruta que le corresponde en su piso. */
    fun distanciaALaRuta(ruta: Ruta, pose: PoseMapa): Double {
        var minima = Double.MAX_VALUE
        for (i in 0 until ruta.puntos.lastIndex) {
            val a = ruta.puntos[i]
            val b = ruta.puntos[i + 1]
            if (a.pisoId != pose.pisoId || b.pisoId != pose.pisoId) continue
            minima = minOf(minima, Geometria.distanciaASegmento(pose.posicion, a.posicion, b.posicion))
        }
        if (minima == Double.MAX_VALUE) {
            minima = ruta.puntos.filter { it.pisoId == pose.pisoId }
                .minOfOrNull { Geometria.distancia(pose.posicion, it.posicion) }
                ?: 0.0
        }
        return minima
    }

    private fun clasificarGiro(relativoGrados: Double): Giro = when {
        abs(relativoGrados) >= config.umbralMediaVueltaGrados -> Giro.MEDIA_VUELTA
        relativoGrados > config.umbralGiroGrados -> Giro.DERECHA
        relativoGrados < -config.umbralGiroGrados -> Giro.IZQUIERDA
        else -> Giro.ADELANTE
    }

    private fun instruccionDe(giro: Giro, distanciaMetros: Double): String {
        val metros = distanciaMetros.roundToInt()
        return when (giro) {
            Giro.ADELANTE -> "Sigue recto $metros m"
            Giro.IZQUIERDA -> "Gira a la izquierda"
            Giro.DERECHA -> "Gira a la derecha"
            Giro.MEDIA_VUELTA -> "Da media vuelta"
        }
    }

    private fun avisoPrecision(pose: PoseMapa): String? = when {
        pose.fuente == FuentePosicion.GPS ->
            "Precisión limitada en interiores: escanea un QR para ajustar tu posición"
        pose.precisionMetros > 10.0 ->
            "Precisión aproximada (±${pose.precisionMetros.roundToInt()} m)"
        else -> null
    }

    private fun estadoLlegada(ruta: Ruta, pose: PoseMapa) = EstadoNavegacion(
        fase = Fase.LLEGADO,
        ruta = ruta,
        indiceObjetivo = ruta.puntos.lastIndex,
        posicionObjetivo = ruta.puntos.last().posicion,
        pisoObjetivo = ruta.puntos.last().pisoId,
        distanciaAlObjetivoMetros = Geometria.distancia(pose.posicion, ruta.puntos.last().posicion),
        distanciaRestanteMetros = 0.0,
        instruccion = "Has llegado",
        precisionMetros = pose.precisionMetros,
        fuentePosicion = pose.fuente,
    )
}
