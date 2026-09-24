package com.rumbo.ar.posicionamiento

import com.rumbo.nucleo.posicionamiento.PoseMapa
import kotlinx.coroutines.flow.StateFlow

/**
 * Fuente de posicion del usuario dentro del mapa.
 *
 * Esta es la costura que permite cambiar el sistema de posicionamiento sin
 * tocar la navegacion: hoy hay dos implementaciones (ARCore y sensores), y
 * manana se pueden anadir BLE, Wi-Fi RTT o UWB implementando esta misma
 * interfaz. El motor de navegacion solo consume [pose].
 *
 *   ProveedorPose
 *      +-- ProveedorPoseArCore     (odometria visual-inercial, MVP)
 *      +-- ProveedorPoseSensores   (brujula + contador de pasos, respaldo)
 *      +-- (futuro) ProveedorPoseBle / WifiRtt / Uwb
 *
 * Todas necesitan un punto de partida conocido, que en el MVP da el QR o el
 * punto marcado a mano sobre el mapa.
 */
interface ProveedorPose {

    val pose: StateFlow<PoseMapa?>

    /** Texto corto para la UI ("Seguimiento AR", "Brújula + pasos"). */
    val descripcion: String

    /** Fija una pose conocida (QR, mapa o nodo de llegada tras un cambio de piso). */
    fun fijarReferencia(poseConocida: PoseMapa)

    fun iniciar()

    fun detener()
}
