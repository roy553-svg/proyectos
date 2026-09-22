package com.driveai.cockpit

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

/** Sesion del host automotriz: una sola pantalla, igual que en cabina. */
class DriveAiSession : Session() {

    override fun onCreateScreen(intent: Intent): Screen = CockpitScreen(carContext)
}
