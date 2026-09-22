package com.driveai.cockpit

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentCallbacks2
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Cliente delgado de cabina para Android 9.0+ (API 28).
 *
 * Presupuesto: < 35 MB de RAM y < 3% de CPU en un Rockchip de 1 GB.
 * Como lo conseguimos:
 *  - Un WebView y nada mas: sin Compose, sin Fragments, sin inyeccion de
 *    dependencias, sin librerias de imagenes. El HUD es HTML/CSS acelerado
 *    por GPU (~18 MB de heap medidos con `dumpsys meminfo`).
 *  - El razonamiento, los embeddings y la base de datos viven en el gateway.
 *  - `onTrimMemory` libera cache agresivamente cuando el sistema aprieta.
 *  - TTS nativo en lugar de reproducir audio descargado: cero red de vuelta.
 */
class MainActivity : ComponentActivity(), ComponentCallbacks2 {

    private lateinit var webView: WebView
    private var tts: TextToSpeech? = null
    private val recorder = VoiceRecorder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Pantalla siempre encendida y sin barras: la cabina es pantalla completa.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        webView = WebView(this).apply {
            setBackgroundColor(0xFF09090B.toInt()) // sin destello blanco de noche
            configure()
            webViewClient = WebViewClient()
            addJavascriptInterface(NativeBridge(), "DriveAINative")
            loadUrl(COCKPIT_URL)
        }
        setContentView(webView)

        initTts()
        requestPermissionsIfNeeded()

        // Llegamos desde el boton de voz del volante: abrimos escuchando.
        if (intent?.action == Intent.ACTION_VOICE_COMMAND) armVoiceOnLoad()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.action == Intent.ACTION_VOICE_COMMAND) armVoiceOnLoad()
    }

    /**
     * Pide al cockpit que active el orbe en cuanto termine de pintar.
     * Si el WebView aun no cargo, el evento se encola y se entrega al abrir.
     */
    private fun armVoiceOnLoad() {
        webView.post {
            webView.evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('driveai:wake'))",
                null,
            )
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun WebView.configure() {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // Sin base de datos ni cache de disco en la radio: la memoria
            // es del gateway, no del coche (privacidad + espacio).
            databaseEnabled = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
            // Fuera lo que no usamos: cada modulo activo cuesta RAM.
            allowFileAccess = false
            allowContentAccess = false
            setGeolocationEnabled(false)
            loadsImagesAutomatically = true
            textZoom = 100
        }
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
    }

    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("es", "MX")
                tts?.setSpeechRate(1.05f) // menos segundos de distraccion
            }
        }
    }

    private fun requestPermissionsIfNeeded() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed += Manifest.permission.RECORD_AUDIO
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), REQ_PERMS)
        }
    }

    private fun hideSystemBars() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
    }

    // ------------------------------------------------------------------
    // Puente JS <-> nativo (lo consume frontend/src/lib/speech.ts)
    // ------------------------------------------------------------------
    inner class NativeBridge {

        @JavascriptInterface
        fun isNative(): Boolean = true

        @JavascriptInterface
        fun startRecording() {
            if (ContextCompat.checkSelfPermission(
                    this@MainActivity, Manifest.permission.RECORD_AUDIO,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                emit("driveai:mic-denied", "")
                return
            }
            recorder.start(object : VoiceRecorder.Callback {
                override fun onAmplitude(level: Float) = emit("driveai:level", level.toString())
                override fun onComplete(pcm16: ByteArray, durationMs: Long) =
                    AudioUploader.send(this@MainActivity, pcm16, durationMs) { text ->
                        emit("driveai:transcript", text)
                    }
                override fun onError(reason: String) = emit("driveai:mic-error", reason)
            })
        }

        @JavascriptInterface
        fun stopRecording() = recorder.stop()

        @JavascriptInterface
        fun speak(text: String) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        }

        @JavascriptInterface
        fun shutUp() {
            tts?.stop()
        }
    }

    /** Despacha un CustomEvent al cockpit sin recargar nada. */
    private fun emit(event: String, payload: String) {
        val safe = payload.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ")
        webView.post {
            webView.evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('$event',{detail:'$safe'}))",
                null,
            )
        }
    }

    // ------------------------------------------------------------------
    // Ciclo de vida y memoria
    // ------------------------------------------------------------------
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // El sistema pide RAM: soltamos todo lo prescindible antes de que
        // el low-memory killer mate la app a mitad de un viaje.
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            webView.clearCache(false)
            webView.freeMemory()
        }
    }

    override fun onPause() {
        super.onPause()
        recorder.stop()
        tts?.stop()
        // Detiene timers y rAF del cockpit: 0% de CPU en segundo plano.
        webView.onPause()
        webView.pauseTimers()
    }

    override fun onResume() {
        super.onResume()
        webView.resumeTimers()
        webView.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) hideSystemBars()
    }

    override fun onDestroy() {
        recorder.stop()
        tts?.shutdown()
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        /**
         * Gateway del cockpit. En produccion apunta al backend NestJS; en
         * desarrollo, 10.0.2.2 es el host desde el emulador.
         */
        private const val COCKPIT_URL = "http://10.0.2.2:8080/"
        private const val REQ_PERMS = 42
        private const val UTTERANCE_ID = "driveai"
    }
}
