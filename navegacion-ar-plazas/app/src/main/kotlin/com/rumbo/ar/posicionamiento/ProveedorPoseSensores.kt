package com.rumbo.ar.posicionamiento

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.rumbo.nucleo.modelo.Geometria
import com.rumbo.nucleo.modelo.Punto2D
import com.rumbo.nucleo.posicionamiento.FuentePosicion
import com.rumbo.nucleo.posicionamiento.PoseMapa
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Respaldo sin ARCore: orientacion con el vector de rotacion (acelerometro +
 * giroscopio + magnetometro fusionados por Android) y avance con el detector de
 * pasos del propio telefono.
 *
 * Es menos preciso que ARCore (la brujula se desvia con el metal y las zancadas
 * son una estimacion), pero permite navegar en dispositivos sin soporte AR sin
 * instalar nada en la plaza. La precision se declara como tal en la UI.
 *
 * Detalle importante del eje: se remapea el sistema de coordenadas con
 * (AXIS_X, AXIS_Z) para que el azimut corresponda a la direccion en la que
 * apunta la CAMARA trasera con el telefono en vertical, que es lo que el usuario
 * entiende como "hacia delante". Ese remapeo usa los ejes del cuerpo del
 * telefono, asi que no depende de la rotacion de la pantalla.
 */
class ProveedorPoseSensores(
    contexto: Context,
    private val zancadaMetros: Double = 0.70,
) : ProveedorPose, SensorEventListener {

    private val gestorSensores =
        contexto.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val sensorRotacion: Sensor? = gestorSensores.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val sensorPasos: Sensor? = gestorSensores.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

    private val _pose = MutableStateFlow<PoseMapa?>(null)
    override val pose: StateFlow<PoseMapa?> = _pose.asStateFlow()

    override val descripcion: String = "Brújula + pasos"

    /** true si el telefono tiene los sensores necesarios para este modo. */
    val hayBrujula: Boolean get() = sensorRotacion != null
    val hayContadorDePasos: Boolean get() = sensorPasos != null

    private val matrizRotacion = FloatArray(9)
    private val matrizRemapeada = FloatArray(9)
    private val orientacion = FloatArray(3)

    private var pisoId: String? = null
    private var rumboNorteGrados: Double = 0.0
    /** Declinacion magnetica del lugar, si se conoce (grados). */
    var declinacionGrados: Double = 0.0

    private var posicion: Punto2D? = null
    private var rumboMapa: Double = 0.0
    private var registrado = false

    fun configurarPiso(pisoId: String, rumboNorteGrados: Double) {
        this.pisoId = pisoId
        this.rumboNorteGrados = rumboNorteGrados
    }

    override fun fijarReferencia(poseConocida: PoseMapa) {
        pisoId = poseConocida.pisoId
        posicion = poseConocida.posicion
        rumboMapa = poseConocida.rumboGrados
        _pose.value = poseConocida
    }

    override fun iniciar() {
        if (registrado) return
        sensorRotacion?.let {
            gestorSensores.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        sensorPasos?.let {
            gestorSensores.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
        registrado = true
    }

    override fun detener() {
        if (!registrado) return
        gestorSensores.unregisterListener(this)
        registrado = false
    }

    override fun onSensorChanged(evento: SensorEvent) {
        when (evento.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> actualizarRumbo(evento)
            Sensor.TYPE_STEP_DETECTOR -> darPaso()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, precision: Int) = Unit

    private fun actualizarRumbo(evento: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(matrizRotacion, evento.values)
        SensorManager.remapCoordinateSystem(
            matrizRotacion,
            SensorManager.AXIS_X,
            SensorManager.AXIS_Z,
            matrizRemapeada,
        )
        SensorManager.getOrientation(matrizRemapeada, orientacion)
        val azimut = Geometria.normalizar360(
            Math.toDegrees(orientacion[0].toDouble()) + declinacionGrados,
        )
        rumboMapa = Geometria.rumboMapaDesdeAzimut(azimut, rumboNorteGrados)
        emitir()
    }

    private fun darPaso() {
        val actual = posicion ?: return
        val radianes = Math.toRadians(rumboMapa)
        posicion = Punto2D(
            x = actual.x + sin(radianes) * zancadaMetros,
            y = actual.y + cos(radianes) * zancadaMetros,
        )
        emitir()
    }

    private fun emitir() {
        val piso = pisoId ?: return
        val actual = posicion ?: return
        _pose.value = PoseMapa(
            pisoId = piso,
            posicion = actual,
            rumboGrados = rumboMapa,
            precisionMetros = FuentePosicion.PASOS_BRUJULA.precisionTipicaMetros,
            fuente = FuentePosicion.PASOS_BRUJULA,
            marcaTiempoMs = System.currentTimeMillis(),
        )
    }
}
