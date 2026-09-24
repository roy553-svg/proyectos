package com.rumbo.ar.datos

import android.content.Context
import com.rumbo.nucleo.datos.AlmacenDirectorio
import com.rumbo.nucleo.datos.RepositorioPlazasJson
import java.io.File

/**
 * Almacenamiento local de las plazas: archivos JSON en el directorio privado de
 * la app ([Context.getFilesDir]/plazas). No hace falta permiso alguno y los
 * datos sobreviven al cierre de la app.
 *
 * Para pasar a un backend remoto basta con implementar
 * `com.rumbo.nucleo.datos.RepositorioPlazas` y devolverlo aqui.
 */
object FabricaRepositorio {

    @Volatile
    private var instancia: RepositorioPlazasJson? = null

    fun obtener(contexto: Context): RepositorioPlazasJson =
        instancia ?: synchronized(this) {
            instancia ?: crear(contexto).also { instancia = it }
        }

    private fun crear(contexto: Context): RepositorioPlazasJson {
        val directorio = File(contexto.filesDir, "plazas")
        val repositorio = RepositorioPlazasJson(AlmacenDirectorio(directorio))
        // Primer arranque: se copian las plazas de demostracion.
        repositorio.sembrarSiHaceFalta()
        return repositorio
    }
}
