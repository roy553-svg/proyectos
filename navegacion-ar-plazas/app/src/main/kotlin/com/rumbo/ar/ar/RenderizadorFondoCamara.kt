package com.rumbo.ar.ar

import android.opengl.GLES11Ext
import android.opengl.GLES20
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Dibuja la imagen de la camara de ARCore como fondo de la escena.
 *
 * ARCore entrega el frame de camara en una textura externa de OpenGL; hay que
 * pintarla a pantalla completa con un quad. Sceneform esta descontinuado, asi
 * que este es el camino soportado hoy (el mismo que usan los ejemplos oficiales
 * hello_ar): GLSurfaceView + OpenGL ES 2.0.
 *
 * Las coordenadas de textura se recalculan con `Frame.transformCoordinates2d`
 * en lugar de a mano: asi la imagen queda correcta en cualquier rotacion,
 * relacion de aspecto y recorte de sensor.
 */
class RenderizadorFondoCamara {

    var idTextura: Int = -1
        private set

    private var programa = 0
    private var atributoPosicion = 0
    private var atributoTextura = 0
    private var uniformeTextura = 0

    private val verticesNdc = floatArrayOf(
        -1f, -1f,
        +1f, -1f,
        -1f, +1f,
        +1f, +1f,
    )
    private val coordenadasTextura = FloatArray(8)

    private lateinit var bufferVertices: FloatBuffer
    private lateinit var bufferTextura: FloatBuffer

    /** Debe llamarse dentro del hilo de GL (onSurfaceCreated). */
    fun crearEnGl() {
        val texturas = IntArray(1)
        GLES20.glGenTextures(1, texturas, 0)
        idTextura = texturas[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, idTextura)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        bufferVertices = crearBuffer(verticesNdc)
        bufferTextura = crearBuffer(FloatArray(8))

        programa = UtilesGl.crearPrograma(VERTEX_SHADER, FRAGMENT_SHADER)
        atributoPosicion = GLES20.glGetAttribLocation(programa, "a_Posicion")
        atributoTextura = GLES20.glGetAttribLocation(programa, "a_CoordTextura")
        uniformeTextura = GLES20.glGetUniformLocation(programa, "u_Textura")
    }

    fun dibujar(frame: Frame) {
        if (idTextura < 0 || programa == 0) return
        // Solo hay que recalcular cuando cambia la geometria de pantalla.
        if (frame.hasDisplayGeometryChanged() || !coordenadasCalculadas) {
            frame.transformCoordinates2d(
                Coordinates2d.OPENGL_NDC,
                verticesNdc,
                Coordinates2d.TEXTURE_NORMALIZED,
                coordenadasTextura,
            )
            bufferTextura.position(0)
            bufferTextura.put(coordenadasTextura)
            bufferTextura.position(0)
            coordenadasCalculadas = true
        }
        if (frame.timestamp == 0L) return // aun no hay imagen real

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)
        GLES20.glUseProgram(programa)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, idTextura)
        GLES20.glUniform1i(uniformeTextura, 0)

        bufferVertices.position(0)
        GLES20.glVertexAttribPointer(atributoPosicion, 2, GLES20.GL_FLOAT, false, 0, bufferVertices)
        bufferTextura.position(0)
        GLES20.glVertexAttribPointer(atributoTextura, 2, GLES20.GL_FLOAT, false, 0, bufferTextura)
        GLES20.glEnableVertexAttribArray(atributoPosicion)
        GLES20.glEnableVertexAttribArray(atributoTextura)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(atributoPosicion)
        GLES20.glDisableVertexAttribArray(atributoTextura)
        GLES20.glDepthMask(true)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        UtilesGl.comprobarError("dibujar fondo")
    }

    private var coordenadasCalculadas = false

    private fun crearBuffer(datos: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(datos.size * BYTES_POR_FLOAT)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(datos)
                position(0)
            }

    private companion object {
        const val BYTES_POR_FLOAT = 4

        const val VERTEX_SHADER = """
            attribute vec4 a_Posicion;
            attribute vec2 a_CoordTextura;
            varying vec2 v_CoordTextura;
            void main() {
                gl_Position = a_Posicion;
                v_CoordTextura = a_CoordTextura;
            }
        """

        const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 v_CoordTextura;
            uniform samplerExternalOES u_Textura;
            void main() {
                gl_FragColor = texture2D(u_Textura, v_CoordTextura);
            }
        """
    }
}
