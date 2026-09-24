package com.rumbo.ar.ui.pantallas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rumbo.ar.ar.EstadoAr
import com.rumbo.ar.ui.componentes.Aviso
import com.rumbo.ar.ui.componentes.BarraSuperior
import com.rumbo.ar.ui.tema.ColoresRumbo
import com.rumbo.ar.ui.tema.Espacio
import com.rumbo.nucleo.modelo.Establecimiento
import com.rumbo.nucleo.modelo.Plaza
import kotlin.math.roundToInt

/**
 * Confirmacion del destino. La camara todavia NO se abre: solo se activa cuando
 * el usuario pulsa "Comenzar navegación".
 */
@Composable
fun PantallaDestino(
    plaza: Plaza,
    destino: Establecimiento,
    distanciaMetros: Double?,
    estadoAr: EstadoAr,
    alComenzar: () -> Unit,
    alVerMapa: () -> Unit,
    alVolver: () -> Unit,
) {
    val piso = plaza.piso(destino.pisoId)?.nombre ?: destino.pisoId

    Scaffold(
        topBar = { BarraSuperior(titulo = plaza.nombre, alVolver = alVolver) },
    ) { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno)
                .padding(Espacio.l),
            verticalArrangement = Arrangement.spacedBy(Espacio.s),
        ) {
            Text(
                text = "DESTINO",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = destino.nombre,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = piso,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (distanciaMetros != null) {
                Text(
                    text = "Distancia aproximada: ${distanciaMetros.roundToInt()} m",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            destino.descripcion?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(Espacio.m))

            when (estadoAr) {
                EstadoAr.DISPONIBLE -> Aviso(
                    texto = "Navegación AR disponible en este dispositivo.",
                    color = ColoresRumbo.Turquesa,
                )

                EstadoAr.REQUIERE_INSTALACION -> Aviso(
                    "Para la vista AR hay que instalar Google Play Services for AR. " +
                        "Te lo pediremos al empezar; si no quieres, puedes usar el mapa.",
                )

                EstadoAr.NO_COMPATIBLE -> Aviso(
                    "Este dispositivo no admite AR. Navegarás con el mapa y la brújula.",
                )

                EstadoAr.COMPROBANDO -> Aviso("Comprobando el soporte AR del dispositivo…")
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "La cámara se activa solo durante la navegación y no se guarda ninguna imagen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = alComenzar,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text("COMENZAR NAVEGACIÓN", style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(
                onClick = alVerMapa,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("Ver mapa")
            }
        }
    }
}
