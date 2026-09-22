-- =====================================================================
-- DriveAI :: Motor de Memoria Jerarquico en 4 Capas
-- PostgreSQL 16 + pgvector
-- =====================================================================
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Conductores / perfiles ------------------------------------------------
CREATE TABLE IF NOT EXISTS drivers (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    external_id     TEXT UNIQUE NOT NULL,
    display_name    TEXT,
    locale          TEXT NOT NULL DEFAULT 'es-MX',
    mic_enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    location_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    memory_enabled  BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Trayectos (una sesion de conduccion) ---------------------------------
CREATE TABLE IF NOT EXISTS trips (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    driver_id   UUID NOT NULL REFERENCES drivers(id) ON DELETE CASCADE,
    started_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at    TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_trips_driver ON trips(driver_id, started_at DESC);

-- ---------------------------------------------------------------------
-- CAPA 1 :: Memoria de trabajo inmediata (ultimos turnos del trayecto)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS l1_working_memory (
    id          BIGSERIAL PRIMARY KEY,
    driver_id   UUID NOT NULL REFERENCES drivers(id) ON DELETE CASCADE,
    trip_id     UUID REFERENCES trips(id) ON DELETE CASCADE,
    role        TEXT NOT NULL CHECK (role IN ('driver','assistant')),
    content     TEXT NOT NULL,
    source      TEXT NOT NULL DEFAULT 'gemini',  -- gemini | offline | cache
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_l1_recent ON l1_working_memory(driver_id, created_at DESC);

-- ---------------------------------------------------------------------
-- CAPA 2 :: Preferencias declaradas e inferidas
-- origin = 'declared' -> el conductor lo dijo explicitamente
-- origin = 'inferred' -> deducido por el sistema (confidence < 1.0)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS l2_preferences (
    id          BIGSERIAL PRIMARY KEY,
    driver_id   UUID NOT NULL REFERENCES drivers(id) ON DELETE CASCADE,
    category    TEXT NOT NULL,       -- music | food | route | climate | other
    subject     TEXT NOT NULL,       -- 'rock en espanol', 'tacos al pastor'
    sentiment   TEXT NOT NULL DEFAULT 'like' CHECK (sentiment IN ('like','dislike','neutral')),
    origin      TEXT NOT NULL CHECK (origin IN ('declared','inferred')),
    confidence  REAL NOT NULL DEFAULT 1.0 CHECK (confidence >= 0 AND confidence <= 1),
    evidence    TEXT,                -- frase textual que respalda el dato (anti-alucinacion)
    embedding   vector(768),
    hits        INTEGER NOT NULL DEFAULT 1,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (driver_id, category, subject)
);
CREATE INDEX IF NOT EXISTS idx_l2_driver ON l2_preferences(driver_id, category);
-- HNSW y no ivfflat: ivfflat necesita entrenarse con datos ya cargados, y
-- creado sobre una tabla vacia deja el recall degradado hasta un REINDEX.
CREATE INDEX IF NOT EXISTS idx_l2_vec ON l2_preferences
    USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);

-- ---------------------------------------------------------------------
-- CAPA 3 :: Memoria episodica de lugares
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS l3_places (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    driver_id    UUID NOT NULL REFERENCES drivers(id) ON DELETE CASCADE,
    name         TEXT NOT NULL,
    kind         TEXT,               -- restaurante | gasolinera | taller | casa | oficina
    address      TEXT,
    lat          DOUBLE PRECISION,
    lon          DOUBLE PRECISION,
    rating       REAL CHECK (rating >= 0 AND rating <= 5),
    visit_count  INTEGER NOT NULL DEFAULT 1,
    last_visit   TIMESTAMPTZ NOT NULL DEFAULT now(),
    notes        TEXT,
    embedding    vector(768),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (driver_id, name)
);
CREATE INDEX IF NOT EXISTS idx_l3_driver ON l3_places(driver_id, visit_count DESC);
CREATE INDEX IF NOT EXISTS idx_l3_vec ON l3_places
    USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);

-- ---------------------------------------------------------------------
-- CAPA 4 :: Hechos historicos consolidados (rutinas fijas)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS l4_facts (
    id            BIGSERIAL PRIMARY KEY,
    driver_id     UUID NOT NULL REFERENCES drivers(id) ON DELETE CASCADE,
    fact_key      TEXT NOT NULL,     -- home_address | office_address | office_schedule
    fact_value    TEXT NOT NULL,
    schedule_cron TEXT,              -- '0 8 * * 1-5' cuando aplica una rutina
    confidence    REAL NOT NULL DEFAULT 1.0,
    evidence      TEXT,
    embedding     vector(768),
    consolidated_from INTEGER NOT NULL DEFAULT 1, -- cuantas observaciones lo respaldan
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (driver_id, fact_key)
);
CREATE INDEX IF NOT EXISTS idx_l4_driver ON l4_facts(driver_id);
CREATE INDEX IF NOT EXISTS idx_l4_vec ON l4_facts
    USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);

-- Auditoria de comandos al vehiculo (seguridad) -------------------------
CREATE TABLE IF NOT EXISTS vehicle_command_log (
    id          BIGSERIAL PRIMARY KEY,
    driver_id   UUID REFERENCES drivers(id) ON DELETE SET NULL,
    provider    TEXT NOT NULL,
    command     TEXT NOT NULL,
    payload     JSONB,
    allowed     BOOLEAN NOT NULL,
    reason      TEXT,
    speed_kph   REAL,
    gear        TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_cmdlog_driver ON vehicle_command_log(driver_id, created_at DESC);

-- Semilla de desarrollo -------------------------------------------------
INSERT INTO drivers (external_id, display_name)
VALUES ('demo-driver', 'Conductor Demo')
ON CONFLICT (external_id) DO NOTHING;
