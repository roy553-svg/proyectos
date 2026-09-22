package com.driveai.cockpit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.KeyEvent

/**
 * Boton de voz del volante.
 *
 * En casi todas las radios chinas el volante emite KEYCODE_VOICE_ASSIST o
 * KEYCODE_MEDIA_PLAY_PAUSE mantenido. Lo capturamos para abrir DriveAI
 * escuchando, sin que el conductor toque la pantalla: cero segundos de
 * vista fuera del camino.
 */
class SteeringWheelReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MEDIA_BUTTON) return

        @Suppress("DEPRECATION")
        val event = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return

        if (event.action != KeyEvent.ACTION_DOWN) return

        val triggers = setOf(
            KeyEvent.KEYCODE_VOICE_ASSIST,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK,
        )
        if (event.keyCode !in triggers) return
        // Un toque largo en play/pause no debe secuestrar la musica.
        if (event.keyCode != KeyEvent.KEYCODE_VOICE_ASSIST && !event.isLongPress) return

        context.startActivity(
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                action = Intent.ACTION_VOICE_COMMAND
            },
        )
    }
}
