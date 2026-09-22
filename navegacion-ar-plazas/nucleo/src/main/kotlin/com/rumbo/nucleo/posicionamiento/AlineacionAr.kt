package com.rumbo.nucleo.posicionamiento

import com.rumbo.nucleo.modelo.Geometria
import com.rumbo.nucleo.modelo.Punto2D

/**
 * Puente entre el marco de mundo de ARCore y el marco del mapa.
 *
 * ARCore da una pose metrica muy estable, pero en un sistema de coordenadas
 * arbitrario: el origen es donde arranco la sesion y el giro respecto al norte
 * es desconocido. Para que una flecha virtual apunte a una tienda real hace
 * falta una unica cosa: conocer la pose del usuario en el mapa en un instante
 * concreto (la da el QR o el punto marcado a mano) y emparejarla con la pose de
 * ARCore en ese mismo instante.
 *
 * A partir de ese emparejamiento:
 *   posicionMapa = origenMapa + rotarHorario(desplazamientoAr, desfaseGrados)
 *   rumboMapa    = rumboAr + desfaseGrados
 *
 * La escala es 1:1 porque ARCore trabaja en metros, igual que el mapa.
 */
class AlineacionAr(
    val pisoId: String,
    /** Proyeccion horizontal (derecha, adelante) de la traslacion AR en el instante del ajuste. */
    val origenAr: Punto2D,
    val alturaArOrigen: Double,
    val origenMapa: Punto2D,
    /** rumboMapa - rumboAr, en grados. */
    val desfaseGrados: Double,
    val fuenteAjuste: FuentePosicion,
    val marcaTiempoMs: Long = 0L,
) {

    /** Convierte una pose de ARCore en una pose del mapa. */
    fun aPoseMapa(poseAr: PoseAr, marcaTiempoMs: Long = this.marcaTiempoMs): PoseMapa {
        val desplazamiento = Punto2D(
            x = poseAr.x - origenAr.x,
            y = -poseAr.z - origenAr.y,
        )
        val enMapa = Geometria.rotarHorario(desplazamiento, desfaseGrados)
        val recorrido = Geometria.distancia(Punto2D(0.0, 0.0), desplazamiento)
        return PoseMapa(
            pisoId = pisoId,
            posicion = origenMapa + enMapa,
            rumboGrados = Geometria.normalizar360(poseAr.rumboGrados + desfaseGrados),
            // La odometria visual acumula deriva: del orden del 1-2% de lo recorrido.
            precisionMetros = precisionEstimada(recorrido),
            fuente = FuentePosicion.AR_ODOMETRIA,
            marcaTiempoMs = marcaTiempoMs,
        )
    }

    /** Punto del mundo AR que corresponde a un punto del mapa (para anchors). */
    fun aPuntoAr(puntoMapa: Punto2D, alturaRelativaMetros: Double = 0.0): PuntoAr {
        val enMapa = puntoMapa - origenMapa
        val enAr = Geometria.rotarHorario(enMapa, -desfaseGrados)
        return PuntoAr(
            x = origenAr.x + enAr.x,
            y = alturaArOrigen + alturaRelativaMetros,
            z = -(origenAr.y + enAr.y),
        )
    }

    fun precisionEstimada(metrosRecorridos: Double): Double =
        fuenteAjuste.precisionTipicaMetros + DERIVA_RELATIVA * metrosRecorridos

    companion object {
        /** Deriva tipica de la odometria visual-inercial de ARCore en interiores. */
        const val DERIVA_RELATIVA = 0.02

        /**
         * Empareja la pose de ARCore con una pose conocida del mapa.
         * [poseMapaConocida] debe venir de un QR o de un punto marcado a mano.
         */
        fun alinear(
            poseAr: PoseAr,
            poseMapaConocida: PoseMapa,
            marcaTiempoMs: Long = poseMapaConocida.marcaTiempoMs,
        ): AlineacionAr = AlineacionAr(
            pisoId = poseMapaConocida.pisoId,
            origenAr = Punto2D(poseAr.x, -poseAr.z),
            alturaArOrigen = poseAr.y,
            origenMapa = poseMapaConocida.posicion,
            desfaseGrados = Geometria.normalizar360(
                poseMapaConocida.rumboGrados - poseAr.rumboGrados,
            ),
            fuenteAjuste = poseMapaConocida.fuente,
            marcaTiempoMs = marcaTiempoMs,
        )
    }
}
