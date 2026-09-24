# El Profeta — App Android (Fase 3)

Cliente móvil del periódico mágico: descarga la **edición semanal publicada**
del backend y la muestra con aspecto de prensa impresa, reproduciendo las
animaciones de las noticias cuando existen.

La app **no genera contenido**: sólo consume `GET /api/v1/news?user_id=…`.

---

## 1. Stack

| Pieza | Elección |
| --- | --- |
| Lenguaje | Kotlin 2.0 |
| UI | Jetpack Compose + Material 3 |
| Navegación | `navigation-compose` |
| HTTP | Retrofit + OkHttp |
| JSON | kotlinx.serialization |
| Imágenes | Coil |
| Vídeo | Media3 / ExoPlayer |
| Caché | DataStore (Preferences) |
| Inyección de dependencias | contenedor manual (`AppContainer`) |

`minSdk 26` (permite `java.time` sin desugaring), `targetSdk`/`compileSdk 35`.

### Por qué DI manual y no Hilt

La app tiene **un** repositorio, **un** endpoint y **un** `ViewModel`. Un
contenedor explícito de 40 líneas se lee de un vistazo y evita añadir KSP y
tiempo de compilación. Si en el futuro crecen las fuentes de datos, migrar a
Hilt es cambiar `DefaultAppContainer` por módulos.

---

## 2. Arquitectura

```
ui/            Compose: portada, detalle, tema, componentes
 └── news/     NewsViewModel + NewsUiState (estado de pantalla)
domain/model/  Modelos que usa la UI (Article, Edition, NewsFeed)
data/
 ├── remote/   ProfetaApi (Retrofit) + DTOs que replican el contrato JSON
 ├── mapper/   DTO -> dominio (fechas, nulos, estados desconocidos)
 ├── local/    FeedCache (DataStore): última edición descargada
 └── repository/ NewsRepository: red primero, caché como respaldo
core/          AppContainer (Retrofit, OkHttp, Json, repositorio)
```

Reglas que se respetan en todo el proyecto:

* **Los DTO nunca llegan a la UI.** El mapper aísla a la app de cambios del
  contrato: un campo nuevo no rompe nada (`ignoreUnknownKeys = true`) y un
  `video_status` desconocido se degrada a "sin animación".
* **El estado de pantalla es un `sealed interface`** (`Loading`, `Ready`,
  `Empty`, `Error`), así la UI no tiene banderas sueltas que contradecirse.
* **Sin red se muestra la última edición descargada** con un aviso; un
  periódico semanal tiene que poder leerse en el metro.
* **ExoPlayer se libera siempre** en `onDispose` y se pausa con el ciclo de
  vida: un player vivo fuera de pantalla retiene códec y conexión.

### Estados de la portada

| Situación | Respuesta del backend | Qué ve el lector |
| --- | --- | --- |
| Edición disponible | `200` | Portada con la edición de la semana |
| Sin red, con caché | — | La edición anterior + aviso "sin conexión" |
| Sin red, sin caché | — | Mensaje de error + botón *Reintentar* |
| Lector desconocido | `404` | "Este lector no tiene preferencias registradas" |
| Sin edición publicada | `404` | "Todavía no hay una edición publicada" |

### Animaciones

`video_url` llega `null` hasta que el backend termina de generar el vídeo
(Fase 2). La app usa `video_status` para decidir:

* `ready` → se reproduce en bucle, silenciado, con ExoPlayer;
* `pending` / `processing` → imagen estática + distintivo "la animación aún se
  está revelando";
* `not_requested` / `failed` → imagen estática, sin distintivo.

---

## 3. Cómo ejecutarla

### 3.1. Requisitos

* Android Studio Ladybug (2024.2) o superior.
* JDK 17.
* Android SDK 35 y un emulador (o un dispositivo con depuración USB).
* El backend de `../backend` en marcha.

### 3.2. Arrancar el backend y sembrar datos

```bash
cd ../backend
source .venv/bin/activate
alembic upgrade head
python -m scripts.seed --reset
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

`--host 0.0.0.0` es necesario para que el emulador o el móvil lo alcancen.

### 3.3. Abrir el proyecto

```bash
# Desde Android Studio: File > Open > carpeta android/
# O por línea de comandos, una vez generado el wrapper:
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

