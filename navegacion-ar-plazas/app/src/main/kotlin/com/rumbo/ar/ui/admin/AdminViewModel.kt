package com.rumbo.ar.ui.admin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.rumbo.ar.datos.FabricaRepositorio
import com.rumbo.nucleo.grafo.GrafoNavegacion
import com.rumbo.nucleo.modelo.Arista
import com.rumbo.nucleo.modelo.Categoria
import com.rumbo.nucleo.modelo.Establecimiento
import com.rumbo.nucleo.modelo.Geometria
import com.rumbo.nucleo.modelo.Nodo
import com.rumbo.nucleo.modelo.Piso
import com.rumbo.nucleo.modelo.Plaza
import com.rumbo.nucleo.modelo.Punto2D
import com.rumbo.nucleo.modelo.PuntoQr
import com.rumbo.nucleo.modelo.Rectangulo
import com.rumbo.nucleo.modelo.ResultadoValidacion
import com.rumbo.nucleo.modelo.TipoArista
import com.rumbo.nucleo.modelo.TipoNodo
import java.text.Normalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Estado del panel de administracion. Todas las operaciones trabajan sobre una
 * copia en memoria de la plaza y solo se escriben en disco al guardar, asi que
 * una edicion a medias nunca deja el mapa inconsistente.
 */
class AdminViewModel(aplicacion: Application) : AndroidViewModel(aplicacion) {

    private val repositorio = FabricaRepositorio.obtener(aplicacion)

    private val _plazas = MutableStateFlow(repositorio.listar())
    val plazas: StateFlow<List<Plaza>> = _plazas.asStateFlow()

    private val _plaza = MutableStateFlow<Plaza?>(null)
    val plaza: StateFlow<Plaza?> = _plaza.asStateFlow()

    private val _validacion = MutableStateFlow<ResultadoValidacion?>(null)
    val validacion: StateFlow<ResultadoValidacion?> = _validacion.asStateFlow()

    private val _cambiosSinGuardar = MutableStateFlow(false)
    val cambiosSinGuardar: StateFlow<Boolean> = _cambiosSinGuardar.asStateFlow()

    fun recargar() {
        _plazas.value = repositorio.listar()
    }

    fun abrir(plazaId: String) {
        _plaza.value = repositorio.obtener(plazaId)
        _cambiosSinGuardar.value = false
        validar()
    }

    fun cerrar() {
        _plaza.value = null
        _validacion.value = null
        _cambiosSinGuardar.value = false
    }

    fun crearPlaza(nombre: String, ciudad: String, anchoMetros: Double, altoMetros: Double): String {
        val id = idUnico("plaza_${slug(nombre)}", _plazas.value.map { it.id })
        val plaza = Plaza(
            id = id,
            nombre = nombre.trim().ifBlank { "Plaza sin nombre" },
            ciudad = ciudad.trim(),
            ficticia = true,
            pisos = listOf(
                Piso(
                    id = "p1",
                    nivel = 1,
                    nombre = "Piso 1",
                    anchoMetros = anchoMetros,
                    altoMetros = altoMetros,
                ),
            ),
        )
        repositorio.guardar(plaza)
        recargar()
        return id
    }

    fun eliminarPlaza(plazaId: String) {
        repositorio.eliminar(plazaId)
        if (_plaza.value?.id == plazaId) cerrar()
        recargar()
    }

    fun agregarPiso(nombre: String, anchoMetros: Double, altoMetros: Double, rumboNorteGrados: Double) {
        modificar { plaza ->
            val nivel = (plaza.pisos.maxOfOrNull { it.nivel } ?: 0) + 1
            plaza.copy(
                pisos = plaza.pisos + Piso(
                    id = idUnico("p$nivel", plaza.pisos.map { it.id }),
                    nivel = nivel,
                    nombre = nombre.ifBlank { "Piso $nivel" },
                    anchoMetros = anchoMetros,
                    altoMetros = altoMetros,
                    rumboNorteGrados = rumboNorteGrados,
                ),
            )
        }
    }

    fun ajustarRumboNorte(pisoId: String, grados: Double) {
        modificar { plaza ->
            plaza.copy(
                pisos = plaza.pisos.map {
                    if (it.id == pisoId) it.copy(rumboNorteGrados = Geometria.normalizar360(grados)) else it
                },
            )
        }
    }

