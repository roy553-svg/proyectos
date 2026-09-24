# Arquitectura

## Principio que manda sobre todo lo demás

```
Base de datos de la plaza
        v
      Mapa (pisos, locales, geometría)
        v
Grafo de navegación (nodos + aristas)
        v
      A* / Dijkstra
        v
      Ruta (lista de puntos)
        v
Posición del usuario  <--  ARCore / QR / sensores
        v
      Flecha AR
```

La cámara **no** decide la ruta. La cámara aporta dos cosas: la imagen real de
fondo y la orientación del teléfono. Todo lo demás es determinista y sale de los
datos de la plaza. No hay IA generativa, ni chatbot, ni asistente, ni visión
artificial interpretando escaparates.

## Módulos

El proyecto son dos módulos Gradle, y esa separación es la que permite cambiar el
sistema de posicionamiento sin reescribir la navegación.

### `:nucleo` — Kotlin/JVM puro, sin Android

Compila y se testea en cualquier máquina con un JDK 17 o superior. Aquí está todo
lo que puede probarse sin un teléfono:

| Paquete | Contenido |
|---|---|
| `modelo` | `Plaza`, `Piso`, `Establecimiento`, `Nodo`, `Arista`, `PuntoQr`, `Punto2D`, `Rectangulo` y `Geometria` (convenciones de coordenadas y ángulos) |
| `grafo` | `GrafoNavegacion` (construcción y validación) y `AEstrella` |
| `ruta` | `Ruta`, `PuntoRuta`, tramos por piso |
| `navegacion` | `MotorNavegacion`: avance de puntos, giros, desvío, cambio de piso, llegada |
| `posicionamiento` | `PoseMapa`, `PoseAr`, `AlineacionAr`, `OrientacionAr`, `FiltroPose`, `FuentePosicion` |
| `qr` | `PayloadQr`: formato de texto de los QR |
| `datos` | `RepositorioPlazas` (interfaz), `RepositorioPlazasJson`, almacenes de archivos y memoria, `SemillaPlazas` |

Los datos de la plaza de demostración viven en
`nucleo/src/main/resources/plazas/plaza_aurora.json` y se leen por el
`ClassLoader`, así que sirven igual en JVM y en Android.

### `:app` — Android (Compose + ARCore + CameraX)

| Paquete | Contenido |
|---|---|
| `ar` | `DisponibilidadAr`, `SesionArCore`, `RenderizadorFondoCamara` (OpenGL ES 2.0), `VistaCamaraAr`, `ProyeccionAr` |
| `posicionamiento` | `ProveedorPose` (interfaz), `ProveedorPoseArCore`, `ProveedorPoseSensores` |
| `datos` | `FabricaRepositorio`: almacenamiento local en `filesDir/plazas` |
| `ui/pantallas` | plazas, categorías, establecimientos, destino, navegación AR, mapa 2D, escáner QR, posición manual |
| `ui/admin` | `AdminViewModel`, lista de plazas, editor visual de mapas, generador de QR |
| `ui/componentes` | `FlechaAr`, `MapaPiso`, cámara simple, permiso de cámara |
| `ui/tema` | identidad visual propia (colores, tipografía, espacios) |
| raíz | `NavegacionViewModel` (orquesta todo), `MainActivity` (grafo de navegación) |

## Flujo de la aplicación

```
plazas -> categorías -> establecimientos -> destino --(COMENZAR NAVEGACIÓN)--> AR
                                              |                                |
                                              +---------> mapa 2D <------------+
                                                             ^
                                       escáner QR / marcar en el mapa
```

La cámara se abre **solo** al entrar en la pantalla AR o en el escáner de QR, y
se cierra al salir de ellas.

## Dónde se enchufa un sistema de posicionamiento nuevo

```
ProveedorPose  (app/posicionamiento/ProveedorPose.kt)
   +- ProveedorPoseArCore     odometría visual-inercial
   +- ProveedorPoseSensores   brújula + pasos
   +- (futuro) ProveedorPoseBle / WifiRtt / Uwb
```

Una implementación nueva solo tiene que publicar `StateFlow<PoseMapa?>`. El
`MotorNavegacion`, el mapa, el grafo, A\*, la flecha y toda la UI quedan intactos.

## Decisiones técnicas y por qué

- **Kotlin + Jetpack Compose + Material 3**: es el camino actual y estable en
  Android; una sola `Activity` y navegación con `navigation-compose`.
- **ARCore + OpenGL ES 2.0 propio** en lugar de Sceneform: Sceneform está
  descontinuado. `RenderizadorFondoCamara` pinta la imagen de cámara con el mismo
  enfoque que los ejemplos oficiales (`Frame.transformCoordinates2d`), así que la
  imagen queda correcta en cualquier rotación y relación de aspecto.
- **La flecha se dibuja en Compose, no en 3D**: es el elemento principal y debe
  verse siempre, sin depender de que ARCore detecte un plano. El *anclaje* real al
  mundo lo aporta el marcador del siguiente punto, que sí se proyecta con las
  matrices de vista/proyección del frame.
- **CameraX solo para lo que ARCore no cubre**: la vista sin AR y el escáner de QR.
  Nunca hay dos consumidores de la cámara a la vez.
- **ML Kit Barcode Scanning** (modelo local, sin red) para leer QR y **ZXing** para
  generarlos en el panel de administración.
- **Almacenamiento local en JSON** con `kotlinx.serialization`, detrás de
  `RepositorioPlazas`. Sin Room: no hace falta un ORM para unas cuantas plazas, y
  así el módulo `:nucleo` no arrastra procesadores de anotaciones ni Android.
- **Sin dependencias de red**: la app funciona entera sin conexión.
- **Android 9 (API 28) como mínimo**, `compileSdk`/`targetSdk` 35.

## Pruebas

40 pruebas unitarias en `:nucleo` cubren geometría y ángulos, A\* (incluidos los
enlaces verticales), el motor de navegación (recorrido completo simulado,
desvío + recálculo, cambio de piso, llegada), la alineación AR (ida y vuelta
exacta), la orientación por cuaternión, el formato de los QR y la validez de los
datos de demostración.

```
cd navegacion-ar-plazas
gradle :nucleo:test        # o ./gradlew :nucleo:test
```

El módulo `:app` necesita el SDK de Android; `settings.gradle.kts` lo incluye solo
si detecta `local.properties`, `ANDROID_HOME` o `ANDROID_SDK_ROOT`, para que las
pruebas del núcleo se puedan ejecutar en cualquier máquina o en CI sin SDK.
