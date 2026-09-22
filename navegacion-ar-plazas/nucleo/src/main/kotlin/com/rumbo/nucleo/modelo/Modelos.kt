package com.rumbo.nucleo.modelo

import kotlinx.serialization.Serializable

/** Categorias con las que se agrupan los establecimientos en la UI. */
@Serializable
enum class Categoria(val etiqueta: String, val plural: String) {
    TIENDA("Tienda", "Tiendas"),
    RESTAURANTE("Restaurante", "Restaurantes"),
    BANO("Baño", "Baños"),
    FARMACIA("Farmacia", "Farmacias"),
    CAJERO("Cajero", "Cajeros"),
    ASCENSOR("Ascensor", "Ascensores"),
    ESCALERA("Escalera", "Escaleras"),
    ENTRADA("Entrada", "Entradas"),
    SALIDA("Salida", "Salidas"),
    SERVICIO("Servicio", "Servicios"),
}

@Serializable
enum class TipoNodo {
    /** Nodo de pasillo o cruce. */
    PASILLO,

    /** Nodo pegado a la puerta de un establecimiento. */
    ACCESO,

    /** Nodo de ascensor (se conecta verticalmente con otros pisos). */
    ASCENSOR,

    /** Nodo de escalera (se conecta verticalmente con otros pisos). */
    ESCALERA,

    /** Entrada / salida del edificio. */
    ENTRADA,
}

@Serializable
enum class TipoArista {
    /** Tramo de pasillo en el mismo piso. */
    PASILLO,

    /** Enlace vertical mediante ascensor. */
    ASCENSOR,

    /** Enlace vertical mediante escalera. */
    ESCALERA,
}

/**
 * Un piso de la plaza. El plano se describe con geometria (pasillos y locales en
 * metros) para que el mapa 2D y el editor funcionen sin depender de una imagen.
 * [planoImagen] es opcional: si esta presente se dibuja como fondo.
 */
@Serializable
data class Piso(
    val id: String,
    val nivel: Int,
    val nombre: String,
    val anchoMetros: Double,
    val altoMetros: Double,
    /** Azimut de brujula (0 = norte) al que apunta el eje +Y del plano. */
    val rumboNorteGrados: Double = 0.0,
    /** Ruta local o URI de una imagen de plano opcional. */
    val planoImagen: String? = null,
    val pasillos: List<Rectangulo> = emptyList(),
    val locales: List<Rectangulo> = emptyList(),
)

@Serializable
data class Establecimiento(
    val id: String,
    val nombre: String,
    val categoria: Categoria,
    val pisoId: String,
    val posicion: Punto2D,
    /** Nodo del grafo por el que se entra al establecimiento. */
    val nodoDestinoId: String,
    val activo: Boolean = true,
    val descripcion: String? = null,
)

@Serializable
data class Nodo(
    val id: String,
    val pisoId: String,
    val posicion: Punto2D,
    val tipo: TipoNodo = TipoNodo.PASILLO,
    val nombre: String? = null,
)

@Serializable
data class Arista(
    val desdeId: String,
    val hastaId: String,
    val tipo: TipoArista = TipoArista.PASILLO,
    val bidireccional: Boolean = true,
    /**
     * Coste adicional en "metros equivalentes". Se usa para penalizar enlaces
     * verticales (esperar el ascensor, subir escaleras) frente a caminar recto.
     */
    val costeExtraMetros: Double = 0.0,
)

/**
 * Punto QR fisico pegado en la plaza. Fija la posicion Y la orientacion del
 * usuario, que es lo que necesita ARCore para alinear su marco con el mapa.
 */
@Serializable
data class PuntoQr(
    val codigo: String,
    val plazaId: String,
    val pisoId: String,
    val nodoId: String,
    val posicion: Punto2D,
    /** Rumbo del mapa al que mira el usuario cuando lee el QR de frente. */
    val rumboGrados: Double,
    val descripcion: String = "",
)

@Serializable
data class Plaza(
    val id: String,
    val nombre: String,
    val ciudad: String = "",
    /** Marca explicita de que los datos son de demostracion, no de un centro real. */
    val ficticia: Boolean = true,
    val pisos: List<Piso> = emptyList(),
    val establecimientos: List<Establecimiento> = emptyList(),
    val nodos: List<Nodo> = emptyList(),
    val aristas: List<Arista> = emptyList(),
    val puntosQr: List<PuntoQr> = emptyList(),
) {
    fun piso(id: String): Piso? = pisos.firstOrNull { it.id == id }
    fun nodo(id: String): Nodo? = nodos.firstOrNull { it.id == id }
    fun establecimiento(id: String): Establecimiento? = establecimientos.firstOrNull { it.id == id }

    fun categoriasDisponibles(): List<Categoria> =
        establecimientos.filter { it.activo }
            .map { it.categoria }
            .distinct()
            .sortedBy { it.ordinal }

    fun establecimientosDe(categoria: Categoria): List<Establecimiento> =
        establecimientos.filter { it.activo && it.categoria == categoria }
            .sortedBy { it.nombre }
}

/** Resultado de validar una plaza antes de usarla para navegar. */
data class ResultadoValidacion(val errores: List<String>, val avisos: List<String>) {
    val valida: Boolean get() = errores.isEmpty()
}
