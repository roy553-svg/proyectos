# Cómo se consigue que la flecha virtual apunte al establecimiento real

Este documento responde, una por una, a las diez preguntas del requisito 17 del
enunciado. Es la parte técnica que sostiene todo lo demás: si esto no está bien
planteado, la app es una brújula con cámara.

Todas las convenciones de coordenadas están en
`nucleo/src/main/kotlin/com/rumbo/nucleo/modelo/Geometria.kt` y verificadas por
`GeometriaTest`.

## Resumen en una línea

ARCore sabe **cómo se mueve** el teléfono con mucha precisión, pero no sabe
**dónde está** ni **dónde está el norte**. El mapa sabe dónde está cada tienda.
Basta emparejar los dos sistemas **una vez** (con un QR o con un punto marcado a
mano) para poder convertir cualquier pose de ARCore en una posición del mapa, y
cualquier punto del mapa en una dirección de la pantalla.

## 1. Cómo se obtiene la posición del usuario

Tres fuentes, todas detrás de la misma interfaz
(`app/.../posicionamiento/ProveedorPose.kt`):

| Fuente | Precisión declarada | Cuándo se usa |
|---|---|---|
| QR (`FuentePosicion.QR`) | ~0,5 m | punto de partida y reanclaje |
| Punto marcado en el mapa (`MANUAL`) | ~3 m | si no hay QR impreso |
| Odometría de ARCore (`AR_ODOMETRIA`) | 0,5 m + 2 % de lo caminado | mientras se camina |
| Brújula + pasos (`PASOS_BRUJULA`) | ~6 m | dispositivos sin ARCore |

El GPS **no** se usa para posicionar dentro del edificio: su error típico en
interiores (15–30 m) es mayor que el ancho de un pasillo. El enum
`FuentePosicion` deja el hueco reservado (`GPS`) y la interfaz `ProveedorPose` es
el punto de extensión para BLE, Wi-Fi RTT o UWB.

## 2. Cómo se obtiene la orientación del teléfono

En modo AR, del cuaternión de `Frame.camera.displayOrientedPose`. Se rota el eje
de visión (0, 0, −1) por el cuaternión y se proyecta al plano horizontal:

```
rumboAr = atan2(f.x, −f.z)      // grados horarios desde el eje −Z del mundo AR
```

Si el teléfono apunta casi al suelo o al techo, ese vector deja de tener
componente horizontal útil: entonces se usa el eje vertical de la pantalla, con
el signo corregido según se mire arriba o abajo. Está implementado en
`OrientacionAr.rumboDeCamara` y probado en `OrientacionArTest`, incluido el caso
degenerado.

En modo sin AR se usa `TYPE_ROTATION_VECTOR` (fusión de acelerómetro, giroscopio
y magnetómetro que hace Android) con `remapCoordinateSystem(AXIS_X, AXIS_Z)`,
que da el azimut de la cámara trasera con el teléfono en vertical.

## 3. Cómo se representa la posición del establecimiento

En metros, dentro del marco del piso: origen en la esquina inferior izquierda del
plano, X a la derecha, Y hacia arriba. Cada establecimiento tiene `posicion`
(donde se dibuja en el mapa) y `nodoDestinoId` (el nodo del grafo que está en su
puerta, que es el verdadero destino de la ruta).

Nada de esto sale de la visión por computador: sale del mapa configurado por el
administrador.

## 4. Cómo se convierten las coordenadas del establecimiento en una dirección del mundo AR

En el instante del ajuste (`AlineacionAr.alinear`) se guardan:

- `origenAr`: proyección horizontal de la traslación de ARCore, como `(x, −z)`,
- `origenMapa`: la posición conocida en el mapa,
- `desfaseGrados = rumboMapa − rumboAr`.

Después, para cada frame:

```
desplazamientoAr = (x − origenAr.x , −z − origenAr.y)
posicionMapa     = origenMapa + rotarHorario(desplazamientoAr, desfase)
rumboMapa        = rumboAr + desfase
```

La escala es 1:1 porque ARCore trabaja en metros, igual que el mapa. La operación
inversa (`aPuntoAr`) coloca cualquier punto del mapa en el mundo AR, y es la que
permite proyectar el marcador del siguiente punto sobre la imagen de cámara con
las matrices de vista y proyección del frame (`ProyeccionAr`).

Con eso, la flecha necesita un solo número:

```
ángulo de pantalla = normalizar180(rumboAlObjetivo − rumboDelUsuario)
```

0 = recto, positivo = a la derecha, negativo = a la izquierda. Lo calcula
`MotorNavegacion` y lo dibuja `FlechaAr`. `AlineacionArTest` comprueba los cuatro
casos (frente, derecha, giro de cámara e inversa exacta).

## 5. Cómo se mantiene estable la flecha mientras el usuario camina

Cuatro medidas, todas necesarias:

1. **La pose la da ARCore**, no la brújula: la odometría visual-inercial no salta
   con el metal de las tiendas.
