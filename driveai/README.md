# DriveAI

Asistente de voz vehicular ultra-ligero para el **90%+ de los coches del mundo**:
radios chinas Android 9+, Android Auto, Android Automotive OS, tablets montadas
y pantallas Tesla.

El coche solo ejecuta un **cliente delgado** (captura audio, pinta el HUD,
reproduce voz). El razonamiento, los embeddings y la memoria viven en el
gateway.

```
┌────────────────────────┐         ┌──────────────────────────────────────┐
│  CABINA (cliente)      │  HTTP   │  GATEWAY (NestJS)                    │
│  · HUD + Orbe de voz   │ ──────► │  · Enrutador de IA                   │
│  · WebView / CarApp    │         │      Gemini → respaldo → motor local │
│  · VoiceRecorder.kt    │ ◄────── │  · Memoria jerarquica en 4 capas     │
│  < 35 MB RAM, < 3% CPU │         │  · Abstraccion vehicular             │
└────────────────────────┘         └───────────────┬──────────────────────┘
                                                   │
                                   ┌───────────────┴───────────────┐
                                   │ postgres + pgvector │ redis 7 │
                                   └───────────────────────────────┘
```

---

## Arranque en 60 segundos

```bash
cp .env.example .env          # opcional: pega tu GEMINI_API_KEY
docker compose up -d
curl http://localhost:8080/api/assistant/health
```

Abre `http://localhost:8080/` — el gateway sirve el cockpit compilado desde el
mismo origen, así que la radio no sufre CORS ni DNS extra.

**Sin clave de API el sistema arranca igual** y responde con su motor
determinista local. No es un modo degradado de emergencia: es el piso mínimo
garantizado del producto.

### Prueba de humo

```bash
node scripts/smoke.mjs        # 33 comprobaciones de extremo a extremo
```

Cubre lo que no puede romperse en carretera: latencia del motor determinista,
brevedad de seguridad vial, las 4 capas de memoria, precedencia de reglas,
bloqueo de comandos en movimiento y GDPR.

### Desarrollo

```bash
cd backend  && npm install && npm run start:dev   # :8080
cd frontend && npm install && npm run dev         # :5173 (proxy a :8080)
```

---

## 1 · Restricciones de conducción y hardware

**Filosofía "tipo DOOM":** una sola pantalla de cabina. Cero pestañas, cero
menús anidados. Todo se opera con **un toque o por voz**.

| Presupuesto | Objetivo | Cómo se cumple |
|---|---|---|
| RAM del cliente | < 35 MB | Un WebView y nada más: sin Compose, sin DI, sin OkHttp, sin librerías de imágenes. Heap medido ~18 MB. |
| CPU | < 3% | Un único `setInterval` de 1 s para el HUD; `pauseTimers()` al pasar a segundo plano. |
| Descarga | 57 KB gzip | Un solo chunk, `target: es2019` (WebView de Android 9 = Chromium 66+). |
| APK | ~2.5 MB | R8 en modo completo + `shrinkResources`. |

**Ergonomía:** negro OLED `zinc-950` con acentos esmeralda / cian / ámbar.
Botones de 64–72 px mínimo. Dígitos tabulares para que el velocímetro no baile.
Fondo negro pintado antes de que cargue el JS: cero destello blanco de noche.

---

## 2 · Voz y preguntas (Gemini + entrada táctil)

- **SDK oficial `@google/genai`** con cascada de modelos:
  `gemini-3.8-flash` → `gemini-2.5-flash` → motor determinista local.
  Cada escalón tiene presupuesto de latencia duro (`GEMINI_TIMEOUT_MS`);
  si se agota, baja un nivel. El conductor **siempre** recibe respuesta.
- **Orbe de voz** con estados `ESCUCHANDO` / `PROCESANDO` / `HABLANDO` y TTS
  en `es-MX`.
- **Barra táctil** con botón PREGUNTAR y 4 sugerencias de un toque.

### Regla de brevedad, aplicada en el servidor

El prompt pide 2–3 oraciones cortas, pero un prompt se puede ignorar. El recorte
de `enforceBrevity()` no: limpia markdown, corta a `MAX_SENTENCES` oraciones y
aplica un tope de `MAX_ANSWER_CHARS` (~10 s de audio hablado), cortando en la
última pausa natural.