    /** Pasillo rectangular: ayuda visual para el plano, no afecta a la ruta. */
    fun agregarPasillo(pisoId: String, desde: Punto2D, hasta: Punto2D) {
        modificar { plaza ->
            val rectangulo = rectanguloEntre(desde, hasta, "Pasillo")
            plaza.copy(
                pisos = plaza.pisos.map {
                    if (it.id == pisoId) it.copy(pasillos = it.pasillos + rectangulo) else it
                },
            )
        }
    }

    /**
     * Coloca un establecimiento: crea su local en el plano, un nodo de acceso y
     * lo conecta con el nodo mas cercano del piso (normalmente el pasillo).
     */
    fun colocarEstablecimiento(
        pisoId: String,
        punto: Punto2D,
        nombre: String,
        categoria: Categoria,
        anchoLocalMetros: Double = 8.0,
        altoLocalMetros: Double = 8.0,
    ) {
        modificar { plaza ->
            val idEstablecimiento = idUnico("est_${slug(nombre)}", plaza.establecimientos.map { it.id })
            val idNodo = idUnico("${pisoId.uppercase()}_A_${slug(nombre).take(14)}", plaza.nodos.map { it.id })
            val nodoAcceso = Nodo(
                id = idNodo,
                pisoId = pisoId,
                posicion = punto,
                tipo = when (categoria) {
                    Categoria.ASCENSOR -> TipoNodo.ASCENSOR
                    Categoria.ESCALERA -> TipoNodo.ESCALERA
                    Categoria.ENTRADA, Categoria.SALIDA -> TipoNodo.ENTRADA
                    else -> TipoNodo.ACCESO
                },
                nombre = nombre,
            )
            val cercano = plaza.nodos.filter { it.pisoId == pisoId }
                .minByOrNull { Geometria.distancia(it.posicion, punto) }
            val local = Rectangulo(
                x = punto.x - anchoLocalMetros / 2,
                y = punto.y,
                ancho = anchoLocalMetros,
                alto = altoLocalMetros,
                etiqueta = nombre,
            )
            plaza.copy(
                pisos = plaza.pisos.map {
                    if (it.id == pisoId && categoria !in setOf(Categoria.ENTRADA, Categoria.SALIDA)) {
                        it.copy(locales = it.locales + local)
                    } else {
                        it
                    }
                },
                nodos = plaza.nodos + nodoAcceso,
                aristas = if (cercano != null) plaza.aristas + Arista(idNodo, cercano.id) else plaza.aristas,
                establecimientos = plaza.establecimientos + Establecimiento(
                    id = idEstablecimiento,
                    nombre = nombre,
                    categoria = categoria,
                    pisoId = pisoId,
                    posicion = Punto2D(punto.x, punto.y + altoLocalMetros / 2),
                    nodoDestinoId = idNodo,
                ),
            )
        }
    }

    fun colocarNodo(pisoId: String, punto: Punto2D, tipo: TipoNodo = TipoNodo.PASILLO, conectarAlMasCercano: Boolean = true) {
        modificar { plaza ->
            val prefijo = pisoId.uppercase()
            val id = idUnico("${prefijo}_N${plaza.nodos.count { it.pisoId == pisoId } + 1}", plaza.nodos.map { it.id })
            val cercano = plaza.nodos.filter { it.pisoId == pisoId }
                .minByOrNull { Geometria.distancia(it.posicion, punto) }
            plaza.copy(
                nodos = plaza.nodos + Nodo(id = id, pisoId = pisoId, posicion = punto, tipo = tipo),
                aristas = if (conectarAlMasCercano && cercano != null) {
                    plaza.aristas + Arista(id, cercano.id)
                } else {
                    plaza.aristas
                },
            )
        }
    }

    fun conectar(nodoA: String, nodoB: String, tipo: TipoArista = TipoArista.PASILLO, costeExtraMetros: Double = 0.0) {
        if (nodoA == nodoB) return
        modificar { plaza ->
            val yaExiste = plaza.aristas.any {
                (it.desdeId == nodoA && it.hastaId == nodoB) || (it.desdeId == nodoB && it.hastaId == nodoA)
            }
            if (yaExiste) plaza else plaza.copy(
                aristas = plaza.aristas + Arista(nodoA, nodoB, tipo, costeExtraMetros = costeExtraMetros),
            )
        }
    }

