package com.rumbo.nucleo.datos

import com.rumbo.nucleo.modelo.Plaza
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Acceso a los datos de plazas. La app usa siempre esta interfaz, asi que el
 * almacenamiento local de hoy (archivos JSON) se puede sustituir manana por un
 * backend remoto sin tocar la navegacion.
 */
interface RepositorioPlazas {
    fun listar(): List<Plaza>
    fun obtener(id: String): Plaza?
    fun guardar(plaza: Plaza)
    fun eliminar(id: String)
}

val JsonPlazas: Json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** Abstraccion minima de almacenamiento, para poder testear sin disco real. */
interface AlmacenArchivos {
    fun nombres(): List<String>
    fun leer(nombre: String): String?
    fun escribir(nombre: String, contenido: String)
    fun borrar(nombre: String)
}

/** Almacen en un directorio del sistema de archivos (en Android: `context.filesDir`). */
class AlmacenDirectorio(private val directorio: File) : AlmacenArchivos {
    init {
        if (!directorio.exists()) directorio.mkdirs()
    }

    override fun nombres(): List<String> =
        directorio.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.map { it.name }
            ?.sorted()
            .orEmpty()

    override fun leer(nombre: String): String? =
        File(directorio, nombre).takeIf { it.exists() }?.readText()

    override fun escribir(nombre: String, contenido: String) {
        File(directorio, nombre).writeText(contenido)
    }

    override fun borrar(nombre: String) {
        File(directorio, nombre).delete()
    }
}

/** Almacen en memoria, util para tests y para previsualizaciones de la UI. */
class AlmacenMemoria(inicial: Map<String, String> = emptyMap()) : AlmacenArchivos {
    private val datos = LinkedHashMap<String, String>(inicial)
    override fun nombres(): List<String> = datos.keys.toList()
    override fun leer(nombre: String): String? = datos[nombre]
    override fun escribir(nombre: String, contenido: String) {
        datos[nombre] = contenido
    }

    override fun borrar(nombre: String) {
        datos.remove(nombre)
    }
}

/**
 * Repositorio de plazas sobre JSON. En el primer arranque copia las plazas de
 * demostracion incluidas en los recursos ([SemillaPlazas]).
 */
class RepositorioPlazasJson(
    private val almacen: AlmacenArchivos,
    private val semillas: List<Plaza> = SemillaPlazas.cargarTodas(),
) : RepositorioPlazas {

    private fun archivoDe(id: String) = "plaza_$id.json"

    /** Escribe las plazas de demostracion si el almacen esta vacio. */
    fun sembrarSiHaceFalta(): Int {
        if (almacen.nombres().isNotEmpty()) return 0
        semillas.forEach { guardar(it) }
        return semillas.size
    }

    override fun listar(): List<Plaza> = almacen.nombres().mapNotNull { nombre ->
        runCatching { JsonPlazas.decodeFromString<Plaza>(almacen.leer(nombre) ?: return@runCatching null) }
            .getOrNull()
    }.sortedBy { it.nombre }

    override fun obtener(id: String): Plaza? =
        almacen.leer(archivoDe(id))?.let { runCatching { JsonPlazas.decodeFromString<Plaza>(it) }.getOrNull() }
            ?: listar().firstOrNull { it.id == id }

    override fun guardar(plaza: Plaza) {
        almacen.escribir(archivoDe(plaza.id), JsonPlazas.encodeToString(plaza))
    }

    override fun eliminar(id: String) {
        almacen.borrar(archivoDe(id))
    }
}

/**
 * Plazas de demostracion incluidas como recurso del modulo :nucleo.
 * Son datos FICTICIOS; no representan ningun centro comercial real.
 */
object SemillaPlazas {

    val RUTAS_RECURSOS = listOf("/plazas/plaza_aurora.json")

    fun cargarTodas(): List<Plaza> = RUTAS_RECURSOS.mapNotNull { cargar(it) }

    fun cargar(ruta: String): Plaza? {
        val texto = SemillaPlazas::class.java.getResourceAsStream(ruta)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: return null
        return runCatching { JsonPlazas.decodeFromString<Plaza>(texto) }.getOrNull()
    }
}
