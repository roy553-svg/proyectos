package com.rumbo.ar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.ar.core.Frame
import com.rumbo.ar.ar.DisponibilidadAr
import com.rumbo.ar.ar.EstadoAr
import com.rumbo.ar.datos.FabricaRepositorio
import com.rumbo.ar.posicionamiento.EstadoSeguimientoAr
import com.rumbo.ar.posicionamiento.ProveedorPose
import com.rumbo.ar.posicionamiento.ProveedorPoseArCore
import com.rumbo.ar.posicionamiento.ProveedorPoseSensores
import com.rumbo.nucleo.grafo.AEstrella
import com.rumbo.nucleo.grafo.GrafoNavegacion
import com.rumbo.nucleo.modelo.Establecimiento
import com.rumbo.nucleo.modelo.Geometria
import com.rumbo.nucleo.modelo.Plaza
import com.rumbo.nucleo.modelo.Punto2D
import com.rumbo.nucleo.modelo.TipoNodo
import com.rumbo.nucleo.navegacion.EstadoNavegacion
import com.rumbo.nucleo.navegacion.Fase
import com.rumbo.nucleo.navegacion.MotorNavegacion
import com.rumbo.nucleo.posicionamiento.AlineacionAr
import com.rumbo.nucleo.posicionamiento.FuentePosicion
import com.rumbo.nucleo.posicionamiento.PoseMapa
import com.rumbo.nucleo.qr.PayloadQr
import com.rumbo.nucleo.ruta.Ruta
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Como se esta obteniendo la posicion durante la navegacion. */
enum class ModoNavegacion {
    /** ARCore disponible: camara + odometria visual-inercial. */
    AR,

    /** Sin ARCore: camara simple + brujula y pasos. */
    SENSORES,

    /** Sin camara ni sensores utiles: solo mapa 2D. */
    SOLO_MAPA,
}

data class EstadoUi(
    val plaza: Plaza? = null,
    val destino: Establecimiento? = null,
    val navegacion: EstadoNavegacion = EstadoNavegacion(Fase.SIN_POSICION),
    val pose: PoseMapa? = null,
    val modo: ModoNavegacion = ModoNavegacion.SOLO_MAPA,
    val estadoAr: EstadoAr = EstadoAr.COMPROBANDO,
    val seguimientoAr: EstadoSeguimientoAr = EstadoSeguimientoAr(),
    val mensaje: String? = null,
) {
    val necesitaPosicionInicial: Boolean get() = pose == null
}

/**
 * Orquesta la experiencia: plaza -> destino -> posicion inicial -> ruta -> flecha.
 *
 * La decision de la ruta es siempre del [MotorNavegacion] (mapa + grafo + A*).
 * La camara y los sensores solo aportan la pose del usuario.
 */
class NavegacionViewModel(aplicacion: Application) : AndroidViewModel(aplicacion) {

    private val repositorio = FabricaRepositorio.obtener(aplicacion)

    private val _plazas = MutableStateFlow(repositorio.listar())
    val plazas: StateFlow<List<Plaza>> = _plazas.asStateFlow()

    private val _estado = MutableStateFlow(EstadoUi())
    val estado: StateFlow<EstadoUi> = _estado.asStateFlow()

    val proveedorArCore = ProveedorPoseArCore()
    private val proveedorSensores by lazy { ProveedorPoseSensores(aplicacion) }

    private var motor: MotorNavegacion? = null
    private var grafo: GrafoNavegacion? = null
    private var trabajoPose: Job? = null
    private var trabajoSeguimiento: Job? = null

    /** Ruta ya calculada, para dibujarla en el mapa 2D. */
    val ruta: Ruta? get() = motor?.ruta

    val alineacionAr: AlineacionAr? get() = proveedorArCore.alineacion

    fun recargarPlazas() {
        _plazas.value = repositorio.listar()
    }

    fun comprobarSoporteAr() {
        val estadoAr = DisponibilidadAr.comprobar(getApplication())
        _estado.update { it.copy(estadoAr = estadoAr) }
    }

