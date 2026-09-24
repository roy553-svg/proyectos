package com.rumbo.ar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rumbo.ar.ui.admin.AdminViewModel
import com.rumbo.ar.ui.admin.PantallaAdminPlazas
import com.rumbo.ar.ui.admin.PantallaEditorMapa
import com.rumbo.ar.ui.pantallas.PantallaCategorias
import com.rumbo.ar.ui.pantallas.PantallaDestino
import com.rumbo.ar.ui.pantallas.PantallaEscanerQr
import com.rumbo.ar.ui.pantallas.PantallaEstablecimientos
import com.rumbo.ar.ui.pantallas.PantallaMapa2D
import com.rumbo.ar.ui.pantallas.PantallaNavegacionAr
import com.rumbo.ar.ui.pantallas.PantallaPlazas
import com.rumbo.ar.ui.pantallas.PantallaPosicionManual
import com.rumbo.ar.ui.tema.TemaRumbo
import com.rumbo.nucleo.modelo.Categoria
import com.rumbo.nucleo.qr.PayloadQr

class MainActivity : ComponentActivity() {

    private val vistaModelo: NavegacionViewModel by viewModels()
    private val administracion: AdminViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TemaRumbo {
                AppRumbo(vistaModelo, administracion)
            }
        }
    }
}

private object Rutas {
    const val PLAZAS = "plazas"
    const val CATEGORIAS = "categorias"
    const val ESTABLECIMIENTOS = "establecimientos/{categoria}"
    const val DESTINO = "destino"
    const val NAVEGACION = "navegacion"
    const val MAPA = "mapa"
    const val ESCANER = "escaner"
    const val POSICION_MANUAL = "posicion_manual"
    const val ADMIN = "admin"
    const val ADMIN_EDITOR = "admin_editor"

    fun establecimientos(categoria: Categoria) = "establecimientos/${categoria.name}"
}

/**
 * Flujo completo del MVP:
 *
 *   plazas -> categorias -> establecimientos -> destino -> navegacion AR
 *                                                  |            |
 *                                                  +-> mapa 2D <+
 *
 * La camara solo se abre al entrar en `navegacion` o en `escaner`.
 */