---

## 3 · Motor de memoria jerárquico en 4 capas

PostgreSQL + pgvector, búsqueda semántica por coseno con índices **HNSW**
(no `ivfflat`: creado sobre tabla vacía deja el recall degradado hasta un
`REINDEX`).

| Capa | Tabla | Contenido | Ventana |
|---|---|---|---|
| **L1** Memoria de trabajo | `l1_working_memory` | Turnos del trayecto en curso | Últimos 4 turnos |
| **L2** Preferencias | `l2_preferences` | Gustos musicales, comida, rutas, clima | Top 6 por similitud |
| **L3** Lugares episódicos | `l3_places` | Visitas, rating, coordenadas | Top 4 |
| **L4** Hechos consolidados | `l4_facts` | Domicilio, oficina, horarios fijos | Top 5 |

### Anti-alucinación

Esto es lo que impide que el asistente invente cosas sobre el conductor:

1. **`origin` explícito.** Cada preferencia es `declared` (el conductor lo dijo)
   o `inferred` (el sistema lo dedujo). Una declaración explícita **siempre gana**
   sobre una inferencia en el `ON CONFLICT`.
2. **`evidence` textual.** Se guarda la frase literal que respalda cada dato.
3. **El prompt marca la diferencia.** Lo inferido llega al modelo etiquetado
   `[INFERIDO 62%]` con la instrucción de matizarlo ("creo que", "me parece").
4. **Sin memoria, se dice.** Si Postgres no responde, el prompt lleva
   `AVISO: memoria no disponible. No inventes historial del conductor.`

La extracción `declared` es determinista (regex + léxico), sin llamar al LLM:

```
"Me encanta el rock en español cuando manejo"  →  music   | rock en español      | like
"Me gusta mucho comer tacos al pastor"         →  food    | tacos al pastor      | like
"No me gusta el tráfico del periférico"        →  route   | tráfico del periférico | dislike
"Trabajo de 9 a 18 todos los días"             →  L4: office_schedule = 9:00-18:00, cron 0 9 * * 1-5
```

---

## 4 · Telemetría y control seguro

**`VehicleProvider`** normaliza tres backends tras una sola interfaz:

| Proveedor | Uso | Transporte |
|---|---|---|
| `GenericAndroid` | Radios Android 9+ | Puente HTTP local al CAN-Bus / OBD-II Bluetooth |
| `TeslaProvider` | Tesla | Fleet API v1, OAuth 2.0 **PKCE** (S256) |
| `MockVehicleProvider` | Desarrollo | Ciclo de conducción simulado |

**HUD:** velocímetro, marcha `P R N D`, batería %, autonomía km, TPMS de los 4
neumáticos, estado de seguros.

### Política de seguridad (no negociable)

```
comando físico  +  (velocidad > LOCKOUT_SPEED_KPH  ó  marcha ∈ {D, R})  →  BLOQUEADO
```

- La decisión se toma con **telemetría leída justo antes del comando**, nunca
  con el estado cacheado del HUD.
- El clima sí se ajusta en movimiento; los seguros no.
- Todo intento queda auditado en `vehicle_command_log` con velocidad y marcha.
- La UI consulta `safety.lockCommandsAllowed` para **atenuar el botón antes**
  de que el conductor lo presione.

---

## 5 · Resiliencia extrema

Túnel, estacionamiento subterráneo, zona rural o sin clave de API:

> **Nunca** una pantalla de error. **Nunca** un spinner infinito. **Nunca** un
> cuelgue.

Entra el **motor de reglas local determinista**: ~20 reglas precompiladas sobre
telemetría y memoria ya cargada. Sin red, sin I/O, sin base de datos.

```
Latencia medida, extremo a extremo (HTTP incluido):  5.8 ms/consulta
Objetivo:                                            < 15 ms
```

Cubre telemetría (batería, autonomía, TPMS, seguros, clima, marcha), navegación
local por Capas 3 y 4, recuerdo de preferencias, hora, y FAQ de seguridad vial
(lluvia, niebla, ponchadura, sobrecalentamiento, testigos del tablero, frenos).

