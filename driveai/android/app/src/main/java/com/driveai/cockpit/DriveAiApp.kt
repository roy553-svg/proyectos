package com.driveai.cockpit

import android.app.Application
import android.os.StrictMode
import android.webkit.WebView

/**
 * Application de la cabina.
 *
 * Minima a proposito: cada objeto vivo aqui se paga durante todo el viaje.
 * No hay inyeccion de dependencias, ni analitica, ni crash reporter: solo
 * los ajustes de proceso que bajan el consumo en un Rockchip de 1 GB.
 */
class DriveAiApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Un unico proceso de WebView compartido; evita duplicar ~12 MB.
        WebView.setDataDirectorySuffix("driveai")

        if (BuildConfig.DEBUG) {
            // En desarrollo queremos ver cualquier I/O en el hilo principal:
            // un frame perdido en cabina es una distraccion real.
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectNetwork()
                    .penaltyLog()
                    .build(),
            )
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        // El low-memory killer viene por nosotros: soltamos lo prescindible
        // antes de que mate la app a mitad de carretera.
        System.gc()
    }
}
