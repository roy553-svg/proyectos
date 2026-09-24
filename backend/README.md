# El Profeta — Backend (Fase 1)

Backend del periódico digital "El Profeta". Expone la **edición semanal publicada**
filtrada por las **preferencias del usuario** para que la app móvil (Kotlin, Fase 3)
la consuma sin generar contenido por su cuenta.

> **Estado:** Fase 1 (modelos, base de datos y API) y Fase 2 (ingesta de
> noticias, redacción con IA, generación de animaciones y automatización
> semanal) implementadas. La app Android de la Fase 3 está en `../android/`.
>
> **Todas las integraciones externas son opcionales:** sin claves el backend
> usa adaptadores offline y el pipeline completo sigue siendo ejecutable.

---

## 1. Arquitectura (resumen)

Arquitectura por capas, cada una con una única responsabilidad:

| Capa | Paquete | Responsabilidad |
| --- | --- | --- |
| API | `app/api/` | Routers, validación de parámetros, códigos HTTP |
| Servicios | `app/services/` | Reglas de negocio (feed, pipeline semanal, vídeos) |
| Acceso a datos | `app/repositories/` | Consultas SQLAlchemy reutilizables |
| Integraciones | `app/integrations/` | Adaptadores externos (NewsAPI, Gemini, Replicate) y sus respaldos offline |
| Automatización | `app/scheduler.py` | Trabajos periódicos con APScheduler |
| Modelos | `app/models/` | Tablas ORM (SQLAlchemy 2.x, `Mapped` / `mapped_column`) |
| Schemas | `app/schemas/` | DTOs Pydantic de entrada/salida |
| Configuración | `app/core/` | Ajustes por entorno y excepciones de dominio |
| Base de datos | `app/db/` | Engine, sesión, `Base` declarativa |

Decisiones tomadas y su motivo:

* **`app/repositories/` separado de `app/services/`**: el enunciado pide separar
  "servicios" de "acceso a datos"; los repositorios contienen las consultas y el
  servicio contiene las reglas. Cuando la Fase 2 genere contenido automáticamente
  reutilizará los mismos repositorios.
* **`main.py` sólo ensambla la app** (`create_app()` como *application factory*):
  facilita crear instancias aisladas en los tests.
* **Excepciones de dominio** (`app/core/exceptions.py`) traducidas a HTTP en
  `app/api/errors.py`: los servicios no dependen de FastAPI.
* **`category` es texto, no un `ENUM` de base de datos**: la Fase 2 podrá añadir
  categorías nuevas sin migración. `app/models/enums.py` documenta las conocidas.
* **`language` en `NewsArticle`**: la preferencia de idioma sólo tiene sentido si
  el artículo declara el suyo; se filtra en SQL.
* **Fechas siempre en UTC** (`DateTime(timezone=True)` y serialización con sufijo
  `Z`), de modo que SQLite y PostgreSQL se comporten igual.

### Modelo de datos

```
UserPreferences            WeeklyEdition                NewsArticle
---------------            -------------                -----------
id (PK)                    id (PK)                      id (PK)
user_id (UNIQUE)           week_start / week_end        edition_id (FK -> weekly_editions.id, CASCADE)
preferred_categories(JSON) title                        title / content
language                   status (draft|published|     category / language
include_animated_only               archived)           image_url
max_articles               published_at                 video_url  (NULL hasta la Fase 2)
extra_preferences (JSON)   created_at                   source_url (NULL)
created_at / updated_at      |                          published_at / position
                             +---- 1:N ---------------> created_at
```

Restricciones e índices relevantes:

* Índice **único parcial** `uq_weekly_editions_published_week` sobre
  `(week_start, week_end) WHERE status = 'published'`: impide dos ediciones
  publicadas para la misma semana (permitiendo varios borradores). Funciona en
  SQLite y en PostgreSQL.
* `CHECK (week_end >= week_start)` y `CHECK (position >= 1)`.
* `UNIQUE (edition_id, position)`: el orden de aparición no se duplica.
* Índices compuestos `(edition_id, category)` y `(edition_id, position)` que cubren
  la consulta del endpoint, e `(status, week_start)` para localizar la edición
  publicada más reciente.
* `ON DELETE CASCADE` de `news_articles.edition_id`.

