package com.rumbo.ar

import android.app.Application
import com.rumbo.ar.datos.FabricaRepositorio

class RumboApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Siembra las plazas de demostracion la primera vez que se abre la app.
        FabricaRepositorio.obtener(this)
    }
}
