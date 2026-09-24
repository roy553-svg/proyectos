package com.rumbo.ar.ar

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.ar.core.Frame
import com.google.ar.core.Session
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Render de la sesion AR: pinta la imagen de camara y publica cada frame para
 * que la capa de navegacion calcule la pose.
 */
class RenderizadorArCore(
    private val obtenerSesion: () -> Session?,
    private val alCambiarTamano: (Int, Int) -> Unit,
    private val alFrame: (Frame, Int, Int) -> Unit,
) : GLSurfaceView.Renderer {

    private val fondo = RenderizadorFondoCamara()
    private var ancho = 0
    private var alto = 0
    private var texturaAsignada = false

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        fondo.crearEnGl()
        texturaAsignada = false
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        ancho = width
        alto = height
        GLES20.glViewport(0, 0, width, height)
        alCambiarTamano(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val sesion = obtenerSesion() ?: return
        if (!texturaAsignada && fondo.idTextura >= 0) {
            sesion.setCameraTextureName(fondo.idTextura)
            texturaAsignada = true
        }
        try {
            val frame = sesion.update()
            fondo.dibujar(frame)
            alFrame(frame, ancho, alto)
        } catch (e: Exception) {
            // Un frame perdido no debe tumbar la app: se reintenta en el siguiente.
            android.util.Log.w("RenderizadorArCore", "Frame descartado: ${e.javaClass.simpleName}")
        }
    }
}

/**
 * Vista de camara AR lista para Compose. Gestiona el orden correcto del ciclo de
 * vida: la sesion de ARCore se reanuda antes que el GLSurfaceView y se pausa
 * despues, que es lo que exige ARCore para no perder la camara.
 */
@Composable
fun VistaCamaraAr(
    sesionAr: SesionArCore,
    alFrame: (Frame, Int, Int) -> Unit,
    alError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val contexto = LocalContext.current
    val propietarioCiclo = LocalLifecycleOwner.current

    val renderizador = remember {
        RenderizadorArCore(
            obtenerSesion = { sesionAr.sesion },
            alCambiarTamano = { ancho, alto -> sesionAr.actualizarGeometria(ancho, alto) },
            alFrame = alFrame,
        )
    }

    val vistaGl = remember {
        GLSurfaceView(contexto).apply {
            preserveEGLContextOnPause = true
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            setRenderer(renderizador)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
    }

    DisposableEffect(propietarioCiclo) {
        var activa = false

        fun reanudar() {
            if (activa) return
            if (sesionAr.reanudar()) {
                vistaGl.onResume()
                activa = true
            } else {
                alError(sesionAr.ultimoError ?: "No se pudo iniciar la sesión AR")
            }
        }

        fun pausar() {
            if (!activa) return
            vistaGl.onPause()
            sesionAr.pausar()
            activa = false
        }

        val observador = LifecycleEventObserver { _, evento ->
            when (evento) {
                Lifecycle.Event.ON_RESUME -> reanudar()
                Lifecycle.Event.ON_PAUSE -> pausar()
                else -> Unit
            }
        }
        propietarioCiclo.lifecycle.addObserver(observador)
        if (propietarioCiclo.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) reanudar()

        onDispose {
            propietarioCiclo.lifecycle.removeObserver(observador)
            pausar()
            sesionAr.cerrar()
        }
    }

    AndroidView(factory = { vistaGl }, modifier = modifier)
}
