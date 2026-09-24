package com.rumbo.ar.ui.tema

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * Identidad visual propia de Rumbo: fondo profundo, un unico acento turquesa
 * para la navegacion y ambar para los avisos. Nada de graficos, personajes ni
 * interfaz de terceros.
 */
object ColoresRumbo {
    val Turquesa = Color(0xFF10B3A0)
    val TurquesaClaro = Color(0xFF5FE3D1)
    val Ambar = Color(0xFFFFB020)
    val Rojo = Color(0xFFFF6B5B)
    val FondoOscuro = Color(0xFF0E1116)
    val SuperficieOscura = Color(0xFF161B22)
    val SuperficieOscuraAlta = Color(0xFF1F2733)
    val TextoClaro = Color(0xFFF2F5F7)
    val TextoApagado = Color(0xFF9AA7B4)
    val FondoClaro = Color(0xFFF6F8FA)
    val SuperficieClara = Color(0xFFFFFFFF)
    val TextoOscuro = Color(0xFF10151B)
}

private val EsquemaOscuro = darkColorScheme(
    primary = ColoresRumbo.Turquesa,
    onPrimary = Color(0xFF00201C),
    primaryContainer = Color(0xFF00504A),
    onPrimaryContainer = ColoresRumbo.TurquesaClaro,
    secondary = ColoresRumbo.Ambar,
    onSecondary = Color(0xFF3A2500),
    background = ColoresRumbo.FondoOscuro,
    onBackground = ColoresRumbo.TextoClaro,
    surface = ColoresRumbo.SuperficieOscura,
    onSurface = ColoresRumbo.TextoClaro,
    surfaceVariant = ColoresRumbo.SuperficieOscuraAlta,
    onSurfaceVariant = ColoresRumbo.TextoApagado,
    error = ColoresRumbo.Rojo,
)

private val EsquemaClaro = lightColorScheme(
    primary = Color(0xFF00695F),
    onPrimary = Color.White,
    secondary = Color(0xFF8A5B00),
    background = ColoresRumbo.FondoClaro,
    onBackground = ColoresRumbo.TextoOscuro,
    surface = ColoresRumbo.SuperficieClara,
    onSurface = ColoresRumbo.TextoOscuro,
    error = Color(0xFFB3261E),
)

private val TipografiaRumbo = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
)

/** Separaciones consistentes en toda la app. */
object Espacio {
    val xs = 4.dp
    val s = 8.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 32.dp
}

@Composable
fun TemaRumbo(
    oscuro: Boolean = isSystemInDarkTheme(),
    contenido: @Composable () -> Unit,
) {
    val esquema = if (oscuro) EsquemaOscuro else EsquemaClaro
    val vista = LocalView.current
    if (!vista.isInEditMode) {
        val contexto = LocalContext.current
        SideEffect {
            (contexto as? Activity)?.window?.let { ventana ->
                WindowCompat.getInsetsController(ventana, vista).isAppearanceLightStatusBars = !oscuro
            }
        }
    }
    MaterialTheme(
        colorScheme = esquema,
        typography = TipografiaRumbo,
        content = contenido,
    )
}
