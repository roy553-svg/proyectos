package com.rumbo.ar.ar

import android.opengl.Matrix
import com.google.ar.core.Camera
import com.rumbo.nucleo.posicionamiento.PuntoAr

/**
 * Proyecta un punto del mundo AR a coordenadas de pantalla, para poder dibujar
 * el marcador del siguiente punto de la ruta encima de la imagen de camara.
 *
 * `Camera.getViewMatrix` es la inversa de `getDisplayOrientedPose` y
 * `getProjectionMatrix` ya tiene en cuenta la rotacion de pantalla declarada en
 * `Session.setDisplayGeometry`, asi que el par de matrices lleva del mundo a
 * coordenadas de recorte tal y como se ve en el movil.
 */
object ProyeccionAr {

    /** Devuelve (x, y) en pixeles, o null si el punto queda detras o muy fuera de la pantalla. */
    fun aPantalla(
        camara: Camera,
        punto: PuntoAr,
        anchoPx: Int,
        altoPx: Int,
        margenNdc: Float = 1.6f,
    ): FloatArray? {
        if (anchoPx <= 0 || altoPx <= 0) return null
        val proyeccion = FloatArray(16)
        val vista = FloatArray(16)
        camara.getProjectionMatrix(proyeccion, 0, CERCA, LEJOS)
        camara.getViewMatrix(vista, 0)

        val vistaProyeccion = FloatArray(16)
        Matrix.multiplyMM(vistaProyeccion, 0, proyeccion, 0, vista, 0)

        val entrada = floatArrayOf(punto.x.toFloat(), punto.y.toFloat(), punto.z.toFloat(), 1f)
        val salida = FloatArray(4)
        Matrix.multiplyMV(salida, 0, vistaProyeccion, 0, entrada, 0)
        if (salida[3] <= 0f) return null // el punto esta detras de la camara

        val ndcX = salida[0] / salida[3]
        val ndcY = salida[1] / salida[3]
        if (ndcX < -margenNdc || ndcX > margenNdc || ndcY < -margenNdc || ndcY > margenNdc) return null

        return floatArrayOf(
            (ndcX + 1f) / 2f * anchoPx,
            (1f - ndcY) / 2f * altoPx,
        )
    }

    private const val CERCA = 0.1f
    private const val LEJOS = 120f
}
