package com.rumbo.ar.ui.componentes

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Vista de camara sin AR (CameraX). Se usa en los dispositivos que no admiten
 * ARCore: la imagen real sigue detras de la flecha, pero la orientacion viene de
 * la brujula y el avance del contador de pasos.
 */
@Composable
fun VistaCamaraSimple(modifier: Modifier = Modifier) {
    val contexto = LocalContext.current
    val propietarioCiclo = LocalLifecycleOwner.current
    val vistaPrevia = remember { PreviewView(contexto) }

    DisposableEffect(propietarioCiclo) {
        var proveedorCamara: ProcessCameraProvider? = null
        val futuro = ProcessCameraProvider.getInstance(contexto)
        futuro.addListener({
            try {
                val proveedor = futuro.get()
                proveedorCamara = proveedor
                val previa = Preview.Builder().build()
                previa.setSurfaceProvider(vistaPrevia.surfaceProvider)
                proveedor.unbindAll()
                proveedor.bindToLifecycle(propietarioCiclo, CameraSelector.DEFAULT_BACK_CAMERA, previa)
            } catch (e: Exception) {
                Log.e("VistaCamaraSimple", "No se pudo abrir la camara", e)
            }
        }, ContextCompat.getMainExecutor(contexto))

        onDispose { proveedorCamara?.unbindAll() }
    }

    AndroidView(factory = { vistaPrevia }, modifier = modifier)
}
