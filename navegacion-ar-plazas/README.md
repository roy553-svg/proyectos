# Rumbo — navegación AR hacia establecimientos en centros comerciales

Aplicación Android que guía al usuario hasta una tienda concreta con una flecha en
realidad aumentada sobre la imagen real de la cámara.

```
ABRIR APP -> SELECCIONAR PLAZA -> SELECCIONAR ESTABLECIMIENTO -> CONFIRMAR DESTINO
   -> COMENZAR NAVEGACIÓN (aquí, y solo aquí, se abre la cámara)
   -> ¿DÓNDE ESTÁS? (QR o punto en el mapa) -> RUTA -> FLECHA AR -> "HAS LLEGADO"
```

Sin chatbot, sin IA generativa y sin asistente. Todo es determinista: el mapa, los
establecimientos, las coordenadas y las rutas salen de la base de datos de la
plaza. La identidad visual es propia.

## Estado

| Parte | Estado |
|---|---|
| Modelo de datos, mapa y grafo | completo, con 40 pruebas unitarias en verde |
| A\* multipiso (ascensor/escalera) | completo y probado |
| Motor de navegación (giros, desvío + recálculo, cambio de piso, llegada) | completo y probado |
| Alineación ARCore ↔ mapa, orientación por cuaternión, filtro de pose | completo y probado |
| Posicionamiento por QR y por punto marcado a mano | completo |
| Pantallas del flujo, flecha AR, mapa 2D de respaldo | completo |
| Panel de administración con editor visual y generador de QR | completo |
| Plaza ficticia de demostración (2 pisos, 20 locales, baños, ascensores, escaleras, entradas, 52 nodos, 6 QR) | completo |
| Compilación del módulo `:app` | **pendiente de verificar en una máquina con Android SDK** (ver más abajo) |
| BLE / Wi-Fi RTT / UWB | no implementado a propósito: el MVP no los necesita (ver `docs/03`) |

## Cómo ejecutar

### Pruebas del núcleo (no hace falta Android SDK)

```bash
cd navegacion-ar-plazas
gradle :nucleo:test          # necesita un JDK 17 o superior
```

`settings.gradle.kts` solo incluye el módulo `:app` si detecta un Android SDK
(`local.properties`, `ANDROID_HOME` o `ANDROID_SDK_ROOT`), para que las pruebas del
núcleo funcionen en cualquier máquina y en CI.

### La aplicación

```bash
# Con Android Studio (Ladybug o posterior): abrir la carpeta navegacion-ar-plazas
# Desde línea de comandos, con el SDK instalado:
cd navegacion-ar-plazas
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

Requisitos: Android 9 (API 28) o superior. En dispositivos con ARCore se usa la
vista AR; en el resto, mapa 2D y brújula + pasos. La app **nunca** se cierra por
falta de soporte AR.

> **Aviso honesto**: el módulo `:app` se escribió y revisó a mano, pero no se ha
> compilado todavía porque el entorno donde se desarrolló no tiene Android SDK ni
> acceso al repositorio Maven de Google. La primera compilación en Android Studio
> puede requerir ajustes menores (imports sin usar, alguna versión de dependencia).
> El módulo `:nucleo`, que contiene toda la lógica de navegación, sí está compilado
> y con sus 40 pruebas pasando.

## Estructura

```
navegacion-ar-plazas/
├── nucleo/          lógica pura (modelo, grafo, A*, motor de ruta, posicionamiento, QR)
│   └── src/main/resources/plazas/plaza_aurora.json   plaza ficticia de demostración
├── app/             Android: Compose, ARCore, CameraX, ML Kit, panel de administración
├── herramientas/    generador reproducible de la plaza de demostración (Python)
└── docs/
    ├── 01-ARQUITECTURA.md              módulos, decisiones y dónde se enchufa cada cosa
    ├── 02-ORIENTACION-DE-LA-FLECHA.md  cómo se consigue que la flecha apunte bien (las 10 preguntas)
    ├── 03-PRECISION-Y-LIMITACIONES.md  qué puede prometer el teléfono y qué exige infraestructura
    └── 04-COMO-AGREGAR-UNA-PLAZA.md    editor visual y formato JSON
```

## Lo esencial en tres párrafos

**Por qué hace falta el paso "¿dónde estás?".** ARCore mide muy bien *cómo* se
mueve el teléfono, pero no sabe *dónde* está dentro del edificio ni dónde está el
norte; el GPS en interiores falla por 15–30 m. La app empareja una vez la pose de
ARCore con una posición conocida del mapa (un QR, que da posición **y**
orientación, o dos toques en el plano) y a partir de ahí convierte cada frame en
una posición del mapa. Detalle completo en `docs/02`.

**Cómo apunta la flecha.** Con la posición y el rumbo del usuario en coordenadas
del mapa, el ángulo de la flecha es
`normalizar180(rumboAlSiguientePunto − rumboDelUsuario)`: 0 recto, positivo
derecha, negativo izquierda. Apunta al siguiente punto de la ruta, no al
establecimiento, y avanza de punto en punto con histéresis para no oscilar en las
esquinas. Además se ancla un marcador en el mundo AR sobre ese punto, proyectado
con las matrices de vista y proyección del frame.

**Qué pasa cuando algo falla.** Sin ARCore: mapa 2D y brújula + pasos. Con
seguimiento perdido: la flecha se atenúa y se explica el motivo real (poca luz,
movimiento excesivo…). Fuera de ruta: "Te has desviado" y recálculo. Cambio de
piso: instrucción de ascensor o escalera y reanclaje al confirmar. Nunca se deja al
usuario sin navegación, y la precisión se muestra tal cual es (`±N m`).

## Privacidad

Solo se piden los permisos necesarios: cámara (para AR y para leer los QR) y, de
forma opcional, ubicación aproximada. La cámara se activa únicamente al pulsar
*Comenzar navegación* o al abrir el escáner, y se libera al salir. No se guardan ni
se envían fotos ni vídeos: los frames se usan en memoria para el seguimiento AR y
se descartan.