> El binario `gradle/wrapper/gradle-wrapper.jar` y los scripts `gradlew` no
> están versionados en este repositorio. Android Studio los genera al abrir el
> proyecto; para crearlos a mano: `gradle wrapper --gradle-version 8.11.1`
> (la versión ya está fijada en `gradle/wrapper/gradle-wrapper.properties`).

### 3.4. Apuntar al backend

La URL se define en `app/build.gradle.kts` como `buildConfigField`:

| Build | `API_BASE_URL` | Uso |
| --- | --- | --- |
| `debug` | `http://10.0.2.2:8000/` | Emulador → host de desarrollo |
| `release` | `https://api.elprofeta.example/` | Producción (cámbialo) |

En un **dispositivo físico** hay que usar la IP del PC en la red local
(`http://192.168.1.X:8000/`) y añadir ese dominio a
`res/xml/network_security_config.xml`, que por defecto sólo permite tráfico sin
cifrar contra `10.0.2.2` y `localhost`. En release **todo tiene que ir por
HTTPS**.

### 3.5. Cambiar de lector

`NewsViewModel.DEFAULT_USER_ID` vale `1`. El seed del backend crea tres
lectores con preferencias distintas (1: tecnología/ciencia/magia, 2: deportes,
3: sólo noticias animadas); `NewsViewModel.changeUser(id)` recarga la portada
con otro lector.

---

## 4. Tests

Tests unitarios JVM (no requieren emulador):

```bash
./gradlew :app:testDebugUnitTest
```

| Fichero | Qué comprueba |
| --- | --- |
| `data/NewsMappersTest.kt` | DTO → dominio: orden, fechas UTC, `video_url` nulo, estados desconocidos |
| `data/ProfetaApiTest.kt` | Contrato real con `MockWebServer`: query `user_id`, parseo, 404, campos nuevos |
| `ui/NewsViewModelTest.kt` | Los cinco estados de pantalla, caché, cambio de lector |

Además, en el backend hay un test que verifica que **los DTO de esta app
siguen coincidiendo con la respuesta real de la API**
(`backend/tests/test_android_contract.py`): si alguien renombra un campo del
backend, falla ahí antes de romper la app.

---

## 5. Estado de verificación

Los ficheros Gradle, el manifiesto y el código Kotlin están completos, pero
**este proyecto no se ha compilado en el entorno donde se escribió**: la
política de red de ese contenedor bloquea `dl.google.com` (Android SDK y Google
Maven) y limita Maven Central, así que no había forma de ejecutar
`assembleDebug` ni los tests unitarios. Lo que sí está verificado
automáticamente es el **contrato con el backend**, mediante el test de contrato
mencionado arriba.

Al abrir el proyecto por primera vez, Android Studio descargará el SDK y las
dependencias; si alguna versión del catálogo (`gradle/libs.versions.toml`) no
estuviera disponible, actualizarla ahí basta: no hay versiones repetidas por el
resto del proyecto.

---

## 6. Estructura de ficheros

```
android/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml           # catálogo de versiones
│   └── wrapper/gradle-wrapper.properties
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/elprofeta/app/
        │   │   ├── ProfetaApplication.kt
        │   │   ├── MainActivity.kt
        │   │   ├── core/AppContainer.kt
        │   │   ├── data/
        │   │   │   ├── local/FeedCache.kt
        │   │   │   ├── mapper/NewsMappers.kt
        │   │   │   ├── remote/ProfetaApi.kt
        │   │   │   ├── remote/dto/NewsDto.kt
        │   │   │   └── repository/NewsRepository.kt
        │   │   ├── domain/model/NewsModels.kt
        │   │   └── ui/
        │   │       ├── components/{ArticleMedia,Masthead,StatusViews}.kt
        │   │       ├── navigation/ProfetaNavHost.kt
        │   │       ├── news/{NewsScreen,ArticleDetailScreen,NewsViewModel,NewsUiState}.kt
        │   │       └── theme/{Color,Theme,Type}.kt
        │   └── res/
        │       ├── values/{strings,themes}.xml
        │       └── xml/network_security_config.xml
        └── test/java/com/elprofeta/app/
            ├── data/{NewsMappersTest,ProfetaApiTest}.kt
            └── ui/NewsViewModelTest.kt
```
