package com.elprofeta.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.elprofeta.app.ui.navigation.ProfetaNavHost
import com.elprofeta.app.ui.theme.ProfetaTheme

/** Unica Activity: toda la interfaz es Compose. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ProfetaTheme {
                ProfetaNavHost()
            }
        }
    }
}
