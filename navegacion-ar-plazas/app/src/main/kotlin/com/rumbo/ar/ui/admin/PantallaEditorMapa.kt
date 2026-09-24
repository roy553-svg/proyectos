package com.rumbo.ar.ui.admin

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.rumbo.ar.ui.componentes.Aviso
import com.rumbo.ar.ui.componentes.BarraSuperior
import com.rumbo.ar.ui.componentes.MapaPiso
import com.rumbo.ar.ui.tema.ColoresRumbo
import com.rumbo.ar.ui.tema.Espacio
import com.rumbo.nucleo.modelo.Categoria
import com.rumbo.nucleo.modelo.Plaza
import com.rumbo.nucleo.modelo.Punto2D
import com.rumbo.nucleo.modelo.PuntoQr
import com.rumbo.nucleo.modelo.ResultadoValidacion
import com.rumbo.nucleo.modelo.TipoArista
import com.rumbo.nucleo.modelo.TipoNodo
import com.rumbo.nucleo.qr.PayloadQr

/** Herramienta activa del editor: que se coloca al tocar el plano. */
enum class Herramienta(val etiqueta: String, val categoria: Categoria? = null) {
    TIENDA("Tienda", Categoria.TIENDA),
    RESTAURANTE("Restaurante", Categoria.RESTAURANTE),
    BANO("Baño", Categoria.BANO),
    FARMACIA("Farmacia", Categoria.FARMACIA),
    CAJERO("Cajero", Categoria.CAJERO),
    ASCENSOR("Ascensor", Categoria.ASCENSOR),
    ESCALERA("Escalera", Categoria.ESCALERA),
    ENTRADA("Entrada", Categoria.ENTRADA),
    NODO("Nodo"),
    CONECTAR("Conectar"),
    QR("Punto QR"),
    BORRAR("Borrar"),
}

/**
 * Editor visual del mapa: se abre el plano de un piso y se toca para colocar
 * tiendas, baños, ascensores, escaleras, nodos de navegacion y puntos QR.
 *
 * Al colocar un establecimiento se crea automaticamente su nodo de acceso y se
 * conecta con el nodo mas cercano, que es el 90% del trabajo manual.
 */