2. **Filtro de pose** (`FiltroPose`): suavizado exponencial de la posición y media
   circular del rumbo, para que el paso de 359° a 1° no dé un latigazo. Un salto
   grande y fiable (un QR nuevo) se aplica de golpe en lugar de suavizarse.
3. **Animación por el camino corto** en la flecha: se acumula el ángulo con
   diferencias de como mucho 180°, así que nunca gira "al revés".
4. **Histéresis en el avance de puntos**: un punto se considera alcanzado a 2,5 m
   o cuando el siguiente ya está más cerca y el usuario va por el tramo correcto.
   Sin eso, la flecha oscila entre dos nodos al pasar por una esquina.

## 6. Cómo se manejan los errores del GPS

No se manejan: el GPS no participa en el posicionamiento interior. Si en el
futuro se usa en el exterior (para sugerir la plaza más cercana), la regla es
descartar cualquier fix con precisión declarada peor que 10 m y no mezclarlo con
la odometría, porque un salto de GPS de 20 m arruinaría la alineación AR.
`MotorNavegacion` ya avisa al usuario cuando la fuente es GPS o la precisión pasa
de 10 m, en lugar de fingir exactitud.

## 7. Cómo se maneja el interior, donde no hay GPS preciso

Con un **ajuste inicial explícito** y **odometría relativa** después:

```
QR o punto en el mapa  ->  AlineacionAr  ->  odometría de ARCore  ->  pose del mapa
                                   ^                                      |
                                   +------- reanclaje (otro QR) ----------+
```

Los QR se colocan donde el usuario se para de forma natural: entradas, cruce del
pasillo central, junto a los ascensores. Cada reanclaje borra la deriva
acumulada. En los cambios de piso el reanclaje es automático: el nodo del
ascensor en el piso de llegada es una posición conocida, y la app pide confirmar
"ya estoy en ese piso".

## 8. Cómo se detecta que el usuario llegó

`MotorNavegacion` compara la distancia al nodo de destino con
`radioDestinoMetros` (4 m por defecto, configurable). Al entrar en ese radio la
fase pasa a `LLEGADO`, se muestra "Has llegado" y la flecha desaparece. Los
nodos intermedios usan un radio menor (2,5 m). Ambos valores están en
`ConfigNavegacion` porque dependen del tamaño real de los pasillos.

Nota honesta: con un error de posición de ±2 m, "has llegado" significa "estás en
la puerta, mira alrededor", no "estás exactamente en el mostrador".

## 9. Qué limitaciones tiene ARCore

- **No sabe dónde está**. El origen del mundo es donde arrancó la sesión y el giro
  respecto al norte es arbitrario. Sin un ajuste externo no puede apuntar a una
  tienda concreta.
- **Deriva**. La odometría acumula entre el 1 % y el 3 % de la distancia recorrida,
  y más si se camina rápido, se gira brusco o se tapa la cámara.
- **Pierde el seguimiento** con poca luz, superficies lisas sin textura
  (suelos pulidos uniformes), movimiento rápido o cristales y espejos.
- **Cada sesión nueva estrena origen**: al volver del mapa 2D hay que realinear
  (lo hace `prepararNuevaSesion`, degradando la precisión declarada).
- **No todos los dispositivos lo soportan**, y en los que sí, requiere *Google
  Play Services for AR* instalado.
- **Sceneform está descontinuado**: el camino soportado hoy es ARCore + OpenGL ES
  propio (lo que hace `RenderizadorFondoCamara`) o un motor externo.
- **La API Geospatial** (que sí daría posición absoluta) necesita red y cobertura
  VPS, y dentro de un centro comercial no es fiable.
- **No hay altímetro fiable**: el cambio de piso no se detecta solo, se confirma.

## 10. Qué haría falta para precisión de nivel comercial

Lo que puede hacer el teléfono solo:

- orientación (muy bien), desplazamiento relativo (bien), ruta y lógica de
  navegación (perfecto, es determinista), lectura de QR (perfecto).

Lo que exige infraestructura en la plaza:

| Tecnología | Precisión típica | Coste / trabajo |
|---|---|---|
| QR impresos | 0,3–1 m en el punto, deriva después | casi nulo (imprimir y pegar) |
| Balizas BLE (iBeacon/Eddystone) | 2–5 m continuo | 1 baliza cada 10–15 m, pilas, mantenimiento |
| Wi-Fi RTT (802.11mc) | 1–2 m | puntos de acceso compatibles y dispositivos compatibles |
| UWB | 10–30 cm | anclas cableadas, caro, pocos móviles lo llevan |
| Fingerprinting Wi-Fi/magnético | 3–8 m | levantamiento del edificio y recalibrado periódico |

La recomendación para pasar de prototipo a producto es: **QR + BLE**. Los QR
resuelven el arranque y los reanclajes voluntarios; las balizas corrigen la
deriva sin que el usuario haga nada. Ambas entran como nuevas implementaciones de
`ProveedorPose`, sin tocar el mapa, el grafo, A\*, el motor de navegación ni la
flecha.
