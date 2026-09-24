package com.rumbo.ar.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.rumbo.ar.ui.componentes.Aviso
import com.rumbo.ar.ui.componentes.BarraSuperior
import com.rumbo.ar.ui.tema.Espacio
import com.rumbo.nucleo.modelo.Plaza

/** Lista de plazas del administrador: crear, abrir el editor o borrar. */
@Composable
fun PantallaAdminPlazas(
    plazas: List<Plaza>,
    alAbrirEditor: (String) -> Unit,
    alCrear: (String, String, Double, Double) -> Unit,
    alEliminar: (String) -> Unit,
    alVolver: () -> Unit,
) {
    var dialogoAbierto by remember { mutableStateOf(false) }
    var plazaAEliminar by remember { mutableStateOf<Plaza?>(null) }

    Scaffold(
        topBar = { BarraSuperior(titulo = "Administración", alVolver = alVolver) },
        floatingActionButton = {
            FloatingActionButton(onClick = { dialogoAbierto = true }) {
                Icon(Icons.Default.Add, contentDescription = "Nueva plaza")
            }
        },
    ) { relleno ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno),
            contentPadding = PaddingValues(Espacio.m),
            verticalArrangement = Arrangement.spacedBy(Espacio.s),
        ) {
            item {
                Aviso(
                    "Los mapas se guardan en este teléfono. Crea una plaza, añade pisos y " +
                        "coloca los lugares y los nodos de navegación en el editor.",
                )
            }
            items(plazas, key = { it.id }) { plaza ->
                Card(
                    onClick = { alAbrirEditor(plaza.id) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(Espacio.m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(plaza.nombre, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${plaza.pisos.size} pisos · ${plaza.establecimientos.size} lugares · " +
                                    "${plaza.nodos.size} nodos · ${plaza.puntosQr.size} QR",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { plazaAEliminar = plaza }) {
                            Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                        }
                    }
                }
            }
        }
    }

    if (dialogoAbierto) {
        DialogoNuevaPlaza(
            alCancelar = { dialogoAbierto = false },
            alCrear = { nombre, ciudad, ancho, alto ->
                alCrear(nombre, ciudad, ancho, alto)
                dialogoAbierto = false
            },
        )
    }

    plazaAEliminar?.let { plaza ->
        AlertDialog(
            onDismissRequest = { plazaAEliminar = null },
            title = { Text("¿Eliminar ${plaza.nombre}?") },
            text = { Text("Se borrarán sus pisos, lugares, nodos y puntos QR de este teléfono.") },
            confirmButton = {
                TextButton(onClick = {
                    alEliminar(plaza.id)
                    plazaAEliminar = null
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { plazaAEliminar = null }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun DialogoNuevaPlaza(
    alCancelar: () -> Unit,
    alCrear: (String, String, Double, Double) -> Unit,
) {
    var nombre by remember { mutableStateOf("") }
    var ciudad by remember { mutableStateOf("") }
    var ancho by remember { mutableStateOf("80") }
    var alto by remember { mutableStateOf("50") }

    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text("Nueva plaza") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = ciudad,
                    onValueChange = { ciudad = it },
                    label = { Text("Ciudad") },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
                    OutlinedTextField(
                        value = ancho,
                        onValueChange = { ancho = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Ancho (m)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = alto,
                        onValueChange = { alto = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Alto (m)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    text = "El ancho y el alto son las medidas reales del piso 1 en metros. " +
                        "Todo el sistema trabaja en metros.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = nombre.isNotBlank(),
                onClick = {
                    alCrear(
                        nombre,
                        ciudad,
                        ancho.toDoubleOrNull() ?: 80.0,
                        alto.toDoubleOrNull() ?: 50.0,
                    )
                },
            ) { Text("Crear") }
        },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar") } },
    )
}