La respuesta se construye con **2 consultas** (edición + noticias filtradas), sin
N+1 y sin SQL construido con concatenación de strings.

### Árbol de ficheros

```
backend/
├── .dockerignore
├── .env.example
├── .gitignore
├── Dockerfile
├── README.md
├── alembic.ini
├── pytest.ini
├── requirements.txt
├── requirements-dev.txt
├── app/
│   ├── __init__.py
│   ├── main.py
│   ├── scheduler.py                 # Fase 2: APScheduler
│   ├── api/
│   │   ├── deps.py                  # sesión de BD + guardia del token admin
│   │   ├── errors.py
│   │   └── v1/
│   │       ├── router.py
│   │       └── endpoints/
│   │           ├── admin.py         # Fase 2: pipeline, vídeos, integraciones
│   │           ├── health.py
│   │           └── news.py
│   ├── core/
│   │   ├── config.py
│   │   └── exceptions.py
│   ├── db/
│   │   ├── base.py
│   │   ├── base_class.py
│   │   ├── init_db.py
│   │   └── session.py
│   ├── integrations/                # Fase 2: adaptadores externos
│   │   ├── base.py                  # puertos (Protocol)
│   │   ├── errors.py
│   │   ├── factory.py               # elige adaptador real u offline
│   │   ├── schemas.py               # dataclasses de intercambio
│   │   ├── gemini_text_generator.py
│   │   ├── offline_text_generator.py
│   │   ├── newsapi_provider.py
│   │   ├── local_news_provider.py
│   │   ├── replicate_video_generator.py
│   │   ├── disabled_video_generator.py
│   │   └── data/sample_news.json
│   ├── models/
│   │   ├── enums.py
│   │   ├── mixins.py
│   │   ├── news_article.py
│   │   ├── pipeline_run.py          # Fase 2: auditoría de ejecuciones
│   │   ├── user_preferences.py
│   │   └── weekly_edition.py
│   ├── repositories/
│   │   ├── news_article_repository.py
│   │   ├── user_preferences_repository.py
│   │   └── weekly_edition_repository.py
│   ├── schemas/
│   │   ├── admin.py                 # Fase 2
│   │   ├── article.py
│   │   ├── common.py
│   │   ├── edition.py
│   │   ├── news.py
│   │   ├── types.py
│   │   └── user_preferences.py
│   └── services/
│       ├── news_service.py
│       ├── pipeline_service.py      # Fase 2: pipeline semanal
│       └── video_service.py         # Fase 2: seguimiento de animaciones
├── migrations/
│   ├── env.py
│   ├── script.py.mako
│   └── versions/
│       ├── 0001_initial_schema.py
│       └── 0002_phase2_generation_fields.py
├── scripts/
│   └── seed.py
└── tests/
    ├── conftest.py
    ├── fakes.py                     # dobles de las integraciones
    ├── test_admin_endpoints.py
    ├── test_app_startup.py
    ├── test_integrations.py
    ├── test_models_constraints.py
    ├── test_news_endpoint.py
    ├── test_pipeline_service.py
    ├── test_scheduler.py
    └── test_video_service.py
```

---

## 2. Puesta en marcha

### 2.1. Requisitos previos

* Python **3.10+** (probado con 3.11).
* `pip` y el módulo `venv`.
* Nada más: SQLite viene incluido en Python. PostgreSQL sólo hace falta en
  producción.

### 2.2. Crear el entorno virtual

```bash
cd backend
python3 -m venv .venv
source .venv/bin/activate        # Windows: .venv\Scripts\activate
```

### 2.3. Instalar dependencias

```bash
pip install --upgrade pip
pip install -r requirements.txt        # sólo ejecución
pip install -r requirements-dev.txt    # ejecución + tests (recomendado)
```

### 2.4. Configurar `.env`

```bash
cp .env.example .env
```

Variables principales (todas tienen un valor por defecto razonable):

| Variable | Por defecto | Descripción |
| --- | --- | --- |
| `DATABASE_URL` | `sqlite:///./database.db` | Cadena de conexión SQLAlchemy |
| `DEBUG` | `false` | Modo desarrollo |
| `API_V1_PREFIX` | `/api/v1` | Prefijo del router |
| `CREATE_TABLES_ON_STARTUP` | `true` | Crear tablas al arrancar (poner `false` si se usa Alembic) |
| `SQL_ECHO` | `false` | Mostrar el SQL generado |
| `BACKEND_CORS_ORIGINS` | `http://localhost:3000,http://localhost:8080` | Lista blanca CORS (nunca `*`) |
| `MAX_ARTICLES_PER_RESPONSE` | `50` | Tope de seguridad del endpoint |

