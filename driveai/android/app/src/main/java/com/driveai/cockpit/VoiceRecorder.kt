package com.driveai.cockpit

import android.Manifest
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.abs

/**
 * Captura de audio PCM 16 kHz mono de bajo consumo.
 *
 * Presupuesto de hardware (radios Rockchip / Allwinner / MediaTek de 1 GB):
 *  - Un solo buffer reutilizado: cero asignaciones por ciclo de lectura.
 *  - 16 kHz / 16 bit / mono = 32 KB por segundo de voz.
 *  - Corte automatico por silencio: nunca grabamos de mas ni mandamos
 *    audio inutil por la red movil del coche.
 *
 * El audio jamas se escribe a disco: vive en memoria, se envia al gateway
 * y se descarta (privacidad por diseno).
 */
class VoiceRecorder(
    private val sampleRate: Int = 16_000,
    /** Milisegundos de silencio que cierran la frase. */
    private val silenceTimeoutMs: Long = 1_200,
    /** Tope duro: ninguna consulta de cabina dura mas que esto. */
    private val maxDurationMs: Long = 12_000,
) {

    interface Callback {
        fun onAmplitude(level: Float)
        fun onComplete(pcm16: ByteArray, durationMs: Long)
        fun onError(reason: String)
    }

    private val recording = AtomicBoolean(false)
    private var record: AudioRecord? = null

    fun isRecording(): Boolean = recording.get()

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(callback: Callback) {
        if (recording.getAndSet(true)) return

        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            recording.set(false)
            callback.onError("El hardware no soporta captura a ${sampleRate} Hz")
            return
        }
        // x2: margen para que un scheduler lento no nos tire muestras.
        val bufferSize = minBuffer * 2

        val audio = try {
            AudioRecord(
                // VOICE_RECOGNITION aplica la cancelacion de ruido del SoC,
                // clave con el motor y el aire acondicionado encendidos.
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
            )
        } catch (e: Exception) {
            recording.set(false)
            callback.onError("No se pudo abrir el microfono: ${e.message}")
            return
        }

        if (audio.state != AudioRecord.STATE_INITIALIZED) {
            audio.release()
            recording.set(false)
            callback.onError("Microfono no disponible")
            return
        }

        record = audio
        audio.startRecording()

        thread(name = "driveai-mic", priority = Thread.NORM_PRIORITY + 1) {
            val buffer = ByteArray(bufferSize)
            val out = ByteArrayOutputStream(sampleRate * 2 * 4) // ~4 s iniciales
            val startedAt = System.currentTimeMillis()
            var lastVoiceAt = startedAt
            var sawVoice = false

            try {
                while (recording.get()) {
                    val read = audio.read(buffer, 0, buffer.size)
                    if (read <= 0) continue

                    out.write(buffer, 0, read)
                    val level = rms(buffer, read)
                    callback.onAmplitude(level)

                    val now = System.currentTimeMillis()
                    if (level > VOICE_THRESHOLD) {
                        sawVoice = true
                        lastVoiceAt = now
                    }
                    // Corte por silencio, solo despues de oir algo.
                    if (sawVoice && now - lastVoiceAt > silenceTimeoutMs) break
                    if (now - startedAt > maxDurationMs) break
                }

                val durationMs = System.currentTimeMillis() - startedAt
                val pcm = out.toByteArray()
                if (!sawVoice || pcm.isEmpty()) {
                    callback.onError("No escuche nada")
                } else {
                    callback.onComplete(pcm, durationMs)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Fallo de captura", e)
                callback.onError(e.message ?: "error de captura")
            } finally {
                stop()
                out.close()
            }
        }
    }

    fun stop() {
        if (!recording.getAndSet(false)) return
        record?.runCatching {
            if (recordingState == AudioRecord.RECORDSTATE_RECORDING) stop()
            release()
        }
        record = null
    }

    /** RMS normalizado 0..1 sobre muestras de 16 bit little-endian. */
    private fun rms(buffer: ByteArray, length: Int): Float {
        var sum = 0.0
        var i = 0
        while (i + 1 < length) {
            val sample = (buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)
            sum += abs(sample.toShort().toInt()).toDouble()
            i += 2
        }
        val samples = length / 2
        return if (samples == 0) 0f else (sum / samples / Short.MAX_VALUE).toFloat()
    }

    /** Cabecera WAV de 44 bytes, por si el backend prefiere un contenedor. */
    fun wrapAsWav(pcm: ByteArray): ByteArray {
        val header = ByteArray(44)
        val totalDataLen = pcm.size + 36
        val byteRate = sampleRate * 2

        fun putAscii(offset: Int, text: String) =
            text.forEachIndexed { i, c -> header[offset + i] = c.code.toByte() }

        fun putInt(offset: Int, value: Int) {
            header[offset] = (value and 0xff).toByte()
            header[offset + 1] = ((value shr 8) and 0xff).toByte()
            header[offset + 2] = ((value shr 16) and 0xff).toByte()
            header[offset + 3] = ((value shr 24) and 0xff).toByte()
        }

        putAscii(0, "RIFF"); putInt(4, totalDataLen); putAscii(8, "WAVE")
        putAscii(12, "fmt "); putInt(16, 16)
        header[20] = 1; header[21] = 0            // PCM
        header[22] = 1; header[23] = 0            // mono
        putInt(24, sampleRate); putInt(28, byteRate)
        header[32] = 2; header[33] = 0            // block align
        header[34] = 16; header[35] = 0           // bits por muestra
        putAscii(36, "data"); putInt(40, pcm.size)

        return header + pcm
    }

    companion object {
        private const val TAG = "DriveAI/Voice"
        /** Umbral de voz sobre ruido de cabina en carretera. */
        private const val VOICE_THRESHOLD = 0.018f
    }
}
