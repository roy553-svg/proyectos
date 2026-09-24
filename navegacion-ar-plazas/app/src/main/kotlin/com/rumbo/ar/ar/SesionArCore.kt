package com.rumbo.ar.ar

import android.app.Activity
import android.os.Build
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableException

/**
 * Ciclo de vida de la sesion de ARCore.
 *
 * Configuracion elegida:
 *  - `updateMode = LATEST_CAMERA_IMAGE`: la vista previa no se bloquea esperando
 *    frames; es lo adecuado cuando el render lo dirige un GLSurfaceView.
 *  - `planeFindingMode = HORIZONTAL`: detectar el suelo permite apoyar el
 *    marcador del siguiente punto a la altura correcta. No se usa vision para
 *    decidir DONDE estan las tiendas: eso sale siempre del mapa.
 *  - Sin Depth ni Geospatial: el primer prototipo no los necesita y no estan
 *    disponibles en todos los dispositivos.
 */
class SesionArCore(private val actividad: Activity) {

    var sesion: Session? = null
        private set

    var ultimoError: String? = null
        private set

    private var instalacionSolicitada = false

    /** Crea la sesion si hace falta y la reanuda. Devuelve true si quedo activa. */
    fun reanudar(): Boolean {
        if (sesion == null) {
            if (!DisponibilidadAr.solicitarInstalacionSiHaceFalta(actividad, instalacionSolicitada)) {
                instalacionSolicitada = true
                ultimoError = "Google Play Services for AR no está disponible"
                return false
            }
            sesion = try {
                Session(actividad).also { nueva ->
                    nueva.configure(
                        Config(nueva).apply {
                            updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                            planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                            lightEstimationMode = Config.LightEstimationMode.DISABLED
                            focusMode = Config.FocusMode.AUTO
                        },
                    )
                }
            } catch (e: UnavailableException) {
                ultimoError = "No se pudo iniciar AR: ${e.javaClass.simpleName}"
                Log.w(ETIQUETA, "Sesion AR no disponible", e)
                null
            }
        }
        val activa = sesion ?: return false
        return try {
            activa.resume()
            ultimoError = null
            true
        } catch (e: CameraNotAvailableException) {
            ultimoError = "La cámara no está disponible"
            Log.w(ETIQUETA, "Camara ocupada", e)
            cerrar()
            false
        }
    }

    fun pausar() {
        sesion?.pause()
    }

    fun cerrar() {
        sesion?.close()
        sesion = null
    }

    /** ARCore necesita saber la rotacion de la pantalla para orientar la imagen. */
    fun actualizarGeometria(anchoPx: Int, altoPx: Int) {
        sesion?.setDisplayGeometry(rotacionPantalla(), anchoPx, altoPx)
    }

    private fun rotacionPantalla(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            actividad.display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            actividad.getSystemService(WindowManager::class.java).defaultDisplay.rotation
        }

    private companion object {
        const val ETIQUETA = "SesionArCore"
    }
}