Cada capa degrada por separado y en silencio:

| Falla | Comportamiento |
|---|---|
| Sin Gemini | Motor determinista local |
| Sin Postgres | Responde sin memoria, lo declara en el prompt |
| Sin Redis | Caché LRU en memoria; el rate limit deja pasar |
| Sin bus CAN | Último estado conocido con `stale: true` |
| Sin gateway | El cockpit responde desde la cabina |

---

## 6 · Estructura

```
driveai/
├── docker-compose.yml        postgres(pgvector) + redis + backend
├── .env.example              todo opcional, con valores por defecto seguros
├── backend/                  NestJS + TypeScript
│   ├── Dockerfile            multi-stage: cockpit → build → deps → runtime Alpine
│   ├── db/init/001_schema.sql  las 4 capas + HNSW + auditoría
│   └── src/
│       ├── assistant/        enrutador de IA + motor de reglas offline
│       ├── memory/           4 capas + embeddings (Gemini o hash local)
│       ├── vehicles/         VehicleProvider + política de seguridad
│       └── infra/            pools tolerantes a fallos (pg, redis)
├── frontend/                 React + Tailwind + lucide-react
│   └── src/components/       HudBar · VoiceOrb · QueryBar · TelemetryCard
│                             AnswerPanel · SettingsModal
└── android/                  Kotlin, minSdk 28
    └── app/src/main/
        ├── AndroidManifest.xml     CarAppService + AAOS + botón del volante
        └── java/com/driveai/cockpit/
            ├── MainActivity.kt      WebView delgado + puente JS
            ├── VoiceRecorder.kt     PCM 16 kHz, corte por silencio
            ├── DriveAiCarAppService.kt / DriveAiSession.kt / CockpitScreen.kt
            └── SteeringWheelReceiver.kt
```

---

## API

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/assistant/message` | Pregunta libre (voz o texto) |
| `POST` | `/api/assistant/transcribe` | PCM 16 kHz crudo → texto |
| `GET`  | `/api/assistant/health` | Modo activo y modelos |
| `GET`  | `/api/vehicles/status` | Telemetría + veredicto de seguridad |
| `POST` | `/api/vehicles/commands` | Comandos seguros (clima, seguros) |
| `GET`  | `/api/vehicles/tesla/auth-url` | Inicia OAuth PKCE |
| `GET`  | `/api/memory` | Vista de las 4 capas |
| `POST` | `/api/memory/{preferences,places,facts}` | Alta manual por capa |
| `POST` | `/api/memory/privacy` | Interruptores de micrófono / ubicación / memoria |
| `GET`  | `/api/memory/export` | **GDPR:** portabilidad en JSON |
| `DELETE` | `/api/memory` | **GDPR art. 17:** derecho al olvido |

---

## Privacidad por diseño

- **El audio nunca se escribe a disco.** Vive en memoria, se transcribe, se
  descarta.
- **La memoria vive en el gateway, no en la radio.** `databaseEnabled = false`
  en el WebView; el coche no guarda historial.
- **Interruptores en tiempo real** de micrófono, ubicación y memoria.
- **Exportación JSON** completa con las 4 capas y el log de comandos.
- **Purga total o por capa**, inmediata y en cascada (`ON DELETE CASCADE`).
- **Permisos mínimos:** micrófono, red y ubicación opcional. Sin contactos, sin
  SMS, sin cámara.

---

## Notas de operación

- Los identificadores `gemini-3.8-flash` / `gemini-2.5-flash` son configurables
  (`GEMINI_PRIMARY_MODEL` / `GEMINI_FALLBACK_MODEL`). Si tu clave no tiene acceso
  al primario, la cascada baja sola al de respaldo y, si tampoco, al motor local
  — sin tocar código.
- `MainActivity.COCKPIT_URL` apunta a `http://10.0.2.2:8080` (host desde el
  emulador). En un coche real, cámbialo por la IP o el dominio del gateway.
- Los embeddings usan `text-embedding-004` (768 dim). Sin clave, cae a un
  embedding local determinista por hashing de n-gramas: no es semántico, pero es
  estable y mantiene la búsqueda vectorial operativa offline.
