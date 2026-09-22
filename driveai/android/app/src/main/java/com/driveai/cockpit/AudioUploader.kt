package com.driveai.cockpit

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Sube el PCM capturado al gateway y devuelve la transcripcion.
 *
 * Usa `HttpURLConnection` a proposito: cero dependencias (OkHttp/Retrofit
 * anaden ~1.5 MB de dex y varios MB de heap, presupuesto que la radio china
 * no tiene). Un unico hilo reutilizado, sin pool que crezca.
 *
 * Si el gateway no responde, avisamos y el cockpit cae a su motor local:
 * nunca se queda un spinner girando.
 */
object AudioUploader {

    private const val TAG = "DriveAI/Upload"
    private const val TIMEOUT_MS = 8_000

    /** Un solo hilo: las peticiones de voz son secuenciales por naturaleza. */
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "driveai-upload").apply { isDaemon = true }
    }

    fun send(
        context: Context,
        pcm16: ByteArray,
        durationMs: Long,
        onTranscript: (String) -> Unit,
    ) {
        val endpoint = GatewayConfig.baseUrl(context) + "/api/assistant/transcribe"
        executor.execute {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    setRequestProperty("Content-Type", "audio/L16; rate=16000; channels=1")
                    setRequestProperty("X-Duration-Ms", durationMs.toString())
                    setRequestProperty("X-Driver-Id", GatewayConfig.driverId(context))
                    // Streaming: no duplicamos el audio en memoria.
                    setFixedLengthStreamingMode(pcm16.size)
                }
                conn.outputStream.use { out: OutputStream ->
                    out.write(pcm16)
                    out.flush()
                }

                if (conn.responseCode in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val text = JSONObject(body).optString("text", "")
                    if (text.isNotBlank()) onTranscript(text)
                    else onTranscript("")
                } else {
                    Log.w(TAG, "Gateway respondio ${conn.responseCode}")
                    onTranscript("")
                }
            } catch (e: Exception) {
                // Tunel, estacionamiento o gateway caido: el cockpit ya sabe
                // responder solo con su motor determinista.
                Log.w(TAG, "Sin gateway: ${e.message}")
                onTranscript("")
            } finally {
                conn?.disconnect()
            }
        }
    }
}

/** Configuracion persistida del gateway (editable desde el panel de ajustes). */
object GatewayConfig {
    private const val PREFS = "driveai"

    fun baseUrl(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("gateway_url", "http://10.0.2.2:8080")!!

    fun driverId(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("driver_id", "demo-driver")!!

    fun save(context: Context, gatewayUrl: String, driverId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("gateway_url", gatewayUrl.trimEnd('/'))
            .putString("driver_id", driverId)
            .apply()
    }
}