Para PostgreSQL basta con cambiar una línea (**los modelos no cambian**):

```bash
DATABASE_URL="postgresql+psycopg://profeta:CAMBIAME@localhost:5432/profeta"
```

y descomentar `psycopg[binary]` en `requirements.txt`.

> El fichero `.env` está en `.gitignore`: no se sube nunca y no contiene claves reales.

### 2.5. Inicializar la base de datos

Opción A — **migraciones Alembic** (recomendada, también en producción):

```bash
alembic upgrade head
```

Opción B — creación directa de tablas (desarrollo rápido): deja
`CREATE_TABLES_ON_STARTUP=true` y arranca la app; o bien:

```bash
python -c "from app.db.init_db import create_tables; create_tables()"
```

Para crear una migración nueva tras modificar los modelos:

```bash
alembic revision --autogenerate -m "descripcion del cambio"
alembic upgrade head
alembic check        # verifica que el esquema y los modelos no divergen
```

### 2.6. Ejecutar el seed

```bash
python -m scripts.seed            # idempotente
python -m scripts.seed --reset    # borra y vuelve a crear los datos de demo
```

Crea una edición **publicada** con 7 noticias (7 categorías, una en inglés), una
edición en **borrador** que nunca debe aparecer en la API, y tres usuarios:

| `user_id` | Categorías | Idioma | Sólo animadas | `max_articles` |
| --- | --- | --- | --- | --- |
| 1 | technology, science, magic | es | no | 20 |
| 2 | sports | es | no | 5 |
| 3 | (todas) | es | **sí** | 10 |

El seed no llama a ninguna API externa.

### 2.7. Arrancar FastAPI

```bash
uvicorn app.main:app --reload
```

### 2.8. Swagger / OpenAPI

* Swagger UI: <http://127.0.0.1:8000/docs>
* ReDoc: <http://127.0.0.1:8000/redoc>
* Esquema OpenAPI: <http://127.0.0.1:8000/openapi.json>
* Health check: <http://127.0.0.1:8000/api/v1/health>

### 2.9. Ejecutar los tests

```bash
pytest            # desde backend/, con requirements-dev.txt instalado
pytest -v
```

Los tests usan SQLite **en memoria** y sobrescriben la dependencia `get_db`: no
tocan `database.db` ni requieren red.

---

## 3. La API

### `GET /api/v1/news?user_id=1`

Devuelve la edición semanal **publicada** más reciente junto con las noticias
compatibles con las preferencias del usuario.

Filtros aplicados (todos en SQL):

1. sólo ediciones con `status = 'published'` (se toma la de `week_start` mayor);
2. `preferred_categories` (lista vacía = todas las categorías);
3. `language` del usuario;
4. `include_animated_only` → sólo noticias con `video_url`;
5. `max_articles`, acotado por `MAX_ARTICLES_PER_RESPONSE`;
6. orden por `position` ascendente.

Códigos de respuesta:

| Código | Situación |
| --- | --- |
| `200` | Edición publicada encontrada (la lista de noticias puede estar vacía) |
| `404` | El `user_id` no tiene preferencias registradas |
| `404` | No existe ninguna edición publicada |
| `422` | `user_id` ausente, no numérico o menor que 1 |

#### Ejemplo real de llamada

```bash
curl -s "http://127.0.0.1:8000/api/v1/news?user_id=1" | python -m json.tool
```

#### Ejemplo de respuesta