@Composable
fun PantallaEditorMapa(
    plaza: Plaza,
    validacion: ResultadoValidacion?,
    cambiosSinGuardar: Boolean,
    alColocarEstablecimiento: (String, Punto2D, String, Categoria) -> Unit,
    alColocarNodo: (String, Punto2D, TipoNodo) -> Unit,
    alConectar: (String, String, TipoArista, Double) -> Unit,
    alColocarQr: (String, String, Double, String) -> Unit,
    alEliminarNodo: (String) -> Unit,
    alAgregarPiso: (String, Double, Double, Double) -> Unit,
    alAjustarNorte: (String, Double) -> Unit,
    alGuardar: () -> Unit,
    alVolver: () -> Unit,
) {
    var herramienta by remember { mutableStateOf(Herramienta.TIENDA) }
    var pisoSeleccionado by remember { mutableStateOf(plaza.pisos.firstOrNull()?.id) }
    var nodoSeleccionado by remember { mutableStateOf<String?>(null) }
    var puntoPendiente by remember { mutableStateOf<Punto2D?>(null) }
    var nodoParaQr by remember { mutableStateOf<String?>(null) }
    var qrMostrado by remember { mutableStateOf<PuntoQr?>(null) }
    var dialogoPiso by remember { mutableStateOf(false) }
    var dialogoNorte by remember { mutableStateOf(false) }

    val piso = plaza.pisos.firstOrNull { it.id == pisoSeleccionado } ?: plaza.pisos.firstOrNull()

    Scaffold(
        topBar = {
            BarraSuperior(
                titulo = plaza.nombre,
                alVolver = alVolver,
                acciones = {
                    IconButton(onClick = { dialogoPiso = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Nuevo piso")
                    }
                    IconButton(onClick = alGuardar, enabled = cambiosSinGuardar) {
                        Icon(Icons.Default.Check, contentDescription = "Guardar")
                    }
                },
            )
        },
    ) { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno)
                .padding(horizontal = Espacio.m),
            verticalArrangement = Arrangement.spacedBy(Espacio.s),
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Espacio.s),
            ) {
                plaza.pisos.sortedBy { it.nivel }.forEach { candidato ->
                    FilterChip(
                        selected = candidato.id == piso?.id,
                        onClick = { pisoSeleccionado = candidato.id },
                        label = { Text(candidato.nombre) },
                    )
                }
                piso?.let {
                    FilterChip(
                        selected = false,
                        onClick = { dialogoNorte = true },
                        label = { Text("Norte ${it.rumboNorteGrados.toInt()}°") },
                    )
                }
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
            ) {
                Herramienta.entries.forEach { opcion ->
                    FilterChip(
                        selected = opcion == herramienta,
                        onClick = {
                            herramienta = opcion
                            nodoSeleccionado = null
                        },
                        label = { Text(opcion.etiqueta) },
                    )
                }
            }

            if (piso == null) {
                Aviso("Crea un piso para empezar a dibujar el mapa.")
                return@Column
            }

            Text(
                text = ayudaDe(herramienta, nodoSeleccionado),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            MapaPiso(
                piso = piso,
                establecimientos = plaza.establecimientos,
                nodos = plaza.nodos,
                aristas = plaza.aristas,
                puntosQr = plaza.puntosQr,
                mostrarNodos = true,
                seleccionado = nodoSeleccionado,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                alTocar = { punto ->
                    when {
                        herramienta.categoria != null -> puntoPendiente = punto
                        herramienta == Herramienta.NODO ->
                            alColocarNodo(piso.id, punto, TipoNodo.PASILLO)

                        herramienta == Herramienta.CONECTAR -> {
                            val cercano = plaza.nodos
                                .filter { it.pisoId == piso.id }
                                .minByOrNull {
                                    com.rumbo.nucleo.modelo.Geometria.distancia(it.posicion, punto)
                                }
                            if (cercano != null) {
                                val anterior = nodoSeleccionado
                                if (anterior == null || anterior == cercano.id) {
                                    nodoSeleccionado = cercano.id
                                } else {
                                    val nodoA = plaza.nodo(anterior)
                                    val vertical = nodoA != null && nodoA.pisoId != cercano.pisoId
                                    val tipo = when {
                                        !vertical -> TipoArista.PASILLO
                                        nodoA?.tipo == TipoNodo.ESCALERA -> TipoArista.ESCALERA
                                        else -> TipoArista.ASCENSOR
                                    }
                                    val coste = when (tipo) {
                                        TipoArista.PASILLO -> 0.0
                                        TipoArista.ASCENSOR -> 18.0
                                        TipoArista.ESCALERA -> 26.0
                                    }
                                    alConectar(anterior, cercano.id, tipo, coste)
                                    nodoSeleccionado = cercano.id
                                }
                            }
                        }

                        herramienta == Herramienta.QR -> {
                            val cercano = plaza.nodos
                                .filter { it.pisoId == piso.id }
                                .minByOrNull {
                                    com.rumbo.nucleo.modelo.Geometria.distancia(it.posicion, punto)
                                }
                            nodoParaQr = cercano?.id
                        }

                        herramienta == Herramienta.BORRAR -> {
                            val cercano = plaza.nodos
                                .filter { it.pisoId == piso.id }
                                .minByOrNull {
                                    com.rumbo.nucleo.modelo.Geometria.distancia(it.posicion, punto)
                                }
                            cercano?.let { alEliminarNodo(it.id) }
                        }
                    }
                },
            )

            validacion?.let { resultado ->
                if (resultado.errores.isNotEmpty()) {
                    Aviso(
                        texto = "Errores: " + resultado.errores.take(3).joinToString(" · "),
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (resultado.avisos.isNotEmpty()) {
                    Aviso("Avisos: " + resultado.avisos.take(2).joinToString(" · "))
                } else {
                    Aviso("Mapa válido: todos los lugares son alcanzables.", color = ColoresRumbo.Turquesa)
                }
            }

            if (plaza.puntosQr.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = Espacio.s),
                    horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
                ) {
                    plaza.puntosQr.forEach { qr ->
                        TextButton(onClick = { qrMostrado = qr }) { Text(qr.codigo) }
                    }
                }
            }
        }
    }

    // --- Dialogos ---

    puntoPendiente?.let { punto ->
        val categoria = herramienta.categoria
        if (categoria == null) {
            puntoPendiente = null
        } else {
            DialogoNombre(
                titulo = "Nuevo ${categoria.etiqueta.lowercase()}",
                nombreInicial = if (categoria == Categoria.BANO) "Baños" else "",
                alCancelar = { puntoPendiente = null },
                alConfirmar = { nombre ->
                    piso?.let { alColocarEstablecimiento(it.id, punto, nombre, categoria) }
                    puntoPendiente = null
                },
            )
        }
    }

    nodoParaQr?.let { nodoId ->
        DialogoQr(
            nodoId = nodoId,
            alCancelar = { nodoParaQr = null },
            alConfirmar = { rumbo, descripcion ->
                piso?.let { alColocarQr(it.id, nodoId, rumbo, descripcion) }
                nodoParaQr = null
            },
        )
    }

    qrMostrado?.let { qr ->
        DialogoVerQr(qr = qr, alCerrar = { qrMostrado = null })
    }

    if (dialogoPiso) {
        DialogoNuevoPiso(
            alCancelar = { dialogoPiso = false },
            alConfirmar = { nombre, ancho, alto, norte ->
                alAgregarPiso(nombre, ancho, alto, norte)
                dialogoPiso = false
            },
        )
    }

    if (dialogoNorte && piso != null) {
        DialogoNorte(
            valorInicial = piso.rumboNorteGrados,
            alCancelar = { dialogoNorte = false },
            alConfirmar = { grados ->
                alAjustarNorte(piso.id, grados)
                dialogoNorte = false
            },
        )
    }
}

