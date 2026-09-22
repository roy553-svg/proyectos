package com.driveai.cockpit

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator

/**
 * Punto de entrada nativo para Android Auto y Android Automotive OS (AAOS).
 *
 * En estos hosts NO renderizamos nuestra propia interfaz: el sistema dibuja
 * las plantillas certificadas por Google, que ya cumplen las reglas de
 * distraccion del conductor. Reutilizamos el mismo gateway y el mismo motor
 * de memoria que el cockpit WebView.
 */
class DriveAiCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator =
        // En depuracion aceptamos cualquier host (DHU / emulador de escritorio).
        if (BuildConfig.DEBUG) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }

    override fun onCreateSession(sessionInfo: SessionInfo): Session = DriveAiSession()

    /** Compatibilidad con hosts antiguos de Android Auto. */
    @Suppress("DEPRECATION")
    override fun onCreateSession(): Session = DriveAiSession()
}