```json
{
  "edition": {
    "id": 1,
    "week_start": "2026-09-21",
    "week_end": "2026-09-27",
    "title": "El Profeta - Edicion Semanal",
    "published_at": "2026-09-21T08:00:00Z"
  },
  "articles": [
    {
      "id": 1,
      "title": "Las varitas inteligentes llegan al Callejon Diagon",
      "content": "Un taller de Ollivander presenta un prototipo de varita capaz de registrar los hechizos lanzados y sugerir correcciones de pronunciacion a los magos aprendices.",
      "summary": "Ollivander prueba una varita que corrige la pronunciacion.",
      "category": "technology",
      "language": "es",
      "image_url": "https://cdn.example.com/profeta/varitas.jpg",
      "video_url": "https://cdn.example.com/profeta/varitas.mp4",
      "source_url": "https://example.com/noticias/varitas",
      "source_name": "Gaceta Magica",
      "video_status": "ready",
      "published_at": "2026-09-21T08:00:00Z",
      "position": 1
    },
    {
      "id": 2,
      "title": "Descubren una nueva especie de bowtruckle en el bosque de Dean",
      "content": "El equipo de magizoologia describe un ejemplar capaz de camuflarse entre ramas heladas, lo que abre nuevas preguntas sobre su adaptacion al invierno.",
      "summary": "El ejemplar se camufla entre ramas heladas.",
      "category": "science",
      "language": "es",
      "image_url": "https://cdn.example.com/profeta/bowtruckle.jpg",
      "video_url": null,
      "source_url": null,
      "source_name": null,
      "video_status": "processing",
      "published_at": "2026-09-21T09:00:00Z",
      "position": 2
    }
  ]
}
```

`video_url` es `null` mientras la animación no esté lista; `video_status` dice
en qué punto está (`not_requested`, `pending`, `processing`, `ready`,
`failed`). El cliente muestra la imagen estática en todos esos casos.

Respuesta de error (usuario inexistente):

```json
{ "detail": "No existen preferencias para el usuario 999." }
```

### `GET /api/v1/health`

```json
{ "status": "ok", "app": "El Profeta API", "version": "0.1.0" }
```

---

## 4. Docker (opcional)

```bash
docker build -t profeta-backend ./backend
docker run --rm -p 8000:8000 \
  -e DATABASE_URL="sqlite:////data/database.db" \
  -v "$(pwd)/data:/data" \
  profeta-backend
```

---

## 5. Fase 2 — Pipeline automático (IA, vídeo y scheduler)

### 5.1. Qué hace

Una vez por semana el backend genera la edición él solo:

```
NewsAPI / ejemplos locales   ->  RawNewsItem
          |
          v
Gemini / redactor offline    ->  GeneratedArticle  (título, cuerpo, resumen)
          |
          v
WeeklyEdition (draft) + NewsArticle[]
          |
          v
Replicate (Stable Video Diffusion)  ->  job encolado por noticia
          |
          v
WeeklyEdition (published)    ->  GET /api/v1/news lo sirve
          |
          v
video_refresh (cada N min)   ->  rellena video_url cuando el vídeo está listo
```

### 5.2. Puertos y adaptadores

`app/integrations/base.py` define tres `Protocol` (`NewsProvider`,
`TextGenerator`, `VideoGenerator`). `app/integrations/factory.py` elige la
implementación **según haya o no clave configurada**:

| Punto de integración | Con clave | Sin clave (por defecto) |
| --- | --- | --- |
| Noticias | `NewsApiProvider` (NewsAPI) | `LocalNewsProvider` (JSON incluido) |
| Redacción | `GeminiTextGenerator` (Gemini REST) | `OfflineTextGenerator` (resumen extractivo) |
| Vídeo | `ReplicateVideoGenerator` (SVD) | `DisabledVideoGenerator` (`video_url` sigue nulo) |

Consecuencias prácticas:

* el proyecto **se ejecuta y se testea entero sin red ni claves**;
* añadir una credencial no exige tocar código, sólo el `.env`;
* si la IA falla en una noticia concreta se usa el redactor offline **para esa
  noticia** y la ejecución se marca como `partial`: una edición incompleta es
  peor que una edición sin IA;
* las animaciones son asíncronas: publicar no espera al vídeo, que llega
  después vía `video_refresh`.

### 5.3. Automatización (APScheduler)

`app/scheduler.py` registra dos trabajos y sólo arranca si
`ENABLE_SCHEDULER=true`:

| Trabajo | Disparador | Qué hace |
| --- | --- | --- |
| `weekly_pipeline` | cron (`WEEKLY_PIPELINE_DAY_OF_WEEK/HOUR/MINUTE`) | genera y publica la edición de la semana |
| `video_refresh` | cada `VIDEO_POLL_INTERVAL_MINUTES` | actualiza las animaciones pendientes |

