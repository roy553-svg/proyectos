# DriveAI · Decisiones de arquitectura

Documento de las decisiones no obvias y de por qué se tomaron. Lo evidente está
en el README; aquí están los porqués.

---

## 1. Por qué el cliente de cabina es un WebView y no una app nativa

El presupuesto es **< 35 MB de RAM en un Rockchip de 1 GB**. Medido:

| Enfoque | Heap típico | Veredicto |
|---|---|---|
| Jetpack Compose + Retrofit + Coil | 55–80 MB | Fuera de presupuesto |
| Vistas XML + OkHttp | 35–45 MB | Justo en el límite, sin margen |
| **WebView + HttpURLConnection** | **~18 MB** | Elegido |

El WebView ya está residente en el sistema: su coste marginal es bajo porque el
proceso de renderizado se comparte. A cambio perdemos animaciones nativas, algo
que en una interfaz de cabina —cuatro paneles estáticos y un orbe— no importa.

`HttpURLConnection` en lugar de OkHttp/Retrofit ahorra ~1.5 MB de dex y varios
MB de heap. Para tres endpoints no compensa la ergonomía de una librería.

---

## 2. Por qué la cascada de IA baja por latencia y no por error

Lo intuitivo sería: intentar Gemini, y si devuelve error, usar el motor local.
No sirve para un coche.

El fallo real en carretera no es un `500`: es **un socket que no cierra nunca**
al entrar a un túnel. Sin presupuesto de latencia, el conductor se queda mirando
un spinner mientras conduce.

Por eso cada escalón corre contra un `AbortController` con un presupuesto duro
(`GEMINI_TIMEOUT_MS`, 3.5 s por defecto). Agotado el presupuesto, se aborta y
baja de nivel. El peor caso total está acotado: `2 × 3.5 s + 15 ms`.

El motor local es el **piso garantizado**, no un modo de emergencia. Por eso
tiene reglas escritas a mano para las preguntas que un conductor hace de verdad,
y no un mensaje genérico de "sin conexión".

---

## 3. Por qué la brevedad se aplica en el servidor

El prompt pide 2–3 oraciones. Un modelo puede ignorarlo, y lo hace con ciertas
preguntas ("explícame cómo funciona un turbo").

Un párrafo de 40 segundos leído por TTS mientras alguien conduce a 110 km/h es
un problema de seguridad, no de estilo. Por eso `enforceBrevity()` recorta
siempre, con o sin cooperación del modelo.

El corte de oraciones usa `split(/(?<=[.!?])\s+/)` y no `match(/[^.!?]+[.!?]+/g)`:
la segunda forma parte `2.4 bar` en `2.` + `4 bar`, y el TTS acaba leyendo
"dos punto. cuatro bar". Un punto solo cierra oración si va seguido de espacio.

---

## 4. Por qué HNSW y no ivfflat

`ivfflat` necesita entrenarse con datos ya cargados para construir sus listas.
El esquema se crea en el primer arranque del contenedor, con las tablas vacías:
el índice queda con **recall degradado de forma permanente** hasta un `REINDEX`
manual que nadie va a ejecutar.

Postgres lo avisa al crearlo (`NOTICE: ivfflat index created with little data`)
y es fácil pasarlo por alto entre el ruido del arranque.

HNSW se construye de forma incremental y no tiene fase de entrenamiento. Cuesta
más memoria por índice, pero el conjunto de datos de un conductor es pequeño —
decenas o cientos de filas, no millones.

---

## 5. Por qué `origin` es una columna y no un campo de confianza

La alternativa sería una sola columna `confidence`: 1.0 = declarado, < 1.0 =
inferido. Es peor por dos razones.

**Resolución de conflictos.** Si el conductor dice "ya no escucho reggaetón",
esa declaración debe ganar sobre cualquier inferencia acumulada por más visitas
que la respalden. Con una sola columna numérica habría que inventar reglas de
desempate; con `origin` explícito, el `ON CONFLICT` lo resuelve directo:

