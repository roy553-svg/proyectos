package com.rumbo.ar.ui.pantallas

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.rumbo.ar.ui.componentes.Aviso
import com.rumbo.ar.ui.componentes.BarraSuperior
import com.rumbo.ar.ui.tema.ColoresRumbo
import com.rumbo.ar.ui.tema.Espacio
import com.rumbo.nucleo.modelo.PuntoQr
import com.rumbo.nucleo.qr.PayloadQr
import java.util.concurrent.Executors

/**
 * Lectura del QR de posicionamiento con CameraX + ML Kit (detector local, sin red).
 *
 * El QR da posicion Y orientacion, que es justo lo que ARCore no puede deducir.
 * Mientras esta pantalla esta abierta la sesion AR esta cerrada, asi que no hay
 * dos consumidores de la camara al mismo tiempo.
 */
@OptIn(ExperimentalGetImage::class)
@Composable
fun PantallaEscanerQr(
    puntosDePrueba: List<PuntoQr>,
    alLeer: (String) -> Unit,
    alElegirPunto: (PuntoQr) -> Unit,
    alVolver: () -> Unit,
    mensajeError: String? = null,
) {
    val contexto = LocalContext.current
    val propietarioCiclo = LocalLifecycleOwner.current
    var listaVisible by remember { mutableStateOf(false) }

    val vistaPrevia = remember { PreviewView(contexto) }
    val ejecutor = remember { Executors.newSingleThreadExecutor() }
    val escaner: BarcodeScanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build(),
        )
    }

    DisposableEffect(propietarioCiclo) {
        var proveedorCamara: ProcessCameraProvider? = null
        var yaLeido = false
        val futuro = ProcessCameraProvider.getInstance(contexto)
        // El enlace con el ciclo de vida tiene que hacerse en el hilo principal.
        futuro.addListener({
            try {
                val proveedor = futuro.get()
                proveedorCamara = proveedor
                val previa = Preview.Builder().build()
                previa.setSurfaceProvider(vistaPrevia.surfaceProvider)
                val analisis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analisis.setAnalyzer(ejecutor) { imagenProxy ->
                    val medios = imagenProxy.image
                    if (medios == null || yaLeido) {
                        imagenProxy.close()
                        return@setAnalyzer
                    }
                    val entrada = InputImage.fromMediaImage(medios, imagenProxy.imageInfo.rotationDegrees)
                    escaner.process(entrada)
                        .addOnSuccessListener { codigos ->
                            val texto = codigos.firstNotNullOfOrNull { it.rawValue }
                            if (texto != null && PayloadQr.decodificar(texto) != null && !yaLeido) {
                                yaLeido = true
                                alLeer(texto)
                            }
                        }
                        .addOnCompleteListener { imagenProxy.close() }
                }
                proveedor.unbindAll()
                proveedor.bindToLifecycle(
                    propietarioCiclo,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    previa,
                    analisis,
                )
            } catch (e: Exception) {
                Log.e("PantallaEscanerQr", "No se pudo abrir la camara", e)
            }
        }, ContextCompat.getMainExecutor(contexto))

        onDispose {
            proveedorCamara?.unbindAll()
            escaner.close()
            ejecutor.shutdown()
        }
    }

    Scaffold(
        topBar = { BarraSuperior(titulo = "¿Dónde estás?", alVolver = alVolver) },
    ) { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                AndroidView(factory = { vistaPrevia }, modifier = Modifier.fillMaxSize())
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    val lado = size.minDimension * 0.62f
                    val esquina = androidx.compose.ui.geometry.Offset(
                        (size.width - lado) / 2f,
                        (size.height - lado) / 2f,
                    )
                    drawRoundRect(
                        color = ColoresRumbo.TurquesaClaro,
                        topLeft = esquina,
                        size = androidx.compose.ui.geometry.Size(lado, lado),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(28f, 28f),
                        style = Stroke(width = 5f),
                    )
                    drawRect(color = Color.Black.copy(alpha = 0.25f))
                }
            }
            Column(
                modifier = Modifier.padding(Espacio.m),
                verticalArrangement = Arrangement.spacedBy(Espacio.s),
            ) {
                mensajeError?.let { Aviso(it, color = MaterialTheme.colorScheme.error) }
                Text(
                    text = "Apunta al código QR pegado en la plaza (entradas, pasillo central, ascensores).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { listaVisible = !listaVisible }) {
                    Text(if (listaVisible) "Ocultar puntos de la plaza" else "No tengo los QR impresos")
                }
                if (listaVisible) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp),
                        verticalArrangement = Arrangement.spacedBy(Espacio.xs),
                    ) {
                        items(puntosDePrueba, key = { it.codigo }) { punto ->
                            Card(
                                onClick = { alElegirPunto(punto) },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(Espacio.s)) {
                                    Text(
                                        text = punto.descripcion.ifBlank { punto.codigo },
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        text = "${punto.codigo} · nodo ${punto.nodoId}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