private fun ayudaDe(herramienta: Herramienta, nodoSeleccionado: String?): String = when (herramienta) {
    Herramienta.NODO -> "Toca el plano para añadir un nodo de pasillo (se une al más cercano)."
    Herramienta.CONECTAR ->
        if (nodoSeleccionado == null) {
            "Toca el primer nodo a conectar."
        } else {
            "Toca el segundo nodo ($nodoSeleccionado seleccionado). Puedes cambiar de piso para unir ascensores."
        }

    Herramienta.QR -> "Toca cerca del nodo donde se pegará el QR."
    Herramienta.BORRAR -> "Toca un nodo para borrarlo junto con sus conexiones."
    else -> "Toca la posición de la puerta del establecimiento."
}

@Composable
private fun DialogoNombre(
    titulo: String,
    nombreInicial: String,
    alCancelar: () -> Unit,
    alConfirmar: (String) -> Unit,
) {
    var nombre by remember { mutableStateOf(nombreInicial) }
    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text(titulo) },
        text = {
            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                label = { Text("Nombre") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(enabled = nombre.isNotBlank(), onClick = { alConfirmar(nombre.trim()) }) {
                Text("Colocar")
            }
        },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar") } },
    )
}

@Composable
private fun DialogoQr(
    nodoId: String,
    alCancelar: () -> Unit,
    alConfirmar: (Double, String) -> Unit,
) {
    var rumbo by remember { mutableStateOf("0") }
    var descripcion by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text("Punto QR en $nodoId") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
                OutlinedTextField(
                    value = rumbo,
                    onValueChange = { rumbo = it.filter { c -> c.isDigit() || c == '.' || c == '-' } },
                    label = { Text("Rumbo al que mira el usuario (grados)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = descripcion,
                    onValueChange = { descripcion = it },
                    label = { Text("Descripción (Entrada Sur, junto al ascensor…)") },
                    singleLine = true,
                )
                Text(
                    text = "El rumbo es la dirección del mapa hacia la que mira quien lee el QR: " +
                        "0° = hacia arriba del plano, 90° = hacia la derecha. Es el dato que " +
                        "permite alinear la flecha AR sin brújula.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { alConfirmar(rumbo.toDoubleOrNull() ?: 0.0, descripcion.trim()) }) {
                Text("Crear")
            }
        },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar") } },
    )
}

@Composable
private fun DialogoVerQr(qr: PuntoQr, alCerrar: () -> Unit) {
    val imagen = remember(qr.codigo) { GeneradorQr.generar(qr) }
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(qr.codigo) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
                imagen?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Código QR ${qr.codigo}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                    )
                }
                Text(
                    text = PayloadQr.codificar(qr),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Imprime este código y pégalo en ${qr.descripcion.ifBlank { qr.nodoId }}.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = { Button(onClick = alCerrar) { Text("Cerrar") } },
    )
}

@Composable
private fun DialogoNuevoPiso(
    alCancelar: () -> Unit,
    alConfirmar: (String, Double, Double, Double) -> Unit,
) {
    var nombre by remember { mutableStateOf("") }
    var ancho by remember { mutableStateOf("80") }
    var alto by remember { mutableStateOf("50") }
    var norte by remember { mutableStateOf("0") }
    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text("Nuevo piso") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Nombre (Piso 2, Planta baja…)") },
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
                OutlinedTextField(
                    value = norte,
                    onValueChange = { norte = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Rumbo del norte (grados)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                alConfirmar(
                    nombre.trim(),
                    ancho.toDoubleOrNull() ?: 80.0,
                    alto.toDoubleOrNull() ?: 50.0,
                    norte.toDoubleOrNull() ?: 0.0,
                )
            }) { Text("Crear") }
        },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar") } },
    )
}

@Composable
private fun DialogoNorte(
    valorInicial: Double,
    alCancelar: () -> Unit,
    alConfirmar: (Double) -> Unit,
) {
    var valor by remember { mutableStateOf(valorInicial.toInt().toString()) }
    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text("Orientación del plano") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
                OutlinedTextField(
                    value = valor,
                    onValueChange = { valor = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Azimut del eje +Y (grados)") },
                    singleLine = true,
                )
                Text(
                    text = "Es el rumbo de brújula hacia el que apunta la parte de arriba del " +
                        "plano. Solo se usa para el modo sin AR (brújula); con QR o AR no hace falta.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { alConfirmar(valor.toDoubleOrNull() ?: 0.0) }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar") } },
    )
}
