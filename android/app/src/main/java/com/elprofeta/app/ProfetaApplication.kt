package com.elprofeta.app

import android.app.Application
import com.elprofeta.app.core.AppContainer
import com.elprofeta.app.core.DefaultAppContainer

/** Punto de entrada: construye el contenedor de dependencias. */
class ProfetaApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