    fun seleccionarPlaza(plazaId: String) {
        val plaza = repositorio.obtener(plazaId) ?: return
        grafo = GrafoNavegacion.construir(plaza)
        motor = null
        _estado.value = EstadoUi(
            plaza = plaza,
            estadoAr = _estado.value.estadoAr,
        )
    }

    fun seleccionarDestino(establecimientoId: String) {
        val plaza = _estado.value.plaza ?: return
        val destino = plaza.establecimiento(establecimientoId) ?: return
        motor = MotorNavegacion(plaza, destino, grafo = grafo ?: GrafoNavegacion.construir(plaza))
        _estado.update { it.copy(destino = destino, navegacion = EstadoNavegacion(Fase.SIN_POSICION)) }
        // Si ya hay una pose (por ejemplo de un QR anterior) se calcula ya la ruta.
        _estado.value.pose?.let { aplicarPose(it) }
    }

    /**
     * Distancia aproximada que se muestra antes de abrir la camara. Si todavia no
     * se conoce la posicion del usuario se mide desde la entrada principal.
     */
    fun distanciaEstimadaMetros(destino: Establecimiento): Double? {
        val plaza = _estado.value.plaza ?: return null
        val grafoActual = grafo ?: GrafoNavegacion.construir(plaza).also { grafo = it }
        val pose = _estado.value.pose
        val origen = if (pose != null) {
            grafoActual.nodoMasCercano(pose.pisoId, pose.posicion)
        } else {
            plaza.nodos.firstOrNull { it.tipo == TipoNodo.ENTRADA }
        } ?: return null
        val ruta = AEstrella.buscar(grafoActual, origen.id, destino.nodoDestinoId) ?: return null
        return ruta.longitudHorizontalMetros +
            (pose?.let { Geometria.distancia(it.posicion, origen.posicion) } ?: 0.0)
    }

    /** Decide el modo de navegacion y arranca el proveedor de posicion adecuado. */
    fun iniciarNavegacion() {
        val estadoAr = DisponibilidadAr.comprobar(getApplication())
        val modo = when (estadoAr) {
            EstadoAr.DISPONIBLE, EstadoAr.REQUIERE_INSTALACION -> ModoNavegacion.AR
            EstadoAr.COMPROBANDO -> ModoNavegacion.AR
            EstadoAr.NO_COMPATIBLE ->
                if (proveedorSensores.hayBrujula) ModoNavegacion.SENSORES else ModoNavegacion.SOLO_MAPA
        }
        _estado.update { it.copy(modo = modo, estadoAr = estadoAr) }
        escuchar(proveedorActivo())
    }

    /** Cambia a navegacion sin AR (fallo de ARCore, poca luz o eleccion del usuario). */
    fun cambiarASensores(motivo: String? = null) {
        val modo = if (proveedorSensores.hayBrujula) ModoNavegacion.SENSORES else ModoNavegacion.SOLO_MAPA
        proveedorArCore.detener()
        _estado.update { it.copy(modo = modo, mensaje = motivo) }
        val pose = _estado.value.pose
        val plaza = _estado.value.plaza
        if (pose != null && plaza != null) {
            proveedorSensores.configurarPiso(
                pose.pisoId,
                plaza.piso(pose.pisoId)?.rumboNorteGrados ?: 0.0,
            )
            proveedorSensores.fijarReferencia(pose)
        }
        escuchar(proveedorActivo())
    }

    private fun proveedorActivo(): ProveedorPose = when (_estado.value.modo) {
        ModoNavegacion.AR -> proveedorArCore
        else -> proveedorSensores
    }

    private fun escuchar(proveedor: ProveedorPose) {
        proveedor.iniciar()
        trabajoPose?.cancel()
        trabajoPose = viewModelScope.launch {
            proveedor.pose.collect { aplicarPose(it) }
        }
        if (proveedor === proveedorArCore) {
            trabajoSeguimiento?.cancel()
            trabajoSeguimiento = viewModelScope.launch {
                proveedorArCore.seguimiento.collect { seguimiento ->
                    _estado.update { it.copy(seguimientoAr = seguimiento) }
                }
            }
        }
    }

