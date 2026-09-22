"""Creacion de tablas para desarrollo.

En produccion la fuente de verdad son las migraciones de Alembic
(``alembic upgrade head``); esta utilidad existe para arrancar rapido en
local y para los tests.
"""

from __future__ import annotations

from sqlalchemy.engine import Engine

from app.db.base import Base  # noqa: F401  (registra todos los modelos)
from app.db.session import engine as default_engine


def create_tables(engine: Engine | None = None) -> None:
    """Crea todas las tablas que aun no existan."""
    Base.metadata.create_all(bind=engine or default_engine)