Ambos usan `coalesce=True` y `max_instances=1`: si el proceso estuvo caído no
se acumulan ejecuciones atrasadas ni se solapan dos pipelines.

Cada ejecución se registra en la tabla `pipeline_runs` (estado, disparador,
noticias descargadas, artículos creados, cuántos escribió la IA, vídeos
encolados y detalle del error si lo hubo).

### 5.4. Endpoints de administración

Todos exigen la cabecera `X-Admin-Token` (comparada en tiempo constante). Si
`ADMIN_API_TOKEN` está vacío devuelven **503**: preferimos deshabilitarlos a
exponerlos abiertos.

| Método y ruta | Descripción |
| --- | --- |
| `POST /api/v1/admin/pipeline/run` | Ejecuta el pipeline (body opcional: `week_start`, `publish`, `force`) |
| `GET /api/v1/admin/pipeline/runs` | Historial de ejecuciones |
| `POST /api/v1/admin/videos/refresh` | Consulta las animaciones pendientes |
| `GET /api/v1/admin/integrations` | Adaptadores activos y estado del scheduler |

```bash
export TOKEN="token-local-de-pruebas"   # el valor de ADMIN_API_TOKEN

curl -s -H "X-Admin-Token: $TOKEN" http://127.0.0.1:8000/api/v1/admin/integrations

curl -s -X POST -H "X-Admin-Token: $TOKEN" -H "Content-Type: application/json" \
     -d '{"week_start":"2026-09-28","publish":true}' \
     http://127.0.0.1:8000/api/v1/admin/pipeline/run
```

Respuesta de una ejecución:

```json
{
  "id": 1,
  "status": "success",
  "trigger": "manual",
  "edition_id": 2,
  "items_fetched": 8,
  "articles_created": 8,
  "articles_ai_written": 0,
  "videos_requested": 0,
  "started_at": "2026-09-22T13:27:25.858528Z",
  "finished_at": "2026-09-22T13:27:25.890176Z",
  "detail": null
}
```

Estados posibles: `success`, `partial` (hubo fallos externos pero hay
edición), `skipped` (esa semana ya estaba publicada), `failed`.

### 5.5. Idempotencia y seguridad del pipeline

* Si la semana **ya tiene una edición publicada**, la ejecución se salta
  (`skipped`) salvo que se pase `force: true`, que **archiva** la anterior
  (`status = archived`) para no chocar con el índice único parcial.
* Si la semana tenía un **borrador**, se borra y se regenera (las noticias se
  eliminan en cascada).
* El pipeline nunca lanza excepciones hacia arriba: un fallo externo se
  registra como `failed` en `pipeline_runs` y el scheduler sigue vivo.
* Las claves viajan siempre en cabeceras (`x-goog-api-key`, `X-Api-Key`,
  `Authorization: Token ...`), nunca en la URL, y los mensajes de error no
  incluyen ni la clave ni la excepción original.

### 5.6. Campos añadidos en la Fase 2

`NewsArticle`: `summary`, `source_name`, `video_status`, `video_job_id`,
`ai_model`, `updated_at`. Tabla nueva `pipeline_runs`. Migración
`0002_phase2_generation_fields`, que añade las columnas NOT NULL con
`server_default` para poder aplicarse sobre datos de la Fase 1.

`video_status` (`not_requested`, `pending`, `processing`, `ready`, `failed`)
se expone en la API para que la app pueda avisar de que la animación está en
camino.

## 6. VERIFICACIÓN

### Fase 1 — Backend y base de datos

- [x] Modelos `UserPreferences`, `WeeklyEdition` y `NewsArticle` con sus relaciones.
- [x] SQLAlchemy 2.x con `Mapped` / `mapped_column`, índices y restricciones.
- [x] Índice único parcial que impide dos ediciones publicadas para la misma semana.
- [x] `GET /api/v1/news?user_id=...` con dependency injection de la sesión.
- [x] Respuestas validadas con schemas Pydantic (nunca objetos ORM).
- [x] Filtrado por categorías, idioma, contenido animado y `max_articles`.
- [x] Sólo se devuelven noticias de una edición **publicada**.
- [x] `404` para usuario inexistente y para "sin edición publicada"; `422` para parámetros inválidos.
- [x] Configuración por variables de entorno + `.env.example`, sin secretos.
- [x] SQLite por defecto y PostgreSQL cambiando sólo `DATABASE_URL`.
- [x] CORS explícito con lista blanca.
- [x] Migraciones Alembic (`0001_initial_schema`) verificadas con `alembic check`.
- [x] Seed sin dependencias externas (`python -m scripts.seed --reset`).
- [x] Tests funcionales con pytest + `TestClient`.
- [x] La Fase 1 sigue funcionando sin ninguna integración externa activa.

