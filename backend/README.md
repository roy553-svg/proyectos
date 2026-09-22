# El Profeta — Backend (Fase 1)

Backend del periódico digital "El Profeta". Expone la **edición semanal publicada**
filtrada por las **preferencias del usuario** para que la app móvil (Kotlin, Fase 3)
la consuma sin generar contenido por su cuenta.

> **Alcance:** esta entrega implementa **solo la Fase 1** (modelos, base de datos y
> API). No hay Gemini, Replicate, NewsAPI, APScheduler, scraping ni código Android.
> La arquitectura queda preparada para añadirlos en la Fase 2 sin reescribir nada.

---

## 1. Arquitectura (resumen)

Arquitectura por capas, cada una con una única responsabilidad:

| Capa | Paquete | Responsabilidad |
| --- | --- | --- |
| API | `app/api/` | Routers, validación de parámetros, códigos HTTP |
| Servicios | `app/services/` | Reglas de negocio (qué edición y qué noticias se devuelven) |
| Acceso a datos | `app/repositories/` | Consultas SQLAlchemy reutilizables |
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
│   ├── api/
│   │   ├── __init__.py
│   │   ├── deps.py
│   │   ├── errors.py
│   │   └── v1/
│   │       ├── __init__.py
│   │       ├── router.py
│   │       └── endpoints/
│   │           ├── __init__.py
│   │           ├── health.py
│   │           └── news.py
│   ├── core/
│   │   ├── __init__.py
│   │   ├── config.py
│   │   └── exceptions.py
│   ├── db/
│   │   ├── __init__.py
│   │   ├── base.py
│   │   ├── base_class.py
│   │   ├── init_db.py
│   │   └── session.py
│   ├── models/
│   │   ├── __init__.py
│   │   ├── enums.py
│   │   ├── mixins.py
│   │   ├── news_article.py
│   │   ├── user_preferences.py
│   │   └── weekly_edition.py
│   ├── repositories/
│   │   ├── __init__.py
│   │   ├── news_article_repository.py
│   │   ├── user_preferences_repository.py
│   │   └── weekly_edition_repository.py
│   ├── schemas/
│   │   ├── __init__.py
│   │   ├── article.py
│   │   ├── common.py
│   │   ├── edition.py
│   │   ├── news.py
│   │   ├── types.py
│   │   └── user_preferences.py
│   └── services/
│       ├── __init__.py
│       └── news_service.py
├── migrations/
│   ├── env.py
│   ├── script.py.mako
│   └── versions/
│       └── 0001_initial_schema.py
├── scripts/
│   ├── __init__.py
│   └── seed.py
└── tests/
    ├── __init__.py
    ├── conftest.py
    ├── test_app_startup.py
    ├── test_models_constraints.py
    └── test_news_endpoint.py
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
      "category": "technology",
      "language": "es",
      "image_url": "https://cdn.example.com/profeta/varitas.jpg",
      "video_url": "https://cdn.example.com/profeta/varitas.mp4",
      "source_url": "https://example.com/noticias/varitas",
      "published_at": "2026-09-21T08:00:00Z",
      "position": 1
    },
    {
      "id": 2,
      "title": "Descubren una nueva especie de bowtruckle en el bosque de Dean",
      "content": "El equipo de magizoologia describe un ejemplar capaz de camuflarse entre ramas heladas, lo que abre nuevas preguntas sobre su adaptacion al invierno.",
      "category": "science",
      "language": "es",
      "image_url": "https://cdn.example.com/profeta/bowtruckle.jpg",
      "video_url": null,
      "source_url": null,
      "published_at": "2026-09-21T09:00:00Z",
      "position": 2
    }
  ]
}
```

`video_url` es `null` mientras la generación de animaciones (Fase 2) no exista; el
cliente debe mostrar la imagen estática en ese caso.

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

## 5. Preparado para la Fase 2 (sin implementar)

Puntos de extensión que ya existen y que la Fase 2 sólo tendrá que rellenar:

* `NewsArticle.video_url` y `source_url` ya son nulos → el generador de vídeo sólo
  tendrá que actualizarlos.
* `WeeklyEdition.status` permite crear la edición como `draft`, completarla y
  publicarla en un único paso (`published` + `published_at`).
* `UserPreferences.extra_preferences` (JSON) admite preferencias nuevas sin migrar.
* Los repositorios encapsulan todas las consultas: el pipeline automático los
  reutilizará en vez de escribir SQL propio.
* Alembic ya está configurado para versionar cualquier campo que la Fase 2 añada.

---

## 6. VERIFICACIÓN DE FASE 1

Checklist de lo implementado:

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
- [x] 19 tests funcionales con pytest + `TestClient`.
- [x] Sin dependencias de Gemini, Replicate, NewsAPI, APScheduler ni Android.

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
pytest                            # -> 19 passed

# 4) API
uvicorn app.main:app --reload &
curl -s "http://127.0.0.1:8000/api/v1/health"
curl -s "http://127.0.0.1:8000/api/v1/news?user_id=1" | python -m json.tool   # 3 noticias
curl -s "http://127.0.0.1:8000/api/v1/news?user_id=2" | python -m json.tool   # 1 noticia (sports)
curl -s "http://127.0.0.1:8000/api/v1/news?user_id=3" | python -m json.tool   # sólo noticias con video_url
curl -s -o /dev/null -w "%{http_code}\n" "http://127.0.0.1:8000/api/v1/news?user_id=999"  # 404
curl -s -o /dev/null -w "%{http_code}\n" "http://127.0.0.1:8000/api/v1/news?user_id=0"    # 422
```
