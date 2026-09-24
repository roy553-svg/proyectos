package com.rumbo.ar.ui.admin

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.rumbo.nucleo.modelo.PuntoQr
import com.rumbo.nucleo.qr.PayloadQr

/**
 * Genera la imagen del QR que el administrador imprime y pega en la plaza.
 * El contenido es el texto de [PayloadQr], asi que el QR es autosuficiente:
 * no hace falta red para interpretarlo.
 */
object GeneradorQr {

    fun generar(punto: PuntoQr, ladoPx: Int = 512): Bitmap? = try {
        val matriz = QRCodeWriter().encode(
            PayloadQr.codificar(punto),
            BarcodeFormat.QR_CODE,
            ladoPx,
            ladoPx,
            mapOf(
                EncodeHintType.MARGIN to 1,
                // Nivel alto: la etiqueta puede acabar rayada o con reflejos.
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
            ),
        )
        val mapa = Bitmap.createBitmap(matriz.width, matriz.height, Bitmap.Config.ARGB_8888)
        for (x in 0 until matriz.width) {
            for (y in 0 until matriz.height) {
                mapa.setPixel(x, y, if (matriz.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        mapa
    } catch (e: Exception) {
        android.util.Log.e("GeneradorQr", "No se pudo generar el QR ${punto.codigo}", e)
        null
    }
}
