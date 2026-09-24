package com.rumbo.ar.ui.componentes

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.rumbo.ar.ui.tema.Espacio

/**
 * Pide el permiso de camara justo cuando se necesita (al empezar a navegar o al
 * leer un QR), nunca antes. Si el usuario lo rechaza, la app ofrece el mapa 2D.
 */
@Composable
fun ConPermisoCamara(
    alRechazar: () -> Unit,
    contenido: @Composable () -> Unit,
) {
    val contexto = LocalContext.current
    var concedido by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var solicitado by remember { mutableStateOf(false) }
    var rechazado by remember { mutableStateOf(false) }

    val lanzador = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { resultado ->
        concedido = resultado
        rechazado = !resultado
    }

    LaunchedEffect(concedido) {
        if (!concedido && !solicitado) {
            solicitado = true
            lanzador.launch(Manifest.permission.CAMERA)
        }
    }

    if (concedido) {
        contenido()
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Espacio.l),
        verticalArrangement = Arrangement.spacedBy(Espacio.m, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Necesitamos la cámara", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "La cámara solo se usa durante la navegación AR y para leer los códigos QR " +
                "de la plaza. No se guarda ninguna imagen.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (rechazado) {
            Aviso("Sin cámara no hay vista AR, pero puedes seguir la ruta en el mapa.")
        }
        Button(onClick = { lanzador.launch(Manifest.permission.CAMERA) }) {
            Text("Permitir cámara")
        }
        TextButton(onClick = alRechazar) { Text("Usar el mapa") }
    }
}