```sql
origin = CASE WHEN EXCLUDED.origin = 'declared' THEN 'declared'
              ELSE l2_preferences.origin END
```

**Lenguaje del asistente.** Un dato inferido debe decirse distinto: "creo que te
gusta la comida italiana" y no "te gusta la comida italiana". El prompt necesita
la distinción categórica, no un número que el modelo interprete a su manera.

---

## 6. Por qué la política de seguridad no confía en el HUD

El HUD se refresca cada segundo. Si el comando de "abrir seguros" se validara
contra ese estado, habría hasta **un segundo de ventana** en que el coche ya
arrancó y la caché todavía dice `speedKph: 0`.

Por eso `sendCommand()` llama a `getStatus()` antes de cada comando físico. Es
una lectura extra por comando —los comandos son raros, el HUD no— y elimina la
ventana por completo.

La UI usa el estado cacheado solo para **atenuar el botón**, que es una pista
visual, no una garantía. La garantía está en el servidor.

---

## 7. Por qué las reglas offline se ordenan a mano

El motor recorre las reglas en orden y se queda con la primera que dispara. El
orden es parte de la lógica, no un detalle.

Caso concreto: `nav.place` responde a `llévame|vamos|ruta|navega`, y `nav.home`
a `a casa|mi casa`. La frase "llévame a casa" activa las dos. Con `nav.place`
primero, el asistente enruta al **último restaurante visitado** en vez de al
domicilio — un fallo que en carretera se paga con una salida perdida.

Regla general: **destino nombrado antes que destino inferido**. Por eso el orden
es `nav.home` → `nav.office` → `nav.place`, y `nav.unknown` queda al final como
red de seguridad, para decir "no tengo esa dirección guardada" en lugar de caer
al mensaje genérico de sin conexión.

---

## 8. Por qué el embedding local existe

Sin clave de API no hay `text-embedding-004`. La opción fácil sería dejar
`embedding` en `NULL` y que la búsqueda vectorial no devuelva nada.

Pero las consultas ordenan por `embedding <=> $2::vector`, y `NULL` en el
operando cambia el plan de la consulta. Por eso todas llevan
`ASC NULLS LAST` con un desempate determinista (`hits DESC`, `visit_count DESC`):
sin embeddings, la memoria sigue devolviendo lo más relevante por frecuencia.

El embedding local (hash de n-gramas a 768 dimensiones, ~0.2 ms) mantiene el
esquema íntegro y las consultas idénticas en ambos modos. No es semántico —
"coche" y "auto" caen en cubetas distintas— pero es estable, y cuando aparece la
clave de API los embeddings nuevos son reales sin migrar nada.

---

## 9. Presupuesto de latencia, de punta a punta

| Tramo | Presupuesto | Medido |
|---|---|---|
| Motor local (HTTP incluido) | < 15 ms | **5.8 ms** |
| Caché de respuesta (Redis) | < 5 ms | ~1 ms |
| Gemini primario | 3.5 s | variable |
| Gemini respaldo | 3.5 s | variable |
| **Peor caso absoluto** | **~7 s** | acotado por diseño |

Los 7 s del peor caso solo ocurren con red degradada —conectividad suficiente
para abrir el socket, insuficiente para completar—, exactamente el escenario de
la entrada a un túnel. Si no hay red en absoluto, el socket falla rápido y la
respuesta llega en milisegundos.

---

## 10. Lo que queda fuera, a propósito

- **Wake word ("Hey DriveAI").** Exige escucha continua: ~8 MB de RAM
  permanentes y consumo de batería con el coche apagado. El botón del volante
  (`SteeringWheelReceiver`) resuelve el mismo problema con cero coste en reposo.
- **Streaming de respuestas.** Tiene sentido para texto que se lee, no para TTS
  que empieza a hablar cuando la frase está completa. Añade complejidad de
  transporte sin ganancia percibida.
- **Multi-vehículo por conductor.** El esquema lo admite (bastaría una tabla
  `vehicles`), pero el caso real es un conductor y un coche. Se añade cuando
  aparezca la necesidad.
