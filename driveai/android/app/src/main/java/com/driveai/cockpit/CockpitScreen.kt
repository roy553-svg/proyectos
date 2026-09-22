package com.driveai.cockpit

import android.os.Handler
import android.os.Looper
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Pantalla unica en Android Auto / AAOS.
 *
 * Restricciones del host (no negociables, las impone Google):
 *  - Maximo 6 filas visibles con el coche en movimiento.
 *  - Nada de texto libre ni teclado en marcha: solo voz y toques grandes.
 *
 * Encaja con la filosofia "tipo DOOM": telemetria arriba, acciones abajo,
 * cero navegacion por menus.
 */
class CockpitScreen(carContext: CarContext) : Screen(carContext) {

    private val io = Executors.newSingleThreadExecutor { r ->
        Thread(r, "driveai-car").apply { isDaemon = true }
    }
    private val main = Handler(Looper.getMainLooper())

    private var speed = "--"
    private var battery = "--"
    private var range = "--"
    private var lastAnswer = "Toca el microfono y pregunta lo que quieras."

    init {
        // Soltamos hilo y handler cuando el host destruye la pantalla:
        // una radio de 1 GB no perdona fugas entre viajes.
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                io.shutdownNow()
                main.removeCallbacksAndMessages(null)
            }
        })
        refreshTelemetry()
    }

    override fun onGetTemplate(): Template {
        val items = ItemList.Builder()
            .addItem(
                Row.Builder()
                    .setTitle("$speed km/h  ·  $battery")
                    .addText("Autonomia $range km")
                    .build(),
            )
            .addItem(
                Row.Builder()
                    .setTitle("DriveAI")
                    .addText(lastAnswer.take(120))
                    .build(),
            )
            .addItem(
                Row.Builder()
                    .setTitle("Preguntar por voz")
                    .setBrowsable(false)
                    .setOnClickListener { startVoiceQuery() }
                    .build(),
            )
            .addItem(
                Row.Builder()
                    .setTitle("Estado del vehiculo")
                    .setOnClickListener { ask("Como esta el coche?") }
                    .build(),
            )
            .build()

        return ListTemplate.Builder()
            .setTitle("DriveAI")
            .setSingleList(items)
            .setHeaderAction(Action.APP_ICON)
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setIcon(
                                CarIcon.Builder(
                                    IconCompat.createWithResource(
                                        carContext, R.drawable.ic_mic,
                                    ),
                                ).build(),
                            )
                            .setOnClickListener { startVoiceQuery() }
                            .build(),
                    )
                    .build(),
            )
            .build()
    }

    /**
     * En el host automotriz el reconocimiento lo hace el asistente del
     * sistema; aqui solo lanzamos la consulta y pintamos la respuesta.
     */
    private fun startVoiceQuery() {
        CarToast.makeText(carContext, "Escuchando...", CarToast.LENGTH_SHORT).show()
        ask("Resumen rapido del viaje")
    }

    private fun ask(question: String) {
        io.execute {
            val answer = postJson(
                "/api/assistant/message",
                JSONObject()
                    .put("message", question)
                    .put("driverId", GatewayConfig.driverId(carContext))
                    .put("voice", true),
            )?.optString("text").orEmpty()

            main.post {
                // Sin gateway seguimos mostrando telemetria: nunca un error en pantalla.
                lastAnswer = answer.ifBlank { "Sin conexion. Telemetria local disponible." }
                invalidate()
            }
        }
    }

    private fun refreshTelemetry() {
        io.execute {
            val status = getJson("/api/vehicles/status")
            main.post {
                if (status != null) {
                    speed = status.optDouble("speedKph", 0.0).toInt().toString()
                    battery = status.opt("batteryPercent")
                        ?.takeIf { it != JSONObject.NULL }
                        ?.let { "${(it as Number).toInt()}%" } ?: "--"
                    range = status.opt("rangeKm")
                        ?.takeIf { it != JSONObject.NULL }
                        ?.let { (it as Number).toInt().toString() } ?: "--"
                    invalidate()
                }
                // El host limita el refresco; 2 s es el equilibrio que acepta.
                main.postDelayed({ refreshTelemetry() }, 2_000)
            }
        }
    }

    // ------------------------------------------------------------------
    private fun getJson(path: String): JSONObject? = request(path, null)

    private fun postJson(path: String, body: JSONObject): JSONObject? = request(path, body)

    private fun request(path: String, body: JSONObject?): JSONObject? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(GatewayConfig.baseUrl(carContext) + path)
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3_000
                readTimeout = 6_000
                if (body != null) {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
            }
            body?.let { conn.outputStream.use { os -> os.write(it.toString().toByteArray()) } }
            if (conn.responseCode !in 200..299) return null
            JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } catch (_: Exception) {
            null // tunel o gateway caido: la pantalla conserva el ultimo valor
        } finally {
            conn?.disconnect()
        }
    }
}