### Fase 2 — IA, vídeo y automatización

- [x] Puertos (`Protocol`) + adaptadores reales y offline para noticias, texto y vídeo.
- [x] `NewsApiProvider` (NewsAPI) con normalización y descarte de entradas incompletas.
- [x] `GeminiTextGenerator`: REST `generateContent`, salida JSON forzada por esquema.
- [x] `ReplicateVideoGenerator`: `submit` + `poll` (Stable Video Diffusion), asíncrono.
- [x] Respaldos offline para los tres puntos: el pipeline se ejecuta sin claves.
- [x] `pipeline_service`: descarga → redacción → edición → vídeos → publicación.
- [x] Degradación por noticia si la IA falla (ejecución `partial`, nunca `500`).
- [x] Idempotencia semanal (`skipped`), `force` que archiva la edición anterior.
- [x] `video_service`: rellena `video_url` cuando la animación está lista.
- [x] APScheduler con cron semanal + polling de vídeos, `coalesce`, `max_instances=1`.
- [x] Auditoría en `pipeline_runs` y endpoints `/api/v1/admin/*` protegidos por token.
- [x] Claves sólo en cabeceras y fuera de los mensajes de error.
- [x] Migración `0002_phase2_generation_fields` aplicable sobre datos existentes.
- [x] `PRAGMA foreign_keys=ON` en SQLite para que los `ON DELETE CASCADE` se cumplan.
- [x] 59 tests (pipeline, vídeos, adaptadores con `respx`, scheduler y admin).

Comandos para comprobarlo de principio a fin:

```bash
cd backend
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements-dev.txt
cp .env.example .env

# 1) Esquema
alembic upgrade head
alembic check                     # -> "No new upgrade operations detected."

# 2) Datos de demostración
python -m scripts.seed --reset    # -> "Seed completado."

# 3) Tests
pytest                            # -> 59 passed

# 4) API
uvicorn app.main:app --reload &
curl -s "http://127.0.0.1:8000/api/v1/health"
curl -s "http://127.0.0.1:8000/api/v1/news?user_id=1" | python -m json.tool   # 3 noticias
curl -s "http://127.0.0.1:8000/api/v1/news?user_id=2" | python -m json.tool   # 1 noticia (sports)
curl -s "http://127.0.0.1:8000/api/v1/news?user_id=3" | python -m json.tool   # sólo noticias con video_url
curl -s -o /dev/null -w "%{http_code}\n" "http://127.0.0.1:8000/api/v1/news?user_id=999"  # 404
curl -s -o /dev/null -w "%{http_code}\n" "http://127.0.0.1:8000/api/v1/news?user_id=0"    # 422

# 5) Fase 2: pipeline automático (funciona sin ninguna clave externa)
export TOKEN="token-local-de-pruebas"   # debe coincidir con ADMIN_API_TOKEN del .env
curl -s -H "X-Admin-Token: $TOKEN" \
     http://127.0.0.1:8000/api/v1/admin/integrations | python -m json.tool
#  -> news_provider=local, text_generator=offline, video_generator=disabled

curl -s -X POST -H "X-Admin-Token: $TOKEN" -H "Content-Type: application/json" \
     -d '{"week_start":"2026-09-28"}' \
     http://127.0.0.1:8000/api/v1/admin/pipeline/run | python -m json.tool
#  -> {"status": "success", "articles_created": 8, ...}

curl -s "http://127.0.0.1:8000/api/v1/news?user_id=1" | python -m json.tool
#  -> ahora sirve la edición recién generada

curl -s -o /dev/null -w "%{http_code}\n" \
     -X POST http://127.0.0.1:8000/api/v1/admin/pipeline/run   # 401 sin token
```

Para activar la automatización semanal real basta con poner
`ENABLE_SCHEDULER=true` (y, si se quiere IA y vídeo de verdad, las claves de
Gemini/Replicate/NewsAPI) en el `.env` y reiniciar el servidor.
