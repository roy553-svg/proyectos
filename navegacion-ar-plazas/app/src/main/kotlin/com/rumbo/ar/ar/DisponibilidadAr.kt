package com.rumbo.ar.ar

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.ar.core.ArCoreApk
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException

/**
 * Estado del soporte AR del dispositivo.
 *
 * La app NUNCA se cierra por falta de AR: si el resultado no es [DISPONIBLE] se
 * navega con el mapa 2D y la brujula (ver ProveedorPoseSensores).
 */
enum class EstadoAr {
    /** ARCore instalado y dispositivo compatible. */
    DISPONIBLE,

    /** Dispositivo compatible, pero hay que instalar/actualizar Google Play Services for AR. */
    REQUIERE_INSTALACION,

    /** Dispositivo sin soporte AR. */
    NO_COMPATIBLE,

    /** Todavia se esta comprobando (la consulta es asincrona la primera vez). */
    COMPROBANDO,
}

object DisponibilidadAr {

    private const val ETIQUETA = "DisponibilidadAr"

    fun comprobar(contexto: Context): EstadoAr =
        when (ArCoreApk.getInstance().checkAvailability(contexto)) {
            ArCoreApk.Availability.SUPPORTED_INSTALLED -> EstadoAr.DISPONIBLE
            ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD,
            ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED,
            -> EstadoAr.REQUIERE_INSTALACION

            ArCoreApk.Availability.UNKNOWN_CHECKING -> EstadoAr.COMPROBANDO

            ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE,
            ArCoreApk.Availability.UNKNOWN_ERROR,
            ArCoreApk.Availability.UNKNOWN_TIMED_OUT,
            -> EstadoAr.NO_COMPATIBLE
        }

    /**
     * Pide la instalacion de Google Play Services for AR si hace falta.
     * Devuelve true si ya se puede crear la sesion.
     */
    fun solicitarInstalacionSiHaceFalta(actividad: Activity, yaSolicitado: Boolean): Boolean = try {
        ArCoreApk.getInstance().requestInstall(actividad, !yaSolicitado) ==
            ArCoreApk.InstallStatus.INSTALLED
    } catch (e: UnavailableUserDeclinedInstallationException) {
        Log.i(ETIQUETA, "El usuario rechazo instalar ARCore; se usara el modo sin AR", e)
        false
    } catch (e: UnavailableDeviceNotCompatibleException) {
        Log.i(ETIQUETA, "Dispositivo no compatible con AR; se usara el modo sin AR", e)
        false
    } catch (e: Exception) {
        Log.w(ETIQUETA, "No se pudo preparar ARCore", e)
        false
    }
}
