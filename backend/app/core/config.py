"""Configuracion centralizada de la aplicacion.

Toda la configuracion se lee de variables de entorno (o de un fichero .env),
de modo que no existan secretos ni rutas embebidas en el codigo.
"""

from __future__ import annotations

import json
from functools import lru_cache
from typing import List

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
    app_version: str = "0.1.0"
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
