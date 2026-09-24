package com.rumbo.ar.ui.pantallas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rumbo.ar.ui.componentes.BarraSuperior
import com.rumbo.ar.ui.componentes.Cabecera
import com.rumbo.ar.ui.componentes.TarjetaOpcion
import com.rumbo.ar.ui.tema.Espacio
import com.rumbo.nucleo.modelo.Categoria
import com.rumbo.nucleo.modelo.Plaza

/** Segundo paso: que tipo de lugar busca el usuario. Las categorias salen de los datos. */
@Composable
fun PantallaCategorias(
    plaza: Plaza,
    alElegirCategoria: (Categoria) -> Unit,
    alVolver: () -> Unit,
) {
    Scaffold(
        topBar = { BarraSuperior(titulo = plaza.nombre, alVolver = alVolver) },
    ) { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno),
        ) {
            Cabecera(titulo = "¿A dónde quieres ir?")
            LazyColumn(
                contentPadding = PaddingValues(Espacio.m),
                verticalArrangement = Arrangement.spacedBy(Espacio.s),
            ) {
                items(plaza.categoriasDisponibles(), key = { it.name }) { categoria ->
                    val cuantos = plaza.establecimientosDe(categoria).size
                    TarjetaOpcion(
                        titulo = categoria.plural,
                        detalle = "$cuantos ${if (cuantos == 1) "lugar" else "lugares"}",
                        inicial = categoria.plural,
                        alPulsar = { alElegirCategoria(categoria) },
                    )
                }
            }
        }
    }
}
