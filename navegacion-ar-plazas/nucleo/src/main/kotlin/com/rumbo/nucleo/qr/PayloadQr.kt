package com.rumbo.nucleo.qr

import com.rumbo.nucleo.modelo.Punto2D
import com.rumbo.nucleo.modelo.PuntoQr

/**
 * Formato de texto de los QR de posicionamiento.
 *
 * Ejemplo:
 *   RUMBO1;codigo=QR_001;plaza=plaza_aurora;piso=p1;nodo=ENT_S;x=40.00;y=2.00;rumbo=0.0;desc=Entrada Sur
 *
 * Es texto plano y autodescriptivo a proposito: se imprime en una etiqueta, se
 * lee con cualquier escaner y no depende de que el telefono tenga red. El QR
 * aporta POSICION y ORIENTACION, que es exactamente lo que ARCore no puede
 * deducir por si solo.
 */
object PayloadQr {

    const val PREFIJO = "RUMBO1"
    private const val SEPARADOR = ';'

    fun codificar(punto: PuntoQr): String = buildString {
        append(PREFIJO)
        append(SEPARADOR).append("codigo=").append(punto.codigo)
        append(SEPARADOR).append("plaza=").append(punto.plazaId)
        append(SEPARADOR).append("piso=").append(punto.pisoId)
        append(SEPARADOR).append("nodo=").append(punto.nodoId)
        append(SEPARADOR).append("x=").append(formatear(punto.posicion.x))
        append(SEPARADOR).append("y=").append(formatear(punto.posicion.y))
        append(SEPARADOR).append("rumbo=").append(formatear(punto.rumboGrados))
        if (punto.descripcion.isNotBlank()) {
            append(SEPARADOR).append("desc=").append(punto.descripcion.replace(SEPARADOR, ' '))
        }
    }

    /** Devuelve null si el texto no es un QR de esta aplicacion o le faltan campos. */
    fun decodificar(texto: String?): PuntoQr? {
        val limpio = texto?.trim().orEmpty()
        if (!limpio.startsWith(PREFIJO)) return null
        val campos = limpio.split(SEPARADOR)
            .drop(1)
            .mapNotNull { trozo ->
                val i = trozo.indexOf('=')
                if (i <= 0) null else trozo.substring(0, i).trim() to trozo.substring(i + 1).trim()
            }
            .toMap()

        val plazaId = campos["plaza"] ?: return null
        val pisoId = campos["piso"] ?: return null
        val nodoId = campos["nodo"] ?: return null
        val x = campos["x"]?.toDoubleOrNull() ?: return null
        val y = campos["y"]?.toDoubleOrNull() ?: return null
        val rumbo = campos["rumbo"]?.toDoubleOrNull() ?: return null

        return PuntoQr(
            codigo = campos["codigo"] ?: "QR_SIN_CODIGO",
            plazaId = plazaId,
            pisoId = pisoId,
            nodoId = nodoId,
            posicion = Punto2D(x, y),
            rumboGrados = rumbo,
            descripcion = campos["desc"].orEmpty(),
        )
    }

    private fun formatear(valor: Double): String =
        if (valor == valor.toLong().toDouble()) valor.toLong().toString()
        else String.format(java.util.Locale.US, "%.2f", valor)
}
