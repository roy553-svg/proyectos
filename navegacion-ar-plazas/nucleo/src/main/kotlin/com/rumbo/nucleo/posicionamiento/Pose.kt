package com.rumbo.nucleo.posicionamiento

import com.rumbo.nucleo.modelo.Punto2D

/** De donde viene una estimacion de posicion. Ordenadas de mas a menos fiable. */
enum class FuentePosicion(val etiqueta: String, val precisionTipicaMetros: Double) {
    QR("Código QR", 0.5),
    MANUAL("Punto marcado en el mapa", 3.0),
    AR_ODOMETRIA("Seguimiento AR", 1.5),
    PASOS_BRUJULA("Pasos + brújula", 6.0),
    GPS("GPS", 15.0),
    DESCONOCIDA("Sin posición", Double.MAX_VALUE),
}

/**
 * Pose del usuario EN EL MARCO DEL MAPA: piso, posicion en metros y rumbo
 * (horario desde el eje +Y del plano). Es la unica entrada que el motor de
 * navegacion necesita, venga de QR, de ARCore, de pasos o de GPS.
 */
data class PoseMapa(
    val pisoId: String,
    val posicion: Punto2D,
    val rumboGrados: Double,
    val precisionMetros: Double = FuentePosicion.MANUAL.precisionTipicaMetros,
    val fuente: FuentePosicion = FuentePosicion.MANUAL,
    val marcaTiempoMs: Long = 0L,
)

/**
 * Pose de la camara en el marco de mundo de ARCore: metros, Y hacia arriba,
 * X y Z horizontales pero con una orientacion arbitraria fijada al arrancar la
 * sesion (ARCore NO sabe donde esta el norte).
 */
data class PoseAr(
    val x: Double,
    val y: Double,
    val z: Double,
    /** Rumbo del eje de vision de la camara dentro del marco de ARCore. */
    val rumboGrados: Double,
)

/** Punto en el marco de mundo de ARCore (para colocar anchors). */
data class PuntoAr(val x: Double, val y: Double, val z: Double)
