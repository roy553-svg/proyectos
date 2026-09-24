"""Configuracion centralizada de la aplicacion.

Toda la configuracion se lee de variables de entorno (o de un fichero .env),
de modo que no existan secretos ni rutas embebidas en el codigo.
"""

from __future__ import annotations

import json
from functools import lru_cache
from typing import List, Optional

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Ajustes de la aplicacion cargados desde el entorno."""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )

    # --- Aplicacion -------------------------------------------------------
    app_name: str = "El Profeta API"
    app_version: str = "0.2.0"
    debug: bool = False
    api_v1_prefix: str = "/api/v1"

    # --- Base de datos ----------------------------------------------------
    # Cambiar a "postgresql+psycopg://user:pass@host:5432/db" en produccion.
    database_url: str = "sqlite:///./database.db"
    sql_echo: bool = False
    create_tables_on_startup: bool = True

    # --- Seguridad / CORS -------------------------------------------------
    # Se guarda como texto para admitir tanto "a,b" como '["a","b"]' en .env;
    # usar siempre la propiedad ``cors_origins``.
    backend_cors_origins: str = "http://localhost:3000,http://localhost:8080"

    # --- Reglas de negocio ------------------------------------------------
    max_articles_per_response: int = 50

    # --- Fase 2: administracion ------------------------------------------
    # Token para los endpoints /api/v1/admin. Si esta vacio los endpoints
    # quedan deshabilitados (503): nunca se exponen sin proteccion.
    admin_api_token: Optional[str] = None

    # --- Fase 2: fuente de noticias --------------------------------------
    # Sin NEWSAPI_API_KEY se usa el proveedor local de ejemplos (offline).
    newsapi_api_key: Optional[str] = None
    newsapi_base_url: str = "https://newsapi.org/v2"
    news_language: str = "es"
    news_country: str = "es"
    news_items_per_edition: int = 8

    # --- Fase 2: redaccion con IA (Gemini) -------------------------------
    # Sin GEMINI_API_KEY se usa el redactor offline (resumen extractivo).
    gemini_api_key: Optional[str] = None
    gemini_base_url: str = "https://generativelanguage.googleapis.com/v1beta"
    gemini_model: str = "gemini-2.5-flash"
    gemini_timeout_seconds: float = 60.0

    # --- Fase 2: generacion de video (Replicate) -------------------------
    # Sin REPLICATE_API_TOKEN no se generan animaciones y video_url sigue nulo.
    replicate_api_token: Optional[str] = None
    replicate_base_url: str = "https://api.replicate.com/v1"
    # Version del modelo (p. ej. Stable Video Diffusion) en Replicate.
    replicate_model_version: Optional[str] = None
    replicate_timeout_seconds: float = 30.0
    videos_per_edition: int = 3

    # --- Fase 2: automatizacion (APScheduler) ----------------------------
    enable_scheduler: bool = False
    scheduler_timezone: str = "UTC"
    # Lunes a las 06:00 (zona horaria de scheduler_timezone).
    weekly_pipeline_day_of_week: str = "mon"
    weekly_pipeline_hour: int = 6
    weekly_pipeline_minute: int = 0
    # Cada cuantos minutos se consulta el estado de los videos pendientes.
    video_poll_interval_minutes: int = 15

    @property
    def cors_origins(self) -> List[str]:
        """Lista explicita de origenes permitidos por CORS."""
        raw = self.backend_cors_origins.strip()
        if not raw:
            return []
        if raw.startswith("["):
            parsed = json.loads(raw)
            return [str(origin).strip() for origin in parsed if str(origin).strip()]
        return [origin.strip() for origin in raw.split(",") if origin.strip()]

    @property
    def is_sqlite(self) -> bool:
        """True si la URL configurada apunta a SQLite."""
        return self.database_url.startswith("sqlite")


@lru_cache
def get_settings() -> Settings:
    """Devuelve la instancia unica de configuracion (cacheada)."""
    return Settings()


settings = get_settings()