    fun desconectar(nodoA: String, nodoB: String) {
        modificar { plaza ->
            plaza.copy(
                aristas = plaza.aristas.filterNot {
                    (it.desdeId == nodoA && it.hastaId == nodoB) || (it.desdeId == nodoB && it.hastaId == nodoA)
                },
            )
        }
    }

    fun colocarQr(pisoId: String, nodoId: String, rumboGrados: Double, descripcion: String) {
        modificar { plaza ->
            val nodo = plaza.nodo(nodoId) ?: return@modificar plaza
            val codigo = idUnico(
                "QR_${(plaza.puntosQr.size + 1).toString().padStart(3, '0')}",
                plaza.puntosQr.map { it.codigo },
            )
            plaza.copy(
                puntosQr = plaza.puntosQr + PuntoQr(
                    codigo = codigo,
                    plazaId = plaza.id,
                    pisoId = pisoId,
                    nodoId = nodoId,
                    posicion = nodo.posicion,
                    rumboGrados = Geometria.normalizar360(rumboGrados),
                    descripcion = descripcion,
                ),
            )
        }
    }

    fun eliminarNodo(nodoId: String) {
        modificar { plaza ->
            plaza.copy(
                nodos = plaza.nodos.filterNot { it.id == nodoId },
                aristas = plaza.aristas.filterNot { it.desdeId == nodoId || it.hastaId == nodoId },
                establecimientos = plaza.establecimientos.filterNot { it.nodoDestinoId == nodoId },
                puntosQr = plaza.puntosQr.filterNot { it.nodoId == nodoId },
            )
        }
    }

    fun activarEstablecimiento(establecimientoId: String, activo: Boolean) {
        modificar { plaza ->
            plaza.copy(
                establecimientos = plaza.establecimientos.map {
                    if (it.id == establecimientoId) it.copy(activo = activo) else it
                },
            )
        }
    }

    fun eliminarEstablecimiento(establecimientoId: String) {
        modificar { plaza ->
            plaza.copy(establecimientos = plaza.establecimientos.filterNot { it.id == establecimientoId })
        }
    }

    fun nodoMasCercano(pisoId: String, punto: Punto2D, radioMetros: Double = 6.0): Nodo? =
        _plaza.value?.nodos
            ?.filter { it.pisoId == pisoId }
            ?.minByOrNull { Geometria.distancia(it.posicion, punto) }
            ?.takeIf { Geometria.distancia(it.posicion, punto) <= radioMetros }

    fun guardar(): Boolean {
        val plaza = _plaza.value ?: return false
        repositorio.guardar(plaza)
        _cambiosSinGuardar.value = false
        recargar()
        validar()
        return true
    }

    fun validar() {
        _validacion.value = _plaza.value?.let { GrafoNavegacion.validar(it) }
    }

    private fun modificar(bloque: (Plaza) -> Plaza) {
        val actual = _plaza.value ?: return
        _plaza.value = bloque(actual)
        _cambiosSinGuardar.value = true
        validar()
    }

    private fun rectanguloEntre(a: Punto2D, b: Punto2D, etiqueta: String) = Rectangulo(
        x = minOf(a.x, b.x),
        y = minOf(a.y, b.y),
        ancho = kotlin.math.abs(b.x - a.x).coerceAtLeast(1.0),
        alto = kotlin.math.abs(b.y - a.y).coerceAtLeast(1.0),
        etiqueta = etiqueta,
    )

    private fun idUnico(base: String, existentes: List<String>): String {
        if (base !in existentes) return base
        var indice = 2
        while ("${base}_$indice" in existentes) indice++
        return "${base}_$indice"
    }

    private fun slug(texto: String): String =
        Normalizer.normalize(texto, Normalizer.Form.NFKD)
            .replace("\\p{M}".toRegex(), "")
            .lowercase()
            .map { if (it.isLetterOrDigit()) it else '_' }
            .joinToString("")
            .trim('_')
            .ifBlank { "sin_nombre" }
}
