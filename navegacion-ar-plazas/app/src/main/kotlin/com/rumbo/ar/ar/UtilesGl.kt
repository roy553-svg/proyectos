package com.rumbo.ar.ar

import android.opengl.GLES20
import android.util.Log

/** Utilidades minimas de OpenGL ES 2.0 (compilar shaders y enlazar programas). */
object UtilesGl {

    private const val ETIQUETA = "UtilesGl"

    fun crearPrograma(codigoVertex: String, codigoFragment: String): Int {
        val vertex = compilar(GLES20.GL_VERTEX_SHADER, codigoVertex)
        val fragment = compilar(GLES20.GL_FRAGMENT_SHADER, codigoFragment)
        val programa = GLES20.glCreateProgram()
        GLES20.glAttachShader(programa, vertex)
        GLES20.glAttachShader(programa, fragment)
        GLES20.glLinkProgram(programa)
        val estado = IntArray(1)
        GLES20.glGetProgramiv(programa, GLES20.GL_LINK_STATUS, estado, 0)
        if (estado[0] == GLES20.GL_FALSE) {
            val error = GLES20.glGetProgramInfoLog(programa)
            GLES20.glDeleteProgram(programa)
            error("No se pudo enlazar el programa de GL: $error")
        }
        GLES20.glDeleteShader(vertex)
        GLES20.glDeleteShader(fragment)
        return programa
    }

    private fun compilar(tipo: Int, codigo: String): Int {
        val shader = GLES20.glCreateShader(tipo)
        GLES20.glShaderSource(shader, codigo)
        GLES20.glCompileShader(shader)
        val estado = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, estado, 0)
        if (estado[0] == GLES20.GL_FALSE) {
            val error = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            error("No se pudo compilar el shader: $error")
        }
        return shader
    }

    fun comprobarError(etapa: String) {
        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) Log.e(ETIQUETA, "Error de GL en $etapa: $error")
    }
}
