package com.rumbo.ar.posicionamiento

import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.rumbo.nucleo.posicionamiento.AlineacionAr
import com.rumbo.nucleo.posicionamiento.FiltroPose
import com.rumbo.nucleo.posicionamiento.FuentePosicion
import com.rumbo.nucleo.posicionamiento.OrientacionAr
import com.rumbo.nucleo.posicionamiento.PoseAr
import com.rumbo.nucleo.posicionamiento.PoseMapa
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Calidad del seguimiento de ARCore, para avisar al usuario con honestidad. */
data class EstadoSeguimientoAr(
    val rastreando: Boolean = false,
    val aviso: String? = null,
)

/**
 * Posicion a partir de la odometria visual-inercial de ARCore.
 *
 * ARCore sabe con mucha precision COMO se ha movido el telefono (metros y
 * grados respecto a donde arranco la sesion), pero no sabe DONDE esta dentro de
 * la plaza ni donde esta el norte. Por eso:
 *
 *   1. El usuario fija una pose conocida (QR o punto en el mapa).
 *   2. Se empareja con la pose de ARCore de ese instante -> [AlineacionAr].
 *   3. A partir de ahi cada frame se convierte a coordenadas del mapa.
 */
class ProveedorPoseArCore : ProveedorPose {

    private val _pose = MutableStateFlow<PoseMapa?>(null)
    override val pose: StateFlow<PoseMapa?> = _pose.asStateFlow()

    private val _seguimiento = MutableStateFlow(EstadoSeguimientoAr())
    val seguimiento: StateFlow<EstadoSeguimientoAr> = _seguimiento.asStateFlow()

    override val descripcion: String = "Seguimiento AR"

    private val filtro = FiltroPose()

    var alineacion: AlineacionAr? = null
        private set

    /** Altura del suelo en el marco AR, si ARCore ha detectado un plano horizontal. */
    var alturaSueloAr: Double? = null
        private set

    private var ultimaPoseAr: PoseAr? = null
    private var referenciaPendiente: PoseMapa? = null

    override fun iniciar() = Unit

    override fun detener() {
        filtro.reiniciar()
        _pose.value = null
        alineacion = null
        ultimaPoseAr = null
    }

    override fun fijarReferencia(poseConocida: PoseMapa) {
        val poseAr = ultimaPoseAr
        if (poseAr == null) {
            // Todavia no hay frames: se aplica en cuanto llegue el primero.
            referenciaPendiente = poseConocida
            _pose.value = poseConocida
            return
        }
        alineacion = AlineacionAr.alinear(poseAr, poseConocida, System.currentTimeMillis())
        filtro.reiniciar()
        _pose.value = poseConocida
    }

    /**
     * Prepara una sesion AR nueva. ARCore fija un origen distinto cada vez que se
     * crea la sesion (por ejemplo al volver del mapa 2D), asi que la alineacion
     * anterior deja de valer: se vuelve a anclar en la ultima posicion conocida
     * del mapa en el primer frame, degradando la precision declarada.
     */
    fun prepararNuevaSesion() {
        val ultima = _pose.value
        alineacion = null
        ultimaPoseAr = null
        alturaSueloAr = null
        filtro.reiniciar()
        referenciaPendiente = ultima?.copy(
            fuente = FuentePosicion.MANUAL,
            precisionMetros = maxOf(FuentePosicion.MANUAL.precisionTipicaMetros, ultima.precisionMetros),
        )
    }

    /** Se llama desde el hilo de render con cada frame de ARCore. */
    fun procesarFrame(frame: Frame) {
        val camara = frame.camera
        if (camara.trackingState != TrackingState.TRACKING) {
            _seguimiento.value = EstadoSeguimientoAr(
                rastreando = false,
                aviso = mensajeDePerdida(camara.trackingFailureReason),
            )
            return
        }
        _seguimiento.value = EstadoSeguimientoAr(rastreando = true)

        actualizarAlturaSuelo(frame)

        val poseCamara = camara.displayOrientedPose
        val traslacion = FloatArray(3)
        val rotacion = FloatArray(4)
        poseCamara.getTranslation(traslacion, 0)
        poseCamara.getRotationQuaternion(rotacion, 0)

        val poseAr = PoseAr(
            x = traslacion[0].toDouble(),
            y = traslacion[1].toDouble(),
            z = traslacion[2].toDouble(),
            rumboGrados = OrientacionAr.rumboDeCamara(
                rotacion[0].toDouble(),
                rotacion[1].toDouble(),
                rotacion[2].toDouble(),
                rotacion[3].toDouble(),
            ),
        )
        ultimaPoseAr = poseAr

        referenciaPendiente?.let { pendiente ->
            alineacion = AlineacionAr.alinear(poseAr, pendiente, System.currentTimeMillis())
            referenciaPendiente = null
            filtro.reiniciar()
        }

        alineacion?.let { alineada ->
            _pose.value = filtro.filtrar(alineada.aPoseMapa(poseAr, System.currentTimeMillis()))
        }
    }

    /** Altura de la camara sobre el suelo, util para colocar el marcador AR. */
    fun alturaCamaraAr(): Double? = ultimaPoseAr?.y

    private fun actualizarAlturaSuelo(frame: Frame) {
        val planos = frame.getUpdatedTrackables(Plane::class.java)
        for (plano in planos) {
            if (plano.trackingState != TrackingState.TRACKING) continue
            if (plano.type != Plane.Type.HORIZONTAL_UPWARD_FACING) continue
            val altura = plano.centerPose.ty().toDouble()
            val actual = alturaSueloAr
            // El suelo es el plano horizontal mas bajo detectado.
            if (actual == null || altura < actual) alturaSueloAr = altura
        }
    }

    private fun mensajeDePerdida(motivo: TrackingFailureReason?): String? = when (motivo) {
        TrackingFailureReason.INSUFFICIENT_LIGHT -> "Hay poca luz: la cámara no puede orientarse"
        TrackingFailureReason.EXCESSIVE_MOTION -> "Mueve el teléfono más despacio"
        TrackingFailureReason.INSUFFICIENT_FEATURES -> "Apunta a una zona con más detalle (suelo o escaparates)"
        TrackingFailureReason.CAMERA_UNAVAILABLE -> "Otra aplicación está usando la cámara"
        TrackingFailureReason.BAD_STATE -> "Reiniciando el seguimiento AR"
        else -> null
    }
}