@Composable
fun AppRumbo(
    vistaModelo: NavegacionViewModel,
    administracion: AdminViewModel,
) {
    val navegador = rememberNavController()
    val estado by vistaModelo.estado.collectAsStateWithLifecycle()
    val plazas by vistaModelo.plazas.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vistaModelo.comprobarSoporteAr() }

    NavHost(navController = navegador, startDestination = Rutas.PLAZAS) {

        composable(Rutas.PLAZAS) {
            LaunchedEffect(Unit) { vistaModelo.recargarPlazas() }
            PantallaPlazas(
                plazas = plazas,
                alElegirPlaza = { plazaId ->
                    vistaModelo.seleccionarPlaza(plazaId)
                    navegador.navigate(Rutas.CATEGORIAS)
                },
                alAbrirAdmin = {
                    administracion.recargar()
                    navegador.navigate(Rutas.ADMIN)
                },
            )
        }

        composable(Rutas.CATEGORIAS) {
            val plaza = estado.plaza
            if (plaza == null) {
                VolverA(navegador, Rutas.PLAZAS)
            } else {
                PantallaCategorias(
                    plaza = plaza,
                    alElegirCategoria = { navegador.navigate(Rutas.establecimientos(it)) },
                    alVolver = { navegador.popBackStack() },
                )
            }
        }

        composable(Rutas.ESTABLECIMIENTOS) { entrada ->
            val plaza = estado.plaza
            val categoria = entrada.arguments?.getString("categoria")
                ?.let { runCatching { Categoria.valueOf(it) }.getOrNull() }
            if (plaza == null || categoria == null) {
                VolverA(navegador, Rutas.PLAZAS)
            } else {
                PantallaEstablecimientos(
                    plaza = plaza,
                    categoria = categoria,
                    distanciaEstimada = { establecimientoId ->
                        plaza.establecimiento(establecimientoId)
                            ?.let { vistaModelo.distanciaEstimadaMetros(it) }
                    },
                    alElegirEstablecimiento = { establecimientoId ->
                        vistaModelo.seleccionarDestino(establecimientoId)
                        navegador.navigate(Rutas.DESTINO)
                    },
                    alVolver = { navegador.popBackStack() },
                )
            }
        }

        composable(Rutas.DESTINO) {
            val plaza = estado.plaza
            val destino = estado.destino
            if (plaza == null || destino == null) {
                VolverA(navegador, Rutas.PLAZAS)
            } else {
                PantallaDestino(
                    plaza = plaza,
                    destino = destino,
                    distanciaMetros = vistaModelo.distanciaEstimadaMetros(destino),
                    estadoAr = estado.estadoAr,
                    alComenzar = {
                        vistaModelo.iniciarNavegacion()
                        navegador.navigate(Rutas.NAVEGACION)
                    },
                    alVerMapa = { navegador.navigate(Rutas.MAPA) },
                    alVolver = { navegador.popBackStack() },
                )
            }
        }

        composable(Rutas.NAVEGACION) {
            PantallaNavegacionAr(
                vistaModelo = vistaModelo,
                estado = estado,
                alEscanearQr = { navegador.navigate(Rutas.ESCANER) },
                alMarcarEnMapa = { navegador.navigate(Rutas.POSICION_MANUAL) },
                alVerMapa = { navegador.navigate(Rutas.MAPA) },
                alSalir = {
                    vistaModelo.terminarNavegacion()
                    navegador.popBackStack(Rutas.PLAZAS, inclusive = false)
                },
            )
        }

        composable(Rutas.ESCANER) {
            val plaza = estado.plaza
            var error by remember { mutableStateOf<String?>(null) }
            PantallaEscanerQr(
                puntosDePrueba = plaza?.puntosQr.orEmpty(),
                alLeer = { texto ->
                    error = vistaModelo.fijarPoseDesdeQr(texto)
                    if (error == null) navegador.popBackStack()
                },
                alElegirPunto = { punto ->
                    error = vistaModelo.fijarPoseDesdeQr(PayloadQr.codificar(punto))
                    if (error == null) navegador.popBackStack()
                },
                alVolver = { navegador.popBackStack() },
                mensajeError = error,
            )
        }

        composable(Rutas.POSICION_MANUAL) {
            val plaza = estado.plaza
            if (plaza == null) {
                VolverA(navegador, Rutas.PLAZAS)
            } else {
                PantallaPosicionManual(
                    plaza = plaza,
                    pisoSugerido = estado.pose?.pisoId ?: estado.destino?.pisoId,
                    alConfirmar = { pisoId, punto, mirando ->
                        vistaModelo.fijarPoseManual(pisoId, punto, mirando)
                        navegador.popBackStack()
                    },
                    alVolver = { navegador.popBackStack() },
                )
            }
        }

        composable(Rutas.MAPA) {
            val plaza = estado.plaza
            if (plaza == null) {
                VolverA(navegador, Rutas.PLAZAS)
            } else {
                PantallaMapa2D(
                    plaza = plaza,
                    destino = estado.destino,
                    ruta = vistaModelo.ruta,
                    pose = estado.pose,
                    instruccion = estado.navegacion.instruccion.takeIf { it.isNotBlank() },
                    puedeSeguirEnAr = estado.destino != null && estado.modo != ModoNavegacion.SOLO_MAPA,
                    alSeguirEnAr = {
                        navegador.navigate(Rutas.NAVEGACION) {
                            launchSingleTop = true
                            popUpTo(Rutas.NAVEGACION) { inclusive = true }
                        }
                    },
                    alVolver = { navegador.popBackStack() },
                )
            }
        }

        composable(Rutas.ADMIN) {
            val plazasAdmin by administracion.plazas.collectAsStateWithLifecycle()
            PantallaAdminPlazas(
                plazas = plazasAdmin,
                alAbrirEditor = { plazaId ->
                    administracion.abrir(plazaId)
                    navegador.navigate(Rutas.ADMIN_EDITOR)
                },
                alCrear = { nombre, ciudad, ancho, alto ->
                    val id = administracion.crearPlaza(nombre, ciudad, ancho, alto)
                    administracion.abrir(id)
                    navegador.navigate(Rutas.ADMIN_EDITOR)
                },
                alEliminar = { administracion.eliminarPlaza(it) },
                alVolver = {
                    vistaModelo.recargarPlazas()
                    navegador.popBackStack()
                },
            )
        }

        composable(Rutas.ADMIN_EDITOR) {
            val plazaEditada by administracion.plaza.collectAsStateWithLifecycle()
            val validacion by administracion.validacion.collectAsStateWithLifecycle()
            val cambios by administracion.cambiosSinGuardar.collectAsStateWithLifecycle()
            val plaza = plazaEditada
            if (plaza == null) {
                VolverA(navegador, Rutas.ADMIN)
            } else {
                PantallaEditorMapa(
                    plaza = plaza,
                    validacion = validacion,
                    cambiosSinGuardar = cambios,
                    alColocarEstablecimiento = { pisoId, punto, nombre, categoria ->
                        administracion.colocarEstablecimiento(pisoId, punto, nombre, categoria)
                    },
                    alColocarNodo = { pisoId, punto, tipo -> administracion.colocarNodo(pisoId, punto, tipo) },
                    alConectar = { a, b, tipo, coste -> administracion.conectar(a, b, tipo, coste) },
                    alColocarQr = { pisoId, nodoId, rumbo, descripcion ->
                        administracion.colocarQr(pisoId, nodoId, rumbo, descripcion)
                    },
                    alEliminarNodo = { administracion.eliminarNodo(it) },
                    alAgregarPiso = { nombre, ancho, alto, norte ->
                        administracion.agregarPiso(nombre, ancho, alto, norte)
                    },
                    alAjustarNorte = { pisoId, grados -> administracion.ajustarRumboNorte(pisoId, grados) },
                    alGuardar = { administracion.guardar() },
                    alVolver = {
                        administracion.guardar()
                        administracion.cerrar()
                        vistaModelo.recargarPlazas()
                        navegador.popBackStack()
                    },
                )
            }
        }
    }
}

/** Salvaguarda: si falta el estado necesario, se vuelve a una pantalla valida. */
@Composable
private fun VolverA(navegador: NavHostController, ruta: String) {
    LaunchedEffect(ruta) {
        navegador.popBackStack(ruta, inclusive = false)
    }
}
