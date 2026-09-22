package com.rumbo.nucleo.posicionamiento

import com.rumbo.nucleo.modelo.Geometria
import kotlin.math.hypot

/**
 * Conversion de un cuaternion de ARCore (o del sensor de rotacion) al rumbo
 * horizontal dentro del marco de mundo correspondiente.
 *
 * Se mantiene aqui, fuera de Android, para poder probarlo con tests unitarios.
 */
object OrientacionAr {

    /** Umbral por debajo del cual el eje de vision esta demasiado vertical. */
    private const val MINIMO_COMPONENTE_HORIZONTAL = 0.15

    /** Rota el vector (vx, vy, vz) por el cuaternion (x, y, z, w). */
    fun rotarPorCuaternion(
        x: Double, y: Double, z: Double, w: Double,
        vx: Double, vy: Double, vz: Double,
    ): Triple<Double, Double, Double> {
        // v' = v + 2 * q_v x (q_v x v + w * v)
        val tx = y * vz - z * vy + w * vx
        val ty = z * vx - x * vz + w * vy
        val tz = x * vy - y * vx + w * vz
        return Triple(
            vx + 2.0 * (y * tz - z * ty),
            vy + 2.0 * (z * tx - x * tz),
            vz + 2.0 * (x * ty - y * tx),
        )
    }

    /**
     * Rumbo horizontal (horario desde el eje -Z del marco de mundo) del eje de
     * vision de la camara, dado su cuaternion de rotacion.
     *
     * Si el telefono apunta casi al suelo o al techo el eje de vision deja de
     * tener informacion horizontal util; en ese caso se usa el eje "arriba" de
     * la pantalla, que es lo que hace cualquier brujula AR seria para no dar
     * saltos de 180 grados.
     */
    fun rumboDeCamara(qx: Double, qy: Double, qz: Double, qw: Double): Double {
        val (fx, _, fz) = rotarPorCuaternion(qx, qy, qz, qw, 0.0, 0.0, -1.0)
        if (hypot(fx, fz) >= MINIMO_COMPONENTE_HORIZONTAL) {
            return Geometria.rumboDeVector(derecha = fx, adelante = -fz)
        }
        // Camara casi vertical: usar el eje +Y de la pantalla proyectado al plano.
        val (ux, _, uz) = rotarPorCuaternion(qx, qy, qz, qw, 0.0, 1.0, 0.0)
        val horizontal = hypot(ux, uz)
        if (horizontal < 1e-6) return 0.0
        // Mirando al suelo, el "arriba" de la pantalla apunta hacia delante;
        // mirando al techo apunta hacia atras. Se distingue por el signo de fy.
        val (_, fy, _) = rotarPorCuaternion(qx, qy, qz, qw, 0.0, 0.0, -1.0)
        val signo = if (fy < 0) 1.0 else -1.0
        return Geometria.rumboDeVector(derecha = signo * ux, adelante = signo * -uz)
    }
}