    /** Punto de partida conocido: QR, punto marcado en el mapa o cambio de piso. */
    fun fijarPoseInicial(pose: PoseMapa) {
        val plaza = _estado.value.plaza
        proveedorSensores.configurarPiso(pose.pisoId, plaza?.piso(pose.pisoId)?.rumboNorteGrados ?: 0.0)
        proveedorArCore.fijarReferencia(pose)
        proveedorSensores.fijarReferencia(pose)
        motor?.calcularRuta(pose)
        aplicarPose(pose)
    }

    /**
     * Procesa el texto de un QR. Devuelve un mensaje de error o null si todo fue bien.
     */
    fun fijarPoseDesdeQr(texto: String?): String? {
        val punto = PayloadQr.decodificar(texto)
            ?: return "Ese código QR no es de Rumbo"
        val plaza = _estado.value.plaza ?: return "Selecciona primero una plaza"
        if (punto.plazaId != plaza.id) return "Ese QR pertenece a otra plaza"
        if (plaza.piso(punto.pisoId) == null) return "El QR apunta a un piso que no existe en el mapa"
        fijarPoseInicial(
            PoseMapa(
                pisoId = punto.pisoId,
                posicion = punto.posicion,
                rumboGrados = punto.rumboGrados,
                precisionMetros = FuentePosicion.QR.precisionTipicaMetros,
                fuente = FuentePosicion.QR,
                marcaTiempoMs = System.currentTimeMillis(),
            ),
        )
        return null
    }

    /** Posicion marcada a mano: un toque para el punto y otro para la direccion. */
    fun fijarPoseManual(pisoId: String, punto: Punto2D, puntoAlQueMira: Punto2D) {
        fijarPoseInicial(
            PoseMapa(
                pisoId = pisoId,
                posicion = punto,
                rumboGrados = Geometria.rumboEntre(punto, puntoAlQueMira),
                precisionMetros = FuentePosicion.MANUAL.precisionTipicaMetros,
                fuente = FuentePosicion.MANUAL,
                marcaTiempoMs = System.currentTimeMillis(),
            ),
        )
    }

    /**
     * Aviso de que va a arrancar una sesion AR nueva (entrada en la pantalla AR o
     * vuelta desde el mapa): hay que rehacer la alineacion con el mapa.
     */
    fun prepararSesionAr() {
        proveedorArCore.prepararNuevaSesion()
    }

    /** Se llama desde el hilo de render de ARCore. */
    fun procesarFrame(frame: Frame) {
        proveedorArCore.procesarFrame(frame)
    }

    /** El usuario confirma que ya subio al piso indicado por el ascensor o la escalera. */
    fun confirmarCambioDePiso() {
        val rumbo = _estado.value.pose?.rumboGrados ?: 0.0
        val nueva = motor?.poseTrasCambioDePiso(rumbo) ?: return
        fijarPoseInicial(nueva)
    }

    fun mensajeMostrado() {
        _estado.update { it.copy(mensaje = null) }
    }

    fun avisarErrorAr(mensaje: String) {
        cambiarASensores(mensaje)
    }

    fun terminarNavegacion() {
        trabajoPose?.cancel()
        trabajoSeguimiento?.cancel()
        proveedorArCore.detener()
        proveedorSensores.detener()
        motor = null
        _estado.update {
            it.copy(
                destino = null,
                pose = null,
                navegacion = EstadoNavegacion(Fase.SIN_POSICION),
                seguimientoAr = EstadoSeguimientoAr(),
            )
        }
    }

    private fun aplicarPose(pose: PoseMapa?) {
        val motorActual = motor
        if (motorActual == null) {
            _estado.update { it.copy(pose = pose) }
            return
        }
        val nuevo = motorActual.actualizar(pose)
        _estado.update { it.copy(pose = pose, navegacion = nuevo) }
    }

    override fun onCleared() {
        super.onCleared()
        proveedorSensores.detener()
        proveedorArCore.detener()
    }
}
